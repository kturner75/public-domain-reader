# Current Feature Inventory

Last audited: 2026-02-08

This inventory reflects implemented behavior in backend controllers/services and `static/js/reader.js`.

## Reading Experience

- Two-view app: library view and focused reader view.
- No-scroll reading mode with dynamic pagination and two-column layout.
- Keyboard-first navigation:
  - page: `h`/`l`
  - paragraph: `j`/`k`
  - chapter: `H`/`L`
  - chapter list: `c`
- Chapter list overlay with keyboard selection.
- Reader search (`/`) with in-book result navigation.
- Resume state persisted in `localStorage` (book/chapter/page/paragraph).
- Recently-read list in library.

## Library and Import

- Browse popular Project Gutenberg catalog.
- Search Gutenberg catalog from library search box.
- Import book by Gutenberg ID.
- Detect and mark already-imported catalog books.
- Library APIs for list/get/delete/delete-all books.
- Per-book feature flags for:
  - `ttsEnabled`
  - `illustrationEnabled`
  - `characterEnabled`

## Search

- Lucene-based full-text search for book metadata and paragraph content.
- Optional `bookId` scoped search for in-reader queries.
- Search index rebuilt at startup from DB records.

## Text-to-Speech (TTS)

- Feature-gated per book.
- Voice settings retrieval and AI-assisted voice analysis.
- Paragraph-level TTS endpoint with:
  - voice/speed/instructions options
  - cache support
  - cache-only mode support
  - CDN redirect support for cached audio
- Browser speech synthesis fallback in frontend when API generation is unavailable.
- TTS prefetch for next paragraph in frontend.
- Cost estimate endpoint.

## Speed Reading

- Feature flag endpoint and frontend availability checks.
- Speed reading overlay with play/pause/exit.
- Adjustable speed (WPM slider), persisted in `localStorage`.
- Chapter-boundary pause/continue flow.

## Illustrations

- Feature-gated per book.
- Book-level style settings retrieval and AI style analysis.
- Chapter illustration request/status/image endpoints.
- Background queue-based illustration generation.
- Prefetch next chapter illustration.
- Prompt inspection and prompt-based regeneration flow (when enabled).
- Regeneration preview/accept flow in frontend modal.
- Retry endpoint for stuck pending illustrations.
- Cache-only mode and CDN redirect support.

## Characters

- Feature-gated per book.
- Character extraction pipeline by chapter.
- Background queue-based portrait generation.
- Character prefetch by book and next-chapter prefetch.
- Character status APIs and portrait retrieval (with CDN support).
- In-reader discovery checks and toast notifications for newly available characters.
- Character browser modal with list/detail views.
- Navigate from character detail to first appearance in text.
- Character chat (primary characters only), with reading-position context guardrails.
- Chat history persisted in `localStorage` per book/character.
- `generation.cache-only` does not disable character chat; chat remains available when `ai.chat.enabled=true` and the chat provider is available.

## Pre-Generation and Operations

- Pre-generation APIs:
  - by existing `bookId`
  - by `gutenbergId` (import + generation)
- Batch pre-generation runner for curated top books.
- Stall detection and requeue/reset behavior for generation jobs.
- Asset key normalization for stable paths.
- CLI utilities for asset migration and orphan cleanup.
- Spaces/CDN sync script for audio/portraits/illustrations.

## Persistence and Configuration

- H2 file-backed database (`./data/library`).
- JPA entities for books/chapters/paragraphs/illustrations/characters.
- LLM provider abstraction with configurable reasoning/chat providers.
- Runtime feature and generation behavior configured via `application.properties`.
- `generation.cache-only` is artifact-generation-only (recaps/quizzes/illustrations/character generation pipelines); cached reads still work, and chat is controlled separately by `ai.chat.enabled`.

## Test Coverage (Current)

- Strong coverage for:
  - import controller/service
  - search controller/service
  - Gutenberg content parser
- Limited coverage for:
  - TTS, illustrations, characters, pre-generation workflows
  - frontend reader logic
  - CLI utilities and operational scripts
