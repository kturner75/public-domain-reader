package org.example.reader.config;

import org.example.reader.service.BookStorageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2) // Run after DataInitializer
public class SearchIndexInitializer implements CommandLineRunner {

    private final BookStorageService bookStorageService;

    public SearchIndexInitializer(BookStorageService bookStorageService) {
        this.bookStorageService = bookStorageService;
    }

    @Override
    public void run(String... args) {
        // Reindex all books from database into Lucene
        bookStorageService.reindexAll();
        System.out.println("Search index rebuilt from database");
    }
}
