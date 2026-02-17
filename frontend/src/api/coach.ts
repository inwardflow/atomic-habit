import api from './axios';

export interface ChatMessage {
    role: string;
    content: string;
    timestamp?: string;
}

export interface GreetingResponse {
    response: string;
}

export interface WeeklyReviewRecord {
    id: number;
    totalCompleted: number;
    currentStreak: number;
    bestStreak?: number;
    highlights: string[];
    suggestion: string;
    createdAt: string;
    formattedDate: string;
}

export const getGreeting = async (): Promise<GreetingResponse> => {
  const response = await api.get('/coach/greeting');
  return response.data;
};

export const getChatHistory = async (): Promise<ChatMessage[]> => {
    const response = await api.get('/coach/history');
    return response.data;
};

export const generateWeeklyReview = async (): Promise<GreetingResponse> => {
    const response = await api.post('/coach/weekly-review');
    return response.data;
};

export const getWeeklyReviews = async (limit: number = 10): Promise<WeeklyReviewRecord[]> => {
    const response = await api.get('/coach/weekly-reviews', { params: { limit } });
    return response.data;
};

export interface CoachMemory {
    id: number;
    type: 'DAILY_SUMMARY' | 'USER_INSIGHT' | 'LONG_TERM_FACT';
    content: string;
    referenceDate: string | null;
    createdAt?: string;
    importanceScore?: number;
    expiresAt?: string | null;
    formattedDate: string;
}

export const getCoachMemories = async (): Promise<CoachMemory[]> => {
    const response = await api.get('/coach/memories');
    return response.data;
};

export interface CoachMemoryHitsResponse {
    hits: string[];
    updatedAt?: string | null;
}

export const getCoachMemoryHits = async (): Promise<CoachMemoryHitsResponse> => {
    const response = await api.get('/coach/memory-hits');
    return response.data;
};
