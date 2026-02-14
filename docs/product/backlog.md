# Product Backlog

Last updated: 2026-02-14

Statuses: `Discovery`, `Proposed`, `Ready`, `In Progress`, `Blocked`, `Done`

## Current Delivery State

- Most recent completed slice: `BL-003 - Expand automated test coverage for AI/media pipelines` (`Done`, validated with full `mvn test`).
- Most recent shipped hardening (2026-02-13): cache-only mode no longer blocks recap/character chat when `ai.chat.enabled=true`; docs/tests/UI indicator were updated in PR #16.
- Active priority work: `BL-004 - Migrate from H2 to production database` (`P0`, `Ready`).

## Discovery Epics (Pending Product Discussion)

### BL-018 - Personalized Landing Page Rework
- Type: Improvement
- Priority: P1
- Effort: L
- Status: Discovery
- Problem: Current library landing does not adapt to reading behavior or intent.
- Scope Buckets:
- Personalization model (continue reading, streaks, recommended next action).
- Information architecture for library/recents/discovery sections.
- Lightweight ranking logic using local activity signals.
- Discovery Questions:
- Should the landing page prioritize "continue reading" over exploration?
- What activity dimensions matter most (recency, completion, pace, favorites)?
- Should this personalization be local-only initially or account-backed later?
- Current Direction (2026-02-08):
- Move from generic catalog landing to reader-activity-driven sections:
- User library (books started or explicitly saved).
- In progress.
- Completed.
- Up next queue.
- Keep a discovery rail for new books so exploration remains visible.
- Exit Criteria for Discovery:
- Ranked section model with tie-break rules.
- Approved landing page layout and interaction flow.

### BL-019 - Gamification and Trophy System
- Type: Feature
- Priority: P2
- Effort: L
- Status: Discovery
- Problem: The product lacks long-term engagement mechanics tied to reading progress.
- Scope Buckets:
- Trophy taxonomy and unlock rules.
- Progress tracking events and persistence model.
- Trophy presentation in UI (profile, chapter pause, library badges).
- Discovery Questions:
- Should trophies emphasize consistency, completion, comprehension, or exploration?
- Are trophies private-only or shareable?
- Should trophy logic be deterministic and transparent to users?
- Current Direction (2026-02-08):
- Support private tracking by default.
- Add optional social sharing entry points for selected trophies for growth experiments.
- Design unlock rules to be deterministic and auditable.
- Exit Criteria for Discovery:
- Initial trophy catalog (v1) with explicit unlock conditions.
- Event tracking schema for unlock evaluation.

### BL-020 - Post-Chapter Pop Quiz
- Type: Feature
- Priority: P2
- Effort: M
- Status: Done
- Problem: Readers may want optional chapter-level comprehension checks and reflection prompts.
- Implementation Plan:
- Phase 1 (Data + API): Add per-chapter quiz persistence with immutable payload storage, generation status, and read/status/generate/grade endpoints.
- Phase 2 (Generation Pipeline): Implement async on-demand quiz generation on chapter load with LLM JSON output and extractive fallback.
- Phase 3 (Reader UX): Add `Quiz` tab in chapter pause overlay with multiple-choice flow, score summary, and wrong-answer citation snippets.
- Phase 4 (Progression): Define difficulty ramp and trophy linkage once quiz completion telemetry is stable.
- Current Direction (2026-02-08):
- Start with factual-only quizzes.
- Present at chapter pause as optional interaction.
- Add citations/snippets for wrong answers as a likely v1 requirement.
- Current Direction (2026-02-11):
- v1 quiz format: factual-only multiple-choice (target 3 questions, allow 2-5 from LLM output).
- v1 generation mode: on-demand async generation with persisted chapter quiz payloads and cache reuse.
- v1 review feedback: grading response includes correct answer and citation snippet for each missed question.
- Acceptance Criteria:
- Quiz API returns stable, static payload for a chapter after first successful generation.
- Quiz grading endpoint returns total score and per-question correctness with citation snippets for missed answers.
- Reader chapter pause UI exposes quiz interaction without blocking continue/skip chapter navigation.
- Quiz difficulty ramps deterministically by chapter progression and returns current difficulty in quiz payload/grade responses.
- Quiz grading records progression attempts and unlocks deterministic quiz trophies for future gamification surfaces.
- Work Tracker:
| Slice | Status | Scope | Done When |
| --- | --- | --- | --- |
| BL-020.1 Quiz Data + API | Done | `chapter_quizzes` persistence, async generation queue, `/api/quizzes` status/read/generate/grade endpoints | Controller/service tests pass and generated quiz payload is stable per chapter |
| BL-020.2 Reader Quiz UX | Done | Add `Quiz` tab to chapter pause overlay with answer submission + score/citation feedback | Reader can complete quiz and view missed-answer citations without nav regressions |
| BL-020.3 Difficulty + Trophy Linkage | Done | Add configurable difficulty ramp and integrate quiz outcomes with trophy logic | Difficulty settings and trophy unlock hooks are implemented and validated |
- Session Log:
- 2026-02-11: Started BL-020 by implementing chapter quiz persistence + async generation service and adding `/api/quizzes` read/status/generate/grade endpoints.
- 2026-02-11: Added chapter pause `Quiz` tab in reader overlay with multi-question submission flow, score summary, and missed-answer citation feedback.
- 2026-02-11: Validated BL-020.1/BL-020.2 with passing `ChapterQuizServiceTest` and `ChapterQuizControllerTest` plus recap regression tests.
- 2026-02-11: Completed BL-020.3 by adding chapter-index-based quiz difficulty ramping, persisted quiz attempt/trophy tracking, trophy/readout APIs, and UI feedback for unlocked trophies and streak progress.

### BL-021 - User Registration and Account System
- Type: Feature
- Priority: P1
- Effort: XL
- Status: Discovery
- Problem: User-specific progress and personalization cannot reliably persist across devices without accounts.
- Scope Buckets:
- Authentication and session architecture.
- User data model migration from local-only state.
- Privacy/security controls and account lifecycle operations.
- Discovery Questions:
- What auth modes are required at launch (email/password, OAuth, magic link)?
- What existing local data should be migrated into new accounts?
- Is anonymous mode still supported alongside registration?
- Current Direction (2026-02-08):
- Cost and traction uncertainty are primary constraints.
- Registration remains discovery-phase and should be sequenced after validating engagement loops.
- Any account approach must include cost controls and staged rollout.
- Exit Criteria for Discovery:
- Auth architecture decision record.
- User data ownership and retention policy.
- Minimum viable account feature set and rollout plan.

### BL-022 - Reader Chapter Summary Feedback (AI Coach)
- Type: Feature
- Priority: P2
- Effort: M
- Status: Discovery
- Problem: Factual quizzes measure recall, but readers may also want qualitative feedback on their own chapter understanding.
- Scope Buckets:
- Reader-written chapter summary capture UI.
- AI rubric scoring against chapter key points.
- Missed-point feedback with spoiler-safe guidance.
- Discovery Questions:
- Should this be optional after quiz completion or standalone?
- Should scoring be numeric, tiered badges, or guidance-only?
- Should feedback include direct quote snippets from chapter text?
- Exit Criteria for Discovery:
- Defined feedback rubric and output format.
- Decision on placement in chapter transition flow.

## P0

### BL-001 - Secure and rate-limit generation/chat endpoints
- Type: Tech Debt
- Priority: P0
- Effort: L
- Status: Done
- Problem: Expensive endpoints (`tts`, `illustrations`, `characters`, `pregen`) are callable without auth/rate controls.
- Current Direction (2026-02-14):
- Implement a deployment-mode-aware guardrail layer for non-local profiles first:
- Add request auth gate for sensitive generation/chat endpoints when `deployment.mode=public`.
- Add per-IP rate limiting for generation + chat endpoints with conservative defaults and explicit 429 payloads.
- Keep local/dev behavior unchanged by default to preserve current iteration speed.
- Work Tracker:
| Slice | Status | Scope | Done When |
| --- | --- | --- | --- |
| BL-001.1 Public-mode API guardrails | Done | Add centralized endpoint matcher + interceptor enforcing `X-API-Key` in `deployment.mode=public`; add per-IP rate limits for sensitive generation/chat routes with 429 responses | Sensitive generation/chat routes reject missing/invalid key in public mode and enforce configured request limits |
,| BL-001.2 Collaborator session auth | Done | Add browser-usable collaborator auth via `/api/auth/login` + HttpOnly session cookie, and allow sensitive public endpoints to authenticate with either API key or collaborator session | Collaborators can authenticate in-app and access protected generation/chat endpoints without exposing server API key in frontend code |
| BL-001.3 Rate-limit model expansion | In Progress | Add auth-identity-aware limiter keys and per-authenticated-principal limits; evaluate external/durable limiter backing for multi-instance deployments | Limits are aligned to authenticated identity and resilient across replicas |
- Session Log:
- 2026-02-14: Implemented BL-001.1 with `PublicApiGuardInterceptor`, sensitive route matcher, and in-memory per-IP fixed-window limiter; added `deployment.mode`/`security.public.*` properties with local-safe defaults.
- 2026-02-14: Added coverage for route classification and interceptor behavior in `SensitiveApiRequestMatcherTest`, `PublicApiGuardInterceptorPublicModeTest`, and `PublicApiGuardInterceptorLocalModeTest`; validated with full `mvn test`.
- 2026-02-14: Implemented BL-001.2 with `PublicSessionAuthService`, `/api/auth` login/status/logout endpoints, and interceptor support for either `X-API-Key` or collaborator session auth in public mode.
- 2026-02-14: Added collaborator sign-in modal in `reader.js`/`index.html` and validated auth + guardrails with `AuthControllerTest` and `PublicApiGuardInterceptorSessionAuthTest` plus full `mvn test`.
- 2026-02-14: Implemented BL-001.3 identity-aware limiter scope in `PublicApiGuardInterceptor` (API key/session principal scoped keys + authenticated limit properties); added `rateLimit_isScopedPerCollaboratorSession` coverage and validated with full `mvn test`.
- Acceptance Criteria:
- Add authentication/authorization strategy for non-local deployments.
- Add per-IP or per-user rate limits for generation and chat endpoints.
- Add safe defaults when deployment mode is `public`.

### BL-002 - Replace in-memory generation queues with durable job orchestration
- Type: Tech Debt
- Priority: P0
- Effort: XL
- Status: In Progress
- Problem: Illustration/character work queues run in-process with single-thread executors and are vulnerable to restart loss.
- Current Direction (2026-02-14):
- Deliver BL-002 incrementally while preserving existing generation behavior:
- Add startup recovery that rehydrates queued work from persisted DB state (`PENDING` + stuck `GENERATING`) for illustration/character/recap pipelines.
- Then replace in-memory queue ownership with durable job leasing + retry/backoff policies.
- Work Tracker:
| Slice | Status | Scope | Done When |
| --- | --- | --- | --- |
| BL-002.1 Startup queue recovery | Done | Add app-start recovery service that requeues persisted pending/stuck generation work across books | Restarting the app rehydrates generation queues without manual operator requeue |
| BL-002.2 Durable worker leasing | Done | Introduce durable lease claims so multi-instance workers coordinate safely and avoid duplicate processing | Queue ownership survives restarts and prevents duplicate work across replicas |
| BL-002.2a Recap lease claims | Done | Add DB-backed lease claim on chapter recap jobs before processing, with lease release on terminal states | Concurrent workers cannot both process the same recap unless lease expires |
| BL-002.2b Illustration/character lease claims | Done | Extend durable lease claims to illustration and character queues | Illustration/character pipelines have the same cross-instance coordination guarantees as recaps |
| BL-002.3 Retry/backoff + status API | Done | Add explicit retry/backoff policy and aggregate job status endpoint/query model | Retry behavior is explicit/test-covered and job state is queryable without log inspection |
- Session Log:
- 2026-02-14: Added `GenerationQueueRecoveryService` (startup orchestrator) to requeue persisted illustration/portrait/analysis/recap work from DB state and added `GenerationQueueRecoveryServiceTest`; validated with full `mvn test`.
- 2026-02-14: Added recap durable lease claims in `ChapterRecapService`/`ChapterRecapRepository` (`claimGenerationLease`, `leaseOwner`, `leaseExpiresAt`) with worker identity + lease duration config; added coverage in `ChapterRecapServiceTest`.
- 2026-02-14: Added durable lease claims for `IllustrationService` and `CharacterService` pipelines (portrait + chapter analysis) with atomic repository claim queries and lease cleanup on terminal transitions; added `GenerationLeaseClaimRepositoryTest` for illustration/portrait/analysis claim behavior.
- 2026-02-14: Implemented DB-backed retry/backoff metadata (`retryCount`, `nextRetryAt`) for recap/illustration/portrait/analysis jobs, added exponential retry scheduling in generation services, and exposed aggregate status APIs at `/api/generation/status` and `/api/generation/book/{bookId}/status`; added coverage in `GenerationJobStatusServiceTest`, `GenerationStatusControllerTest`, and extended lease-claim/retry tests.
- Acceptance Criteria:
- Jobs persist across application restarts.
- Retry/backoff policies are explicit and test-covered.
- Job status can be queried without scraping logs.

### BL-003 - Expand automated test coverage for AI/media pipelines
- Type: Tech Debt
- Priority: P0
- Effort: L
- Status: Done
- Problem: Tests focus on import/search/parser; high-risk flows (TTS/illustration/character/pregen) have minimal coverage.
- Acceptance Criteria:
- Add controller tests for `TtsController`, `IllustrationController`, `CharacterController`, `PreGenerationController`.
- Add service-level tests for queue/retry/status transitions.
- Add smoke test profile for end-to-end happy path with mocked providers.
- Session Log:
- 2026-02-14: Added controller coverage for `TtsController`, `IllustrationController`, `CharacterController`, and `PreGenerationController` (including cache-only conflicts + feature gating paths) in `TtsControllerTest`, `IllustrationControllerTest`, `CharacterControllerTest`, `PreGenerationControllerTest`, and `PreGenerationControllerCacheOnlyTest`; added book-scoped retry/pending aggregate coverage in `GenerationJobStatusServiceTest`.
- 2026-02-14: Added `smoke` profile (`application-smoke.properties`) and `AiMediaPipelinesSmokeTest` as an end-to-end happy-path `@SpringBootTest` using mocked providers; validated via targeted smoke run and full `mvn test`.

### BL-004 - Migrate from H2 to production database
- Type: Tech Debt
- Priority: P0
- Effort: M
- Status: Ready
- Problem: Runtime still depends on H2 + `ddl-auto=update`, which increases schema drift and production risk for a public deployment.
- Current Direction (2026-02-14):
- Execute immediate DB cutover work now rather than deferring.
- Default target is PostgreSQL unless explicitly switched to MariaDB before implementation starts.
- Keep H2 only for local/dev convenience and selected tests after migration ownership is in place.
- Acceptance Criteria:
- App runs against PostgreSQL or MariaDB in non-local environments with no H2 dependency for production runtime.
- Adopt Flyway or Liquibase migrations and baseline existing schema/history.
- Restrict seed data to explicit dev/test profiles.
- Replace `ddl-auto=update` with migration-owned schema management (`validate` or equivalent) outside local dev.
- Add rollback-safe migration and cutover guidance (backup, restore, verification checklist).
- Work Tracker:
| Slice | Status | Scope | Done When |
| --- | --- | --- | --- |
| BL-004.1 Engine + runtime config | Ready | Finalize target engine (default PostgreSQL), add env-driven datasource profiles, and document local vs non-local DB behavior | Non-local profile starts cleanly on target engine using env vars only |
| BL-004.2 Migration baseline | Proposed | Introduce Flyway/Liquibase and baseline current schema so schema changes are migration-owned | Fresh DB and existing DB both reach expected schema via migrations |
| BL-004.3 Controlled seeding | Proposed | Move startup sample seed behavior to explicit dev/test profiles only | Production profile does not auto-seed sample books |
| BL-004.4 Cutover + rollback runbook | Proposed | Add deployment runbook for backup, cutover steps, verification queries, and rollback | Operator can execute and reverse cutover without ad-hoc DB edits |
- Session Log:
- 2026-02-14: Reframed BL-004 as concrete production DB migration work, set status to `Ready`, and made PostgreSQL the default target unless explicitly changed to MariaDB.

## P1

### BL-005 - Add notes, highlights, and bookmarks
- Type: Feature
- Priority: P1
- Effort: L
- Status: Proposed
- Problem: Reader currently lacks durable annotations/bookmarking despite being a core deep-reading need.
- Acceptance Criteria:
- Users can create/edit/delete highlights and notes per paragraph.
- Users can jump to bookmarks from a reader sidebar/modal.
- Data persists per book across sessions.

### BL-006 - Reader preferences panel (typography/layout controls)
- Type: Feature
- Priority: P1
- Effort: M
- Status: Proposed
- Problem: Font size/line-height/theme/column-gap are static in practice.
- Acceptance Criteria:
- Add preferences UI for typography/layout controls.
- Persist preferences and apply on load.
- Re-pagination remains stable after setting changes.

### BL-007 - Library management UI for local books and feature toggles
- Type: Improvement
- Priority: P1
- Effort: M
- Status: Proposed
- Problem: Backend supports delete and feature toggles, but UI lacks first-class controls.
- Acceptance Criteria:
- Expose delete/unimport action with confirmation.
- Expose per-book feature toggles in library UI.
- Reflect toggle state immediately in reader availability checks.

### BL-008 - Upgrade in-reader search quality and navigation
- Type: Improvement
- Priority: P1
- Effort: M
- Status: Proposed
- Problem: Snippets are fixed-length and ranking is generic; navigation context is limited.
- Acceptance Criteria:
- Improve snippet extraction around match location.
- Add optional chapter filter and result grouping.
- Highlight matched terms in displayed paragraph after navigation.

### BL-009 - Make pre-generation non-blocking with progress API
- Type: Improvement
- Priority: P1
- Effort: L
- Status: Proposed
- Problem: Current pre-generation endpoint blocks while polling and sleeps in-process.
- Acceptance Criteria:
- Start job endpoint returns job ID immediately.
- Progress endpoint reports counts/state/errors.
- Frontend or CLI can poll/cancel safely.

### BL-010 - Unify user-facing errors and retries
- Type: Improvement
- Priority: P1
- Effort: M
- Status: Proposed
- Problem: Frontend still relies on `alert()` and scattered error patterns.
- Acceptance Criteria:
- Replace blocking alerts with consistent toast/inline error components.
- Standardize retry affordances for import/search/generation failures.
- Map backend error payloads to clear UX states.

### BL-011 - Add observability for long-running generation flows
- Type: Tech Debt
- Priority: P1
- Effort: M
- Status: Proposed
- Problem: Debugging relies mostly on logs; queue/backlog metrics are not first-class.
- Acceptance Criteria:
- Expose metrics for queue depth, success/failure counts, and processing latency.
- Add correlation IDs to generation requests.
- Add health detail endpoint for provider and queue status.

### BL-023 - Adaptive mobile reader experience
- Type: Feature
- Priority: P1
- Effort: L
- Status: Proposed
- Problem: Reader interactions assume keyboard + desktop viewport, causing friction and broken affordances on phones.
- Acceptance Criteria:
- Preserve existing desktop keyboard shortcuts and behavior (`h/l`, `j/k`, `H/L`, `/`, `c`) for non-mobile layouts.
- Provide touch-first mobile navigation for page/paragraph/chapter progression without keyboard dependency.
- Add capability checks so desktop-centric features can be disabled on mobile when needed, with clear UI fallback messaging.
- Ensure chapter list and chapter-pause overlays remain usable on common phone breakpoints while keeping continue/skip/submit actions accessible.
- Add a mobile QA checklist (iOS Safari + Android Chrome) and desktop regression checklist for keyboard flows.
- Notes/Dependencies:
- Coordinate with BL-013 so accessibility/focus changes ship alongside mobile interaction updates.

### BL-024 - Cache Transfer + Remote Deploy Automation
- Type: Improvement
- Priority: P1
- Effort: M
- Status: Done
- Problem: Moving pre-generated recap data between local and remote environments required manual, error-prone CLI sequences and ad-hoc deployment/import steps.
- Acceptance Criteria:
- Provide a recap cache transfer CLI with dry-run/apply safety, conflict policy controls, and stable book/chapter matching semantics.
- Provide operator scripts for book-level pregen/export/import flow and local-to-remote transfer orchestration over SSH.
- Support remote execution without requiring Maven when a deployed Spring Boot jar is available.
- Validate transfer/import on the production-like target flow and document usage.
- Scope Notes:
- v1 transfer scope includes recap + quiz metadata (`chapter_recaps`, `chapter_quizzes` payload/status data).
- Binary assets (audio/illustrations/portraits) remain managed via Spaces sync (`scripts/sync_spaces.sh`).
- Session Log:
- 2026-02-12: Implemented `org.example.reader.cli.CacheTransferRunner` with `export`/`import`, `skip|overwrite` conflict handling, format validation, dry-run default, and H2 URL normalization (`DB_CLOSE_ON_EXIT=FALSE`) to avoid exec-classloader shutdown issues.
- 2026-02-12: Added recap transfer coverage in `CacheTransferRunnerTest` including all-cached export, multi-book export, dry-run immutability, and conflict policy behavior.
- 2026-02-12: Added operator scripts `scripts/pregen_transfer_book.sh`, `scripts/transfer_recaps_remote.sh`, and `scripts/deploy_remote.sh`; documented workflows in `README.md`.
- 2026-02-12: Hardened remote transfer script for SSH alias/config usage, strict-mode bash handling, project-root Maven execution, robust remote arg transport, jar-runner fallback (via Spring Boot `PropertiesLauncher`), and remote service stop/start orchestration.
- 2026-02-12: Validated end-to-end transfer flow against remote target with successful dry-run import summary (`21` books, `1768` recaps, `0` validation errors) and successful apply import run.
- 2026-02-12: Added `scripts/pregen_quizzes_book.sh` and `docs/operations/pre-generation-runbook.md` to document and automate quiz pre-generation alongside existing image/portrait/recap workflows.
- 2026-02-12: Added `scripts/pregen_quizzes_top20.sh` to import + pre-generate quizzes for the top-20 Gutenberg set, with server-direct execution guidance in the runbook.
- 2026-02-12: Extended `CacheTransferRunner` and `scripts/transfer_recaps_remote.sh` to support `--feature quizzes` export/import so locally generated quizzes can be promoted to remote DB without paid server-side generation.

### BL-017 - Post-Chapter Recap + Discussion Experience
- Type: Feature
- Priority: P1
- Effort: XL
- Status: Done
- Problem: Readers need structured comprehension support between chapters without breaking immersion.
- Implementation Plan:
- Phase 1 (Data + Contracts): Add recap persistence model per `bookId/chapterId` with immutable generated payload, status, timestamps, and prompt/version metadata; add recap retrieval/status APIs and typed frontend response models.
- Phase 2 (Generation Pipeline): Implement hybrid generation path: pre-generate recaps during batch pre-generation for top-N books and generate on-demand fallback on first chapter completion for non-pre-generated books.
- Phase 3 (Reader UX): Add chapter-transition recap screen in `reader.js` with short default recap, expandable detail (key events + character deltas), and explicit continue CTA to next chapter.
- Phase 4 (Discussion Chat): Add chapter-bounded discussion endpoint that only uses text from chapters `<= currentChapterIndex` and recap payload for context; persist thread locally like character chat.
- Phase 5 (Safety + Quality): Add spoiler and hallucination controls (context window hard cap, chapter index guard, refusal/fallback response); add regeneration only via explicit admin/CLI action so recap stays static for readers.
- Phase 6 (Rollout + Ops): Ship behind feature flag, enable first for pre-generated books, then expand to on-demand fallback after latency/error thresholds are met.
- Acceptance Criteria:
- Recap API returns consistent, static recap payload for a chapter after first successful generation.
- Recap payload includes short summary, key events, and character development deltas with structured fields.
- Pre-generation flow queues recap generation alongside existing pipelines and reports recap progress in status output.
- On-demand recap generation is triggered once per chapter when missing and does not block chapter navigation.
- Chapter discussion responses never reference content from future chapters and return a guarded fallback when request context is invalid.
- Reader UI shows recap between chapters with opt-out toggle and allows users to skip directly to next chapter.
- Feature can be disabled globally and per book without breaking existing reader flow.
- Dependencies:
- BL-009 (non-blocking pre-generation progress API) to expose recap generation state cleanly.
- BL-010 (unified error/retry UX) to handle recap/chat failures without `alert()` regressions.
- Existing character analysis/pre-generation queue patterns (`CharacterService`, `PreGenerationService`) reused for recap job orchestration.
- Risks:
- LLM cost/latency spikes from on-demand recap generation on long chapters.
- False-positive spoiler leakage if chapter-bound context checks are incomplete.
- Reader transition fatigue if recap interrupts users who prefer continuous reading.
- Rollout Notes:
- Start with recap format `short + key events + character deltas`; defer alternate formats (timeline/bullets variants) to follow-up.
- Default rollout: enabled for top-N pre-generated books only; on-demand fallback toggled on after one release cycle of metrics.
- Add instrumentation for generation latency, failure rate, spoiler-guard fallback rate, and recap view/skip rate before widening rollout.
- Work Tracker:
| Slice | Status | Scope | Done When |
| --- | --- | --- | --- |
| BL-017.1 Recap Data + API | Done | Recap entity/repo/service + read/status endpoints + payload schema | Endpoints return stable recap payload; controller/service tests pass |
| BL-017.2 Generation Integration | Done | Hook recap generation into pre-generation + on-demand fallback | Recaps generate in both paths; progress/state visible |
| BL-017.3 Reader Transition UI | Done | Post-chapter recap screen, skip/continue flow, opt-out toggle | Reader can view recap or skip with no nav regressions |
| BL-017.4 Bounded Discussion Chat | Done | Chapter-bounded chat endpoint + frontend thread persistence | Chat never leaks future-chapter content; fallback responses handled |
| BL-017.5 Guardrails + Rollout | Done | Spoiler guards, feature flags, metrics, rollout toggles | Flags/metrics wired, recap-only pregen stable, and manual spoiler checks passed on tested books |
- Session Log:
- 2026-02-08: Moved BL-017 from `Discovery` to `Ready` with concrete implementation plan.
- 2026-02-08: Added minimal slice tracker and dated log to support pause/resume execution.
- 2026-02-08: Implemented BL-017.1 with `chapter_recaps` persistence, recap payload schema, `/api/recaps` read/status endpoints, and passing controller/service tests.
- 2026-02-08: Implemented BL-017.2 with async recap generation queue, pre-generation integration, recap status metrics in pregen results, and passing recap/pregen tests.
- 2026-02-08: Started BL-017.3 with first-pass chapter recap transition overlay, skip/continue actions, and per-book recap opt-out toggle in reader UI.
- 2026-02-08: Started BL-017.4 with chapter-bounded recap chat API contract (`/api/recaps/book/{bookId}/chat`) and context-guard service tests.
- 2026-02-08: Upgraded recap generation to reasoning-LLM JSON output with extractive fallback and added recap service tests for provider path.
- 2026-02-08: Completed BL-017.4 frontend work by adding recap discussion UI and per-book local thread persistence in reader recap overlay.
- 2026-02-08: Updated recap overlay UX to auto-refresh while status is `MISSING/PENDING/GENERATING`, stopping polling once terminal status is reached or overlay closes.
- 2026-02-08: Refined recap overlay into two tabs (`Recap` default, `Chat`) to improve focus now and leave clean UI space for future `Pop Quiz` expansion.
- 2026-02-08: Started BL-017.5 by adding rollout gating modes (`all`, `allow-list`, `pre-generated`), per-book recap availability endpoint, and recap metrics capture (generation/chat/modal events) with status visibility.
- 2026-02-08: Added recap-specific reasoning provider config so recaps can use Ollama independently of other reasoning tasks (cost-control path for recap generation).
- 2026-02-08: Added recap-only pre-generation mode for batch runner to support top-20 recap generation without triggering illustration/portrait generation.
- 2026-02-11: Stabilized recap pre-generation by preventing duplicate queue entries, skipping already-completed recaps, and resetting only stale `GENERATING` recaps (plus configurable stall thresholds).
- 2026-02-11: Completed recap UX polish with persistent modal chrome + scrollable body, recap/chat tab flow polish, and a reader header control to re-enable per-book recap popups after opt-out.
- 2026-02-11: Hardened recap chat guardrails by enforcing source-only prompt behavior and chapter-scoped local chat history; validated behavior in manual QA on tested books.
- 2026-02-11: Marked BL-017 as Done; next feature work continues under BL-020 (Post-Chapter Pop Quiz).

## P2

### BL-012 - Split `reader.js` into modules and add frontend tests
- Type: Tech Debt
- Priority: P2
- Effort: L
- Status: Proposed
- Problem: Reader logic is monolithic, increasing regression risk and slowing iteration.
- Acceptance Criteria:
- Break reader logic into coherent modules (library/reader/tts/illustration/character).
- Add unit tests for pagination, keyboard handling, and persistence helpers.
- Keep existing keyboard behavior unchanged.

### BL-013 - Accessibility and mobile optimization pass
- Type: Improvement
- Priority: P2
- Effort: M
- Status: Proposed
- Problem: Complex overlays and keyboard flows need explicit a11y and mobile verification.
- Acceptance Criteria:
- Add ARIA labels/focus management for all modals/overlays.
- Validate reader and overlays on common mobile breakpoints.
- Document keyboard shortcuts/help discoverability in UI.

### BL-014 - Automated asset lifecycle management
- Type: Tech Debt
- Priority: P2
- Effort: M
- Status: Proposed
- Problem: Asset cleanup/migration is manual via CLI; no retention or scheduled pruning.
- Acceptance Criteria:
- Define retention policy for stale assets.
- Add safe dry-run + scheduled cleanup workflow.
- Emit cleanup summary metrics/logs.

### BL-015 - API documentation and integration contract
- Type: Improvement
- Priority: P2
- Effort: S
- Status: Proposed
- Problem: API surface is broad and mostly discoverable only from code.
- Acceptance Criteria:
- Publish OpenAPI spec for current controllers.
- Add examples for import/search/tts/illustration/character flows.
- Add versioning/change log policy for API-breaking changes.

### BL-016 - Add secondary import source support (EPUB/Standard Ebooks)
- Type: Feature
- Priority: P2
- Effort: L
- Status: Proposed
- Problem: Import is currently Gutenberg-centric.
- Acceptance Criteria:
- Support one additional source (EPUB or Standard Ebooks).
- Normalize metadata/chapters into existing schema.
- Preserve source attribution and de-dup behavior.
