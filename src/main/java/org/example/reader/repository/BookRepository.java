package org.example.reader.repository;

import org.example.reader.entity.BookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<BookEntity, String> {

    Optional<BookEntity> findBySourceAndSourceId(String source, String sourceId);

    boolean existsBySourceAndSourceId(String source, String sourceId);
}
