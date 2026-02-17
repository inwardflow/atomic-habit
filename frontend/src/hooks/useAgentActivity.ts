import { useCallback, useRef, useState } from 'react';
import type { AgentActivity, AgentPhase, ToolCallActivity } from '../components/AgentActivityIndicator';

const INITIAL_ACTIVITY: AgentActivity = {
  phase: 'idle',
  toolCalls: [],
  elapsedMs: 0,
};

/**
 * Hook that tracks agent activity phases from AG-UI events.
 *
 * Usage:
 *   const { activity, handlers, reset } = useAgentActivity();
 *   // Pass handlers to agent.subscribe() or call them from onRawEvent
 *   // Use <AgentActivityIndicator activity={activity} /> to render
 */
export function useAgentActivity() {
  const [activity, setActivity] = useState<AgentActivity>(INITIAL_ACTIVITY);
  const startTimeRef = useRef<number>(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const startTimer = useCallback(() => {
    startTimeRef.current = Date.now();
    // Update elapsed every second
    if (timerRef.current) clearInterval(timerRef.current);
    timerRef.current = setInterval(() => {
      setActivity((prev) => ({
        ...prev,
        elapsedMs: Date.now() - startTimeRef.current,
      }));
    }, 1000);
  }, []);

  const stopTimer = useCallback(() => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const setPhase = useCallback(
    (phase: AgentPhase) => {
      setActivity((prev) => ({
        ...prev,
        phase,
        elapsedMs: startTimeRef.current ? Date.now() - startTimeRef.current : 0,
      }));
      if (phase === 'done' || phase === 'error' || phase === 'idle') {
        stopTimer();
      }
    },
    [stopTimer],
  );

  const addToolCall = useCallback((name: string, args?: Record<string, unknown>) => {
    setActivity((prev) => ({
      ...prev,
      phase: 'tool_calling',
      toolCalls: [...prev.toolCalls, { name, status: 'running', args }],
      elapsedMs: startTimeRef.current ? Date.now() - startTimeRef.current : 0,
    }));
  }, []);

  const completeToolCall = useCallback((name: string) => {
    setActivity((prev) => ({
      ...prev,
      toolCalls: prev.toolCalls.map((tc) =>
        tc.name === name && tc.status === 'running' ? { ...tc, status: 'done' as const } : tc,
      ),
    }));
  }, []);

  const reset = useCallback(() => {
    stopTimer();
    setActivity(INITIAL_ACTIVITY);
  }, [stopTimer]);

  /**
   * Process a raw AG-UI event to update activity state.
   * Call this from the agent's onRawEvent subscriber.
   */
  const processEvent = useCallback(
    (event: any) => {
      if (!event) return;

      const type = event.type || event.eventType;
      if (!type) return;

      switch (type) {
        case 'RUN_STARTED':
          startTimer();
          setPhase('thinking');
          break;

        case 'TOOL_CALL_START': {
          const toolName =
            event.toolCallName ||
            event.name ||
            (event.toolCall && event.toolCall.name) ||
            'unknown_tool';
          const toolArgs =
            event.toolCallArgs ||
            event.args ||
            (event.toolCall && event.toolCall.arguments) ||
            undefined;

          // Check if it's a memory-related tool
          const isMemoryTool =
            toolName.includes('memory') ||
            toolName.includes('search') ||
            toolName.includes('recall');
          if (isMemoryTool) {
            setPhase('reading_memory');
          }

          addToolCall(toolName, toolArgs);
          break;
        }

        case 'TOOL_CALL_END':
        case 'TOOL_CALL_RESULT': {
          const endToolName =
            event.toolCallName ||
            event.name ||
            (event.toolCall && event.toolCall.name) ||
            '';
          if (endToolName) {
            completeToolCall(endToolName);
          }
          // After tool call completes, go back to thinking
          setPhase('thinking');
          break;
        }

        case 'TEXT_MESSAGE_START':
          setPhase('generating');
          break;

        case 'TEXT_MESSAGE_CONTENT':
          setPhase('generating');
          break;

        case 'TEXT_MESSAGE_END':
          // Stay in generating until RUN_FINISHED
          break;

        case 'RUN_FINISHED':
          setPhase('done');
          // Auto-reset to idle after a brief moment
          setTimeout(() => {
            setActivity(INITIAL_ACTIVITY);
          }, 1500);
          break;

        case 'RUN_ERROR':
          setPhase('error');
          break;

        default:
          break;
      }
    },
    [addToolCall, completeToolCall, setPhase, startTimer],
  );

  /**
   * Mark the start of a new agent run. Call this before agent.runAgent().
   */
  const markRunStart = useCallback(() => {
    setActivity({
      phase: 'connecting',
      toolCalls: [],
      elapsedMs: 0,
    });
    startTimer();
  }, [startTimer]);

  /**
   * Mark the run as failed. Call this in the catch block.
   */
  const markRunError = useCallback(() => {
    setPhase('error');
    setTimeout(() => {
      setActivity(INITIAL_ACTIVITY);
    }, 3000);
  }, [setPhase]);

  return {
    activity,
    processEvent,
    markRunStart,
    markRunError,
    reset,
  };
}
