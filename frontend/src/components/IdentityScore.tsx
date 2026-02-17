import React, { useEffect, useState } from 'react';
import type { UserStats, Badge } from '../types';
import { Trophy, Flame, Target, Star, PartyPopper, Footprints, Diamond, Sunrise, Moon, Calendar, Zap, TrendingUp } from 'lucide-react';
import clsx from 'clsx';
import { AnimatePresence, motion } from 'framer-motion';
import confetti from 'canvas-confetti';

interface IdentityScoreProps {
  stats: UserStats | null;
  identity: string;
}

const IdentityScore: React.FC<IdentityScoreProps> = ({ stats, identity }) => {
  const [prevLevel, setPrevLevel] = useState<number | null>(null);
  const [showLevelUp, setShowLevelUp] = useState(false);
  const [newBadge, setNewBadge] = useState<Badge | null>(null);
  const [prevBadgeCount, setPrevBadgeCount] = useState<number>(0);

  useEffect(() => {
    if (!stats) return;
    const currentLevel = Math.floor(stats.identityScore / 100) + 1;
    
    if (prevLevel !== null && currentLevel > prevLevel) {
        setShowLevelUp(true);
        confetti({
            particleCount: 200,
            spread: 120,
            origin: { y: 0.6 }
        });
        setTimeout(() => setShowLevelUp(false), 4000);
    }
    
    setPrevLevel(currentLevel);

    // Check for new badges
    if (stats.badges && stats.badges.length > prevBadgeCount && prevBadgeCount > 0) {
        // Find the new badge (simplification: just take the last one)
        // In a real app, we'd diff the lists.
        const latest = stats.badges[stats.badges.length - 1];
        setNewBadge(latest);
        setTimeout(() => setNewBadge(null), 5000);
    }
    if (stats.badges) {
        setPrevBadgeCount(stats.badges.length);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stats?.identityScore, stats?.badges]);

  if (!stats) return null;

  // Calculate a "Level" based on score to gamify progress
  const level = Math.floor(stats.identityScore / 100) + 1;
  const progressToNextLevel = stats.identityScore % 100;
  
  const getLevelTitle = (lvl: number) => {
      if (lvl >= 15) return "Master";
      if (lvl >= 10) return "Expert";
      if (lvl >= 6) return "Practitioner";
      if (lvl >= 3) return "Apprentice";
      return "Novice";
  };

  const levelTitle = getLevelTitle(level);

  const getBadgeIcon = (iconName: string) => {
      switch(iconName) {
          case 'flame': return <Flame className="w-5 h-5 text-orange-500" />;
          case 'flame-on': return <Flame className="w-5 h-5 text-red-500 fill-orange-500" />;
          case 'trophy': return <Trophy className="w-5 h-5 text-yellow-500" />;
          case 'star': return <Star className="w-5 h-5 text-yellow-400" />;
          case 'diamond': return <Diamond className="w-5 h-5 text-blue-400" />;
          case 'footprints': return <Footprints className="w-5 h-5 text-green-500" />;
          case 'zap': return <Zap className="w-5 h-5 text-yellow-400 fill-yellow-200" />;
          case 'zap-filled': return <Zap className="w-5 h-5 text-yellow-500 fill-yellow-400" />;
          case 'arrow-up': return <TrendingUp className="w-5 h-5 text-indigo-400" />;
          case 'sunrise': return <Sunrise className="w-5 h-5 text-amber-500" />;
          case 'moon': return <Moon className="w-5 h-5 text-indigo-400" />;
          case 'calendar': return <Calendar className="w-5 h-5 text-green-500" />;
          default: return <Trophy className="w-5 h-5 text-indigo-500" />;
      }
  };

  return (
    <>
      <div className="bg-gradient-to-br from-indigo-600 to-indigo-800 rounded-2xl p-6 text-white shadow-xl shadow-indigo-200 dark:shadow-indigo-900/20 mb-8 relative overflow-hidden group">
        {/* Background decoration */}
        <div className="absolute top-0 right-0 -mr-16 -mt-16 w-64 h-64 rounded-full bg-white/10 blur-3xl group-hover:scale-110 transition-transform duration-700"></div>
        
        <div className="relative z-10 flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
          <div className="flex-1">
            <h2 className="text-3xl font-bold tracking-tight mb-2 flex items-center gap-2">
                I am a {identity}
            </h2>
            
            <div className="flex flex-wrap items-center gap-3 mb-3">
                <div className="flex items-center gap-1.5 bg-white/20 backdrop-blur-md px-3 py-1.5 rounded-full border border-white/10">
                    <Star className="w-4 h-4 text-yellow-300 fill-yellow-300" />
                    <span className="text-sm font-bold text-white">Level {level}</span>
                </div>
                <div className="flex items-center gap-1.5 bg-indigo-900/40 backdrop-blur-md px-3 py-1.5 rounded-full border border-indigo-500/30">
                    <Trophy className="w-3.5 h-3.5 text-indigo-200" />
                    <span className="text-sm font-medium text-indigo-100">{levelTitle}</span>
                </div>
            </div>
            
            {/* Progress bar for level */}
            <div className="w-full max-w-md">
                <div className="flex justify-between text-xs text-indigo-200 mb-1 font-medium px-1">
                    <span>XP: {progressToNextLevel}/100</span>
                    <span>Next: Level {level + 1}</span>
                </div>
                <div className="w-full bg-indigo-950/50 rounded-full h-2.5 overflow-hidden border border-indigo-500/20">
                    <motion.div 
                        initial={{ width: 0 }}
                        animate={{ width: `${progressToNextLevel}%` }}
                        transition={{ duration: 1, ease: "easeOut" }}
                        className="bg-gradient-to-r from-yellow-400 to-yellow-200 h-full rounded-full shadow-[0_0_10px_rgba(250,204,21,0.5)]" 
                    />
                </div>
            </div>
          </div>

            <div className="flex gap-4">
            <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4 flex flex-col items-center min-w-[100px]">
                <Flame className={clsx("w-6 h-6 mb-1", stats.currentStreak > 0 ? "text-orange-400" : "text-slate-300")} />
                <span className="text-2xl font-bold">{stats.currentStreak}</span>
                <span className="text-xs text-indigo-200 uppercase tracking-wide">
                    {stats.currentStreak > 0 ? 'Day Streak' : 'Ready to Start'}
                </span>
            </div>
            
            <div className="bg-white/10 backdrop-blur-sm rounded-xl p-4 flex flex-col items-center min-w-[100px]">
                <Target className="w-6 h-6 mb-1 text-green-400" />
                <span className="text-2xl font-bold">{stats.totalHabitsCompleted}</span>
                <span className="text-xs text-indigo-200 uppercase tracking-wide">Small Wins</span>
            </div>
            </div>
        </div>
        
        {/* Badges Section */}
        {stats.badges && stats.badges.length > 0 && (
            <div className="mt-6 pt-4 border-t border-white/10">
                <p className="text-xs text-indigo-200 uppercase tracking-wide mb-2">Earned Badges ({stats.badges.length})</p>
                <div className="flex flex-wrap gap-2">
                    {stats.badges.map((badge) => (
                        <div key={badge.id} className="group/badge relative bg-white/10 p-2 rounded-lg hover:bg-white/20 transition-colors cursor-help">
                            {getBadgeIcon(badge.icon)}
                            <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-48 bg-slate-900 text-white text-xs p-2 rounded shadow-lg opacity-0 group-hover/badge:opacity-100 transition-opacity pointer-events-none z-20 text-center">
                                <p className="font-bold text-yellow-300 mb-1">{badge.name}</p>
                                <p>{badge.description}</p>
                                <p className="text-[10px] text-slate-400 mt-1">{new Date(badge.earnedAt).toLocaleDateString()}</p>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        )}
        
        {/* Encouraging message if streak is broken/low */}
        {stats.currentStreak === 0 && (
            <div className="mt-4 text-xs text-indigo-200 text-center bg-indigo-900/30 py-1 rounded-lg">
                "You don't have to be perfect. You just have to show up today."
            </div>
        )}
        </div>

        <AnimatePresence>
            {showLevelUp && (
                <motion.div 
                    initial={{ opacity: 0, scale: 0.8 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.8 }}
                    className="fixed inset-0 flex items-center justify-center z-50 pointer-events-none"
                >
                    <div className="bg-white dark:bg-slate-800 p-8 rounded-2xl shadow-2xl border-4 border-yellow-400 text-center relative overflow-hidden max-w-sm w-full mx-4">
                        <div className="absolute inset-0 bg-yellow-50 opacity-50 dark:opacity-10"></div>
                        <div className="relative z-10">
                            <div className="w-20 h-20 bg-yellow-100 dark:bg-yellow-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
                                <PartyPopper className="w-10 h-10 text-yellow-600 dark:text-yellow-400" />
                            </div>
                            <h2 className="text-3xl font-black text-indigo-900 dark:text-white mb-2">LEVEL UP!</h2>
                            <p className="text-indigo-600 dark:text-indigo-300 font-medium text-lg">You are now Level {level}</p>
                            <div className="mt-4 bg-indigo-50 dark:bg-indigo-900/30 p-3 rounded-lg text-sm text-indigo-800 dark:text-indigo-200">
                                "Every level is proof that you are becoming the person you want to be."
                            </div>
                        </div>
                    </div>
                </motion.div>
            )}
            
            {newBadge && (
                <motion.div 
                    initial={{ opacity: 0, y: 50 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: 50 }}
                    className="fixed bottom-8 left-8 z-50 pointer-events-none"
                >
                     <div className="bg-white dark:bg-slate-800 p-4 rounded-xl shadow-2xl border-2 border-yellow-400 flex items-center gap-4 max-w-sm">
                        <div className="bg-yellow-100 dark:bg-yellow-900/30 p-3 rounded-full">
                            {getBadgeIcon(newBadge.icon)}
                        </div>
                        <div>
                            <p className="text-xs font-bold text-yellow-600 dark:text-yellow-400 uppercase tracking-wider">New Badge Unlocked!</p>
                            <h3 className="font-bold text-slate-900 dark:text-white">{newBadge.name}</h3>
                            <p className="text-xs text-slate-500 dark:text-slate-400">{newBadge.description}</p>
                        </div>
                     </div>
                </motion.div>
            )}
        </AnimatePresence>
    </>
  );
};

export default IdentityScore;
