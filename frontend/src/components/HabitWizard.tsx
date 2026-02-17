import React, { useState } from 'react';
import api from '../api/axios';
import type { HabitRequest } from '../types';
import toast from 'react-hot-toast';

interface HabitWizardProps {
  onClose: () => void;
  onSuccess: () => void;
}

const HabitWizard: React.FC<HabitWizardProps> = ({ onClose, onSuccess }) => {
  const [step, setStep] = useState(1);
  const [data, setData] = useState<HabitRequest>({
    name: '',
    twoMinuteVersion: '',
    cueImplementationIntention: '',
    cueHabitStack: ''
  });
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    if (!data.name) {
        toast.error('Habit name is required');
        return;
    }
    setLoading(true);
    try {
      await api.post('/habits', data);
      toast.success('Habit created successfully!');
      onSuccess();
      onClose();
    } catch (error) {
      console.error(error);
      toast.error('Failed to create habit');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-gray-600/50 dark:bg-black/70 flex items-center justify-center z-50 p-4">
      <div className="bg-white dark:bg-slate-800 p-6 rounded-lg w-full max-w-md shadow-xl dark:shadow-black/50 transition-colors duration-300">
        <h3 className="text-lg font-bold mb-4 text-slate-900 dark:text-white">Create New Habit (Step {step}/4)</h3>
        
        {step === 1 && (
          <div>
            <label className="block mb-2 font-medium text-slate-900 dark:text-slate-200">Habit Name</label>
            <input
              className="w-full border dark:border-slate-700 p-2 rounded mb-4 focus:ring-2 focus:ring-indigo-500 outline-none bg-white dark:bg-slate-900 text-slate-900 dark:text-white"
              value={data.name}
              onChange={(e) => setData({ ...data, name: e.target.value })}
              placeholder="e.g. Read 1 page"
              autoFocus
            />
            <div className="bg-indigo-50 dark:bg-indigo-900/30 p-3 rounded-lg mb-4 text-xs text-indigo-800 dark:text-indigo-300 italic">
               "The secret to getting results that last is to never stop making improvements."
            </div>
            <div className="flex flex-col gap-2">
                <button 
                    onClick={() => setStep(2)} 
                    className="bg-indigo-600 text-white px-4 py-2 rounded hover:bg-indigo-700 w-full font-medium"
                    disabled={!data.name}
                >
                    Continue to Plan (Recommended)
                </button>
                <button 
                    onClick={handleSubmit} 
                    className="text-gray-500 dark:text-slate-400 text-sm py-2 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors w-full"
                    disabled={!data.name || loading}
                >
                    {loading ? 'Creating...' : 'Quick Add (Skip Planning)'}
                </button>
            </div>
          </div>
        )}
        
        {step === 2 && (
          <div>
            <label className="block mb-2 font-medium text-slate-900 dark:text-slate-200">Make it Easy (2-Minute Version)</label>
            <p className="text-sm text-gray-500 dark:text-slate-400 mb-2">Scale it down to just 2 minutes.</p>
            <input
              className="w-full border dark:border-slate-700 p-2 rounded mb-4 focus:ring-2 focus:ring-indigo-500 outline-none bg-white dark:bg-slate-900 text-slate-900 dark:text-white"
              value={data.twoMinuteVersion}
              onChange={(e) => setData({ ...data, twoMinuteVersion: e.target.value })}
              placeholder="e.g. Open the book"
              autoFocus
            />
             <div className="bg-indigo-50 dark:bg-indigo-900/30 p-3 rounded-lg mb-4 text-xs text-indigo-800 dark:text-indigo-300 italic">
               "A new habit should not feel like a challenge. The actions that follow can be challenging, but the first two minutes should be easy."
            </div>
            <div className="flex gap-2">
                <button onClick={() => setStep(1)} className="bg-gray-200 dark:bg-slate-700 text-gray-700 dark:text-slate-300 px-4 py-2 rounded hover:bg-gray-300 dark:hover:bg-slate-600">Back</button>
                <button onClick={() => setStep(3)} className="bg-indigo-600 text-white px-4 py-2 rounded hover:bg-indigo-700 flex-1">Next</button>
            </div>
          </div>
        )}
        
        {step === 3 && (
          <div>
            <label className="block mb-2 font-medium text-slate-900 dark:text-slate-200">Make it Obvious (Implementation Intention)</label>
            <p className="text-sm text-gray-500 dark:text-slate-400 mb-2">I will [BEHAVIOR] at [TIME] in [LOCATION].</p>
            <input
              className="w-full border dark:border-slate-700 p-2 rounded mb-4 focus:ring-2 focus:ring-indigo-500 outline-none bg-white dark:bg-slate-900 text-slate-900 dark:text-white"
              placeholder="I will read at 8pm in my bedroom"
              value={data.cueImplementationIntention}
              onChange={(e) => setData({ ...data, cueImplementationIntention: e.target.value })}
              autoFocus
            />
             <div className="bg-indigo-50 dark:bg-indigo-900/30 p-3 rounded-lg mb-4 text-xs text-indigo-800 dark:text-indigo-300 italic">
               "Many people think they lack motivation when what they really lack is clarity."
            </div>
             <div className="flex gap-2">
                <button onClick={() => setStep(2)} className="bg-gray-200 dark:bg-slate-700 text-gray-700 dark:text-slate-300 px-4 py-2 rounded hover:bg-gray-300 dark:hover:bg-slate-600">Back</button>
                <button onClick={() => setStep(4)} className="bg-indigo-600 text-white px-4 py-2 rounded hover:bg-indigo-700 flex-1">Next</button>
            </div>
          </div>
        )}
        
        {step === 4 && (
          <div>
            <label className="block mb-2 font-medium text-slate-900 dark:text-slate-200">Make it Obvious (Habit Stacking)</label>
             <p className="text-sm text-gray-500 dark:text-slate-400 mb-2">After I [CURRENT HABIT], I will [NEW HABIT].</p>
            <input
              className="w-full border dark:border-slate-700 p-2 rounded mb-4 focus:ring-2 focus:ring-indigo-500 outline-none bg-white dark:bg-slate-900 text-slate-900 dark:text-white"
              placeholder="After I pour my coffee, I will read 1 page"
              value={data.cueHabitStack}
              onChange={(e) => setData({ ...data, cueHabitStack: e.target.value })}
              autoFocus
            />
             <div className="bg-indigo-50 dark:bg-indigo-900/30 p-3 rounded-lg mb-4 text-xs text-indigo-800 dark:text-indigo-300 italic">
               "One of the best ways to build a new habit is to identify a current habit you already do each day and then stack your new behavior on top."
            </div>
             <div className="flex gap-2">
                <button onClick={() => setStep(3)} className="bg-gray-200 dark:bg-slate-700 text-gray-700 dark:text-slate-300 px-4 py-2 rounded hover:bg-gray-300 dark:hover:bg-slate-600">Back</button>
                <button 
                    onClick={handleSubmit} 
                    disabled={loading}
                    className="bg-green-600 text-white px-4 py-2 rounded hover:bg-green-700 flex-1 disabled:opacity-50"
                >
                    {loading ? 'Creating...' : 'Create Habit'}
                </button>
            </div>
          </div>
        )}
        
        <button onClick={onClose} className="mt-4 w-full text-center text-gray-500 dark:text-slate-500 hover:text-gray-700 dark:hover:text-slate-300 text-sm transition-colors">Cancel</button>
      </div>
    </div>
  );
};

export default HabitWizard;
