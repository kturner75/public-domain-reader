package org.example.reader.repository;

import org.example.reader.entity.UserReaderClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserReaderClaimRepository extends JpaRepository<UserReaderClaimEntity, String> {

    boolean existsByUserIdAndReaderId(String userId, String readerId);
}
