(function(root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
        return;
    }
    root.CitationUtils = factory();
})(typeof globalThis !== 'undefined' ? globalThis : this, function() {
    'use strict';

    function citationPreviewText(citation, maxLength = 280) {
        const normalized = typeof citation === 'string'
            ? citation.replace(/\s+/g, ' ').trim()
            : '';
        if (!normalized) {
            return '';
        }
        if (normalized.length <= maxLength) {
            return normalized;
        }
        return `${normalized.slice(0, Math.max(1, maxLength - 1)).trimEnd()}…`;
    }

    async function copyTextToClipboard(text, env = {}) {
        const navigatorObj = env.navigator
            || (typeof navigator !== 'undefined' ? navigator : null);
        if (navigatorObj?.clipboard && typeof navigatorObj.clipboard.writeText === 'function') {
            try {
                await navigatorObj.clipboard.writeText(text);
                return;
            } catch (_error) {
                // Fall through to legacy copy path for browsers that expose clipboard
                // but still reject writes in some user gesture/security contexts.
            }
        }

        const documentObj = env.document
            || (typeof document !== 'undefined' ? document : null);
        if (!documentObj
            || typeof documentObj.createElement !== 'function'
            || !documentObj.body
            || typeof documentObj.body.appendChild !== 'function'
            || typeof documentObj.body.removeChild !== 'function'
            || typeof documentObj.execCommand !== 'function') {
            throw new Error('Clipboard API unavailable');
        }

        const textArea = documentObj.createElement('textarea');
        textArea.value = text;
        textArea.setAttribute('readonly', '');
        textArea.style.position = 'fixed';
        textArea.style.top = '-9999px';
        textArea.style.left = '-9999px';
        documentObj.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        textArea.setSelectionRange(0, text.length);
        const copied = documentObj.execCommand('copy');
        documentObj.body.removeChild(textArea);
        if (!copied) {
            throw new Error('Clipboard copy command failed');
        }
    }

    return {
        citationPreviewText,
        copyTextToClipboard
    };
});
