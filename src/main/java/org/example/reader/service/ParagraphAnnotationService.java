package org.example.reader.service;

import org.example.reader.entity.ChapterEntity;
import org.example.reader.entity.ParagraphAnnotationEntity;
import org.example.reader.entity.ParagraphEntity;
import org.example.reader.model.BookmarkedParagraph;
import org.example.reader.model.ParagraphAnnotation;
import org.example.reader.repository.BookRepository;
import org.example.reader.repository.ChapterRepository;
import org.example.reader.repository.ParagraphAnnotationRepository;
import org.example.reader.repository.ParagraphRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ParagraphAnnotationService {

    private static final int MAX_NOTE_LENGTH = 4000;
    private static final int SNIPPET_MAX_LENGTH = 180;

    private final ParagraphAnnotationRepository paragraphAnnotationRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final ParagraphRepository paragraphRepository;

    public ParagraphAnnotationService(
            ParagraphAnnotationRepository paragraphAnnotationRepository,
            BookRepository bookRepository,
            ChapterRepository chapterRepository,
            ParagraphRepository paragraphRepository) {
        this.paragraphAnnotationRepository = paragraphAnnotationRepository;
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
        this.paragraphRepository = paragraphRepository;
    }

    @Transactional(readOnly = true)
    public Optional<List<ParagraphAnnotation>> getBookAnnotations(String readerId, String bookId) {
        if (!bookRepository.existsById(bookId)) {
            return Optional.empty();
        }
        List<ParagraphAnnotation> annotations = paragraphAnnotationRepository
                .findByReaderIdAndBook_Id(readerId, bookId)
                .stream()
                .map(this::toParagraphAnnotation)
                .toList();
        return Optional.of(annotations);
    }

    @Transactional(readOnly = true)
    public Optional<List<BookmarkedParagraph>> getBookmarkedParagraphs(String readerId, String bookId) {
        if (!bookRepository.existsById(bookId)) {
            return Optional.empty();
        }

        List<BookmarkedParagraph> bookmarks = paragraphAnnotationRepository
                .findByReaderIdAndBook_IdAndBookmarkedTrueOrderByUpdatedAtDesc(readerId, bookId)
                .stream()
                .map(annotation -> {
                    String chapterId = annotation.getChapter().getId();
                    String chapterTitle = annotation.getChapter().getTitle();
                    String snippet = paragraphRepository
                            .findByChapterIdAndParagraphIndex(chapterId, annotation.getParagraphIndex())
                            .map(ParagraphEntity::getContent)
                            .map(this::toSnippet)
                            .orElse("");
                    return new BookmarkedParagraph(
                            chapterId,
                            chapterTitle,
                            annotation.getParagraphIndex(),
                            snippet,
                            annotation.getUpdatedAt()
                    );
                })
                .toList();

        return Optional.of(bookmarks);
    }

    @Transactional
    public SaveOutcome saveAnnotation(
            String readerId,
            String bookId,
            String chapterId,
            int paragraphIndex,
            boolean highlighted,
            String noteText,
            boolean bookmarked) {
        Optional<ChapterEntity> chapterOpt = chapterRepository.findByIdWithBook(chapterId)
                .filter(chapter -> chapter.getBook().getId().equals(bookId));
        if (chapterOpt.isEmpty()) {
            return new SaveOutcome(SaveStatus.NOT_FOUND, null);
        }
        if (!paragraphRepository.existsByChapterIdAndParagraphIndex(chapterId, paragraphIndex)) {
            return new SaveOutcome(SaveStatus.NOT_FOUND, null);
        }

        String sanitizedNote = sanitizeNote(noteText);
        boolean hasData = highlighted || bookmarked || sanitizedNote != null;

        Optional<ParagraphAnnotationEntity> existingOpt = paragraphAnnotationRepository
                .findByReaderIdAndBook_IdAndChapter_IdAndParagraphIndex(readerId, bookId, chapterId, paragraphIndex);

        if (!hasData) {
            existingOpt.ifPresent(paragraphAnnotationRepository::delete);
            return new SaveOutcome(SaveStatus.CLEARED, null);
        }

        ParagraphAnnotationEntity entity = existingOpt.orElseGet(ParagraphAnnotationEntity::new);
        entity.setReaderId(readerId);
        entity.setBook(chapterOpt.get().getBook());
        entity.setChapter(chapterOpt.get());
        entity.setParagraphIndex(paragraphIndex);
        entity.setHighlighted(highlighted);
        entity.setBookmarked(bookmarked);
        entity.setNoteText(sanitizedNote);

        ParagraphAnnotationEntity saved = paragraphAnnotationRepository.save(entity);
        return new SaveOutcome(SaveStatus.SAVED, toParagraphAnnotation(saved));
    }

    @Transactional
    public DeleteStatus deleteAnnotation(String readerId, String bookId, String chapterId, int paragraphIndex) {
        Optional<ChapterEntity> chapterOpt = chapterRepository.findByIdWithBook(chapterId)
                .filter(chapter -> chapter.getBook().getId().equals(bookId));
        if (chapterOpt.isEmpty()) {
            return DeleteStatus.NOT_FOUND;
        }
        if (!paragraphRepository.existsByChapterIdAndParagraphIndex(chapterId, paragraphIndex)) {
            return DeleteStatus.NOT_FOUND;
        }

        paragraphAnnotationRepository
                .findByReaderIdAndBook_IdAndChapter_IdAndParagraphIndex(readerId, bookId, chapterId, paragraphIndex)
                .ifPresent(paragraphAnnotationRepository::delete);

        return DeleteStatus.DELETED;
    }

    private ParagraphAnnotation toParagraphAnnotation(ParagraphAnnotationEntity entity) {
        return new ParagraphAnnotation(
                entity.getChapter().getId(),
                entity.getParagraphIndex(),
                entity.isHighlighted(),
                entity.getNoteText(),
                entity.isBookmarked(),
                entity.getUpdatedAt()
        );
    }

    private String sanitizeNote(String noteText) {
        if (noteText == null) {
            return null;
        }
        String trimmed = noteText.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            return trimmed.substring(0, MAX_NOTE_LENGTH);
        }
        return trimmed;
    }

    private String toSnippet(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return "";
        }
        String text = htmlContent
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() <= SNIPPET_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, SNIPPET_MAX_LENGTH - 3) + "...";
    }

    public enum SaveStatus {
        NOT_FOUND,
        SAVED,
        CLEARED
    }

    public record SaveOutcome(SaveStatus status, ParagraphAnnotation annotation) {
    }

    public enum DeleteStatus {
        NOT_FOUND,
        DELETED
    }
}
