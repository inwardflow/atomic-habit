import React from 'react';
import { useTranslation } from 'react-i18next';
import { Trophy, TrendingUp } from 'lucide-react';

interface WeeklyReviewCardProps {
  stats: {
    totalCompleted: number;
    currentStreak: number;
    bestStreak?: number;
  };
  highlights: string[];
  suggestion: string;
}

const WeeklyReviewCard: React.FC<WeeklyReviewCardProps> = ({ stats, highlights, suggestion }) => {
  const { t } = useTranslation('coach');

  return (
    <div className="bg-white rounded-xl shadow-sm border border-purple-100 overflow-hidden max-w-md w-full">
      <div className="bg-gradient-to-r from-purple-500 to-indigo-600 p-4 text-white">
        <h3 className="font-bold text-lg flex items-center gap-2">
          <Trophy className="w-5 h-5 text-yellow-300" />
          {t('card.weekly.title')}
        </h3>
        <p className="text-purple-100 text-sm opacity-90">{t('card.weekly.subtitle')}</p>
      </div>
      
      <div className="p-4 space-y-4">
        {/* Stats Row */}
        <div className="flex justify-around bg-purple-50 rounded-lg p-3">
          <div className="text-center">
            <div className="text-2xl font-bold text-purple-700">{stats.totalCompleted}</div>
            <div className="text-xs text-gray-500 uppercase font-medium">{t('card.weekly.completions')}</div>
          </div>
          <div className="h-full w-px bg-purple-200 mx-2"></div>
          <div className="text-center">
            <div className="text-2xl font-bold text-indigo-700">{stats.currentStreak}</div>
            <div className="text-xs text-gray-500 uppercase font-medium">{t('card.weekly.streak')}</div>
          </div>
        </div>

        {/* Highlights */}
        {highlights.length > 0 && (
          <div>
            <h4 className="text-sm font-semibold text-gray-700 mb-2 flex items-center gap-1">
              <TrendingUp className="w-4 h-4 text-green-500" />
              {t('card.weekly.highlights')}
            </h4>
            <ul className="space-y-1">
              {highlights.map((highlight, idx) => (
                <li key={idx} className="text-sm text-gray-600 flex items-start gap-2">
                  <span className="text-green-500 mt-1">●</span>
                  {highlight}
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* Coach Suggestion */}
        <div className="bg-yellow-50 border border-yellow-100 rounded-lg p-3 text-sm text-yellow-800">
            <span className="font-semibold block mb-1">💡 {t('card.weekly.note')}</span>
            {suggestion}
        </div>
      </div>
    </div>
  );
};

export default WeeklyReviewCard;
