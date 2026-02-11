package org.example.reader.repository;

import org.example.reader.entity.QuizAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttemptEntity, String> {

    long countByChapterBookId(String bookId);

    long countByChapterBookIdAndPerfectTrue(String bookId);

    List<QuizAttemptEntity> findByChapterBookIdOrderByCreatedAtDesc(String bookId);
}
