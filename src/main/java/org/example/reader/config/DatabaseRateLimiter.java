package org.example.reader.config;

import jakarta.persistence.OptimisticLockException;
import org.example.reader.entity.RateLimitWindowEntity;
import org.example.reader.repository.RateLimitWindowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "security.public.rate-limit.store", havingValue = "database")
public class DatabaseRateLimiter implements PublicApiRateLimiter {

    private final RateLimitWindowRepository repository;
    private final AtomicInteger cleanupTicker = new AtomicInteger();
    private final int cleanupInterval;
    private final int maxRetries;
    private final Clock clock;

    @Autowired
    public DatabaseRateLimiter(
            RateLimitWindowRepository repository,
            @Value("${security.public.rate-limit.cleanup-interval:256}") int cleanupInterval,
            @Value("${security.public.rate-limit.db-max-retries:4}") int maxRetries) {
        this(repository, cleanupInterval, maxRetries, Clock.systemUTC());
    }

    DatabaseRateLimiter(
            RateLimitWindowRepository repository,
            int cleanupInterval,
            int maxRetries,
            Clock clock) {
        this.repository = repository;
        this.cleanupInterval = Math.max(1, cleanupInterval);
        this.maxRetries = Math.max(1, maxRetries);
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean tryConsume(String key, int maxRequests, Duration window) {
        if (key == null || key.isBlank() || maxRequests <= 0 || window == null || window.isZero() || window.isNegative()) {
            return false;
        }

        long windowMillis = Math.max(1, window.toMillis());
        long nowMillis = clock.millis();
        long bucketStart = (nowMillis / windowMillis) * windowMillis;
        String bucketKey = buildBucketKey(key, bucketStart);
        Instant now = Instant.ofEpochMilli(nowMillis);
        Instant expiresAt = Instant.ofEpochMilli(bucketStart).plus(window.multipliedBy(3));

        maybeCleanup(now);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Optional<RateLimitWindowEntity> existing = repository.findById(bucketKey);
                if (existing.isEmpty()) {
                    RateLimitWindowEntity newWindow = new RateLimitWindowEntity();
                    newWindow.setWindowKey(bucketKey);
                    newWindow.setScopeKey(key);
                    newWindow.setWindowStartEpoch(bucketStart);
                    newWindow.setRequestCount(1);
                    newWindow.setUpdatedAt(now);
                    newWindow.setExpiresAt(expiresAt);
                    repository.saveAndFlush(newWindow);
                    return true;
                }

                RateLimitWindowEntity windowEntity = existing.get();
                if (windowEntity.getRequestCount() >= maxRequests) {
                    return false;
                }

                windowEntity.setRequestCount(windowEntity.getRequestCount() + 1);
                windowEntity.setUpdatedAt(now);
                windowEntity.setExpiresAt(expiresAt);
                repository.saveAndFlush(windowEntity);
                return true;
            } catch (DataIntegrityViolationException
                     | ObjectOptimisticLockingFailureException
                     | OptimisticLockException e) {
                if (attempt == maxRetries) {
                    return false;
                }
            }
        }

        return false;
    }

    private String buildBucketKey(String scopeKey, long bucketStartMillis) {
        String normalizedScope = scopeKey.length() > 220 ? scopeKey.substring(0, 220) : scopeKey;
        return normalizedScope + ":" + bucketStartMillis;
    }

    private void maybeCleanup(Instant now) {
        int tick = cleanupTicker.incrementAndGet();
        if (tick % cleanupInterval != 0) {
            return;
        }
        repository.deleteExpired(now);
    }
}
