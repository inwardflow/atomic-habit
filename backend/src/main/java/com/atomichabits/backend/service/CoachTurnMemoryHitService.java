package com.atomichabits.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class CoachTurnMemoryHitService {
    private static final int MAX_HITS = 6;
    private static final int SNAPSHOT_TTL_MINUTES = 10;

    private final ConcurrentMap<String, MemoryHitSnapshot> snapshots = new ConcurrentHashMap<>();

    public void updateHits(String email, List<String> hits) {
        if (!StringUtils.hasText(email)) {
            return;
        }

        List<String> normalizedHits = normalizeHits(hits);
        snapshots.put(email, new MemoryHitSnapshot(normalizedHits, LocalDateTime.now()));
    }

    public MemoryHitSnapshot getLatestHits(String email) {
        if (!StringUtils.hasText(email)) {
            return new MemoryHitSnapshot(Collections.emptyList(), null);
        }
        MemoryHitSnapshot snapshot = snapshots.get(email);
        if (snapshot == null) {
            return new MemoryHitSnapshot(Collections.emptyList(), null);
        }

        if (snapshot.updatedAt() == null || snapshot.updatedAt().isBefore(LocalDateTime.now().minusMinutes(SNAPSHOT_TTL_MINUTES))) {
            snapshots.remove(email);
            return new MemoryHitSnapshot(Collections.emptyList(), null);
        }
        return snapshot;
    }

    private List<String> normalizeHits(List<String> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String hit : hits) {
            if (!StringUtils.hasText(hit)) {
                continue;
            }
            String normalized = hit.trim().replaceAll("\\s+", " ");
            if (!normalized.isEmpty()) {
                unique.add(normalized);
            }
            if (unique.size() >= MAX_HITS) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    public record MemoryHitSnapshot(List<String> hits, LocalDateTime updatedAt) {
    }
}
