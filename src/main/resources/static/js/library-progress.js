(function(root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
        return;
    }
    root.LibraryProgress = factory();
})(typeof globalThis !== 'undefined' ? globalThis : this, function() {
    'use strict';

    function clampNumber(value, min, max) {
        return Math.min(max, Math.max(min, value));
    }

    function toFiniteNumber(value, fallback) {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : fallback;
    }

    function clampInteger(value, min, max) {
        return Math.floor(clampNumber(toFiniteNumber(value, min), min, max));
    }

    function buildBookProgressSnapshot(activity) {
        const chapterCount = Math.max(1, clampInteger(activity?.chapterCount, 1, Number.MAX_SAFE_INTEGER));
        const rawProgress = clampNumber(toFiniteNumber(activity?.maxProgressRatio, 0), 0, 1);
        const completed = Boolean(activity?.completed) || rawProgress >= 0.999;

        let chapterNumber = 0;
        let percentComplete = clampInteger(Math.round(rawProgress * 100), 0, 100);
        let statusLabel = 'Not Started';
        let statusClass = 'not-started';

        if (completed) {
            chapterNumber = chapterCount;
            percentComplete = 100;
            statusLabel = 'Completed';
            statusClass = 'completed';
        } else if (rawProgress > 0) {
            chapterNumber = clampInteger(toFiniteNumber(activity?.lastChapterIndex, 0) + 1, 1, chapterCount);
            percentComplete = Math.max(1, percentComplete);
            statusLabel = 'In Progress';
            statusClass = 'in-progress';
        }

        return {
            chapterLabel: `Chapter ${chapterNumber}/${chapterCount}`,
            percentLabel: `${percentComplete}%`,
            statusLabel,
            statusClass
        };
    }

    return {
        buildBookProgressSnapshot
    };
});
