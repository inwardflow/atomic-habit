import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, User, Lock, Trash2, Moon, Sun, Bell, BellOff, Save, AlertTriangle, Check, Sparkles } from 'lucide-react';
import { useAuthStore } from '../store/authStore';
import { useThemeStore } from '../store/themeStore';
import { useNotifications } from '../hooks/useNotifications';
import api from '../api/axios';
import toast from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import LanguageSwitcher from '../components/LanguageSwitcher';

const Settings = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { user, setUser, logout } = useAuthStore();
  const { theme, toggleTheme } = useThemeStore();
  const { notificationsEnabled, toggleNotifications, permission } = useNotifications({ connect: false });

  // Profile
  const [identityStatement, setIdentityStatement] = useState('');
  const [email, setEmail] = useState('');
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileSaved, setProfileSaved] = useState(false);

  // Password
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordLoading, setPasswordLoading] = useState(false);

  // Delete Account
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteLoading, setDeleteLoading] = useState(false);

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const res = await api.get('/users/me');
        setIdentityStatement(res.data.identityStatement || '');
        setEmail(res.data.email || '');
        if (!user || user.email !== res.data.email) {
          setUser(res.data);
        }
      } catch {
        toast.error(t('settings.toast.profile_error'));
      }
    };
    fetchProfile();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleProfileSave = async () => {
    setProfileLoading(true);
    try {
      const res = await api.put('/users/me', { identityStatement, email });
      setUser(res.data);
      setProfileSaved(true);
      toast.success(t('settings.toast.update_success'));
      setTimeout(() => setProfileSaved(false), 2000);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      toast.error(error.response?.data?.message || t('settings.toast.update_error'));
    } finally {
      setProfileLoading(false);
    }
  };

  const handlePasswordChange = async () => {
    if (newPassword !== confirmPassword) {
      toast.error(t('settings.toast.password_mismatch'));
      return;
    }
    if (newPassword.length < 6) {
      toast.error(t('settings.toast.password_min_length'));
      return;
    }
    setPasswordLoading(true);
    try {
      await api.post('/users/me/change-password', {
        currentPassword,
        newPassword,
      });
      toast.success(t('settings.toast.password_success'));
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      toast.error(error.response?.data?.message || t('settings.toast.password_error'));
    } finally {
      setPasswordLoading(false);
    }
  };

  const handleDeleteAccount = async () => {
    if (!deletePassword) {
      toast.error(t('settings.toast.delete_confirm_password'));
      return;
    }
    setDeleteLoading(true);
    try {
      await api.post('/users/me/delete-account', { password: deletePassword });
      toast.success(t('settings.toast.delete_success'));
      logout();
      navigate('/login');
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      toast.error(error.response?.data?.message || t('settings.toast.delete_error'));
    } finally {
      setDeleteLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-900 transition-colors duration-300">
      {/* Header */}
      <div className="sticky top-0 z-30 w-full bg-white/80 dark:bg-slate-900/80 backdrop-blur-md border-b border-slate-200 dark:border-slate-800">
        <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center h-16 gap-3">
            <button
              onClick={() => navigate(-1)}
              className="p-2 -ml-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors text-slate-500 dark:text-slate-400"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <h1 className="text-xl font-bold text-slate-900 dark:text-white">{t('settings.title')}</h1>
          </div>
        </div>
      </div>

      <main className="max-w-3xl mx-auto py-8 px-4 sm:px-6 lg:px-8 space-y-8">
        {/* Profile Section */}
        <section className="bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-sm border border-slate-100 dark:border-slate-700">
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 bg-indigo-100 dark:bg-indigo-900/30 rounded-xl flex items-center justify-center">
              <User className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-slate-900 dark:text-white">{t('settings.profile.title')}</h2>
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('settings.profile.subtitle')}</p>
            </div>
          </div>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                {t('settings.profile.email')}
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full rounded-xl border border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-900 px-4 py-2.5 text-slate-900 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition-all"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                <span className="flex items-center gap-1.5">
                  <Sparkles className="w-4 h-4 text-indigo-500" />
                  {t('settings.profile.identityStatement')}
                </span>
              </label>
              <input
                type="text"
                value={identityStatement}
                onChange={(e) => setIdentityStatement(e.target.value)}
                placeholder={t('settings.profile.identityPlaceholder')}
                className="w-full rounded-xl border border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-900 px-4 py-2.5 text-slate-900 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition-all"
              />
              <p className="mt-1.5 text-xs text-slate-400 dark:text-slate-500">
                {t('settings.profile.identityHint')}
              </p>
            </div>

            <button
              onClick={handleProfileSave}
              disabled={profileLoading}
              className="flex items-center gap-2 px-5 py-2.5 bg-indigo-600 text-white rounded-xl font-medium hover:bg-indigo-700 transition-all disabled:opacity-50 shadow-sm"
            >
              {profileSaved ? (
                <>
                  <Check className="w-4 h-4" />
                  {t('settings.profile.saved')}
                </>
              ) : (
                <>
                  <Save className="w-4 h-4" />
                  {profileLoading ? t('settings.profile.saving') : t('settings.profile.save')}
                </>
              )}
            </button>
          </div>
        </section>

        {/* Preferences Section */}
        <section className="bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-sm border border-slate-100 dark:border-slate-700">
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 bg-amber-100 dark:bg-amber-900/30 rounded-xl flex items-center justify-center">
              {theme === 'dark' ? (
                <Moon className="w-5 h-5 text-amber-600 dark:text-amber-400" />
              ) : (
                <Sun className="w-5 h-5 text-amber-600 dark:text-amber-400" />
              )}
            </div>
            <div>
              <h2 className="text-lg font-semibold text-slate-900 dark:text-white">{t('settings.preferences.title')}</h2>
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('settings.preferences.subtitle')}</p>
            </div>
          </div>

          <div className="space-y-4">
            {/* Language Switcher */}
            <div className="flex items-center justify-between p-4 bg-slate-50 dark:bg-slate-900/50 rounded-xl">
              <div>
                <p className="font-medium text-slate-900 dark:text-white">{t('settings.preferences.language')}</p>
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  {t('settings.preferences.subtitle')}
                </p>
              </div>
              <LanguageSwitcher />
            </div>

            {/* Theme Toggle */}
            <div className="flex items-center justify-between p-4 bg-slate-50 dark:bg-slate-900/50 rounded-xl">
              <div>
                <p className="font-medium text-slate-900 dark:text-white">{t('settings.preferences.theme')}</p>
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  {theme === 'dark' ? t('settings.preferences.darkMode') : t('settings.preferences.lightMode')}
                </p>
              </div>
              <button
                onClick={toggleTheme}
                className="relative w-14 h-7 rounded-full transition-colors duration-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-slate-800"
                style={{ backgroundColor: theme === 'dark' ? '#6366f1' : '#cbd5e1' }}
              >
                <span
                  className="absolute top-0.5 left-0.5 w-6 h-6 bg-white rounded-full shadow-sm transition-transform duration-300 flex items-center justify-center"
                  style={{ transform: theme === 'dark' ? 'translateX(28px)' : 'translateX(0)' }}
                >
                  {theme === 'dark' ? (
                    <Moon className="w-3.5 h-3.5 text-indigo-600" />
                  ) : (
                    <Sun className="w-3.5 h-3.5 text-amber-500" />
                  )}
                </span>
              </button>
            </div>

            {/* Notifications Toggle */}
            <div className="flex items-center justify-between p-4 bg-slate-50 dark:bg-slate-900/50 rounded-xl">
              <div>
                <p className="font-medium text-slate-900 dark:text-white">{t('settings.preferences.notifications')}</p>
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  {permission === 'denied'
                    ? t('settings.preferences.blocked')
                    : notificationsEnabled
                    ? t('settings.preferences.enabled')
                    : t('settings.preferences.disabled')}
                </p>
              </div>
              <button
                onClick={toggleNotifications}
                disabled={permission === 'denied'}
                className="relative w-14 h-7 rounded-full transition-colors duration-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-slate-800 disabled:opacity-50"
                style={{ backgroundColor: notificationsEnabled ? '#6366f1' : '#cbd5e1' }}
              >
                <span
                  className="absolute top-0.5 left-0.5 w-6 h-6 bg-white rounded-full shadow-sm transition-transform duration-300 flex items-center justify-center"
                  style={{ transform: notificationsEnabled ? 'translateX(28px)' : 'translateX(0)' }}
                >
                  {notificationsEnabled ? (
                    <Bell className="w-3.5 h-3.5 text-indigo-600" />
                  ) : (
                    <BellOff className="w-3.5 h-3.5 text-slate-400" />
                  )}
                </span>
              </button>
            </div>
          </div>
        </section>

        {/* Security Section */}
        <section className="bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-sm border border-slate-100 dark:border-slate-700">
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 bg-green-100 dark:bg-green-900/30 rounded-xl flex items-center justify-center">
              <Lock className="w-5 h-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-slate-900 dark:text-white">{t('settings.security.title')}</h2>
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('settings.security.subtitle')}</p>
            </div>
          </div>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                {t('settings.security.currentPassword')}
              </label>
              <input
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                className="w-full rounded-xl border border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-900 px-4 py-2.5 text-slate-900 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition-all"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                {t('settings.security.newPassword')}
              </label>
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                className="w-full rounded-xl border border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-900 px-4 py-2.5 text-slate-900 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition-all"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                {t('settings.security.confirmPassword')}
              </label>
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="w-full rounded-xl border border-slate-200 dark:border-slate-600 bg-slate-50 dark:bg-slate-900 px-4 py-2.5 text-slate-900 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition-all"
              />
            </div>
            <button
              onClick={handlePasswordChange}
              disabled={passwordLoading || !currentPassword || !newPassword || !confirmPassword}
              className="flex items-center gap-2 px-5 py-2.5 bg-green-600 text-white rounded-xl font-medium hover:bg-green-700 transition-all disabled:opacity-50 shadow-sm"
            >
              <Lock className="w-4 h-4" />
              {passwordLoading ? t('settings.security.changing') : t('settings.security.changePassword')}
            </button>
          </div>
        </section>

        {/* Danger Zone */}
        <section className="bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-sm border border-red-200 dark:border-red-900/50">
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 bg-red-100 dark:bg-red-900/30 rounded-xl flex items-center justify-center">
              <AlertTriangle className="w-5 h-5 text-red-600 dark:text-red-400" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-red-700 dark:text-red-400">{t('settings.dangerZone.title')}</h2>
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('settings.dangerZone.subtitle')}</p>
            </div>
          </div>

          {!showDeleteConfirm ? (
            <button
              onClick={() => setShowDeleteConfirm(true)}
              className="flex items-center gap-2 px-5 py-2.5 bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-800 rounded-xl font-medium hover:bg-red-100 dark:hover:bg-red-900/40 transition-all"
            >
              <Trash2 className="w-4 h-4" />
              {t('settings.dangerZone.deleteAccount')}
            </button>
          ) : (
            <div className="space-y-4 p-4 bg-red-50 dark:bg-red-900/10 rounded-xl border border-red-200 dark:border-red-800">
              <p className="text-sm text-red-700 dark:text-red-300 font-medium">
                {t('settings.dangerZone.deleteWarning')}
              </p>
              <div>
                <label className="block text-sm font-medium text-red-700 dark:text-red-300 mb-1.5">
                  {t('settings.dangerZone.confirmPassword')}
                </label>
                <input
                  type="password"
                  value={deletePassword}
                  onChange={(e) => setDeletePassword(e.target.value)}
                  className="w-full rounded-xl border border-red-200 dark:border-red-800 bg-white dark:bg-slate-900 px-4 py-2.5 text-slate-900 dark:text-white focus:ring-2 focus:ring-red-500 focus:border-transparent outline-none transition-all"
                />
              </div>
              <div className="flex gap-3">
                <button
                  onClick={() => {
                    setShowDeleteConfirm(false);
                    setDeletePassword('');
                  }}
                  className="px-4 py-2 text-sm font-medium text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition-colors"
                >
                  {t('settings.dangerZone.cancel')}
                </button>
                <button
                  onClick={handleDeleteAccount}
                  disabled={deleteLoading || !deletePassword}
                  className="flex items-center gap-2 px-5 py-2.5 bg-red-600 text-white rounded-xl font-medium hover:bg-red-700 transition-all disabled:opacity-50 shadow-sm"
                >
                  <Trash2 className="w-4 h-4" />
                  {deleteLoading ? t('settings.dangerZone.deleting') : t('settings.dangerZone.delete')}
                </button>
              </div>
            </div>
          )}
        </section>
      </main>
    </div>
  );
};

export default Settings;
