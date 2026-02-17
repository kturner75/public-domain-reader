const test = require('node:test');
const assert = require('node:assert/strict');

const {
    buildRecommendations
} = require('../../main/resources/static/js/library-discover.js');

function createEntry({
    id,
    title,
    author,
    description = '',
    favorite = false,
    maxProgressRatio = 0,
    completed = false,
    lastReadAt = null,
    lastOpenedAt = null
}) {
    return {
        book: { id, title, author, description },
        favorite,
        activity: {
            maxProgressRatio,
            completed,
            lastReadAt,
            lastOpenedAt
        }
    };
}

function createCatalogBook({
    gutenbergId,
    title,
    author,
    downloadCount = 1000,
    subjects = [],
    bookshelves = [],
    alreadyImported = false
}) {
    return {
        gutenbergId,
        title,
        author,
        downloadCount,
        subjects,
        bookshelves,
        alreadyImported
    };
}

test('cold start discover falls back to popularity order with popular reason', () => {
    const catalog = [
        createCatalogBook({ gutenbergId: 1, title: 'Less Popular', author: 'Author A', downloadCount: 100 }),
        createCatalogBook({ gutenbergId: 2, title: 'Most Popular', author: 'Author B', downloadCount: 100000 })
    ];

    const ranked = buildRecommendations(catalog, []);

    assert.equal(ranked[0].gutenbergId, 2);
    assert.equal(ranked[0].discoverReason, 'Popular with readers right now');
    assert.equal(ranked[1].discoverReason, 'Popular with readers right now');
});

test('discover prioritizes same-author recommendations from favorites', () => {
    const localEntries = [
        createEntry({
            id: 'local-1',
            title: 'Pride and Prejudice',
            author: 'Jane Austen',
            description: 'Courtship fiction, England -- Fiction',
            favorite: true,
            maxProgressRatio: 0.55,
            lastReadAt: '2026-02-16T10:00:00.000Z'
        })
    ];

    const catalog = [
        createCatalogBook({
            gutenbergId: 20,
            title: 'Sense and Sensibility',
            author: 'Jane Austen',
            downloadCount: 30000,
            subjects: ['England -- Fiction']
        }),
        createCatalogBook({
            gutenbergId: 21,
            title: 'Random Popular',
            author: 'Other Author',
            downloadCount: 500000
        })
    ];

    const ranked = buildRecommendations(catalog, localEntries);

    assert.equal(ranked[0].gutenbergId, 20);
    assert.match(ranked[0].discoverReason, /^Because you liked Pride and Prejudice/);
});

test('discover uses genre affinity when author does not match', () => {
    const localEntries = [
        createEntry({
            id: 'local-2',
            title: 'Moby Dick',
            author: 'Herman Melville',
            description: 'Sea stories, Adventure stories',
            favorite: false,
            maxProgressRatio: 0.35,
            lastReadAt: '2026-02-15T12:00:00.000Z'
        })
    ];

    const catalog = [
        createCatalogBook({
            gutenbergId: 31,
            title: 'The Sea-Wolf',
            author: 'Jack London',
            downloadCount: 10000,
            subjects: ['Sea stories']
        }),
        createCatalogBook({
            gutenbergId: 32,
            title: 'Unrelated Bestseller',
            author: 'Another Author',
            downloadCount: 200000
        })
    ];

    const ranked = buildRecommendations(catalog, localEntries);

    assert.equal(ranked[0].gutenbergId, 31);
    assert.match(ranked[0].discoverReason, /^Because you liked Moby Dick/);
});

test('discover ranking is deterministic on ties via title then gutenberg id', () => {
    const catalog = [
        createCatalogBook({ gutenbergId: 102, title: 'Alpha', author: 'Author', downloadCount: 1500 }),
        createCatalogBook({ gutenbergId: 101, title: 'Alpha', author: 'Author', downloadCount: 1500 }),
        createCatalogBook({ gutenbergId: 103, title: 'Beta', author: 'Author', downloadCount: 1500 })
    ];

    const ranked = buildRecommendations(catalog, []);

    assert.deepEqual(
        ranked.map(book => book.gutenbergId),
        [101, 102, 103]
    );
});
