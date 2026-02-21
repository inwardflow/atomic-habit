import React, { useState } from 'react';
import api from '../api/axios';
import { useAuthStore } from '../store/authStore';
import { useHabits } from '../hooks/useHabits';
import toast from 'react-hot-toast';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowRight, Check, Sparkles, Feather } from 'lucide-react';
import confetti from 'canvas-confetti';
import { useTranslation } from 'react-i18next';

interface IdentityModalProps {
  onClose: () => void;
}

const IdentityModal: React.FC<IdentityModalProps> = ({ onClose }) => {
  const { t } = useTranslation('identity_modal');
  const [step, setStep] = useState(1);
  const [identity, setIdentity] = useState('');
  const [habitName, setHabitName] = useState('');
  const [loading, setLoading] = useState(false);
  const { user, setUser } = useAuthStore();
  const { addHabits } = useHabits();

  const handleIdentitySubmit = async () => {
    if (!identity.trim()) return;
    setLoading(true);
    try {
      const response = await api.put('/users/me', { identityStatement: identity });
      setUser({ ...user!, identityStatement: response.data.identityStatement });
      setStep(2);
    } catch {
      toast.error(t('error_identity'));
    } finally {
      setLoading(false);
    }
  };

  const handleHabitSubmit = async () => {
      if (!habitName.trim()) return;
      setLoading(true);
      try {
          await addHabits([{
              name: habitName,
              twoMinuteVersion: habitName, // Simple start
              cueImplementationIntention: "When I start my day",
              cueHabitStack: "After I wake up",
              frequency: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
          }]);
          
          confetti({
              particleCount: 150,
              spread: 100,
              origin: { y: 0.6 },
              colors: ['#818cf8', '#34d399', '#fbbf24']
          });
          
          toast.success(t('success_habit'), { icon: '🌱' });
          onClose();
      } catch {
          toast.error(t('error_habit'));
      } finally {
          setLoading(false);
      }
  };

  return (
    <div className="fixed inset-0 bg-slate-900/80 backdrop-blur-md flex items-center justify-center z-50 p-4">
      <motion.div 
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        className="bg-white dark:bg-slate-800 rounded-3xl w-full max-w-xl shadow-2xl dark:shadow-black/50 overflow-hidden relative"
      >
        {/* Decorative background */}
        <div className="absolute top-0 right-0 w-64 h-64 bg-indigo-50 dark:bg-indigo-900/20 rounded-full -mr-20 -mt-20 blur-3xl opacity-50"></div>
        <div className="absolute bottom-0 left-0 w-64 h-64 bg-green-50 dark:bg-green-900/20 rounded-full -ml-20 -mb-20 blur-3xl opacity-50"></div>

        <div className="relative z-10 p-8 md:p-12">
            <AnimatePresence mode="wait">
                {step === 1 ? (
                    <motion.div
                        key="step1"
                        initial={{ opacity: 0, x: 20 }}
                        animate={{ opacity: 1, x: 0 }}
                        exit={{ opacity: 0, x: -20 }}
                    >
                        <div className="w-12 h-12 bg-indigo-100 dark:bg-indigo-900/50 rounded-2xl flex items-center justify-center mb-6 text-indigo-600 dark:text-indigo-400">
                            <Sparkles size={24} />
                        </div>
                        <h2 className="text-3xl font-bold text-slate-900 dark:text-white mb-3">{t('step1_title')}</h2>
                        <p className="text-slate-500 dark:text-slate-400 mb-8 text-lg leading-relaxed" dangerouslySetInnerHTML={{ __html: t('step1_subtitle') }}></p>
                        <p className="text-slate-500 dark:text-slate-400 mb-8 text-lg leading-relaxed" dangerouslySetInnerHTML={{ __html: t('step1_instruction') }}></p>
                        
                        <div className="mb-8">
                            <label className="block text-sm font-semibold text-indigo-900 dark:text-indigo-300 mb-3 uppercase tracking-wide">{t('step1_label')}</label>
                            <input
                                className="w-full border-0 bg-slate-50 dark:bg-slate-900/50 p-5 rounded-2xl text-xl text-slate-900 dark:text-white placeholder-slate-300 dark:placeholder-slate-600 focus:ring-2 focus:ring-indigo-100 dark:focus:ring-indigo-900 transition-all shadow-inner"
                                placeholder={t('step1_placeholder')}
                                value={identity}
                                onChange={(e) => setIdentity(e.target.value)}
                                onKeyDown={(e) => e.key === 'Enter' && handleIdentitySubmit()}
                                autoFocus
                            />
                        </div>

                        <button 
                            onClick={handleIdentitySubmit} 
                            disabled={loading || !identity.trim()}
                            className="w-full bg-indigo-600 text-white px-6 py-4 rounded-2xl font-bold text-lg hover:bg-indigo-700 transition-all shadow-lg shadow-indigo-200 dark:shadow-indigo-900/30 disabled:opacity-50 disabled:shadow-none flex items-center justify-center gap-2 group"
                        >
                            {loading ? t('step1_button_saving') : t('step1_button')}
                            <ArrowRight className="w-5 h-5 group-hover:translate-x-1 transition-transform" />
                        </button>
                    </motion.div>
                ) : (
                    <motion.div
                        key="step2"
                        initial={{ opacity: 0, x: 20 }}
                        animate={{ opacity: 1, x: 0 }}
                        exit={{ opacity: 0, x: -20 }}
                    >
                        <div className="w-12 h-12 bg-green-100 dark:bg-green-900/50 rounded-2xl flex items-center justify-center mb-6 text-green-600 dark:text-green-400">
                            <Feather size={24} />
                        </div>
                        <h2 className="text-3xl font-bold text-slate-900 dark:text-white mb-3">{t('step2_title')}</h2>
                        <p className="text-slate-500 dark:text-slate-400 mb-8 text-lg leading-relaxed" dangerouslySetInnerHTML={{ __html: t('step2_subtitle', { identity }) }}></p>
                        
                        <div className="mb-8">
                            <label className="block text-sm font-semibold text-green-800 dark:text-green-300 mb-3 uppercase tracking-wide">{t('step2_label')}</label>
                            <input
                                className="w-full border-0 bg-slate-50 dark:bg-slate-900/50 p-5 rounded-2xl text-xl text-slate-900 dark:text-white placeholder-slate-300 dark:placeholder-slate-600 focus:ring-2 focus:ring-green-100 dark:focus:ring-green-900 transition-all shadow-inner"
                                placeholder={t('step2_placeholder')}
                                value={habitName}
                                onChange={(e) => setHabitName(e.target.value)}
                                onKeyDown={(e) => e.key === 'Enter' && handleHabitSubmit()}
                                autoFocus
                            />
                            <p className="text-xs text-slate-400 dark:text-slate-500 mt-3 ml-1">
                                {t('step2_tip')}
                            </p>
                        </div>

                        <button 
                            onClick={handleHabitSubmit} 
                            disabled={loading || !habitName.trim()}
                            className="w-full bg-green-600 text-white px-6 py-4 rounded-2xl font-bold text-lg hover:bg-green-700 transition-all shadow-lg shadow-green-200 dark:shadow-green-900/30 disabled:opacity-50 disabled:shadow-none flex items-center justify-center gap-2 group"
                        >
                            {loading ? t('step2_button_creating') : t('step2_button')}
                            <Check className="w-5 h-5 group-hover:scale-110 transition-transform" />
                        </button>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
      </motion.div>
    </div>
  );
};

export default IdentityModal;
