const test = require('node:test');
const assert = require('node:assert/strict');

const {
    citationPreviewText,
    copyTextToClipboard
} = require('../../main/resources/static/js/citation-utils.js');

test('citationPreviewText normalizes whitespace', () => {
    const preview = citationPreviewText('  Austen,   Jane.\nPride   and   Prejudice.  ');
    assert.equal(preview, 'Austen, Jane. Pride and Prejudice.');
});

test('citationPreviewText truncates with ellipsis when over max length', () => {
    const preview = citationPreviewText('abcdefghijklmnopqrstuvwxyz', 10);
    assert.equal(preview, 'abcdefghi…');
});

test('copyTextToClipboard prefers navigator clipboard API', async () => {
    const calls = [];
    const navigatorMock = {
        clipboard: {
            writeText(value) {
                calls.push(value);
                return Promise.resolve();
            }
        }
    };

    await copyTextToClipboard('MLA citation', { navigator: navigatorMock });
    assert.deepEqual(calls, ['MLA citation']);
});

test('copyTextToClipboard falls back to document.execCommand', async () => {
    const appended = [];
    const removed = [];
    const commands = [];
    let selectedRange = null;

    const body = {
        appendChild(node) {
            appended.push(node);
        },
        removeChild(node) {
            removed.push(node);
        }
    };
    const documentMock = {
        body,
        createElement(tag) {
            assert.equal(tag, 'textarea');
            return {
                value: '',
                style: {},
                setAttribute() {},
                focus() {},
                select() {},
                setSelectionRange(start, end) {
                    selectedRange = [start, end];
                }
            };
        },
        execCommand(command) {
            commands.push(command);
            return true;
        }
    };

    await copyTextToClipboard('citation text', {
        navigator: {},
        document: documentMock
    });

    assert.deepEqual(commands, ['copy']);
    assert.equal(appended.length, 1);
    assert.equal(removed.length, 1);
    assert.deepEqual(selectedRange, [0, 'citation text'.length]);
});

test('copyTextToClipboard falls back when navigator clipboard write fails', async () => {
    const commands = [];
    const documentMock = {
        body: {
            appendChild() {},
            removeChild() {}
        },
        createElement() {
            return {
                value: '',
                style: {},
                setAttribute() {},
                focus() {},
                select() {},
                setSelectionRange() {}
            };
        },
        execCommand(command) {
            commands.push(command);
            return true;
        }
    };
    const navigatorMock = {
        clipboard: {
            writeText() {
                return Promise.reject(new Error('NotAllowedError'));
            }
        }
    };

    await copyTextToClipboard('citation text', {
        navigator: navigatorMock,
        document: documentMock
    });

    assert.deepEqual(commands, ['copy']);
});

test('copyTextToClipboard throws when no clipboard mechanism exists', async () => {
    await assert.rejects(
        () => copyTextToClipboard('citation text', { navigator: {}, document: null }),
        /Clipboard API unavailable/
    );
});
