import React, { useCallback, useEffect, useMemo, useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { HttpAgent } from '@ag-ui/client';
import { Send, User, Bot, Loader2, CalendarDays, History, Lightbulb, ArrowLeft, ChevronDown } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import ReactMarkdown from 'react-markdown';
import JSON5 from 'json5';
import toast from 'react-hot-toast';
import { generateWeeklyReview, getCoachMemoryHits, getGreeting, getChatHistory, getWeeklyReviews } from '../api/coach';
import { BACKEND_URL, refreshAccessToken } from '../api/axios';
import { runAgentWithRetry, classifyAgentError, getErrorToastMessage } from '../utils/agentRetry';
import type { ClassifiedError } from '../utils/agentRetry';
import { useAgentActivity } from '../hooks/useAgentActivity';
import AgentActivityIndicator from '../components/AgentActivityIndicator';
import type { CoachMemoryHitsResponse, WeeklyReviewRecord } from '../api/coach';
import WeeklyReviewCard from '../components/WeeklyReviewCard';
import DailyFocusCard from '../components/DailyFocusCard';
import CoachMemorySidebar from '../components/CoachMemorySidebar';
import { useHabits } from '../hooks/useHabits';

interface Message {
    id: string;
    role: string;
    content: string;
    timestamp?: string;
}

interface HabitPlanItem {
    name: string;
    twoMinuteVersion: string;
    cueImplementationIntention: string;
    cueHabitStack: string;
}

interface ParsedHabitPlan {
    title?: string;
    description?: string;
    habits: HabitPlanItem[];
}

interface ToolCallData {
    name: string;
    arguments: Record<string, unknown>;
}

interface ParsedWeeklyReview {
    stats: {
      totalCompleted: number;
      currentStreak: number;
      bestStreak?: number;
    };
    highlights: string[];
    suggestion: string;
}

const parseToolArguments = (rawArgs: unknown): Record<string, unknown> => {
  if (typeof rawArgs === 'string') {
    try {
      const parsed = JSON.parse(rawArgs);
      if (parsed && typeof parsed === 'object') {
        return parsed as Record<string, unknown>;
      }
    } catch {
      return { raw: rawArgs };
    }
    return { raw: rawArgs };
  }

  if (rawArgs && typeof rawArgs === 'object') {
    return rawArgs as Record<string, unknown>;
  }

  return {};
};

/**
 * Tool calls that exist purely to render a card in the chat. All other tools (status lookups,
 * mood logging, memory writes...) are internal actions and are shown only in the activity timeline.
 */
const VISUAL_TOOLS = new Set(['present_daily_focus', 'present_weekly_review']);

/** Only user and assistant turns are shown; tool results and system messages are internal. */
const isDisplayableRole = (role: unknown): boolean => {
  const normalized = typeof role === 'string' ? role.toLowerCase() : '';
  return normalized === 'user' || normalized === 'assistant' || normalized === 'ai';
};

const buildToolCallBlocks = (toolCalls: unknown): string => {
  if (!Array.isArray(toolCalls) || toolCalls.length === 0) {
    return '';
  }

  const blocks = toolCalls
    .map((call) => {
      if (!call || typeof call !== 'object') return '';
      const functionObject = (call as Record<string, unknown>).function as Record<string, unknown> | undefined;
      if (!functionObject) return '';
      const name = typeof functionObject.name === 'string' ? functionObject.name : '';
      if (!VISUAL_TOOLS.has(name)) return '';

      const payload = {
        name,
        arguments: parseToolArguments(functionObject.arguments),
      };

      return `\`\`\`json\n${JSON.stringify(payload, null, 2)}\n\`\`\``;
    })
    .filter(Boolean);

  return blocks.join('\n\n');
};

const buildRenderableContent = (message: Record<string, unknown>): string => {
  const textContent =
    typeof message?.content === 'string'
      ? message.content
      : message?.content == null
        ? ''
        : JSON.stringify(message.content);

  const toolBlocks = buildToolCallBlocks(message?.toolCalls);
  if (!toolBlocks) {
    return textContent;
  }

  if (textContent.trim()) {
    return `${textContent}\n\n${toolBlocks}`;
  }
  return toolBlocks;
};

const isAssistantRole = (role: unknown): boolean => {
  const normalized = typeof role === 'string' ? role.toLowerCase() : '';
  return normalized === 'assistant' || normalized === 'ai';
};

const getLastAssistantContent = (messages: Message[]): string => {
  const lastAssistant = [...messages].reverse().find((msg) => isAssistantRole(msg.role));
  if (!lastAssistant) return '';
  return buildRenderableContent(lastAssistant as unknown as Record<string, unknown>).trim();
};

const getLastAssistantSignature = (messages: Message[]): string => {
  const lastAssistant = [...messages].reverse().find((msg) => isAssistantRole(msg.role));
  if (!lastAssistant) return '';
  const id = lastAssistant.id;
  const content = buildRenderableContent(lastAssistant as unknown as Record<string, unknown>).trim();
  return `${id}::${content}`;
};

const extractAssistantFromMessagesArray = (candidate: unknown): string | null => {
  if (!Array.isArray(candidate)) return null;

  for (let i = candidate.length - 1; i >= 0; i -= 1) {
    const item = candidate[i];
    if (!item || typeof item !== 'object') continue;
    const role = (item as Record<string, unknown>).role;
    if (!isAssistantRole(role)) continue;

    const content = buildRenderableContent(item).trim();
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

  if (!result || typeof result !== 'object') return null;
  const record = result as Record<string, unknown>;

  const directKeys = ['response', 'content', 'text', 'output', 'message'] as const;
  for (const key of directKeys) {
    const value = record[key];
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
  }

  const fromMessages =
    extractAssistantFromMessagesArray(record.messages) ??
    (record.output && typeof record.output === 'object'
      ? extractAssistantFromMessagesArray((record.output as Record<string, unknown>).messages)
      : null) ??
    (record.data && typeof record.data === 'object'
      ? extractAssistantFromMessagesArray((record.data as Record<string, unknown>).messages)
      : null);
  if (fromMessages) return fromMessages;

  const nestedKeys = ['result', 'data', 'output'] as const;
  for (const key of nestedKeys) {
    const nested = record[key];
    const extracted = extractAssistantFromRunResult(nested, depth + 1);
    if (extracted) return extracted;
  }

  return null;
};

const CoachPage = () => {
  const { t } = useTranslation('coach');
  // Persisted history / greeting (REST) and the live AG-UI conversation are kept apart: the agent
  // reports its full message list on every change, which would otherwise wipe the history.
  const [historyMessages, setHistoryMessages] = useState<Message[]>([]);
  const [agentMessages, setAgentMessages] = useState<Message[]>([]);
  const messages = useMemo(() => [...historyMessages, ...agentMessages], [historyMessages, agentMessages]);
  const [input, setInput] = useState('');
  const [agent, setAgent] = useState<HttpAgent | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isApplyingPlan, setIsApplyingPlan] = useState(false);
  const [weeklyReviewHistory, setWeeklyReviewHistory] = useState<WeeklyReviewRecord[]>([]);
  const [selectedReviewId, setSelectedReviewId] = useState<number | null>(null);
  // Collapsed by default so past reviews don't squeeze the conversation.
  const [showReviewHistory, setShowReviewHistory] = useState(false);
  const [memoryHitsByMessage, setMemoryHitsByMessage] = useState<Record<string, string[]>>({});
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const lastAssistantMessageIdRef = useRef<string>('');
  const handledRunErrorSignaturesRef = useRef<Set<string>>(new Set());
  const user = useAuthStore((state) => state.user);
  const token = useAuthStore((state) => state.token);
  const tokenRef = useRef(token);
  const initialLoadForUserRef = useRef<number | null>(null);
  const { addHabits } = useHabits();

  // Agent activity tracking
  const { activity, processEvent, markRunStart, markRunError, reset: resetActivity } = useAgentActivity();

  const toUserFacingAgentError = useCallback((errorText: string): string => {
    const normalized = errorText.toLowerCase();
    if (normalized.includes('invalid token') || normalized.includes('401')) {
      return t('errors.token');
    }
    return t('errors.agui', { error: errorText });
  }, [t]);

  const promptsData = t('starter.prompts', { returnObjects: true });
  const coldStartPrompts = Array.isArray(promptsData) ? promptsData as string[] : [];

  const normalizePlanItem = (habit: Record<string, unknown>, index: number): HabitPlanItem => {
    const fallbackName = t('defaults.habit_name', { index: index + 1 });
    const name = typeof habit?.name === 'string' && habit.name.trim() ? habit.name.trim() : fallbackName;
    const twoMinuteVersion = typeof habit?.twoMinuteVersion === 'string' && habit.twoMinuteVersion.trim()
        ? habit.twoMinuteVersion.trim()
        : t('defaults.two_minute', { name });
    const cueImplementationIntention = typeof habit?.cueImplementationIntention === 'string' && habit.cueImplementationIntention.trim()
        ? habit.cueImplementationIntention.trim()
        : t('defaults.intention');
    const cueHabitStack = typeof habit?.cueHabitStack === 'string' && habit.cueHabitStack.trim()
        ? habit.cueHabitStack.trim()
        : t('defaults.stack');

    return {
      name,
      twoMinuteVersion,
      cueImplementationIntention,
      cueHabitStack,
    };
  };

  const parseHabitPlan = (candidate: unknown): ParsedHabitPlan | null => {
    if (!candidate) return null;
    const obj = candidate as Record<string, unknown>;

    let rawHabits: Record<string, unknown>[] | null = null;
    let title: string | undefined;
    let description: string | undefined;

    if (Array.isArray(candidate)) {
      rawHabits = candidate as Record<string, unknown>[];
    } else if (typeof candidate === 'object' && Array.isArray(obj.habits)) {
      rawHabits = obj.habits as Record<string, unknown>[];
      if (typeof obj.goalName === 'string') title = obj.goalName;
      if (typeof obj.description === 'string') description = obj.description;
    }

    if (!rawHabits || rawHabits.length === 0) return null;

    const habits = rawHabits.map(normalizePlanItem);
    return habits.length > 0 ? { title, description, habits } : null;
  };

  const parseWeeklyReview = (candidate: unknown): ParsedWeeklyReview | null => {
    if (!candidate || typeof candidate !== 'object') return null;
    const obj = candidate as Record<string, unknown>;

    const statsSource = (obj.stats && typeof obj.stats === 'object' ? obj.stats : obj) as Record<string, unknown>;
    const totalCompletedRaw = statsSource.totalCompleted;
    const currentStreakRaw = statsSource.currentStreak;
    const bestStreakRaw = statsSource.bestStreak;

    const totalCompleted = Number(totalCompletedRaw);
    const currentStreak = Number(currentStreakRaw);
    const bestStreak = Number(bestStreakRaw);

    if (!Number.isFinite(totalCompleted) || !Number.isFinite(currentStreak)) {
      return null;
    }

    const highlights = Array.isArray(obj.highlights)
      ? (obj.highlights as unknown[]).filter((h: unknown) => typeof h === 'string') as string[]
      : [];
    const suggestion = typeof obj.suggestion === 'string' && obj.suggestion.trim()
      ? obj.suggestion.trim()
      : t('card.weekly.default_suggestion');

    const parsed: ParsedWeeklyReview = {
      stats: {
        totalCompleted,
        currentStreak,
      },
      highlights,
      suggestion,
    };

    if (Number.isFinite(bestStreak)) {
      parsed.stats.bestStreak = bestStreak;
    }

    return parsed;
  };

  const handleAddAllToHabits = async (plan: ParsedHabitPlan) => {
    if (isApplyingPlan) return;
    setIsApplyingPlan(true);
    try {
      await addHabits(plan.habits);
    } catch {
      toast.error(t('errors.add_plan_failed'));
    } finally {
      setIsApplyingPlan(false);
    }
  };

  const handleStartSmall = async (plan: ParsedHabitPlan) => {
    if (isApplyingPlan) return;
    if (plan.habits.length === 0) return;

    setIsApplyingPlan(true);
    try {
      await addHabits([plan.habits[0]]);
      toast.success(t('success.starter_added'));
    } catch {
      toast.error(t('errors.add_starter_failed'));
    } finally {
      setIsApplyingPlan(false);
    }
  };

  const parseMessageContent = (content: string) => {
    let text = content;
    let suggestions: string[] = [];
    let toolCall: ToolCallData | null = null;
    let plan: ParsedHabitPlan | null = null;
    let weeklyReview: ParsedWeeklyReview | null = null;

    // Fenced blocks may carry a language tag (```json, ```replies, ```action) and may be written on
    // one line, e.g. ```replies ["A", "B"] ```.
    const codeBlockRegex = /```([a-zA-Z_-]*)[ \t]*([\s\S]*?)```/g;
    let match;
    const codeBlocks = [];
    while ((match = codeBlockRegex.exec(content)) !== null) {
        codeBlocks.push({ fullMatch: match[0], lang: match[1].toLowerCase(), inner: match[2].trim() });
    }

    for (const block of codeBlocks) {
        let jsonStr = block.inner;
        while (jsonStr.startsWith('{{') && jsonStr.endsWith('}}')) {
             jsonStr = jsonStr.substring(1, jsonStr.length - 1);
        }

        if (block.lang === 'replies' || block.lang === 'action') {
            text = text.replace(block.fullMatch, '').trim();
            if (block.lang === 'replies') {
                try {
                    const parsed = JSON5.parse(jsonStr);
                    if (Array.isArray(parsed)) {
                        suggestions = parsed.filter((i: unknown) => typeof i === 'string');
                    }
                } catch { /* malformed replies block: drop it */ }
            }
            continue;
        }
        if (block.lang && block.lang !== 'json' && block.lang !== 'json5') {
            continue; // regular code sample: leave it in the text
        }

        try {
            const parsed = JSON5.parse(jsonStr);
            
            if (parsed.name && parsed.arguments && !toolCall) {
                toolCall = parsed as ToolCallData;
                text = text.replace(block.fullMatch, '').trim();
                continue; 
            }

            if (parsed.replies && Array.isArray(parsed.replies)) {
                suggestions = parsed.replies;
                if (!toolCall || toolCall !== parsed) {
                     text = text.replace(block.fullMatch, '').trim();
                }
            }
            
            if (Array.isArray(parsed) && parsed.every(i => typeof i === 'string') && suggestions.length === 0) {
                 suggestions = parsed;
                 text = text.replace(block.fullMatch, '').trim();
            }

            const extractedPlan = parseHabitPlan(parsed);
            if (extractedPlan && !plan) {
                plan = extractedPlan;
                text = text.replace(block.fullMatch, '').trim();
            }

            const extractedWeeklyReview = parseWeeklyReview(parsed);
            if (extractedWeeklyReview && !weeklyReview) {
                weeklyReview = extractedWeeklyReview;
                text = text.replace(block.fullMatch, '').trim();
            }

        } catch {
            // Not a valid JSON block, ignore
        }
    }

    // Fallback: match bare `replies ["..."]` lines
    if (suggestions.length === 0) {
      text = text.replace(
        /^\s*replies\s+(\[.*])\s*$/gim,
        (_match: string, jsonPart: string) => {
          try {
            const parsed = JSON5.parse(jsonPart);
            if (Array.isArray(parsed) && parsed.every((i: unknown) => typeof i === 'string')) {
              suggestions = parsed;
              return '';
            }
          } catch { /* not valid JSON */ }
          return _match;
        }
      );
      text = text.trim();
    }

    return { text, suggestions, toolCall, plan, weeklyReview };
  };

  const loadMemoryHitsForMessage = useCallback(async (messageId: string) => {
    if (!messageId) return;
    try {
      const data: CoachMemoryHitsResponse = await getCoachMemoryHits();
      const hits = Array.isArray(data.hits) ? data.hits : [];
      setMemoryHitsByMessage((prev) => ({
        ...prev,
        [messageId]: hits,
      }));
    } catch (error) {
      console.error('Failed to load memory hits', error);
    }
  }, []);

  useEffect(() => {
    // Initialize Agent
    // The server pins threadId to the authenticated user; the value sent here is only a hint.
    const newAgent = new HttpAgent({
      url: `${BACKEND_URL}/agui/run`,
      threadId: 'user-' + (user ? user.id : 'anonymous'),
      headers: tokenRef.current ? { Authorization: `Bearer ${tokenRef.current}` } : {},
    });

    const { unsubscribe } = newAgent.subscribe({
      onMessagesChanged: ({ messages: msgs }) => {
        const formattedMessages: Message[] = (msgs as unknown as Record<string, unknown>[])
          .filter((m) => isDisplayableRole(m.role))
          .map((m) => ({
            id: typeof m.id === 'string' ? m.id : `msg-${Date.now()}-${Math.random()}`,
            role: typeof m.role === 'string' ? m.role.toLowerCase() : 'assistant',
            content: buildRenderableContent(m),
            timestamp: m.timestamp as string | undefined
          }))
          .filter((m) => m.content.trim().length > 0);
        setAgentMessages(formattedMessages);

        const lastAssistant = [...formattedMessages]
          .reverse()
          .find((m) => {
            const role = String(m.role || '').toLowerCase();
            return role === 'assistant' || role === 'ai';
          });
        if (lastAssistant && lastAssistant.id !== lastAssistantMessageIdRef.current) {
          lastAssistantMessageIdRef.current = lastAssistant.id;
          void loadMemoryHitsForMessage(lastAssistant.id);
        }
      },
      onEvent: ({ event }) => {
        // Drive the activity timeline (thinking / tool calls / generating) from protocol events.
        processEvent(event as unknown as Record<string, unknown>);
      },
      onRawEvent: ({ event }) => {
        const evtRaw = event as unknown as Record<string, unknown>;
        const rawEvent = evtRaw?.rawEvent as Record<string, unknown> | undefined;
        const errorText = typeof rawEvent?.error === 'string' ? rawEvent.error.trim() : '';
        if (!errorText) return;

        const runId = typeof evtRaw?.runId === 'string' ? evtRaw.runId as string : 'unknown-run';
        const signature = `${runId}::${errorText}`;
        if (handledRunErrorSignaturesRef.current.has(signature)) return;
        handledRunErrorSignaturesRef.current.add(signature);

        newAgent.addMessage({
          id: `error-${Date.now()}`,
          role: 'assistant',
          content: toUserFacingAgentError(errorText),
        });
        toast.error(t('errors.agui', { error: 'Backend error' }));
      },
      onRunErrorEvent: ({ event }) => {
        const errEvt = event as unknown as Record<string, unknown>;
        const errorText = typeof errEvt?.message === 'string' ? (errEvt.message as string).trim() : '';
        if (!errorText) return;

        const runId = typeof errEvt?.runId === 'string' ? errEvt.runId as string : 'unknown-run';
        const signature = `${runId}::${errorText}`;
        if (handledRunErrorSignaturesRef.current.has(signature)) return;
        handledRunErrorSignaturesRef.current.add(signature);

        newAgent.addMessage({
          id: `error-${Date.now()}`,
          role: 'assistant',
          content: toUserFacingAgentError(errorText),
        });
        toast.error(t('errors.run_failed'));
      },
    });

    setAgent(newAgent);
    setAgentMessages([]);

    return () => {
      unsubscribe();
      resetActivity();
    };
    // Recreate only for a different user; token refreshes just update headers (below), so the
    // live conversation survives the 15-minute access-token rotation.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id]);

  useEffect(() => {
    tokenRef.current = token;
    if (agent) {
      agent.headers = token ? { Authorization: `Bearer ${token}` } : {};
    }
  }, [agent, token]);

  // Load chat history or greeting
  useEffect(() => {
    const loadInitialData = async () => {
        if (!user) {
            setMemoryHitsByMessage({});
            setHistoryMessages([]);
            initialLoadForUserRef.current = null;
            return;
        }
        // StrictMode runs effects twice; the greeting is generated and persisted server-side, so a
        // second request would store a duplicate greeting.
        if (initialLoadForUserRef.current === user.id) return;
        initialLoadForUserRef.current = user.id ?? null;

        try {
            const history = await getChatHistory();
            if (history && history.length > 0) {
                const formattedHistory: Message[] = history.map((msg, idx) => ({
                    id: `hist-${idx}`,
                    role: msg.role.toLowerCase(),
                    content: msg.content,
                    timestamp: msg.timestamp
                }));
                setHistoryMessages(formattedHistory);
            } else {
                // Cold start: Fetch greeting
                setIsLoading(true);
                try {
                    const data = await getGreeting();
                    const greetingMsg: Message = {
                        id: `greeting-${Date.now()}`,
                        role: 'assistant',
                        content: data.response
                    };
                    setHistoryMessages([greetingMsg]);
                } catch (e) {
                    console.error("Failed to fetch greeting", e);
                    const fallbackGreeting: Message = {
                        id: `fallback-greeting-${Date.now()}`,
                        role: 'assistant',
                        content: t('starter.title') + "\n" + t('starter.subtitle')
                    };
                    setHistoryMessages([fallbackGreeting]);
                } finally {
                    setIsLoading(false);
                }
            }
        } catch (error) {
            console.error("Failed to load history", error);
        }
    };

    loadInitialData();
    // Reload only when the signed-in user changes, not on every access-token refresh.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, activity]);

  const loadWeeklyReviewHistory = async () => {
    try {
      const reviews = await getWeeklyReviews(5);
      setWeeklyReviewHistory(reviews);
    } catch (error) {
      console.error('Failed to load weekly review history', error);
    }
  };

  useEffect(() => {
    if (!user) return;
    loadWeeklyReviewHistory();
  }, [user]);

  const handleSend = async (textOverride?: string) => {
    const textToSend = textOverride || input;
    if (!textToSend.trim() || !agent || isLoading) return;

    const userMsg = {
      id: `msg-${Date.now()}`,
      role: 'user' as const,
      content: textToSend,
    };

    agent.addMessage(userMsg);
    if (!textOverride) setInput('');
    setIsLoading(true);
    markRunStart();
    
    try {
      const beforeSignature = getLastAssistantSignature(agent.messages as Message[]);
      let runResult;
      try {
        runResult = await runAgentWithRetry(agent, { runId: `run-${Date.now()}` });
      } catch (error) {
        const kind = error && typeof error === 'object' && 'kind' in error
          ? (error as ClassifiedError).kind
          : classifyAgentError(error).kind;
        if (kind !== 'auth') throw error;
        // Access token expired mid-session: refresh once and retry the run.
        const fresh = await refreshAccessToken();
        agent.headers = { Authorization: `Bearer ${fresh}` };
        runResult = await runAgentWithRetry(agent, { runId: `run-${Date.now()}` });
      }
      const afterSignature = getLastAssistantSignature(agent.messages as Message[]);

      if (beforeSignature === afterSignature) {
        const recovered = extractAssistantFromRunResult(runResult.result);
        const lastAssistantContent = getLastAssistantContent(agent.messages as Message[]);

        if (recovered && recovered !== lastAssistantContent) {
          agent.addMessage({
            id: `result-${Date.now()}`,
            role: 'assistant' as const,
            content: recovered,
          });
        }
      }
    } catch (error) {
      const classified = error && typeof error === 'object' && 'kind' in error
        ? (error as ClassifiedError)
        : classifyAgentError(error);
      console.error('Agent run failed:', classified.kind, classified.message);
      toast.error(getErrorToastMessage(classified));
      markRunError();
    } finally {
      setIsLoading(false);
    }
  };

  const handleWeeklyReview = async () => {
    if (isLoading) return;

    const promptMessage: Message = {
      id: `msg-${Date.now()}`,
      role: 'user',
      content: t('actions.start_weekly_review'),
      timestamp: new Date().toISOString(),
    };

    if (agent) {
      agent.addMessage({ id: promptMessage.id, role: 'user', content: promptMessage.content });
    } else {
      setHistoryMessages((prev) => [...prev, promptMessage]);
    }

    setIsLoading(true);
    markRunStart();
    try {
      const result = await generateWeeklyReview();
      const aiMessage: Message = {
        id: `weekly-${Date.now()}`,
        role: 'assistant',
        content: result.response,
        timestamp: new Date().toISOString(),
      };

      if (agent) {
        agent.addMessage({ id: aiMessage.id, role: 'assistant', content: aiMessage.content });
      } else {
        setHistoryMessages((prev) => [...prev, aiMessage]);
      }
      await loadWeeklyReviewHistory();
      setShowReviewHistory(true); // reveal the freshly saved review card
      void loadMemoryHitsForMessage(aiMessage.id);
    } catch (error) {
      toast.error(t('errors.weekly_review_failed'));
      console.error('Failed to generate weekly review', error);
      markRunError();
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (weeklyReviewHistory.length === 0) {
      setSelectedReviewId(null);
      return;
    }

    const selectedStillExists = weeklyReviewHistory.some((r) => r.id === selectedReviewId);
    if (!selectedStillExists) {
      setSelectedReviewId(weeklyReviewHistory[0].id);
    }
  }, [weeklyReviewHistory, selectedReviewId]);

  const selectedReview = weeklyReviewHistory.find((r) => r.id === selectedReviewId) || null;
  const hasUserMessage = messages.some((msg) => msg.role.toLowerCase() === 'user');
  const shouldShowStarterGuide = !hasUserMessage;

  return (
    <div className="flex h-screen bg-gray-50 dark:bg-slate-900 overflow-hidden">
      {/* Sidebar - hidden on mobile */}
      <div className="hidden md:block h-full">
        <CoachMemorySidebar />
      </div>

      {/* Main Chat Area */}
      <div className="flex-1 flex flex-col h-full p-4 overflow-hidden">
        <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
                <Link
                    to="/dashboard"
                    aria-label={t('back_to_dashboard', { defaultValue: 'Back to dashboard' })}
                    className="p-1.5 rounded-md text-gray-500 dark:text-slate-400 hover:text-gray-800 hover:bg-gray-100 transition-colors"
                >
                    <ArrowLeft size={18} />
                </Link>
                <h1 className="text-lg font-semibold text-gray-800 dark:text-slate-100">{t('title')}</h1>
            </div>
            <button
                onClick={handleWeeklyReview}
                disabled={isLoading}
                className="inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-indigo-600 text-white text-sm font-medium hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
                <CalendarDays size={16} />
                {t('weekly_review')}
            </button>
        </div>
        {weeklyReviewHistory.length > 0 && (
            <div className="mb-3 bg-white dark:bg-slate-800 border border-indigo-100 dark:border-indigo-900 rounded-lg p-3">
                <button
                    type="button"
                    onClick={() => setShowReviewHistory((v) => !v)}
                    aria-expanded={showReviewHistory}
                    className="w-full flex items-center gap-2 text-sm font-semibold text-indigo-700 dark:text-indigo-300"
                >
                    <History size={14} />
                    {t('weekly_review_history')}
                    <span className="text-xs font-normal text-indigo-400">({weeklyReviewHistory.length})</span>
                    <ChevronDown size={14} className={`ml-auto transition-transform ${showReviewHistory ? 'rotate-180' : ''}`} />
                </button>
                {showReviewHistory && (<>
                <div className="flex flex-wrap gap-2 mb-3 mt-2">
                    {weeklyReviewHistory.map((item, idx) => {
                      const label = item.formattedDate
                        ? item.formattedDate
                        : (item.createdAt ? new Date(item.createdAt).toLocaleDateString() : t('actions.review_label', { index: weeklyReviewHistory.length - idx }));
                      const isSelected = selectedReviewId === item.id;

                      return (
                        <button
                            key={item.id}
                            onClick={() => setSelectedReviewId(item.id)}
                            className={`px-2.5 py-1 text-xs rounded-full border transition-colors ${
                              isSelected
                                ? 'bg-indigo-600 text-white border-indigo-600'
                                : 'bg-indigo-50 dark:bg-indigo-950/40 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-800 hover:bg-indigo-100'
                            }`}
                        >
                            {label}
                        </button>
                      );
                    })}
                </div>
                {selectedReview && (
                    <div className="max-w-[420px]">
                        <WeeklyReviewCard
                            stats={{
                                totalCompleted: selectedReview.totalCompleted,
                                currentStreak: selectedReview.currentStreak,
                                bestStreak: selectedReview.bestStreak
                            }}
                            highlights={selectedReview.highlights || []}
                            suggestion={selectedReview.suggestion || t('card.weekly.default_suggestion')}
                        />
                    </div>
                )}
                </>)}
            </div>
        )}
        <div className="flex-1 overflow-y-auto mb-4 space-y-4 pr-2">
            {shouldShowStarterGuide && (
                <div className="bg-gradient-to-r from-indigo-50 dark:from-indigo-950/40 to-blue-50 dark:to-slate-900 border border-indigo-100 dark:border-indigo-900 rounded-xl p-4">
                    <h2 className="text-sm font-semibold text-indigo-800 dark:text-indigo-200">{t('starter.title')}</h2>
                    <p className="text-xs text-indigo-700 dark:text-indigo-300 mt-1">
                        {t('starter.subtitle')}
                    </p>
                    <div className="flex flex-wrap gap-2 mt-3">
                        {coldStartPrompts.map((prompt, idx) => (
                            <button
                                key={idx}
                                onClick={() => handleSend(prompt)}
                                disabled={isLoading}
                                className="px-3 py-1.5 bg-white dark:bg-slate-800 text-indigo-700 dark:text-indigo-300 text-xs rounded-full border border-indigo-200 dark:border-indigo-800 hover:bg-indigo-100 transition-colors disabled:opacity-60"
                            >
                                {prompt}
                            </button>
                        ))}
                    </div>
                </div>
            )}
            {/* Messages */}
            {messages.length === 0 && !isLoading && (
                <div className="text-center text-gray-500 dark:text-slate-400 mt-10">
                    <Bot size={48} className="mx-auto mb-2 opacity-50" />
                    <p>{t('start_chatting')}</p>
                </div>
            )}
            {messages.map((msg) => {
              const { text, suggestions, toolCall, plan, weeklyReview } = parseMessageContent(msg.content);
              const handledToolNames = ['create_first_habit', 'save_user_identity', 'present_weekly_review', 'present_daily_focus'];
              const messageHits = memoryHitsByMessage[msg.id] || [];
              const isAssistantMessage = msg.role !== 'user';
              return (
                <div
                    key={msg.id}
                    className={`flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'}`}
                >
                    {text && (
                    <div className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'} max-w-[80%]`}>
                        <div
                        className={`p-3 rounded-lg ${
                            msg.role === 'user'
                            ? 'bg-blue-600 text-white'
                            : 'bg-white dark:bg-slate-800 text-gray-800 dark:text-slate-100 shadow'
                        }`}
                        >
                        <div className="flex items-center gap-2 mb-1">
                            {msg.role === 'user' ? <User size={16} /> : <Bot size={16} />}
                            <span className="text-xs opacity-75">{msg.role === 'user' ? t('roles.user', { defaultValue: 'You' }) : t('roles.assistant', { defaultValue: 'Coach' })}</span>
                        </div>
                        <div className={`${msg.role === 'user' ? 'whitespace-pre-wrap' : ''} prose prose-sm max-w-none dark:prose-invert`}>
                            <ReactMarkdown>{text}</ReactMarkdown>
                        </div>
                        </div>
                    </div>
                    )}
                    {isAssistantMessage && messageHits.length > 0 && (
                        <div className="mt-2 max-w-[80%] bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-lg p-2.5">
                            <div className="flex items-center gap-1 text-[11px] font-semibold text-amber-700 dark:text-amber-300 mb-1.5">
                                <Lightbulb size={12} />
                                {t('memory.used')}
                            </div>
                            <div className="flex flex-wrap gap-1.5">
                                {messageHits.map((hit, idx) => (
                                    <span
                                        key={`${msg.id}-memory-${idx}`}
                                        className="inline-flex items-center px-2 py-0.5 text-[11px] rounded-full border border-amber-300 dark:border-amber-700 bg-white dark:bg-slate-800 text-amber-800 dark:text-amber-200"
                                    >
                                        {hit}
                                    </span>
                                ))}
                            </div>
                        </div>
                    )}
                    {toolCall && handledToolNames.includes(toolCall.name) && (
                        <div className="mt-2 max-w-[80%] bg-white dark:bg-slate-800 p-4 rounded-lg shadow border border-blue-100 dark:border-blue-900 text-gray-800 dark:text-slate-100">
                            <h3 className="font-semibold text-gray-800 dark:text-slate-100 mb-2">{t('proposal.title')}</h3>
                            {toolCall.name === 'create_first_habit' && (
                                <div className="space-y-2 text-sm text-gray-600 dark:text-slate-300">
                                    <p><span className="font-medium text-gray-700 dark:text-slate-200">{t('proposal.habit')}:</span> {String(toolCall.arguments.habitName ?? '')}</p>
                                    <p><span className="font-medium text-gray-700 dark:text-slate-200">{t('proposal.two_minute')}:</span> {String(toolCall.arguments.twoMinuteVersion ?? '')}</p>
                                    <div className="pt-2 flex gap-2">
                                        <button 
                                            onClick={() => handleSend(t('proposal.yes_setup'))}
                                            className="bg-green-500 text-white px-3 py-1.5 rounded-md hover:bg-green-600 text-xs font-medium transition-colors"
                                        >
                                            {t('proposal.accept')}
                                        </button>
                                        <button 
                                            onClick={() => handleSend(t('proposal.change_details'))}
                                            className="bg-gray-200 dark:bg-slate-700 text-gray-700 dark:text-slate-200 px-3 py-1.5 rounded-md hover:bg-gray-300 text-xs font-medium transition-colors"
                                        >
                                            {t('proposal.modify')}
                                        </button>
                                    </div>
                                </div>
                            )}
                            {toolCall.name === 'present_weekly_review' && (
                                <WeeklyReviewCard 
                                    stats={{
                                        totalCompleted: Number(toolCall.arguments.totalCompleted) || 0,
                                        currentStreak: Number(toolCall.arguments.currentStreak) || 0
                                    }}
                                    highlights={(toolCall.arguments.highlights as string[]) || []}
                                    suggestion={String(toolCall.arguments.suggestion ?? 'Keep going!')}
                                />
                            )}
                            {toolCall.name === 'present_daily_focus' && (
                                <div className="max-w-md w-full">
                                    <DailyFocusCard
                                        habitName={String(toolCall.arguments.habitName ?? '')}
                                        twoMinuteVersion={String(toolCall.arguments.twoMinuteVersion || toolCall.arguments.habitName || '')}
                                        onComplete={(habitName) => {
                                            handleSend(`I completed ${habitName}!`);
                                        }}
                                    />
                                </div>
                            )}
                            {toolCall.name === 'save_user_identity' && (
                                <div className="space-y-2 text-sm text-gray-600 dark:text-slate-300">
                                    <p><span className="font-medium text-gray-700 dark:text-slate-200">{t('proposal.identity')}:</span> {String(toolCall.arguments.identity ?? '')}</p>
                                    <div className="pt-2 flex gap-2">
                                        <button 
                                            onClick={() => handleSend(t('proposal.yes_identity'))}
                                            className="bg-green-500 text-white px-3 py-1.5 rounded-md hover:bg-green-600 text-xs font-medium transition-colors"
                                        >
                                            {t('proposal.confirm_identity')}
                                        </button>
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                    {plan && msg.role !== 'user' && (
                        <div className="mt-2 max-w-[80%] bg-white dark:bg-slate-800 p-4 rounded-lg shadow border border-indigo-100 dark:border-indigo-900 text-gray-800 dark:text-slate-100">
                            <h3 className="font-semibold text-indigo-700 dark:text-indigo-300 mb-1">{t('plan.title')}</h3>
                            {plan.title && (
                                <p className="text-sm font-medium text-gray-800 dark:text-slate-100 mb-1">{plan.title}</p>
                            )}
                            {plan.description && (
                                <p className="text-xs text-gray-600 dark:text-slate-300 mb-3 italic">{plan.description}</p>
                            )}
                            <ul className="space-y-2 text-sm text-gray-700 dark:text-slate-200 mb-3">
                                {plan.habits.map((habit, idx) => (
                                    <li key={`${habit.name}-${idx}`} className="border-b border-gray-100 dark:border-slate-700 pb-2 last:border-0">
                                        <p className="font-medium">{idx + 1}. {habit.name}</p>
                                        <p className="text-xs text-green-700">{t('plan.two_min_label')} {habit.twoMinuteVersion}</p>
                                        <p className="text-xs text-gray-500 dark:text-slate-400">{habit.cueImplementationIntention}</p>
                                    </li>
                                ))}
                            </ul>
                            <div className="flex flex-wrap gap-2">
                                <button
                                    onClick={() => handleStartSmall(plan)}
                                    disabled={isApplyingPlan}
                                    className="bg-green-600 text-white px-3 py-1.5 rounded-md hover:bg-green-700 text-xs font-medium transition-colors disabled:opacity-60"
                                >
                                    {isApplyingPlan ? t('plan.saving') : t('plan.start_small')}
                                </button>
                                <button
                                    onClick={() => handleAddAllToHabits(plan)}
                                    disabled={isApplyingPlan}
                                    className="bg-indigo-600 text-white px-3 py-1.5 rounded-md hover:bg-indigo-700 text-xs font-medium transition-colors disabled:opacity-60"
                                >
                                    {isApplyingPlan ? <Loader2 size={14} className="animate-spin" /> : t('plan.add_all')}
                                </button>
                            </div>
                        </div>
                    )}
                    {weeklyReview && msg.role !== 'user' && (!toolCall || toolCall.name !== 'present_weekly_review') && (
                        <div className="mt-2 max-w-[80%]">
                            <WeeklyReviewCard
                                stats={weeklyReview.stats}
                                highlights={weeklyReview.highlights}
                                suggestion={weeklyReview.suggestion}
                            />
                        </div>
                    )}
                    {suggestions.length > 0 && msg.role !== 'user' && (
                        <div className="flex flex-wrap gap-2 mt-2 max-w-[80%]">
                            {suggestions.map((suggestion, idx) => (
                                <button
                                    key={idx}
                                    onClick={() => handleSend(suggestion)}
                                    disabled={isLoading}
                                    className="px-3 py-1 bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-200 text-sm rounded-full hover:bg-blue-200 transition-colors border border-blue-200 dark:border-blue-800 disabled:opacity-50"
                                >
                                    {suggestion}
                                </button>
                            ))}
                        </div>
                    )}
                </div>
              );
            })}

            {/* Agent Activity Indicator - replaces simple loading spinner */}
            {isLoading && (
              <div className="flex flex-col items-start">
                <div className="max-w-[80%]">
                  {activity.phase === 'idle' ? (
                    // REST calls (greeting, weekly review) emit no agent events.
                    <div className="flex items-center gap-2 rounded-lg border border-gray-100 dark:border-slate-700 bg-white dark:bg-slate-800 p-3 text-sm text-indigo-600 shadow-sm">
                      <Loader2 size={14} className="animate-spin" />
                      {t('thinking')}
                    </div>
                  ) : (
                    <AgentActivityIndicator activity={activity} />
                  )}
                </div>
              </div>
            )}

            <div ref={messagesEndRef} />
        </div>
        <div className="flex gap-2">
            <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !e.nativeEvent.isComposing && handleSend()}
            disabled={isLoading}
            className="flex-1 p-2 border rounded-lg bg-white text-gray-900 dark:bg-slate-800 dark:text-slate-100 dark:border-slate-700 placeholder:text-gray-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
            placeholder={isLoading ? t('thinking') : (shouldShowStarterGuide ? t('starter.example_placeholder') : t('placeholder'))}
            />
            <button
            onClick={() => handleSend()}
            disabled={isLoading}
            className={`p-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed`}
            >
            {isLoading ? <Loader2 size={20} className="animate-spin" /> : <Send size={20} />}
            </button>
        </div>
      </div>
    </div>
  );
};

export default CoachPage;
