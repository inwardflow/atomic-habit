import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Target, CheckCircle2 } from 'lucide-react';

interface DailyFocusCardProps {
  habitName: string;
  twoMinuteVersion: string;
  onComplete: (habitName: string) => void;
}

const DailyFocusCard: React.FC<DailyFocusCardProps> = ({ habitName, twoMinuteVersion, onComplete }) => {
  const { t } = useTranslation('coach');
  const [completed, setCompleted] = useState(false);

  const handleComplete = () => {
    setCompleted(true);
    // Add small delay for animation before notifying parent
    setTimeout(() => {
        onComplete(habitName);
    }, 1500);
  };

  if (completed) {
      return (
          <div className="bg-green-50 border border-green-200 rounded-xl p-6 text-center animate-in fade-in zoom-in duration-500">
              <CheckCircle2 className="w-16 h-16 text-green-500 mx-auto mb-3 animate-bounce" />
              <h3 className="text-xl font-bold text-green-800">{t('card.focus.success_title')}</h3>
              <p className="text-green-600 mt-1">{t('card.focus.success_subtitle')}</p>
          </div>
      );
  }

  return (
    <div className="bg-white rounded-xl shadow-lg border-2 border-blue-100 overflow-hidden max-w-md w-full transform transition-all hover:scale-[1.01]">
      <div className="bg-blue-600 p-4 text-white">
        <h3 className="font-bold text-lg flex items-center gap-2">
          <Target className="w-5 h-5 text-blue-200" />
          {t('card.focus.title')}
        </h3>
        <p className="text-blue-100 text-sm opacity-90">{t('card.focus.subtitle')}</p>
      </div>
      
      <div className="p-6 text-center space-y-4">
        <div>
            <div className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-1">{t('card.focus.mission')}</div>
            <div className="text-2xl font-bold text-gray-800">{twoMinuteVersion}</div>
            <div className="text-sm text-gray-500 mt-1">({habitName})</div>
        </div>

        <div className="pt-4">
            <button
                onClick={handleComplete}
                className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 px-6 rounded-lg shadow-md hover:shadow-lg transition-all flex items-center justify-center gap-2 group"
            >
                <CheckCircle2 className="w-5 h-5 group-hover:scale-110 transition-transform" />
                {t('card.focus.did_it')}
            </button>
            <p className="text-xs text-gray-400 mt-3">
                {t('card.focus.worry')}
            </p>
        </div>
      </div>
    </div>
  );
};

export default DailyFocusCard;
