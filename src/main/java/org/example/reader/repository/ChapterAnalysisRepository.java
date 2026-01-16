package org.example.reader.repository;

import org.example.reader.entity.ChapterAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChapterAnalysisRepository extends JpaRepository<ChapterAnalysisEntity, String> {

    Optional<ChapterAnalysisEntity> findByChapterId(String chapterId);

    boolean existsByChapterId(String chapterId);

    @Modifying
    @Query("DELETE FROM ChapterAnalysisEntity ca WHERE ca.chapter.book.id = :bookId")
    void deleteByBookId(@Param("bookId") String bookId);
}
