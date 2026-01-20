package org.example.reader.controller;

import org.example.reader.model.Book;
import org.example.reader.model.ChapterContent;
import org.example.reader.model.Paragraph;
import org.example.reader.service.BookStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LibraryController.class)
class LibraryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookStorageService bookStorageService;

    @Test
    void listBooks_returnsAllBooks() throws Exception {
        List<Book> books = List.of(
            new Book("book-1", "Pride and Prejudice", "Jane Austen", "A romantic novel.", null, List.of(), false, false, false),
            new Book("book-2", "Moby Dick", "Herman Melville", "A whale tale.", null, List.of(), false, false, false)
        );
        when(bookStorageService.getAllBooks()).thenReturn(books);

        mockMvc.perform(get("/api/library"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id", is("book-1")))
            .andExpect(jsonPath("$[0].title", is("Pride and Prejudice")))
            .andExpect(jsonPath("$[0].author", is("Jane Austen")))
            .andExpect(jsonPath("$[1].id", is("book-2")))
            .andExpect(jsonPath("$[1].title", is("Moby Dick")));
    }

    @Test
    void listBooks_includesChapters() throws Exception {
        List<Book.Chapter> chapters = List.of(
            new Book.Chapter("ch-1", "Chapter 1"),
            new Book.Chapter("ch-2", "Chapter 2"),
            new Book.Chapter("ch-3", "Chapter 3")
        );
        List<Book> books = List.of(
            new Book("book-1", "Pride and Prejudice", "Jane Austen", null, null, chapters, false, false, false)
        );
        when(bookStorageService.getAllBooks()).thenReturn(books);

        mockMvc.perform(get("/api/library"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].chapters", hasSize(3)))
            .andExpect(jsonPath("$[0].chapters[0].id", is("ch-1")))
            .andExpect(jsonPath("$[0].chapters[0].title", is("Chapter 1")));
    }

    @Test
    void getBook_existingBook_returnsBook() throws Exception {
        Book book = new Book("book-1", "Pride and Prejudice", "Jane Austen",
            "A romantic novel following the Bennet family.", null, List.of(), false, false, false);
        when(bookStorageService.getBook("book-1")).thenReturn(Optional.of(book));

        mockMvc.perform(get("/api/library/book-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is("book-1")))
            .andExpect(jsonPath("$.title", is("Pride and Prejudice")))
            .andExpect(jsonPath("$.author", is("Jane Austen")))
            .andExpect(jsonPath("$.description", is("A romantic novel following the Bennet family.")));
    }

    @Test
    void getBook_nonExistingBook_returns404() throws Exception {
        when(bookStorageService.getBook("unknown-book")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/library/unknown-book"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getChapterContent_existingChapter_returnsContent() throws Exception {
        List<Paragraph> paragraphs = List.of(
            new Paragraph(0, "It is a truth universally acknowledged..."),
            new Paragraph(1, "However little known...")
        );
        ChapterContent content = new ChapterContent("book-1", "ch-1", "Chapter 1", paragraphs);
        when(bookStorageService.getChapterContent("book-1", "ch-1")).thenReturn(Optional.of(content));

        mockMvc.perform(get("/api/library/book-1/chapters/ch-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bookId", is("book-1")))
            .andExpect(jsonPath("$.chapterId", is("ch-1")))
            .andExpect(jsonPath("$.title", is("Chapter 1")))
            .andExpect(jsonPath("$.paragraphs", hasSize(2)))
            .andExpect(jsonPath("$.paragraphs[0].index", is(0)))
            .andExpect(jsonPath("$.paragraphs[0].content", containsString("truth universally acknowledged")));
    }

    @Test
    void getChapterContent_existingChapter_returnsParagraphsInOrder() throws Exception {
        List<Paragraph> paragraphs = List.of(
            new Paragraph(0, "Call me Ishmael."),
            new Paragraph(1, "It is a way I have of driving off the spleen.")
        );
        ChapterContent content = new ChapterContent("book-2", "ch-1", "Loomings", paragraphs);
        when(bookStorageService.getChapterContent("book-2", "ch-1")).thenReturn(Optional.of(content));

        mockMvc.perform(get("/api/library/book-2/chapters/ch-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paragraphs[0].index", is(0)))
            .andExpect(jsonPath("$.paragraphs[0].content", containsString("Call me Ishmael")))
            .andExpect(jsonPath("$.paragraphs[1].index", is(1)))
            .andExpect(jsonPath("$.paragraphs[1].content", containsString("driving off the spleen")));
    }

    @Test
    void getChapterContent_nonExistingBook_returns404() throws Exception {
        when(bookStorageService.getChapterContent("unknown-book", "ch-1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/library/unknown-book/chapters/ch-1"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getChapterContent_nonExistingChapter_returns404() throws Exception {
        when(bookStorageService.getChapterContent("book-1", "ch-99")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/library/book-1/chapters/ch-99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getChapterContent_chapterWithoutContent_returnsEmptyParagraphs() throws Exception {
        ChapterContent content = new ChapterContent("book-1", "ch-2", "Chapter 2", List.of());
        when(bookStorageService.getChapterContent("book-1", "ch-2")).thenReturn(Optional.of(content));

        mockMvc.perform(get("/api/library/book-1/chapters/ch-2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paragraphs", hasSize(0)));
    }

    @Test
    void updateBookFeatures_updatesFlags() throws Exception {
        Book updated = new Book(
            "book-1",
            "Pride and Prejudice",
            "Jane Austen",
            "A romantic novel following the Bennet family.",
            null,
            List.of(),
            true,
            false,
            true
        );
        when(bookStorageService.updateBookFeatures("book-1", true, false, true))
            .thenReturn(Optional.of(updated));

        mockMvc.perform(patch("/api/library/book-1/features")
                .contentType("application/json")
                .content("{\"ttsEnabled\":true,\"illustrationEnabled\":false,\"characterEnabled\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is("book-1")))
            .andExpect(jsonPath("$.ttsEnabled", is(true)))
            .andExpect(jsonPath("$.illustrationEnabled", is(false)))
            .andExpect(jsonPath("$.characterEnabled", is(true)));
    }
}
