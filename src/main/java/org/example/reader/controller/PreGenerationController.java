package org.example.reader.controller;

import org.example.reader.service.PreGenerationService;
import org.example.reader.service.PreGenerationService.PreGenResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint for triggering pre-generation of book assets.
 * This is a long-running operation that will block until all assets are generated.
 */
@RestController
@RequestMapping("/api/pregen")
public class PreGenerationController {

    private final PreGenerationService preGenerationService;

    public PreGenerationController(PreGenerationService preGenerationService) {
        this.preGenerationService = preGenerationService;
    }

    /**
     * Pre-generate all assets (illustrations and character portraits) for a book.
     * This is a blocking operation that will wait for all generation to complete.
     *
     * @param bookId The book ID to generate assets for
     * @return Result containing generation statistics
     */
    @PostMapping("/book/{bookId}")
    public ResponseEntity<PreGenResult> preGenerateForBook(@PathVariable String bookId) {
        PreGenResult result = preGenerationService.preGenerateForBook(bookId);

        if (result.success()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Pre-generate all assets for a book by its Gutenberg ID.
     * Will import the book if not already present.
     * This is a blocking operation that will wait for all generation to complete.
     *
     * @param gutenbergId The Project Gutenberg book ID
     * @return Result containing generation statistics
     */
    @PostMapping("/gutenberg/{gutenbergId}")
    public ResponseEntity<PreGenResult> preGenerateByGutenbergId(@PathVariable int gutenbergId) {
        PreGenResult result = preGenerationService.preGenerateByGutenbergId(gutenbergId);

        if (result.success()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
}
