package org.example.reader.repository;

import org.example.reader.entity.CharacterEntity;
import org.example.reader.entity.CharacterStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterRepository extends JpaRepository<CharacterEntity, String> {

    @Query("SELECT c FROM CharacterEntity c JOIN FETCH c.book JOIN FETCH c.firstChapter WHERE c.id = :id")
    Optional<CharacterEntity> findByIdWithBookAndChapter(@Param("id") String id);

    List<CharacterEntity> findByBookIdOrderByCreatedAt(String bookId);

    Optional<CharacterEntity> findByBookIdAndNameIgnoreCase(String bookId, String name);

    List<CharacterEntity> findByBookIdAndStatus(String bookId, CharacterStatus status);

    List<CharacterEntity> findByStatus(CharacterStatus status);

    @Query("SELECT c FROM CharacterEntity c WHERE c.book.id = :bookId " +
           "AND c.firstChapter.chapterIndex <= :chapterIndex " +
           "ORDER BY c.firstChapter.chapterIndex, c.firstParagraphIndex")
    List<CharacterEntity> findByBookIdUpToChapter(
            @Param("bookId") String bookId,
            @Param("chapterIndex") int chapterIndex);

    @Query("SELECT c FROM CharacterEntity c WHERE c.book.id = :bookId AND " +
           "(c.firstChapter.chapterIndex < :chapterIndex OR " +
           "(c.firstChapter.chapterIndex = :chapterIndex AND c.firstParagraphIndex <= :paragraphIndex)) " +
           "ORDER BY c.characterType ASC, c.firstChapter.chapterIndex, c.firstParagraphIndex")
    List<CharacterEntity> findByBookIdUpToPosition(
            @Param("bookId") String bookId,
            @Param("chapterIndex") int chapterIndex,
            @Param("paragraphIndex") int paragraphIndex);

    @Query("SELECT c FROM CharacterEntity c WHERE c.book.id = :bookId " +
           "AND c.status = 'COMPLETED' " +
           "AND c.completedAt > :sinceTime " +
           "ORDER BY c.completedAt")
    List<CharacterEntity> findNewlyCompletedSince(
            @Param("bookId") String bookId,
            @Param("sinceTime") LocalDateTime sinceTime);

    void deleteByBookId(String bookId);
}
