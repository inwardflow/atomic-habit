import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { HttpAgent } from '@ag-ui/client';
import type { Message } from '@ag-ui/client';
import JSON5 from 'json5';
import { Calendar, Layers, Send, X, Zap } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import toast from 'react-hot-toast';
import api, { BACKEND_URL } from '../api/axios';
import { runAgentWithRetry, classifyAgentError, getErrorToastMessage } from '../utils/agentRetry';
import type { ClassifiedError } from '../utils/agentRetry';
import { useGoals } from '../hooks/useGoals';
import { useHabits } from '../hooks/useHabits';
import { useAuthStore } from '../store/authStore';
import type { GoalRequest, HabitRequest } from '../types';

const AGENT_RUN_URL = `${BACKEND_URL}/agui/run`;

const STARTER_PROMPTS = [
  'I feel a bit overwhelmed. Walk me through 3 tiny first steps.',
  'Ask me 3 questions and help me find my first 2-minute habit.',
  'Give me one smallest action I can do right now.',
];

interface ChatInterfaceProps {
  onClose: () => void;
  onHabitsAdded?: () => void;
  onIdentityUpdated?: () => void;
}

interface MoodItem {
  moodType: string;
}

interface UiMessage {
  id: string;
  role: string;
  content: string;
}

interface AgentMessageShape {
  id?: unknown;
  role?: unknown;
  content?: unknown;
  toolCalls?: unknown;
}

interface PlanHabit {
  name: string;
  twoMinuteVersion?: string;
  cueImplementationIntention?: string;
}

interface GoalPlan {
  goalName: string;
  description?: string;
  startDate?: string;
  endDate?: string;
  habits: PlanHabit[];
}

type PlanPayload = GoalPlan | PlanHabit[];

const TypingIndicator = () => (
  <div className="flex space-x-1 rounded-lg bg-gray-100 p-2 w-fit">
    <div className="h-2 w-2 animate-bounce rounded-full bg-gray-400 [animation-delay:-0.3s]"></div>
    <div className="h-2 w-2 animate-bounce rounded-full bg-gray-400 [animation-delay:-0.15s]"></div>
    <div className="h-2 w-2 animate-bounce rounded-full bg-gray-400"></div>
  </div>
);

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

const isStringArray = (value: unknown): value is string[] =>
  Array.isArray(value) && value.every((item) => typeof item === 'string');

const isPlanHabit = (value: unknown): value is PlanHabit =>
  isRecord(value) && typeof value.name === 'string';

const isPlanHabitList = (value: unknown): value is PlanHabit[] =>
  Array.isArray(value) && value.length > 0 && value.every(isPlanHabit);

const isGoalPlan = (value: unknown): value is GoalPlan =>
  isRecord(value) &&
  typeof value.goalName === 'string' &&
  Array.isArray(value.habits) &&
  value.habits.every(isPlanHabit);

const normalizeContent = (value: unknown): string => {
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value ?? '');
  }
};

const tryParseJson = (raw: string): unknown | null => {
  try {
    return JSON5.parse(raw);
  } catch {
    return null;
  }
};

const parseToolArguments = (rawArgs: unknown): Record<string, unknown> => {
  if (typeof rawArgs === 'string') {
    try {
      const parsed = JSON.parse(rawArgs);
      if (isRecord(parsed)) return parsed;
    } catch {
      return { raw: rawArgs };
    }
    return { raw: rawArgs };
  }

  if (isRecord(rawArgs)) {
    return rawArgs;
  }

  return {};
};

const buildToolCallBlocks = (toolCalls: unknown): string => {
  if (!Array.isArray(toolCalls) || toolCalls.length === 0) return '';

  const blocks = toolCalls
    .map((call) => {
      if (!isRecord(call)) return '';
      const func = isRecord(call.function) ? call.function : null;
      const name = func && typeof func.name === 'string' ? func.name : '';
      if (!name) return '';

      const payload = {
        name,
        arguments: parseToolArguments(func ? func.arguments : undefined),
      };
      return `\`\`\`json\n${JSON.stringify(payload, null, 2)}\n\`\`\``;
    })
    .filter(Boolean);

  return blocks.join('\n\n');
};

const buildRenderableContent = (message: AgentMessageShape): string => {
  const textContent = normalizeContent(message.content);
  const toolBlocks = buildToolCallBlocks(message.toolCalls);

  if (!toolBlocks) return textContent;
  if (textContent.trim()) return `${textContent}\n\n${toolBlocks}`;
  return toolBlocks;
};

const isAssistantRole = (role: unknown): boolean => {
  const normalized = typeof role === 'string' ? role.toLowerCase() : '';
  return normalized === 'assistant' || normalized === 'ai';
};

const getLastAssistantContent = (messages: Message[]): string => {
  const lastAssistant = [...messages].reverse().find((msg) => isAssistantRole((msg as any).role));
  if (!lastAssistant) return '';
  return buildRenderableContent(lastAssistant as unknown as AgentMessageShape).trim();
};

const getLastAssistantSignature = (messages: Message[]): string => {
  const lastAssistant = [...messages].reverse().find((msg) => isAssistantRole((msg as any).role));
  if (!lastAssistant) return '';
  const id = typeof (lastAssistant as any).id === 'string' ? (lastAssistant as any).id : '';
  const content = buildRenderableContent(lastAssistant as unknown as AgentMessageShape).trim();
  return `${id}::${content}`;
};

const toUserFacingAgentError = (errorText: string): string => {
  const normalized = errorText.toLowerCase();
  if (normalized.includes('invalid token') || normalized.includes('401')) {
    return 'AI service token is invalid on backend. Please configure a valid AGENTSCOPE_MODEL_API_KEY.';
  }
  return `AG-UI error: ${errorText}`;
};

const extractAssistantFromMessagesArray = (candidate: unknown): string | null => {
  if (!Array.isArray(candidate)) return null;

  for (let i = candidate.length - 1; i >= 0; i -= 1) {
    const item = candidate[i];
    if (!isRecord(item) || !isAssistantRole(item.role)) continue;
    const content = buildRenderableContent(item as AgentMessageShape).trim();
    if (content) return content;
  }

  return null;
};

const extractAssistantFromRunResult = (result: unknown, depth: number = 0): string | null => {
  if (depth > 4 || result == null) return null;

  if (typeof result === 'string') {
    const trimmed = result.trim();
    return trimmed || null;
  }

  if (!isRecord(result)) return null;

  const directKeys = ['response', 'content', 'text', 'output', 'message'] as const;
  for (const key of directKeys) {
    const value = result[key];
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
  }

  const fromMessages =
    extractAssistantFromMessagesArray(result.messages) ??
    (isRecord(result.output) ? extractAssistantFromMessagesArray(result.output.messages) : null) ??
    (isRecord(result.data) ? extractAssistantFromMessagesArray(result.data.messages) : null);
  if (fromMessages) return fromMessages;

  const nestedKeys = ['result', 'data', 'output'] as const;
  for (const key of nestedKeys) {
    const nested = result[key];
    const extracted = extractAssistantFromRunResult(nested, depth + 1);
    if (extracted) return extracted;
  }

  return null;
};

const toUiMessages = (messages: AgentMessageShape[]): UiMessage[] =>
  messages.map((message, index) => {
    const roleValue = typeof message.role === 'string' ? message.role : 'assistant';
    const role = roleValue === 'assistant' ? 'ai' : roleValue;
    const content = buildRenderableContent(message);
    const rawId = typeof message.id === 'string' ? message.id.trim() : '';
    const id = rawId || `${role}-${index}-${content.slice(0, 32)}`;

    return {
      id,
      role,
      content,
    };
  });

const extractRefreshAction = (content: string): boolean => {
  const match = content.match(/```action\s*([\s\S]*?)\s*```/i);
  if (!match) return false;

  const parsed = tryParseJson(match[1]);
  return isRecord(parsed) && parsed.type === 'REFRESH';
};

const parseDisplayContent = (
  content: string
): {
  displayContent: string;
  replies: string[];
  plan: PlanPayload | null;
  planBlock: string | null;
} => {
  let displayContent = content;
  let replies: string[] = [];

  displayContent = displayContent.replace(/```action\s*([\s\S]*?)\s*```/gi, (match, group) => {
    const parsed = tryParseJson(group);
    if (isRecord(parsed) && parsed.type === 'REFRESH') {
      return '';
    }
    return match;
  });

  displayContent = displayContent.replace(/```(?:replies|json)?\s*([\s\S]*?)\s*```/gi, (match, group) => {
    const parsed = tryParseJson(group);
    if (isStringArray(parsed)) {
      replies = parsed;
      return '';
    }
    return match;
  });

  displayContent = displayContent.trim();

  const planMatch = displayContent.match(/```(?:json)?\s*([\s\S]*?)\s*```/i);
  if (!planMatch) {
    return { displayContent, replies, plan: null, planBlock: null };
  }

  const parsedPlan = tryParseJson(planMatch[1]);
  if (isGoalPlan(parsedPlan) || isPlanHabitList(parsedPlan)) {
    return {
      displayContent,
      replies,
      plan: parsedPlan,
      planBlock: planMatch[0],
    };
  }

  return { displayContent, replies, plan: null, planBlock: null };
};

const ChatInterface: React.FC<ChatInterfaceProps> = ({ onClose, onHabitsAdded, onIdentityUpdated }) => {
  const [messages, setMessages] = useState<UiMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [recentMoods, setRecentMoods] = useState<MoodItem[]>([]);

  const { addHabits } = useHabits();
  const { createGoal } = useGoals();
  const user = useAuthStore((state) => state.user);
  const token = useAuthStore((state) => state.token);

  const agentRef = useRef<HttpAgent | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const processedActionsRef = useRef<Set<string>>(new Set());
  const handledRunErrorSignaturesRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    let active = true;

    api
      .get<MoodItem[]>('/moods/recent?hours=24')
      .then((res) => {
        if (active) {
          setRecentMoods(Array.isArray(res.data) ? res.data : []);
        }
      })
      .catch(() => {
        if (active) {
          setRecentMoods([]);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const handleRefreshAction = useCallback(
    (content: string) => {
      if (!extractRefreshAction(content)) return;
      onHabitsAdded?.();
      onIdentityUpdated?.();
    },
    [onHabitsAdded, onIdentityUpdated]
  );

  useEffect(() => {
    if (!user) return;

    const agent = new HttpAgent({
      url: AGENT_RUN_URL,
      threadId: `user-${user.id}`,
      ...(token ? { headers: { Authorization: `Bearer ${token}` } } : {}),
    });

    agentRef.current = agent;

    const { unsubscribe } = agent.subscribe({
      onMessagesChanged: ({ messages: rawMessages }) => {
        const nextMessages = toUiMessages(rawMessages as AgentMessageShape[]);

        setMessages(nextMessages);

        const lastMessage = nextMessages[nextMessages.length - 1];
        if (!lastMessage) return;

        if (lastMessage.role === 'ai' && !processedActionsRef.current.has(lastMessage.id)) {
          handleRefreshAction(lastMessage.content);
          processedActionsRef.current.add(lastMessage.id);
        }

        setLoading(false);
      },
      onRawEvent: ({ event }) => {
        const rawEvent = (event as any)?.rawEvent;
        const errorText = typeof rawEvent?.error === 'string' ? rawEvent.error.trim() : '';
        if (!errorText) return;

        const runId = typeof (event as any)?.runId === 'string' ? (event as any).runId : 'unknown-run';
        const signature = `${runId}::${errorText}`;
        if (handledRunErrorSignaturesRef.current.has(signature)) return;
        handledRunErrorSignaturesRef.current.add(signature);

        agent.addMessage({
          id: `error-${Date.now()}`,
          role: 'assistant',
          content: toUserFacingAgentError(errorText),
        } as Message);
        toast.error('AG-UI returned an error from backend.');
      },
      onRunErrorEvent: ({ event }) => {
        const errorText = typeof (event as any)?.message === 'string' ? (event as any).message.trim() : '';
        if (!errorText) return;

        const runId = typeof (event as any)?.runId === 'string' ? (event as any).runId : 'unknown-run';
        const signature = `${runId}::${errorText}`;
        if (handledRunErrorSignaturesRef.current.has(signature)) return;
        handledRunErrorSignaturesRef.current.add(signature);

        agent.addMessage({
          id: `error-${Date.now()}`,
          role: 'assistant',
          content: toUserFacingAgentError(errorText),
        } as Message);
        toast.error('Agent run failed.');
      },
    });

    return () => {
      unsubscribe();
      agentRef.current = null;
    };
  }, [handleRefreshAction, token, user]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const sendMessage = useCallback(
    async (text: string = input) => {
      const agent = agentRef.current;
      const trimmed = text.trim();

      if (!agent || !trimmed || loading) return;

      const message: Message = {
        id: `msg-${Date.now()}`,
        role: 'user',
        content: trimmed,
      } as Message;

      agent.addMessage(message);
      setInput('');
      setLoading(true);

      try {
        const beforeSignature = getLastAssistantSignature(agent.messages as Message[]);
        const runResult = await runAgentWithRetry(agent, { runId: `run-${Date.now()}` });
        const afterSignature = getLastAssistantSignature(agent.messages as Message[]);

        if (beforeSignature === afterSignature) {
          const recovered = extractAssistantFromRunResult(runResult.result);
          const lastAssistantContent = getLastAssistantContent(agent.messages as Message[]);

          if (recovered && recovered !== lastAssistantContent) {
            const recoveredMessage: Message = {
              id: `result-${Date.now()}`,
              role: 'assistant',
              content: recovered,
            } as Message;
            agent.addMessage(recoveredMessage);
          }
        }
      } catch (error) {
        const classified = error && typeof error === 'object' && 'kind' in error
          ? (error as ClassifiedError)
          : classifyAgentError(error);
        console.error('Agent run failed:', classified.kind, classified.message);
        toast.error(getErrorToastMessage(classified));
      } finally {
        setLoading(false);
      }
    },
    [input, loading]
  );

  const handleWeeklyReview = useCallback(() => {
    void sendMessage('Start Weekly Review');
    toast('Starting Weekly Review...');
  }, [sendMessage]);

  const handleAddPlan = useCallback(
    async (plan: PlanPayload) => {
      if (Array.isArray(plan)) {
        await addHabits(plan as Partial<HabitRequest>[]);
      } else {
        const goalRequest: GoalRequest = {
          name: plan.goalName,
          description: plan.description ?? '',
          startDate: plan.startDate,
          endDate: plan.endDate,
          status: 'IN_PROGRESS',
          habits: plan.habits as HabitRequest[],
        };
        await createGoal(goalRequest);
      }

      toast.success('Challenge accepted! Good luck!');
      onHabitsAdded?.();
    },
    [addHabits, createGoal, onHabitsAdded]
  );

  const handleStartSmall = useCallback(
    async (plan: PlanPayload) => {
      const habits = Array.isArray(plan) ? plan : plan.habits;
      if (habits.length === 0) return;

      const firstHabit = habits[0];
      const starterHabit: Partial<HabitRequest> = {
        ...firstHabit,
        name: firstHabit.twoMinuteVersion ? `${firstHabit.name} (2-Min Start)` : firstHabit.name,
      };

      await addHabits([starterHabit]);
      toast.success('Great choice! Focusing on one small step is the key.');
      onHabitsAdded?.();
    },
    [addHabits, onHabitsAdded]
  );

  const renderReplies = useCallback(
    (replies: string[]) => {
      if (replies.length === 0) return null;

      return (
        <div className="mt-3 flex flex-wrap gap-2">
          {replies.map((reply) => (
            <button
              key={reply}
              onClick={() => void sendMessage(reply)}
              className="rounded-full border border-indigo-200 bg-white px-3 py-1.5 text-xs text-indigo-600 shadow-sm transition-colors hover:bg-indigo-50"
            >
              {reply}
            </button>
          ))}
        </div>
      );
    },
    [sendMessage]
  );

  const renderMessageContent = useCallback(
    (content: string) => {
      const { displayContent, replies, plan, planBlock } = parseDisplayContent(content);

      if (!plan || !planBlock) {
        return (
          <div>
            <div className="prose prose-sm max-w-none dark:prose-invert">
              <ReactMarkdown>{displayContent}</ReactMarkdown>
            </div>
            {renderReplies(replies)}
          </div>
        );
      }

      const planIndex = displayContent.indexOf(planBlock);
      const textBefore = planIndex >= 0 ? displayContent.slice(0, planIndex).trim() : displayContent.trim();
      const textAfter =
        planIndex >= 0 ? displayContent.slice(planIndex + planBlock.length).trim() : '';
      const habits = Array.isArray(plan) ? plan : plan.habits;
      const planTitle = Array.isArray(plan) ? 'Suggested Plan' : `Plan: ${plan.goalName}`;
      const planDescription = Array.isArray(plan) ? '' : (plan.description ?? '');

      return (
        <div>
          <div className="prose prose-sm max-w-none dark:prose-invert">
            <ReactMarkdown>{textBefore}</ReactMarkdown>
          </div>

          <div className="my-3 rounded-lg border border-indigo-100 bg-white p-3 shadow-sm">
            <h4 className="mb-2 flex items-center text-sm font-bold text-indigo-700">
              <Zap size={14} className="mr-1" />
              {planTitle}
            </h4>

            {planDescription && <p className="mb-3 text-xs italic text-gray-600">"{planDescription}"</p>}

            <ul className="mb-4 space-y-3 text-sm">
              {habits.map((habit, index) => (
                <li key={`${habit.name}-${index}`} className="border-b border-gray-100 pb-2 last:border-0">
                  <div className="flex items-start">
                    <span className="mr-2 mt-1 text-xs text-gray-400">{index + 1}.</span>
                    <div>
                      <strong className="block text-gray-800">{habit.name}</strong>
                      {habit.twoMinuteVersion && (
                        <div className="mt-0.5 w-fit rounded bg-green-50 px-1.5 py-0.5 text-xs font-medium text-green-600">
                          2-Min Rule: {habit.twoMinuteVersion}
                        </div>
                      )}
                      {habit.cueImplementationIntention && (
                        <div className="mt-1 border-l-2 border-gray-200 pl-1 text-xs text-gray-500">
                          {habit.cueImplementationIntention}
                        </div>
                      )}
                    </div>
                  </div>
                </li>
              ))}
            </ul>

            <div className="grid grid-cols-1 gap-2">
              <button
                onClick={() => void handleStartSmall(plan)}
                className="flex w-full items-center justify-center rounded-md bg-green-600 py-2 text-xs font-medium text-white shadow-sm transition-colors hover:bg-green-700"
              >
                <Zap size={14} className="mr-1" />
                Start Small (Recommended)
              </button>
              <button
                onClick={() => void handleAddPlan(plan)}
                className="flex w-full items-center justify-center rounded-md border border-gray-300 bg-white py-2 text-xs text-gray-500 transition-colors hover:bg-gray-50 hover:text-gray-700"
              >
                <Layers size={14} className="mr-1" />
                {Array.isArray(plan) ? 'Add All (Challenge Mode)' : 'Add Full Plan (Challenge Mode)'}
              </button>
            </div>
          </div>

          <div className="prose prose-sm max-w-none dark:prose-invert">
            <ReactMarkdown>{textAfter}</ReactMarkdown>
          </div>

          {renderReplies(replies)}
        </div>
      );
    },
    [handleAddPlan, handleStartSmall, renderReplies]
  );

  const shouldShowStarterGuide = useMemo(
    () => !messages.some((message) => message.role === 'user'),
    [messages]
  );

  return (
    <div className="fixed bottom-24 right-6 z-40 flex h-[500px] max-h-[70vh] w-96 max-w-[calc(100vw-3rem)] flex-col overflow-hidden rounded-lg border border-gray-200 bg-white shadow-2xl">
      <div className="flex shrink-0 items-center justify-between border-b bg-indigo-600 p-4 text-white">
        <h3 className="flex items-center gap-2 font-bold">
          <Zap size={18} />
          AI Coach
        </h3>
        <div className="flex items-center gap-2">
          <button
            onClick={handleWeeklyReview}
            className="rounded p-1 text-indigo-100 transition-colors hover:bg-indigo-700 hover:text-white"
            title="Start Weekly Review"
          >
            <Calendar size={18} />
          </button>
          <button onClick={onClose} className="rounded p-1 hover:bg-indigo-700">
            <X size={20} />
          </button>
        </div>
      </div>

      {recentMoods.length > 0 && (
        <div className="flex shrink-0 items-center gap-2 border-b border-indigo-100 bg-indigo-50 px-4 py-2 text-xs text-indigo-800">
          <span className="font-semibold">Context:</span>
          <div className="no-scrollbar flex gap-1 overflow-x-auto">
            {recentMoods.slice(0, 3).map((mood, index) => (
              <span
                key={`${mood.moodType}-${index}`}
                className="whitespace-nowrap rounded border border-indigo-200 bg-white px-2 py-0.5"
              >
                {mood.moodType}
              </span>
            ))}
            {recentMoods.length > 3 && <span className="text-gray-400">+{recentMoods.length - 3}</span>}
          </div>
        </div>
      )}

      <div className="flex-1 space-y-4 overflow-y-auto bg-slate-50 p-4">
        {shouldShowStarterGuide && (
          <div className="rounded-lg border border-indigo-100 bg-gradient-to-r from-indigo-50 to-blue-50 p-3">
            <h4 className="text-xs font-semibold text-indigo-800">Need a starting point? Pick one:</h4>
            <div className="mt-2 flex flex-wrap gap-2">
              {STARTER_PROMPTS.map((prompt) => (
                <button
                  key={prompt}
                  onClick={() => void sendMessage(prompt)}
                  disabled={loading}
                  className="rounded-full border border-indigo-200 bg-white px-2.5 py-1 text-xs text-indigo-700 transition-colors hover:bg-indigo-100 disabled:opacity-60"
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.length === 0 && loading && (
          <div className="mt-10 flex flex-col items-center justify-center space-y-2">
            <TypingIndicator />
            <p className="text-sm text-gray-400">Checking your habits...</p>
          </div>
        )}

        {messages.map((message) => (
          <div key={message.id} className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div
              className={`max-w-[85%] rounded-lg p-3 ${
                message.role === 'user' ? 'bg-indigo-100 text-indigo-900' : 'bg-gray-100 text-gray-800'
              } ${message.role === 'ai' ? 'shadow-sm' : ''}`}
            >
              {message.role === 'ai' ? renderMessageContent(message.content) : message.content}
            </div>
          </div>
        ))}

        {loading && messages.length > 0 && (
          <div className="flex justify-start">
            <TypingIndicator />
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      <div className="flex gap-2 border-t p-4">
        <input
          className="flex-1 rounded border p-2 outline-none focus:ring-2 focus:ring-indigo-500"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              void sendMessage();
            }
          }}
          placeholder={shouldShowStarterGuide ? 'For example: where should I start?' : 'Ask a question...'}
        />
        <button
          onClick={() => void sendMessage()}
          className="rounded bg-indigo-600 p-2 text-white hover:bg-indigo-700 disabled:opacity-50"
          disabled={loading || !input.trim()}
        >
          <Send size={20} />
        </button>
      </div>
    </div>
  );
};

export default ChatInterface;
