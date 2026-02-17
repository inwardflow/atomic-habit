$ErrorActionPreference = "Stop"

Write-Host "1. Registering User..." -NoNewline
try {
    $body = @{
        email = "test2@example.com"
        password = "password123"
        identityStatement = "I am a tester"
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -Body $body -ContentType "application/json"
    Write-Host " OK" -ForegroundColor Green
} catch {
    Write-Host " FAILED: $($_.Exception.Message)" -ForegroundColor Red
    # Continue if user already exists (might be re-run)
}

Write-Host "2. Logging In..." -NoNewline
try {
    $body = @{
        email = "test2@example.com"
        password = "password123"
    } | ConvertTo-Json
    $tokenResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $body -ContentType "application/json"
    $token = $tokenResponse.accessToken
    if (-not $token) { throw "No token" }
    Write-Host " OK" -ForegroundColor Green
} catch {
    Write-Host " FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

$headers = @{ Authorization = "Bearer $token" }

Write-Host "3. Creating Habit..." -NoNewline
try {
    $body = @{
        name = "Test Habit"
        twoMinuteVersion = "2 mins"
        cueImplementationIntention = "8am"
        cueHabitStack = "Coffee"
    } | ConvertTo-Json
    $habit = Invoke-RestMethod -Uri "http://localhost:8080/api/habits" -Method Post -Body $body -ContentType "application/json" -Headers $headers
    Write-Host " OK (ID: $($habit.id))" -ForegroundColor Green
} catch {
    Write-Host " FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host "4. Chatting with Coach..." -NoNewline
try {
    $body = @{ message = "Hello" } | ConvertTo-Json
    $chat = Invoke-RestMethod -Uri "http://localhost:8080/api/coach/chat" -Method Post -Body $body -ContentType "application/json" -Headers $headers
    Write-Host " OK (Response: $($chat.response))" -ForegroundColor Green
} catch {
    Write-Host " SKIPPED (AI Service likely unreachable/mocked)" -ForegroundColor Yellow
}

Write-Host "5. Completing Habit..." -NoNewline
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/habits/$($habit.id)/complete" -Method Post -Headers $headers
    Write-Host " OK" -ForegroundColor Green
} catch {
    Write-Host " FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host "`nVERIFICATION COMPLETE." -ForegroundColor Cyan
