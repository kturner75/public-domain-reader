package org.example.reader.model;

public record CharacterInfo(
    String id,
    String name,
    String description,
    String firstChapterId,
    String firstChapterTitle,
    int firstChapterIndex,
    int firstParagraphIndex,
    String status,
    boolean portraitReady,
    String characterType
) {
    public static CharacterInfo from(org.example.reader.entity.CharacterEntity entity) {
        return new CharacterInfo(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getFirstChapter().getId(),
            entity.getFirstChapter().getTitle(),
            entity.getFirstChapter().getChapterIndex(),
            entity.getFirstParagraphIndex(),
            entity.getStatus().name(),
            entity.getStatus() == org.example.reader.entity.CharacterStatus.COMPLETED,
            entity.getCharacterType().name()
        );
    }
}
