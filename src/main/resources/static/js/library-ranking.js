(function(root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
        return;
    }
    root.LibraryRanking = factory();
})(typeof globalThis !== 'undefined' ? globalThis : this, function() {
    'use strict';

    function toTimestamp(value) {
        if (!value || typeof value !== 'string') {
            return 0;
        }
        const parsed = Date.parse(value);
        return Number.isFinite(parsed) ? parsed : 0;
    }

    function toProgress(entry) {
        const progress = Number(entry?.activity?.maxProgressRatio);
        if (!Number.isFinite(progress)) {
            return 0;
        }
        return Math.max(0, Math.min(1, progress));
    }

    function isCompleted(entry) {
        return Boolean(entry?.activity?.completed) || toProgress(entry) >= 0.999;
    }

    function isInProgress(entry) {
        return !isCompleted(entry) && toProgress(entry) > 0;
    }

    function getCompletionStateRank(entry) {
        if (isInProgress(entry)) {
            return 2;
        }
        if (!isCompleted(entry)) {
            return 1;
        }
        return 0;
    }

    function getRecencyTimestamp(entry) {
        return Math.max(
            toTimestamp(entry?.activity?.lastReadAt),
            toTimestamp(entry?.activity?.lastOpenedAt),
            0
        );
    }

    function compareByTitleAndId(a, b) {
        const titleDiff = (a?.book?.title || '').localeCompare(b?.book?.title || '', undefined, { sensitivity: 'base' });
        if (titleDiff !== 0) {
            return titleDiff;
        }
        return (a?.book?.id || '').localeCompare(b?.book?.id || '', undefined, { sensitivity: 'base' });
    }

    // Active queue tie-break order:
    // 1) Completion state (in-progress > not-started > completed)
    // 2) Recency
    // 3) Favorite intent
    // 4) Progress depth
    // 5) Title / id
    function compareForActiveQueue(a, b) {
        const stateDiff = getCompletionStateRank(b) - getCompletionStateRank(a);
        if (stateDiff !== 0) {
            return stateDiff;
        }

        const recencyDiff = getRecencyTimestamp(b) - getRecencyTimestamp(a);
        if (recencyDiff !== 0) {
            return recencyDiff;
        }

        const favoriteDiff = Number(Boolean(b?.favorite)) - Number(Boolean(a?.favorite));
        if (favoriteDiff !== 0) {
            return favoriteDiff;
        }

        const progressDiff = toProgress(b) - toProgress(a);
        if (Math.abs(progressDiff) > 0.0001) {
            return progressDiff;
        }

        return compareByTitleAndId(a, b);
    }

    function compareForCompleted(a, b) {
        const completedDiff = toTimestamp(b?.activity?.completedAt) - toTimestamp(a?.activity?.completedAt);
        if (completedDiff !== 0) {
            return completedDiff;
        }

        const recencyDiff = getRecencyTimestamp(b) - getRecencyTimestamp(a);
        if (recencyDiff !== 0) {
            return recencyDiff;
        }

        const favoriteDiff = Number(Boolean(b?.favorite)) - Number(Boolean(a?.favorite));
        if (favoriteDiff !== 0) {
            return favoriteDiff;
        }

        return compareByTitleAndId(a, b);
    }

    return {
        toTimestamp,
        isCompleted,
        isInProgress,
        compareForActiveQueue,
        compareForCompleted
    };
});
