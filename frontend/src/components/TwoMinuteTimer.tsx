import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Play, Pause, Check, Clock, RotateCcw } from 'lucide-react';
import { soundEngine } from '../utils/soundEngine';
import confetti from 'canvas-confetti';
import { useTranslation } from 'react-i18next';

interface TwoMinuteTimerProps {
    habitName: string;
    onClose: () => void;
    onComplete: () => void;
}

const TwoMinuteTimer: React.FC<TwoMinuteTimerProps> = ({ habitName, onClose, onComplete }) => {
    const { t } = useTranslation('timer');
    const [timeLeft, setTimeLeft] = useState(120); // 2 minutes
    const [isActive, setIsActive] = useState(false);
    const [isFinished, setIsFinished] = useState(false);

    useEffect(() => {
        let interval: ReturnType<typeof setInterval>;

        if (isActive && timeLeft > 0) {
            interval = setInterval(() => {
                setTimeLeft((prev) => prev - 1);
            }, 1000);
        } else if (timeLeft === 0) {
            setIsActive(false);
            setIsFinished(true);
            soundEngine.beep();
            confetti({
                particleCount: 150,
                spread: 70,
                origin: { y: 0.6 }
            });
        }

        return () => clearInterval(interval);
    }, [isActive, timeLeft]);

    const toggleTimer = () => {
        setIsActive(!isActive);
    };

    const resetTimer = () => {
        setIsActive(false);
        setIsFinished(false);
        setTimeLeft(120);
    };

    const formatTime = (seconds: number) => {
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    };

    const progress = ((120 - timeLeft) / 120) * 100;

    return (
        <AnimatePresence>
            <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/80 backdrop-blur-sm"
            >
                <motion.div
                    initial={{ scale: 0.9, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    exit={{ scale: 0.9, opacity: 0 }}
                    className="bg-white dark:bg-slate-800 rounded-2xl p-8 max-w-md w-full shadow-2xl border border-indigo-100 dark:border-slate-700 relative overflow-hidden"
                >
                    <button 
                        onClick={onClose}
                        className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors"
                    >
                        <X size={24} />
                    </button>

                    <div className="text-center mb-8">
                        <div className="inline-flex items-center justify-center w-12 h-12 bg-indigo-100 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 rounded-full mb-4">
                            <Clock size={24} />
                        </div>
                        <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-2">{t('title')}</h2>
                        <p className="text-slate-500 dark:text-slate-400 text-sm">
                            {t('subtitle', { habitName })}
                        </p>
                    </div>

                    <div className="flex justify-center mb-8 relative">
                        {/* Circular Progress */}
                        <div className="relative w-48 h-48">
                            <svg className="w-full h-full transform -rotate-90">
                                <circle
                                    cx="96"
                                    cy="96"
                                    r="88"
                                    stroke="currentColor"
                                    strokeWidth="12"
                                    fill="transparent"
                                    className="text-slate-100 dark:text-slate-700"
                                />
                                <circle
                                    cx="96"
                                    cy="96"
                                    r="88"
                                    stroke="currentColor"
                                    strokeWidth="12"
                                    fill="transparent"
                                    strokeDasharray={2 * Math.PI * 88}
                                    strokeDashoffset={2 * Math.PI * 88 * (1 - progress / 100)}
                                    className={`text-indigo-500 transition-all duration-1000 ease-linear ${isFinished ? 'text-green-500' : ''}`}
                                    strokeLinecap="round"
                                />
                            </svg>
                            <div className="absolute inset-0 flex items-center justify-center flex-col">
                                <span className={`text-5xl font-mono font-bold ${isFinished ? 'text-green-500' : 'text-slate-800 dark:text-white'}`}>
                                    {formatTime(timeLeft)}
                                </span>
                                {isActive && <span className="text-xs text-indigo-500 animate-pulse mt-2">{t('focusing')}</span>}
                            </div>
                        </div>
                    </div>

                    <div className="flex justify-center gap-4">
                        {!isFinished ? (
                            <>
                                <button
                                    onClick={toggleTimer}
                                    className={`flex items-center gap-2 px-6 py-3 rounded-xl font-bold text-white transition-all transform active:scale-95 ${
                                        isActive 
                                        ? 'bg-amber-500 hover:bg-amber-600 shadow-lg shadow-amber-500/30' 
                                        : 'bg-indigo-600 hover:bg-indigo-700 shadow-lg shadow-indigo-600/30'
                                    }`}
                                >
                                    {isActive ? <><Pause size={20} /> {t('pause')}</> : <><Play size={20} /> {t('start')}</>}
                                </button>
                                <button
                                    onClick={resetTimer}
                                    className="p-3 rounded-xl bg-slate-100 dark:bg-slate-700 text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
                                    title={t('reset')}
                                >
                                    <RotateCcw size={20} />
                                </button>
                            </>
                        ) : (
                            <button
                                onClick={onComplete}
                                className="flex items-center gap-2 px-8 py-3 rounded-xl font-bold text-white bg-green-500 hover:bg-green-600 shadow-lg shadow-green-500/30 transition-all transform hover:scale-105 active:scale-95 animate-bounce"
                            >
                                <Check size={20} /> {t('mark_done')}
                            </button>
                        )}
                    </div>
                </motion.div>
            </motion.div>
        </AnimatePresence>
    );
};

export default TwoMinuteTimer;
