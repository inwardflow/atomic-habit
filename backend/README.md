# Backend

Spring Boot backend for Atomic Habits.

## Run locally
```bash
mvn spring-boot:run
```

## Test
```bash
mvn test
```

## Build
```bash
mvn clean package
```

## Key environment variables
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JWT_SECRET`
- `AGENTSCOPE_MODEL_API_KEY`
- `AGENTSCOPE_MODEL_NAME`

## API docs
After startup, visit:
- `http://localhost:8080/swagger-ui/index.html`
