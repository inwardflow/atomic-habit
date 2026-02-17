import { useState, useEffect, useCallback } from 'react';
import api from '../api/axios';
import type { Habit } from '../types';
import toast from 'react-hot-toast';

export const useHabits = () => {
  const [habits, setHabits] = useState<Habit[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchHabits = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.get<Habit[]>('/habits');
      setHabits(response.data);
    } catch (err) {
      console.error(err);
      setError('Failed to fetch habits');
      toast.error('Failed to load habits');
    } finally {
      setLoading(false);
    }
  }, []);

  const completeHabit = async (id: number) => {
    try {
      await api.post(`/habits/${id}/complete`);
      toast.success('Habit Completed! +1 Vote for your identity.');
      // Refresh habits to update status
      fetchHabits();
    } catch (err) {
      console.error(err);
      toast.error('Failed to complete habit');
    }
  };

  const uncompleteHabit = async (id: number) => {
    try {
      await api.delete(`/habits/${id}/complete`);
      toast('Habit Unchecked', { icon: '↩️' });
      // Refresh habits to update status
      fetchHabits();
    } catch (err) {
      console.error(err);
      toast.error('Failed to uncomplete habit');
    }
  };

  useEffect(() => {
    fetchHabits();
  }, [fetchHabits]);

  const addHabits = async (newHabits: Partial<Habit>[]) => {
    try {
      await api.post('/habits/batch', newHabits);
      toast.success('Plan added successfully!');
      fetchHabits();
    } catch (err) {
      console.error(err);
      toast.error('Failed to add plan');
    }
  };

  const updateHabit = async (id: number, updates: Partial<Habit>) => {
      try {
          await api.put(`/habits/${id}`, updates);
          toast.success('Habit updated');
          fetchHabits();
      } catch (err) {
          toast.error('Failed to update habit');
      }
  };

  const toggleHabitStatus = async (id: number) => {
      try {
          await api.patch(`/habits/${id}/status`, {});
          toast.success('Habit status updated');
          fetchHabits();
      } catch (err) {
          toast.error('Failed to update status');
      }
  };

  const deleteHabit = async (id: number) => {
      try {
          await api.delete(`/habits/${id}`);
          toast.success('Habit deleted');
          fetchHabits();
      } catch (err) {
          toast.error('Failed to delete habit');
      }
  };

  return { habits, loading, error, fetchHabits, completeHabit, uncompleteHabit, addHabits, updateHabit, toggleHabitStatus, deleteHabit };
};
