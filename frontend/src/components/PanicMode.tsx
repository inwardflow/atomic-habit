import React, { useState, useEffect } from 'react';
import { Wind, X, Eye, Ear, Hand, Coffee, Footprints, ArrowRight, Check, Volume2, VolumeX } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import api from '../api/axios';
import { useHabits } from '../hooks/useHabits';
import type { Habit } from '../types';
import confetti from 'canvas-confetti';
import toast from 'react-hot-toast';
import { soundEngine } from '../utils/soundEngine';

const PanicMode = () => {
  const { t } = useTranslation('panic');
  const [isOpen, setIsOpen] = useState(false);
  const [text, setText] = useState(t('breathe.inhale'));
  const [mode, setMode] = useState<'breathe' | 'grounding' | 'focus'>('breathe');
  const [groundingStep, setGroundingStep] = useState(0);
  const { habits, completeHabit } = useHabits();
  const [focusHabit, setFocusHabit] = useState<Habit | null>(null);
  const [soundEnabled, setSoundEnabled] = useState(false);

  const groundingSteps = [
      { icon: Eye, count: 5, text: t('grounding.step1.text'), desc: t('grounding.step1.desc') },
      { icon: Hand, count: 4, text: t('grounding.step2.text'), desc: t('grounding.step2.desc') },
      { icon: Ear, count: 3, text: t('grounding.step3.text'), desc: t('grounding.step3.desc') },
      { icon: Coffee, count: 2, text: t('grounding.step4.text'), desc: t('grounding.step4.desc') },
      { icon: Footprints, count: 1, text: t('grounding.step5.text'), desc: t('grounding.step5.desc') },
  ];

  useEffect(() => {
    if (isOpen) {
        // Log the panic state to the backend
        api.post('/moods', { moodType: 'OVERWHELMED', note: 'Panic Mode activated' })
           .catch(err => console.error('Failed to log mood', err));
        
        // Pick one incomplete habit for focus mode
        const incomplete = habits.filter(h => !h.completedToday);
        if (incomplete.length > 0) {
            // Prefer habits with 2-minute version defined
            const withTwoMin = incomplete.find(h => h.twoMinuteVersion);
            setFocusHabit(withTwoMin || incomplete[0]);
        }
    } else {
        // Reset state when closed
        setMode('breathe');
        setGroundingStep(0);
        setFocusHabit(null);
        if (soundEnabled) {
            soundEngine.stop();
            setSoundEnabled(false);
        }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, habits]);

  const toggleSound = () => {
      if (soundEnabled) {
          soundEngine.stop();
          setSoundEnabled(false);
      } else {
          soundEngine.play('rain');
          setSoundEnabled(true);
      }
  };

  const handleCompleteFocus = async () => {
      if (focusHabit) {
          await completeHabit(focusHabit.id);
          confetti({
              particleCount: 150,
              spread: 100,
              origin: { y: 0.6 }
          });
          toast.success(t('focus.success'), { icon: '🌟' });
          setIsOpen(false);
      }
  };

  useEffect(() => {
    if (!isOpen || mode !== 'breathe') return;
    
    const cycle = () => {
      setText(t('breathe.inhale'));
      setTimeout(() => {
        setText(t('breathe.hold'));
        setTimeout(() => {
          setText(t('breathe.exhale'));
        }, 4000);
      }, 4000);
    };
    
    cycle();
    const interval = setInterval(cycle, 12000); // 4-4-4 breathing box
    return () => clearInterval(interval);
  }, [isOpen, mode, t]);

  return (
    <>
      <AnimatePresence>
        {!isOpen && (
          <motion.button
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0, opacity: 0 }}
            onClick={() => setIsOpen(true)}
            className="fixed bottom-8 left-8 md:bottom-8 md:left-8 p-3 bg-sky-100 dark:bg-sky-900 text-sky-700 dark:text-sky-200 rounded-full shadow-lg hover:bg-sky-200 dark:hover:bg-sky-800 transition-all z-40 flex items-center gap-2 text-sm font-medium hover:scale-105 active:scale-95"
            title={t('button')}
          >
            <Wind className="w-5 h-5" />
            <span className="hidden md:inline">{t('button')}</span>
          </motion.button>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-sky-50/95 dark:bg-slate-900/95 backdrop-blur-sm z-[100] flex flex-col items-center justify-center p-6"
          >
            <button 
                onClick={() => setIsOpen(false)}
                className="absolute top-6 right-6 p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors"
            >
                <X size={32} />
            </button>

            <div className="text-center max-w-md w-full">
                {/* Mode Switcher */}
                {mode !== 'focus' && (
                    <div className="flex justify-center gap-4 mb-8">
                        <button 
                            onClick={() => setMode('breathe')}
                            className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${mode === 'breathe' ? 'bg-sky-200 dark:bg-sky-800 text-sky-800 dark:text-sky-100' : 'text-sky-400 dark:text-sky-500 hover:text-sky-600 dark:hover:text-sky-300'}`}
                        >
                            {t('breathe.mode')}
                        </button>
                        <button 
                            onClick={() => setMode('grounding')}
                            className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${mode === 'grounding' ? 'bg-sky-200 dark:bg-sky-800 text-sky-800 dark:text-sky-100' : 'text-sky-400 dark:text-sky-500 hover:text-sky-600 dark:hover:text-sky-300'}`}
                        >
                            {t('grounding.mode')}
                        </button>
                        <button 
                            onClick={toggleSound}
                            className={`px-4 py-2 rounded-full text-sm font-medium transition-colors flex items-center gap-2 ${soundEnabled ? 'bg-sky-200 dark:bg-sky-800 text-sky-800 dark:text-sky-100' : 'text-sky-400 dark:text-sky-500 hover:text-sky-600 dark:hover:text-sky-300'}`}
                        >
                            {soundEnabled ? <Volume2 size={16} /> : <VolumeX size={16} />}
                            {soundEnabled ? t('sound.on') : t('sound.off')}
                        </button>
                    </div>
                )}

                {mode === 'breathe' ? (
                    <motion.div
                        key="breathe"
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                    >
                        <motion.h2 
                            initial={{ y: -20, opacity: 0 }}
                            animate={{ y: 0, opacity: 1 }}
                            className="text-3xl font-light text-sky-900 dark:text-sky-100 mb-12"
                        >
                            {t('breathe.title')}
                        </motion.h2>
                        
                        <div className="relative w-64 h-64 mx-auto mb-16 flex items-center justify-center">
                            {/* Breathing Circle Animation */}
                            <motion.div 
                                animate={{ 
                                    scale: [1, 1.5, 1.5, 1],
                                    opacity: [0.3, 0.6, 0.6, 0.3],
                                }}
                                transition={{ 
                                    duration: 12, 
                                    repeat: Infinity,
                                    ease: "easeInOut",
                                    times: [0, 0.33, 0.66, 1] 
                                }}
                                className="absolute inset-0 bg-sky-300 dark:bg-sky-700 rounded-full"
                            />
                            
                            <motion.div 
                                animate={{ 
                                    scale: [1, 1.2, 1.2, 1],
                                }}
                                transition={{ 
                                    duration: 12, 
                                    repeat: Infinity,
                                    ease: "easeInOut",
                                    times: [0, 0.33, 0.66, 1]
                                }}
                                className="w-32 h-32 bg-white dark:bg-slate-800 rounded-full shadow-xl flex items-center justify-center relative z-10"
                            >
                                <span className="text-sky-600 dark:text-sky-300 font-medium text-lg tracking-widest uppercase">
                                    {text}
                                </span>
                            </motion.div>
                        </div>
                    </motion.div>
                ) : mode === 'grounding' ? (
                    <motion.div
                        key="grounding"
                        initial={{ opacity: 0, x: 20 }}
                        animate={{ opacity: 1, x: 0 }}
                        exit={{ opacity: 0, x: -20 }}
                        className="mb-12"
                    >
                        <h2 className="text-3xl font-light text-sky-900 dark:text-sky-100 mb-2">{t('grounding.title')}</h2>
                        <p className="text-sky-600 dark:text-sky-300 mb-10 text-sm">{t('grounding.subtitle')}</p>

                        <div className="bg-white/50 dark:bg-slate-800/50 rounded-2xl p-8 shadow-sm mb-8 min-h-[200px] flex flex-col items-center justify-center">
                            {(() => {
                                const StepIcon = groundingSteps[groundingStep].icon;
                                return (
                                    <motion.div
                                        key={groundingStep}
                                        initial={{ opacity: 0, scale: 0.9 }}
                                        animate={{ opacity: 1, scale: 1 }}
                                        transition={{ duration: 0.3 }}
                                        className="text-center"
                                    >
                                        <div className="w-16 h-16 bg-sky-100 dark:bg-sky-900 rounded-full flex items-center justify-center mx-auto mb-4 text-sky-600 dark:text-sky-300">
                                            <StepIcon size={32} />
                                        </div>
                                        <h3 className="text-4xl font-bold text-sky-800 dark:text-sky-200 mb-2">
                                            {groundingSteps[groundingStep].count}
                                        </h3>
                                        <p className="text-lg font-medium text-sky-700 dark:text-sky-300 mb-1">
                                            {groundingSteps[groundingStep].text}
                                        </p>
                                        <p className="text-sky-500 dark:text-sky-400 text-sm">
                                            {groundingSteps[groundingStep].desc}
                                        </p>
                                    </motion.div>
                                );
                            })()}
                        </div>

                        <div className="flex justify-center gap-4">
                            <button
                                onClick={() => setGroundingStep(prev => Math.max(0, prev - 1))}
                                disabled={groundingStep === 0}
                                className="px-4 py-2 rounded-lg text-sky-600 dark:text-sky-400 disabled:opacity-30 hover:bg-sky-100 dark:hover:bg-sky-900"
                            >
                                {t('grounding.previous')}
                            </button>
                            <button
                                onClick={() => setGroundingStep(prev => Math.min(groundingSteps.length - 1, prev + 1))}
                                disabled={groundingStep === groundingSteps.length - 1}
                                className="px-6 py-2 bg-sky-600 text-white rounded-lg hover:bg-sky-700 disabled:opacity-50"
                            >
                                {t('grounding.next')}
                            </button>
                        </div>
                    </motion.div>
                ) : (
                    <motion.div
                        key="focus"
                        initial={{ opacity: 0, scale: 0.9 }}
                        animate={{ opacity: 1, scale: 1 }}
                        className="mb-12"
                    >
                        <h2 className="text-3xl font-light text-sky-900 dark:text-sky-100 mb-4">{t('focus.title')}</h2>
                        <p className="text-sky-600 dark:text-sky-300 mb-10 text-sm">{t('focus.subtitle')}</p>
                        
                        {focusHabit ? (
                            <div className="bg-white dark:bg-slate-800 rounded-2xl p-8 shadow-xl mb-8 border-2 border-sky-100 dark:border-sky-900">
                                <h3 className="text-2xl font-bold text-slate-800 dark:text-white mb-4">{focusHabit.name}</h3>
                                {focusHabit.twoMinuteVersion && (
                                    <div className="bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300 px-4 py-2 rounded-lg inline-block mb-4 font-medium">
                                        🌱 {t('focus.twoMinute')}: {focusHabit.twoMinuteVersion}
                                    </div>
                                )}
                                <p className="text-slate-500 dark:text-slate-400 italic">"{focusHabit.cueImplementationIntention || t('focus.start')}"</p>
                            </div>
                        ) : (
                             <div className="bg-white dark:bg-slate-800 rounded-2xl p-8 shadow-xl mb-8 border-2 border-sky-100 dark:border-sky-900">
                                <h3 className="text-xl font-medium text-slate-600 dark:text-slate-300">{t('focus.caughtUp')}</h3>
                                <p className="text-slate-400 dark:text-slate-500 mt-2">{t('focus.break')}</p>
                            </div>
                        )}
                        
                        {focusHabit ? (
                            <button
                                onClick={handleCompleteFocus}
                                className="bg-green-500 text-white px-8 py-4 rounded-full hover:bg-green-600 transition-all shadow-lg shadow-green-200 dark:shadow-green-900/30 text-lg font-bold flex items-center justify-center gap-2 mx-auto w-full max-w-xs hover:scale-105 active:scale-95"
                            >
                                <Check className="w-6 h-6" />
                                {t('focus.didIt')}
                            </button>
                        ) : (
                             <button
                                onClick={() => setIsOpen(false)}
                                className="bg-sky-600 text-white px-8 py-3 rounded-full hover:bg-sky-700 transition-colors"
                            >
                                {t('close')}
                            </button>
                        )}
                    </motion.div>
                )}

                {mode !== 'focus' && (
                    <>
                        <motion.p 
                            initial={{ opacity: 0 }}
                            animate={{ opacity: 1 }}
                            transition={{ delay: 1 }}
                            className="text-sky-800 dark:text-sky-200 text-lg mb-10 font-light leading-relaxed"
                        >
                            {t('message.text1')}<br/>
                            {t('message.text2')}
                        </motion.p>

                        <motion.button
                            initial={{ y: 20, opacity: 0 }}
                            animate={{ y: 0, opacity: 1 }}
                            transition={{ delay: 2 }}
                            onClick={() => setMode('focus')}
                            className="bg-sky-600 text-white px-8 py-3 rounded-full hover:bg-sky-700 transition-colors shadow-lg shadow-sky-200 dark:shadow-sky-900/30 text-sm font-medium tracking-wide flex items-center justify-center gap-2 mx-auto"
                        >
                            {t('message.ready')}
                            <ArrowRight className="w-4 h-4" />
                        </motion.button>
                    </>
                )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
};

export default PanicMode;
