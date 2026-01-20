package org.example.reader.controller;

import org.example.reader.entity.BookEntity;
import org.example.reader.entity.CharacterEntity;
import org.example.reader.entity.CharacterStatus;
import org.example.reader.entity.CharacterType;
import org.example.reader.model.CharacterInfo;
import org.example.reader.model.ChatMessage;
import org.example.reader.repository.BookRepository;
import org.example.reader.repository.ChapterRepository;
import org.example.reader.service.CharacterChatService;
import org.example.reader.service.CharacterExtractionService;
import org.example.reader.service.CharacterPrefetchService;
import org.example.reader.service.CharacterService;
import org.example.reader.service.ComfyUIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private static final Logger log = LoggerFactory.getLogger(CharacterController.class);

    @Value("${character.enabled:true}")
    private boolean characterEnabled;

    @Value("${generation.cache-only:false}")
    private boolean cacheOnly;

    private final CharacterService characterService;
    private final CharacterChatService chatService;
    private final CharacterExtractionService extractionService;
    private final CharacterPrefetchService prefetchService;
    private final ComfyUIService comfyUIService;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;

    public CharacterController(
            CharacterService characterService,
            CharacterChatService chatService,
            CharacterExtractionService extractionService,
            CharacterPrefetchService prefetchService,
            ComfyUIService comfyUIService,
            BookRepository bookRepository,
            ChapterRepository chapterRepository) {
        this.characterService = characterService;
        this.chatService = chatService;
        this.extractionService = extractionService;
        this.prefetchService = prefetchService;
        this.comfyUIService = comfyUIService;
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", characterEnabled);
        status.put("ollamaAvailable", extractionService.isOllamaAvailable());
        status.put("comfyuiAvailable", comfyUIService.isAvailable());
        status.put("available", characterEnabled && characterService.isAvailable());
        status.put("cacheOnly", cacheOnly);
        return status;
    }

    @GetMapping("/book/{bookId}")
    public List<CharacterInfo> getCharactersForBook(@PathVariable String bookId) {
        requireCharacterEnabledBook(bookId);
        return characterService.getCharactersForBook(bookId);
    }

    @GetMapping("/book/{bookId}/up-to")
    public List<CharacterInfo> getCharactersUpToPosition(
            @PathVariable String bookId,
            @RequestParam int chapterIndex,
            @RequestParam int paragraphIndex) {
        requireCharacterEnabledBook(bookId);
        return characterService.getCharactersUpToPosition(bookId, chapterIndex, paragraphIndex);
    }

    @GetMapping("/book/{bookId}/new-since")
    public List<CharacterInfo> getNewCharactersSince(
            @PathVariable String bookId,
            @RequestParam long sinceTimestamp) {
        requireCharacterEnabledBook(bookId);
        LocalDateTime sinceTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(sinceTimestamp),
                ZoneId.systemDefault()
        );
        return characterService.getNewlyCompletedSince(bookId, sinceTime);
    }

    @DeleteMapping("/book/{bookId}")
    public ResponseEntity<Map<String, Object>> deleteCharactersForBook(@PathVariable String bookId) {
        if (!characterEnabled) {
            return ResponseEntity.status(403).build();
        }
        requireCharacterEnabledBook(bookId);

        log.info("Deleting all characters for book: {}", bookId);
        int deletedCount = characterService.deleteCharactersForBook(bookId);

        Map<String, Object> response = new HashMap<>();
        response.put("bookId", bookId);
        response.put("deletedCount", deletedCount);
        response.put("message", "Characters deleted. Re-open the book to regenerate.");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindexPrimaryCharacters() {
        if (!characterEnabled) {
            return ResponseEntity.status(403).build();
        }

        int updatedCount = prefetchService.refreshPrimaryCharacterPositionsForAll();
        Map<String, Object> response = new HashMap<>();
        response.put("updatedCount", updatedCount);
        response.put("message", "Primary character first appearances refreshed.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/book/{bookId}/reindex")
    public ResponseEntity<Map<String, Object>> reindexPrimaryCharactersForBook(@PathVariable String bookId) {
        if (!characterEnabled) {
            return ResponseEntity.status(403).build();
        }
        requireCharacterEnabledBook(bookId);

        int updatedCount = prefetchService.refreshPrimaryCharacterPositionsForBook(bookId);
        Map<String, Object> response = new HashMap<>();
        response.put("bookId", bookId);
        response.put("updatedCount", updatedCount);
        response.put("message", "Primary character first appearances refreshed for book.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{characterId}")
    public ResponseEntity<CharacterInfo> getCharacter(@PathVariable String characterId) {
        Optional<CharacterEntity> characterOpt = characterService.getCharacter(characterId);
        if (characterOpt.isPresent() && !isCharacterEnabled(characterOpt.get().getBook())) {
            return ResponseEntity.status(403).build();
        }
        return characterOpt
                .map(c -> ResponseEntity.ok(CharacterInfo.from(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{characterId}/portrait")
    public ResponseEntity<byte[]> getPortrait(@PathVariable String characterId) {
        Optional<CharacterEntity> characterOpt = characterService.getCharacter(characterId);
        if (characterOpt.isPresent() && !isCharacterEnabled(characterOpt.get().getBook())) {
            return ResponseEntity.status(403).build();
        }
        byte[] image = characterOpt.map(c -> characterService.getPortrait(c.getId())).orElse(null);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/png")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=604800")
                .body(image);
    }

    @GetMapping("/{characterId}/portrait/status")
    public Map<String, Object> getPortraitStatus(@PathVariable String characterId) {
        Optional<CharacterEntity> characterOpt = characterService.getCharacter(characterId);
        if (characterOpt.isPresent() && !isCharacterEnabled(characterOpt.get().getBook())) {
            return Map.of(
                    "characterId", characterId,
                    "status", "DISABLED",
                    "ready", false
            );
        }
        CharacterStatus status = characterOpt
                .map(character -> characterService.getPortraitStatus(character.getId()))
                .orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("characterId", characterId);
        response.put("status", status != null ? status.name() : "NOT_FOUND");
        response.put("ready", status == CharacterStatus.COMPLETED);

        return response;
    }

    @PostMapping("/chapter/{chapterId}/analyze")
    public ResponseEntity<Void> requestChapterAnalysis(@PathVariable String chapterId) {
        if (!characterEnabled) {
            return ResponseEntity.status(403).build();
        }
        if (cacheOnly) {
            return ResponseEntity.status(409).build();
        }
        requireCharacterEnabledChapter(chapterId);
        characterService.requestChapterAnalysis(chapterId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/chapter/{chapterId}/prefetch-next")
    public ResponseEntity<Void> prefetchNextChapter(@PathVariable String chapterId) {
        if (!characterEnabled) {
            return ResponseEntity.status(403).build();
        }
        if (cacheOnly) {
            return ResponseEntity.status(409).build();
        }
        requireCharacterEnabledChapter(chapterId);
        characterService.prefetchNextChapter(chapterId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/book/{bookId}/prefetch")
    public ResponseEntity<Void> prefetchBookCharacters(@PathVariable String bookId) {
        if (!characterEnabled) {
            return ResponseEntity.status(403).build();
        }
        if (cacheOnly) {
            return ResponseEntity.status(409).build();
        }
        requireCharacterEnabledBook(bookId);
        // Run asynchronously to not block book opening
        java.util.concurrent.CompletableFuture.runAsync(() ->
            prefetchService.prefetchCharactersForBook(bookId)
        );
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{characterId}/chat")
    public ResponseEntity<ChatResponse> chat(
            @PathVariable String characterId,
            @RequestBody ChatRequest request) {

        if (!characterEnabled) {
            return ResponseEntity.status(403).build();
        }
        if (cacheOnly) {
            return ResponseEntity.status(409).build();
        }

        // Check character type - only PRIMARY characters can chat
        Optional<CharacterEntity> characterOpt = characterService.getCharacter(characterId);
        if (characterOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CharacterEntity character = characterOpt.get();
        if (!isCharacterEnabled(character.getBook())) {
            return ResponseEntity.status(403).build();
        }
        if (character.getCharacterType() != CharacterType.PRIMARY) {
            return ResponseEntity.ok(new ChatResponse(
                    "Chat is only available for main characters.",
                    characterId,
                    System.currentTimeMillis()
            ));
        }

        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String response = chatService.chat(
                characterId,
                request.message(),
                request.conversationHistory(),
                request.readerChapterIndex(),
                request.readerParagraphIndex()
        );

        return ResponseEntity.ok(new ChatResponse(
                response,
                characterId,
                System.currentTimeMillis()
        ));
    }

    public record ChatRequest(
            String message,
            List<ChatMessage> conversationHistory,
            int readerChapterIndex,
            int readerParagraphIndex
    ) {}

    public record ChatResponse(
            String response,
            String characterId,
            long timestamp
    ) {}

    private void requireCharacterEnabledBook(String bookId) {
        if (!characterEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Character feature disabled");
        }
        BookEntity book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
        if (!isCharacterEnabled(book)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Character mode disabled for book");
        }
    }

    private void requireCharacterEnabledChapter(String chapterId) {
        if (!characterEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Character feature disabled");
        }
        BookEntity book = chapterRepository.findById(chapterId)
                .map(chapter -> chapter.getBook())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapter not found"));
        if (!isCharacterEnabled(book)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Character mode disabled for book");
        }
    }

    private boolean isCharacterEnabled(BookEntity book) {
        return characterEnabled && Boolean.TRUE.equals(book.getCharacterEnabled());
    }
}
