package org.example.reader.model;

import java.util.List;

public record Book(
    String id,
    String title,
    String author,
    String description,
    String coverUrl,
    List<Chapter> chapters
) {
    public record Chapter(String id, String title) {}
}
