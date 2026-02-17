import { useState, useEffect, useCallback } from 'react';
import api from '../api/axios';
import type { Goal, GoalRequest } from '../types';
import toast from 'react-hot-toast';

export const useGoals = () => {
  const [goals, setGoals] = useState<Goal[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchGoals = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.get<Goal[]>('/goals');
      setGoals(response.data);
    } catch (err) {
      console.error(err);
      setError('Failed to fetch goals');
      toast.error('Failed to load goals');
    } finally {
      setLoading(false);
    }
  }, []);

  const createGoal = async (request: GoalRequest) => {
    try {
      const response = await api.post<Goal>('/goals', request);
      setGoals((prev) => [...prev, response.data]);
      toast.success('Goal created successfully!');
      return response.data;
    } catch (err) {
      console.error(err);
      toast.error('Failed to create goal');
      throw err;
    }
  };
  
  // Since habits are created with the goal in our backend implementation,
  // we might want to refresh goals to see them, or just rely on the response.
  
  const addHabitsToGoal = async (goalId: number, habits: any[]) => {
      try {
          const response = await api.post<Goal>(`/goals/${goalId}/habits`, habits);
          setGoals(prev => prev.map(g => g.id === goalId ? response.data : g));
          toast.success('Habits added to goal!');
      } catch (err) {
          console.error(err);
          toast.error('Failed to add habits to goal');
      }
  }

  useEffect(() => {
    fetchGoals();
  }, [fetchGoals]);

  return { goals, loading, error, fetchGoals, createGoal, addHabitsToGoal };
};
