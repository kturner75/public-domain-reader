package org.example.reader.cli;

import org.example.reader.service.PreGenerationService;
import org.example.reader.service.PreGenerationService.PreGenResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Command-line runner for batch pre-generation of assets for the top 20 public domain books.
 *
 * Run with: mvn spring-boot:run -Dspring-boot.run.profiles=pregen-batch
 * Or: java -jar target/reader.jar --spring.profiles.active=pregen-batch
 */
@Component
@Profile("pregen-batch")
public class PreGenerationBatchRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PreGenerationBatchRunner.class);

    private final PreGenerationService preGenerationService;

    @Value("${pregen.cooldown-minutes:3}")
    private int cooldownMinutes;

    @Value("${pregen.cooldown-skip-under-images:20}")
    private int cooldownSkipUnderImages;

    public PreGenerationBatchRunner(PreGenerationService preGenerationService) {
        this.preGenerationService = preGenerationService;
    }

    /**
     * Top 20 public domain books with their Project Gutenberg IDs.
     * All books are confirmed public domain (published before 1928).
     */
    private static final List<BookEntry> TOP_20_BOOKS = List.of(
            new BookEntry(1342, "Pride and Prejudice", "Jane Austen"),
            new BookEntry(2701, "Moby Dick", "Herman Melville"),
            new BookEntry(84, "Frankenstein", "Mary Shelley"),
            new BookEntry(345, "Dracula", "Bram Stoker"),
            new BookEntry(11, "Alice's Adventures in Wonderland", "Lewis Carroll"),
            new BookEntry(1260, "Jane Eyre", "Charlotte Bronte"),
            new BookEntry(768, "Wuthering Heights", "Emily Bronte"),
            new BookEntry(174, "The Picture of Dorian Gray", "Oscar Wilde"),
            new BookEntry(98, "A Tale of Two Cities", "Charles Dickens"),
            new BookEntry(1184, "The Count of Monte Cristo", "Alexandre Dumas"),
            new BookEntry(2554, "Crime and Punishment", "Fyodor Dostoyevsky"),
            new BookEntry(1399, "Anna Karenina", "Leo Tolstoy"),
            new BookEntry(1661, "The Adventures of Sherlock Holmes", "Arthur Conan Doyle"),
            new BookEntry(1727, "The Odyssey", "Homer"),
            new BookEntry(996, "Don Quixote", "Miguel de Cervantes"),
            new BookEntry(135, "Les Miserables", "Victor Hugo"),
            new BookEntry(2600, "War and Peace", "Leo Tolstoy"),
            new BookEntry(28054, "The Brothers Karamazov", "Fyodor Dostoyevsky"),
            new BookEntry(120, "Treasure Island", "Robert Louis Stevenson"),
            new BookEntry(25, "The Scarlet Letter", "Nathaniel Hawthorne")
    );

    private record BookEntry(int gutenbergId, String title, String author) {}

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("Pre-Generation Batch Runner");
        log.info("========================================");
        log.info("Processing {} books with {}min cooldown between books", TOP_20_BOOKS.size(), cooldownMinutes);
        log.info("");

        List<PreGenResult> results = new ArrayList<>();
        int bookNumber = 0;

        for (BookEntry book : TOP_20_BOOKS) {
            bookNumber++;
            log.info("========================================");
            log.info("[{}/{}] Processing: '{}' by {}", bookNumber, TOP_20_BOOKS.size(), book.title(), book.author());
            log.info("Gutenberg ID: {}", book.gutenbergId());
            log.info("========================================");

            PreGenResult result;
            try {
                result = preGenerationService.preGenerateByGutenbergId(book.gutenbergId());
                results.add(result);

                log.info("Result for '{}': {}", book.title(), result.message());
                log.info("  - Chapters: {}", result.chaptersProcessed());
                log.info("  - Illustrations: {} completed, {} failed",
                        result.illustrationsCompleted(), result.illustrationsFailed());
                log.info("  - Portraits: {} completed, {} failed",
                        result.portraitsCompleted(), result.portraitsFailed());

            } catch (Exception e) {
                log.error("Failed to process '{}': {}", book.title(), e.getMessage(), e);
                result = PreGenResult.failure("Exception: " + e.getMessage());
                results.add(result);
            }

            // Cooldown between books (skip after last book)
            if (bookNumber < TOP_20_BOOKS.size() && cooldownMinutes > 0) {
                int totalImages = result.newIllustrations() + result.newPortraits();
                if (totalImages < cooldownSkipUnderImages) {
                    log.info("");
                    log.info("Skipping cooldown ({} new images < threshold {} for '{}')",
                            totalImages, cooldownSkipUnderImages, book.title());
                    continue;
                }
                log.info("");
                log.info("Cooling down for {} minutes to prevent overheating...", cooldownMinutes);
                try {
                    Thread.sleep(cooldownMinutes * 60 * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Cooldown interrupted, continuing...");
                }
            }
        }

        // Print summary
        log.info("");
        log.info("========================================");
        log.info("BATCH COMPLETE - SUMMARY");
        log.info("========================================");

        int successful = 0;
        int failed = 0;
        int totalIllustrations = 0;
        int totalPortraits = 0;

        for (int i = 0; i < results.size(); i++) {
            PreGenResult result = results.get(i);
            BookEntry book = TOP_20_BOOKS.get(i);
            String status = result.success() ? "OK" : "FAILED";
            log.info("[{}] {} - {} illustrations, {} portraits",
                    status, book.title(),
                    result.illustrationsCompleted(), result.portraitsCompleted());

            if (result.success()) {
                successful++;
            } else {
                failed++;
            }
            totalIllustrations += result.illustrationsCompleted();
            totalPortraits += result.portraitsCompleted();
        }

        log.info("----------------------------------------");
        log.info("Books: {} successful, {} failed", successful, failed);
        log.info("Total illustrations generated: {}", totalIllustrations);
        log.info("Total portraits generated: {}", totalPortraits);
        log.info("========================================");
    }
}
