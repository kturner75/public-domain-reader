# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Public Domain Classics Reader - A distraction-free web reader for public domain literature. The goal is to create a peaceful deep-reading experience that feels like holding a physical book.

## Build Commands

```bash
mvn clean compile      # Compile the project
mvn test               # Run all tests
mvn clean package      # Build the JAR artifact
mvn spring-boot:run    # Run the application (default port 8080)
mvn test -Dtest=TestClassName           # Run a single test class
mvn test -Dtest=TestClassName#testMethod # Run a single test method
```

## Tech Stack

- **Backend:** Java 21 with Spring Boot + Lucene (or SQLite) for search/storage
- **Frontend:** Pure vanilla HTML/CSS/JS (no frameworks)
- **Data:** EPUB/HTML from Standard Ebooks or Project Gutenberg
- **Build:** Maven

## Architecture (Planned)

The application serves a two-column, no-scroll reading experience:

- Backend REST API provides book content, search, and library management
- Frontend renders dynamically viewport-fitted pages with top-to-bottom column flow
- LocalStorage persists user state (reading position, font size, bookmarks)

## Design Principles

- **No scrolling** - Content is paginated to fit the viewport exactly
- **Two-column layout** - Text flows top-to-bottom in each column, then to the next
- **Minimalist UI** - Optional features stay hidden until invoked
- **High legibility** - EB Garamond or similar serif typography
