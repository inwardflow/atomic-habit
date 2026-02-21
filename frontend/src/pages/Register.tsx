import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { authService } from '../services/authService';

const Register = () => {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [identityStatement, setIdentityStatement] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const validatePassword = (pwd: string) => {
    const regex = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$/;
    if (!regex.test(pwd)) {
      setPasswordError(t('auth.register.passwordRequirements'));
      return false;
    }
    setPasswordError('');
    return true;
  };

  const handlePasswordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setPassword(val);
    validatePassword(val);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    
    if (!validatePassword(password)) {
      return;
    }

    try {
      await authService.register({ email, password, identityStatement });
      alert(t('auth.register.success'));
      navigate('/login');
    } catch (err: any) {
      console.error(err);
      if (err.response?.data?.error) {
        setError(err.response.data.error);
      } else {
        setError(t('auth.register.failed'));
      }
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-100">
      <div className="p-8 bg-white rounded shadow-md w-96">
        <h2 className="mb-6 text-2xl font-bold text-center">{t('auth.register.title')}</h2>
        {error && <div className="mb-4 p-2 bg-red-100 text-red-700 rounded text-sm">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="mb-4">
            <label className="block mb-2 text-sm font-bold text-gray-700">{t('auth.register.emailLabel')}</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-3 py-2 border rounded shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
            />
          </div>
          <div className="mb-4">
            <label className="block mb-2 text-sm font-bold text-gray-700">{t('auth.register.passwordLabel')}</label>
            <input
              type="password"
              value={password}
              onChange={handlePasswordChange}
              className={`w-full px-3 py-2 border rounded shadow-sm focus:outline-none focus:ring-2 ${passwordError ? 'border-red-500 focus:ring-red-500' : 'focus:ring-blue-500'}`}
              required
            />
            {passwordError && <p className="mt-1 text-xs text-red-500">{passwordError}</p>}
            <p className="mt-1 text-xs text-gray-500">
              {t('auth.register.passwordRequirements')}
            </p>
          </div>
          <div className="mb-6">
            <label className="block mb-2 text-sm font-bold text-gray-700">{t('auth.register.identityLabel')}</label>
            <input
              type="text"
              value={identityStatement}
              onChange={(e) => setIdentityStatement(e.target.value)}
              placeholder={t('auth.register.identityPlaceholder')}
              className="w-full px-3 py-2 border rounded shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
            />
          </div>
          <button
            type="submit"
            disabled={!!passwordError || !password}
            className={`w-full px-4 py-2 font-bold text-white rounded focus:outline-none focus:ring-2 focus:ring-green-500 ${passwordError || !password ? 'bg-gray-400 cursor-not-allowed' : 'bg-green-500 hover:bg-green-700'}`}
          >
            {t('auth.register.submit')}
          </button>
        </form>
        <p className="mt-4 text-sm text-center">
          {t('auth.haveAccount')} <Link to="/login" className="text-blue-500 hover:underline">{t('auth.loginLink')}</Link>
        </p>
      </div>
    </div>
  );
};

export default Register;
