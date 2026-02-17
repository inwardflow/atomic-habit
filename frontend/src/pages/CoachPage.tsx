import React, { useCallback, useEffect, useState, useRef } from 'react';
import { HttpAgent } from '@ag-ui/client';
import { Send, User, Bot, Loader2, CalendarDays, History, Lightbulb } from 'lucide-react';
import { useAuthStore } from '../store/authStore';
import ReactMarkdown from 'react-markdown';
import JSON5 from 'json5';
import toast from 'react-hot-toast';
import { generateWeeklyReview, getCoachMemoryHits, getGreeting, getChatHistory, getWeeklyReviews } from '../api/coach';
import { BACKEND_URL } from '../api/axios';
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
      if (!name) return '';

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
  const id = typeof lastAssistant.id === 'string' ? lastAssistant.id : '';
  const content = buildRenderableContent(lastAssistant as unknown as Record<string, unknown>).trim();
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
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [agent, setAgent] = useState<HttpAgent | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isApplyingPlan, setIsApplyingPlan] = useState(false);
  const [weeklyReviewHistory, setWeeklyReviewHistory] = useState<WeeklyReviewRecord[]>([]);
  const [selectedReviewId, setSelectedReviewId] = useState<number | null>(null);
  const [memoryHitsByMessage, setMemoryHitsByMessage] = useState<Record<string, string[]>>({});
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const lastAssistantMessageIdRef = useRef<string>('');
  const handledRunErrorSignaturesRef = useRef<Set<string>>(new Set());
  const user = useAuthStore((state) => state.user);
  const token = useAuthStore((state) => state.token);
  const { addHabits } = useHabits();

  // Agent activity tracking
  const { activity, processEvent, markRunStart, markRunError, reset: resetActivity } = useAgentActivity();

  const coldStartPrompts = [
    "我现在有点茫然，请你带我3步开始，越简单越好。",
    "先问我3个问题，帮我找到第一个2分钟习惯。",
    "按我今天的状态，给我一个最小可执行动作。"
  ];

  const normalizePlanItem = (habit: Record<string, unknown>, index: number): HabitPlanItem => {
    const fallbackName = `Habit ${index + 1}`;
    const name = typeof habit?.name === 'string' && habit.name.trim() ? habit.name.trim() : fallbackName;
    const twoMinuteVersion = typeof habit?.twoMinuteVersion === 'string' && habit.twoMinuteVersion.trim()
        ? habit.twoMinuteVersion.trim()
        : `Do 2 minutes of ${name}`;
    const cueImplementationIntention = typeof habit?.cueImplementationIntention === 'string' && habit.cueImplementationIntention.trim()
        ? habit.cueImplementationIntention.trim()
        : 'When I start my day';
    const cueHabitStack = typeof habit?.cueHabitStack === 'string' && habit.cueHabitStack.trim()
        ? habit.cueHabitStack.trim()
        : 'After I finish breakfast';

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
      : 'Keep going with small, consistent steps this week.';

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
      toast.error('Failed to add plan');
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
      toast.success('Added the first tiny habit. Keep it easy.');
    } catch {
      toast.error('Failed to add starter habit');
    } finally {
      setIsApplyingPlan(false);
    }
  };

  const parseMessageContent = (content: string) => {
    if (typeof content !== 'string') return { text: JSON.stringify(content), suggestions: [], toolCall: null, plan: null, weeklyReview: null };

    let text = content;
    let suggestions: string[] = [];
    let toolCall: ToolCallData | null = null;
    let plan: ParsedHabitPlan | null = null;
    let weeklyReview: ParsedWeeklyReview | null = null;

    const codeBlockRegex = /```(?:json)?\s*([\s\S]*?)\s*```/g;
    let match;
    const codeBlocks = [];
    while ((match = codeBlockRegex.exec(content)) !== null) {
        codeBlocks.push({ fullMatch: match[0], inner: match[1].trim() });
    }

    for (const block of codeBlocks) {
        let jsonStr = block.inner;
        while (jsonStr.startsWith('{{') && jsonStr.endsWith('}}')) {
             jsonStr = jsonStr.substring(1, jsonStr.length - 1);
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
        /^\s*replies\s+(\[.*\])\s*$/gim,
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
    const newAgent = new HttpAgent({
      url: `${BACKEND_URL}/agui/run`,
      threadId: 'user-' + (user ? user.id : Date.now()),
      ...(token ? { headers: { Authorization: `Bearer ${token}` } } : {})
    });

    const { unsubscribe } = newAgent.subscribe({
      onMessagesChanged: ({ messages: msgs }) => {
        const formattedMessages: Message[] = msgs.map((m: Record<string, unknown>) => ({
            id: typeof m.id === 'string' ? m.id : `msg-${Date.now()}-${Math.random()}`,
            role: typeof m.role === 'string' ? m.role.toLowerCase() : 'assistant',
            content: buildRenderableContent(m),
            timestamp: m.timestamp as string | undefined
        }));
        setMessages(formattedMessages);

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
      onRawEvent: ({ event }) => {
        // Forward raw events to activity tracker
        processEvent(event);

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
        toast.error('AG-UI returned an error from backend.');
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
        toast.error('Agent run failed.');
      },
    });

    setAgent(newAgent);

    return () => {
      unsubscribe();
      resetActivity();
    };
  }, [user, token, loadMemoryHitsForMessage, processEvent, resetActivity]);

  // Load chat history or greeting
  useEffect(() => {
    const loadInitialData = async () => {
        if (!user) {
            setMemoryHitsByMessage({});
            return;
        }

        try {
            const history = await getChatHistory();
            if (history && history.length > 0) {
                const formattedHistory: Message[] = history.map((msg, idx) => ({
                    id: `hist-${idx}`,
                    role: msg.role.toLowerCase(),
                    content: msg.content,
                    timestamp: msg.timestamp
                }));
                setMessages(formattedHistory);
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
                    setMessages([greetingMsg]);
                } catch (e) {
                    console.error("Failed to fetch greeting", e);
                    const fallbackGreeting: Message = {
                        id: `fallback-greeting-${Date.now()}`,
                        role: 'assistant',
                        content: "欢迎来到 AI Coach。你不需要一次想清楚所有事，我们可以从一个 2 分钟的小动作开始。"
                    };
                    setMessages([fallbackGreeting]);
                } finally {
                    setIsLoading(false);
                }
            }
        } catch (error) {
            console.error("Failed to load history", error);
        }
    };

    loadInitialData();
  }, [user]);

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
      const runResult = await runAgentWithRetry(agent, { runId: `run-${Date.now()}` });
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
      content: 'Start Weekly Review',
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, promptMessage]);
    if (agent) {
      agent.addMessage({ id: promptMessage.id, role: 'user', content: promptMessage.content });
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

      setMessages((prev) => [...prev, aiMessage]);
      if (agent) {
        agent.addMessage({ id: aiMessage.id, role: 'assistant', content: aiMessage.content });
      }
      await loadWeeklyReviewHistory();
      void loadMemoryHitsForMessage(aiMessage.id);
    } catch (error) {
      toast.error('Failed to generate weekly review');
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
    <div className="flex h-screen bg-gray-50 overflow-hidden">
      {/* Sidebar - hidden on mobile */}
      <div className="hidden md:block h-full">
        <CoachMemorySidebar />
      </div>

      {/* Main Chat Area */}
      <div className="flex-1 flex flex-col h-full p-4 overflow-hidden">
        <div className="flex items-center justify-between mb-3">
            <h1 className="text-lg font-semibold text-gray-800">AI Coach</h1>
            <button
                onClick={handleWeeklyReview}
                disabled={isLoading}
                className="inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-indigo-600 text-white text-sm font-medium hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
                <CalendarDays size={16} />
                Weekly Review
            </button>
        </div>
        {weeklyReviewHistory.length > 0 && (
            <div className="mb-3 bg-white border border-indigo-100 rounded-lg p-3">
                <div className="flex items-center gap-2 text-sm font-semibold text-indigo-700 mb-2">
                    <History size={14} />
                    Weekly Review History
                </div>
                <div className="flex flex-wrap gap-2 mb-3">
                    {weeklyReviewHistory.map((item, idx) => {
                      const label = item.formattedDate
                        ? item.formattedDate
                        : (item.createdAt ? new Date(item.createdAt).toLocaleDateString() : `Review ${weeklyReviewHistory.length - idx}`);
                      const isSelected = selectedReviewId === item.id;

                      return (
                        <button
                            key={item.id}
                            onClick={() => setSelectedReviewId(item.id)}
                            className={`px-2.5 py-1 text-xs rounded-full border transition-colors ${
                              isSelected
                                ? 'bg-indigo-600 text-white border-indigo-600'
                                : 'bg-indigo-50 text-indigo-700 border-indigo-200 hover:bg-indigo-100'
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
                            suggestion={selectedReview.suggestion || 'Keep going with small, consistent steps this week.'}
                        />
                    </div>
                )}
            </div>
        )}
        <div className="flex-1 overflow-y-auto mb-4 space-y-4 pr-2">
            {shouldShowStarterGuide && (
                <div className="bg-gradient-to-r from-indigo-50 to-blue-50 border border-indigo-100 rounded-xl p-4">
                    <h2 className="text-sm font-semibold text-indigo-800">不知道从哪开始？先走这 3 步</h2>
                    <p className="text-xs text-indigo-700 mt-1">
                        1. 明确你想成为谁 2. 选一个 2 分钟动作 3. 立刻执行今天最小一步。
                    </p>
                    <div className="flex flex-wrap gap-2 mt-3">
                        {coldStartPrompts.map((prompt, idx) => (
                            <button
                                key={idx}
                                onClick={() => handleSend(prompt)}
                                disabled={isLoading}
                                className="px-3 py-1.5 bg-white text-indigo-700 text-xs rounded-full border border-indigo-200 hover:bg-indigo-100 transition-colors disabled:opacity-60"
                            >
                                {prompt}
                            </button>
                        ))}
                    </div>
                </div>
            )}
            {/* Messages */}
            {messages.length === 0 && !isLoading && (
                <div className="text-center text-gray-500 mt-10">
                    <Bot size={48} className="mx-auto mb-2 opacity-50" />
                    <p>Start chatting with your Atomic Habits Coach!</p>
                </div>
            )}
            {messages.map((msg) => {
              const { text, suggestions, toolCall, plan, weeklyReview } = parseMessageContent(typeof msg.content === 'string' ? msg.content : JSON.stringify(msg.content));
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
                            : 'bg-white text-gray-800 shadow'
                        }`}
                        >
                        <div className="flex items-center gap-2 mb-1">
                            {msg.role === 'user' ? <User size={16} /> : <Bot size={16} />}
                            <span className="text-xs opacity-75 capitalize">{msg.role}</span>
                        </div>
                        <div className="whitespace-pre-wrap prose prose-sm max-w-none dark:prose-invert">
                            <ReactMarkdown>{text}</ReactMarkdown>
                        </div>
                        </div>
                    </div>
                    )}
                    {isAssistantMessage && messageHits.length > 0 && (
                        <div className="mt-2 max-w-[80%] bg-amber-50 border border-amber-200 rounded-lg p-2.5">
                            <div className="flex items-center gap-1 text-[11px] font-semibold text-amber-700 mb-1.5">
                                <Lightbulb size={12} />
                                Memory Used
                            </div>
                            <div className="flex flex-wrap gap-1.5">
                                {messageHits.map((hit, idx) => (
                                    <span
                                        key={`${msg.id}-memory-${idx}`}
                                        className="inline-flex items-center px-2 py-0.5 text-[11px] rounded-full border border-amber-300 bg-white text-amber-800"
                                    >
                                        {hit}
                                    </span>
                                ))}
                            </div>
                        </div>
                    )}
                    {toolCall && (
                        <div className="mt-2 max-w-[80%] bg-white p-4 rounded-lg shadow border border-blue-100 text-gray-800">
                            <h3 className="font-semibold text-gray-800 mb-2">Coach Proposal</h3>
                            {toolCall.name === 'create_first_habit' && (
                                <div className="space-y-2 text-sm text-gray-600">
                                    <p><span className="font-medium text-gray-700">Habit:</span> {String(toolCall.arguments.habitName ?? '')}</p>
                                    <p><span className="font-medium text-gray-700">2-Min Version:</span> {String(toolCall.arguments.twoMinuteVersion ?? '')}</p>
                                    <div className="pt-2 flex gap-2">
                                        <button 
                                            onClick={() => handleSend("Yes, let's set it up!")}
                                            className="bg-green-500 text-white px-3 py-1.5 rounded-md hover:bg-green-600 text-xs font-medium transition-colors"
                                        >
                                            Accept & Save
                                        </button>
                                        <button 
                                            onClick={() => handleSend("I'd like to change the details.")}
                                            className="bg-gray-200 text-gray-700 px-3 py-1.5 rounded-md hover:bg-gray-300 text-xs font-medium transition-colors"
                                        >
                                            Modify
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
                                <div className="space-y-2 text-sm text-gray-600">
                                    <p><span className="font-medium text-gray-700">Identity:</span> {String(toolCall.arguments.identity ?? '')}</p>
                                    <div className="pt-2 flex gap-2">
                                        <button 
                                            onClick={() => handleSend("Yes, that's who I want to be!")}
                                            className="bg-green-500 text-white px-3 py-1.5 rounded-md hover:bg-green-600 text-xs font-medium transition-colors"
                                        >
                                            Confirm Identity
                                        </button>
                                    </div>
                                </div>
                            )}
                             {!handledToolNames.includes(toolCall.name) && (
                                <div className="text-xs text-gray-500">
                                    <p className="font-medium text-gray-700">Tool: {String(toolCall.name)}</p>
                                    <pre className="mt-1 bg-gray-50 p-2 rounded overflow-x-auto border border-gray-200">
                                        {JSON.stringify(toolCall.arguments, null, 2)}
                                    </pre>
                                </div>
                            )}
                        </div>
                    )}
                    {plan && msg.role !== 'user' && (
                        <div className="mt-2 max-w-[80%] bg-white p-4 rounded-lg shadow border border-indigo-100 text-gray-800">
                            <h3 className="font-semibold text-indigo-700 mb-1">Suggested Habit Plan</h3>
                            {plan.title && (
                                <p className="text-sm font-medium text-gray-800 mb-1">{plan.title}</p>
                            )}
                            {plan.description && (
                                <p className="text-xs text-gray-600 mb-3 italic">{plan.description}</p>
                            )}
                            <ul className="space-y-2 text-sm text-gray-700 mb-3">
                                {plan.habits.map((habit, idx) => (
                                    <li key={`${habit.name}-${idx}`} className="border-b border-gray-100 pb-2 last:border-0">
                                        <p className="font-medium">{idx + 1}. {habit.name}</p>
                                        <p className="text-xs text-green-700">2-Min: {habit.twoMinuteVersion}</p>
                                        <p className="text-xs text-gray-500">{habit.cueImplementationIntention}</p>
                                    </li>
                                ))}
                            </ul>
                            <div className="flex flex-wrap gap-2">
                                <button
                                    onClick={() => handleStartSmall(plan)}
                                    disabled={isApplyingPlan}
                                    className="bg-green-600 text-white px-3 py-1.5 rounded-md hover:bg-green-700 text-xs font-medium transition-colors disabled:opacity-60"
                                >
                                    {isApplyingPlan ? 'Saving...' : 'Start Small'}
                                </button>
                                <button
                                    onClick={() => handleAddAllToHabits(plan)}
                                    disabled={isApplyingPlan}
                                    className="bg-indigo-600 text-white px-3 py-1.5 rounded-md hover:bg-indigo-700 text-xs font-medium transition-colors disabled:opacity-60"
                                >
                                    {isApplyingPlan ? <Loader2 size={14} className="animate-spin" /> : 'Add All to Habits'}
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
                                    className="px-3 py-1 bg-blue-100 text-blue-700 text-sm rounded-full hover:bg-blue-200 transition-colors border border-blue-200 disabled:opacity-50"
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
                  <AgentActivityIndicator activity={activity} />
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
            onKeyDown={(e) => e.key === 'Enter' && handleSend()}
            disabled={isLoading}
            className="flex-1 p-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
            placeholder={isLoading ? "Coach is thinking..." : (shouldShowStarterGuide ? "例如：我该从哪一个微习惯开始？" : "Ask your coach...")}
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
