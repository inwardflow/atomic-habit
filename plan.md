# Atomic Habits App Development Plan

## Phase 1: Backend Core & AI Enhancement (Java + AgentScope)

### 1.1 Enhance AI Coach Capabilities
- **Goal**: Enable the AI Coach to generate structured habit plans and calculate identity scores.
- **Tasks**:
    - Modify `CoachTools.java` to add `generate_habit_plan` tool.
    - Update `CoachService.java` system prompt to output JSON for plans.
    - Implement `IdentityScore` logic in `UserService` (based on streak calculation).
    - Add `POST /api/coach/weekly-review` endpoint to trigger weekly review analysis.

### 1.2 Backend API Improvements
- **Goal**: Support batch operations and advanced metrics.
- **Tasks**:
    - Add `POST /api/habits/batch` for adding multiple habits at once (from AI suggestions).
    - Add `GET /api/users/stats` endpoint to return `identityScore`, `currentStreak`, and `longestStreak`.

## Phase 2: Frontend Core & Onboarding (React)

### 2.1 Onboarding & Identity
- **Goal**: Guide users to define their identity upon first login.
- **Tasks**:
    - Create `IdentityModal.tsx` component.
    - Integrate `IdentityModal` into `Dashboard.tsx` to show if `user.identityStatement` is missing.
    - Connect to `PUT /api/users/me` to save identity.

### 2.2 Visualizations & Dashboard
- **Goal**: Visualize progress and reinforce identity.
- **Tasks**:
    - Create `HabitHeatmap.tsx` component (using a calendar library or custom SVG).
    - Create `IdentityScore.tsx` component to display the calculated score.
    - Integrate these components into the `Dashboard`.

### 2.3 Enhanced AI Chat Interface
- **Goal**: Make the chat actionable.
- **Tasks**:
    - Update `ChatInterface.tsx` to parse JSON plans from AI.
    - Add "Add All to Habits" button in the chat UI for suggested plans.
    - format AI responses with markdown support.

## Phase 3: Integration & Polish

### 3.1 Weekly Review & Notifications
- **Goal**: Proactive engagement.
- **Tasks**:
    - Implement a "Weekly Review" button or auto-trigger in the chat.
    - Add toast notifications for key actions (habit completion, plan added).

### 3.2 UI/UX Polish
- **Goal**: Ensure a high-quality user experience.
- **Tasks**:
    - Review all screens for responsiveness (Mobile/Desktop).
    - Ensure consistent Tailwind styling.
    - Verify "4 Laws" flow in `HabitWizard`.

### 3.3 Documentation
- **Goal**: Project handover.
- **Tasks**:
    - Update `README.md` with setup instructions.
    - Document API endpoints.

## Phase 4: Robustness & Deployment (New)

### 4.1 Backend Testing
- **Goal**: Ensure core logic is correct.
- **Tasks**:
    - Add unit tests for `HabitService` (creation, completion logic).
    - Add unit tests for `UserService` (streak calculation).
    - Add integration tests for `HabitController`.

### 4.2 Deployment Ready
- **Goal**: Containerize the application.
- **Tasks**:
    - Create `Dockerfile` for Backend.
    - Create `Dockerfile` for Frontend.
    - Create `docker-compose.yml` for full stack deployment.

## Phase 5: Advanced Features & Optimization (New)

### 5.1 Advanced Analytics
- **Goal**: Provide deeper insights into habit performance.
- **Tasks**:
    - Backend: Add `GET /api/stats/advanced` to return completion rates by week/month.
    - Frontend: Add `AnalyticsDashboard.tsx` with charts (using Recharts).

### 5.2 Gamification
- **Goal**: Increase user engagement with rewards.
- **Tasks**:
    - Backend: Create `Badge` entity and `GamificationService`.
    - Backend: Award badges for streaks (7 days, 30 days) and total completions.
    - Frontend: Display earned badges on the Dashboard.

### 5.3 Notifications
- **Goal**: Remind users to complete habits.
- **Tasks**:
    - Backend: Implement a simple `NotificationService` (logging-based for now).
    - Frontend: Request browser notification permission (optional).
