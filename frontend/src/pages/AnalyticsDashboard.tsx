import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, AreaChart, Area, PieChart, Pie, Cell } from 'recharts';
import { Award, Flame, Trophy, Footprints, Star, Diamond, Activity, HeartPulse, History, ChevronDown, ArrowLeft, Sun, Moon, Calendar, Zap, TrendingUp, Sunrise } from 'lucide-react';
import api from '../api/axios';
import type { AdvancedUserStats, Badge, MoodInsight, MoodLog, Page } from '../types';
import { format, subDays } from 'date-fns';
import { zhCN } from 'date-fns/locale';

const BadgeIcon = ({ icon, className }: { icon: string, className?: string }) => {
    switch (icon) {
        case 'flame': return <Flame className={className} />;
        case 'flame-on': return <Flame className={`text-orange-500 ${className}`} />;
        case 'trophy': return <Trophy className={className} />;
        case 'footprints': return <Footprints className={className} />;
        case 'diamond': return <Diamond className={className} />;
        case 'sunrise': return <Sunrise className={`text-amber-500 ${className}`} />;
        case 'moon': return <Moon className={`text-indigo-400 ${className}`} />;
        case 'calendar': return <Calendar className={`text-green-500 ${className}`} />;
        case 'zap': return <Zap className={`text-yellow-400 ${className}`} />;
        case 'zap-filled': return <Zap className={`text-yellow-500 fill-yellow-500 ${className}`} />;
        case 'arrow-up': return <TrendingUp className={`text-blue-500 ${className}`} />;
        default: return <Star className={className} />;
    }
};

const MOOD_COLORS: Record<string, string> = {
    'MOTIVATED': '#EAB308', // yellow-500
    'FOCUSED': '#3B82F6', // blue-500
    'HAPPY': '#22C55E', // green-500
    'NEUTRAL': '#6B7280', // gray-500
    'TIRED': '#A855F7', // purple-500
    'SAD': '#6366F1', // indigo-500
    'ANXIOUS': '#FB923C', // orange-400
    'ANGRY': '#EF4444', // red-500
    'GRATITUDE': '#EC4899', // pink-500
};

const MoodInsightCard = ({ insight }: { insight: MoodInsight }) => {
    const { t } = useTranslation(['analytics', 'translation']);
    return (
        <div className="bg-white dark:bg-slate-800 p-5 rounded-xl border border-indigo-50 dark:border-slate-700 shadow-sm hover:shadow-md transition-all">
            <div className="flex justify-between items-start mb-3">
                <div className="px-3 py-1 bg-indigo-50 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 rounded-full text-xs font-bold uppercase tracking-wider">
                    {t(`mood.types.${insight.mood.toLowerCase()}`, { ns: 'translation', defaultValue: insight.mood })}
                </div>
                <span className="text-xs text-gray-400">{insight.logCount} {t('logs')}</span>
            </div>
            
            <div className="mb-4">
                <div className="text-2xl font-bold text-gray-800 dark:text-gray-100">{insight.avgCompletions.toFixed(1)}</div>
                <div className="text-xs text-gray-500 dark:text-gray-400">{t('avg_habits_per_day')}</div>
            </div>
            
            {insight.topHabits && insight.topHabits.length > 0 && (
                <div className="border-t border-gray-100 dark:border-slate-700 pt-3">
                    <div className="text-xs text-gray-400 mb-2 uppercase font-medium">{t('top_habits')}</div>
                    <div className="flex flex-wrap gap-2">
                        {insight.topHabits.map((habit, idx) => (
                            <span key={idx} className="text-xs bg-gray-50 dark:bg-slate-700 text-gray-600 dark:text-gray-300 px-2 py-1 rounded border border-gray-100 dark:border-slate-600">
                                {habit}
                            </span>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
};

export const AnalyticsDashboard = () => {
    const { t, i18n } = useTranslation(['analytics', 'translation']);
    const navigate = useNavigate();
    const [stats, setStats] = useState<AdvancedUserStats | null>(null);
    const [badges, setBadges] = useState<Badge[]>([]);
    const [loading, setLoading] = useState(true);
    const [moodHistory, setMoodHistory] = useState<MoodLog[]>([]);
    const [moodPage, setMoodPage] = useState(0);
    const [hasMoreMoods, setHasMoreMoods] = useState(true);
    const [loadingMoods, setLoadingMoods] = useState(false);
    const [recentMoods, setRecentMoods] = useState<MoodLog[]>([]);

    useEffect(() => {
        Promise.all([
            api.get('/users/stats/advanced'),
            api.get('/users/badges'),
            api.get('/moods/since?since=' + subDays(new Date(), 7).toISOString())
        ])
            .then(([statsRes, badgesRes, recentMoodsRes]) => {
                setStats(statsRes.data);
                setBadges(badgesRes.data);
                setRecentMoods(recentMoodsRes.data);
            })
            .catch(err => console.error(err))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        setLoadingMoods(true);
        api.get<Page<MoodLog>>(`/moods?page=${moodPage}&size=5&sort=createdAt,desc`)
            .then(res => {
                 if (moodPage === 0) {
                     setMoodHistory(res.data.content);
                 } else {
                     setMoodHistory(prev => [...prev, ...res.data.content]);
                 }
                 setHasMoreMoods(!res.data.last);
            })
            .catch(console.error)
            .finally(() => setLoadingMoods(false));
    }, [moodPage]);

    if (loading) return <div className="p-8 text-center text-gray-500 dark:text-gray-400">{t('loading_analytics')}</div>;
    if (!stats) return <div className="p-8 text-center text-gray-500 dark:text-gray-400">{t('no_data')}</div>;

    const habitData = Object.entries(stats.completionsByHabit).map(([name, count]) => ({ name, count }));

    // Process recent moods for pie chart
    const moodDistribution = recentMoods.reduce((acc, log) => {
        acc[log.moodType] = (acc[log.moodType] || 0) + 1;
        return acc;
    }, {} as Record<string, number>);

    const moodPieData = Object.entries(moodDistribution).map(([name, value]) => ({ name, value }));

    return (
        <div className="p-6 space-y-8 max-w-6xl mx-auto">
            <div className="flex items-center gap-3 mb-4">
                <button 
                    onClick={() => navigate(-1)} 
                    className="p-2 -ml-2 mr-1 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white"
                    aria-label="Go back"
                >
                    <ArrowLeft className="w-6 h-6" />
                </button>
                <Activity className="w-8 h-8 text-indigo-600 dark:text-indigo-400" />
                <h1 className="text-3xl font-bold text-gray-900 dark:text-white">{t('title')}</h1>
            </div>
            
            {/* Mood & Productivity Correlation */}
            {stats.moodInsights && stats.moodInsights.length > 0 && (
                <div className="bg-gradient-to-br from-indigo-500 to-purple-600 p-8 rounded-2xl text-white shadow-lg overflow-hidden relative">
                    <div className="absolute top-0 right-0 -mr-16 -mt-16 w-64 h-64 rounded-full bg-white/10 blur-3xl"></div>
                    
                    <div className="flex items-center gap-2 mb-6 relative z-10">
                        <HeartPulse className="w-6 h-6 text-pink-300" />
                        <h2 className="text-xl font-bold">{t('mood_habit_correlation')}</h2>
                    </div>
                    
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6 relative z-10">
                        {stats.moodInsights.slice(0, 3).map((insight, idx) => (
                           <MoodInsightCard key={idx} insight={insight} />
                        ))}
                    </div>
                    <p className="mt-6 text-sm text-indigo-200 italic relative z-10">
                        "{t('quote')}"
                    </p>
                </div>
            )}
            
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                {/* Mood Distribution Chart (New) */}
                <div className="bg-white dark:bg-slate-800 p-6 rounded-2xl shadow-sm border border-gray-100 dark:border-slate-700 transition-colors duration-300">
                    <div className="flex items-center gap-2 mb-6">
                        <Sun className="w-5 h-5 text-orange-400" />
                        <h2 className="text-lg font-semibold text-gray-700 dark:text-gray-200">{t('moods_last_7_days')}</h2>
                    </div>
                    <div className="h-72 flex items-center justify-center">
                        {moodPieData.length > 0 ? (
                            <ResponsiveContainer width="100%" height="100%">
                                <PieChart>
                                    <Pie
                                        data={moodPieData}
                                        cx="50%"
                                        cy="50%"
                                        innerRadius={60}
                                        outerRadius={80}
                                        paddingAngle={5}
                                        dataKey="value"
                                    >
                                        {moodPieData.map((entry, index) => (
                                            <Cell key={`cell-${index}`} fill={MOOD_COLORS[entry.name] || '#CBD5E1'} />
                                        ))}
                                    </Pie>
                                    <Tooltip 
                                        contentStyle={{borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)'}}
                                    />
                                </PieChart>
                            </ResponsiveContainer>
                        ) : (
                            <p className="text-gray-400 text-sm">{t('no_mood_data')}</p>
                        )}
                    </div>
                     {moodPieData.length > 0 && (
                        <div className="flex flex-wrap justify-center gap-3 mt-4">
                            {moodPieData.map((entry) => (
                                <div key={entry.name} className="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
                                    <div className="w-2 h-2 rounded-full" style={{ backgroundColor: MOOD_COLORS[entry.name] || '#CBD5E1' }}></div>
                                    <span>
                                        {t(`mood.types.${entry.name.toLowerCase()}`, { ns: 'translation', defaultValue: entry.name })} ({entry.value})
                                    </span>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Daily Activity Chart */}
                <div className="bg-white dark:bg-slate-800 p-6 rounded-2xl shadow-sm border border-gray-100 dark:border-slate-700 transition-colors duration-300">
                    <h2 className="text-lg font-semibold mb-6 text-gray-700 dark:text-gray-200">{t('consistency_rhythm')}</h2>
                    <div className="h-72">
                        <ResponsiveContainer width="100%" height="100%">
                            <AreaChart data={stats.last30Days}>
                                <defs>
                                    <linearGradient id="colorCount" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#818cf8" stopOpacity={0.8}/>
                                        <stop offset="95%" stopColor="#818cf8" stopOpacity={0}/>
                                    </linearGradient>
                                </defs>
                                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" strokeOpacity={0.3} />
                                <XAxis 
                                    dataKey="date" 
                                    tickFormatter={(d) => d.slice(5)} 
                                    tick={{fontSize: 12, fill: '#6B7280'}}
                                    axisLine={false}
                                    tickLine={false}
                                />
                                <YAxis 
                                    allowDecimals={false} 
                                    tick={{fontSize: 12, fill: '#6B7280'}}
                                    axisLine={false}
                                    tickLine={false}
                                />
                                <Tooltip 
                                    cursor={{fill: '#F3F4F6'}}
                                    contentStyle={{borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)'}}
                                />
                                <Area type="monotone" dataKey="count" stroke="#4f46e5" fillOpacity={1} fill="url(#colorCount)" />
                            </AreaChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                {/* Habit Breakdown Chart */}
                <div className="bg-white dark:bg-slate-800 p-6 rounded-2xl shadow-sm border border-gray-100 dark:border-slate-700 transition-colors duration-300">
                    <h2 className="text-lg font-semibold mb-6 text-gray-700 dark:text-gray-200">{t('habit_strength')}</h2>
                    <div className="h-72">
                         <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={habitData} layout="vertical" margin={{left: 20}}>
                                <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#E5E7EB" strokeOpacity={0.3} />
                                <XAxis 
                                    type="number" 
                                    allowDecimals={false} 
                                    tick={{fontSize: 12, fill: '#6B7280'}}
                                    axisLine={false}
                                    tickLine={false}
                                />
                                <YAxis 
                                    dataKey="name" 
                                    type="category" 
                                    width={120} 
                                    tick={{fontSize: 12, fill: '#6B7280'}}
                                    axisLine={false}
                                    tickLine={false}
                                />
                                <Tooltip 
                                    cursor={{fill: '#F3F4F6'}}
                                    contentStyle={{borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)'}}
                                />
                                <Bar dataKey="count" fill="#10B981" radius={[0, 4, 4, 0]} barSize={20} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                 {/* Badges Section */}
                <div className="bg-white dark:bg-slate-800 p-6 rounded-2xl shadow-sm border border-gray-100 dark:border-slate-700 transition-colors duration-300">
                    <div className="flex items-center gap-2 mb-6">
                        <Award className="w-6 h-6 text-yellow-500" />
                        <h2 className="text-lg font-semibold text-gray-700 dark:text-gray-200">{t('milestones_memories')}</h2>
                    </div>
                    
                    {badges.length === 0 ? (
                        <p className="text-gray-500 dark:text-gray-400 text-sm">{t('no_milestones')}</p>
                    ) : (
                        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
                            {badges.map(badge => (
                                <div key={badge.id} className="flex flex-col items-center text-center p-4 bg-gray-50 dark:bg-slate-900 rounded-xl border border-gray-100 dark:border-slate-700 hover:shadow-md transition-shadow">
                                    <div className="w-12 h-12 bg-white dark:bg-slate-800 rounded-full flex items-center justify-center shadow-sm mb-3 text-indigo-600 dark:text-indigo-400">
                                        <BadgeIcon icon={badge.icon} className="w-6 h-6" />
                                    </div>
                                    <h3 className="font-semibold text-gray-900 dark:text-white text-xs">{badge.name}</h3>
                                    <p className="text-[10px] text-gray-500 dark:text-gray-400 mt-1 line-clamp-2" title={badge.description}>
                                        {badge.description}
                                    </p>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Mood History Timeline */}
                <div className="bg-white dark:bg-slate-800 p-6 rounded-2xl shadow-sm border border-gray-100 dark:border-slate-700 transition-colors duration-300 flex flex-col">
                    <div className="flex items-center gap-2 mb-6">
                        <History className="w-6 h-6 text-blue-500" />
                        <h2 className="text-lg font-semibold text-gray-700 dark:text-gray-200">{t('emotional_journey')}</h2>
                    </div>
                    
                    <div className="flex-1 overflow-y-auto max-h-[400px] pr-2 space-y-4">
                        {moodHistory.length === 0 ? (
                            <p className="text-gray-500 dark:text-gray-400 text-sm text-center py-8">{t('no_mood_logs')}</p>
                        ) : (
                            moodHistory.map((log) => (
                                <div key={log.id} className="relative pl-6 pb-2 border-l-2 border-indigo-100 dark:border-slate-700 last:border-0">
                                    <div className="absolute -left-[9px] top-0 w-4 h-4 rounded-full bg-indigo-500 border-4 border-white dark:border-slate-800"></div>
                                    <div className="flex flex-col sm:flex-row sm:justify-between sm:items-baseline mb-1">
                                        <span className="font-semibold text-gray-900 dark:text-white text-sm">
                                            {t(`mood.types.${log.moodType.toLowerCase()}`, { ns: 'translation', defaultValue: log.moodType })}
                                        </span>
                                        <span className="text-xs text-gray-400">
                                            {format(new Date(log.createdAt), 'MMM d, h:mm a', { locale: i18n.language.startsWith('zh') ? zhCN : undefined })}
                                        </span>
                                    </div>
                                    {log.note && (
                                        <p className="text-sm text-gray-600 dark:text-gray-300 bg-gray-50 dark:bg-slate-900/50 p-3 rounded-lg mt-1 italic">
                                            "{log.note}"
                                        </p>
                                    )}
                                </div>
                            ))
                        )}
                        
                        {hasMoreMoods && (
                            <button 
                                onClick={() => setMoodPage(p => p + 1)}
                                disabled={loadingMoods}
                                className="w-full py-2 text-xs font-medium text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-slate-700 rounded transition-colors flex items-center justify-center gap-1"
                            >
                                {loadingMoods ? t('loading') : <>{t('load_more')} <ChevronDown size={14} /></>}
                            </button>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};
