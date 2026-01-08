# Project Brief: Public Domain Classics Reader

A distraction-free, web reader for public domain literature.

Core Purpose  
Create the most focused, peaceful deep-reading experience possible for classic books — feeling like holding a beautiful physical volume in your hands.

Key Principles
- Absolute minimalism: zero clutter, zero scrolling
- Dynamic viewport-fitted "pages" — exactly two columns, top-to-bottom flow
- Calm, high-legibility typography (EB Garamond or similar serif)
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

