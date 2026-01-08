package org.example.reader.service;

import org.example.reader.entity.BookEntity;
import org.example.reader.gutendex.GutenbergContentParser;
import org.example.reader.gutendex.GutenbergContentParser.ParsedBook;
import org.example.reader.gutendex.GutenbergContentParser.ParsedChapter;
import org.example.reader.gutendex.GutendexBook;
import org.example.reader.gutendex.GutendexClient;
import org.example.reader.gutendex.GutendexResponse;
import org.example.reader.model.Book;
import org.example.reader.service.BookImportService.ImportResult;
import org.example.reader.service.BookImportService.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookImportServiceTest {

    @Mock
    private GutendexClient gutendexClient;

    @Mock
    private GutenbergContentParser contentParser;

    @Mock
    private BookStorageService bookStorageService;

    private BookImportService bookImportService;

    @BeforeEach
    void setUp() {
        bookImportService = new BookImportService(gutendexClient, contentParser, bookStorageService);
    }

    @Test
    void searchGutenbergReturnsResults() {
        GutendexBook book = createGutendexBook(1, "Pride and Prejudice", "Jane Austen");
        GutendexResponse response = new GutendexResponse(1, null, null, List.of(book));
        when(gutendexClient.searchBooks("austen")).thenReturn(response);
        when(bookStorageService.existsBySource("gutenberg", "1")).thenReturn(false);

        List<SearchResult> results = bookImportService.searchGutenberg("austen");

        assertEquals(1, results.size());
        assertEquals(1, results.get(0).gutenbergId());
        assertEquals("Pride and Prejudice", results.get(0).title());
        assertEquals("Jane Austen", results.get(0).author());
        assertFalse(results.get(0).alreadyImported());
    }

    @Test
    void searchGutenbergMarksAlreadyImportedBooks() {
        GutendexBook book = createGutendexBook(1, "Pride and Prejudice", "Jane Austen");
        GutendexResponse response = new GutendexResponse(1, null, null, List.of(book));
        when(gutendexClient.searchBooks("austen")).thenReturn(response);
        when(bookStorageService.existsBySource("gutenberg", "1")).thenReturn(true);

        List<SearchResult> results = bookImportService.searchGutenberg("austen");

        assertEquals(1, results.size());
        assertTrue(results.get(0).alreadyImported());
    }

    @Test
    void searchGutenbergFiltersNonEnglishBooks() {
        GutendexBook englishBook = createGutendexBook(1, "Pride and Prejudice", "Jane Austen");
        GutendexBook frenchBook = new GutendexBook(
            2, "Les Misérables",
            List.of(new GutendexBook.Author("Victor Hugo", null, null)),
            List.of(), List.of(), List.of("fr"),
            Map.of("text/html", "http://example.com/book.html"),
            1000
        );
        GutendexResponse response = new GutendexResponse(2, null, null, List.of(englishBook, frenchBook));
        when(gutendexClient.searchBooks("book")).thenReturn(response);
        when(bookStorageService.existsBySource(anyString(), anyString())).thenReturn(false);

        List<SearchResult> results = bookImportService.searchGutenberg("book");

        assertEquals(1, results.size());
        assertEquals("Pride and Prejudice", results.get(0).title());
    }

    @Test
    void searchGutenbergFiltersBooksSansHtml() {
        GutendexBook bookWithHtml = createGutendexBook(1, "Pride and Prejudice", "Jane Austen");
        GutendexBook bookWithoutHtml = new GutendexBook(
            2, "No HTML Book",
            List.of(new GutendexBook.Author("Author", null, null)),
            List.of(), List.of(), List.of("en"),
            Map.of("text/plain", "http://example.com/book.txt"),
            500
        );
        GutendexResponse response = new GutendexResponse(2, null, null, List.of(bookWithHtml, bookWithoutHtml));
        when(gutendexClient.searchBooks("book")).thenReturn(response);
        when(bookStorageService.existsBySource(anyString(), anyString())).thenReturn(false);

        List<SearchResult> results = bookImportService.searchGutenberg("book");

        assertEquals(1, results.size());
        assertEquals("Pride and Prejudice", results.get(0).title());
    }

    @Test
    void searchGutenbergHandlesNullResponse() {
        when(gutendexClient.searchBooks("query")).thenReturn(null);

        List<SearchResult> results = bookImportService.searchGutenberg("query");

        assertTrue(results.isEmpty());
    }

    @Test
    void getPopularBooksReturnsResults() {
        GutendexBook book = createGutendexBook(1, "Moby Dick", "Herman Melville");
        GutendexResponse response = new GutendexResponse(1, null, null, List.of(book));
        when(gutendexClient.getPopularBooks()).thenReturn(response);
        when(bookStorageService.existsBySource(anyString(), anyString())).thenReturn(false);

        List<SearchResult> results = bookImportService.getPopularBooks();

        assertEquals(1, results.size());
        assertEquals("Moby Dick", results.get(0).title());
    }

    @Test
    void getPopularBooksWithPageReturnsResults() {
        GutendexBook book = createGutendexBook(1, "Moby Dick", "Herman Melville");
        GutendexResponse response = new GutendexResponse(1, null, null, List.of(book));
        when(gutendexClient.getPopularBooks(2)).thenReturn(response);
        when(bookStorageService.existsBySource(anyString(), anyString())).thenReturn(false);

        List<SearchResult> results = bookImportService.getPopularBooks(2);

        assertEquals(1, results.size());
        verify(gutendexClient).getPopularBooks(2);
    }

    @Test
    void importBookSucceedsForNewBook() {
        GutendexBook gutendexBook = createGutendexBook(1234, "Test Book", "Test Author");
        when(bookStorageService.existsBySource("gutenberg", "1234")).thenReturn(false);
        when(gutendexClient.getBook(1234)).thenReturn(Optional.of(gutendexBook));
        when(gutendexClient.fetchContent(anyString())).thenReturn("<html><body><p>Content</p></body></html>");

        ParsedChapter chapter = new ParsedChapter("Chapter 1", List.of("Paragraph one", "Paragraph two"));
        ParsedBook parsedBook = new ParsedBook(List.of(chapter));
        when(contentParser.parse(anyString())).thenReturn(parsedBook);

        Book savedBook = new Book("book-id", "Test Book", "Test Author", "", null, List.of());
        when(bookStorageService.saveBook(any(BookEntity.class))).thenReturn(savedBook);

        ImportResult result = bookImportService.importBook(1234);

        assertTrue(result.success());
        assertEquals("book-id", result.bookId());
        assertEquals("Successfully imported", result.message());
        assertEquals(1, result.chapterCount());
        assertEquals(2, result.paragraphCount());
    }

    @Test
    void importBookFailsIfAlreadyImported() {
        when(bookStorageService.existsBySource("gutenberg", "1234")).thenReturn(true);
        when(bookStorageService.findBySource("gutenberg", "1234"))
            .thenReturn(Optional.of(new Book("existing-id", "Test", "Author", "", null, List.of())));

        ImportResult result = bookImportService.importBook(1234);

        assertFalse(result.success());
        assertEquals("existing-id", result.bookId());
        assertEquals("Book already imported", result.message());
    }

    @Test
    void importBookFailsIfBookNotFound() {
        when(bookStorageService.existsBySource("gutenberg", "1234")).thenReturn(false);
        when(gutendexClient.getBook(1234)).thenReturn(Optional.empty());

        ImportResult result = bookImportService.importBook(1234);

        assertFalse(result.success());
        assertNull(result.bookId());
        assertEquals("Book not found in Gutenberg", result.message());
    }

    @Test
    void importBookFailsIfNoHtmlVersion() {
        GutendexBook bookWithoutHtml = new GutendexBook(
            1234, "No HTML",
            List.of(new GutendexBook.Author("Author", null, null)),
            List.of(), List.of(), List.of("en"),
            Map.of("text/plain", "http://example.com/book.txt"),
            100
        );
        when(bookStorageService.existsBySource("gutenberg", "1234")).thenReturn(false);
        when(gutendexClient.getBook(1234)).thenReturn(Optional.of(bookWithoutHtml));

        ImportResult result = bookImportService.importBook(1234);

        assertFalse(result.success());
        assertEquals("No HTML version available", result.message());
    }

    @Test
    void importBookFailsIfContentFetchFails() {
        GutendexBook gutendexBook = createGutendexBook(1234, "Test Book", "Test Author");
        when(bookStorageService.existsBySource("gutenberg", "1234")).thenReturn(false);
        when(gutendexClient.getBook(1234)).thenReturn(Optional.of(gutendexBook));
        when(gutendexClient.fetchContent(anyString())).thenThrow(new RuntimeException("Network error"));

        ImportResult result = bookImportService.importBook(1234);

        assertFalse(result.success());
        assertTrue(result.message().contains("Failed to fetch content"));
    }

    @Test
    void importBookFailsIfNoContentParsed() {
        GutendexBook gutendexBook = createGutendexBook(1234, "Test Book", "Test Author");
        when(bookStorageService.existsBySource("gutenberg", "1234")).thenReturn(false);
        when(gutendexClient.getBook(1234)).thenReturn(Optional.of(gutendexBook));
        when(gutendexClient.fetchContent(anyString())).thenReturn("<html><body></body></html>");
        when(contentParser.parse(anyString())).thenReturn(new ParsedBook(List.of()));

        ImportResult result = bookImportService.importBook(1234);

        assertFalse(result.success());
        assertEquals("No content could be parsed", result.message());
    }

    @Test
    void importBookSetsCorrectSourceInfo() {
        GutendexBook gutendexBook = createGutendexBook(1234, "Test Book", "Test Author");
        when(bookStorageService.existsBySource("gutenberg", "1234")).thenReturn(false);
        when(gutendexClient.getBook(1234)).thenReturn(Optional.of(gutendexBook));
        when(gutendexClient.fetchContent(anyString())).thenReturn("<html><body><p>Content</p></body></html>");

        ParsedChapter chapter = new ParsedChapter("Chapter 1", List.of("Paragraph"));
        when(contentParser.parse(anyString())).thenReturn(new ParsedBook(List.of(chapter)));

        Book savedBook = new Book("id", "Test Book", "Test Author", "", null, List.of());
        when(bookStorageService.saveBook(any(BookEntity.class))).thenReturn(savedBook);

        bookImportService.importBook(1234);

        ArgumentCaptor<BookEntity> captor = ArgumentCaptor.forClass(BookEntity.class);
        verify(bookStorageService).saveBook(captor.capture());

        BookEntity saved = captor.getValue();
        assertEquals("gutenberg", saved.getSource());
        assertEquals("1234", saved.getSourceId());
    }

    private GutendexBook createGutendexBook(int id, String title, String author) {
        return new GutendexBook(
            id, title,
            List.of(new GutendexBook.Author(author, null, null)),
            List.of("Fiction"), List.of(), List.of("en"),
            Map.of("text/html", "http://gutenberg.org/files/" + id + "/" + id + "-h.htm"),
            1000
        );
    }
}
