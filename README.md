# Atomic Habits

Atomic Habits is a full-stack habit-tracking application focused on low-pressure, anxiety-friendly behavior change.

## Tech stack
- Frontend: React 19, TypeScript, Vite, Tailwind CSS
- Backend: Spring Boot 3 (Java 17), Spring Security, JPA
- Database: H2 (local default), PostgreSQL (production)
- AI integration: AgentScope-compatible OpenAI API provider

## Repository layout
```text
.
|-- backend/                  # Spring Boot API
|-- frontend/                 # React app
|-- .github/workflows/        # CI and dependency-review pipelines
|-- docker-compose.yml        # Local full-stack compose setup
|-- verify.ps1                # Basic API smoke script
```

## Quick start

### 1. Prerequisites
- Java 17+
- Maven 3.9+
- Node.js 20+
- Docker (optional)

### 2. Local development (without Docker)
Backend:
```bash
cd backend
mvn spring-boot:run
```

Frontend:
```bash
cd frontend
npm ci
npm run dev
```

Default URLs:
- Frontend: http://localhost:5173
- Backend: http://localhost:8080
- OpenAPI UI: http://localhost:8080/swagger-ui/index.html

### 3. Docker Compose
```bash
cp .env.example .env
# fill .env values (especially AGENTSCOPE_API_KEY)
docker compose up --build
```

## Configuration
All sensitive values are environment-driven.

Important variables:
- `AGENTSCOPE_MODEL_API_KEY`: LLM provider API key
- `AGENTSCOPE_MODEL_MODEL_NAME`: model name
- `SPRING_JWT_SECRET`: JWT signing secret
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`

See `.env.example` for the full list.

## Quality checks
Backend:
```bash
mvn -f backend/pom.xml clean test
```

Frontend:
```bash
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run build
```

## CI/CD
GitHub Actions includes:
- `CI`: changed-path aware backend/frontend validation
- `Dependency Review`: blocks risky dependency changes on pull requests
- `Dependabot`: weekly updates for Maven, npm, and GitHub Actions

## Open source governance
This repository includes:
- `LICENSE` (MIT)
- `CONTRIBUTING.md`
- `CODE_OF_CONDUCT.md`
- `SECURITY.md`
- Issue and PR templates under `.github/`

## Security notes
- Never commit real API keys or passwords.
- Use `.env` locally and secret managers in CI/production.
- If you discover a vulnerability, report it privately via `SECURITY.md`.
