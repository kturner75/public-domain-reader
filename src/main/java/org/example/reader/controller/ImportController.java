package org.example.reader.controller;

import org.example.reader.service.BookImportService;
import org.example.reader.service.BookImportService.ImportResult;
import org.example.reader.service.BookImportService.SearchResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final BookImportService bookImportService;

    public ImportController(BookImportService bookImportService) {
        this.bookImportService = bookImportService;
    }

    @GetMapping("/search")
    public List<SearchResult> searchGutenberg(@RequestParam String q) {
        return bookImportService.searchGutenberg(q);
    }

    @GetMapping("/popular")
    public List<SearchResult> getPopularBooks(@RequestParam(defaultValue = "1") int page) {
        return bookImportService.getPopularBooks(page);
    }

    @PostMapping("/gutenberg/{gutenbergId}")
    public ResponseEntity<ImportResult> importBook(@PathVariable int gutenbergId) {
        ImportResult result = bookImportService.importBook(gutenbergId);

        if (result.success()) {
            return ResponseEntity.ok(result);
        } else if (result.message().equals("Book already imported")) {
            return ResponseEntity.status(409).body(result); // Conflict
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
}
