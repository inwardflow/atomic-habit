import React from 'react';
import { useTranslation } from 'react-i18next';

const LanguageSwitcher: React.FC = () => {
  const { i18n } = useTranslation();

  const changeLanguage = (lng: string) => {
    i18n.changeLanguage(lng);
  };

  return (
    <div className="flex bg-slate-200 dark:bg-slate-700 rounded-lg p-1">
      <button
        onClick={() => changeLanguage('en')}
        className={`px-3 py-1.5 rounded-md text-sm font-medium transition-all ${
          i18n.resolvedLanguage === 'en'
            ? 'bg-white dark:bg-slate-600 text-slate-900 dark:text-white shadow-sm'
            : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'
        }`}
      >
        English
      </button>
      <button
        onClick={() => changeLanguage('zh')}
        className={`px-3 py-1.5 rounded-md text-sm font-medium transition-all ${
          i18n.resolvedLanguage === 'zh'
            ? 'bg-white dark:bg-slate-600 text-slate-900 dark:text-white shadow-sm'
            : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'
        }`}
      >
        中文
      </button>
    </div>
  );
};

export default LanguageSwitcher;
