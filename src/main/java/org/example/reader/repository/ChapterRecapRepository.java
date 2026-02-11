package org.example.reader.repository;

import org.example.reader.entity.ChapterRecapEntity;
import org.example.reader.entity.ChapterRecapStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRecapRepository extends JpaRepository<ChapterRecapEntity, String> {

    Optional<ChapterRecapEntity> findByChapterId(String chapterId);

    List<ChapterRecapEntity> findByChapterBookIdAndStatus(String bookId, ChapterRecapStatus status);

    List<ChapterRecapEntity> findByChapterBookIdAndStatusIsNull(String bookId);

    boolean existsByChapterBookIdAndStatus(String bookId, ChapterRecapStatus status);

    @Query("SELECT cr FROM ChapterRecapEntity cr JOIN FETCH cr.chapter c JOIN FETCH c.book WHERE c.id = :chapterId")
    Optional<ChapterRecapEntity> findByChapterIdWithChapterAndBook(@Param("chapterId") String chapterId);
}
