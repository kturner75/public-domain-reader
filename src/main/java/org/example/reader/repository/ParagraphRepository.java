package org.example.reader.repository;

import org.example.reader.entity.ParagraphEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParagraphRepository extends JpaRepository<ParagraphEntity, String> {

    List<ParagraphEntity> findByChapterIdOrderByParagraphIndex(String chapterId);
}
