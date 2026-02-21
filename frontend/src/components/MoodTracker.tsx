import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { 
    Zap, Target, BatteryLow, Wind, Smile, Flame, Minus, 
    MessageSquare, Check, Sun, CloudRain
} from 'lucide-react';
import api from '../api/axios';
import toast from 'react-hot-toast';
import confetti from 'canvas-confetti';
import { useTranslation } from 'react-i18next';

const MOODS = [
    { id: 'MOTIVATED', icon: Zap, color: 'text-yellow-500', bg: 'bg-yellow-50 dark:bg-yellow-900/20' },
    { id: 'FOCUSED', icon: Target, color: 'text-blue-500', bg: 'bg-blue-50 dark:bg-blue-900/20' },
    { id: 'HAPPY', icon: Smile, color: 'text-green-500', bg: 'bg-green-50 dark:bg-green-900/20' },
    { id: 'NEUTRAL', icon: Minus, color: 'text-gray-500', bg: 'bg-gray-50 dark:bg-gray-800' },
    { id: 'TIRED', icon: BatteryLow, color: 'text-purple-500', bg: 'bg-purple-50 dark:bg-purple-900/20' },
    { id: 'SAD', icon: CloudRain, color: 'text-indigo-400', bg: 'bg-indigo-50 dark:bg-indigo-900/20' },
    { id: 'ANXIOUS', icon: Wind, color: 'text-orange-400', bg: 'bg-orange-50 dark:bg-orange-900/20' },
    { id: 'ANGRY', icon: Flame, color: 'text-red-500', bg: 'bg-red-50 dark:bg-red-900/20' },
];

const MoodTracker = () => {
    const { t } = useTranslation();
    const [selectedMood, setSelectedMood] = useState<string | null>(null);
    const [note, setNote] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [showNoteInput, setShowNoteInput] = useState(false);

    const handleMoodSelect = (moodId: string) => {
        setSelectedMood(moodId);
        setShowNoteInput(true);
    };

    const handleSubmit = async () => {
        if (!selectedMood) return;

        setIsSubmitting(true);
        try {
            await api.post('/moods', { 
                moodType: selectedMood, 
                note: note.trim() 
            });
            
            toast.success(t('mood.success'), { icon: '📝' });
            
            if (['MOTIVATED', 'HAPPY', 'FOCUSED'].includes(selectedMood)) {
                confetti({
                    particleCount: 30,
                    spread: 50,
                    origin: { y: 0.7 },
                    colors: ['#FCD34D', '#34D399', '#60A5FA']
                });
            }

            // Reset
            setSelectedMood(null);
            setNote('');
            setShowNoteInput(false);
        } catch {
            toast.error(t('mood.error'));
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleCancel = () => {
        setSelectedMood(null);
        setNote('');
        setShowNoteInput(false);
    };

    return (
        <div className="bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-sm border border-slate-100 dark:border-slate-700 transition-colors duration-300">
            <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-4 flex items-center gap-2">
                <Sun className="w-5 h-5 text-orange-400" />
                {t('mood.title')}
            </h3>

            <div className="grid grid-cols-4 sm:grid-cols-8 gap-2 mb-4">
                {MOODS.map((mood) => {
                    const Icon = mood.icon;
                    const isSelected = selectedMood === mood.id;
                    const typeKey = mood.id === 'ANGRY' ? 'frustrated' : mood.id.toLowerCase();
                    return (
                        <button
                            key={mood.id}
                            onClick={() => handleMoodSelect(mood.id)}
                            className={`flex flex-col items-center justify-center p-3 rounded-xl transition-all ${
                                isSelected 
                                    ? 'ring-2 ring-indigo-500 bg-indigo-50 dark:bg-indigo-900/30 scale-105' 
                                    : 'hover:bg-slate-50 dark:hover:bg-slate-700/50'
                            }`}
                        >
                            <div className={`w-8 h-8 rounded-full flex items-center justify-center mb-2 ${mood.bg} ${mood.color}`}>
                                <Icon size={18} />
                            </div>
                            <span className="text-[10px] font-medium text-slate-600 dark:text-slate-300">{t(`mood.types.${typeKey}` as any)}</span>
                        </button>
                    );
                })}
            </div>

            <AnimatePresence>
                {showNoteInput && (
                    <motion.div
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: 'auto' }}
                        exit={{ opacity: 0, height: 0 }}
                        className="overflow-hidden"
                    >
                        <div className="bg-slate-50 dark:bg-slate-900/50 rounded-xl p-4 border border-slate-100 dark:border-slate-700">
                            <div className="flex items-center gap-2 mb-2 text-sm text-slate-500 dark:text-slate-400">
                                <MessageSquare size={14} />
                                <span>{t('mood.add_note')}</span>
                            </div>
                            <textarea
                                value={note}
                                onChange={(e) => setNote(e.target.value)}
                                placeholder={t('mood.placeholder')}
                                className="w-full bg-white dark:bg-slate-800 border-0 rounded-lg p-3 text-sm text-slate-900 dark:text-white placeholder-slate-400 focus:ring-2 focus:ring-indigo-500 mb-3 resize-none h-20"
                                onKeyDown={(e) => {
                                    if (e.key === 'Enter' && !e.shiftKey) {
                                        e.preventDefault();
                                        handleSubmit();
                                    }
                                }}
                            />
                            <div className="flex justify-end gap-2">
                                <button
                                    onClick={handleCancel}
                                    className="px-3 py-1.5 text-xs font-medium text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
                                >
                                    {t('mood.cancel_btn')}
                                </button>
                                <button
                                    onClick={handleSubmit}
                                    disabled={isSubmitting}
                                    className="px-4 py-1.5 bg-indigo-600 text-white text-xs font-medium rounded-lg hover:bg-indigo-700 disabled:opacity-50 flex items-center gap-1"
                                >
                                    {isSubmitting ? t('mood.saving_btn') : <>{t('mood.save_btn')} <Check size={12} /></>}
                                </button>
                            </div>
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
};

export default MoodTracker;
