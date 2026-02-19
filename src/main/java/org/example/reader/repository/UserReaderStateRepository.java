package org.example.reader.repository;

import org.example.reader.entity.UserReaderStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserReaderStateRepository extends JpaRepository<UserReaderStateEntity, String> {
}
