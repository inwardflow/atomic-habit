import React from 'react';
import { Brain, Wrench, Database, MessageSquare, Loader2, CheckCircle2, AlertCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';

export type AgentPhase =
  | 'idle'
  | 'connecting'
  | 'thinking'
  | 'tool_calling'
  | 'reading_memory'
  | 'generating'
  | 'done'
  | 'error';

export interface ToolCallActivity {
  name: string;
  status: 'running' | 'done';
  args?: Record<string, unknown>;
}

export interface AgentActivity {
  phase: AgentPhase;
  toolCalls: ToolCallActivity[];
  elapsedMs: number;
  /** Optional text content received so far (for streaming preview) */
  partialContent?: string;
}

const getToolDisplayName = (name: string, t: TFunction<"coach", undefined>): string => {
  const key = `agent_activity.tools.${name}`;
  // @ts-expect-error Dynamic keys are not strictly typed
  const translated = t(key) as string;
  // If translation returns the key itself (meaning missing), fallback to a formatted name
  return translated !== key ? translated : name.replace(/_/g, ' ');
};

const getPhaseConfig = (phase: AgentPhase, t: TFunction<"coach", undefined>) => {
  const configs: Record<AgentPhase, { icon: React.ReactNode; label: string; color: string }> = {
    idle: { icon: null, label: '', color: '' },
    connecting: {
      icon: <Loader2 size={14} className="animate-spin" />,
      label: t('agent_activity.phases.connecting'),
      color: 'text-gray-500',
    },
    thinking: {
      icon: <Brain size={14} className="animate-pulse" />,
      label: t('agent_activity.phases.thinking'),
      color: 'text-indigo-600',
    },
    tool_calling: {
      icon: <Wrench size={14} className="animate-bounce" />,
      label: t('agent_activity.phases.using_tools'),
      color: 'text-amber-600',
    },
    reading_memory: {
      icon: <Database size={14} className="animate-pulse" />,
      label: t('agent_activity.phases.reading_memory'),
      color: 'text-purple-600',
    },
    generating: {
      icon: <MessageSquare size={14} className="animate-pulse" />,
      label: t('agent_activity.phases.generating'),
      color: 'text-green-600',
    },
    done: {
      icon: <CheckCircle2 size={14} />,
      label: t('agent_activity.phases.done'),
      color: 'text-green-600',
    },
    error: {
      icon: <AlertCircle size={14} />,
      label: t('agent_activity.phases.error'),
      color: 'text-red-500',
    },
  };
  return configs[phase];
};

const formatElapsed = (ms: number): string => {
  if (ms < 1000) return '';
  const seconds = Math.floor(ms / 1000);
  return `${seconds}s`;
};

interface AgentActivityIndicatorProps {
  activity: AgentActivity;
  /** Compact mode for ChatInterface (sidebar chat), full mode for CoachPage */
  compact?: boolean;
}

const AgentActivityIndicator: React.FC<AgentActivityIndicatorProps> = ({
  activity,
  compact = false,
}) => {
  const { t } = useTranslation('coach');
  const { phase, toolCalls, elapsedMs } = activity;

  if (phase === 'idle' || phase === 'done') return null;

  const config = getPhaseConfig(phase, t);
  const elapsed = formatElapsed(elapsedMs);

  if (compact) {
    return (
      <div className="flex items-center gap-2 rounded-lg bg-gray-50 px-3 py-2 text-xs">
        <span className={config.color}>{config.icon}</span>
        <span className={`font-medium ${config.color}`}>{config.label}</span>
        {toolCalls.length > 0 && (
          <span className="text-gray-400">
            — {getToolDisplayName(toolCalls[toolCalls.length - 1].name, t)}
          </span>
        )}
        {elapsed && <span className="ml-auto text-gray-300">{elapsed}</span>}
      </div>
    );
  }

  // Full mode for CoachPage
  return (
    <div className="rounded-lg border border-gray-100 bg-white p-3 shadow-sm">
      {/* Phase header */}
      <div className="flex items-center gap-2">
        <span className={config.color}>{config.icon}</span>
        <span className={`text-sm font-medium ${config.color}`}>{config.label}</span>
        {elapsed && <span className="ml-auto text-xs text-gray-300">{elapsed}</span>}
      </div>

      {/* Tool call timeline */}
      {toolCalls.length > 0 && (
        <div className="mt-2 ml-1 border-l-2 border-gray-100 pl-3 space-y-1.5">
          {toolCalls.map((tc, idx) => (
            <div key={`${tc.name}-${idx}`} className="flex items-center gap-2 text-xs">
              {tc.status === 'running' ? (
                <Loader2 size={10} className="animate-spin text-amber-500 shrink-0" />
              ) : (
                <CheckCircle2 size={10} className="text-green-500 shrink-0" />
              )}
              <span className={tc.status === 'running' ? 'text-amber-700 font-medium' : 'text-gray-500'}>
                {getToolDisplayName(tc.name, t)}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default AgentActivityIndicator;
