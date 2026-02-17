import { useState, useEffect } from 'react';
import api from '../api/axios';
import type { UserStats } from '../types';

export const useHabitStats = () => {
  const [stats, setStats] = useState<UserStats | null>(null);
  const [completions, setCompletions] = useState<string[]>([]); // Dates as strings
  const [loading, setLoading] = useState(true);

  const fetchStats = async () => {
    try {
      const [statsRes, completionsRes] = await Promise.all([
        api.get('/users/stats'),
        api.get('/habits/completions')
      ]);
      setStats(statsRes.data);
      setCompletions(completionsRes.data);
    } catch (error) {
      console.error('Failed to fetch stats', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
  }, []);

  return { stats, completions, loading, refetch: fetchStats };
};
