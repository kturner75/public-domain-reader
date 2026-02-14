package org.example.reader.repository;

import org.example.reader.entity.RateLimitWindowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface RateLimitWindowRepository extends JpaRepository<RateLimitWindowEntity, String> {

    @Modifying
    @Query("delete from RateLimitWindowEntity w where w.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
