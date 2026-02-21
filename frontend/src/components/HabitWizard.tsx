import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import api from '../api/axios';
import type { HabitRequest } from '../types';
import toast from 'react-hot-toast';
import clsx from 'clsx';

interface HabitWizardProps {
  onClose: () => void;
  onSuccess: () => void;
}

const HabitWizard: React.FC<HabitWizardProps> = ({ onClose, onSuccess }) => {
  const { t } = useTranslation('habits');
  const [step, setStep] = useState(1);
  const [data, setData] = useState<HabitRequest>({
    name: '',
    twoMinuteVersion: '',
    cueImplementationIntention: '',
    cueHabitStack: ''
  });
  const [selectedDays, setSelectedDays] = useState<string[]>([]);
  const [isDaily, setIsDaily] = useState(true);
  const [loading, setLoading] = useState(false);

  const DAYS = [
    { key: 'MONDAY', label: t('days.MONDAY') },
    { key: 'TUESDAY', label: t('days.TUESDAY') },
    { key: 'WEDNESDAY', label: t('days.WEDNESDAY') },
    { key: 'THURSDAY', label: t('days.THURSDAY') },
    { key: 'FRIDAY', label: t('days.FRIDAY') },
    { key: 'SATURDAY', label: t('days.SATURDAY') },
    { key: 'SUNDAY', label: t('days.SUNDAY') },
  ];

  const toggleDay = (day: string) => {
    setSelectedDays(prev =>
      prev.includes(day) ? prev.filter(d => d !== day) : [...prev, day]
    );
  };

  const handleSubmit = async () => {
    if (!data.name) {
        toast.error(t('wizard.toast.name_required'));
        return;
    }
    setLoading(true);
    try {
      const payload: HabitRequest = {
        ...data,
        frequency: isDaily ? undefined : selectedDays.length > 0 ? selectedDays : undefined,
      };
      await api.post('/habits', payload);
      toast.success(t('wizard.toast.success'));
      onSuccess();
      onClose();
    } catch (error) {
      console.error(error);
      toast.error(t('wizard.toast.failed'));
    } finally {
      setLoading(false);
    }
  };

  const totalSteps = 5;

  return (
    <div className="fixed inset-0 bg-gray-600/50 dark:bg-black/70 flex items-center justify-center z-50 p-4">
      <div className="bg-white dark:bg-slate-800 p-6 rounded-lg w-full max-w-md shadow-xl dark:shadow-black/50 transition-colors duration-300">
        <h3 className="text-lg font-bold mb-4 text-slate-900 dark:text-white">{t('wizard.title', { step, total: totalSteps })}</h3>
        
        {step === 1 && (
          <div>
            <label className="block mb-2 font-medium text-slate-900 dark:text-slate-200">{t('wizard.step1.label')}</label>
            <input
              className="w-full border dark:border-slate-700 p-2 rounded mb-4 focus:ring-2 focus:ring-indigo-500 outline-none bg-white dark:bg-slate-900 text-slate-900 dark:text-white"
              value={data.name}
              onChange={(e) => setData({ ...data, name: e.target.value })}
              placeholder={t('wizard.step1.placeholder')}
              autoFocus
            />
            <div className="bg-indigo-50 dark:bg-indigo-900/30 p-3 rounded-lg mb-4 text-xs text-indigo-800 dark:text-indigo-300 italic">
               {t('wizard.step1.quote')}
            </div>
            <div className="flex flex-col gap-2">
                <button 
                    onClick={() => setStep(2)} 
                    className="bg-indigo-600 text-white px-4 py-2 rounded hover:bg-indigo-700 w-full font-medium"
                    disabled={!data.name}
                >
                    {t('wizard.step1.continue')}
                </button>
                <button 
                    onClick={handleSubmit} 
                    className="text-gray-500 dark:text-slate-400 text-sm py-2 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors w-full"
                    disabled={!data.name || loading}
                >
                    {loading ? t('wizard.step1.creating') : t('wizard.step1.quick_add')}
                </button>
            </div>
          </div>
        )}
        
        {step === 2 && (
          <div>
            <label className="block mb-2 font-medium text-slate-900 dark:text-slate-200">{t('wizard.step2.label')}</label>
            <p className="text-sm text-gray-500 dark:text-slate-400 mb-2">{t('wizard.step2.subtitle')}</p>
            <input
              className="w-full border dark:border-slate-700 p-2 rounded mb-4 focus:ring-2 focus:ring-indigo-500 outline-none bg-white dark:bg-slate-900 text-slate-900 dark:text-white"
              value={data.twoMinuteVersion}
              onChange={(e) => setData({ ...data, twoMinuteVersion: e.target.value })}
              placeholder={t('wizard.step2.placeholder')}
              autoFocus
            />
             <div className="bg-indigo-50 dark:bg-indigo-900/30 p-3 rounded-lg mb-4 text-xs text-indigo-800 dark:text-indigo-300 italic">
               {t('wizard.step2.quote')}
            </div>
            <div className="flex gap-2">
                <button onClick={() => setStep(1)} className="bg-gray-200 dark:bg-slate-700 text-gray-700 dark:text-slate-300 px-4 py-2 rounded hover:bg-gray-300 dark:hover:bg-slate-600">{t('wizard.back')}</button>
                <button onClick={() => setStep(3)} className="bg-indigo-600 text-white px-4 py-2 rounded hover:bg-indigo-700 flex-1">{t('wizard.next')}</button>
            </div>
          </div>
        )}
        
        {step === 3 && (
          <div>
            <label className="block mb-2 font-medium text-slate-900 dark:text-slate-200">{t('wizard.step3.label')}</label>
            <p className="text-sm text-gray-500 dark:text-slate-400 mb-2">{t('wizard.step3.subtitle')}</p>
            <input
              className="w-full border dark:border-slate-700 p-2 rounded mb-4 focus:ring-2 focus:ring-indigo-500 outline-none bg-white dark:bg-slate-900 text-slate-900 dark:text-white"
              placeholder={t('wizard.step3.placeholder')}
              value={data.cueImplementationIntention}
              onChange={(e) => setData({ ...data, cueImplementationIntention: e.target.value })}
              autoFocus
            />
             <div className="bg-indigo-50 dark:bg-indigo-900/30 p-3 rounded-lg mb-4 text-xs text-indigo-800 dark:text-indigo-300 italic">
               {t('wizard.step3.quote')}
            </div>
             <div className="flex gap-2">
                <button onClick={() => setStep(2)} className="bg-gray-200 dark:bg-slate-700 text-gray-700 dark:text-slate-300 px-4 py-2 rounded hover:bg-gray-300 dark:hover:bg-slate-600">{t('wizard.back')}</button>
                <button onClick={() => setStep(4)} className="bg-indigo-600 text-white px-4 py-2 rounded hover:bg-indigo-700 flex-1">{t('wizard.next')}</button>
            </div>
          </div>
        )}
        
        {step === 4 && (
          <div>
            <label className="block mb-2 font-medium text-slate-900 dark:text-slate-200">{t('wizard.step4.label')}</label>
             <p className="text-sm text-gray-500 dark:text-slate-400 mb-2">{t('wizard.step4.subtitle')}</p>
            <input
              className="w-full border dark:border-slate-700 p-2 rounded mb-4 focus:ring-2 focus:ring-indigo-500 outline-none bg-white dark:bg-slate-900 text-slate-900 dark:text-white"
              placeholder={t('wizard.step4.placeholder')}
              value={data.cueHabitStack}
              onChange={(e) => setData({ ...data, cueHabitStack: e.target.value })}
              autoFocus
            />
             <div className="bg-indigo-50 dark:bg-indigo-900/30 p-3 rounded-lg mb-4 text-xs text-indigo-800 dark:text-indigo-300 italic">
               {t('wizard.step4.quote')}
            </div>
             <div className="flex gap-2">
                <button onClick={() => setStep(3)} className="bg-gray-200 dark:bg-slate-700 text-gray-700 dark:text-slate-300 px-4 py-2 rounded hover:bg-gray-300 dark:hover:bg-slate-600">{t('wizard.back')}</button>
                <button onClick={() => setStep(5)} className="bg-indigo-600 text-white px-4 py-2 rounded hover:bg-indigo-700 flex-1">{t('wizard.next')}</button>
            </div>
          </div>
        )}

        {step === 5 && (
          <div>
            <label className="block mb-2 font-medium text-slate-900 dark:text-slate-200">{t('wizard.step5.label')}</label>
            <p className="text-sm text-gray-500 dark:text-slate-400 mb-4">{t('wizard.step5.subtitle')}</p>
            
            <div className="flex gap-3 mb-4">
              <button
                onClick={() => setIsDaily(true)}
                className={clsx(
                  "flex-1 py-2.5 rounded-lg text-sm font-medium transition-all border",
                  isDaily
                    ? "bg-indigo-600 text-white border-indigo-600 shadow-sm"
                    : "bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-400 border-slate-200 dark:border-slate-700 hover:border-indigo-300"
                )}
              >
                {t('wizard.step5.every_day')}
              </button>
              <button
                onClick={() => setIsDaily(false)}
                className={clsx(
                  "flex-1 py-2.5 rounded-lg text-sm font-medium transition-all border",
                  !isDaily
                    ? "bg-indigo-600 text-white border-indigo-600 shadow-sm"
                    : "bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-400 border-slate-200 dark:border-slate-700 hover:border-indigo-300"
                )}
              >
                {t('wizard.step5.specific_days')}
              </button>
            </div>

            {!isDaily && (
              <div className="flex gap-1.5 mb-4 justify-center">
                {DAYS.map(day => (
                  <button
                    key={day.key}
                    onClick={() => toggleDay(day.key)}
                    className={clsx(
                      "w-10 h-10 rounded-full text-xs font-bold transition-all",
                      selectedDays.includes(day.key)
                        ? "bg-indigo-600 text-white shadow-sm shadow-indigo-300 dark:shadow-indigo-900"
                        : "bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/30"
                    )}
                  >
                    {day.label}
                  </button>
                ))}
              </div>
            )}

            <div className="bg-indigo-50 dark:bg-indigo-900/30 p-3 rounded-lg mb-4 text-xs text-indigo-800 dark:text-indigo-300 italic">
              {t('wizard.step5.quote')}
            </div>

            <div className="flex gap-2">
              <button onClick={() => setStep(4)} className="bg-gray-200 dark:bg-slate-700 text-gray-700 dark:text-slate-300 px-4 py-2 rounded hover:bg-gray-300 dark:hover:bg-slate-600">{t('wizard.back')}</button>
              <button 
                onClick={handleSubmit} 
                disabled={loading || (!isDaily && selectedDays.length === 0)}
                className="bg-green-600 text-white px-4 py-2 rounded hover:bg-green-700 flex-1 disabled:opacity-50"
              >
                {loading ? t('wizard.step5.creating') : t('wizard.step5.create')}
              </button>
            </div>
          </div>
        )}
        
        <button onClick={onClose} className="mt-4 w-full text-center text-gray-500 dark:text-slate-500 hover:text-gray-700 dark:hover:text-slate-300 text-sm transition-colors">{t('wizard.cancel')}</button>
      </div>
    </div>
  );
};

export default HabitWizard;
