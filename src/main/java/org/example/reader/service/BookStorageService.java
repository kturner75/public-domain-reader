package org.example.reader.service;

import org.example.reader.entity.BookEntity;
import org.example.reader.entity.ChapterEntity;
import org.example.reader.entity.ParagraphEntity;
import org.example.reader.model.Book;
import org.example.reader.model.ChapterContent;
import org.example.reader.model.Paragraph;
import org.example.reader.repository.BookRepository;
import org.example.reader.repository.ChapterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class BookStorageService {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final SearchService searchService;

    public BookStorageService(BookRepository bookRepository,
                              ChapterRepository chapterRepository,
                              SearchService searchService) {
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
        this.searchService = searchService;
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll().stream()
            .map(this::toBookDto)
            .toList();
    }

    public Optional<Book> getBook(String bookId) {
        return bookRepository.findById(bookId)
            .map(this::toBookDto);
    }

    @Transactional
    public Optional<Book> updateBookFeatures(String bookId,
                                             Boolean ttsEnabled,
                                             Boolean illustrationEnabled,
                                             Boolean characterEnabled) {
        Optional<BookEntity> bookOpt = bookRepository.findById(bookId);
        if (bookOpt.isEmpty()) {
            return Optional.empty();
        }

        BookEntity book = bookOpt.get();
        if (ttsEnabled != null) {
            book.setTtsEnabled(ttsEnabled);
        }
        if (illustrationEnabled != null) {
            book.setIllustrationEnabled(illustrationEnabled);
        }
        if (characterEnabled != null) {
            book.setCharacterEnabled(characterEnabled);
        }

        BookEntity saved = bookRepository.save(book);
        return Optional.of(toBookDto(saved));
    }

    public Optional<ChapterContent> getChapterContent(String bookId, String chapterId) {
        return chapterRepository.findById(chapterId)
            .filter(chapter -> chapter.getBook().getId().equals(bookId))
            .map(this::toChapterContentDto);
    }

    public Optional<ChapterContent> getChapterContentByIndex(String bookId, int chapterIndex) {
        return chapterRepository.findByBookIdAndChapterIndex(bookId, chapterIndex)
            .map(this::toChapterContentDto);
    }

    @Transactional
    public Book saveBook(BookEntity book) {
        BookEntity saved = bookRepository.save(book);
        indexBook(saved);
        return toBookDto(saved);
    }

    @Transactional
    public boolean deleteBook(String bookId) {
        if (!bookRepository.existsById(bookId)) {
            return false;
        }
        bookRepository.deleteById(bookId);
        try {
            searchService.deleteByBookId(bookId);
        } catch (Exception e) {
            System.err.println("Failed to remove book from search index: " + e.getMessage());
        }
        return true;
    }

    @Transactional
    public int deleteAllBooks() {
        List<BookEntity> books = bookRepository.findAll();
        int count = books.size();
        for (BookEntity book : books) {
            try {
                searchService.deleteByBookId(book.getId());
            } catch (Exception e) {
                System.err.println("Failed to remove book from search index: " + e.getMessage());
            }
        }
        bookRepository.deleteAll();
        return count;
    }

    public boolean existsBySource(String source, String sourceId) {
        return bookRepository.existsBySourceAndSourceId(source, sourceId);
    }

    @Transactional(readOnly = true)
    public Optional<Book> findBySource(String source, String sourceId) {
        return bookRepository.findBySourceAndSourceId(source, sourceId)
            .map(this::toBookDto);
    }

    // Index a book and its content in the search index
    private void indexBook(BookEntity book) {
        try {
            searchService.indexBook(book.getId(), book.getTitle(), book.getAuthor());

            for (ChapterEntity chapter : book.getChapters()) {
                for (ParagraphEntity paragraph : chapter.getParagraphs()) {
                    searchService.indexParagraph(
                        book.getId(),
                        chapter.getId(),
                        paragraph.getParagraphIndex(),
                        paragraph.getContent()
                    );
                }
            }
        } catch (Exception e) {
            // Log but don't fail the save operation
            System.err.println("Failed to index book: " + e.getMessage());
        }
    }

    // Reindex all books (useful after application restart)
    @Transactional(readOnly = true)
    public void reindexAll() {
        bookRepository.findAll().forEach(this::indexBook);
    }

    // Convert entity to DTO
    private Book toBookDto(BookEntity entity) {
        List<Book.Chapter> chapters = entity.getChapters().stream()
            .map(ch -> new Book.Chapter(ch.getId(), ch.getTitle()))
            .toList();

        return new Book(
            entity.getId(),
            entity.getTitle(),
            entity.getAuthor(),
            entity.getDescription(),
            entity.getCoverUrl(),
            chapters,
            Boolean.TRUE.equals(entity.getTtsEnabled()),
            Boolean.TRUE.equals(entity.getIllustrationEnabled()),
            Boolean.TRUE.equals(entity.getCharacterEnabled())
        );
    }

    private ChapterContent toChapterContentDto(ChapterEntity entity) {
        List<Paragraph> paragraphs = entity.getParagraphs().stream()
            .map(p -> new Paragraph(p.getParagraphIndex(), p.getContent()))
            .toList();

        return new ChapterContent(
            entity.getBook().getId(),
            entity.getId(),
            entity.getTitle(),
            paragraphs
        );
    }
}
