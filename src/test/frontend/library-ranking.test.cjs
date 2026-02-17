const test = require('node:test');
const assert = require('node:assert/strict');

const {
    compareForActiveQueue,
    compareForCompleted
} = require('../../main/resources/static/js/library-ranking.js');

function createEntry({
    id,
    title,
    favorite = false,
    maxProgressRatio = 0,
    completed = false,
    lastReadAt = null,
    lastOpenedAt = null,
    completedAt = null
}) {
    return {
        book: { id, title },
        favorite,
        activity: {
            maxProgressRatio,
            completed,
            lastReadAt,
            lastOpenedAt,
            completedAt
        }
    };
}

test('active ranking prioritizes in-progress over unread even when unread is more recent', () => {
    const inProgressOlder = createEntry({
        id: 'a',
        title: 'In Progress',
        maxProgressRatio: 0.2,
        lastReadAt: '2026-02-01T12:00:00.000Z'
    });
    const unreadRecent = createEntry({
        id: 'b',
        title: 'Unread Recent',
        maxProgressRatio: 0,
        lastReadAt: '2026-02-10T12:00:00.000Z'
    });

    const ranked = [unreadRecent, inProgressOlder].sort(compareForActiveQueue);
    assert.equal(ranked[0].book.id, 'a');
});

test('active ranking uses recency then favorite then progress depth as tie-breakers', () => {
    const sameStateOlder = createEntry({
        id: 'older',
        title: 'Older',
        favorite: true,
        maxProgressRatio: 0.4,
        lastReadAt: '2026-02-02T10:00:00.000Z'
    });
    const sameStateNewer = createEntry({
        id: 'newer',
        title: 'Newer',
        favorite: false,
        maxProgressRatio: 0.1,
        lastReadAt: '2026-02-03T10:00:00.000Z'
    });
    const sameRecencyFavorite = createEntry({
        id: 'fav',
        title: 'Fav',
        favorite: true,
        maxProgressRatio: 0.35,
        lastReadAt: '2026-02-04T10:00:00.000Z'
    });
    const sameRecencyNotFavoriteHigherProgress = createEntry({
        id: 'not-fav',
        title: 'Not Fav',
        favorite: false,
        maxProgressRatio: 0.8,
        lastReadAt: '2026-02-04T10:00:00.000Z'
    });
    const sameEverythingTitleA = createEntry({
        id: 'id-b',
        title: 'Alpha',
        favorite: false,
        maxProgressRatio: 0.1,
        lastReadAt: '2026-02-05T10:00:00.000Z'
    });
    const sameEverythingTitleB = createEntry({
        id: 'id-a',
        title: 'Alpha',
        favorite: false,
        maxProgressRatio: 0.1,
        lastReadAt: '2026-02-05T10:00:00.000Z'
    });

    const ranked = [
        sameStateOlder,
        sameStateNewer,
        sameRecencyFavorite,
        sameRecencyNotFavoriteHigherProgress,
        sameEverythingTitleA,
        sameEverythingTitleB
    ].sort(compareForActiveQueue);

    // Same state: newer outranks older.
    assert.ok(ranked.findIndex(e => e.book.id === 'newer') < ranked.findIndex(e => e.book.id === 'older'));
    // Same state+recency: favorite outranks non-favorite even with lower progress.
    assert.ok(ranked.findIndex(e => e.book.id === 'fav') < ranked.findIndex(e => e.book.id === 'not-fav'));
    // Same title/state/recency/favorite/progress: id provides deterministic order.
    assert.ok(ranked.findIndex(e => e.book.id === 'id-a') < ranked.findIndex(e => e.book.id === 'id-b'));
});

test('completed ranking prioritizes completedAt then recency then favorite', () => {
    const finishedLater = createEntry({
        id: 'later',
        title: 'Finished Later',
        completed: true,
        maxProgressRatio: 1,
        completedAt: '2026-02-10T12:00:00.000Z',
        lastReadAt: '2026-02-10T12:00:00.000Z'
    });
    const finishedEarlier = createEntry({
        id: 'earlier',
        title: 'Finished Earlier',
        completed: true,
        maxProgressRatio: 1,
        completedAt: '2026-02-09T12:00:00.000Z',
        lastReadAt: '2026-02-11T12:00:00.000Z'
    });
    const sameCompletedAtFav = createEntry({
        id: 'fav',
        title: 'Fav Complete',
        favorite: true,
        completed: true,
        maxProgressRatio: 1,
        completedAt: '2026-02-08T12:00:00.000Z',
        lastReadAt: '2026-02-08T12:00:00.000Z'
    });
    const sameCompletedAtNotFav = createEntry({
        id: 'not-fav',
        title: 'Not Fav Complete',
        favorite: false,
        completed: true,
        maxProgressRatio: 1,
        completedAt: '2026-02-08T12:00:00.000Z',
        lastReadAt: '2026-02-08T12:00:00.000Z'
    });

    const ranked = [
        sameCompletedAtNotFav,
        finishedEarlier,
        sameCompletedAtFav,
        finishedLater
    ].sort(compareForCompleted);

    assert.equal(ranked[0].book.id, 'later');
    assert.ok(ranked.findIndex(e => e.book.id === 'earlier') < ranked.findIndex(e => e.book.id === 'fav'));
    assert.ok(ranked.findIndex(e => e.book.id === 'fav') < ranked.findIndex(e => e.book.id === 'not-fav'));
});
