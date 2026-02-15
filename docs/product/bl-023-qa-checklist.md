# BL-023 QA Checklist (Mobile + Desktop Regression)

Last updated: 2026-02-15

This checklist validates BL-023 acceptance criteria for adaptive mobile behavior while protecting desktop keyboard workflows.

## Test Matrix

- iOS Safari (iPhone simulator, narrow viewport)
- Android Chrome (phone viewport)
- Desktop Chrome or Safari (>= 1280px width)

## Preconditions

- Run latest local app build.
- Use a book with long title and enough chapters/paragraphs (for search + navigation checks).
- Ensure chapter has searchable text and at least one expected search hit.

## Mobile Checklist (iOS Safari + Android Chrome)

- Open reader on phone viewport and confirm title remains visible in header with hamburger present.
- Open hamburger menu and verify panel is fully visible on-screen (not clipped off-left/off-right).
- Confirm desktop icon action cluster is hidden on mobile.
- In hamburger search, type text and verify menu does not close while typing.
- Tap `Search` (or press `Enter`) and verify the menu closes.
- Verify the search results panel appears after submit.
- Select a search result and verify reader jumps to the expected paragraph.
- Re-open hamburger and tap `Reader Preferences`; verify preferences panel opens every time.
- In `Reader Preferences`, verify no paragraph/search content overlays slider rows.
- Adjust font size, line height, and column gap; verify values update and content repaginates without breaking navigation.
- Change theme and verify immediate visual update.
- From hamburger, verify `Read Aloud` toggle and speed control both work.
- Verify `Speed Reading` action appears at the bottom of the hamburger menu.
- Verify highlight/note/bookmark actions from hamburger operate on current paragraph.
- Verify chapter list remains usable from touch controls.
- Verify bottom touch navigation (chapter/page/paragraph) works and buttons disable appropriately at bounds.
- Verify orientation change (portrait <-> landscape) keeps controls usable and content readable.

## Desktop Keyboard Regression Checklist

- Confirm desktop header search remains visible and usable.
- Verify `h`/`l` page navigation.
- Verify `j`/`k` paragraph navigation.
- Verify `H`/`L` chapter navigation.
- Verify `/` focuses search.
- Verify `c` opens chapter list.
- Verify `u`, `n`, `b`, `B` annotation flows.
- Verify `?` shortcuts overlay.
- Verify chapter list keyboard behavior still works (`ArrowUp`, `ArrowDown`, `Enter`, `Escape`).
- Verify opening/closing overlays does not leave reader in broken focus state.
- Verify search result navigation still highlights expected terms and lands on target paragraph.

## Sign-off Template

- iOS Safari: Pass/Fail
- Android Chrome: Pass/Fail
- Desktop regression: Pass/Fail
- Notes / defects:
