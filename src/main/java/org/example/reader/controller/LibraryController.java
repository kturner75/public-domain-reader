package org.example.reader.controller;

import org.example.reader.model.Book;
import org.example.reader.model.ChapterContent;
import org.example.reader.service.BookStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final BookStorageService bookStorageService;

    public LibraryController(BookStorageService bookStorageService) {
        this.bookStorageService = bookStorageService;
    }

    @GetMapping
    public List<Book> listBooks() {
        return bookStorageService.getAllBooks();
    }

    @GetMapping("/{bookId}")
    public ResponseEntity<Book> getBook(@PathVariable String bookId) {
        return bookStorageService.getBook(bookId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{bookId}/features")
    public ResponseEntity<Book> updateBookFeatures(
            @PathVariable String bookId,
            @RequestBody FeatureUpdateRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        return bookStorageService.updateBookFeatures(
                bookId,
                request.ttsEnabled(),
                request.illustrationEnabled(),
                request.characterEnabled()
        ).map(ResponseEntity::ok)
         .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{bookId}/chapters/{chapterId}")
    public ResponseEntity<ChapterContent> getChapterContent(
            @PathVariable String bookId,
            @PathVariable String chapterId) {
        return bookStorageService.getChapterContent(bookId, chapterId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{bookId}")
    public ResponseEntity<Void> deleteBook(@PathVariable String bookId) {
        boolean deleted = bookStorageService.deleteBook(bookId);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping
    public ResponseEntity<DeleteAllResponse> deleteAllBooks() {
        int count = bookStorageService.deleteAllBooks();
        return ResponseEntity.ok(new DeleteAllResponse(count));
    }

    public record DeleteAllResponse(int deletedCount) {}

    public record FeatureUpdateRequest(
            Boolean ttsEnabled,
            Boolean illustrationEnabled,
            Boolean characterEnabled
    ) {}
}
