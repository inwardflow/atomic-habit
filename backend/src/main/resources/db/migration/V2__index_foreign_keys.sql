-- PostgreSQL does not index foreign key columns automatically. Almost every query filters by
-- user (and often orders by time), so index those access paths. IF NOT EXISTS keeps this safe on
-- databases that were baselined at version 1.

create index if not exists idx_badges_user on badges (user_id);
create index if not exists idx_chat_messages_user_time on chat_messages (user_id, timestamp);
create index if not exists idx_coach_memories_user on coach_memories (user_id);
create index if not exists idx_goals_user on goals (user_id);
create index if not exists idx_habit_completions_habit_time on habit_completions (habit_id, completed_at);
create index if not exists idx_habits_user on habits (user_id);
create index if not exists idx_habits_goal on habits (goal_id);
create index if not exists idx_login_history_user_time on login_history (user_id, login_time);
create index if not exists idx_mood_logs_user_time on mood_logs (user_id, created_at);
create index if not exists idx_refresh_tokens_user on refresh_tokens (user_id);
create index if not exists idx_user_roles_user on user_roles (user_id);
create index if not exists idx_weekly_reviews_user_time on weekly_reviews (user_id, created_at);
