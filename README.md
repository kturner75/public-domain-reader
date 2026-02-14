# Project Brief: Public Domain Classics Reader

A distraction-free, web reader for public domain literature.

Core Purpose  
Create a focused deep-reading experience for classic books.

Key Principles
- Minimize ui controls, avoid scrolling
- Dynamic viewport-fitted "pages" — exactly two columns, top-to-bottom flow
- High-legibility typography (EB Garamond or similar serif)
- Seamless keyboard navigation and page turning
- The text should be the focus. Everything else should be understated.

Core Features (Phase 1)
- Library of public domain books (Project Gutenberg / Standard Ebooks)
- Book selection → chapter navigation
- No-scroll, two-column layout with perfect page fitting
- Smart full-text search
- LocalStorage: resume last position, font size, notes/bookmarks

Tech Stack
- Frontend: Pure vanilla HTML/CSS/JS
- Backend: Java Spring Boot + in-memory Lucene (or lightweight SQLite for book storage)
- Data: EPUB/HTML from Standard Ebooks or Gutenberg

Non-Negotiables
- No scrolling ever
- Feels like turning pages in a physical book
- Optional features stay hidden until invoked

## Operational Scripts

- `scripts/pregen_transfer_book.sh`: one workflow for a single Gutenberg book:
  - pre-generate illustrations + portraits + recaps (`/api/pregen/gutenberg/{id}`)
  - export recap transfer JSON (`CacheTransferRunner export`)
  - optionally import into a target DB (`CacheTransferRunner import`)
  - optionally sync assets to Spaces (`scripts/sync_spaces.sh`)
- `scripts/transfer_recaps_remote.sh`: local export + remote import orchestration over SSH:
  - export cache transfer JSON locally (`--feature recaps|quizzes`)
  - upload JSON to server via `scp`
  - run remote import dry-run
  - optionally stop service, run apply import, then restart service
- `scripts/deploy_remote.sh`: deploy helper that mirrors the current jar deployment flow:
  - `mvn clean package`
  - `scp` jar to server
  - `ssh` and run remote deploy command (default: `/root/deploy.sh`)
- `scripts/pregen_quizzes_book.sh`: queue + poll quiz generation for every chapter in a book.
- `scripts/pregen_quizzes_top20.sh`: import + pre-generate quizzes for the top-20 Gutenberg set.

Runbook:
- `docs/operations/pre-generation-runbook.md`: end-to-end generation + transfer workflow.

Example:

```bash
scripts/pregen_transfer_book.sh \
  --gutenberg-id 1342 \
  --import-db-url "jdbc:h2:file:/absolute/path/to/library-transfer-test;DB_CLOSE_DELAY=-1" \
  --sync-assets
```

Remote import orchestration example:

```bash
scripts/transfer_recaps_remote.sh \
  --feature recaps \
  --book-source-id 1342 \
  --remote ubuntu@reader-host \
  --remote-project-dir /opt/public-domain-reader \
  --remote-db-url "jdbc:h2:file:/opt/public-domain-reader/data/library;DB_CLOSE_DELAY=-1" \
  --apply-import \
  --remote-stop-cmd "sudo systemctl stop public-domain-reader" \
  --remote-start-cmd "sudo systemctl start public-domain-reader"
```

Deploy helper example:

```bash
scripts/deploy_remote.sh --ssh-target pdr --ssh-key ~/.ssh/kevin
```
