export interface Habit {
  id: number;
  name: string;
  twoMinuteVersion: string;
  cueImplementationIntention: string;
  cueHabitStack: string;
  isActive: boolean;
  completedToday: boolean;
  scheduledToday: boolean;
  currentStreak?: number;
  goalId?: number;
  frequency?: string[];
}

export interface HabitRequest {
  name: string;
  twoMinuteVersion: string;
  cueImplementationIntention: string;
  cueHabitStack: string;
  goalId?: number;
  frequency?: string[];
}

export interface Goal {
  id: number;
  name: string;
  description: string;
  startDate?: string;
  endDate?: string;
  status: string;
  habits: Habit[];
  createdAt: string;
}

export interface GoalRequest {
  name: string;
  description: string;
  startDate?: string;
  endDate?: string;
  status?: string;
  habits?: HabitRequest[];
}

export interface User {
  id: number;
  email: string;
  identityStatement: string;
}

export interface UserStats {
  identityScore: number;
  currentStreak: number;
  longestStreak: number;
  totalHabitsCompleted: number;
  badges: Badge[];
}

export interface DailyCompletion {
  date: string;
  count: number;
}

export interface MoodInsight {
  mood: string;
  logCount: number;
  avgCompletions: number;
  topHabits: string[];
}

export interface AdvancedUserStats {
  last30Days: DailyCompletion[];
  completionsByHabit: Record<string, number>;
  overallCompletionRate: number;
  moodInsights?: MoodInsight[];
}

export interface Badge {
  id: number;
  name: string;
  description: string;
  icon: string;
  earnedAt: string;
}

export interface MoodLog {
  id: number;
  userId: number;
  moodType: string;
  note?: string;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  last: boolean;
  size: number;
  number: number;
  sort: any;
  numberOfElements: number;
  first: boolean;
  empty: boolean;
}
