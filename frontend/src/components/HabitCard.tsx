import React, { useState } from 'react';
import { Check, MoreVertical, Pause, Trash2, Edit2, Play, Flame, Timer, Calendar } from 'lucide-react';
import clsx from 'clsx';
import type { Habit } from '../types';
import confetti from 'canvas-confetti';
import toast from 'react-hot-toast';
 import TwoMinuteTimer from './TwoMinuteTimer';
import { useTranslation } from 'react-i18next';

type DayKey = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

interface HabitCardProps {
  habit: Habit;
  onComplete: (id: number) => Promise<void>;
  onUncomplete: (id: number) => Promise<void>;
  onToggleStatus: (id: number) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
  onUpdate: (id: number, updates: Partial<Habit>) => Promise<void>;
}

const HabitCard: React.FC<HabitCardProps> = ({
    habit,
    onComplete,
    onUncomplete,
    onToggleStatus,
    onDelete,
    onUpdate
}) => {
  const { t } = useTranslation(['translation', 'habits']);
  const [showMenu, setShowMenu] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [showTimer, setShowTimer] = useState(false);
  const [editName, setEditName] = useState(habit.name);

  const isNotScheduledToday = !habit.scheduledToday;

  const handleComplete = async () => {
      if (habit.completedToday) {
          await onUncomplete(habit.id);
      } else {
          await onComplete(habit.id);
          confetti({
              particleCount: 100,
              spread: 70,
              origin: { y: 0.6 }
          });
          toast.success(t('habit_card.notifications.complete'), { icon: '🔥' });
      }
  };

  const handleTimerComplete = async () => {
      setShowTimer(false);
      await onComplete(habit.id);
      toast.success(t('habit_card.notifications.timer_complete'), { icon: '✅' });
  };

  const handleSaveEdit = async () => {
      if (editName.trim() && editName !== habit.name) {
          await onUpdate(habit.id, { name: editName });
      }
      setIsEditing(false);
  };

  const frequencyLabel = habit.frequency && habit.frequency.length > 0
    ? habit.frequency.map(d => t(`days.${d as DayKey}`, { ns: 'habits' })).join(', ')
    : null;

  return (
    <>
      {showTimer && (
        <TwoMinuteTimer
            habitName={habit.name}
            onClose={() => setShowTimer(false)}
            onComplete={handleTimerComplete}
        />
      )}

      <div
      className={clsx(
        "group relative bg-white dark:bg-slate-800 p-6 rounded-2xl border transition-all duration-300",
        habit.completedToday
          ? "border-green-200 dark:border-green-900 shadow-sm bg-green-50/10 dark:bg-green-900/10"
          : isNotScheduledToday
            ? "border-slate-100 dark:border-slate-700/50 shadow-none opacity-50"
            : "border-slate-200 dark:border-slate-700 shadow-sm hover:shadow-md hover:border-indigo-200 dark:hover:border-indigo-700",
        !habit.isActive && "opacity-60 grayscale-[0.5]"
      )}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
            {isEditing ? (
                <div className="flex items-center gap-2">
                    <input
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                        className="w-full border border-indigo-200 dark:border-indigo-700 rounded px-2 py-1 text-lg font-bold bg-white dark:bg-slate-700 text-slate-900 dark:text-white"
                        autoFocus
                        onBlur={handleSaveEdit}
                        onKeyDown={(e) => e.key === 'Enter' && handleSaveEdit()}
                    />
                </div>
            ) : (
                <h3 className={clsx(
                    "text-lg font-bold truncate transition-colors",
                    habit.completedToday ? "text-slate-500 dark:text-slate-400 line-through decoration-slate-400 dark:decoration-slate-600" : "text-slate-900 dark:text-white"
                )}>
                    {habit.name}
                    {!habit.isActive && <span className="ml-2 text-xs font-normal text-slate-400 dark:text-slate-500 border border-slate-200 dark:border-slate-700 px-2 py-0.5 rounded-full">{t('habit_card.status.paused')}</span>}
                </h3>
            )}

          <div className="mt-2 flex items-center gap-3 text-sm text-slate-500 dark:text-slate-400 flex-wrap">
            {habit.currentStreak !== undefined && habit.currentStreak > 0 && (
                <div className={clsx(
                    "flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-bold border transition-colors",
                    habit.currentStreak >= 3
                        ? "bg-orange-50 text-orange-600 border-orange-200 dark:bg-orange-900/20 dark:text-orange-400 dark:border-orange-900/50"
                        : "bg-slate-50 text-slate-500 border-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:border-slate-700"
                )}>
                    <Flame className={clsx("w-3 h-3", habit.currentStreak >= 3 ? "fill-orange-500 text-orange-600" : "text-slate-400")} />
                    {habit.currentStreak} {habit.currentStreak > 1 ? t('habit_card.streak.days') : t('habit_card.streak.day')}
                </div>
            )}

            {frequencyLabel && (
              <div className="flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium border bg-purple-50 text-purple-600 border-purple-200 dark:bg-purple-900/20 dark:text-purple-400 dark:border-purple-900/50">
                <Calendar className="w-3 h-3" />
                {frequencyLabel}
              </div>
            )}

            {isNotScheduledToday && (
              <span className="text-xs text-slate-400 dark:text-slate-500 italic">{t('habit_card.status.rest_day')}</span>
            )}

            {habit.twoMinuteVersion && (
                <div
                    onClick={() => setShowTimer(true)}
                    className="flex items-center gap-2 max-w-[60%] cursor-pointer hover:opacity-80 transition-opacity group/timer"
                    title={t('habit_card.menu.start_timer')}
                >
                    <span className="bg-slate-100 dark:bg-slate-700 px-2 py-0.5 rounded text-xs font-medium text-slate-600 dark:text-slate-300 whitespace-nowrap group-hover/timer:bg-indigo-100 dark:group-hover/timer:bg-indigo-900/30 group-hover/timer:text-indigo-600 dark:group-hover/timer:text-indigo-300 transition-colors">
                    <Timer size={12} className="inline mr-1" />
                    {t('habit_card.labels.two_min_rule')}
                    </span>
                    <span className="truncate" title={habit.twoMinuteVersion}>
                    {habit.twoMinuteVersion}
                    </span>
                </div>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2">
            <button
                onClick={handleComplete}
                disabled={!habit.isActive}
                className={clsx(
                    "flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center transition-all duration-300 focus:outline-none focus:ring-2 focus:ring-offset-2",
                    habit.completedToday
                    ? "bg-green-500 text-white shadow-green-200 shadow-lg hover:bg-green-600 focus:ring-green-500 dark:shadow-green-900/30"
                    : "bg-slate-100 dark:bg-slate-700 text-slate-300 dark:text-slate-500 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 hover:text-indigo-600 dark:hover:text-indigo-400 group-hover:bg-indigo-100 dark:group-hover:bg-indigo-900/20 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 focus:ring-indigo-500",
                    !habit.isActive && "cursor-not-allowed opacity-50"
                )}
                title={habit.completedToday ? t('habit_card.actions.mark_incomplete') : t('habit_card.actions.mark_complete')}
            >
                <Check className={clsx("w-5 h-5", habit.completedToday ? "stroke-[3px]" : "")} />
            </button>

            {/* Menu Button */}
            <div className="relative">
                <button
                    onClick={() => setShowMenu(!showMenu)}
                    className="p-2 text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 rounded-full hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                >
                    <MoreVertical size={16} />
                </button>

                {showMenu && (
                    <>
                        <div className="fixed inset-0 z-10" onClick={() => setShowMenu(false)}></div>
                        <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-slate-800 rounded-xl shadow-xl border border-slate-100 dark:border-slate-700 z-20 overflow-hidden animate-in fade-in zoom-in-95 duration-200">
                            <button
                                onClick={() => { setShowTimer(true); setShowMenu(false); }}
                                className="w-full text-left px-4 py-3 text-sm text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 flex items-center gap-2"
                            >
                                <Timer size={14} /> {t('habit_card.menu.start_timer')}
                            </button>
                            <button
                                onClick={() => { setIsEditing(true); setShowMenu(false); }}
                                className="w-full text-left px-4 py-3 text-sm text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 flex items-center gap-2"
                            >
                                <Edit2 size={14} /> {t('habit_card.menu.rename')}
                            </button>
                            <button
                                onClick={() => { onToggleStatus(habit.id); setShowMenu(false); }}
                                className="w-full text-left px-4 py-3 text-sm text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 flex items-center gap-2"
                            >
                                {habit.isActive ? <><Pause size={14} /> {t('habit_card.menu.pause')}</> : <><Play size={14} /> {t('habit_card.menu.resume')}</>}
                            </button>
                            <div className="h-px bg-slate-100 dark:bg-slate-700 my-1"></div>
                            <button
                                onClick={() => { if(confirm(t('habit_card.menu.delete_confirm'))) onDelete(habit.id); }}
                                className="w-full text-left px-4 py-3 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 flex items-center gap-2"
                            >
                                <Trash2 size={14} /> {t('habit_card.menu.delete')}
                            </button>
                        </div>
                    </>
                )}
            </div>
        </div>
      </div>

      {habit.cueImplementationIntention && (
        <div className="mt-4 pt-4 border-t border-slate-100 dark:border-slate-700">
          <p className="text-xs text-slate-400 dark:text-slate-500 font-medium uppercase tracking-wide">{t('habit_card.labels.when_where')}</p>
          <p className="text-sm text-slate-600 dark:text-slate-300 mt-1 line-clamp-2">{habit.cueImplementationIntention}</p>
        </div>
      )}

      {habit.cueHabitStack && (
        <div className="mt-2 pt-2 border-t border-slate-50 dark:border-slate-700 border-dashed">
           <p className="text-xs text-indigo-400 dark:text-indigo-300 font-medium uppercase tracking-wide">{t('habit_card.labels.habit_stack')}</p>
           <p className="text-sm text-slate-600 dark:text-slate-300 mt-1 line-clamp-2 italic">{habit.cueHabitStack}</p>
        </div>
      )}
    </div>
    </>
  );
};

export default HabitCard;
