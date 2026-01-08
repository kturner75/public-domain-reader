package org.example.reader.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "books")
public class BookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(length = 2000)
    private String description;

    private String coverUrl;

    @Column(nullable = false)
    private String source; // "gutenberg", "standardebooks", "manual"

    private String sourceId; // ID from the source (e.g., Gutenberg book number)

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("chapterIndex")
    private List<ChapterEntity> chapters = new ArrayList<>();

    public BookEntity() {}

    public BookEntity(String title, String author, String source) {
        this.title = title;
        this.author = author;
        this.source = source;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public List<ChapterEntity> getChapters() { return chapters; }
    public void setChapters(List<ChapterEntity> chapters) { this.chapters = chapters; }

    public void addChapter(ChapterEntity chapter) {
        chapters.add(chapter);
        chapter.setBook(this);
    }
}
