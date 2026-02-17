# Landing Ranking Model (BL-018.3)

Last updated: 2026-02-17

## Scope
- Defines deterministic ranking for personalized landing sections:
- `Continue Reading`
- `Up Next`
- `In Progress`
- `Completed`

## Inputs
- Completion state (`in-progress`, `not-started`, `completed`)
- Recency (`lastReadAt`, fallback `lastOpenedAt`)
- Favorite intent (`My List` flag)
- Progress depth (`maxProgressRatio`)
- Stable identity (`title`, `id`) for deterministic final tie-break

## Active Queue Rules
Used by `Continue Reading`, `Up Next`, and ordering of `In Progress`.

Tie-break order:
1. Completion state: `in-progress` > `not-started` > `completed`
2. Recency: more recent activity first
3. Favorite intent: favorites first
4. Progress depth: higher `% complete` first
5. Stable fallback: title (A-Z), then id (A-Z)

## Completed Rules
Used by `Completed`.

Tie-break order:
1. Completion timestamp (`completedAt`): newest completion first
2. Recency: more recently active first
3. Favorite intent: favorites first
4. Stable fallback: title (A-Z), then id (A-Z)

## Determinism
- Ranking is pure and deterministic for the same input set.
- Node frontend tests cover priority boundaries and tie-break behavior in:
- `src/test/frontend/library-ranking.test.cjs`
