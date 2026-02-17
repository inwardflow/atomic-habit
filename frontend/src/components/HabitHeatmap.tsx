import React from 'react';
import clsx from 'clsx';

interface HabitHeatmapProps {
  completions: string[]; // List of YYYY-MM-DD strings
}

const HabitHeatmap: React.FC<HabitHeatmapProps> = ({ completions }) => {
  // Count completions per day
  const counts = completions.reduce((acc, date) => {
    acc[date] = (acc[date] || 0) + 1;
    return acc;
  }, {} as Record<string, number>);

  // Generate last 90 days (Quarterly view is less overwhelming)
  const today = new Date();
  const days = [];
  for (let i = 90; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    days.push(d);
  }

  const getColor = (count: number) => {
    if (count === 0) return 'bg-gray-50 border border-gray-100 dark:bg-slate-800/50 dark:border-slate-700'; // Softer empty state
    if (count === 1) return 'bg-emerald-200 border border-emerald-300 dark:bg-emerald-900/30 dark:border-emerald-800/50';
    if (count === 2) return 'bg-emerald-300 border border-emerald-400 dark:bg-emerald-800/40 dark:border-emerald-700/50';
    if (count === 3) return 'bg-emerald-400 border border-emerald-500 dark:bg-emerald-700/50 dark:border-emerald-600/50';
    return 'bg-emerald-500 border border-emerald-600 dark:bg-emerald-600 dark:border-emerald-500';
  };

  // Helper to format date as YYYY-MM-DD in local time
  const formatDate = (date: Date) => {
    const offset = date.getTimezoneOffset();
    const localDate = new Date(date.getTime() - (offset * 60 * 1000));
    return localDate.toISOString().split('T')[0];
  };

  return (
    <div className="bg-white dark:bg-slate-800 p-6 rounded-2xl border border-slate-200 dark:border-slate-700 shadow-sm overflow-hidden transition-colors duration-300">
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-lg font-bold text-slate-900 dark:text-white">Consistency Map (Last 90 Days)</h3>
        <span className="text-xs text-slate-400 dark:text-slate-500">Every square is a victory</span>
      </div>
      
      <div className="overflow-x-auto pb-2">
        <div className="min-w-fit">
            <div className="grid grid-rows-7 grid-flow-col gap-1.5">
                {days.map((date) => {
                    const dateStr = formatDate(date);
                    const count = counts[dateStr] || 0;
                    return (
                        <div
                            key={dateStr}
                            title={`${dateStr}: ${count} completions`}
                            className={clsx(
                                "w-3.5 h-3.5 rounded-sm transition-all duration-300",
                                getColor(count)
                            )}
                        />
                    );
                })}
            </div>
        </div>
      </div>
      
      <div className="mt-4 flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400 justify-end">
        <span>Rest</span>
        <div className="w-3.5 h-3.5 bg-gray-50 border border-gray-100 dark:bg-slate-800/50 dark:border-slate-700 rounded-sm" />
        <div className="w-3.5 h-3.5 bg-emerald-200 dark:bg-emerald-900/30 rounded-sm" />
        <div className="w-3.5 h-3.5 bg-emerald-300 dark:bg-emerald-800/40 rounded-sm" />
        <div className="w-3.5 h-3.5 bg-emerald-400 dark:bg-emerald-700/50 rounded-sm" />
        <div className="w-3.5 h-3.5 bg-emerald-500 dark:bg-emerald-600 rounded-sm" />
        <span>Action</span>
      </div>
    </div>
  );
};

export default HabitHeatmap;
