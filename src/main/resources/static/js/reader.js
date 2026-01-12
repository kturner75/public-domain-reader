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
        ttsBrowserAvailable: false,
        ttsUsingBrowser: false,  // true when currently using browser fallback
        ttsPlaybackRate: 1.0  // 1.0, 1.25, 1.5, 1.75, 2.0
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
        chapterTitle: document.getElementById('chapter-title'),
        columnLeft: document.getElementById('column-left'),
        columnRight: document.getElementById('column-right'),
        pageIndicator: document.getElementById('page-indicator'),
        backToLibrary: document.getElementById('back-to-library'),
        searchInput: document.getElementById('search-input'),
        searchResults: document.getElementById('search-results'),
        chapterListOverlay: document.getElementById('chapter-list-overlay'),
        chapterList: document.getElementById('chapter-list'),
        ttsToggle: document.getElementById('tts-toggle'),
        ttsSpeed: document.getElementById('tts-speed'),
        ttsMode: document.getElementById('tts-mode')
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
        TTS_SPEED: 'reader_ttsSpeed'
    };

    const MAX_RECENTLY_READ = 5;

    // Initialize
    async function init() {
        await loadLibrary();
        setupEventListeners();
        await ttsCheckAvailability();

        // Load saved TTS speed preference
        const savedSpeed = parseFloat(localStorage.getItem(STORAGE_KEYS.TTS_SPEED));
        if (savedSpeed && [1.0, 1.25, 1.5, 1.75, 2.0].includes(savedSpeed)) {
            state.ttsPlaybackRate = savedSpeed;
        }

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

    // Render a book item (local book with id)
    function renderLocalBookItem(book) {
        return `
            <div class="book-item" data-book-id="${book.id}">
                <div class="book-item-title">${book.title}</div>
                <div class="book-item-author">${book.author}</div>
            </div>
        `;
    }

    // Render a catalog book item (from Gutenberg)
    function renderCatalogBookItem(book) {
        const importedClass = book.alreadyImported ? ' imported' : '';
        const importedBadge = book.alreadyImported ? '<span class="imported-badge">✓</span>' : '';
        return `
            <div class="book-item catalog-book${importedClass}" data-gutenberg-id="${book.gutenbergId}">
                <div class="book-item-title">${book.title}${importedBadge}</div>
                <div class="book-item-author">${book.author}</div>
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

        // Save to localStorage and recently read
        localStorage.setItem(STORAGE_KEYS.LAST_BOOK, book.id);
        addToRecentlyRead(book.id);

        // Switch to reader view
        elements.libraryView.classList.add('hidden');
        elements.readerView.classList.remove('hidden');

        // Update title
        elements.bookTitle.textContent = book.title;

        // Load chapter
        await loadChapter(chapterIndex, pageIndex, paragraphIndex);

        // Analyze book for voice settings (async, don't block)
        if (state.ttsAvailable && !state.ttsVoiceSettings) {
            ttsAnalyzeBook();
        }
    }

    // Load chapter content
    async function loadChapter(chapterIndex, pageIndex = 0, paragraphIndex = 0) {
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
            if (state.ttsEnabled) {
                ttsSpeakCurrent();
            }
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
                if (columnCount === 0) {
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

            if (!inRightColumn && currentHeight + paraHeight > columnHeight) {
                inRightColumn = true;
                currentHeight = 0;
            }

            if (inRightColumn) {
                rightHtml += paraHtml;
            } else {
                leftHtml += paraHtml;
            }
            currentHeight += paraHeight;
        }

        document.body.removeChild(measureContainer);

        elements.columnLeft.innerHTML = leftHtml || '';
        elements.columnRight.innerHTML = rightHtml || '';

        // Update page indicator
        elements.pageIndicator.textContent = `Page ${state.currentPage + 1} of ${state.totalPages}`;

        // Ensure current paragraph is valid
        if (state.currentParagraphIndex < pageData.startParagraph || state.currentParagraphIndex > pageData.endParagraph) {
            state.currentParagraphIndex = pageData.startParagraph;
        }
    }

    // Navigation functions
    function nextPage() {
        if (state.currentPage < state.totalPages - 1) {
            state.currentPage++;
            state.currentParagraphIndex = state.pagesData[state.currentPage].startParagraph;
            renderPage();
        } else if (state.currentChapterIndex < state.chapters.length - 1) {
            // Go to next chapter
            loadChapter(state.currentChapterIndex + 1, 0);
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
            // Go to next chapter
            loadChapter(state.currentChapterIndex + 1, 0).then(() => {
                state.currentParagraphIndex = 0;
            });
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
            loadChapter(state.currentChapterIndex + 1, 0);
        }
    }

    function prevChapter() {
        if (state.currentChapterIndex > 0) {
            loadChapter(state.currentChapterIndex - 1, 0);
        }
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

        loadChapter(chapterIndex, 0).then(() => {
            // Find which page contains this paragraph
            for (let i = 0; i < state.pagesData.length; i++) {
                const pageData = state.pagesData[i];
                if (paragraphIndex >= pageData.startParagraph && paragraphIndex <= pageData.endParagraph) {
                    state.currentPage = i;
                    state.currentParagraphIndex = paragraphIndex;
                    renderPage();
                    break;
                }
            }
        });
    }

    // Back to library
    function backToLibrary() {
        ttsStop();
        state.ttsVoiceSettings = null;  // Clear voice settings for next book
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
        chapterListSelectedIndex = state.currentChapterIndex;
        renderChapterList();
        elements.chapterListOverlay.classList.remove('hidden');
        scrollChapterIntoView(chapterListSelectedIndex);
    }

    function hideChapterList() {
        elements.chapterListOverlay.classList.add('hidden');
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
        // Check browser speech synthesis support
        state.ttsBrowserAvailable = 'speechSynthesis' in window;

        // Check OpenAI TTS availability
        try {
            const response = await fetch('/api/tts/status');
            const status = await response.json();
            state.ttsOpenAIAvailable = status.openaiConfigured;
        } catch (error) {
            console.warn('OpenAI TTS not available:', error);
            state.ttsOpenAIAvailable = false;
        }

        // TTS is available if either OpenAI or browser is available
        state.ttsAvailable = state.ttsOpenAIAvailable || state.ttsBrowserAvailable;

        if (elements.ttsToggle) {
            elements.ttsToggle.style.display = state.ttsAvailable ? '' : 'none';
        }

        console.log('TTS availability:', {
            openai: state.ttsOpenAIAvailable,
            browser: state.ttsBrowserAvailable,
            available: state.ttsAvailable
        });

        return {
            openaiConfigured: state.ttsOpenAIAvailable,
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
                return; // Don't show notification for saved settings
            }

            // No saved settings (204 or other), analyze with LLM
            console.log('No saved settings, analyzing with LLM...');
            const response = await fetch(`/api/tts/analyze/${state.currentBook.id}`, {
                method: 'POST'
            });
            if (response.ok) {
                state.ttsVoiceSettings = await response.json();
                console.log('Voice analysis complete:', state.ttsVoiceSettings);
                showVoiceRecommendation();
            }
        } catch (error) {
            console.warn('Voice analysis failed:', error);
            state.ttsVoiceSettings = { voice: 'fable', speed: 1.0, instructions: null };
        }
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
        if (state.ttsAudio) {
            state.ttsAudio.pause();
            state.ttsAudio = null;
        }
        // Cancel browser speech synthesis if active
        if (state.ttsBrowserAvailable) {
            speechSynthesis.cancel();
        }
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

        // Build the URL with voice settings
        const chapter = state.chapters[state.currentChapterIndex];
        let url = `/api/tts/speak/${state.currentBook.id}/${chapter.id}/${state.currentParagraphIndex}`;

        const params = new URLSearchParams();
        if (state.ttsVoiceSettings) {
            if (state.ttsVoiceSettings.voice) params.set('voice', state.ttsVoiceSettings.voice);
            if (state.ttsVoiceSettings.speed) params.set('speed', state.ttsVoiceSettings.speed);
            if (state.ttsVoiceSettings.instructions) params.set('instructions', state.ttsVoiceSettings.instructions);
        }
        if (params.toString()) url += '?' + params.toString();

        try {
            // Stop previous audio if any
            if (state.ttsAudio) {
                state.ttsAudio.pause();
            }

            const audio = new Audio(url);
            state.ttsAudio = audio;

            // Set playbackRate both immediately and after load to ensure it sticks
            audio.playbackRate = state.ttsPlaybackRate;
            audio.onloadeddata = () => {
                audio.playbackRate = state.ttsPlaybackRate;
            };

            audio.onended = () => {
                if (state.ttsEnabled) {
                    ttsAdvanceAndContinue();
                }
            };

            audio.onerror = (event) => {
                console.error('OpenAI TTS audio error, falling back to browser:', event);
                // Fall back to browser TTS
                if (state.ttsEnabled && state.ttsBrowserAvailable) {
                    ttsSpeakBrowser(text);
                } else if (state.ttsEnabled) {
                    setTimeout(() => ttsAdvanceAndContinue(), 500);
                }
            };

            await audio.play();
            // Also set after play starts to be extra sure
            audio.playbackRate = state.ttsPlaybackRate;
        } catch (error) {
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
            // Need to load next chapter - nextParagraph handles this
            const prevChapter = state.currentChapterIndex;
            nextParagraph();

            // Wait for chapter to load, then continue
            const checkAndContinue = () => {
                if (state.currentChapterIndex !== prevChapter && state.paragraphs.length > 0) {
                    ttsSpeakCurrent();
                } else if (state.ttsEnabled) {
                    setTimeout(checkAndContinue, 100);
                }
            };
            setTimeout(checkAndContinue, 100);
        } else {
            nextParagraph();
            ttsSpeakCurrent();
        }
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
            if (state.ttsEnabled) {
                ttsAdvanceAndContinue();
            }
        };

        utterance.onerror = (event) => {
            console.error('Browser TTS error:', event);
            if (state.ttsEnabled && event.error !== 'interrupted') {
                setTimeout(() => ttsAdvanceAndContinue(), 500);
            }
        };

        speechSynthesis.speak(utterance);
    }

    function ttsInterrupt() {
        // Called when user navigates manually while TTS is active
        if (state.ttsEnabled) {
            if (state.ttsAudio) {
                state.ttsAudio.pause();
                state.ttsAudio = null;
            }
            // Cancel browser speech synthesis if active
            if (state.ttsBrowserAvailable) {
                speechSynthesis.cancel();
            }
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

        // TTS toggle
        if (elements.ttsToggle) {
            elements.ttsToggle.addEventListener('click', ttsToggle);
        }

        // Search input
        elements.searchInput.addEventListener('input', (e) => {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                performSearch(e.target.value);
            }, 300);
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

        // Keyboard navigation
        document.addEventListener('keydown', (e) => {
            // Skip if typing in search
            if (document.activeElement === elements.searchInput) {
                return;
            }

            // Skip if not in reader view
            if (elements.readerView.classList.contains('hidden')) {
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
