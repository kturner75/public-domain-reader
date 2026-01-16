package org.example.reader.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.reader.entity.*;
import org.example.reader.model.CharacterInfo;
import org.example.reader.model.IllustrationSettings;
import org.example.reader.repository.*;
import org.example.reader.service.CharacterExtractionService.ExtractedCharacter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

@Service
public class CharacterService {

    private static final Logger log = LoggerFactory.getLogger(CharacterService.class);

    private final CharacterRepository characterRepository;
    private final ChapterAnalysisRepository chapterAnalysisRepository;
    private final ChapterRepository chapterRepository;
    private final BookRepository bookRepository;
    private final ParagraphRepository paragraphRepository;
    private final CharacterExtractionService extractionService;
    private final CharacterPortraitService portraitService;
    private final IllustrationService illustrationService;
    private final ComfyUIService comfyUIService;

    private final BlockingQueue<CharacterRequest> requestQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    private CharacterService self;

    public CharacterService(
            CharacterRepository characterRepository,
            ChapterAnalysisRepository chapterAnalysisRepository,
            ChapterRepository chapterRepository,
            BookRepository bookRepository,
            ParagraphRepository paragraphRepository,
            CharacterExtractionService extractionService,
            CharacterPortraitService portraitService,
            IllustrationService illustrationService,
            ComfyUIService comfyUIService) {
        this.characterRepository = characterRepository;
        this.chapterAnalysisRepository = chapterAnalysisRepository;
        this.chapterRepository = chapterRepository;
        this.bookRepository = bookRepository;
        this.paragraphRepository = paragraphRepository;
        this.extractionService = extractionService;
        this.portraitService = portraitService;
        this.illustrationService = illustrationService;
        this.comfyUIService = comfyUIService;
    }

    @Autowired
    @Lazy
    public void setSelf(CharacterService self) {
        this.self = self;
    }

    @PostConstruct
    public void init() {
        executor.submit(this::processQueue);
        log.info("Character service started with background queue processor");
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        executor.shutdownNow();
        log.info("Character service shutting down");
    }

    public boolean isAvailable() {
        return extractionService.isOllamaAvailable() && comfyUIService.isAvailable();
    }

    @Transactional
    public void requestChapterAnalysis(String chapterId) {
        if (chapterAnalysisRepository.existsByChapterId(chapterId)) {
            log.debug("Chapter {} already analyzed for characters", chapterId);
            return;
        }

        ChapterEntity chapter = chapterRepository.findById(chapterId).orElse(null);
        if (chapter == null) {
            log.warn("Cannot analyze chapter: not found: {}", chapterId);
            return;
        }

        try {
            ChapterAnalysisEntity analysis = new ChapterAnalysisEntity(chapter);
            chapterAnalysisRepository.save(analysis);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    boolean queued = requestQueue.offer(new AnalysisRequest(chapterId));
                    if (queued) {
                        log.info("Queued character analysis for chapter: {}", chapterId);
                    } else {
                        log.error("Failed to queue character analysis for chapter: {}", chapterId);
                    }
                }
            });
        } catch (DataIntegrityViolationException e) {
            log.debug("Chapter {} already being analyzed (race condition handled)", chapterId);
        }
    }

    public void prefetchNextChapter(String currentChapterId) {
        ChapterEntity current = chapterRepository.findById(currentChapterId).orElse(null);
        if (current == null) return;

        chapterRepository.findByBookIdAndChapterIndex(
                current.getBook().getId(),
                current.getChapterIndex() + 1
        ).ifPresent(next -> {
            log.debug("Pre-fetching character analysis for next chapter: {}", next.getTitle());
            self.requestChapterAnalysis(next.getId());
        });
    }

    public List<CharacterInfo> getCharactersForBook(String bookId) {
        return characterRepository.findByBookIdOrderByCreatedAt(bookId).stream()
                .map(CharacterInfo::from)
                .collect(Collectors.toList());
    }

    public List<CharacterInfo> getCharactersUpToPosition(String bookId, int chapterIndex, int paragraphIndex) {
        return characterRepository.findByBookIdUpToPosition(bookId, chapterIndex, paragraphIndex).stream()
                .map(CharacterInfo::from)
                .collect(Collectors.toList());
    }

    public List<CharacterInfo> getNewlyCompletedSince(String bookId, LocalDateTime sinceTime) {
        return characterRepository.findNewlyCompletedSince(bookId, sinceTime).stream()
                .map(CharacterInfo::from)
                .collect(Collectors.toList());
    }

    public Optional<CharacterEntity> getCharacter(String characterId) {
        return characterRepository.findById(characterId);
    }

    public CharacterStatus getPortraitStatus(String characterId) {
        return characterRepository.findById(characterId)
                .map(CharacterEntity::getStatus)
                .orElse(null);
    }

    public byte[] getPortrait(String characterId) {
        return characterRepository.findById(characterId)
                .filter(c -> c.getStatus() == CharacterStatus.COMPLETED)
                .map(c -> comfyUIService.getPortraitImage(c.getPortraitFilename()))
                .orElse(null);
    }

    private void processQueue() {
        log.info("Character queue processor thread started");
        int processedCount = 0;
        while (running) {
            try {
                log.debug("Waiting for character request in queue...");
                CharacterRequest request = requestQueue.take();
                processedCount++;

                if (request instanceof AnalysisRequest ar) {
                    log.info("Processing character analysis #{} for chapter: {}", processedCount, ar.chapterId());
                    processChapterAnalysis(ar.chapterId());
                } else if (request instanceof PortraitRequest pr) {
                    log.info("Processing portrait generation #{} for character: {}", processedCount, pr.characterId());
                    generatePortrait(pr.characterId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Character queue processor thread interrupted");
                break;
            } catch (Exception e) {
                log.error("Error processing character queue", e);
            }
        }
        log.info("Character queue processor thread stopped after processing {} requests", processedCount);
    }

    private void processChapterAnalysis(String chapterId) {
        ChapterEntity chapter = chapterRepository.findByIdWithBook(chapterId).orElse(null);
        if (chapter == null) {
            log.warn("Chapter not found for analysis: {}", chapterId);
            return;
        }

        BookEntity book = chapter.getBook();

        List<String> existingNames = characterRepository.findByBookIdOrderByCreatedAt(book.getId())
                .stream()
                .map(CharacterEntity::getName)
                .collect(Collectors.toList());

        String chapterContent = getChapterText(chapterId);

        List<ExtractedCharacter> extracted = extractionService.extractCharactersFromChapter(
                book.getTitle(),
                book.getAuthor(),
                chapter.getTitle(),
                chapterContent,
                existingNames
        );

        for (ExtractedCharacter ec : extracted) {
            try {
                CharacterEntity character = self.createCharacter(
                        book, chapter, ec.name(), ec.description(), ec.approximateParagraphIndex()
                );
                if (character != null) {
                    requestQueue.offer(new PortraitRequest(character.getId()));
                    log.info("Created character '{}' and queued portrait generation", ec.name());
                }
            } catch (Exception e) {
                log.error("Failed to create character '{}'", ec.name(), e);
            }
        }

        self.updateChapterAnalysisCount(chapterId, extracted.size());
    }

    @Transactional
    public CharacterEntity createCharacter(BookEntity book, ChapterEntity chapter,
                                           String name, String description, int paragraphIndex) {
        Optional<CharacterEntity> existing = characterRepository.findByBookIdAndNameIgnoreCase(book.getId(), name);
        if (existing.isPresent()) {
            log.debug("Character '{}' already exists for book '{}'", name, book.getTitle());
            return null;
        }

        CharacterEntity character = new CharacterEntity(book, name, description, chapter, paragraphIndex);
        return characterRepository.save(character);
    }

    /**
     * Queue portrait generation for a character. Used by CharacterPrefetchService.
     */
    public void queuePortraitGeneration(String characterId) {
        boolean queued = requestQueue.offer(new PortraitRequest(characterId));
        if (queued) {
            log.debug("Queued portrait generation for character: {}", characterId);
        } else {
            log.error("Failed to queue portrait generation for character: {}", characterId);
        }
    }

    @Transactional
    public void updateChapterAnalysisCount(String chapterId, int count) {
        chapterAnalysisRepository.findByChapterId(chapterId).ifPresent(analysis -> {
            analysis.setCharacterCount(count);
            analysis.setAnalyzedAt(LocalDateTime.now());
            chapterAnalysisRepository.save(analysis);
        });
    }

    private void generatePortrait(String characterId) {
        CharacterEntity character = characterRepository.findByIdWithBookAndChapter(characterId).orElse(null);
        if (character == null) {
            log.warn("Character not found for portrait generation: {}", characterId);
            return;
        }

        if (character.getStatus() == CharacterStatus.COMPLETED ||
                character.getStatus() == CharacterStatus.GENERATING) {
            log.debug("Skipping portrait for character {} - already {}", characterId, character.getStatus());
            return;
        }

        self.updateCharacterStatus(characterId, CharacterStatus.GENERATING, null, null);

        try {
            BookEntity book = character.getBook();
            IllustrationSettings bookStyle = illustrationService.getOrAnalyzeBookStyle(book.getId(), false);

            String portraitPrompt = portraitService.generatePortraitPrompt(
                    book.getTitle(),
                    book.getAuthor(),
                    character.getName(),
                    character.getDescription(),
                    bookStyle
            );

            self.updatePortraitPrompt(characterId, portraitPrompt);

            String outputPrefix = "portrait_" + characterId;
            String promptId = comfyUIService.submitPortraitWorkflow(portraitPrompt, outputPrefix);

            ComfyUIService.IllustrationResult result = comfyUIService.pollForPortraitCompletion(promptId);

            if (result.success()) {
                self.updateCharacterStatus(characterId, CharacterStatus.COMPLETED, result.filename(), null);
                log.info("Portrait completed for character: {}", character.getName());
            } else {
                self.updateCharacterStatus(characterId, CharacterStatus.FAILED, null, result.errorMessage());
                log.error("Portrait generation failed for '{}': {}", character.getName(), result.errorMessage());
            }

        } catch (Exception e) {
            log.error("Failed to generate portrait for character: {}", character.getName(), e);
            self.updateCharacterStatus(characterId, CharacterStatus.FAILED, null, e.getMessage());
        }
    }

    @Transactional
    public void updateCharacterStatus(String characterId, CharacterStatus status,
                                      String filename, String errorMessage) {
        CharacterEntity character = characterRepository.findById(characterId).orElse(null);
        if (character == null) {
            log.warn("Cannot update status: character not found {}", characterId);
            return;
        }
        character.setStatus(status);
        if (filename != null) {
            character.setPortraitFilename(filename);
        }
        if (errorMessage != null) {
            character.setErrorMessage(errorMessage);
        }
        if (status == CharacterStatus.COMPLETED) {
            character.setCompletedAt(LocalDateTime.now());
        }
        characterRepository.save(character);
        log.debug("Updated character status for {}: {}", characterId, status);
    }

    @Transactional
    public void updatePortraitPrompt(String characterId, String prompt) {
        CharacterEntity character = characterRepository.findById(characterId).orElse(null);
        if (character == null) return;
        character.setPortraitPrompt(prompt);
        characterRepository.save(character);
    }

    private String getChapterText(String chapterId) {
        List<ParagraphEntity> paragraphs = paragraphRepository
                .findByChapterIdOrderByParagraphIndex(chapterId);

        return paragraphs.stream()
                .map(ParagraphEntity::getContent)
                .map(this::stripHtml)
                .collect(Collectors.joining("\n\n"));
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * Delete all characters for a book and clean up portrait files.
     * Also clears chapter analysis records so they can be re-analyzed.
     *
     * @param bookId the book to clear characters for
     * @return number of characters deleted
     */
    @Transactional
    public int deleteCharactersForBook(String bookId) {
        List<CharacterEntity> characters = characterRepository.findByBookIdOrderByCreatedAt(bookId);

        // Delete portrait files first
        int deletedFiles = 0;
        for (CharacterEntity character : characters) {
            if (character.getPortraitFilename() != null) {
                if (comfyUIService.deletePortraitFile(character.getPortraitFilename())) {
                    deletedFiles++;
                }
            }
        }
        log.info("Deleted {} portrait files for book {}", deletedFiles, bookId);

        // Delete chapter analyses so chapters can be re-analyzed
        chapterAnalysisRepository.deleteByBookId(bookId);
        log.info("Deleted chapter analyses for book {}", bookId);

        // Delete characters from database
        int characterCount = characters.size();
        characterRepository.deleteByBookId(bookId);
        log.info("Deleted {} characters for book {}", characterCount, bookId);

        return characterCount;
    }

    private sealed interface CharacterRequest permits AnalysisRequest, PortraitRequest {
    }

    private record AnalysisRequest(String chapterId) implements CharacterRequest {
    }

    private record PortraitRequest(String characterId) implements CharacterRequest {
    }
}
