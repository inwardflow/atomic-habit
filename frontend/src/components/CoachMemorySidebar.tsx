import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../store/authStore';
import { getCoachMemories } from '../api/coach';
import type { CoachMemory } from '../api/coach';
import { Brain, User, Calendar, Lightbulb, Pin } from 'lucide-react';
import { format } from 'date-fns';
import { zhCN } from 'date-fns/locale';

const CoachMemorySidebar = () => {
    const { t, i18n } = useTranslation('coach');
    const user = useAuthStore((state) => state.user);
    const [memories, setMemories] = useState<CoachMemory[]>([]);
    const [isLoading, setIsLoading] = useState(false);

    useEffect(() => {
        if (!user) {
            setMemories([]);
            return;
        }

        let isMounted = true;

        const fetchMemories = async (showLoading: boolean) => {
            if (showLoading) setIsLoading(true);
            try {
                const data = await getCoachMemories();
                if (isMounted) {
                    setMemories(data);
                }
            } catch (error) {
                console.error("Failed to load coach memories", error);
            } finally {
                if (showLoading && isMounted) {
                    setIsLoading(false);
                }
            }
        };

        fetchMemories(true);
        const timer = window.setInterval(() => fetchMemories(false), 20000);

        return () => {
            isMounted = false;
            window.clearInterval(timer);
        };
    }, [user]);

    if (!user) return null;

    const profileMemories = memories.filter(
        (memory) => memory.type === 'USER_INSIGHT' || memory.type === 'LONG_TERM_FACT'
    );
    const dailySummaries = memories.filter((memory) => memory.type === 'DAILY_SUMMARY');
    const prioritizedProfileMemories = [...profileMemories].sort((a, b) => {
        const scoreA = a.importanceScore ?? 3;
        const scoreB = b.importanceScore ?? 3;
        if (scoreA !== scoreB) return scoreB - scoreA;

        const timeA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const timeB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return timeB - timeA;
    });

    const typeBadgeClass = (type: CoachMemory['type']) => {
        if (type === 'LONG_TERM_FACT') return 'bg-amber-100 text-amber-800 border-amber-200';
        if (type === 'USER_INSIGHT') return 'bg-blue-100 text-blue-800 border-blue-200';
        return 'bg-gray-100 text-gray-700 border-gray-200';
    };

    const typeBadgeText = (type: CoachMemory['type']) => {
        if (type === 'LONG_TERM_FACT') return t('card.memory.fact');
        if (type === 'USER_INSIGHT') return t('card.memory.insight');
        return t('card.memory.summary');
    };

    return (
        <div className="w-80 h-full border-r border-gray-200 bg-white flex flex-col overflow-hidden">
            <div className="p-4 border-b border-gray-100">
                <h2 className="text-lg font-semibold text-gray-800 flex items-center gap-2">
                    <Brain className="w-5 h-5 text-indigo-600" />
                    {t('card.memory.title')}
                </h2>
                <p className="text-xs text-gray-500 mt-1">{t('card.memory.subtitle')}</p>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-6">
                {/* Identity Section */}
                <div className="bg-indigo-50 p-4 rounded-xl border border-indigo-100">
                    <div className="flex items-center gap-2 mb-2 text-indigo-800 font-medium">
                        <User className="w-4 h-4" />
                        <span>{t('card.memory.identity')}</span>
                    </div>
                    <p className="text-indigo-900 font-semibold italic text-sm">
                        "{user.identityStatement || t('card.memory.not_set')}"
                    </p>
                </div>

                <div>
                    <h3 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3">{t('card.memory.profile')}</h3>
                    {profileMemories.length === 0 ? (
                        <div className="text-sm text-gray-500 bg-gray-50 border border-gray-100 rounded-lg p-3">
                            {t('card.memory.empty_profile')}
                        </div>
                    ) : (
                        <div className="space-y-3">
                            {prioritizedProfileMemories.slice(0, 10).map((memory) => (
                                <div key={memory.id} className="bg-white border border-gray-100 rounded-lg p-3 shadow-sm">
                                    <div className="flex items-center justify-between mb-2">
                                        <div className="flex items-center gap-1">
                                            <span className={`inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded-full border ${typeBadgeClass(memory.type)}`}>
                                                {memory.type === 'LONG_TERM_FACT' ? <Pin className="w-3 h-3" /> : <Lightbulb className="w-3 h-3" />}
                                                {typeBadgeText(memory.type)}
                                            </span>
                                            <span className="inline-flex items-center text-[10px] px-1.5 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-100">
                                                P{memory.importanceScore ?? 3}
                                            </span>
                                        </div>
                                        <span className="text-[10px] text-gray-400">{memory.formattedDate}</span>
                                    </div>
                                    <p className="text-sm text-gray-700 leading-relaxed">{memory.content}</p>
                                    {memory.expiresAt && (
                                        <p className="mt-2 text-[10px] text-gray-400">
                                            {t('card.memory.expires')} {memory.expiresAt ? format(new Date(memory.expiresAt), 'P', { locale: i18n.language.startsWith('zh') ? zhCN : undefined }) : ''}
                                        </p>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Daily Summaries */}
                <div>
                    <h3 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3">{t('card.memory.daily')}</h3>
                    
                    {isLoading ? (
                        <div className="space-y-3">
                            {[1, 2, 3].map(i => (
                                <div key={i} className="h-20 bg-gray-100 rounded-lg animate-pulse" />
                            ))}
                        </div>
                    ) : dailySummaries.length === 0 ? (
                        <div className="text-center py-8 text-gray-400 text-sm">
                            <Calendar className="w-8 h-8 mx-auto mb-2 opacity-20" />
                            <p>{t('card.memory.empty_daily')}</p>
                            <p className="text-xs mt-1">{t('card.memory.empty_daily_sub')}</p>
                        </div>
                    ) : (
                        <div className="space-y-4">
                            {dailySummaries.map((memory) => (
                                <div key={memory.id} className="relative pl-4 border-l-2 border-gray-200 hover:border-indigo-300 transition-colors">
                                    <div className="absolute -left-[5px] top-0 w-2.5 h-2.5 rounded-full bg-gray-200 ring-4 ring-white" />
                                    <div className="text-xs text-gray-500 font-medium mb-1">
                                        {memory.formattedDate || memory.referenceDate}
                                    </div>
                                    <p className="text-sm text-gray-700 leading-relaxed bg-gray-50 p-3 rounded-lg">
                                        {memory.content}
                                    </p>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default CoachMemorySidebar;
