package com.atomichabits.backend.service;

import com.atomichabits.backend.model.Habit;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.HabitCompletionRepository;
import com.atomichabits.backend.repository.HabitRepository;
import com.atomichabits.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private static final long COACH_NUDGE_COOLDOWN_HOURS = 6;

    private final UserRepository userRepository;
    private final HabitRepository habitRepository;
    private final HabitCompletionRepository habitCompletionRepository;
    private final HabitService habitService;
    private final CoachService coachService;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, Queue<String>> pendingNotifications = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastCoachNudgeAt = new ConcurrentHashMap<>();

    public NotificationService(UserRepository userRepository,
                               HabitRepository habitRepository,
                               HabitCompletionRepository habitCompletionRepository,
                               HabitService habitService,
                               CoachService coachService) {
        this.userRepository = userRepository;
        this.habitRepository = habitRepository;
        this.habitCompletionRepository = habitCompletionRepository;
        this.habitService = habitService;
        this.coachService = coachService;
    }

    public SseEmitter subscribe(String email) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(email, emitter);

        emitter.onCompletion(() -> emitters.remove(email));
        emitter.onTimeout(() -> emitters.remove(email));
        emitter.onError((e) -> emitters.remove(email));

        flushPendingNotifications(email, emitter);
        CompletableFuture.runAsync(() -> sendProactiveCoachCheckIn(email));

        return emitter;
    }

    /**
     * Send a notification to a user.
     * Logs the message and sends via SSE if connected, otherwise queues it.
     */
    public boolean sendNotification(String email, String message) {
        logger.info("NOTIFICATION to [{}]: {}", email, message);

        if (sendToActiveEmitter(email, message)) {
            return true;
        }

        queueNotification(email, message);
        return false;
    }

    /**
     * Check for users with incomplete habits at 8 PM daily.
     */
    @Scheduled(cron = "0 0 20 * * ?")
    public void sendDailyReminders() {
        logger.info("Starting daily reminder check...");
        List<User> users = userRepository.findAll();

        for (User user : users) {
            checkAndNotifyUser(user, false);
        }
        logger.info("Daily reminder check completed.");
    }

    /**
     * Send proactive check-ins during daytime. Cooldown is applied to avoid spamming.
     */
    @Scheduled(cron = "0 0 10,14,18 * * ?")
    public void sendProactiveCoachCheckIns() {
        logger.info("Starting proactive coach check-ins...");
        List<User> users = userRepository.findAll();

        for (User user : users) {
            checkAndNotifyUser(user, true);
        }
        logger.info("Proactive coach check-ins completed.");
    }

    // Package-private for testing
    void checkAndNotifyUser(User user) {
        checkAndNotifyUser(user, false);
    }

    // Package-private for testing
    boolean checkAndNotifyUser(User user, boolean applyCooldown) {
        String email = user.getEmail();
        if (applyCooldown && shouldSkipCoachNudge(email)) {
            return false;
        }

        List<Habit> activeHabits = habitRepository.findByUserIdAndIsActiveTrue(user.getId());
        if (activeHabits.isEmpty()) {
            return false;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        long completedTodayCount = 0;
        int maxStreakAtRisk = 0;
        String riskHabitName = "";
        String firstUncompletedHabitName = "";

        for (Habit habit : activeHabits) {
            boolean completed = habitCompletionRepository.existsByHabitIdAndCompletedAtBetween(
                    habit.getId(), startOfDay, endOfDay);

            if (completed) {
                completedTodayCount++;
            } else {
                if (firstUncompletedHabitName.isEmpty()) {
                    firstUncompletedHabitName = habit.getName();
                }

                // Check streak for this uncompleted habit
                List<com.atomichabits.backend.model.HabitCompletion> completions =
                        habitCompletionRepository.findByHabitIdOrderByCompletedAtDesc(habit.getId());
                int streak = habitService.calculateCurrentStreak(completions);

                if (streak > maxStreakAtRisk) {
                    maxStreakAtRisk = streak;
                    riskHabitName = habit.getName();
                }
            }
        }

        if (completedTodayCount >= activeHabits.size()) {
            return false;
        }

        String message;

        // If no specific risk habit identified (all 0 streak), pick the first uncompleted one
        if (riskHabitName.isEmpty()) {
            riskHabitName = firstUncompletedHabitName;
        }

        try {
            // Try AI generation
            message = coachService.generateReminder(email, riskHabitName, maxStreakAtRisk);

            // Basic validation of AI response
            if (message == null || message.contains("unable to connect") || message.length() > 200) {
                throw new RuntimeException("AI response invalid");
            }

            // Clean up quotes if present (LLMs sometimes add them)
            message = message.replace("\"", "").trim();

        } catch (Exception e) {
            // Fallback to static template
            if (maxStreakAtRisk >= 3) {
                message = String.format("Streak alert: don't break your %d-day streak on '%s'. Do the 2-minute version now.", maxStreakAtRisk, riskHabitName);
            } else {
                long remaining = activeHabits.size() - completedTodayCount;
                message = String.format("You have %d habits left for today. Keep your streak alive!", remaining);
            }
        }

        boolean delivered = sendNotification(email, message);

        if (applyCooldown) {
            lastCoachNudgeAt.put(email, LocalDateTime.now());
        }

        return delivered;
    }

    private void sendProactiveCoachCheckIn(String email) {
        userRepository.findByEmail(email).ifPresent(user -> checkAndNotifyUser(user, true));
    }

    private boolean shouldSkipCoachNudge(String email) {
        Queue<String> queue = pendingNotifications.get(email);
        if (queue != null && !queue.isEmpty()) {
            return true;
        }

        LocalDateTime lastNudge = lastCoachNudgeAt.get(email);
        if (lastNudge == null) {
            return false;
        }

        return lastNudge.isAfter(LocalDateTime.now().minusHours(COACH_NUDGE_COOLDOWN_HOURS));
    }

    private boolean sendToActiveEmitter(String email, String message) {
        SseEmitter emitter = emitters.get(email);
        if (emitter == null) {
            return false;
        }

        try {
            emitter.send(SseEmitter.event().name("notification").data(message));
            return true;
        } catch (IOException e) {
            emitters.remove(email);
            return false;
        }
    }

    private void queueNotification(String email, String message) {
        pendingNotifications.computeIfAbsent(email, key -> new ConcurrentLinkedQueue<>()).offer(message);
    }

    private void flushPendingNotifications(String email, SseEmitter emitter) {
        Queue<String> queue = pendingNotifications.get(email);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        String message;
        while ((message = queue.poll()) != null) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(message));
            } catch (IOException e) {
                queue.offer(message);
                emitters.remove(email);
                logger.warn("Failed to flush pending notifications for [{}], will retry next subscribe.", email);
                return;
            }
        }

        pendingNotifications.remove(email, queue);
    }
}
