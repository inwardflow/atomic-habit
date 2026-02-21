import { useState, useEffect, useCallback } from 'react';
import { useAuthStore } from '../store/authStore';
import { authService } from '../services/authService';
import { Plus, MessageSquare, LogOut, BarChart2, Heart, Moon, Sun, Bell, BellOff, Settings } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useHabits } from '../hooks/useHabits';
import { useGoals } from '../hooks/useGoals';
import { useHabitStats } from '../hooks/useHabitStats';
import { useThemeStore } from '../store/themeStore';
import { useNotifications } from '../hooks/useNotifications';
import HabitCard from '../components/HabitCard';
import HabitWizard from '../components/HabitWizard';
import ChatInterface from '../components/ChatInterface';
import GoalList from '../components/GoalList';
import IdentityModal from '../components/IdentityModal';
import IdentityScore from '../components/IdentityScore';
import HabitHeatmap from '../components/HabitHeatmap';
import MoodTracker from '../components/MoodTracker';
import api from '../api/axios';
import confetti from 'canvas-confetti';
import toast from 'react-hot-toast';
import PanicMode from '../components/PanicMode';

const Dashboard = () => {
  const { t } = useTranslation(['translation', 'gratitude_jar']);
  const { habits, completeHabit, uncompleteHabit, fetchHabits, updateHabit, toggleHabitStatus, deleteHabit } = useHabits();
  const { goals, fetchGoals } = useGoals();
  const { stats, completions, refetch: refetchStats } = useHabitStats();
  const [showWizard, setShowWizard] = useState(false);
  const [showChat, setShowChat] = useState(false);
  const { user, setUser } = useAuthStore();
  const [showIdentityModal, setShowIdentityModal] = useState(false);
  const [gratitude, setGratitude] = useState('');
  const [gratitudeSubmitted, setGratitudeSubmitted] = useState(false);
  const [moodId, setMoodId] = useState<number | null>(null);
  const { theme, toggleTheme } = useThemeStore();
  const { toggleNotifications, permission, notificationsEnabled } = useNotifications({ connect: false });

  // Check for today's gratitude
  useEffect(() => {
    const checkTodayGratitude = async () => {
      try {
        const res = await api.get('/moods/gratitude/today');
        if (res.status === 200 && res.data) {
          setGratitude(res.data.note);
          setGratitudeSubmitted(true);
          setMoodId(res.data.id);
        }
      } catch {
        // No gratitude found for today, which is fine
      }
    };
    checkTodayGratitude();
  }, []);

  // Fetch user profile
  const fetchUser = useCallback(async () => {
    try {
      const res = await api.get('/users/me');
      setUser(res.data);
      if (!res.data.identityStatement) {
        setShowIdentityModal(true);
      }
    } catch (err) {
      console.error(err);
    }
  }, [setUser]);

  useEffect(() => {
    fetchUser();
  }, [fetchUser]);

  const handleRefresh = () => {
    fetchHabits();
    fetchGoals();
    refetchStats();
  };
  
  const handleHabitComplete = async (id: number) => {
      await completeHabit(id);
      refetchStats();
  };
  
  const handleHabitUncomplete = async (id: number) => {
      await uncompleteHabit(id);
      refetchStats();
  };

  const handleHabitToggle = async (id: number) => {
      await toggleHabitStatus(id);
      refetchStats();
  };

  const handleHabitDelete = async (id: number) => {
      await deleteHabit(id);
      refetchStats();
  };

  const handleHabitUpdate = async (id: number, updates: Partial<{ name: string; twoMinuteVersion: string; cueImplementationIntention: string; cueHabitStack: string; frequency: string[] }>) => {
      await updateHabit(id, updates);
      refetchStats();
  };

  const handleGratitudeSubmit = async () => {
      if (!gratitude.trim()) return;
      try {
          if (moodId) {
            await api.put(`/moods/${moodId}`, { note: gratitude });
            toast.success(t('toast_updated', { ns: 'gratitude_jar' }), { icon: '✨' });
          } else {
            const res = await api.post('/moods', { moodType: 'GRATITUDE', note: gratitude });
            setMoodId(res.data.id);
            toast.success(t('toast_saved', { ns: 'gratitude_jar' }), { icon: '🙏' });
            confetti({ particleCount: 50, spread: 50, origin: { y: 0.8 }, colors: ['#FCD34D', '#F87171'] });
          }
          setGratitudeSubmitted(true);
      } catch {
          toast.error(t('toast_error', { ns: 'gratitude_jar' }));
      }
  };

  const handleLogout = async () => {
    await authService.logout();
  };
  
  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-900 transition-colors duration-300">
      {/* Navbar */}
      <nav className="sticky top-0 z-30 w-full bg-white/80 dark:bg-slate-900/80 backdrop-blur-md border-b border-slate-200 dark:border-slate-800 transition-colors duration-300">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center shadow-lg shadow-indigo-500/30">
                <span className="text-white font-bold text-lg">A</span>
              </div>
              <h1 className="text-xl font-bold text-slate-900 dark:text-white tracking-tight">{t('app.title')}</h1>
            </div>
            <div className="flex items-center gap-4">
              <button
                onClick={toggleTheme}
                className="p-2 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors"
                title={t('settings.preferences.theme')}
              >
                {theme === 'dark' ? <Sun className="w-5 h-5" /> : <Moon className="w-5 h-5" />}
              </button>
              <button
                onClick={toggleNotifications}
                className="p-2 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors relative"
                title={notificationsEnabled ? t('settings.preferences.notifications') + ': ' + t('settings.preferences.enabled') : permission === 'denied' ? t('settings.preferences.notifications') + ': ' + t('settings.preferences.blocked') : t('settings.preferences.notifications') + ': ' + t('settings.preferences.disabled')}
              >
                {notificationsEnabled ? <Bell className="w-5 h-5" /> : <BellOff className="w-5 h-5" />}
                {!notificationsEnabled && permission !== 'denied' && (
                  <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-indigo-500 rounded-full animate-pulse"></span>
                )}
              </button>
              <Link 
                to="/analytics" 
                className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 rounded-md transition-colors"
              >
                <BarChart2 className="w-4 h-4" />
                <span className="hidden sm:inline">{t('nav.analytics')}</span>
              </Link>
              <Link 
                to="/settings" 
                className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 rounded-md transition-colors"
              >
                <Settings className="w-4 h-4" />
                <span className="hidden sm:inline">{t('nav.settings')}</span>
              </Link>
              <button 
                onClick={handleLogout} 
                className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 rounded-md transition-colors"
              >
                <LogOut className="w-4 h-4" />
                <span className="hidden sm:inline">{t('nav.logout')}</span>
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8 space-y-12">
        {/* Identity & Stats Section */}
        {user?.identityStatement && (
          <section className="space-y-6">
            <IdentityScore stats={stats} identity={user.identityStatement} />
            <HabitHeatmap completions={completions} />
          </section>
        )}

        {/* Mood Tracker */}
        <MoodTracker />

        {/* Daily Gratitude Micro-Journal */}
        <section className="bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-sm border border-indigo-50 dark:border-slate-700 transition-colors duration-300">
            <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2 text-indigo-800 dark:text-indigo-300">
                    <Heart className="w-5 h-5 fill-indigo-100 dark:fill-indigo-900 text-indigo-500" />
                    <h3 className="font-semibold">{t('title', { ns: 'gratitude_jar' })}</h3>
                </div>
                <Link to="/gratitude" className="text-xs font-medium text-indigo-500 hover:text-indigo-700 dark:hover:text-indigo-300">
                    {t('view_all', { ns: 'gratitude_jar' })}
                </Link>
            </div>
            
            {!gratitudeSubmitted ? (
                <div className="flex gap-2">
                    <input 
                        type="text" 
                        value={gratitude}
                        onChange={(e) => setGratitude(e.target.value)}
                        placeholder={t('placeholder', { ns: 'gratitude_jar' })}
                        className="flex-1 border-0 bg-slate-50 dark:bg-slate-900/50 rounded-lg px-4 py-3 text-sm text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:ring-2 focus:ring-indigo-100 dark:focus:ring-indigo-900 transition-all"
                        onKeyDown={(e) => e.key === 'Enter' && handleGratitudeSubmit()}
                    />
                    <button 
                        onClick={handleGratitudeSubmit}
                        disabled={!gratitude.trim()}
                        className="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-50 transition-colors"
                    >
                        {t('save', { ns: 'gratitude_jar' })}
                    </button>
                </div>
            ) : (
                <div className="bg-indigo-50/50 dark:bg-indigo-900/20 rounded-lg p-4 flex justify-between items-center animate-fade-in border border-indigo-100 dark:border-indigo-900/30">
                    <p className="text-indigo-900 dark:text-indigo-100 italic">"{gratitude}"</p>
                    <button 
                        onClick={() => { setGratitudeSubmitted(false); }}
                        className="text-xs text-indigo-400 hover:text-indigo-600 dark:hover:text-indigo-300"
                    >
                        {t('edit', { ns: 'gratitude_jar' })}
                    </button>
                </div>
            )}
        </section>

        {/* Goals Section */}
        <section>
          <GoalList goals={goals} />
        </section>

        {/* Habits Section */}
        <section>
          <div className="flex justify-between items-end mb-6">
            <div>
              <h2 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">{t('dashboard.sections.habits')}</h2>
              <p className="text-slate-500 dark:text-slate-400 mt-1">{t('dashboard.sections.habits_subtitle')}</p>
            </div>
            <button
              onClick={() => setShowWizard(true)}
              className="inline-flex items-center px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg shadow-sm hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-600 transition-all hover:shadow-md active:scale-95"
            >
              <Plus className="w-4 h-4 mr-2" /> 
              {t('dashboard.buttons.new_habit')}
            </button>
          </div>

          {habits.length === 0 ? (
            <div className="text-center py-16 bg-white dark:bg-slate-800 rounded-2xl border border-dashed border-slate-300 dark:border-slate-700 transition-colors duration-300">
              <div className="mx-auto w-12 h-12 bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 rounded-full flex items-center justify-center mb-4">
                <Plus className="w-6 h-6" />
              </div>
              <h3 className="text-lg font-medium text-slate-900 dark:text-white">{t('dashboard.no_habits.title')}</h3>
              <p className="mt-1 text-slate-500 dark:text-slate-400">{t('dashboard.no_habits.subtitle')}</p>
              <button 
                onClick={() => setShowWizard(true)} 
                className="mt-6 text-indigo-600 dark:text-indigo-400 font-semibold hover:text-indigo-500 hover:underline"
              >
                {t('dashboard.no_habits.cta')}
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {[...habits].sort((a, b) => {
                // Scheduled today first, rest days at the bottom
                if (a.scheduledToday && !b.scheduledToday) return -1;
                if (!a.scheduledToday && b.scheduledToday) return 1;
                return 0;
              }).map((habit) => (
                <HabitCard
                    key={habit.id}
                    habit={habit}
                    onComplete={handleHabitComplete}
                    onUncomplete={handleHabitUncomplete}
                    onToggleStatus={handleHabitToggle}
                    onDelete={handleHabitDelete}
                    onUpdate={handleHabitUpdate}
                />
              ))}
            </div>
          )}
        </section>
      </main>

      {/* Floating Chat Button */}
      <button
        onClick={() => setShowChat(!showChat)}
        className="fixed bottom-8 right-8 p-4 bg-indigo-600 text-white rounded-full shadow-xl shadow-indigo-600/20 hover:bg-indigo-500 transition-all hover:scale-105 active:scale-95 z-50 group"
      >
        <MessageSquare className="w-6 h-6 group-hover:animate-pulse" />
        <span className="absolute -top-2 -right-2 w-4 h-4 bg-red-500 rounded-full border-2 border-white"></span>
      </button>

      <PanicMode />

      {showWizard && <HabitWizard onClose={() => setShowWizard(false)} onSuccess={handleRefresh} />}
      {showChat && <ChatInterface onClose={() => setShowChat(false)} onHabitsAdded={handleRefresh} onIdentityUpdated={fetchUser} />}
      {showIdentityModal && <IdentityModal onClose={() => setShowIdentityModal(false)} />}
    </div>
  );
};

export default Dashboard;
