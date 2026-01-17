package org.example.reader.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chapter_analyses")
public class ChapterAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false, unique = true)
    private ChapterEntity chapter;

    @Column(nullable = false)
    private LocalDateTime analyzedAt;

    @Column(nullable = false)
    private int characterCount;

    @Enumerated(EnumType.STRING)
    @Column
    private ChapterAnalysisStatus status;

    public ChapterAnalysisEntity() {}

    public ChapterAnalysisEntity(ChapterEntity chapter) {
        this.chapter = chapter;
        this.analyzedAt = LocalDateTime.now();
        this.characterCount = 0;
        this.status = ChapterAnalysisStatus.PENDING;
    }

    @PrePersist
    void ensureStatus() {
        if (status == null) {
            status = ChapterAnalysisStatus.PENDING;
        }
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ChapterEntity getChapter() { return chapter; }
    public void setChapter(ChapterEntity chapter) { this.chapter = chapter; }

    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }

    public int getCharacterCount() { return characterCount; }
    public void setCharacterCount(int characterCount) { this.characterCount = characterCount; }

    public ChapterAnalysisStatus getStatus() { return status; }
    public void setStatus(ChapterAnalysisStatus status) { this.status = status; }
}
