# Pre-Generation And Transfer Runbook

Last updated: 2026-02-12

## Scope

This runbook covers:
- Pre-generating illustrations, portraits, and recaps.
- Pre-generating chapter quizzes.
- Syncing binary assets to Spaces/CDN.
- Transferring recap/quiz cache data from local to remote DB.

## Prerequisites

- App deployed/running for API-driven scripts (`http://localhost:8080` by default).
- `curl` installed.
- `jq` installed (needed for quiz pre-generation script).
- For remote transfer:
  - SSH access to target host.
  - Remote host has either:
    - Maven (`mvn`) in PATH, or
    - Java + deployed app jar (`public-domain-reader-1.0-SNAPSHOT.jar`).

## 1) Deploy Latest Code

```bash
scripts/deploy_remote.sh --ssh-target pdr --ssh-key ~/.ssh/kevin
```

## 2) Pre-Generate Illustrations + Portraits + Recaps (Book-Level)

```bash
scripts/pregen_transfer_book.sh --gutenberg-id 1342 --skip-export
```

Notes:
- This calls `POST /api/pregen/gutenberg/{id}` and blocks until generation completes.
- `--skip-export` keeps this step focused on generation only.

## 3) Pre-Generate Quizzes (Book-Level)

Get book id first:

```bash
curl -s http://localhost:8080/api/library | jq '.[] | {id,title,author}'
```

Then pre-generate quizzes for all chapters in that book:

```bash
scripts/pregen_quizzes_book.sh --book-id <book-id>
```

## 3b) Pre-Generate Quizzes (Top 20 Gutenberg Set)

Run against local app:

```bash
scripts/pregen_quizzes_top20.sh --api-base-url http://localhost:8080
```

Run directly against server app (recommended when your goal is server-side cache):

```bash
ssh pdr 'cd /opt/public-domain-reader && ./scripts/pregen_quizzes_top20.sh --api-base-url http://localhost:8080'
```

## 4) Sync Assets To Spaces/CDN

```bash
SYNC_DIRECTION=up scripts/sync_spaces.sh
```

This syncs:
- `audio/**`
- `illustrations/**`
- `character-portraits/**`

## 5) Transfer Cache Local -> Remote

Recaps:

```bash
scripts/transfer_recaps_remote.sh \
  --feature recaps \
  --all-cached \
  --remote pdr \
  --remote-project-dir /opt/public-domain-reader \
  --remote-db-url "jdbc:h2:file:/var/lib/public-domain-reader/library;DB_CLOSE_DELAY=-1" \
  --apply-import \
  --remote-stop-cmd "sudo systemctl stop public-domain-reader" \
  --remote-start-cmd "sudo systemctl start public-domain-reader"
```

Quizzes:

```bash
scripts/transfer_recaps_remote.sh \
  --feature quizzes \
  --all-cached \
  --remote pdr \
  --remote-project-dir /opt/public-domain-reader \
  --remote-db-url "jdbc:h2:file:/var/lib/public-domain-reader/library;DB_CLOSE_DELAY=-1" \
  --apply-import \
  --remote-stop-cmd "sudo systemctl stop public-domain-reader" \
  --remote-start-cmd "sudo systemctl start public-domain-reader"
```

## 6) Validate

Check recap/quiz feature status:

```bash
curl -s http://localhost:8080/api/recaps/status | jq
curl -s http://localhost:8080/api/quizzes/status | jq
```

Spot-check a book:

```bash
curl -s http://localhost:8080/api/library | jq '.[] | {id,title,author,chapters: (.chapters | length)}'
```

## Notes On Scope

- Cache transfer CLI supports recap and quiz metadata (`chapter_recaps`, `chapter_quizzes`).
- Asset binaries are distributed through Spaces sync, not recap transfer JSON.
