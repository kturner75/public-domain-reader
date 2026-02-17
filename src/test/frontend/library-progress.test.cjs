const test = require('node:test');
const assert = require('node:assert/strict');

const {
    buildBookProgressSnapshot
} = require('../../main/resources/static/js/library-progress.js');

test('returns not-started snapshot at 0% progress', () => {
    const snapshot = buildBookProgressSnapshot({
        chapterCount: 12,
        maxProgressRatio: 0
    });

    assert.deepEqual(snapshot, {
        chapterLabel: 'Chapter 0/12',
        percentLabel: '0%',
        statusLabel: 'Not Started',
        statusClass: 'not-started'
    });
});

test('returns in-progress snapshot for mid-progress book', () => {
    const snapshot = buildBookProgressSnapshot({
        chapterCount: 8,
        lastChapterIndex: 2,
        maxProgressRatio: 0.36
    });

    assert.deepEqual(snapshot, {
        chapterLabel: 'Chapter 3/8',
        percentLabel: '36%',
        statusLabel: 'In Progress',
        statusClass: 'in-progress'
    });
});

test('returns completed snapshot when book is completed', () => {
    const snapshot = buildBookProgressSnapshot({
        chapterCount: 5,
        lastChapterIndex: 3,
        maxProgressRatio: 0.81,
        completed: true
    });

    assert.deepEqual(snapshot, {
        chapterLabel: 'Chapter 5/5',
        percentLabel: '100%',
        statusLabel: 'Completed',
        statusClass: 'completed'
    });
});

test('clamps invalid values to safe defaults', () => {
    const snapshot = buildBookProgressSnapshot({
        chapterCount: -3,
        lastChapterIndex: -10,
        maxProgressRatio: -1
    });

    assert.deepEqual(snapshot, {
        chapterLabel: 'Chapter 0/1',
        percentLabel: '0%',
        statusLabel: 'Not Started',
        statusClass: 'not-started'
    });
});
