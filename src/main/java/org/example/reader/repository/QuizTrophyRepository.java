package org.example.reader.repository;

import org.example.reader.entity.QuizTrophyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizTrophyRepository extends JpaRepository<QuizTrophyEntity, String> {

    Optional<QuizTrophyEntity> findByBookIdAndCode(String bookId, String code);

    List<QuizTrophyEntity> findByBookIdOrderByUnlockedAtDesc(String bookId);
}
