# Contributing

Thanks for contributing to Atomic Habits.

## Development setup
1. Install Java 17+, Maven, Node.js 20+, Docker (optional).
2. Copy `.env.example` to `.env` and fill required values.
3. Start backend:
   - `cd backend`
   - `mvn spring-boot:run`
4. Start frontend:
   - `cd frontend`
   - `npm ci`
   - `npm run dev`

## Branch and PR workflow
1. Create a feature branch from `main`.
2. Keep commits focused and descriptive.
3. Open a pull request with:
   - Problem statement
   - Scope of changes
   - Test evidence
   - Screenshots for UI updates

## Quality checks
Before opening a PR, run:
- Backend: `mvn -f backend/pom.xml clean test`
- Frontend: `npm --prefix frontend ci && npm --prefix frontend run lint && npm --prefix frontend run build`

## Coding standards
- Follow `.editorconfig` and existing project conventions.
- Avoid unrelated refactors in the same PR.
- Never commit secrets.

## Reporting security issues
Please do not open public issues for vulnerabilities. Follow `SECURITY.md`.
