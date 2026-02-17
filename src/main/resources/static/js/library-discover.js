(function(root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
        return;
    }
    root.LibraryDiscover = factory();
})(typeof globalThis !== 'undefined' ? globalThis : this, function() {
    'use strict';

    function clampNumber(value, min, max) {
        const parsed = Number(value);
        const safe = Number.isFinite(parsed) ? parsed : min;
        return Math.min(max, Math.max(min, safe));
    }

    function toTimestamp(value) {
        if (!value || typeof value !== 'string') {
            return 0;
        }
        const parsed = Date.parse(value);
        return Number.isFinite(parsed) ? parsed : 0;
    }

    function normalizeText(value) {
        if (!value || typeof value !== 'string') {
            return '';
        }
        return value
            .toLowerCase()
            .replace(/[^a-z0-9\s]/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function normalizePhrase(value) {
        return normalizeText(value);
    }

    function splitDescriptionIntoPhrases(description) {
        if (!description || typeof description !== 'string') {
            return [];
        }
        return description
            .split(/[;,|]+/)
            .map(part => part.trim())
            .filter(Boolean);
    }

    function uniqueNormalizedPhrases(values) {
        const seen = new Set();
        const output = [];
        (values || []).forEach((value) => {
            const normalized = normalizePhrase(value);
            if (!normalized || seen.has(normalized)) {
                return;
            }
            seen.add(normalized);
            output.push(normalized);
        });
        return output;
    }

    function tokenizePhrases(phrases) {
        const tokens = new Set();
        (phrases || []).forEach((phrase) => {
            phrase.split(' ').forEach((part) => {
                const token = part.trim();
                if (token.length >= 4) {
                    tokens.add(token);
                }
            });
        });
        return tokens;
    }

    function toCatalogGenrePhrases(book) {
        const subjects = Array.isArray(book?.subjects) ? book.subjects : [];
        const bookshelves = Array.isArray(book?.bookshelves) ? book.bookshelves : [];
        return uniqueNormalizedPhrases([...subjects, ...bookshelves]);
    }

    function toLocalGenrePhrases(book) {
        const descriptionPhrases = splitDescriptionIntoPhrases(book?.description || '');
        return uniqueNormalizedPhrases(descriptionPhrases);
    }

    function toAuthorKey(author) {
        return normalizePhrase(author);
    }

    function toProgress(entry) {
        const progress = Number(entry?.activity?.maxProgressRatio);
        if (!Number.isFinite(progress)) {
            return 0;
        }
        return clampNumber(progress, 0, 1);
    }

    function isCompleted(entry) {
        return Boolean(entry?.activity?.completed) || toProgress(entry) >= 0.999;
    }

    function isInProgress(entry) {
        return !isCompleted(entry) && toProgress(entry) > 0;
    }

    function getRecency(entry) {
        return Math.max(
            toTimestamp(entry?.activity?.lastReadAt),
            toTimestamp(entry?.activity?.lastOpenedAt),
            0
        );
    }

    function compareSeedStable(a, b) {
        const scoreDiff = b.score - a.score;
        if (Math.abs(scoreDiff) > 0.0001) {
            return scoreDiff;
        }

        const recencyDiff = b.recency - a.recency;
        if (recencyDiff !== 0) {
            return recencyDiff;
        }

        const titleDiff = (a?.title || '').localeCompare(b?.title || '', undefined, { sensitivity: 'base' });
        if (titleDiff !== 0) {
            return titleDiff;
        }

        return (a?.bookId || '').localeCompare(b?.bookId || '', undefined, { sensitivity: 'base' });
    }

    function buildAffinitySeeds(localEntries) {
        const entries = Array.isArray(localEntries) ? localEntries : [];
        if (entries.length === 0) {
            return [];
        }

        const byRecency = [...entries].sort((a, b) => getRecency(b) - getRecency(a));
        const recencyRank = new Map();
        byRecency.forEach((entry, index) => {
            const key = entry?.book?.id || `${index}`;
            recencyRank.set(key, index);
        });

        const seeds = entries.map((entry, index) => {
            const book = entry?.book || {};
            const bookId = typeof book.id === 'string' ? book.id : `${index}`;
            const recency = getRecency(entry);
            const rank = recencyRank.get(bookId) ?? index;
            const recencyBoost = recency > 0 ? Math.max(0, 35 - (rank * 6)) : 0;

            let score = 15;
            if (Boolean(entry?.favorite)) {
                score += 140;
            }
            if (isInProgress(entry)) {
                score += 90;
            } else if (isCompleted(entry)) {
                score += 50;
            }
            score += Math.round(toProgress(entry) * 40);
            score += recencyBoost;

            const genrePhrases = toLocalGenrePhrases(book);
            return {
                bookId,
                title: typeof book.title === 'string' ? book.title : 'a book you read',
                authorKey: toAuthorKey(book.author),
                genrePhrases,
                genreTokens: tokenizePhrases(genrePhrases),
                score,
                recency
            };
        });

        return seeds
            .sort(compareSeedStable)
            .slice(0, 8);
    }

    function countSharedTokens(setA, setB) {
        if (!setA || !setB || setA.size === 0 || setB.size === 0) {
            return 0;
        }
        let count = 0;
        setA.forEach((token) => {
            if (setB.has(token)) {
                count += 1;
            }
        });
        return count;
    }

    function compareRecommendationStable(a, b) {
        const scoreDiff = b.totalScore - a.totalScore;
        if (Math.abs(scoreDiff) > 0.0001) {
            return scoreDiff;
        }

        const importedDiff = Number(Boolean(a?.book?.alreadyImported)) - Number(Boolean(b?.book?.alreadyImported));
        if (importedDiff !== 0) {
            return importedDiff;
        }

        const popularityDiff = Number(b?.book?.downloadCount || 0) - Number(a?.book?.downloadCount || 0);
        if (popularityDiff !== 0) {
            return popularityDiff;
        }

        const titleDiff = (a?.book?.title || '').localeCompare(b?.book?.title || '', undefined, { sensitivity: 'base' });
        if (titleDiff !== 0) {
            return titleDiff;
        }

        return Number(a?.book?.gutenbergId || 0) - Number(b?.book?.gutenbergId || 0);
    }

    function scoreCatalogBook(book, seeds) {
        const authorKey = toAuthorKey(book?.author);
        const genrePhrases = toCatalogGenrePhrases(book);
        const genrePhraseSet = new Set(genrePhrases);
        const genreTokenSet = tokenizePhrases(genrePhrases);

        const defaultReason = seeds.length > 0
            ? 'Popular with readers like you'
            : 'Popular with readers right now';

        let bestAffinity = 0;
        let bestReason = defaultReason;
        let bestReasonType = 'popularity';

        seeds.forEach((seed) => {
            let affinity = 0;
            const authorMatch = Boolean(authorKey) && Boolean(seed.authorKey) && authorKey === seed.authorKey;
            if (authorMatch) {
                affinity += 280 + (seed.score * 1.4);
            }

            const phraseOverlap = countSharedTokens(genrePhraseSet, new Set(seed.genrePhrases));
            const tokenOverlap = phraseOverlap > 0
                ? phraseOverlap
                : countSharedTokens(genreTokenSet, seed.genreTokens);
            if (tokenOverlap > 0) {
                affinity += (tokenOverlap * 46) + seed.score;
            }

            if (affinity <= 0 || affinity <= bestAffinity) {
                return;
            }

            bestAffinity = affinity;
            if (authorMatch) {
                bestReason = `Because you liked ${seed.title} (same author)`;
                bestReasonType = 'author';
                return;
            }

            bestReason = `Because you liked ${seed.title} (similar themes)`;
            bestReasonType = 'genre';
        });

        const popularityScore = Math.log10(Math.max(1, Number(book?.downloadCount) || 0) + 10) * 12;
        const importedPenalty = Boolean(book?.alreadyImported) ? -45 : 0;

        return {
            book,
            totalScore: bestAffinity + popularityScore + importedPenalty,
            reason: bestReason,
            reasonType: bestReasonType
        };
    }

    function buildRecommendations(catalogBooks, localEntries) {
        const catalog = Array.isArray(catalogBooks) ? catalogBooks : [];
        const seeds = buildAffinitySeeds(localEntries);

        return catalog
            .map(book => scoreCatalogBook(book, seeds))
            .sort(compareRecommendationStable)
            .map(item => ({
                ...item.book,
                discoverReason: item.reason,
                discoverReasonType: item.reasonType
            }));
    }

    return {
        buildAffinitySeeds,
        buildRecommendations
    };
});
