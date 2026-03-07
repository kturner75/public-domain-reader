package org.example.reader.service;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class CuratedCatalogService {

    /**
     * Shared curated catalog list used for:
     * - Landing-page discovery/search when catalog mode is "curated"
     * - Batch pre-generation source list
     *
     * Start with 20 pre-generated books and extend this list to 50 over time.
     */
    private static final List<CuratedCatalogBook> CURATED_BOOKS = List.of(
            new CuratedCatalogBook(1342, "Pride and Prejudice", "Jane Austen", 100_000, List.of("Courtship fiction"), List.of("Romance")),
            new CuratedCatalogBook(2701, "Moby Dick", "Herman Melville", 99_000, List.of("Whaling -- Fiction"), List.of("Adventure")),
            new CuratedCatalogBook(84, "Frankenstein", "Mary Shelley", 98_000, List.of("Science fiction"), List.of("Horror")),
            new CuratedCatalogBook(345, "Dracula", "Bram Stoker", 97_000, List.of("Vampires -- Fiction"), List.of("Gothic")),
            new CuratedCatalogBook(11, "Alice's Adventures in Wonderland", "Lewis Carroll", 96_000, List.of("Fantasy fiction"), List.of("Children")),
            new CuratedCatalogBook(1260, "Jane Eyre", "Charlotte Bronte", 95_000, List.of("Governesses -- Fiction"), List.of("Classics")),
            new CuratedCatalogBook(768, "Wuthering Heights", "Emily Bronte", 94_000, List.of("Love stories"), List.of("Classics")),
            new CuratedCatalogBook(174, "The Picture of Dorian Gray", "Oscar Wilde", 93_000, List.of("Psychological fiction"), List.of("Classics")),
            new CuratedCatalogBook(98, "A Tale of Two Cities", "Charles Dickens", 92_000, List.of("Historical fiction"), List.of("Classics")),
            new CuratedCatalogBook(1184, "The Count of Monte Cristo", "Alexandre Dumas", 91_000, List.of("Adventure stories"), List.of("Classics")),
            new CuratedCatalogBook(2554, "Crime and Punishment", "Fyodor Dostoyevsky", 90_000, List.of("Psychological fiction"), List.of("Classics")),
            new CuratedCatalogBook(1399, "Anna Karenina", "Leo Tolstoy", 89_000, List.of("Domestic fiction"), List.of("Classics")),
            new CuratedCatalogBook(1661, "The Adventures of Sherlock Holmes", "Arthur Conan Doyle", 88_000, List.of("Detective and mystery stories"), List.of("Detective Fiction")),
            new CuratedCatalogBook(1727, "The Odyssey", "Homer", 87_000, List.of("Epic poetry"), List.of("Poetry")),
            new CuratedCatalogBook(996, "Don Quixote", "Miguel de Cervantes", 86_000, List.of("Satire"), List.of("Classics")),
            new CuratedCatalogBook(135, "Les Miserables", "Victor Hugo", 85_000, List.of("Historical fiction"), List.of("Classics")),
            new CuratedCatalogBook(2600, "War and Peace", "Leo Tolstoy", 84_000, List.of("Historical fiction"), List.of("Classics")),
            new CuratedCatalogBook(28054, "The Brothers Karamazov", "Fyodor Dostoyevsky", 83_000, List.of("Brothers -- Fiction"), List.of("Classics")),
            new CuratedCatalogBook(120, "Treasure Island", "Robert Louis Stevenson", 82_000, List.of("Treasure troves -- Fiction"), List.of("Adventure")),
            new CuratedCatalogBook(25, "The Scarlet Letter", "Nathaniel Hawthorne", 81_000, List.of("Adultery -- Fiction"), List.of("Classics"))
    );

    private static final Comparator<CuratedCatalogBook> POPULARITY_ORDER =
            Comparator.comparingInt(CuratedCatalogBook::downloadCount).reversed()
                    .thenComparing(CuratedCatalogBook::title, String.CASE_INSENSITIVE_ORDER);

    public List<CuratedCatalogBook> getPopularBooks() {
        return CURATED_BOOKS;
    }

    public List<CuratedCatalogBook> search(String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return CURATED_BOOKS;
        }
        return CURATED_BOOKS.stream()
                .filter(book -> matches(book, normalized))
                .sorted(POPULARITY_ORDER)
                .toList();
    }

    private boolean matches(CuratedCatalogBook book, String normalizedQuery) {
        String normalizedTitle = normalize(book.title());
        String normalizedAuthor = normalize(book.author());
        return normalizedTitle.contains(normalizedQuery)
                || normalizedAuthor.contains(normalizedQuery)
                || (normalizedTitle + " " + normalizedAuthor).contains(normalizedQuery);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record CuratedCatalogBook(
            int gutenbergId,
            String title,
            String author,
            int downloadCount,
            List<String> subjects,
            List<String> bookshelves
    ) {}
}
