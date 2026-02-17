
# Atomic Habit: An AI-Powered Habit Tracker for a Kinder, More Consistent You

[![CI](https://github.com/inwardflow/atomic-habit/actions/workflows/ci.yml/badge.svg)](https://github.com/inwardflow/atomic-habit/actions/workflows/ci.yml) [![LICENSE](https://img.shields.io/github/license/inwardflow/atomic-habit)](https://github.com/inwardflow/atomic-habit/blob/main/LICENSE)

**Atomic Habit** is a full-stack, open-source habit tracking application built on the principles of James Clear's book of the same name. It's designed to be a powerful, yet gentle tool for building a better life, one tiny habit at a time.

---

## The Philosophy: Why Another Habit Tracker?

Most habit trackers are about streaks, pressure, and perfection. They can feel great when you're on a roll, but demoralizing when life gets in the way. A missed day can feel like a total failure, causing many to abandon their goals altogether.

**This project is different.**

We focus on the core principles of *Atomic Habits*: making habits **obvious, attractive, easy, and satisfying**. Our goal is not to build unbreakable streaks, but to lower the friction to getting back on track. It's a tool for imperfect people living real lives.

Key philosophical differences:

*   **Focus on Identity**: The app is built around the idea of casting "votes" for your desired identity, rather than just checking boxes.
*   **2-Minute Rule as a First-Class Citizen**: Every habit can have a "2-minute version," making it easy to show up even on your worst days.
*   **Anxiety-Friendly Design**: Features like "Panic Mode" provide immediate, guided relief when you're overwhelmed, shifting focus from productivity to well-being.
*   **AI as a Coach, Not a Taskmaster**: The integrated AI Coach is designed to be a supportive partner, helping you reflect, strategize, and find the smallest possible step forward, especially when you're stuck.

## ✨ Features

This application is more than just a to-do list. It's a comprehensive system for mindful habit formation.

| Feature                 | Description                                                                                                                               |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| **🤖 AI Coach**         | An integrated AI assistant (powered by AgentScope) that helps you define goals, break down habits, and get back on track when you feel stuck. |
| **📊 Habit Dashboard**    | A clear, focused view of your daily habits. See what's scheduled, what's completed, and cast your "votes" for your new identity.            |
| **📈 Analytics Page**     | Visualize your progress over time with heatmaps and charts. Understand your consistency and celebrate your long-term progress.             |
| **🏆 Gamification System**  | Earn badges and level up your "Identity Score" as you build habits. Turns the process into a satisfying and motivating journey.         |
| **🔔 Notification System**  | Gentle, configurable reminders to help you stay on track without being intrusive.                                                       |
| **🧘 Panic Mode**          | An anxiety-friendly feature that guides you through breathing exercises and grounding techniques when you feel overwhelmed.                 |
| **👁️ Agent Visualization**  | See exactly what the AI Coach is doing in real-time (Thinking, Calling Tools, Reading Memory), providing transparency and building trust. |

## 🛠️ Tech Stack

This project is built with a modern, robust, and scalable technology stack.

**Backend:**
*   **Framework**: Spring Boot 3.2.5 (Java 17)
*   **AI Integration**: [AgentScope](https://github.com/modelscope/agentscope) for creating and managing AI agents.
*   **API**: RESTful API with SSE (Server-Sent Events) for real-time AI chat streaming.
*   **Authentication**: JWT-based security with Spring Security.
*   **Database**: JPA/Hibernate with MySQL (production) and H2 (local development).
*   **Build**: Maven

**Frontend:**
*   **Framework**: React 18 with Vite
*   **Language**: TypeScript
*   **Styling**: TailwindCSS for a utility-first CSS workflow.
*   **State Management**: Zustand for simple, scalable state management.
*   **Data Visualization**: Recharts for analytics charts and heatmaps.
*   **UI Components**: Lucide Icons, Framer Motion for animations.

**AI Service:**
*   Designed to be compatible with any OpenAI-compatible API endpoint.
*   Currently configured and tested with **SiliconFlow** (`Qwen/Qwen2.5-72B-Instruct`).

**Deployment:**
*   **Containerization**: Docker & Docker Compose for easy local and production setup.
*   **CI/CD**: GitHub Actions for automated testing and builds.

## 🚀 Getting Started

Follow these instructions to get the project running on your local machine for development and testing purposes.

### Prerequisites

Make sure you have the following software installed:

*   **Java 17+** (We recommend [SDKMAN!](https://sdkman.io/) for managing Java versions)
*   **Maven 3.9+** (For building the backend)
*   **Node.js 20+** (We recommend [nvm](https://github.com/nvm-sh/nvm) for managing Node.js versions)
*   **Docker & Docker Compose** (For the easiest, most consistent setup)

### 1. Clone the Repository

```bash
git clone https://github.com/inwardflow/atomic-habit.git
cd atomic-habit
```

### 2. Configure Environment Variables

The project uses environment variables for all sensitive configurations. Start by copying the example file:

```bash
cp .env.example .env
```

Now, open the `.env` file and fill in the required values. **At a minimum, you must provide `AGENTSCOPE_MODEL_API_KEY` for the AI Coach to function.**

| Variable                      | Description                                                                 |
| ----------------------------- | --------------------------------------------------------------------------- |
| `AGENTSCOPE_MODEL_API_KEY`    | **Required.** Your API key from an OpenAI-compatible service (e.g., SiliconFlow). |
| `AGENTSCOPE_MODEL_BASE_URL`   | The base URL of the AI service. Defaults to SiliconFlow.                    |
| `AGENTSCOPE_MODEL_MODEL_NAME` | The specific model to use. Defaults to `Qwen/Qwen2.5-72B-Instruct`.         |
| `SPRING_JWT_SECRET`           | A long, random string for signing authentication tokens.                    |
| `SPRING_DATASOURCE_URL`       | The JDBC URL for your database. Defaults to a local MySQL instance.         |
| `SPRING_DATASOURCE_USERNAME`  | Database username.                                                          |
| `SPRING_DATASOURCE_PASSWORD`  | Database password.                                                          |

### 3. Run with Docker Compose (Recommended)

This is the simplest way to get the full stack running.

```bash
docker compose up --build
```

The application will be available at `http://localhost:5173`.

### 4. Manual Local Development (Without Docker)

If you prefer to run the services manually:

**Run the Backend:**
```bash
# From the project root
cd backend
mvn spring-boot:run
```
The backend API will be running on `http://localhost:8080`.

**Run the Frontend:**
```bash
# From the project root
cd frontend
npm install
npm run dev
```
The frontend will be available at `http://localhost:5173`.

## 🤝 Contributing

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

Please see `CONTRIBUTING.md` for details on our code of conduct and the process for submitting pull requests to us.

## 📜 License

This project is licensed under the MIT License - see the `LICENSE` file for details.

## 🙏 Acknowledgments

*   **James Clear** for his life-changing book, *Atomic Habits*.
*   The **AgentScope** team for their powerful and flexible open-source multi-agent framework.
*   The countless developers in the **React, Spring, and open-source communities** whose work made this project possible.
