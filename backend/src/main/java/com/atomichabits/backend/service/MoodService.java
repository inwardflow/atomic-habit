package com.atomichabits.backend.service;

import com.atomichabits.backend.model.MoodLog;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.MoodRepository;
import com.atomichabits.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class MoodService {
    private final MoodRepository moodRepository;
    private final UserRepository userRepository;

    public MoodService(MoodRepository moodRepository, UserRepository userRepository) {
        this.moodRepository = moodRepository;
        this.userRepository = userRepository;
    }

    public MoodLog logMood(String email, String moodType, String note) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        MoodLog log = MoodLog.builder()
                .userId(user.getId())
                .moodType(moodType)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build();
        
        return moodRepository.save(log);
    }

    public Page<MoodLog> getMoodHistory(String email, Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return moodRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
    }

    public List<MoodLog> getRecentMoods(String email, int hours) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return getMoodsSince(user.getId(), LocalDateTime.now().minusHours(hours));
    }

    public List<MoodLog> getMoodsSince(String email, LocalDateTime since) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return getMoodsSince(user.getId(), since);
    }

    public List<MoodLog> getRecentMoods(Long userId) {
        return getMoodsSince(userId, LocalDateTime.now().minusHours(24));
    }

    public List<MoodLog> getMoodsSince(Long userId, LocalDateTime since) {
        return moodRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, since);
    }
    
    public List<MoodLog> getGratitudeLogs(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return moodRepository.findByUserIdAndMoodTypeOrderByCreatedAtDesc(user.getId(), "GRATITUDE");
    }

    public MoodLog getTodayGratitude(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().atTime(LocalTime.MAX);
        return moodRepository.findTopByUserIdAndMoodTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
                user.getId(), "GRATITUDE", start, end).orElse(null);
    }

    public MoodLog updateMood(Long id, String note, String email) {
        MoodLog log = moodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mood log not found"));
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        if (!log.getUserId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        
        log.setNote(note);
        return moodRepository.save(log);
    }
    
    public void deleteMood(Long id, String email) {
        MoodLog log = moodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mood log not found"));
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        if (!log.getUserId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        
        moodRepository.delete(log);
    }
}
