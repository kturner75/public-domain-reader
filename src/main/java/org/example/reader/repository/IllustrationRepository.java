package org.example.reader.repository;

import org.example.reader.entity.IllustrationEntity;
import org.example.reader.entity.IllustrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IllustrationRepository extends JpaRepository<IllustrationEntity, String> {

    Optional<IllustrationEntity> findByChapterId(String chapterId);

    List<IllustrationEntity> findByStatus(IllustrationStatus status);

    List<IllustrationEntity> findByChapterBookIdAndStatus(String bookId, IllustrationStatus status);
}
