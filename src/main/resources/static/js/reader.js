(function() {
    'use strict';

    // State
    const state = {
        catalogBooks: [],      // Books from Gutenberg catalog
        localBooks: [],        // Books imported locally
        currentBook: null,
        currentChapterIndex: 0,
        chapters: [],
        paragraphs: [],
        currentPage: 0,
        totalPages: 0,
        currentParagraphIndex: 0,
        pagesData: [],         // Array of { startParagraph, endParagraph } for each page
        isImporting: false,
        ttsEnabled: false,
        ttsAudio: null,
        ttsWaitingForChapter: false,
        ttsVoiceSettings: null,  // { voice, speed, instructions, reasoning }
        ttsAvailable: false,
        ttsOpenAIAvailable: false,
        ttsOpenAIConfigured: false,
        ttsCachedAvailable: false,
        ttsBrowserAvailable: false,
        ttsUsingBrowser: false,  // true when currently using browser fallback
        ttsPlaybackRate: 1.0,  // 1.0, 1.25, 1.5, 1.75, 2.0
        ttsPrefetchedAudio: null,      // Pre-fetched Audio object for next paragraph
        ttsPrefetchedIndex: -1,        // Paragraph index of pre-fetched audio
        ttsPrefetchedChapter: null,    // Chapter ID of pre-fetched audio
        ttsAbortController: null,      // AbortController for current audio request
        ttsPrefetchAbortController: null,  // AbortController for prefetch request
        // Speed reading state
        speedReadingEnabled: true,
        speedReadingRestoreIllustration: false,
        speedReadingActive: false,
        speedReadingPlaying: false,
        speedReadingWpm: 300,
        speedReadingTimer: null,
        speedReadingTokens: [],
        speedReadingTokenIndex: 0,
        // Illustration mode state
        illustrationMode: false,
        illustrationAvailable: false,
        illustrationCacheOnly: false,
        illustrationSettings: null,  // { style, promptPrefix, reasoning }
        illustrationPolling: null,   // polling interval ID
        allowPromptEditing: false,   // whether prompt editing is enabled
        // Modal/overlay state
        ttsWasPlayingBeforeModal: false,  // track TTS state when modal opens
        // Prompt modal state
        promptModalChapterId: null,       // chapter being edited in modal
        promptModalPolling: null,         // polling interval for regeneration
        promptModalLastPrompt: '',        // last prompt used (for try again)
        // Character feature state
        characterAvailable: false,
        characterCacheOnly: false,
        characterChatAvailable: false,
        recapAvailable: false,
        recapGenerationAvailable: false,
        recapCacheOnly: false,
        recapChatAvailable: false,
        recapOptOut: false,
        recapPendingChapterIndex: null,
        recapActiveTab: 'recap',
        recapChatChapterIndex: null,
        recapChatHistory: [],
        recapChatLoading: false,
        recapPollingInterval: null,
        recapPollingChapterId: null,
        recapPollingInFlight: false,
        quizAvailable: false,
        quizGenerationAvailable: false,
        quizCacheOnly: false,
        quizChapterId: null,
        quizQuestions: [],
        quizSelectedAnswers: [],
        quizSubmitting: false,
        quizResult: null,
        quizDifficultyLevel: 0,
        characters: [],                   // Characters met so far
        characterLastCheck: 0,            // Timestamp of last new character check
        characterPollingInterval: null,   // Interval for checking new characters
        newCharacterQueue: [],            // Queue of characters to show toasts for
        discoveredCharacterIds: new Set(), // Per-book discovery tracking
        discoveredCharacterDetails: new Map(),
        characterDiscoveryTimeout: null,
        currentToastCharacter: null,      // Character currently showing in toast
        // Character browser modal state
        characterBrowserOpen: false,
        selectedCharacterId: null,
        // Character chat modal state
        characterChatOpen: false,
        chatCharacterId: null,
        chatCharacter: null,              // Current character being chatted with
        chatHistory: [],                  // Loaded from localStorage
        chatLoading: false,
        cacheOnly: false
    };

    // DOM Elements
    const elements = {
        libraryView: document.getElementById('library-view'),
        readerView: document.getElementById('reader-view'),
        librarySearch: document.getElementById('library-search'),
        recentlyRead: document.getElementById('recently-read'),
        recentlyReadList: document.getElementById('recently-read-list'),
        allBooks: document.getElementById('all-books'),
        bookList: document.getElementById('book-list'),
        noResults: document.getElementById('no-results'),
        bookTitle: document.getElementById('book-title'),
        bookAuthor: document.getElementById('book-author'),
        chapterTitle: document.getElementById('chapter-title'),
        columnLeft: document.getElementById('column-left'),
        columnRight: document.getElementById('column-right'),
        readerContent: document.querySelector('.reader-content'),
        gutterLeft: document.getElementById('gutter-left'),
        gutterRight: document.getElementById('gutter-right'),
        pageIndicator: document.getElementById('page-indicator'),
        backToLibrary: document.getElementById('back-to-library'),
        searchInput: document.getElementById('search-input'),
        searchResults: document.getElementById('search-results'),
        cacheOnlyIndicator: document.getElementById('cache-only-indicator'),
        chapterListOverlay: document.getElementById('chapter-list-overlay'),
        chapterList: document.getElementById('chapter-list'),
        ttsToggle: document.getElementById('tts-toggle'),
        ttsSpeed: document.getElementById('tts-speed'),
        ttsMode: document.getElementById('tts-mode'),
        ttsHint: document.getElementById('tts-hint'),
        ttsSpeedHint: document.getElementById('tts-speed-hint'),
        speedReadingContainer: document.getElementById('speed-reading-container'),
        speedReadingToggle: document.getElementById('speed-reading-toggle'),
        speedReadingOverlay: document.getElementById('speed-reading-overlay'),
        speedReadingWord: document.getElementById('speed-reading-word'),
        speedReadingPlay: document.getElementById('speed-reading-play'),
        speedReadingExitInline: document.getElementById('speed-reading-exit-inline'),
        speedReadingSlider: document.getElementById('speed-reading-slider'),
        speedReadingWpm: document.getElementById('speed-reading-wpm'),
        speedReadingChapterOverlay: document.getElementById('speed-reading-chapter-overlay'),
        speedReadingChapterTitle: document.getElementById('speed-reading-chapter-title'),
        speedReadingContinue: document.getElementById('speed-reading-continue'),
        speedReadingExit: document.getElementById('speed-reading-exit'),
        speedReadingHint: document.getElementById('speed-reading-hint'),
        chapterRecapOverlay: document.getElementById('chapter-recap-overlay'),
        chapterRecapBackdrop: document.getElementById('chapter-recap-backdrop'),
        chapterRecapClose: document.getElementById('chapter-recap-close'),
        chapterRecapChapterTitle: document.getElementById('chapter-recap-chapter-title'),
        chapterRecapTabRecap: document.getElementById('chapter-recap-tab-recap'),
        chapterRecapTabChat: document.getElementById('chapter-recap-tab-chat'),
        chapterRecapTabQuiz: document.getElementById('chapter-recap-tab-quiz'),
        chapterRecapPanelRecap: document.getElementById('chapter-recap-panel-recap'),
        chapterRecapPanelChat: document.getElementById('chapter-recap-panel-chat'),
        chapterRecapPanelQuiz: document.getElementById('chapter-recap-panel-quiz'),
        chapterRecapStatus: document.getElementById('chapter-recap-status'),
        chapterRecapSummary: document.getElementById('chapter-recap-summary'),
        chapterRecapEvents: document.getElementById('chapter-recap-events'),
        chapterRecapCharacters: document.getElementById('chapter-recap-characters'),
        chapterRecapChatStatus: document.getElementById('chapter-recap-chat-status'),
        chapterRecapChatMessages: document.getElementById('chapter-recap-chat-messages'),
        chapterRecapChatInput: document.getElementById('chapter-recap-chat-input'),
        chapterRecapChatSend: document.getElementById('chapter-recap-chat-send'),
        chapterQuizStatus: document.getElementById('chapter-quiz-status'),
        chapterQuizQuestions: document.getElementById('chapter-quiz-questions'),
        chapterQuizSubmit: document.getElementById('chapter-quiz-submit'),
        chapterQuizFeedback: document.getElementById('chapter-quiz-feedback'),
        chapterRecapOptout: document.getElementById('chapter-recap-optout'),
        chapterRecapSkip: document.getElementById('chapter-recap-skip'),
        chapterRecapContinue: document.getElementById('chapter-recap-continue'),
        // Illustration elements
        illustrationToggle: document.getElementById('illustration-toggle'),
        illustrationColumn: document.getElementById('illustration-column'),
        illustrationSkeleton: document.getElementById('illustration-skeleton'),
        illustrationImage: document.getElementById('illustration-image'),
        illustrationError: document.getElementById('illustration-error'),
        illustrationHint: document.getElementById('illustration-hint'),
        // Prompt editing modal elements
        promptModal: document.getElementById('prompt-modal'),
        promptModalBackdrop: document.querySelector('.prompt-modal-backdrop'),
        promptModalClose: document.getElementById('prompt-modal-close'),
        promptModalTitle: document.getElementById('prompt-modal-title'),
        promptTextarea: document.getElementById('prompt-textarea'),
        promptEditMode: document.getElementById('prompt-edit-mode'),
        promptGeneratingMode: document.getElementById('prompt-generating-mode'),
        promptPreviewMode: document.getElementById('prompt-preview-mode'),
        promptPreviewImage: document.getElementById('prompt-preview-image'),
        promptEditButtons: document.getElementById('prompt-edit-buttons'),
        promptPreviewButtons: document.getElementById('prompt-preview-buttons'),
        promptCancel: document.getElementById('prompt-cancel'),
        promptRegenerate: document.getElementById('prompt-regenerate'),
        promptTryAgain: document.getElementById('prompt-try-again'),
        promptAccept: document.getElementById('prompt-accept'),
        // Character elements
        characterToggle: document.getElementById('character-toggle'),
        characterHint: document.getElementById('character-hint'),
        recapEnableBtn: document.getElementById('recap-enable-btn'),
        characterToast: document.getElementById('character-toast'),
        characterToastImage: document.getElementById('character-toast-image'),
        characterToastName: document.getElementById('character-toast-name'),
        characterToastDesc: document.getElementById('character-toast-desc'),
        characterToastClose: document.getElementById('character-toast-close'),
        characterBrowserModal: document.getElementById('character-browser-modal'),
        characterBrowserClose: document.getElementById('character-browser-close'),
        characterListView: document.getElementById('character-list-view'),
        characterListEmpty: document.getElementById('character-list-empty'),
        characterList: document.getElementById('character-list'),
        characterDetailView: document.getElementById('character-detail-view'),
        characterBackBtn: document.getElementById('character-back-btn'),
        characterDetailPortrait: document.getElementById('character-detail-portrait'),
        characterDetailName: document.getElementById('character-detail-name'),
        characterDetailDesc: document.getElementById('character-detail-desc'),
        characterDetailLink: document.getElementById('character-detail-link'),
        characterDetailChapter: document.getElementById('character-detail-chapter'),
        characterChatBtn: document.getElementById('character-chat-btn'),
        characterChatModal: document.getElementById('character-chat-modal'),
        characterChatClose: document.getElementById('character-chat-close'),
        chatCharacterPortrait: document.getElementById('chat-character-portrait'),
        chatCharacterName: document.getElementById('chat-character-name'),
        chatMessages: document.getElementById('chat-messages'),
        chatInput: document.getElementById('chat-input'),
        chatSendBtn: document.getElementById('chat-send-btn')
    };

    // Chapter list state
    let chapterListSelectedIndex = 0;

    // LocalStorage keys
    const STORAGE_KEYS = {
        LAST_BOOK: 'reader_lastBook',
        LAST_CHAPTER: 'reader_lastChapter',
        LAST_PAGE: 'reader_lastPage',
        LAST_PARAGRAPH: 'reader_lastParagraph',
        RECENTLY_READ: 'reader_recentlyRead',
        TTS_SPEED: 'reader_ttsSpeed',
        SPEED_READING_WPM: 'reader_speedReadingWpm',
        ILLUSTRATION_MODE: 'reader_illustrationMode',
        RECAP_OPTOUT_PREFIX: 'reader_recapOptOut_',
        RECAP_CHAT_PREFIX: 'reader_recapChat_',
        CHARACTER_CHAT_PREFIX: 'reader_characterChat_',
        DISCOVERED_CHARACTERS_PREFIX: 'reader_discoveredCharacters_',
        DISCOVERED_CHARACTER_DETAILS_PREFIX: 'reader_discoveredCharacterDetails_'
    };

    const MAX_RECENTLY_READ = 5;
    function isBookFeatureEnabled(flag) {
        return !!(state.currentBook && state.currentBook[flag] === true);
    }

    function updateCacheOnlyIndicator() {
        if (!elements.cacheOnlyIndicator) return;
        elements.cacheOnlyIndicator.classList.toggle('hidden', !state.cacheOnly);
    }

    // Initialize
    async function init() {
        await loadLibrary();
        await speedReadingCheckAvailability();
        setupEventListeners();
        await ttsCheckAvailability();
        await illustrationCheckAvailability();
        await characterCheckAvailability();
        await recapCheckAvailability();
        await quizCheckAvailability();

        // Load saved TTS speed preference
        const savedSpeed = parseFloat(localStorage.getItem(STORAGE_KEYS.TTS_SPEED));
        if (savedSpeed && [1.0, 1.25, 1.5, 1.75, 2.0].includes(savedSpeed)) {
            state.ttsPlaybackRate = savedSpeed;
        }

        // Load saved speed reading preference
        const savedWpm = parseInt(localStorage.getItem(STORAGE_KEYS.SPEED_READING_WPM), 10);
        if (!Number.isNaN(savedWpm) && savedWpm >= 150 && savedWpm <= 800) {
            state.speedReadingWpm = savedWpm;
        }

        // Load saved illustration mode preference
        const savedIllustrationMode = localStorage.getItem(STORAGE_KEYS.ILLUSTRATION_MODE);
        if (savedIllustrationMode === 'true' && state.illustrationAvailable) {
            state.illustrationMode = true;
            if (elements.illustrationToggle) {
                elements.illustrationToggle.classList.add('active');
            }
        }

        updateSpeedReadingControls();

        // Check for saved book
        const lastBookId = localStorage.getItem(STORAGE_KEYS.LAST_BOOK);
        if (lastBookId) {
            const book = state.localBooks.find(b => b.id === lastBookId);
            if (book) {
                const lastChapter = parseInt(localStorage.getItem(STORAGE_KEYS.LAST_CHAPTER)) || 0;
                const lastPage = parseInt(localStorage.getItem(STORAGE_KEYS.LAST_PAGE)) || 0;
                const lastParagraph = parseInt(localStorage.getItem(STORAGE_KEYS.LAST_PARAGRAPH)) || 0;
                await selectBook(book, lastChapter, lastPage, lastParagraph);
            }
        }
    }

    // Load library - both local books and popular from catalog
    async function loadLibrary() {
        try {
            // Load local books (for recently read)
            const localResponse = await fetch('/api/library');
            state.localBooks = await localResponse.json();

            // Load popular books from Gutenberg
            const catalogResponse = await fetch('/api/import/popular');
            state.catalogBooks = await catalogResponse.json();

            renderLibrary();
        } catch (error) {
            console.error('Failed to load library:', error);
        }
    }

    async function speedReadingCheckAvailability() {
        try {
            const response = await fetch('/api/features');
            if (response.ok) {
                const features = await response.json();
                state.speedReadingEnabled = features.speedReadingEnabled !== false;
            }
        } catch (error) {
            console.warn('Speed reading feature check failed:', error);
        }

        applySpeedReadingAvailability();
    }

    function applySpeedReadingAvailability() {
        if (state.speedReadingEnabled) {
            if (elements.speedReadingContainer) {
                elements.speedReadingContainer.style.display = '';
            }
            if (elements.speedReadingHint) {
                elements.speedReadingHint.style.display = '';
            }
            if (elements.speedReadingOverlay) {
                elements.speedReadingOverlay.style.display = '';
            }
            if (elements.speedReadingChapterOverlay) {
                elements.speedReadingChapterOverlay.style.display = '';
            }
            return;
        }

        exitSpeedReading(true);
        if (elements.speedReadingContainer) {
            elements.speedReadingContainer.style.display = 'none';
        }
        if (elements.speedReadingHint) {
            elements.speedReadingHint.style.display = 'none';
        }
        if (elements.speedReadingOverlay) {
            elements.speedReadingOverlay.classList.add('hidden');
            elements.speedReadingOverlay.style.display = 'none';
        }
        if (elements.speedReadingChapterOverlay) {
            elements.speedReadingChapterOverlay.classList.add('hidden');
            elements.speedReadingChapterOverlay.style.display = 'none';
        }
    }

    // Search the Gutenberg catalog
    let catalogSearchTimeout = null;
    async function searchCatalog(query) {
        if (!query || query.length < 2) {
            // Reset to popular books
            const catalogResponse = await fetch('/api/import/popular');
            state.catalogBooks = await catalogResponse.json();
            renderLibrary();
            return;
        }

        try {
            const response = await fetch(`/api/import/search?q=${encodeURIComponent(query)}`);
            state.catalogBooks = await response.json();
            renderLibrary(query);
        } catch (error) {
            console.error('Catalog search failed:', error);
        }
    }

    // Get recently read book IDs
    function getRecentlyRead() {
        const stored = localStorage.getItem(STORAGE_KEYS.RECENTLY_READ);
        return stored ? JSON.parse(stored) : [];
    }

    // Add book to recently read
    function addToRecentlyRead(bookId) {
        let recent = getRecentlyRead();
        // Remove if already exists
        recent = recent.filter(id => id !== bookId);
        // Add to front
        recent.unshift(bookId);
        // Limit size
        recent = recent.slice(0, MAX_RECENTLY_READ);
        localStorage.setItem(STORAGE_KEYS.RECENTLY_READ, JSON.stringify(recent));
    }

    function normalizeAuthorName(author) {
        const raw = (author || '').trim();
        if (!raw) return '';

        const commaCount = (raw.match(/,/g) || []).length;
        if (commaCount !== 1) {
            return raw;
        }

        const parts = raw.split(',').map(part => part.trim()).filter(Boolean);
        if (parts.length !== 2) {
            return raw;
        }

        return `${parts[1]} ${parts[0]}`.trim();
    }

    // Render a book item (local book with id)
    function renderLocalBookItem(book) {
        const author = normalizeAuthorName(book.author);
        return `
            <div class="book-item" data-book-id="${book.id}">
                <div class="book-item-title">${book.title}</div>
                <div class="book-item-author">${author}</div>
            </div>
        `;
    }

    // Render a catalog book item (from Gutenberg)
    function renderCatalogBookItem(book) {
        const importedClass = book.alreadyImported ? ' imported' : '';
        const importedBadge = book.alreadyImported ? '<span class="imported-badge">✓</span>' : '';
        const author = normalizeAuthorName(book.author);
        return `
            <div class="book-item catalog-book${importedClass}" data-gutenberg-id="${book.gutenbergId}">
                <div class="book-item-title">${book.title}${importedBadge}</div>
                <div class="book-item-author">${author}</div>
            </div>
        `;
    }

    // Render library view
    function renderLibrary(filter = '') {
        const searchTerm = filter.toLowerCase().trim();
        const recentIds = getRecentlyRead();

        // Recently read books (only show if no search filter, from local books)
        if (!searchTerm && recentIds.length > 0) {
            const recentBooks = recentIds
                .map(id => state.localBooks.find(b => b.id === id))
                .filter(Boolean);

            if (recentBooks.length > 0) {
                elements.recentlyReadList.innerHTML = recentBooks.map(renderLocalBookItem).join('');
                elements.recentlyRead.classList.remove('hidden');
            } else {
                elements.recentlyRead.classList.add('hidden');
            }
        } else {
            elements.recentlyRead.classList.add('hidden');
        }

        // Show catalog books (already filtered by search via API)
        if (state.catalogBooks.length > 0) {
            elements.bookList.innerHTML = state.catalogBooks.map(renderCatalogBookItem).join('');
            elements.allBooks.classList.remove('hidden');
            elements.noResults.classList.add('hidden');
        } else if (searchTerm) {
            elements.allBooks.classList.add('hidden');
            elements.noResults.classList.remove('hidden');
        } else {
            elements.bookList.innerHTML = '';
            elements.allBooks.classList.add('hidden');
            elements.noResults.classList.add('hidden');
        }
    }

    // Select a book
    async function selectBook(book, chapterIndex = 0, pageIndex = 0, paragraphIndex = 0) {
        state.currentBook = book;
        state.chapters = book.chapters;
        state.currentChapterIndex = chapterIndex;
        state.newCharacterQueue = [];
        state.currentToastCharacter = null;
        state.discoveredCharacterIds = loadDiscoveredCharacters(book.id);
        state.discoveredCharacterDetails = loadDiscoveredCharacterDetails(book.id);
        state.ttsVoiceSettings = null;
        state.illustrationSettings = null;
        stopCharacterPolling();

        // Save to localStorage and recently read
        localStorage.setItem(STORAGE_KEYS.LAST_BOOK, book.id);
        addToRecentlyRead(book.id);

        // Switch to reader view
        elements.libraryView.classList.add('hidden');
        elements.readerView.classList.remove('hidden');

        // Update title
        elements.bookTitle.textContent = book.title;
        const author = normalizeAuthorName(book.author);
        if (elements.bookAuthor) {
            elements.bookAuthor.textContent = author;
            elements.bookAuthor.classList.toggle('hidden', author.length === 0);
        }

        await ttsCheckAvailability();
        await illustrationCheckAvailability();
        await characterCheckAvailability();
        await recapCheckAvailability();
        await quizCheckAvailability();

        state.recapOptOut = getRecapOptOut(book.id);
        if (elements.chapterRecapOptout) {
            elements.chapterRecapOptout.checked = state.recapOptOut;
        }
        updateRecapOptOutControl();
        closeChapterRecapOverlay(false);

        const savedIllustrationMode = localStorage.getItem(STORAGE_KEYS.ILLUSTRATION_MODE);
        if (!state.illustrationMode && savedIllustrationMode === 'true' && state.illustrationAvailable) {
            state.illustrationMode = true;
            if (elements.illustrationToggle) {
                elements.illustrationToggle.classList.add('active');
            }
            updateColumnLayout();
        }
        if (state.illustrationMode) {
            updateColumnLayout();
        }

        // Load chapter
        await loadChapter(chapterIndex, pageIndex, paragraphIndex);

        // Analyze book for voice settings (async, don't block)
        if (state.ttsAvailable && !state.ttsVoiceSettings) {
            ttsAnalyzeBook();
        }

        // Analyze book for illustration style (async, don't block)
        if (state.illustrationAvailable && !state.illustrationSettings) {
            illustrationAnalyzeBook();
        }

        // Prefetch main characters for the book (async, don't block)
        if (state.characterAvailable) {
            fetch(`/api/characters/book/${book.id}/prefetch`, { method: 'POST' });
        }
    }

    // Load chapter content
    async function loadChapter(chapterIndex, pageIndex = 0, paragraphIndex = 0, suppressTts = false) {
        if (chapterIndex < 0 || chapterIndex >= state.chapters.length) return;

        state.ttsWaitingForChapter = true;
        state.currentChapterIndex = chapterIndex;
        localStorage.setItem(STORAGE_KEYS.LAST_CHAPTER, chapterIndex);

        const chapter = state.chapters[chapterIndex];
        elements.chapterTitle.textContent = chapter.title;

        try {
            const response = await fetch(`/api/library/${state.currentBook.id}/chapters/${chapter.id}`);
            const content = await response.json();
            state.paragraphs = content.paragraphs || [];

            // Calculate pages and render
            calculatePages();
            state.currentPage = Math.min(pageIndex, state.totalPages - 1);
            state.currentPage = Math.max(0, state.currentPage);
            state.currentParagraphIndex = Math.min(paragraphIndex, state.paragraphs.length - 1);
            state.currentParagraphIndex = Math.max(0, state.currentParagraphIndex);
            renderPage();

            // Continue TTS if it was enabled
            state.ttsWaitingForChapter = false;
            if (state.ttsEnabled && !suppressTts) {
                ttsSpeakCurrent();
            }

            // Load illustration if mode is enabled
            if (state.illustrationMode) {
                loadChapterIllustration();
            }

            // Analyze chapter for characters
            loadChapterCharacters();

            // Queue recap generation asynchronously (no-op when disabled or already generated)
            requestChapterRecapGeneration(chapter.id);
            requestChapterQuizGeneration(chapter.id);
        } catch (error) {
            state.ttsWaitingForChapter = false;
            console.error('Failed to load chapter:', error);
            state.paragraphs = [];
            elements.columnLeft.innerHTML = '<p class="no-content">Content not available</p>';
            elements.columnRight.innerHTML = '';
        }
    }

    // Calculate which paragraphs fit on each page
    function calculatePages() {
        state.pagesData = [];

        if (state.paragraphs.length === 0) {
            state.totalPages = 1;
            return;
        }

        // Get available height for content
        const contentArea = document.querySelector('.reader-content');
        const columnHeight = contentArea.clientHeight;
        const columnWidth = elements.columnLeft.clientWidth;

        // In illustration mode, only use single column
        const useSecondColumn = !state.illustrationMode;

        // Create a temporary measurement container
        const measureContainer = document.createElement('div');
        measureContainer.style.cssText = `
            position: absolute;
            visibility: hidden;
            width: ${columnWidth}px;
            font-family: var(--font-serif);
            font-size: var(--font-size-body);
            line-height: var(--line-height);
        `;
        document.body.appendChild(measureContainer);

        let currentPageStart = 0;
        let currentHeight = 0;
        let columnCount = 0; // 0 = first column, 1 = second column

        for (let i = 0; i < state.paragraphs.length; i++) {
            const para = state.paragraphs[i];

            // Measure paragraph height
            measureContainer.innerHTML = `<p class="paragraph" style="text-indent: ${i === currentPageStart || (state.pagesData.length > 0 && i === state.pagesData[state.pagesData.length - 1].startParagraph) ? '0' : '1.5em'}; text-align: justify;">${para.content}</p>`;
            const paraHeight = measureContainer.firstChild.offsetHeight;

            // Check if paragraph fits in current column
            if (currentHeight + paraHeight > columnHeight) {
                if (columnCount === 0 && useSecondColumn) {
                    // Move to second column
                    columnCount = 1;
                    currentHeight = paraHeight;
                } else {
                    // Start new page
                    state.pagesData.push({
                        startParagraph: currentPageStart,
                        endParagraph: i - 1
                    });
                    currentPageStart = i;
                    columnCount = 0;
                    currentHeight = paraHeight;
                }
            } else {
                currentHeight += paraHeight;
            }
        }

        // Add final page
        state.pagesData.push({
            startParagraph: currentPageStart,
            endParagraph: state.paragraphs.length - 1
        });

        state.totalPages = state.pagesData.length;
        document.body.removeChild(measureContainer);
    }

    // Render current page
    function renderPage() {
        localStorage.setItem(STORAGE_KEYS.LAST_PAGE, state.currentPage);
        localStorage.setItem(STORAGE_KEYS.LAST_PARAGRAPH, state.currentParagraphIndex);

        if (state.paragraphs.length === 0) {
            elements.columnLeft.innerHTML = '<p class="no-content">No content available</p>';
            elements.columnRight.innerHTML = '';
            elements.pageIndicator.textContent = '';
            return;
        }

        const pageData = state.pagesData[state.currentPage];
        if (!pageData) return;

        const pageParagraphs = state.paragraphs.slice(pageData.startParagraph, pageData.endParagraph + 1);

        // Get column dimensions
        const contentArea = document.querySelector('.reader-content');
        const columnHeight = contentArea.clientHeight;
        const columnWidth = elements.columnLeft.clientWidth;

        // In illustration mode, only use left column
        const useSecondColumn = !state.illustrationMode;

        // Build HTML for both columns
        let leftHtml = '';
        let rightHtml = '';
        let currentHeight = 0;
        let inRightColumn = false;

        // Temporary container for measurement
        const measureContainer = document.createElement('div');
        measureContainer.style.cssText = `
            position: absolute;
            visibility: hidden;
            width: ${columnWidth}px;
            font-family: var(--font-serif);
            font-size: var(--font-size-body);
            line-height: var(--line-height);
        `;
        document.body.appendChild(measureContainer);

        for (let i = 0; i < pageParagraphs.length; i++) {
            const para = pageParagraphs[i];
            const globalIndex = pageData.startParagraph + i;
            const isFirst = i === 0 || (inRightColumn && rightHtml === '');
            const isHighlighted = globalIndex === state.currentParagraphIndex;

            const paraHtml = `<p class="paragraph${isHighlighted ? ' highlighted' : ''}" data-index="${globalIndex}" style="text-indent: ${isFirst ? '0' : '1.5em'}">${para.content}</p>`;

            // Measure
            measureContainer.innerHTML = `<p class="paragraph" style="text-indent: ${isFirst ? '0' : '1.5em'}; text-align: justify;">${para.content}</p>`;
            const paraHeight = measureContainer.firstChild.offsetHeight;

            if (!inRightColumn && currentHeight + paraHeight > columnHeight && useSecondColumn) {
                inRightColumn = true;
                currentHeight = 0;
            }

            if (inRightColumn && useSecondColumn) {
                rightHtml += paraHtml;
            } else {
                leftHtml += paraHtml;
            }
            currentHeight += paraHeight;
        }

        document.body.removeChild(measureContainer);

        elements.columnLeft.innerHTML = leftHtml || '';
        if (useSecondColumn) {
            elements.columnRight.innerHTML = rightHtml || '';
        }

        // Update page indicator
        elements.pageIndicator.textContent = `Page ${state.currentPage + 1} of ${state.totalPages}`;

        // Ensure current paragraph is valid
        if (state.currentParagraphIndex < pageData.startParagraph || state.currentParagraphIndex > pageData.endParagraph) {
            state.currentParagraphIndex = pageData.startParagraph;
        }

        scheduleCharacterDiscoveryCheck();
    }

    // Navigation functions
    function nextPage() {
        if (state.currentPage < state.totalPages - 1) {
            state.currentPage++;
            state.currentParagraphIndex = state.pagesData[state.currentPage].startParagraph;
            renderPage();
        } else if (state.currentChapterIndex < state.chapters.length - 1) {
            goToNextChapter(true);
        }
    }

    function prevPage() {
        if (state.currentPage > 0) {
            state.currentPage--;
            state.currentParagraphIndex = state.pagesData[state.currentPage].startParagraph;
            renderPage();
        } else if (state.currentChapterIndex > 0) {
            // Go to previous chapter, last page
            loadChapter(state.currentChapterIndex - 1).then(() => {
                state.currentPage = state.totalPages - 1;
                if (state.pagesData[state.currentPage]) {
                    state.currentParagraphIndex = state.pagesData[state.currentPage].startParagraph;
                }
                renderPage();
            });
        }
    }

    function nextParagraph() {
        if (state.currentParagraphIndex < state.paragraphs.length - 1) {
            state.currentParagraphIndex++;

            // Check if we need to change page
            const pageData = state.pagesData[state.currentPage];
            if (state.currentParagraphIndex > pageData.endParagraph) {
                nextPage();
            } else {
                renderPage();
            }
        } else if (state.currentChapterIndex < state.chapters.length - 1) {
            goToNextChapter(true);
        }
    }

    function prevParagraph() {
        if (state.currentParagraphIndex > 0) {
            state.currentParagraphIndex--;

            // Check if we need to change page
            const pageData = state.pagesData[state.currentPage];
            if (state.currentParagraphIndex < pageData.startParagraph) {
                prevPage();
                state.currentParagraphIndex = state.pagesData[state.currentPage].endParagraph;
                renderPage();
            } else {
                renderPage();
            }
        } else if (state.currentChapterIndex > 0) {
            // Go to previous chapter, last paragraph
            loadChapter(state.currentChapterIndex - 1).then(() => {
                state.currentPage = state.totalPages - 1;
                state.currentParagraphIndex = state.paragraphs.length - 1;
                renderPage();
            });
        }
    }

    function nextChapter() {
        if (state.currentChapterIndex < state.chapters.length - 1) {
            goToNextChapter(true);
        }
    }

    function prevChapter() {
        if (state.currentChapterIndex > 0) {
            loadChapter(state.currentChapterIndex - 1, 0);
        }
    }

    function shouldShowChapterRecapOnTransition() {
        return (state.recapAvailable || state.quizAvailable) &&
            !state.recapOptOut &&
            !state.ttsEnabled &&
            !state.speedReadingActive;
    }

    async function goToNextChapter(showRecap) {
        const nextChapterIndex = state.currentChapterIndex + 1;
        if (nextChapterIndex >= state.chapters.length) return;

        if (showRecap && shouldShowChapterRecapOnTransition()) {
            if (state.recapCacheOnly || state.quizCacheOnly) {
                const transitionDataReady = await isCurrentChapterPauseReady();
                if (!transitionDataReady) {
                    loadChapter(nextChapterIndex, 0);
                    return;
                }
            }
            openChapterRecapOverlay(nextChapterIndex);
            return;
        }

        loadChapter(nextChapterIndex, 0);
    }

    async function openChapterRecapOverlay(nextChapterIndex) {
        if (!state.currentBook) {
            loadChapter(nextChapterIndex, 0);
            return;
        }

        const currentChapter = state.chapters[state.currentChapterIndex];
        if (!currentChapter) {
            loadChapter(nextChapterIndex, 0);
            return;
        }

        state.recapPendingChapterIndex = nextChapterIndex;
        state.recapChatChapterIndex = state.currentChapterIndex;
        state.quizChapterId = currentChapter.id;
        state.quizQuestions = [];
        state.quizSelectedAnswers = [];
        state.quizSubmitting = false;
        state.quizResult = null;
        state.quizDifficultyLevel = 0;
        await recapCheckAvailability();
        await quizCheckAvailability();
        if (elements.chapterRecapOptout) {
            elements.chapterRecapOptout.checked = state.recapOptOut;
        }
        setChapterRecapTab(state.recapAvailable ? 'recap' : 'quiz');
        elements.chapterRecapChapterTitle.textContent = currentChapter.title || 'Current chapter';
        elements.chapterRecapStatus.textContent = 'Loading recap...';
        elements.chapterRecapSummary.textContent = 'Preparing chapter recap.';
        if (elements.chapterQuizStatus) {
            elements.chapterQuizStatus.textContent = 'Loading quiz...';
        }
        renderRecapList(elements.chapterRecapEvents, []);
        renderRecapList(elements.chapterRecapCharacters, []);
        renderChapterQuizQuestions();
        renderChapterQuizFeedback(null);
        state.recapChatHistory = loadRecapChatHistory(state.recapChatChapterIndex);
        renderRecapChatMessages();
        setRecapChatControls();
        setQuizControls();
        if (elements.chapterRecapChatInput) {
            elements.chapterRecapChatInput.value = '';
        }
        elements.chapterRecapOverlay.classList.remove('hidden');
        ttsPauseForModal();
        trackRecapAnalytics('viewed');

        stopRecapOverlayPolling();
        const shouldPoll = await refreshChapterRecapOverlay(currentChapter.id);
        if (shouldPoll && isChapterRecapVisible()) {
            startRecapOverlayPolling(currentChapter.id);
        }
    }

    function populateChapterRecapOverlay(recap) {
        const payload = recap && recap.payload ? recap.payload : {};
        const summary = (payload.shortSummary || '').trim();
        const events = Array.isArray(payload.keyEvents) ? payload.keyEvents : [];
        const deltas = Array.isArray(payload.characterDeltas) ? payload.characterDeltas : [];

        if (recap && recap.status) {
            if (recap.status === 'COMPLETED') {
                elements.chapterRecapStatus.textContent = 'Recap ready';
            } else if (recap.status === 'MISSING' || recap.status === 'PENDING' || recap.status === 'GENERATING') {
                elements.chapterRecapStatus.textContent = 'Recap is still generating.';
            } else if (recap.status === 'FAILED') {
                elements.chapterRecapStatus.textContent = 'Recap generation failed.';
            } else {
                elements.chapterRecapStatus.textContent = `Recap status: ${recap.status}`;
            }
        } else {
            elements.chapterRecapStatus.textContent = 'Recap status unknown.';
        }

        elements.chapterRecapSummary.textContent = summary ||
            'Recap details are not ready yet. You can skip recap and continue.';

        renderRecapList(elements.chapterRecapEvents, events);
        renderRecapList(
            elements.chapterRecapCharacters,
            deltas.map(d => `${d.characterName}: ${d.delta}`)
        );
    }

    function setChapterRecapTab(tab) {
        const validTab = tab === 'chat' || tab === 'quiz' || tab === 'recap' ? tab : 'recap';
        let nextTab = validTab;
        if (nextTab === 'recap' && !state.recapAvailable && state.quizAvailable) {
            nextTab = 'quiz';
        }
        if (nextTab === 'quiz' && !state.quizAvailable && state.recapAvailable) {
            nextTab = 'recap';
        }
        if (nextTab === 'chat' && !state.recapChatAvailable) {
            if (state.recapAvailable) {
                nextTab = 'recap';
            } else if (state.quizAvailable) {
                nextTab = 'quiz';
            }
        }

        const recapActive = nextTab === 'recap';
        const chatActive = nextTab === 'chat';
        const quizActive = nextTab === 'quiz';
        state.recapActiveTab = nextTab;

        if (elements.chapterRecapTabRecap) {
            elements.chapterRecapTabRecap.classList.toggle('active', recapActive);
            elements.chapterRecapTabRecap.setAttribute('aria-selected', recapActive ? 'true' : 'false');
        }
        if (elements.chapterRecapTabChat) {
            elements.chapterRecapTabChat.classList.toggle('active', chatActive);
            elements.chapterRecapTabChat.setAttribute('aria-selected', chatActive ? 'true' : 'false');
        }
        if (elements.chapterRecapTabQuiz) {
            elements.chapterRecapTabQuiz.classList.toggle('active', quizActive);
            elements.chapterRecapTabQuiz.setAttribute('aria-selected', quizActive ? 'true' : 'false');
        }
        if (elements.chapterRecapPanelRecap) {
            elements.chapterRecapPanelRecap.classList.toggle('hidden', !recapActive);
        }
        if (elements.chapterRecapPanelChat) {
            elements.chapterRecapPanelChat.classList.toggle('hidden', !chatActive);
        }
        if (elements.chapterRecapPanelQuiz) {
            elements.chapterRecapPanelQuiz.classList.toggle('hidden', !quizActive);
        }
    }

    function shouldPollRecapStatus(status) {
        return status === 'MISSING' || status === 'PENDING' || status === 'GENERATING';
    }

    async function refreshChapterRecapOverlay(chapterId) {
        const pollStates = [];
        try {
            const response = await fetch(`/api/recaps/chapter/${chapterId}`, { cache: 'no-store' });
            if (!response.ok) {
                elements.chapterRecapStatus.textContent = 'Recap unavailable right now.';
                elements.chapterRecapSummary.textContent = 'You can continue to the next chapter now, and recap data will populate once generation completes.';
            } else {
                const recap = await response.json();
                populateChapterRecapOverlay(recap);
                pollStates.push(recap && shouldPollRecapStatus(recap.status));
            }
        } catch (error) {
            console.debug('Failed to load chapter recap:', error);
            elements.chapterRecapStatus.textContent = 'Recap unavailable right now.';
            elements.chapterRecapSummary.textContent = 'You can continue to the next chapter now, and recap data will populate once generation completes.';
        }

        const shouldPollQuiz = await refreshChapterQuizOverlay(chapterId);
        pollStates.push(shouldPollQuiz);
        return pollStates.some(Boolean);
    }

    function startRecapOverlayPolling(chapterId) {
        stopRecapOverlayPolling();
        state.recapPollingChapterId = chapterId;
        state.recapPollingInterval = setInterval(async () => {
            if (!isChapterRecapVisible() || state.recapPollingChapterId !== chapterId) {
                stopRecapOverlayPolling();
                return;
            }
            if (state.recapPollingInFlight) {
                return;
            }

            state.recapPollingInFlight = true;
            try {
                const shouldContinue = await refreshChapterRecapOverlay(chapterId);
                if (!shouldContinue) {
                    stopRecapOverlayPolling();
                }
            } finally {
                state.recapPollingInFlight = false;
            }
        }, 3000);
    }

    function stopRecapOverlayPolling() {
        if (state.recapPollingInterval) {
            clearInterval(state.recapPollingInterval);
        }
        state.recapPollingInterval = null;
        state.recapPollingChapterId = null;
        state.recapPollingInFlight = false;
    }

    function renderRecapList(listElement, values) {
        if (!listElement) return;
        listElement.innerHTML = '';
        if (!Array.isArray(values) || values.length === 0) {
            const li = document.createElement('li');
            li.textContent = 'Not available yet.';
            listElement.appendChild(li);
            return;
        }
        values.forEach(value => {
            const li = document.createElement('li');
            li.textContent = value;
            listElement.appendChild(li);
        });
    }

    function loadRecapChatHistory(chapterIndex = state.recapChatChapterIndex) {
        const key = getRecapChatStorageKey(chapterIndex);
        if (!key) return [];
        const stored = localStorage.getItem(key);
        if (!stored) return [];
        try {
            const parsed = JSON.parse(stored);
            if (!Array.isArray(parsed)) return [];
            return parsed
                .filter(msg => msg && typeof msg.content === 'string' && msg.content.trim())
                .map(msg => ({
                    role: msg.role === 'user' ? 'user' : 'assistant',
                    content: msg.content.trim(),
                    timestamp: Number.isFinite(msg.timestamp) ? msg.timestamp : Date.now()
                }));
        } catch (_error) {
            return [];
        }
    }

    function saveRecapChatHistory(history, chapterIndex = state.recapChatChapterIndex) {
        const key = getRecapChatStorageKey(chapterIndex);
        if (!key) return;
        const limited = Array.isArray(history) ? history.slice(-80) : [];
        localStorage.setItem(key, JSON.stringify(limited));
    }

    function getRecapChatStorageKey(chapterIndex) {
        if (!state.currentBook?.id || !Number.isInteger(chapterIndex) || chapterIndex < 0) {
            return null;
        }
        return `${STORAGE_KEYS.RECAP_CHAT_PREFIX}${state.currentBook.id}_ch${chapterIndex}`;
    }

    function setRecapChatControls() {
        const canUseChat = state.recapChatAvailable && !!state.currentBook;
        const inputDisabled = !canUseChat || state.recapChatLoading;
        const sendDisabled = !canUseChat || state.recapChatLoading;
        const chatUnavailable = !state.recapChatAvailable || state.recapCacheOnly;
        const recapUnavailable = !state.recapAvailable;

        if (elements.chapterRecapChatInput) {
            elements.chapterRecapChatInput.disabled = inputDisabled;
        }
        if (elements.chapterRecapChatSend) {
            elements.chapterRecapChatSend.disabled = sendDisabled;
        }
        if (elements.chapterRecapTabChat) {
            elements.chapterRecapTabChat.classList.toggle('unavailable', chatUnavailable);
        }
        if (elements.chapterRecapTabRecap) {
            elements.chapterRecapTabRecap.classList.toggle('unavailable', recapUnavailable);
        }

        if (!elements.chapterRecapChatStatus) return;
        if (state.recapCacheOnly) {
            elements.chapterRecapChatStatus.textContent = 'Recap chat is unavailable in cache-only mode.';
            return;
        }
        if (!state.recapChatAvailable) {
            elements.chapterRecapChatStatus.textContent = 'Recap chat provider is unavailable.';
            return;
        }
        elements.chapterRecapChatStatus.textContent = state.recapChatLoading
            ? 'Generating response...'
            : 'Spoiler-safe discussion through your current chapter.';
    }

    function renderRecapChatMessages() {
        if (!elements.chapterRecapChatMessages) return;
        if (!Array.isArray(state.recapChatHistory) || state.recapChatHistory.length === 0) {
            elements.chapterRecapChatMessages.innerHTML = '<div class="chapter-recap-chat-empty">Ask a question about what happened so far.</div>';
            return;
        }

        elements.chapterRecapChatMessages.innerHTML = state.recapChatHistory.map(msg => {
            const roleClass = msg.role === 'user' ? 'user' : 'character';
            return `
                <div class="chat-message ${roleClass}">
                    ${escapeHtml(msg.content)}
                </div>
            `;
        }).join('');

        elements.chapterRecapChatMessages.scrollTop = elements.chapterRecapChatMessages.scrollHeight;
    }

    async function refreshChapterQuizOverlay(chapterId) {
        try {
            const response = await fetch(`/api/quizzes/chapter/${chapterId}`, { cache: 'no-store' });
            if (!response.ok) {
                if (elements.chapterQuizStatus) {
                    elements.chapterQuizStatus.textContent = 'Quiz unavailable right now.';
                }
                state.quizQuestions = [];
                state.quizSelectedAnswers = [];
                renderChapterQuizQuestions();
                renderChapterQuizFeedback(null);
                setQuizControls();
                return false;
            }
            const quiz = await response.json();
            populateChapterQuizOverlay(quiz);
            return quiz && shouldPollRecapStatus(quiz.status);
        } catch (error) {
            console.debug('Failed to load chapter quiz:', error);
            if (elements.chapterQuizStatus) {
                elements.chapterQuizStatus.textContent = 'Quiz unavailable right now.';
            }
            state.quizQuestions = [];
            state.quizSelectedAnswers = [];
            renderChapterQuizQuestions();
            renderChapterQuizFeedback(null);
            setQuizControls();
            return false;
        }
    }

    function populateChapterQuizOverlay(quiz) {
        const payload = quiz && quiz.payload ? quiz.payload : {};
        const questions = Array.isArray(payload.questions) ? payload.questions : [];
        const previousSelections = Array.isArray(state.quizSelectedAnswers) ? state.quizSelectedAnswers : [];
        const sameQuestionCount = Array.isArray(state.quizQuestions) && state.quizQuestions.length === questions.length;
        const difficultyLevel = Number.isInteger(quiz?.difficultyLevel) ? quiz.difficultyLevel : 0;

        state.quizQuestions = questions;
        state.quizDifficultyLevel = difficultyLevel;
        state.quizSelectedAnswers = questions.map((_, index) =>
            Number.isInteger(previousSelections[index]) ? previousSelections[index] : null
        );
        if (!sameQuestionCount) {
            state.quizResult = null;
            renderChapterQuizFeedback(null);
        }

        if (elements.chapterQuizStatus) {
            if (quiz && quiz.status === 'COMPLETED') {
                elements.chapterQuizStatus.textContent = `Quiz ready (${formatQuizDifficulty(difficultyLevel)})`;
            } else if (quiz && (quiz.status === 'MISSING' || quiz.status === 'PENDING' || quiz.status === 'GENERATING')) {
                elements.chapterQuizStatus.textContent = 'Quiz is still generating.';
            } else if (quiz && quiz.status === 'FAILED') {
                elements.chapterQuizStatus.textContent = 'Quiz generation failed.';
            } else {
                elements.chapterQuizStatus.textContent = 'Quiz status unknown.';
            }
        }

        renderChapterQuizQuestions();
        setQuizControls();
    }

    function renderChapterQuizQuestions() {
        if (!elements.chapterQuizQuestions) return;

        const questions = Array.isArray(state.quizQuestions) ? state.quizQuestions : [];
        if (questions.length === 0) {
            elements.chapterQuizQuestions.innerHTML = '<div class="chapter-recap-chat-empty">Quiz questions are not ready yet.</div>';
            return;
        }

        elements.chapterQuizQuestions.innerHTML = questions.map((question, questionIndex) => {
            const prompt = escapeHtml(question.question || `Question ${questionIndex + 1}`);
            const options = Array.isArray(question.options) ? question.options : [];
            const optionsHtml = options.map((option, optionIndex) => {
                const checked = state.quizSelectedAnswers[questionIndex] === optionIndex ? 'checked' : '';
                return `
                    <li>
                        <label>
                            <input type="radio" name="chapter-quiz-q-${questionIndex}" data-question-index="${questionIndex}" value="${optionIndex}" ${checked} ${state.quizSubmitting ? 'disabled' : ''} />
                            <span>${escapeHtml(option)}</span>
                        </label>
                    </li>
                `;
            }).join('');
            return `
                <div class="chapter-quiz-question">
                    <div class="chapter-quiz-question-title">${questionIndex + 1}. ${prompt}</div>
                    <ul class="chapter-quiz-options">${optionsHtml}</ul>
                </div>
            `;
        }).join('');

        elements.chapterQuizQuestions.querySelectorAll('input[type="radio"]').forEach(input => {
            input.addEventListener('change', () => {
                const questionIndex = Number.parseInt(input.dataset.questionIndex, 10);
                const selectedIndex = Number.parseInt(input.value, 10);
                if (!Number.isInteger(questionIndex) || questionIndex < 0) return;
                if (!Number.isInteger(selectedIndex) || selectedIndex < 0) return;
                state.quizSelectedAnswers[questionIndex] = selectedIndex;
                setQuizControls();
            });
        });
    }

    function setQuizControls() {
        const quizUnavailable = !state.quizAvailable;
        if (elements.chapterRecapTabQuiz) {
            elements.chapterRecapTabQuiz.classList.toggle('unavailable', quizUnavailable);
            elements.chapterRecapTabQuiz.disabled = quizUnavailable;
            elements.chapterRecapTabQuiz.setAttribute('aria-disabled', quizUnavailable ? 'true' : 'false');
        }
        if (!elements.chapterQuizSubmit) return;

        const hasQuestions = Array.isArray(state.quizQuestions) && state.quizQuestions.length > 0;
        const answeredCount = Array.isArray(state.quizSelectedAnswers)
            ? state.quizSelectedAnswers.filter(value => Number.isInteger(value)).length
            : 0;
        elements.chapterQuizSubmit.disabled = quizUnavailable
            || state.quizSubmitting
            || !hasQuestions
            || answeredCount === 0;
        elements.chapterQuizSubmit.textContent = state.quizSubmitting
            ? 'Checking...'
            : 'Check Answers';
    }

    function renderChapterQuizFeedback(result) {
        if (!elements.chapterQuizFeedback) return;
        if (!result) {
            elements.chapterQuizFeedback.classList.add('hidden');
            elements.chapterQuizFeedback.innerHTML = '';
            return;
        }

        const missed = Array.isArray(result.results)
            ? result.results.filter(item => item && item.correct === false)
            : [];
        const score = Number.isFinite(result.scorePercent) ? result.scorePercent : 0;
        const correctAnswers = Number.isFinite(result.correctAnswers) ? result.correctAnswers : 0;
        const totalQuestions = Number.isFinite(result.totalQuestions) ? result.totalQuestions : 0;
        const difficultyLabel = formatQuizDifficulty(result.difficultyLevel);
        const progress = result.progress || {};
        const totalAttempts = Number.isFinite(progress.totalAttempts) ? progress.totalAttempts : 0;
        const perfectAttempts = Number.isFinite(progress.perfectAttempts) ? progress.perfectAttempts : 0;
        const streak = Number.isFinite(progress.currentPerfectStreak) ? progress.currentPerfectStreak : 0;
        const unlockedTrophies = Array.isArray(result.unlockedTrophies) ? result.unlockedTrophies : [];

        const missedItems = missed.map(item => {
            const qNum = Number.isFinite(item.questionIndex) ? item.questionIndex + 1 : '?';
            const correctAnswer = escapeHtml(item.correctAnswer || '');
            const citation = escapeHtml(item.citationSnippet || '');
            const citationLine = citation
                ? `<div><em>Citation:</em> ${citation}</div>`
                : '';
            return `<li><strong>Q${qNum}</strong> correct answer: ${correctAnswer}${citationLine}</li>`;
        }).join('');

        const details = missed.length > 0
            ? `<ul class="chapter-quiz-feedback-list">${missedItems}</ul>`
            : '<div class="chapter-quiz-feedback-list">Perfect score. Nice recall.</div>';

        const unlockedBlock = unlockedTrophies.length > 0
            ? `<div class="chapter-quiz-trophies"><strong>New trophy unlocked:</strong> ${unlockedTrophies.map(t => escapeHtml(t.title || t.code || 'Trophy')).join(', ')}</div>`
            : '';

        elements.chapterQuizFeedback.innerHTML = `
            <div class="chapter-quiz-feedback-score">Score: ${correctAnswers}/${totalQuestions} (${score}%) • ${difficultyLabel}</div>
            <div class="chapter-quiz-feedback-meta">Attempts: ${totalAttempts} • Perfect: ${perfectAttempts} • Current streak: ${streak}</div>
            ${unlockedBlock}
            ${details}
        `;
        elements.chapterQuizFeedback.classList.remove('hidden');
    }

    function formatQuizDifficulty(level) {
        const numeric = Number.isInteger(level) ? level : 0;
        if (numeric <= 0) return 'Easy';
        if (numeric === 1) return 'Medium';
        if (numeric === 2) return 'Hard';
        return `Expert ${numeric}`;
    }

    async function submitChapterQuiz() {
        if (!state.quizChapterId || !Array.isArray(state.quizQuestions) || state.quizQuestions.length === 0) {
            return;
        }
        if (state.quizSubmitting || !state.quizAvailable) {
            return;
        }

        state.quizSubmitting = true;
        setQuizControls();

        try {
            const payload = {
                selectedOptionIndexes: state.quizQuestions.map((_, index) =>
                    Number.isInteger(state.quizSelectedAnswers[index]) ? state.quizSelectedAnswers[index] : -1
                )
            };
            const response = await fetch(`/api/quizzes/chapter/${state.quizChapterId}/grade`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!response.ok) {
                if (elements.chapterQuizStatus) {
                    elements.chapterQuizStatus.textContent = response.status === 409
                        ? 'Quiz is still generating.'
                        : 'Unable to grade quiz right now.';
                }
                return;
            }

            const result = await response.json();
            state.quizResult = result;
            renderChapterQuizFeedback(result);
        } catch (error) {
            console.debug('Quiz grading failed:', error);
            if (elements.chapterQuizStatus) {
                elements.chapterQuizStatus.textContent = 'Unable to grade quiz right now.';
            }
        } finally {
            state.quizSubmitting = false;
            setQuizControls();
        }
    }

    async function sendRecapChatMessage() {
        const message = elements.chapterRecapChatInput?.value?.trim();
        if (!message || !state.currentBook || state.recapChatLoading || !state.recapChatAvailable) return;
        const chatChapterIndex = Number.isInteger(state.recapChatChapterIndex)
            ? state.recapChatChapterIndex
            : state.currentChapterIndex;

        const userMsg = { role: 'user', content: message, timestamp: Date.now() };
        state.recapChatHistory.push(userMsg);
        saveRecapChatHistory(state.recapChatHistory, chatChapterIndex);
        renderRecapChatMessages();

        if (elements.chapterRecapChatInput) {
            elements.chapterRecapChatInput.value = '';
        }

        state.recapChatLoading = true;
        setRecapChatControls();

        const loadingDiv = document.createElement('div');
        loadingDiv.className = 'chat-message character loading';
        loadingDiv.textContent = 'Thinking';
        elements.chapterRecapChatMessages?.appendChild(loadingDiv);
        if (elements.chapterRecapChatMessages) {
            elements.chapterRecapChatMessages.scrollTop = elements.chapterRecapChatMessages.scrollHeight;
        }

        try {
            const response = await fetch(`/api/recaps/book/${state.currentBook.id}/chat`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    message,
                    conversationHistory: state.recapChatHistory.slice(-10),
                    readerChapterIndex: chatChapterIndex
                })
            });

            let reply = '';
            if (response.ok) {
                const data = await response.json();
                reply = (data && typeof data.response === 'string') ? data.response : '';
            } else {
                try {
                    const errorData = await response.json();
                    reply = (errorData && typeof errorData.response === 'string') ? errorData.response : '';
                } catch (_error) {
                    reply = '';
                }
            }

            loadingDiv.remove();
            const assistantMsg = {
                role: 'assistant',
                content: reply.trim() || "I don't have enough context to answer that yet.",
                timestamp: Date.now()
            };
            state.recapChatHistory.push(assistantMsg);
            saveRecapChatHistory(state.recapChatHistory, chatChapterIndex);
            renderRecapChatMessages();
        } catch (error) {
            console.debug('Recap chat failed:', error);
            loadingDiv.remove();
            const fallbackMsg = {
                role: 'assistant',
                content: "I can't answer right now, but you can continue reading and ask again.",
                timestamp: Date.now()
            };
            state.recapChatHistory.push(fallbackMsg);
            saveRecapChatHistory(state.recapChatHistory, chatChapterIndex);
            renderRecapChatMessages();
        } finally {
            state.recapChatLoading = false;
            setRecapChatControls();
            elements.chapterRecapChatInput?.focus();
        }
    }

    function closeChapterRecapOverlay(restoreAudio = true) {
        if (!elements.chapterRecapOverlay) return;
        stopRecapOverlayPolling();
        elements.chapterRecapOverlay.classList.add('hidden');
        state.recapPendingChapterIndex = null;
        state.recapChatChapterIndex = null;
        state.quizChapterId = null;
        state.quizQuestions = [];
        state.quizSelectedAnswers = [];
        state.quizSubmitting = false;
        state.quizResult = null;
        state.quizDifficultyLevel = 0;
        setChapterRecapTab('recap');
        state.recapChatLoading = false;
        if (elements.chapterRecapChatInput) {
            elements.chapterRecapChatInput.value = '';
        }
        renderChapterQuizFeedback(null);
        renderChapterQuizQuestions();
        setRecapChatControls();
        setQuizControls();
        if (restoreAudio) {
            ttsResumeAfterModal();
        }
    }

    async function isCurrentChapterPauseReady() {
        if (!state.currentBook || !state.chapters || state.currentChapterIndex < 0) {
            return false;
        }
        const currentChapter = state.chapters[state.currentChapterIndex];
        if (!currentChapter) {
            return false;
        }
        try {
            let recapReady = false;
            let quizReady = false;

            if (state.recapAvailable) {
                const recapResponse = await fetch(`/api/recaps/chapter/${currentChapter.id}/status`, { cache: 'no-store' });
                if (recapResponse.ok) {
                    const recapStatus = await recapResponse.json();
                    recapReady = recapStatus && recapStatus.ready === true;
                }
            }

            if (state.quizAvailable) {
                const quizResponse = await fetch(`/api/quizzes/chapter/${currentChapter.id}/status`, { cache: 'no-store' });
                if (quizResponse.ok) {
                    const quizStatus = await quizResponse.json();
                    quizReady = quizStatus && quizStatus.ready === true;
                }
            }
            return recapReady || quizReady;
        } catch (error) {
            console.debug('Failed to check chapter pause cache status:', error);
            return false;
        }
    }

    async function continueFromChapterRecap(eventType = 'continued') {
        const nextChapterIndex = state.recapPendingChapterIndex;
        if (eventType) {
            trackRecapAnalytics(eventType);
        }
        closeChapterRecapOverlay(false);
        if (nextChapterIndex == null) return;
        await loadChapter(nextChapterIndex, 0);
    }

    async function skipChapterRecap() {
        await continueFromChapterRecap('skipped');
    }

    // Search
    let searchTimeout = null;

    async function performSearch(query) {
        if (!query || query.length < 2) {
            elements.searchResults.classList.add('hidden');
            return;
        }

        try {
            const response = await fetch(`/api/search?q=${encodeURIComponent(query)}&bookId=${state.currentBook.id}&limit=20`);
            const results = await response.json();

            if (results.length === 0) {
                elements.searchResults.innerHTML = '<div class="search-result-item"><em>No results found</em></div>';
            } else {
                elements.searchResults.innerHTML = results.map(result => {
                    const chapter = state.chapters.find(c => c.id === result.chapterId);
                    const chapterTitle = chapter ? chapter.title : result.chapterId;
                    const snippet = result.snippet || '';
                    // Highlight the search term in snippet
                    const highlightedSnippet = snippet.replace(
                        new RegExp(`(${query})`, 'gi'),
                        '<mark>$1</mark>'
                    );
                    return `
                        <div class="search-result-item" data-chapter-id="${result.chapterId}" data-paragraph-index="${result.paragraphIndex}">
                            <div class="search-result-chapter">${chapterTitle}</div>
                            <div class="search-result-snippet">${highlightedSnippet}</div>
                        </div>
                    `;
                }).join('');
            }

            elements.searchResults.classList.remove('hidden');
        } catch (error) {
            console.error('Search failed:', error);
        }
    }

    function navigateToSearchResult(chapterId, paragraphIndex) {
        const chapterIndex = state.chapters.findIndex(c => c.id === chapterId);
        if (chapterIndex === -1) return;

        elements.searchResults.classList.add('hidden');
        elements.searchInput.value = '';

        const loadPromise = loadChapter(chapterIndex, 0, paragraphIndex, true);
        if (state.ttsEnabled) {
            ttsStopPlayback();
        }

        loadPromise.then(() => {
            // Find which page contains this paragraph
            for (let i = 0; i < state.pagesData.length; i++) {
                const pageData = state.pagesData[i];
                if (paragraphIndex >= pageData.startParagraph && paragraphIndex <= pageData.endParagraph) {
                    state.currentPage = i;
                    state.currentParagraphIndex = paragraphIndex;
                    renderPage();
                    if (state.ttsEnabled) {
                        ttsSpeakCurrent();
                    }
                    break;
                }
            }
        });
    }

    // Back to library
    function backToLibrary() {
        ttsStop();
        closeChapterRecapOverlay(false);
        if (state.speedReadingActive) {
            exitSpeedReading(true);
        }
        stopCharacterPolling();
        state.newCharacterQueue = [];
        state.currentToastCharacter = null;
        state.ttsVoiceSettings = null;  // Clear voice settings for next book
        state.recapChatHistory = [];
        state.recapChatChapterIndex = null;
        state.recapChatLoading = false;
        state.quizChapterId = null;
        state.quizQuestions = [];
        state.quizSelectedAnswers = [];
        state.quizSubmitting = false;
        state.quizResult = null;
        updateRecapOptOutControl();
        elements.readerView.classList.add('hidden');
        elements.libraryView.classList.remove('hidden');
        elements.searchResults.classList.add('hidden');
        elements.searchInput.value = '';
        elements.librarySearch.value = '';
        renderLibrary();
        elements.librarySearch.focus();
    }

    // Chapter list
    function showChapterList() {
        ttsPauseForModal();
        chapterListSelectedIndex = state.currentChapterIndex;
        renderChapterList();
        elements.chapterListOverlay.classList.remove('hidden');
        scrollChapterIntoView(chapterListSelectedIndex);
    }

    function hideChapterList() {
        elements.chapterListOverlay.classList.add('hidden');
        ttsResumeAfterModal();
    }

    function isChapterListVisible() {
        return !elements.chapterListOverlay.classList.contains('hidden');
    }

    function renderChapterList() {
        elements.chapterList.innerHTML = state.chapters.map((chapter, index) => {
            const isCurrent = index === state.currentChapterIndex;
            const isSelected = index === chapterListSelectedIndex;
            return `
                <li class="chapter-list-item${isCurrent ? ' current' : ''}${isSelected ? ' selected' : ''}"
                    data-chapter-index="${index}">
                    <span class="chapter-number">${index + 1}</span>
                    <span class="chapter-name">${chapter.title}</span>
                </li>
            `;
        }).join('');
    }

    function scrollChapterIntoView(index) {
        const item = elements.chapterList.querySelector(`[data-chapter-index="${index}"]`);
        if (item) {
            item.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
        }
    }

    function selectChapterFromList(index) {
        if (index >= 0 && index < state.chapters.length) {
            hideChapterList();
            loadChapter(index, 0);
        }
    }

    function chapterListNavigate(direction) {
        const newIndex = chapterListSelectedIndex + direction;
        if (newIndex >= 0 && newIndex < state.chapters.length) {
            chapterListSelectedIndex = newIndex;
            renderChapterList();
            scrollChapterIntoView(chapterListSelectedIndex);
        }
    }

    // Text-to-Speech functions (using backend OpenAI TTS with browser fallback)
    async function ttsCheckAvailability() {
        state.cacheOnly = false;
        // Check browser speech synthesis support
        state.ttsBrowserAvailable = 'speechSynthesis' in window;

        // Check OpenAI TTS availability
        try {
            const response = await fetch('/api/tts/status');
            const status = await response.json();
            state.ttsOpenAIConfigured = status.openaiConfigured === true;
            state.ttsCachedAvailable = status.cachedAvailable === true;
            state.ttsOpenAIAvailable = state.ttsOpenAIConfigured || state.ttsCachedAvailable;
            state.cacheOnly = status.cacheOnly === true;
        } catch (error) {
            console.warn('OpenAI TTS not available:', error);
            state.ttsOpenAIAvailable = false;
            state.ttsOpenAIConfigured = false;
            state.ttsCachedAvailable = false;
        }

        // TTS is available if either OpenAI or browser is available and the book allows it
        state.ttsAvailable = isBookFeatureEnabled('ttsEnabled')
            && (state.ttsOpenAIAvailable || state.ttsBrowserAvailable);
        if (!state.ttsAvailable) {
            ttsStop();
        }

        if (elements.ttsToggle) {
            elements.ttsToggle.style.display = state.ttsAvailable ? '' : 'none';
        }
        if (elements.ttsHint) {
            elements.ttsHint.style.display = state.ttsAvailable ? '' : 'none';
        }
        if (elements.ttsSpeedHint) {
            elements.ttsSpeedHint.style.display = state.ttsAvailable ? '' : 'none';
        }

        console.log('TTS availability:', {
            openai: state.ttsOpenAIAvailable,
            cached: state.ttsCachedAvailable,
            browser: state.ttsBrowserAvailable,
            available: state.ttsAvailable
        });
        updateCacheOnlyIndicator();

        return {
            openaiConfigured: state.ttsOpenAIConfigured,
            cachedAvailable: state.ttsCachedAvailable,
            browserAvailable: state.ttsBrowserAvailable
        };
    }

    async function ttsAnalyzeBook() {
        if (!state.currentBook || !state.ttsAvailable) return;

        try {
            // First check if settings are already saved
            const savedResponse = await fetch(`/api/tts/settings/${state.currentBook.id}`);
            if (savedResponse.ok && savedResponse.status === 200) {
                state.ttsVoiceSettings = await savedResponse.json();
                console.log('Loaded saved voice settings:', state.ttsVoiceSettings);
                return;
            }
        } catch (error) {
            console.warn('Voice analysis failed:', error);
        }
        state.ttsVoiceSettings = { voice: 'fable', speed: 1.0, instructions: null };
    }

    function showVoiceRecommendation() {
        if (!state.ttsVoiceSettings || !state.ttsVoiceSettings.reasoning) return;

        // Show a subtle notification about the voice recommendation
        const notification = document.createElement('div');
        notification.className = 'voice-notification';
        notification.innerHTML = `
            <div class="voice-notification-content">
                <strong>AI Voice Selection:</strong> ${state.ttsVoiceSettings.voice}
                <br><small>${state.ttsVoiceSettings.reasoning}</small>
            </div>
        `;
        document.body.appendChild(notification);

        // Auto-dismiss after 5 seconds
        setTimeout(() => {
            notification.classList.add('fade-out');
            setTimeout(() => notification.remove(), 500);
        }, 5000);
    }

    function ttsToggle() {
        if (state.speedReadingActive) {
            return;
        }
        if (!state.ttsAvailable) {
            console.warn('TTS not available');
            return;
        }

        if (state.ttsEnabled) {
            ttsStop();
        } else {
            state.ttsEnabled = true;
            elements.ttsToggle.classList.add('active');
            updateSpeedIndicator();
            updateModeIndicator();
            ttsSpeakCurrent();
        }
    }

    function ttsStop() {
        state.ttsEnabled = false;
        // Abort any in-flight request
        if (state.ttsAbortController) {
            state.ttsAbortController.abort();
            state.ttsAbortController = null;
        }
        if (state.ttsAudio) {
            state.ttsAudio.pause();
            // Clean up blob URL if exists
            if (state.ttsAudio.src && state.ttsAudio.src.startsWith('blob:')) {
                URL.revokeObjectURL(state.ttsAudio.src);
            }
            state.ttsAudio = null;
        }
        // Cancel browser speech synthesis if active
        if (state.ttsBrowserAvailable) {
            speechSynthesis.cancel();
        }
        // Clear any prefetched audio
        ttsClearPrefetch();
        state.ttsUsingBrowser = false;
        if (elements.ttsToggle) {
            elements.ttsToggle.classList.remove('active');
        }
        updateSpeedIndicator();
        updateModeIndicator();
    }

    async function ttsSpeakCurrent() {
        if (!state.ttsEnabled || state.paragraphs.length === 0 || state.ttsWaitingForChapter) {
            return;
        }

        const paragraph = state.paragraphs[state.currentParagraphIndex];
        if (!paragraph) {
            ttsStop();
            return;
        }

        // Extract plain text to check if empty
        const tempDiv = document.createElement('div');
        tempDiv.innerHTML = paragraph.content;
        const text = tempDiv.textContent || tempDiv.innerText || '';

        // Skip empty paragraphs
        if (!text.trim()) {
            ttsAdvanceAndContinue();
            return;
        }

        // If OpenAI is not available, use browser TTS directly
        if (!state.ttsOpenAIAvailable) {
            ttsSpeakBrowser(text);
            return;
        }

        // Try OpenAI TTS first (includes server-side cache check)
        state.ttsUsingBrowser = false;
        updateModeIndicator();

        // Cancel any browser TTS that might still be playing
        if (state.ttsBrowserAvailable) {
            speechSynthesis.cancel();
        }

        // Abort any previous in-flight request
        if (state.ttsAbortController) {
            state.ttsAbortController.abort();
            state.ttsAbortController = null;
        }

        // Stop previous audio if any
        if (state.ttsAudio) {
            state.ttsAudio.pause();
            // Clean up blob URL if it exists
            if (state.ttsAudio.src && state.ttsAudio.src.startsWith('blob:')) {
                URL.revokeObjectURL(state.ttsAudio.src);
            }
        }

        const chapter = state.chapters[state.currentChapterIndex];
        let audio;
        let blobUrl = null;

        // Check if we have prefetched audio for this paragraph
        if (state.ttsPrefetchedAudio &&
            state.ttsPrefetchedIndex === state.currentParagraphIndex &&
            state.ttsPrefetchedChapter === chapter.id) {
            // Use prefetched audio (already has blob URL set)
            audio = state.ttsPrefetchedAudio;
            blobUrl = audio.src;  // Track for cleanup
            state.ttsPrefetchedAudio = null;
            state.ttsPrefetchedIndex = -1;
            state.ttsPrefetchedChapter = null;
            state.ttsPrefetchAbortController = null;  // Clear prefetch controller
            console.log('Using prefetched audio for paragraph', state.currentParagraphIndex);
        } else if (state.ttsCachedAvailable && !state.ttsOpenAIConfigured) {
            const params = new URLSearchParams();
            if (state.ttsVoiceSettings) {
                if (state.ttsVoiceSettings.voice) params.set('voice', state.ttsVoiceSettings.voice);
                if (state.ttsVoiceSettings.speed) params.set('speed', state.ttsVoiceSettings.speed);
                if (state.ttsVoiceSettings.instructions) params.set('instructions', state.ttsVoiceSettings.instructions);
            }
            let url = `/api/tts/speak/${state.currentBook.id}/${chapter.id}/${state.currentParagraphIndex}`;
            if (params.toString()) url += '?' + params.toString();
            audio = new Audio(url);
        } else {
            // Fetch audio with AbortController
            const controller = new AbortController();
            state.ttsAbortController = controller;

            // Build the URL with voice settings
            let url = `/api/tts/speak/${state.currentBook.id}/${chapter.id}/${state.currentParagraphIndex}`;

            const params = new URLSearchParams();
            if (state.ttsVoiceSettings) {
                if (state.ttsVoiceSettings.voice) params.set('voice', state.ttsVoiceSettings.voice);
                if (state.ttsVoiceSettings.speed) params.set('speed', state.ttsVoiceSettings.speed);
                if (state.ttsVoiceSettings.instructions) params.set('instructions', state.ttsVoiceSettings.instructions);
            }
            if (params.toString()) url += '?' + params.toString();

            try {
                const response = await fetch(url, { signal: controller.signal });
                if (!response.ok) {
                    throw new Error(`TTS request failed: ${response.status}`);
                }
                const blob = await response.blob();
                blobUrl = URL.createObjectURL(blob);
                audio = new Audio(blobUrl);
            } catch (error) {
                if (error.name === 'AbortError') {
                    console.log('TTS request aborted for paragraph', state.currentParagraphIndex);
                    return;  // Request was cancelled, don't continue
                }
                console.error('OpenAI TTS fetch error, falling back to browser:', error);
                if (state.ttsEnabled && state.ttsBrowserAvailable) {
                    ttsSpeakBrowser(text);
                }
                return;
            }
        }

        state.ttsAudio = audio;

        // Set playbackRate both immediately and after load to ensure it sticks
        audio.playbackRate = state.ttsPlaybackRate;
        audio.onloadeddata = () => {
            audio.playbackRate = state.ttsPlaybackRate;
        };

        audio.onended = () => {
            // Only handle if this is still the current audio (not replaced by navigation)
            if (state.ttsAudio !== audio) return;

            // Clean up blob URL
            if (blobUrl) {
                URL.revokeObjectURL(blobUrl);
            }

            if (state.ttsEnabled) {
                ttsAdvanceAndContinue();
            }
        };

        audio.onerror = (event) => {
            // Only handle error if this is still the current audio (not replaced by navigation)
            if (state.ttsAudio !== audio) return;

            // Clean up blob URL
            if (blobUrl) {
                URL.revokeObjectURL(blobUrl);
            }

            console.error('OpenAI TTS audio error, falling back to browser:', event);
            // Fall back to browser TTS
            if (state.ttsEnabled && state.ttsBrowserAvailable) {
                ttsSpeakBrowser(text);
            } else if (state.ttsEnabled) {
                setTimeout(() => ttsAdvanceAndContinue(), 500);
            }
        };

        try {
            await audio.play();
            // Also set after play starts to be extra sure
            audio.playbackRate = state.ttsPlaybackRate;
            // Start prefetching next paragraph once playback begins
            ttsPrefetchNext();
        } catch (error) {
            // Only fall back if this is still the current audio (not replaced by navigation)
            if (state.ttsAudio !== audio) return;

            // Clean up blob URL
            if (blobUrl) {
                URL.revokeObjectURL(blobUrl);
            }

            console.error('OpenAI TTS playback error, falling back to browser:', error);
            // Fall back to browser TTS
            if (state.ttsEnabled && state.ttsBrowserAvailable) {
                ttsSpeakBrowser(text);
            }
        }
    }

    function ttsAdvanceAndContinue() {
        const wasLastParagraph = state.currentParagraphIndex >= state.paragraphs.length - 1;
        const wasLastChapter = state.currentChapterIndex >= state.chapters.length - 1;

        if (wasLastParagraph && wasLastChapter) {
            // End of book
            ttsStop();
            return;
        }

        if (wasLastParagraph) {
            // Keep TTS chapter transitions uninterrupted by recap overlay.
            goToNextChapter(false);
        } else {
            nextParagraph();
            ttsSpeakCurrent();
        }
    }

    async function ttsPrefetchNext() {
        // Don't prefetch if OpenAI TTS is not available (browser TTS doesn't benefit from prefetch)
        if (!state.ttsOpenAIAvailable || !state.ttsEnabled) return;
        if (state.ttsCachedAvailable && !state.ttsOpenAIConfigured) return;

        const nextIndex = state.currentParagraphIndex + 1;
        const chapter = state.chapters[state.currentChapterIndex];

        // Don't prefetch if at end of chapter (cross-chapter prefetch is complex)
        if (nextIndex >= state.paragraphs.length) return;

        // Don't prefetch if already have this one cached
        if (state.ttsPrefetchedIndex === nextIndex &&
            state.ttsPrefetchedChapter === chapter.id &&
            state.ttsPrefetchedAudio) {
            return;
        }

        const nextParagraph = state.paragraphs[nextIndex];
        if (!nextParagraph) return;

        // Extract plain text to check if empty
        const tempDiv = document.createElement('div');
        tempDiv.innerHTML = nextParagraph.content;
        const text = tempDiv.textContent || tempDiv.innerText || '';

        // Don't prefetch empty paragraphs
        if (!text.trim()) return;

        // Abort any existing prefetch request
        if (state.ttsPrefetchAbortController) {
            state.ttsPrefetchAbortController.abort();
        }

        // Clean up any existing prefetched audio blob URL
        if (state.ttsPrefetchedAudio && state.ttsPrefetchedAudio.src &&
            state.ttsPrefetchedAudio.src.startsWith('blob:')) {
            URL.revokeObjectURL(state.ttsPrefetchedAudio.src);
        }

        // Build the URL with voice settings
        let url = `/api/tts/speak/${state.currentBook.id}/${chapter.id}/${nextIndex}`;

        const params = new URLSearchParams();
        if (state.ttsVoiceSettings) {
            if (state.ttsVoiceSettings.voice) params.set('voice', state.ttsVoiceSettings.voice);
            if (state.ttsVoiceSettings.speed) params.set('speed', state.ttsVoiceSettings.speed);
            if (state.ttsVoiceSettings.instructions) params.set('instructions', state.ttsVoiceSettings.instructions);
        }
        if (params.toString()) url += '?' + params.toString();

        // Create AbortController for this prefetch
        const controller = new AbortController();
        state.ttsPrefetchAbortController = controller;

        console.log('Prefetching audio for paragraph', nextIndex);

        try {
            const response = await fetch(url, { signal: controller.signal });
            if (!response.ok) {
                throw new Error(`Prefetch request failed: ${response.status}`);
            }
            const blob = await response.blob();
            const blobUrl = URL.createObjectURL(blob);

            // Only store if this prefetch wasn't cancelled
            if (state.ttsPrefetchAbortController === controller) {
                const prefetchAudio = new Audio(blobUrl);
                state.ttsPrefetchedAudio = prefetchAudio;
                state.ttsPrefetchedIndex = nextIndex;
                state.ttsPrefetchedChapter = chapter.id;
                console.log('Prefetch complete for paragraph', nextIndex);
            } else {
                // Prefetch was superseded, clean up
                URL.revokeObjectURL(blobUrl);
            }
        } catch (error) {
            if (error.name === 'AbortError') {
                console.log('Prefetch aborted for paragraph', nextIndex);
            } else {
                console.warn('Prefetch failed for paragraph', nextIndex, error);
            }
        }
    }

    function ttsClearPrefetch() {
        // Abort any in-flight prefetch request
        if (state.ttsPrefetchAbortController) {
            state.ttsPrefetchAbortController.abort();
            state.ttsPrefetchAbortController = null;
        }
        // Clean up blob URL if exists
        if (state.ttsPrefetchedAudio) {
            if (state.ttsPrefetchedAudio.src && state.ttsPrefetchedAudio.src.startsWith('blob:')) {
                URL.revokeObjectURL(state.ttsPrefetchedAudio.src);
            }
            state.ttsPrefetchedAudio = null;
        }
        state.ttsPrefetchedIndex = -1;
        state.ttsPrefetchedChapter = null;
    }

    function ttsSpeakBrowser(text) {
        // Use browser's built-in speech synthesis as fallback
        if (!state.ttsBrowserAvailable) {
            console.warn('Browser speech synthesis not available');
            ttsStop();
            return;
        }

        state.ttsUsingBrowser = true;
        updateModeIndicator();

        // Cancel any pending speech
        speechSynthesis.cancel();

        const utterance = new SpeechSynthesisUtterance(text);

        // Map playback rate (our 1.0-2.0 range works well with utterance.rate)
        utterance.rate = state.ttsPlaybackRate;

        // Try to find a reasonable voice (prefer English)
        const voices = speechSynthesis.getVoices();
        const englishVoice = voices.find(v => v.lang.startsWith('en') && v.localService) ||
                             voices.find(v => v.lang.startsWith('en')) ||
                             voices[0];
        if (englishVoice) {
            utterance.voice = englishVoice;
        }

        utterance.onend = () => {
            // Only advance if still using browser TTS (not switched to OpenAI)
            if (state.ttsEnabled && state.ttsUsingBrowser) {
                ttsAdvanceAndContinue();
            }
        };

        utterance.onerror = (event) => {
            console.error('Browser TTS error:', event);
            // Only handle if still using browser TTS and not interrupted by cancel
            if (state.ttsEnabled && state.ttsUsingBrowser && event.error !== 'interrupted') {
                setTimeout(() => ttsAdvanceAndContinue(), 500);
            }
        };

        speechSynthesis.speak(utterance);
    }

    function ttsStopPlayback() {
        if (!state.ttsEnabled) {
            return;
        }

        // Abort any in-flight request
        if (state.ttsAbortController) {
            state.ttsAbortController.abort();
            state.ttsAbortController = null;
        }
        if (state.ttsAudio) {
            state.ttsAudio.pause();
            // Clean up blob URL if exists
            if (state.ttsAudio.src && state.ttsAudio.src.startsWith('blob:')) {
                URL.revokeObjectURL(state.ttsAudio.src);
            }
            state.ttsAudio = null;
        }
        // Cancel browser speech synthesis if active
        if (state.ttsBrowserAvailable) {
            speechSynthesis.cancel();
        }
        // Clear prefetch since user navigated away
        ttsClearPrefetch();
    }

    function ttsInterrupt() {
        // Called when user navigates manually while TTS is active
        if (state.ttsEnabled) {
            ttsStopPlayback();
            ttsSpeakCurrent();
        }
    }

    function ttsCycleSpeed() {
        const speeds = [1.0, 1.25, 1.5, 1.75, 2.0];
        const currentIndex = speeds.indexOf(state.ttsPlaybackRate);
        const nextIndex = (currentIndex + 1) % speeds.length;
        state.ttsPlaybackRate = speeds[nextIndex];

        // Save preference
        localStorage.setItem(STORAGE_KEYS.TTS_SPEED, state.ttsPlaybackRate);
        console.log('Saved TTS speed:', state.ttsPlaybackRate);

        // Update current audio if playing (OpenAI)
        if (state.ttsAudio) {
            state.ttsAudio.playbackRate = state.ttsPlaybackRate;
        }

        // For browser TTS, restart with new speed (can't change rate mid-utterance)
        if (state.ttsUsingBrowser && state.ttsEnabled) {
            ttsInterrupt();
        }

        updateSpeedIndicator();
        showSpeedNotification();
    }

    function updateSpeedIndicator() {
        if (elements.ttsSpeed) {
            if (state.ttsEnabled && state.ttsPlaybackRate !== 1.0) {
                elements.ttsSpeed.textContent = state.ttsPlaybackRate + 'x';
            } else {
                elements.ttsSpeed.textContent = '';
            }
        }
    }

    function updateModeIndicator() {
        if (elements.ttsMode) {
            if (state.ttsEnabled) {
                if (state.ttsUsingBrowser) {
                    elements.ttsMode.textContent = 'Browser';
                    elements.ttsMode.className = 'tts-mode browser';
                } else {
                    elements.ttsMode.textContent = 'AI';
                    elements.ttsMode.className = 'tts-mode openai';
                }
            } else {
                elements.ttsMode.textContent = '';
                elements.ttsMode.className = 'tts-mode';
            }
        }
    }

    function showSpeedNotification() {
        // Remove existing notification
        const existing = document.querySelector('.speed-notification');
        if (existing) existing.remove();

        const notification = document.createElement('div');
        notification.className = 'speed-notification';
        notification.textContent = state.ttsPlaybackRate + 'x';
        document.body.appendChild(notification);

        // Auto-dismiss after 1 second
        setTimeout(() => {
            notification.classList.add('fade-out');
            setTimeout(() => notification.remove(), 300);
        }, 1000);
    }

    // Pause TTS when opening modals/overlays (saves state for resume)
    function ttsPauseForModal() {
        if (state.ttsEnabled) {
            state.ttsWasPlayingBeforeModal = true;
            // Pause without fully stopping (don't clear ttsEnabled)
            if (state.ttsAudio) {
                state.ttsAudio.pause();
            }
            if (state.ttsBrowserAvailable) {
                speechSynthesis.cancel();
            }
        } else {
            state.ttsWasPlayingBeforeModal = false;
        }
    }

    // Resume TTS after closing modals/overlays (if it was playing before)
    function ttsResumeAfterModal() {
        if (state.ttsWasPlayingBeforeModal && state.ttsEnabled) {
            state.ttsWasPlayingBeforeModal = false;
            ttsSpeakCurrent();
        }
    }

    // ========================================
    // Speed Reading Mode Functions
    // ========================================

    function speedReadingToggle() {
        if (!state.speedReadingEnabled) {
            return;
        }
        if (state.speedReadingActive) {
            exitSpeedReading();
        } else {
            enterSpeedReading();
        }
    }

    function enterSpeedReading() {
        if (!state.speedReadingEnabled) {
            return;
        }
        if (!state.currentBook || state.paragraphs.length === 0) return;

        if (state.ttsEnabled) {
            ttsStop();
        }

        if (state.illustrationMode) {
            state.speedReadingRestoreIllustration = true;
            illustrationToggle();
        } else {
            state.speedReadingRestoreIllustration = false;
        }

        state.speedReadingActive = true;
        state.speedReadingPlaying = true;
        elements.readerView.classList.add('speed-reading-active');
        elements.speedReadingOverlay.classList.remove('hidden');
        elements.speedReadingChapterOverlay.classList.add('hidden');
        elements.speedReadingToggle.classList.add('active');
        if (elements.ttsToggle) {
            elements.ttsToggle.disabled = true;
        }
        if (elements.illustrationToggle) {
            elements.illustrationToggle.disabled = true;
        }

        updateSpeedReadingControls();
        prepareSpeedReadingTokens(state.currentParagraphIndex);
        state.speedReadingTokenIndex = 0;
        speedReadingStep();
    }

    function exitSpeedReading(skipRender = false) {
        clearSpeedReadingTimer();
        state.speedReadingActive = false;
        state.speedReadingPlaying = false;
        updateSpeedReadingControls();
        elements.readerView.classList.remove('speed-reading-active');
        elements.speedReadingOverlay.classList.add('hidden');
        elements.speedReadingChapterOverlay.classList.add('hidden');
        elements.speedReadingToggle.classList.remove('active');
        if (elements.ttsToggle) {
            elements.ttsToggle.disabled = false;
        }
        if (elements.illustrationToggle) {
            elements.illustrationToggle.disabled = false;
        }

        let restoredIllustration = false;
        if (state.speedReadingRestoreIllustration && !state.illustrationMode && state.illustrationAvailable) {
            illustrationToggle();
            restoredIllustration = true;
        }
        state.speedReadingRestoreIllustration = false;

        if (!skipRender && !restoredIllustration) {
            syncPageToParagraph();
        }
    }

    function speedReadingPause() {
        state.speedReadingPlaying = false;
        clearSpeedReadingTimer();
        updateSpeedReadingControls();
    }

    function speedReadingStart() {
        if (state.speedReadingPlaying) return;
        state.speedReadingPlaying = true;
        updateSpeedReadingControls();
        speedReadingStep();
    }

    function speedReadingStep() {
        if (!state.speedReadingActive || !state.speedReadingPlaying) return;

        const token = state.speedReadingTokens[state.speedReadingTokenIndex];
        if (!token) {
            handleSpeedReadingParagraphEnd();
            return;
        }

        renderSpeedReadingToken(token.text);
        state.speedReadingTokenIndex += 1;

        const delay = getSpeedReadingDelay(token);
        state.speedReadingTimer = setTimeout(speedReadingStep, delay);
    }

    function clearSpeedReadingTimer() {
        if (state.speedReadingTimer) {
            clearTimeout(state.speedReadingTimer);
            state.speedReadingTimer = null;
        }
    }

    function getSpeedReadingDelay(token) {
        const baseDelay = Math.max(40, Math.round(60000 / state.speedReadingWpm));
        return token.hasPunctuation ? baseDelay * 2 : baseDelay;
    }

    function handleSpeedReadingParagraphEnd() {
        clearSpeedReadingTimer();

        if (state.currentParagraphIndex < state.paragraphs.length - 1) {
            const nextIndex = findNextReadableParagraph(state.currentParagraphIndex + 1);
            if (nextIndex === -1) {
                showSpeedReadingChapterPause();
                return;
            }
            state.currentParagraphIndex = nextIndex;
            localStorage.setItem(STORAGE_KEYS.LAST_PARAGRAPH, state.currentParagraphIndex);
            scheduleCharacterDiscoveryCheck();
            prepareSpeedReadingTokens(state.currentParagraphIndex);
            state.speedReadingTokenIndex = 0;
            speedReadingStep();
        } else {
            showSpeedReadingChapterPause();
        }
    }

    function findNextReadableParagraph(startIndex) {
        for (let i = startIndex; i < state.paragraphs.length; i++) {
            const tokens = tokenizeParagraph(state.paragraphs[i]);
            if (tokens.length > 0) {
                state.speedReadingTokens = tokens;
                return i;
            }
        }
        return -1;
    }

    function prepareSpeedReadingTokens(paragraphIndex) {
        const paragraph = state.paragraphs[paragraphIndex];
        state.speedReadingTokens = tokenizeParagraph(paragraph);
        if (state.speedReadingTokens.length === 0) {
            const nextIndex = findNextReadableParagraph(paragraphIndex + 1);
            if (nextIndex !== -1) {
                state.currentParagraphIndex = nextIndex;
                localStorage.setItem(STORAGE_KEYS.LAST_PARAGRAPH, state.currentParagraphIndex);
                scheduleCharacterDiscoveryCheck();
            }
        }
    }

    function tokenizeParagraph(paragraph) {
        if (!paragraph) return [];
        const tempDiv = document.createElement('div');
        tempDiv.innerHTML = paragraph.content || '';
        const text = (tempDiv.textContent || tempDiv.innerText || '').trim();
        if (!text) return [];
        return text.split(/\s+/).map(token => ({
            text: token,
            hasPunctuation: /[.,;:!?]+$/.test(token)
        }));
    }

    function renderSpeedReadingToken(tokenText) {
        if (!elements.speedReadingWord) return;
        elements.speedReadingWord.innerHTML = '';

        const pivotIndex = findPivotIndex(tokenText);
        if (pivotIndex === -1) {
            elements.speedReadingWord.textContent = tokenText;
            return;
        }

        const left = document.createElement('span');
        left.textContent = tokenText.slice(0, pivotIndex);

        const pivot = document.createElement('span');
        pivot.className = 'pivot';
        pivot.textContent = tokenText[pivotIndex];

        const right = document.createElement('span');
        right.textContent = tokenText.slice(pivotIndex + 1);

        elements.speedReadingWord.append(left, pivot, right);
    }

    function findPivotIndex(tokenText) {
        const letterIndices = [];
        for (let i = 0; i < tokenText.length; i++) {
            if (/[A-Za-z]/.test(tokenText[i])) {
                letterIndices.push(i);
            }
        }
        if (letterIndices.length === 0) {
            return -1;
        }
        const pivotOffset = Math.floor((letterIndices.length - 1) * 0.4);
        return letterIndices[pivotOffset];
    }

    function updateSpeedReadingControls() {
        if (elements.speedReadingSlider) {
            elements.speedReadingSlider.value = state.speedReadingWpm;
        }
        if (elements.speedReadingWpm) {
            elements.speedReadingWpm.textContent = `${state.speedReadingWpm} wpm`;
        }
        if (elements.speedReadingPlay) {
            elements.speedReadingPlay.textContent = state.speedReadingPlaying ? 'Pause' : 'Play';
        }
    }

    function showSpeedReadingChapterPause() {
        speedReadingPause();

        const nextChapterIndex = state.currentChapterIndex + 1;
        if (nextChapterIndex < state.chapters.length) {
            elements.speedReadingChapterTitle.textContent = `Next Chapter: ${state.chapters[nextChapterIndex].title}`;
            elements.speedReadingContinue.disabled = false;
        } else {
            elements.speedReadingChapterTitle.textContent = 'End of book';
            elements.speedReadingContinue.disabled = true;
        }

        elements.speedReadingChapterOverlay.classList.remove('hidden');
    }

    async function continueSpeedReading() {
        const nextChapterIndex = state.currentChapterIndex + 1;
        if (nextChapterIndex >= state.chapters.length) {
            exitSpeedReading();
            return;
        }

        elements.speedReadingChapterOverlay.classList.add('hidden');
        await loadChapter(nextChapterIndex, 0, 0);
        prepareSpeedReadingTokens(state.currentParagraphIndex);
        state.speedReadingTokenIndex = 0;
        speedReadingStart();
    }

    function syncPageToParagraph() {
        if (state.pagesData.length === 0) return;
        const index = state.currentParagraphIndex;
        for (let i = 0; i < state.pagesData.length; i++) {
            const page = state.pagesData[i];
            if (index >= page.startParagraph && index <= page.endParagraph) {
                state.currentPage = i;
                break;
            }
        }
        renderPage();
    }

    // ========================================
    // Illustration Mode Functions
    // ========================================

    async function illustrationCheckAvailability() {
        try {
            const response = await fetch('/api/illustrations/status');
            const status = await response.json();
            state.illustrationAvailable = isBookFeatureEnabled('illustrationEnabled');
            state.allowPromptEditing = state.illustrationAvailable
                && !status.cacheOnly
                && (status.allowPromptEditing || false);
            state.illustrationCacheOnly = status.cacheOnly === true;
            state.cacheOnly = state.cacheOnly || status.cacheOnly === true;
        } catch (error) {
            console.warn('Illustration service not available:', error);
            state.illustrationAvailable = false;
            state.allowPromptEditing = false;
            state.illustrationCacheOnly = false;
        }

        if (elements.illustrationToggle) {
            elements.illustrationToggle.style.display = state.illustrationAvailable ? '' : 'none';
        }
        if (elements.illustrationHint) {
            elements.illustrationHint.style.display = state.illustrationAvailable ? '' : 'none';
        }
        if (elements.illustrationImage && !state.allowPromptEditing) {
            elements.illustrationImage.classList.remove('editable');
        }

        if (!state.illustrationAvailable && state.illustrationMode) {
            state.illustrationMode = false;
            if (elements.illustrationToggle) {
                elements.illustrationToggle.classList.remove('active');
            }
            updateColumnLayout();
        }

        console.log('Illustration availability:', state.illustrationAvailable, 'Prompt editing:', state.allowPromptEditing);
        updateCacheOnlyIndicator();
    }

    async function illustrationAnalyzeBook() {
        if (!state.currentBook || !state.illustrationAvailable) return;

        try {
            // First check if settings are already saved
            const savedResponse = await fetch(`/api/illustrations/settings/${state.currentBook.id}`);
            if (savedResponse.ok && savedResponse.status === 200) {
                state.illustrationSettings = await savedResponse.json();
                console.log('Loaded saved illustration settings:', state.illustrationSettings);
            }
        } catch (error) {
            console.warn('Illustration style analysis failed:', error);
        }
    }

    function showStyleNotification() {
        if (!state.illustrationSettings || !state.illustrationSettings.reasoning) return;

        const notification = document.createElement('div');
        notification.className = 'style-notification';
        notification.innerHTML = `
            <div class="style-notification-content">
                <strong>AI Illustration Style:</strong> ${state.illustrationSettings.style}
                <br><small>${state.illustrationSettings.reasoning}</small>
            </div>
        `;
        document.body.appendChild(notification);

        // Auto-dismiss after 5 seconds
        setTimeout(() => {
            notification.classList.add('fade-out');
            setTimeout(() => notification.remove(), 500);
        }, 5000);
    }

    function illustrationToggle() {
        if (state.speedReadingActive) {
            return;
        }
        if (!state.illustrationAvailable) {
            console.warn('Illustration mode not available');
            return;
        }

        state.illustrationMode = !state.illustrationMode;
        localStorage.setItem(STORAGE_KEYS.ILLUSTRATION_MODE, state.illustrationMode);

        if (elements.illustrationToggle) {
            elements.illustrationToggle.classList.toggle('active', state.illustrationMode);
        }

        updateColumnLayout();

        if (state.illustrationMode) {
            loadChapterIllustration();
        } else {
            // Stop any polling
            if (state.illustrationPolling) {
                clearInterval(state.illustrationPolling);
                state.illustrationPolling = null;
            }
        }

        // Recalculate pages for the new layout and keep paragraph position.
        calculatePages();
        syncPageToParagraph();
    }

    function updateColumnLayout() {
        const contentArea = document.querySelector('.reader-content');

        if (state.illustrationMode) {
            contentArea.classList.add('illustration-mode');
            elements.columnRight.classList.add('hidden');
            elements.illustrationColumn.classList.remove('hidden');
        } else {
            contentArea.classList.remove('illustration-mode');
            elements.columnRight.classList.remove('hidden');
            elements.illustrationColumn.classList.add('hidden');
            // Hide all illustration states
            elements.illustrationSkeleton.classList.add('hidden');
            elements.illustrationImage.classList.add('hidden');
            elements.illustrationError.classList.add('hidden');
        }
    }

    async function loadChapterIllustration() {
        if (!state.illustrationMode || !state.currentBook) return;

        const chapter = state.chapters[state.currentChapterIndex];
        if (!chapter) return;

        // Show skeleton placeholder
        showIllustrationSkeleton();

        try {
            // Check status
            const statusResponse = await fetch(`/api/illustrations/chapter/${chapter.id}/status`);
            const status = await statusResponse.json();

            if (status.ready) {
                // Load the image
                displayIllustration(chapter.id);
            } else if (state.illustrationCacheOnly) {
                showIllustrationError();
            } else {
                // Always call request first - backend handles duplicates gracefully
                // and will re-queue stuck PENDING illustrations older than 5 minutes
                await fetch(`/api/illustrations/chapter/${chapter.id}/request`, { method: 'POST' });
                // Still generating, poll for completion
                pollForIllustration(chapter.id);
            }

            // Pre-fetch next chapter
            if (!state.illustrationCacheOnly) {
                fetch(`/api/illustrations/chapter/${chapter.id}/prefetch-next`, { method: 'POST' });
            }

        } catch (error) {
            console.error('Failed to load illustration:', error);
            showIllustrationError();
        }
    }

    function pollForIllustration(chapterId) {
        // Clear any existing polling
        if (state.illustrationPolling) {
            clearInterval(state.illustrationPolling);
        }

        let attempts = 0;
        const maxAttempts = 90; // 3 minutes at 2-second intervals

        state.illustrationPolling = setInterval(async () => {
            attempts++;

            // Check if we're still on the same chapter and illustration mode is on
            const currentChapter = state.chapters[state.currentChapterIndex];
            if (!currentChapter || currentChapter.id !== chapterId || !state.illustrationMode) {
                clearInterval(state.illustrationPolling);
                state.illustrationPolling = null;
                return;
            }

            if (attempts >= maxAttempts) {
                clearInterval(state.illustrationPolling);
                state.illustrationPolling = null;
                showIllustrationError();
                return;
            }

            try {
                const response = await fetch(`/api/illustrations/chapter/${chapterId}/status`);
                const status = await response.json();

                if (status.ready) {
                    clearInterval(state.illustrationPolling);
                    state.illustrationPolling = null;
                    displayIllustration(chapterId);
                } else if (status.status === 'FAILED') {
                    clearInterval(state.illustrationPolling);
                    state.illustrationPolling = null;
                    showIllustrationError();
                }
                // Otherwise keep polling
            } catch (error) {
                console.error('Polling error:', error);
                // Continue polling on network errors
            }
        }, 2000);
    }

    function displayIllustration(chapterId) {
        hideIllustrationSkeleton();
        elements.illustrationError.classList.add('hidden');
        elements.illustrationImage.src = `/api/illustrations/chapter/${chapterId}?t=${Date.now()}`;
        elements.illustrationImage.classList.remove('hidden');

        // Make image clickable if prompt editing is enabled
        if (state.allowPromptEditing) {
            elements.illustrationImage.classList.add('editable');
        } else {
            elements.illustrationImage.classList.remove('editable');
        }
    }

    function showIllustrationSkeleton() {
        elements.illustrationSkeleton.classList.remove('hidden');
        elements.illustrationImage.classList.add('hidden');
        elements.illustrationError.classList.add('hidden');
    }

    function hideIllustrationSkeleton() {
        elements.illustrationSkeleton.classList.add('hidden');
    }

    function showIllustrationError() {
        hideIllustrationSkeleton();
        elements.illustrationImage.classList.add('hidden');
        elements.illustrationError.classList.remove('hidden');
    }

    // ========================================
    // Prompt Editing Modal Functions
    // ========================================

    async function openPromptModal() {
        if (!state.allowPromptEditing || state.illustrationCacheOnly || !state.illustrationMode) return;

        const chapter = state.chapters[state.currentChapterIndex];
        if (!chapter) return;

        // Pause TTS while modal is open
        ttsPauseForModal();

        // Store chapter ID
        state.promptModalChapterId = chapter.id;

        // Show modal in edit mode
        showPromptEditMode();
        elements.promptModal.classList.remove('hidden');
        elements.promptTextarea.value = 'Loading prompt...';
        elements.promptTextarea.disabled = true;
        elements.promptRegenerate.disabled = true;

        try {
            const response = await fetch(`/api/illustrations/chapter/${chapter.id}/prompt`);
            if (response.ok) {
                const data = await response.json();
                elements.promptTextarea.value = data.prompt || '';
                state.promptModalLastPrompt = data.prompt || '';
                elements.promptTextarea.disabled = false;
                elements.promptRegenerate.disabled = false;
            } else if (response.status === 404) {
                elements.promptTextarea.value = 'No prompt available for this illustration.';
            } else {
                elements.promptTextarea.value = 'Failed to load prompt.';
            }
        } catch (error) {
            console.error('Failed to load prompt:', error);
            elements.promptTextarea.value = 'Failed to load prompt.';
        }
    }

    function closePromptModal() {
        // Stop any polling
        if (state.promptModalPolling) {
            clearInterval(state.promptModalPolling);
            state.promptModalPolling = null;
        }

        elements.promptModal.classList.add('hidden');
        elements.promptTextarea.value = '';
        state.promptModalChapterId = null;
        state.promptModalLastPrompt = '';
        ttsResumeAfterModal();
    }

    function isPromptModalVisible() {
        return !elements.promptModal.classList.contains('hidden');
    }

    function showPromptEditMode() {
        elements.promptModalTitle.textContent = 'Edit Illustration Prompt';
        elements.promptEditMode.classList.remove('hidden');
        elements.promptGeneratingMode.classList.add('hidden');
        elements.promptPreviewMode.classList.add('hidden');
        elements.promptEditButtons.classList.remove('hidden');
        elements.promptPreviewButtons.classList.add('hidden');
    }

    function showPromptGeneratingMode() {
        elements.promptModalTitle.textContent = 'Generating Illustration';
        elements.promptEditMode.classList.add('hidden');
        elements.promptGeneratingMode.classList.remove('hidden');
        elements.promptPreviewMode.classList.add('hidden');
        elements.promptEditButtons.classList.add('hidden');
        elements.promptPreviewButtons.classList.add('hidden');
    }

    function showPromptPreviewMode(imageUrl) {
        elements.promptModalTitle.textContent = 'Preview Illustration';
        elements.promptPreviewImage.src = imageUrl;
        elements.promptEditMode.classList.add('hidden');
        elements.promptGeneratingMode.classList.add('hidden');
        elements.promptPreviewMode.classList.remove('hidden');
        elements.promptEditButtons.classList.add('hidden');
        elements.promptPreviewButtons.classList.remove('hidden');
    }

    async function regenerateIllustration() {
        const chapterId = state.promptModalChapterId;
        if (!chapterId) return;

        const newPrompt = elements.promptTextarea.value.trim();
        if (!newPrompt) {
            alert('Please enter a prompt.');
            return;
        }

        // Save the prompt for "try again"
        state.promptModalLastPrompt = newPrompt;

        // Disable button while submitting
        elements.promptRegenerate.disabled = true;

        try {
            const response = await fetch(`/api/illustrations/chapter/${chapterId}/regenerate`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ prompt: newPrompt })
            });

            if (response.ok) {
                // Switch to generating mode and poll for completion
                showPromptGeneratingMode();
                pollForRegeneratedIllustration(chapterId);
            } else {
                alert('Failed to regenerate illustration. Please try again.');
                elements.promptRegenerate.disabled = false;
            }
        } catch (error) {
            console.error('Failed to regenerate:', error);
            alert('Failed to regenerate illustration. Please try again.');
            elements.promptRegenerate.disabled = false;
        }
    }

    function pollForRegeneratedIllustration(chapterId) {
        // Clear any existing polling
        if (state.promptModalPolling) {
            clearInterval(state.promptModalPolling);
        }

        let attempts = 0;
        const maxAttempts = 90; // 3 minutes at 2-second intervals

        state.promptModalPolling = setInterval(async () => {
            attempts++;

            // Check if modal was closed
            if (!isPromptModalVisible() || state.promptModalChapterId !== chapterId) {
                clearInterval(state.promptModalPolling);
                state.promptModalPolling = null;
                return;
            }

            if (attempts >= maxAttempts) {
                clearInterval(state.promptModalPolling);
                state.promptModalPolling = null;
                alert('Generation timed out. Please try again.');
                showPromptEditMode();
                elements.promptTextarea.value = state.promptModalLastPrompt;
                elements.promptTextarea.disabled = false;
                elements.promptRegenerate.disabled = false;
                return;
            }

            try {
                const response = await fetch(`/api/illustrations/chapter/${chapterId}/status`);
                const status = await response.json();

                if (status.ready) {
                    clearInterval(state.promptModalPolling);
                    state.promptModalPolling = null;
                    // Show preview with cache-busting timestamp
                    const imageUrl = `/api/illustrations/chapter/${chapterId}?t=${Date.now()}`;
                    showPromptPreviewMode(imageUrl);
                } else if (status.status === 'FAILED') {
                    clearInterval(state.promptModalPolling);
                    state.promptModalPolling = null;
                    alert('Generation failed. Please try again.');
                    showPromptEditMode();
                    elements.promptTextarea.value = state.promptModalLastPrompt;
                    elements.promptTextarea.disabled = false;
                    elements.promptRegenerate.disabled = false;
                }
                // Otherwise keep polling (PENDING or GENERATING)
            } catch (error) {
                console.error('Polling error:', error);
                // Continue polling on network errors
            }
        }, 2000);
    }

    function acceptRegeneration() {
        const chapterId = state.promptModalChapterId;
        if (!chapterId) return;

        // Update the main illustration display
        displayIllustration(chapterId);

        // Close the modal
        closePromptModal();
    }

    function tryAgainRegeneration() {
        // Switch back to edit mode with the last prompt
        showPromptEditMode();
        elements.promptTextarea.value = state.promptModalLastPrompt;
        elements.promptTextarea.disabled = false;
        elements.promptRegenerate.disabled = false;
    }

    // ========================================
    // End Illustration Mode Functions
    // ========================================

    // ========================================
    // Character Feature Functions
    // ========================================

    function getDiscoveredCharactersKey(bookId) {
        return STORAGE_KEYS.DISCOVERED_CHARACTERS_PREFIX + bookId;
    }

    function getDiscoveredCharacterDetailsKey(bookId) {
        return STORAGE_KEYS.DISCOVERED_CHARACTER_DETAILS_PREFIX + bookId;
    }

    function loadDiscoveredCharacters(bookId) {
        if (!bookId) return new Set();
        const stored = localStorage.getItem(getDiscoveredCharactersKey(bookId));
        if (!stored) return new Set();
        try {
            const parsed = JSON.parse(stored);
            if (Array.isArray(parsed)) {
                return new Set(parsed);
            }
        } catch (error) {
            console.debug('Failed to parse discovered characters', error);
        }
        return new Set();
    }

    function loadDiscoveredCharacterDetails(bookId) {
        if (!bookId) return new Map();
        const stored = localStorage.getItem(getDiscoveredCharacterDetailsKey(bookId));
        if (!stored) return new Map();
        try {
            const parsed = JSON.parse(stored);
            if (Array.isArray(parsed)) {
                const map = new Map();
                parsed.forEach(character => {
                    if (character && character.id) {
                        map.set(character.id, character);
                    }
                });
                return map;
            }
        } catch (error) {
            console.debug('Failed to parse discovered character details', error);
        }
        return new Map();
    }

    function saveDiscoveredCharacters() {
        if (!state.currentBook) return;
        const key = getDiscoveredCharactersKey(state.currentBook.id);
        localStorage.setItem(key, JSON.stringify(Array.from(state.discoveredCharacterIds)));
    }

    function saveDiscoveredCharacterDetails() {
        if (!state.currentBook) return;
        const key = getDiscoveredCharacterDetailsKey(state.currentBook.id);
        localStorage.setItem(key, JSON.stringify(Array.from(state.discoveredCharacterDetails.values())));
    }

    function recordDiscoveredCharacter(character) {
        if (!character || !character.id) return;
        state.discoveredCharacterIds.add(character.id);
        state.discoveredCharacterDetails.set(character.id, character);
    }

    function isCharacterWithinCurrentPosition(character) {
        if (!character) return false;
        if (character.firstChapterIndex < state.currentChapterIndex) {
            return true;
        }
        if (character.firstChapterIndex > state.currentChapterIndex) {
            return false;
        }
        return character.firstParagraphIndex <= state.currentParagraphIndex;
    }

    function sortCharacters(characters) {
        return characters.slice().sort((a, b) => {
            if (a.characterType !== b.characterType) {
                return a.characterType.localeCompare(b.characterType);
            }
            if (a.firstChapterIndex !== b.firstChapterIndex) {
                return a.firstChapterIndex - b.firstChapterIndex;
            }
            return a.firstParagraphIndex - b.firstParagraphIndex;
        });
    }

    function mergeCharacterLists(primaryList, secondaryList) {
        const byId = new Map();
        primaryList.forEach(character => byId.set(character.id, character));
        secondaryList.forEach(character => byId.set(character.id, character));
        return Array.from(byId.values());
    }

    function scheduleCharacterDiscoveryCheck() {
        if (!state.characterAvailable || !state.currentBook) return;
        if (state.characterDiscoveryTimeout) {
            clearTimeout(state.characterDiscoveryTimeout);
        }
        state.characterDiscoveryTimeout = setTimeout(() => {
            state.characterDiscoveryTimeout = null;
            checkForDiscoveredCharacters();
        }, 300);
    }

    async function checkForDiscoveredCharacters() {
        if (!state.currentBook || !state.characterAvailable) return;

        const chapterIndex = state.currentChapterIndex;
        const paragraphIndex = state.currentParagraphIndex;

        try {
            const response = await fetch(
                `/api/characters/book/${state.currentBook.id}/up-to?chapterIndex=${chapterIndex}&paragraphIndex=${paragraphIndex}`
            );
            const characters = await response.json();
            const newDiscoveries = characters.filter(c =>
                c.portraitReady && !state.discoveredCharacterIds.has(c.id)
            );

            if (newDiscoveries.length > 0) {
                newDiscoveries.forEach(recordDiscoveredCharacter);
                state.newCharacterQueue.push(...newDiscoveries);
                saveDiscoveredCharacters();
                saveDiscoveredCharacterDetails();
                if (!state.currentToastCharacter) {
                    showNextCharacterToast();
                }
            }
        } catch (error) {
            console.debug('Failed to check discovered characters:', error);
        }
    }

    async function characterCheckAvailability() {
        try {
            const response = await fetch('/api/characters/status');
            const status = await response.json();
            state.characterAvailable = status.enabled && isBookFeatureEnabled('characterEnabled');
            state.characterCacheOnly = status.cacheOnly === true;
            // Chat is available if the feature is enabled AND the chat provider is configured
            state.characterChatAvailable = state.characterAvailable
                && !state.characterCacheOnly
                && status.chatEnabled === true;
            state.cacheOnly = state.cacheOnly || status.cacheOnly === true;

            console.log('Character status response:', status);
            console.log('Character toggle element:', elements.characterToggle);

            if (elements.characterToggle) {
                elements.characterToggle.style.display = state.characterAvailable ? '' : 'none';
                console.log('Character toggle display set to:', elements.characterToggle.style.display);
            }
            if (elements.characterHint) {
                elements.characterHint.style.display = state.characterAvailable ? '' : 'none';
            }
            console.log('Character feature available:', state.characterAvailable);
        } catch (error) {
            console.error('Failed to check character availability:', error);
            state.characterAvailable = false;
            state.characterCacheOnly = false;
            state.characterChatAvailable = false;
        }

        if (!state.characterAvailable) {
            stopCharacterPolling();
            state.newCharacterQueue = [];
            state.currentToastCharacter = null;
        }
        // Chat unavailable if character feature is off, cache-only mode, or chat explicitly disabled
        if (!state.characterAvailable) {
            state.characterChatAvailable = false;
        }
        updateCacheOnlyIndicator();
    }

    async function recapCheckAvailability() {
        try {
            const statusUrl = state.currentBook?.id
                ? `/api/recaps/book/${state.currentBook.id}/status`
                : '/api/recaps/status';
            const response = await fetch(statusUrl, { cache: 'no-store' });
            const status = await response.json();
            state.recapGenerationAvailable = status.enabled === true
                && status.reasoningEnabled === true
                && status.cacheOnly !== true;
            state.recapAvailable = status.available === true;
            state.recapCacheOnly = status.cacheOnly === true;
            state.recapChatAvailable = state.recapAvailable &&
                status.chatProviderAvailable === true &&
                !state.recapCacheOnly;
            state.cacheOnly = state.cacheOnly || status.cacheOnly === true;
        } catch (error) {
            console.debug('Failed to check recap availability:', error);
            state.recapGenerationAvailable = false;
            state.recapAvailable = false;
            state.recapCacheOnly = false;
            state.recapChatAvailable = false;
        }
        updateRecapOptOutControl();
        updateCacheOnlyIndicator();
        setRecapChatControls();
    }

    async function quizCheckAvailability() {
        try {
            const statusUrl = state.currentBook?.id
                ? `/api/quizzes/book/${state.currentBook.id}/status`
                : '/api/quizzes/status';
            const response = await fetch(statusUrl, { cache: 'no-store' });
            const status = await response.json();
            state.quizCacheOnly = status.cacheOnly === true;
            state.quizAvailable = status.available === true;
            state.quizGenerationAvailable = status.generationAvailable === true
                || (status.enabled === true && status.reasoningEnabled === true && status.cacheOnly !== true);
            state.cacheOnly = state.cacheOnly || status.cacheOnly === true;
        } catch (error) {
            console.debug('Failed to check quiz availability:', error);
            state.quizAvailable = false;
            state.quizGenerationAvailable = false;
            state.quizCacheOnly = false;
        }
        updateRecapOptOutControl();
        updateCacheOnlyIndicator();
        setQuizControls();
    }

    function trackRecapAnalytics(eventType) {
        if (!state.currentBook?.id || !eventType) return;
        const currentChapter = state.chapters?.[state.currentChapterIndex];
        fetch('/api/recaps/analytics', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                bookId: state.currentBook.id,
                chapterId: currentChapter?.id || null,
                event: eventType
            })
        }).catch(error => {
            console.debug('Failed to track recap analytics event:', error);
        });
    }

    function getRecapOptOut(bookId) {
        if (!bookId) return false;
        return localStorage.getItem(`${STORAGE_KEYS.RECAP_OPTOUT_PREFIX}${bookId}`) === 'true';
    }

    function setRecapOptOut(bookId, enabled) {
        if (!bookId) return;
        localStorage.setItem(`${STORAGE_KEYS.RECAP_OPTOUT_PREFIX}${bookId}`, enabled ? 'true' : 'false');
        state.recapOptOut = enabled;
        updateRecapOptOutControl();
    }

    function updateRecapOptOutControl() {
        if (!elements.recapEnableBtn) return;
        const showControl = !!state.currentBook && (state.recapAvailable || state.quizAvailable) && state.recapOptOut;
        elements.recapEnableBtn.classList.toggle('hidden', !showControl);
    }

    function isChapterRecapVisible() {
        return elements.chapterRecapOverlay && !elements.chapterRecapOverlay.classList.contains('hidden');
    }

    function isCharacterBrowserVisible() {
        return elements.characterBrowserModal && !elements.characterBrowserModal.classList.contains('hidden');
    }

    function isCharacterChatVisible() {
        return elements.characterChatModal && !elements.characterChatModal.classList.contains('hidden');
    }

    async function loadChapterCharacters() {
        console.log('loadChapterCharacters called, available:', state.characterAvailable, 'book:', state.currentBook?.id);
        if (!state.characterAvailable || !state.currentBook) return;
        if (state.characterCacheOnly) return;

        const chapter = state.chapters[state.currentChapterIndex];
        if (!chapter) return;

        console.log('Requesting character analysis for chapter:', chapter.id);
        try {
            // Request character analysis for current chapter
            await fetch(`/api/characters/chapter/${chapter.id}/analyze`, { method: 'POST' });

            // Prefetch next chapter analysis
            fetch(`/api/characters/chapter/${chapter.id}/prefetch-next`, { method: 'POST' });

            // Start polling for new characters
            startCharacterPolling();
        } catch (error) {
            console.error('Failed to request character analysis:', error);
        }
    }

    async function requestChapterRecapGeneration(chapterId) {
        if (!chapterId || !state.recapGenerationAvailable || state.cacheOnly || state.recapCacheOnly) return;
        try {
            await fetch(`/api/recaps/chapter/${chapterId}/generate`, { method: 'POST' });
        } catch (error) {
            console.debug('Failed to request chapter recap generation:', error);
        }
    }

    async function requestChapterQuizGeneration(chapterId) {
        if (!chapterId || !state.quizGenerationAvailable || state.cacheOnly || state.quizCacheOnly) return;
        try {
            await fetch(`/api/quizzes/chapter/${chapterId}/generate`, { method: 'POST' });
        } catch (error) {
            console.debug('Failed to request chapter quiz generation:', error);
        }
    }

    function startCharacterPolling() {
        // Clear existing polling
        if (state.characterPollingInterval) {
            clearInterval(state.characterPollingInterval);
        }

        state.characterLastCheck = Date.now();

        // Poll every 3 seconds for new characters
        state.characterPollingInterval = setInterval(async () => {
            if (!state.currentBook) {
                clearInterval(state.characterPollingInterval);
                return;
            }

            try {
                const response = await fetch(
                    `/api/characters/book/${state.currentBook.id}/new-since?sinceTimestamp=${state.characterLastCheck}`
                );
                const newCharacters = await response.json();

                if (newCharacters.length > 0) {
                    state.characterLastCheck = Date.now();
                }

                const filtered = newCharacters.filter(c =>
                    c.portraitReady &&
                    isCharacterWithinCurrentPosition(c) &&
                    !state.discoveredCharacterIds.has(c.id)
                );

                if (filtered.length > 0) {
                    // Add to queue
                    filtered.forEach(recordDiscoveredCharacter);
                    state.newCharacterQueue.push(...filtered);
                    saveDiscoveredCharacters();
                    saveDiscoveredCharacterDetails();
                    // Show next toast if not already showing one
                    if (!state.currentToastCharacter) {
                        showNextCharacterToast();
                    }
                }
            } catch (error) {
                console.debug('Character poll failed:', error);
            }
        }, 3000);
    }

    function stopCharacterPolling() {
        if (state.characterPollingInterval) {
            clearInterval(state.characterPollingInterval);
            state.characterPollingInterval = null;
        }
    }

    function showNextCharacterToast() {
        if (state.newCharacterQueue.length === 0) {
            state.currentToastCharacter = null;
            return;
        }

        const character = state.newCharacterQueue.shift();
        state.currentToastCharacter = character;

        elements.characterToastName.textContent = character.name;
        elements.characterToastDesc.textContent = character.description || '';
        elements.characterToastImage.src = `/api/characters/${character.id}/portrait?t=${Date.now()}`;
        elements.characterToast.classList.remove('hidden', 'fade-out');

        // Auto-dismiss after 8 seconds
        setTimeout(() => {
            dismissCharacterToast();
        }, 8000);
    }

    function dismissCharacterToast() {
        elements.characterToast.classList.add('fade-out');
        setTimeout(() => {
            elements.characterToast.classList.add('hidden');
            elements.characterToast.classList.remove('fade-out');
            state.currentToastCharacter = null;
            // Show next if queued
            showNextCharacterToast();
        }, 400);
    }

    function characterBrowserToggle() {
        console.log('characterBrowserToggle called, available:', state.characterAvailable);
        if (!state.characterAvailable) {
            console.log('Character feature not available, returning');
            return;
        }

        if (isCharacterBrowserVisible()) {
            closeCharacterBrowser();
        } else {
            openCharacterBrowser();
        }
    }

    async function openCharacterBrowser() {
        if (!state.currentBook) return;

        ttsPauseForModal();
        state.characterBrowserOpen = true;

        // Load characters up to current reading position
        const chapterIndex = state.currentChapterIndex;
        const paragraphIndex = state.currentParagraphIndex;

        try {
            const response = await fetch(
                `/api/characters/book/${state.currentBook.id}/up-to?chapterIndex=${chapterIndex}&paragraphIndex=${paragraphIndex}`
            );
            const upToCharacters = await response.json();
            let mergedCharacters = upToCharacters;

            if (state.discoveredCharacterIds.size > 0) {
                const cachedDiscovered = Array.from(state.discoveredCharacterDetails.values());
                const cachedIds = new Set(cachedDiscovered.map(c => c.id));
                const missingIds = Array.from(state.discoveredCharacterIds).filter(id => !cachedIds.has(id));
                let discoveredCharacters = cachedDiscovered;

                if (missingIds.length > 0) {
                    const allResponse = await fetch(`/api/characters/book/${state.currentBook.id}`);
                    const allCharacters = await allResponse.json();
                    const missingCharacters = allCharacters.filter(c => missingIds.includes(c.id));
                    missingCharacters.forEach(recordDiscoveredCharacter);
                    saveDiscoveredCharacterDetails();
                    discoveredCharacters = mergeCharacterLists(cachedDiscovered, missingCharacters);
                }

                mergedCharacters = mergeCharacterLists(upToCharacters, discoveredCharacters);
            }

            state.characters = sortCharacters(mergedCharacters);
        } catch (error) {
            console.error('Failed to load characters:', error);
            state.characters = [];
        }

        showCharacterListView();
        elements.characterBrowserModal.classList.remove('hidden');
    }

    function openCharacterBrowserToCharacter(characterId) {
        if (!characterId) return;
        dismissCharacterToast();
        openCharacterBrowser().then(() => {
            showCharacterDetail(characterId);
        });
    }

    function closeCharacterBrowser(skipAudioResume = false) {
        elements.characterBrowserModal.classList.add('hidden');
        state.characterBrowserOpen = false;
        state.selectedCharacterId = null;
        if (!skipAudioResume) {
            ttsResumeAfterModal();
        }
    }

    function showCharacterListView() {
        elements.characterDetailView.classList.add('hidden');
        elements.characterListView.classList.remove('hidden');
        renderCharacterList();
    }

    function renderCharacterCard(char, isLarge) {
        const sizeClass = isLarge ? 'character-card-large' : 'character-card-small';
        const iconSize = isLarge ? 32 : 24;
        return `
            <div class="character-card ${sizeClass}" data-character-id="${char.id}">
                <div class="character-card-portrait ${char.portraitReady ? '' : 'pending'}">
                    ${char.portraitReady
                        ? `<img src="/api/characters/${char.id}/portrait" alt="${char.name}" />`
                        : `<svg width="${iconSize}" height="${iconSize}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                            <circle cx="12" cy="7" r="4"/>
                          </svg>`
                    }
                </div>
                <div class="character-card-name">${char.name}</div>
            </div>
        `;
    }

    function renderCharacterList() {
        if (state.characters.length === 0) {
            elements.characterListEmpty.classList.remove('hidden');
            elements.characterList.innerHTML = '';
            return;
        }

        elements.characterListEmpty.classList.add('hidden');

        // Separate characters by type - primary first, then secondary
        const primaryCharacters = state.characters.filter(c => c.characterType === 'PRIMARY');
        const secondaryCharacters = state.characters.filter(c => c.characterType === 'SECONDARY');

        // Single flowing layout: primary (large) cards first, then secondary (small) cards
        const html = `<div class="character-flow">
            ${primaryCharacters.map(char => renderCharacterCard(char, true)).join('')}
            ${secondaryCharacters.map(char => renderCharacterCard(char, false)).join('')}
        </div>`;

        elements.characterList.innerHTML = html;

        // Add click handlers
        elements.characterList.querySelectorAll('.character-card').forEach(card => {
            card.addEventListener('click', () => {
                showCharacterDetail(card.dataset.characterId);
            });
        });
    }

    function showCharacterDetail(characterId) {
        const character = state.characters.find(c => c.id === characterId);
        if (!character) return;

        state.selectedCharacterId = characterId;

        elements.characterDetailName.textContent = character.name;
        elements.characterDetailDesc.textContent = character.description || '';
        elements.characterDetailChapter.textContent = character.firstChapterTitle || `Chapter ${character.firstChapterIndex + 1}`;

        if (character.portraitReady) {
            elements.characterDetailPortrait.src = `/api/characters/${characterId}/portrait?t=${Date.now()}`;
        } else {
            elements.characterDetailPortrait.src = '';
        }

        // Show/hide chat button based on character type (only PRIMARY can chat)
        if (elements.characterChatBtn) {
            if (character.characterType === 'PRIMARY' && state.characterChatAvailable) {
                elements.characterChatBtn.classList.remove('hidden');
            } else {
                elements.characterChatBtn.classList.add('hidden');
            }
        }

        elements.characterListView.classList.add('hidden');
        elements.characterDetailView.classList.remove('hidden');
    }

    function navigateToCharacterAppearance() {
        const character = state.characters.find(c => c.id === state.selectedCharacterId);
        if (!character) return;

        closeCharacterBrowser();

        // Find chapter by id
        const chapterIndex = state.chapters.findIndex(c => c.id === character.firstChapterId);
        if (chapterIndex >= 0) {
            loadChapter(chapterIndex, character.firstParagraphIndex);
        }
    }

    async function openCharacterChat(characterId) {
        if (!characterId) return;
        if (!state.characterChatAvailable) return;

        const character = state.characters.find(c => c.id === characterId);
        if (!character) return;

        state.chatCharacterId = characterId;
        state.chatCharacter = character;

        // Load chat history from localStorage
        state.chatHistory = loadChatHistory(characterId);

        // Update UI
        elements.chatCharacterName.textContent = character.name;
        if (character.portraitReady) {
            elements.chatCharacterPortrait.src = `/api/characters/${characterId}/portrait`;
        }

        // Render existing messages
        renderChatMessages();

        // Close browser, open chat (skip audio resume since chat modal keeps it paused)
        closeCharacterBrowser(true);
        elements.characterChatModal.classList.remove('hidden');
        state.characterChatOpen = true;

        // Focus input
        elements.chatInput.focus();
    }

    function closeCharacterChat() {
        elements.characterChatModal.classList.add('hidden');
        state.characterChatOpen = false;
        state.chatCharacterId = null;
        state.chatCharacter = null;
        state.chatHistory = [];
        elements.chatMessages.innerHTML = '';
        elements.chatInput.value = '';
        ttsResumeAfterModal();
    }

    function loadChatHistory(characterId) {
        const key = STORAGE_KEYS.CHARACTER_CHAT_PREFIX + state.currentBook.id + '_' + characterId;
        const stored = localStorage.getItem(key);
        return stored ? JSON.parse(stored) : [];
    }

    function saveChatHistory(characterId, history) {
        const key = STORAGE_KEYS.CHARACTER_CHAT_PREFIX + state.currentBook.id + '_' + characterId;
        // Limit history to last 50 messages
        const limited = history.slice(-50);
        localStorage.setItem(key, JSON.stringify(limited));
    }

    function renderChatMessages() {
        elements.chatMessages.innerHTML = state.chatHistory.map(msg => `
            <div class="chat-message ${msg.role}">
                ${escapeHtml(msg.content)}
            </div>
        `).join('');

        // Scroll to bottom
        elements.chatMessages.scrollTop = elements.chatMessages.scrollHeight;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    async function sendChatMessage() {
        const message = elements.chatInput.value.trim();
        if (!message || state.chatLoading || !state.chatCharacterId) return;

        // Add user message to history
        const userMsg = { role: 'user', content: message, timestamp: Date.now() };
        state.chatHistory.push(userMsg);
        renderChatMessages();

        // Clear input
        elements.chatInput.value = '';

        // Show loading
        state.chatLoading = true;
        elements.chatSendBtn.disabled = true;

        // Add loading message
        const loadingDiv = document.createElement('div');
        loadingDiv.className = 'chat-message character loading';
        loadingDiv.textContent = 'Thinking';
        elements.chatMessages.appendChild(loadingDiv);
        elements.chatMessages.scrollTop = elements.chatMessages.scrollHeight;

        try {
            const response = await fetch(`/api/characters/${state.chatCharacterId}/chat`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    message: message,
                    conversationHistory: state.chatHistory.slice(-10),
                    readerChapterIndex: state.currentChapterIndex,
                    readerParagraphIndex: state.currentParagraphIndex
                })
            });

            const data = await response.json();

            // Remove loading message
            loadingDiv.remove();

            // Add character response
            const charMsg = { role: 'character', content: data.response, timestamp: Date.now() };
            state.chatHistory.push(charMsg);
            renderChatMessages();

            // Save history
            saveChatHistory(state.chatCharacterId, state.chatHistory);

        } catch (error) {
            console.error('Chat failed:', error);
            loadingDiv.remove();

            // Add error message
            const errorMsg = { role: 'character', content: "I... I'm not sure how to answer that.", timestamp: Date.now() };
            state.chatHistory.push(errorMsg);
            renderChatMessages();
        } finally {
            state.chatLoading = false;
            elements.chatSendBtn.disabled = false;
            elements.chatInput.focus();
        }
    }

    // ========================================
    // End Character Feature Functions
    // ========================================

    // Import a book from Gutenberg and open it
    async function importAndOpenBook(gutenbergId) {
        if (state.isImporting) return;

        state.isImporting = true;
        showImportingOverlay();

        try {
            const response = await fetch(`/api/import/gutenberg/${gutenbergId}`, {
                method: 'POST'
            });
            const result = await response.json();

            if (result.success || result.message === 'Book already imported') {
                // Fetch the full book from library
                const bookResponse = await fetch(`/api/library/${result.bookId}`);
                const book = await bookResponse.json();

                // Update local books list
                if (!state.localBooks.find(b => b.id === book.id)) {
                    state.localBooks.push(book);
                }

                // Mark as imported in catalog
                const catalogBook = state.catalogBooks.find(b => b.gutenbergId === gutenbergId);
                if (catalogBook) {
                    catalogBook.alreadyImported = true;
                }

                hideImportingOverlay();
                await selectBook(book);
            } else {
                hideImportingOverlay();
                alert('Failed to import book: ' + result.message);
            }
        } catch (error) {
            console.error('Import failed:', error);
            hideImportingOverlay();
            alert('Failed to import book. Please try again.');
        } finally {
            state.isImporting = false;
        }
    }

    // Show/hide importing overlay
    function showImportingOverlay() {
        let overlay = document.getElementById('importing-overlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'importing-overlay';
            overlay.innerHTML = '<div class="importing-message">Importing book...</div>';
            document.body.appendChild(overlay);
        }
        overlay.classList.add('visible');
    }

    function hideImportingOverlay() {
        const overlay = document.getElementById('importing-overlay');
        if (overlay) {
            overlay.classList.remove('visible');
        }
    }

    // Event listeners
    function setupEventListeners() {
        // Library search - search the Gutenberg catalog
        elements.librarySearch.addEventListener('input', (e) => {
            clearTimeout(catalogSearchTimeout);
            catalogSearchTimeout = setTimeout(() => {
                searchCatalog(e.target.value);
            }, 300);
        });

        // Recently read book selection (local books)
        elements.recentlyReadList.addEventListener('click', async (e) => {
            const bookItem = e.target.closest('.book-item');
            if (bookItem) {
                const bookId = bookItem.dataset.bookId;
                const book = state.localBooks.find(b => b.id === bookId);
                if (book) {
                    await selectBook(book);
                }
            }
        });

        // Catalog book selection (may need import)
        elements.bookList.addEventListener('click', async (e) => {
            const bookItem = e.target.closest('.book-item');
            if (bookItem && bookItem.dataset.gutenbergId) {
                const gutenbergId = parseInt(bookItem.dataset.gutenbergId);
                const catalogBook = state.catalogBooks.find(b => b.gutenbergId === gutenbergId);

                if (catalogBook && catalogBook.alreadyImported) {
                    // Find local book by matching - need to fetch by source
                    const localBook = state.localBooks.find(b =>
                        b.title === catalogBook.title && b.author === catalogBook.author
                    );
                    if (localBook) {
                        await selectBook(localBook);
                        return;
                    }
                }

                // Import the book first
                await importAndOpenBook(gutenbergId);
            }
        });

        // Back to library
        elements.backToLibrary.addEventListener('click', backToLibrary);

        // Paragraph click navigation
        if (elements.readerContent) {
            elements.readerContent.addEventListener('click', (e) => {
                const paragraph = e.target.closest('.paragraph');
                if (!paragraph || state.speedReadingActive) return;
                if (elements.readerView.classList.contains('hidden')) return;

                const index = parseInt(paragraph.dataset.index, 10);
                if (Number.isNaN(index) || index === state.currentParagraphIndex) return;

                state.currentParagraphIndex = index;
                renderPage();
                ttsInterrupt();
            });
        }

        // Page gutters
        if (elements.gutterLeft) {
            elements.gutterLeft.addEventListener('click', () => {
                if (!elements.readerView.classList.contains('hidden') && !state.speedReadingActive) {
                    prevPage();
                    ttsInterrupt();
                }
            });
        }
        if (elements.gutterRight) {
            elements.gutterRight.addEventListener('click', () => {
                if (!elements.readerView.classList.contains('hidden') && !state.speedReadingActive) {
                    nextPage();
                    ttsInterrupt();
                }
            });
        }

        // TTS toggle
        if (elements.ttsToggle) {
            elements.ttsToggle.addEventListener('click', ttsToggle);
        }

        // Speed reading toggle
        if (state.speedReadingEnabled) {
            if (elements.speedReadingToggle) {
                elements.speedReadingToggle.addEventListener('click', speedReadingToggle);
            }
            if (elements.speedReadingPlay) {
                elements.speedReadingPlay.addEventListener('click', () => {
                    if (state.speedReadingPlaying) {
                        speedReadingPause();
                    } else {
                        speedReadingStart();
                    }
                });
            }
            if (elements.speedReadingExitInline) {
                elements.speedReadingExitInline.addEventListener('click', () => exitSpeedReading());
            }
            if (elements.speedReadingSlider) {
                elements.speedReadingSlider.addEventListener('input', (e) => {
                    const value = parseInt(e.target.value, 10);
                    if (!Number.isNaN(value)) {
                        state.speedReadingWpm = value;
                        localStorage.setItem(STORAGE_KEYS.SPEED_READING_WPM, value);
                        updateSpeedReadingControls();
                        if (state.speedReadingActive && state.speedReadingPlaying) {
                            clearSpeedReadingTimer();
                            speedReadingStep();
                        }
                    }
                });
            }
            if (elements.speedReadingContinue) {
                elements.speedReadingContinue.addEventListener('click', continueSpeedReading);
            }
            if (elements.speedReadingExit) {
                elements.speedReadingExit.addEventListener('click', () => exitSpeedReading());
            }
        }

        if (elements.chapterRecapClose) {
            elements.chapterRecapClose.addEventListener('click', () => closeChapterRecapOverlay(true));
        }
        if (elements.chapterRecapBackdrop) {
            elements.chapterRecapBackdrop.addEventListener('click', () => closeChapterRecapOverlay(true));
        }
        if (elements.chapterRecapSkip) {
            elements.chapterRecapSkip.addEventListener('click', skipChapterRecap);
        }
        if (elements.chapterRecapContinue) {
            elements.chapterRecapContinue.addEventListener('click', () => continueFromChapterRecap('continued'));
        }
        if (elements.chapterRecapTabRecap) {
            elements.chapterRecapTabRecap.addEventListener('click', () => setChapterRecapTab('recap'));
        }
        if (elements.chapterRecapTabChat) {
            elements.chapterRecapTabChat.addEventListener('click', () => {
                setChapterRecapTab('chat');
                if (state.recapChatAvailable && !state.recapChatLoading) {
                    elements.chapterRecapChatInput?.focus();
                }
            });
        }
        if (elements.chapterRecapTabQuiz) {
            elements.chapterRecapTabQuiz.addEventListener('click', () => setChapterRecapTab('quiz'));
        }
        if (elements.chapterRecapChatSend) {
            elements.chapterRecapChatSend.addEventListener('click', sendRecapChatMessage);
        }
        if (elements.chapterQuizSubmit) {
            elements.chapterQuizSubmit.addEventListener('click', submitChapterQuiz);
        }
        if (elements.chapterRecapChatInput) {
            elements.chapterRecapChatInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    e.stopPropagation();
                    sendRecapChatMessage();
                }
            });
        }
        if (elements.chapterRecapOptout) {
            elements.chapterRecapOptout.addEventListener('change', (e) => {
                setRecapOptOut(state.currentBook?.id, !!e.target.checked);
            });
        }
        if (elements.recapEnableBtn) {
            elements.recapEnableBtn.addEventListener('click', () => {
                if (!state.currentBook?.id) return;
                setRecapOptOut(state.currentBook.id, false);
                if (elements.chapterRecapOptout) {
                    elements.chapterRecapOptout.checked = false;
                }
            });
        }

        // Illustration toggle
        if (elements.illustrationToggle) {
            elements.illustrationToggle.addEventListener('click', illustrationToggle);
        }

        // Illustration image click (opens prompt modal)
        if (elements.illustrationImage) {
            elements.illustrationImage.addEventListener('click', () => {
                if (state.allowPromptEditing && elements.illustrationImage.classList.contains('editable')) {
                    openPromptModal();
                }
            });
        }

        // Prompt modal event listeners
        if (elements.promptModalClose) {
            elements.promptModalClose.addEventListener('click', closePromptModal);
        }
        if (elements.promptCancel) {
            elements.promptCancel.addEventListener('click', closePromptModal);
        }
        if (elements.promptRegenerate) {
            elements.promptRegenerate.addEventListener('click', regenerateIllustration);
        }
        if (elements.promptModalBackdrop) {
            elements.promptModalBackdrop.addEventListener('click', closePromptModal);
        }
        if (elements.promptTryAgain) {
            elements.promptTryAgain.addEventListener('click', tryAgainRegeneration);
        }
        if (elements.promptAccept) {
            elements.promptAccept.addEventListener('click', acceptRegeneration);
        }

        // Search input
        elements.searchInput.addEventListener('input', (e) => {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                performSearch(e.target.value);
            }, 300);
        });

        elements.searchInput.addEventListener('focus', () => {
            ttsPauseForModal();
        });

        elements.searchInput.addEventListener('blur', () => {
            ttsResumeAfterModal();
        });

        elements.searchInput.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                e.stopPropagation();
                elements.searchResults.classList.add('hidden');
                elements.searchInput.blur();
            }
        });

        // Search results click
        elements.searchResults.addEventListener('click', (e) => {
            const resultItem = e.target.closest('.search-result-item');
            if (resultItem && resultItem.dataset.chapterId) {
                const chapterId = resultItem.dataset.chapterId;
                const paragraphIndex = parseInt(resultItem.dataset.paragraphIndex) || 0;
                navigateToSearchResult(chapterId, paragraphIndex);
            }
        });

        // Chapter list click handler
        elements.chapterList.addEventListener('click', (e) => {
            const item = e.target.closest('.chapter-list-item');
            if (item) {
                const index = parseInt(item.dataset.chapterIndex);
                selectChapterFromList(index);
            }
        });

        // Close chapter list when clicking overlay background
        elements.chapterListOverlay.addEventListener('click', (e) => {
            if (e.target === elements.chapterListOverlay) {
                hideChapterList();
            }
        });

        // Character feature event listeners
        if (elements.characterToggle) {
            elements.characterToggle.addEventListener('click', characterBrowserToggle);
        }
        if (elements.characterToastClose) {
            elements.characterToastClose.addEventListener('click', dismissCharacterToast);
        }
        if (elements.characterToast) {
            elements.characterToast.addEventListener('click', (e) => {
                if (e.target !== elements.characterToastClose) {
                    openCharacterBrowserToCharacter(state.currentToastCharacter?.id);
                }
            });
        }
        if (elements.characterBrowserClose) {
            elements.characterBrowserClose.addEventListener('click', closeCharacterBrowser);
        }
        if (elements.characterBrowserModal) {
            elements.characterBrowserModal.querySelector('.character-modal-backdrop')?.addEventListener('click', closeCharacterBrowser);
        }
        if (elements.characterBackBtn) {
            elements.characterBackBtn.addEventListener('click', showCharacterListView);
        }
        if (elements.characterChatBtn) {
            elements.characterChatBtn.addEventListener('click', () => openCharacterChat(state.selectedCharacterId));
        }
        if (elements.characterDetailLink) {
            elements.characterDetailLink.addEventListener('click', (e) => {
                e.preventDefault();
                navigateToCharacterAppearance();
            });
        }
        if (elements.characterChatClose) {
            elements.characterChatClose.addEventListener('click', closeCharacterChat);
        }
        if (elements.characterChatModal) {
            elements.characterChatModal.querySelector('.character-modal-backdrop')?.addEventListener('click', closeCharacterChat);
        }
        if (elements.chatSendBtn) {
            elements.chatSendBtn.addEventListener('click', sendChatMessage);
        }

        // Keyboard navigation
        document.addEventListener('keydown', (e) => {
            // Handle prompt modal keyboard
            if (isPromptModalVisible()) {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    closePromptModal();
                }
                // Don't process other shortcuts when modal is open
                return;
            }

            if (isChapterRecapVisible()) {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    closeChapterRecapOverlay(true);
                } else if (e.key === 'Enter'
                    && state.recapActiveTab === 'recap'
                    && document.activeElement !== elements.chapterRecapChatInput
                    && document.activeElement !== elements.chapterRecapTabRecap
                    && document.activeElement !== elements.chapterRecapTabChat
                    && document.activeElement !== elements.chapterRecapTabQuiz) {
                    e.preventDefault();
                    continueFromChapterRecap('continued');
                }
                return;
            }

            // Handle character browser modal keyboard
            if (isCharacterBrowserVisible()) {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    closeCharacterBrowser();
                }
                return;
            }

            // Handle character chat modal keyboard
            if (isCharacterChatVisible()) {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    closeCharacterChat();
                }
                // Allow Enter to send chat if not shift+enter
                if (e.key === 'Enter' && !e.shiftKey && document.activeElement === elements.chatInput) {
                    e.preventDefault();
                    sendChatMessage();
                }
                return;
            }

            // Skip if typing in search
            if (document.activeElement === elements.searchInput) {
                return;
            }

            // Skip if not in reader view
            if (elements.readerView.classList.contains('hidden')) {
                return;
            }

            if (state.speedReadingEnabled && state.speedReadingActive) {
                if (!elements.speedReadingChapterOverlay.classList.contains('hidden')) {
                    if (e.key === 'Escape') {
                        e.preventDefault();
                        exitSpeedReading();
                    } else if (e.key === 'Enter') {
                        e.preventDefault();
                        continueSpeedReading();
                    }
                    return;
                }

                if (e.key === 'Escape') {
                    e.preventDefault();
                    exitSpeedReading();
                    return;
                }
                if (e.key === 'r') {
                    e.preventDefault();
                    speedReadingToggle();
                    return;
                }
                if (e.key === ' ') {
                    e.preventDefault();
                    if (state.speedReadingPlaying) {
                        speedReadingPause();
                    } else {
                        speedReadingStart();
                    }
                    return;
                }
                return;
            }

            // Handle chapter list keyboard navigation
            if (isChapterListVisible()) {
                switch (e.key) {
                    case 'ArrowDown':
                    case 'j':
                        e.preventDefault();
                        chapterListNavigate(1);
                        break;
                    case 'ArrowUp':
                    case 'k':
                        e.preventDefault();
                        chapterListNavigate(-1);
                        break;
                    case 'Enter':
                        e.preventDefault();
                        selectChapterFromList(chapterListSelectedIndex);
                        break;
                    case 'Escape':
                        e.preventDefault();
                        hideChapterList();
                        break;
                }
                return;
            }

            // Allow native shortcuts (Cmd-C, Ctrl-V, etc.)
            if (e.metaKey || e.ctrlKey) {
                return;
            }

            switch (e.key) {
                case '/':
                    e.preventDefault();
                    elements.searchInput.focus();
                    break;
                case 'c':
                    e.preventDefault();
                    showChapterList();
                    break;
                case 'j':
                    e.preventDefault();
                    nextParagraph();
                    ttsInterrupt();
                    break;
                case 'k':
                    e.preventDefault();
                    prevParagraph();
                    ttsInterrupt();
                    break;
                case 'l':
                    e.preventDefault();
                    nextPage();
                    ttsInterrupt();
                    break;
                case 'h':
                    e.preventDefault();
                    prevPage();
                    ttsInterrupt();
                    break;
                case 'L':
                    e.preventDefault();
                    if (state.ttsEnabled) speechSynthesis.cancel();
                    nextChapter();
                    break;
                case 'H':
                    e.preventDefault();
                    if (state.ttsEnabled) speechSynthesis.cancel();
                    prevChapter();
                    break;
                case 'p':
                    e.preventDefault();
                    ttsToggle();
                    break;
                case 's':
                    e.preventDefault();
                    ttsCycleSpeed();
                    break;
                case 'r':
                    if (state.speedReadingEnabled) {
                        e.preventDefault();
                        speedReadingToggle();
                    }
                    break;
                case 'i':
                    e.preventDefault();
                    illustrationToggle();
                    break;
                case 'm':
                    console.log('M key pressed');
                    e.preventDefault();
                    characterBrowserToggle();
                    break;
                case 'Escape':
                    backToLibrary();
                    break;
            }
        });

        // Handle window resize
        let resizeTimeout;
        window.addEventListener('resize', () => {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(() => {
                if (!elements.readerView.classList.contains('hidden')) {
                    calculatePages();
                    // Ensure current page is still valid
                    state.currentPage = Math.min(state.currentPage, state.totalPages - 1);
                    state.currentPage = Math.max(0, state.currentPage);
                    renderPage();
                }
            }, 250);
        });
    }

    // Start
    document.addEventListener('DOMContentLoaded', init);
})();
