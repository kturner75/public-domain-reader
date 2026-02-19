const { test, expect } = require('@playwright/test');

const TEST_BOOK = {
  id: 'book-1',
  title: 'Account Flow Test Book',
  author: 'Casey Reader',
  chapters: [
    { id: 'ch-1', title: 'Chapter One' }
  ],
  ttsEnabled: false,
  illustrationEnabled: false,
  characterEnabled: false
};

function json(route, status, payload) {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(payload)
  });
}

async function installApiMocks(page) {
  const state = {
    accountEnabled: true,
    accountAuthenticated: false,
    accountEmail: null,
    claimSyncRequests: []
  };

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (method === 'GET' && path === '/api/classroom/context') {
      return json(route, 200, { enrolled: false });
    }
    if (method === 'GET' && path === '/api/library') {
      return json(route, 200, [TEST_BOOK]);
    }
    if (method === 'GET' && path === '/api/import/popular') {
      return json(route, 200, []);
    }
    if (method === 'GET' && path === '/api/features') {
      return json(route, 200, { speedReadingEnabled: false });
    }
    if (method === 'GET' && path === '/api/account/status') {
      return json(route, 200, {
        accountAuthEnabled: state.accountEnabled,
        authenticated: state.accountAuthenticated,
        email: state.accountEmail,
        rolloutMode: 'optional',
        accountRequired: false
      });
    }
    if (method === 'POST' && path === '/api/account/register') {
      const body = request.postDataJSON();
      state.accountAuthenticated = true;
      state.accountEmail = (body.email || '').toLowerCase();
      return json(route, 200, {
        accountAuthEnabled: true,
        authenticated: true,
        email: state.accountEmail,
        message: 'Account created.',
        rolloutMode: 'optional',
        accountRequired: false
      });
    }
    if (method === 'POST' && path === '/api/account/login') {
      const body = request.postDataJSON();
      state.accountAuthenticated = true;
      state.accountEmail = (body.email || '').toLowerCase();
      return json(route, 200, {
        accountAuthEnabled: true,
        authenticated: true,
        email: state.accountEmail,
        message: 'Signed in.',
        rolloutMode: 'optional',
        accountRequired: false
      });
    }
    if (method === 'POST' && path === '/api/account/logout') {
      state.accountAuthenticated = false;
      state.accountEmail = null;
      return json(route, 200, {
        accountAuthEnabled: true,
        authenticated: false,
        email: null,
        message: 'Signed out.',
        rolloutMode: 'optional',
        accountRequired: false
      });
    }
    if (method === 'POST' && path === '/api/account/claim-sync') {
      const body = request.postDataJSON();
      state.claimSyncRequests.push(body);
      return json(route, 200, {
        claimApplied: true,
        state: {
          favoriteBookIds: ['book-1'],
          bookActivity: body?.state?.bookActivity || {},
          readerPreferences: body?.state?.readerPreferences || null,
          recapOptOut: {
            'book-1': false
          }
        }
      });
    }
    if (method === 'GET' && path === '/api/auth/status') {
      return json(route, 200, {
        publicMode: false,
        authRequired: false,
        authenticated: false,
        canAccessSensitive: true
      });
    }
    if (method === 'GET' && path === '/api/tts/status') {
      return json(route, 200, {
        openaiConfigured: false,
        cachedAvailable: false,
        cacheOnly: false
      });
    }
    if (method === 'GET' && path === '/api/illustrations/status') {
      return json(route, 200, {
        comfyuiAvailable: false,
        ollamaAvailable: false,
        allowPromptEditing: false,
        cacheOnly: false
      });
    }
    if (method === 'GET' && path === '/api/characters/status') {
      return json(route, 200, {
        enabled: false,
        cacheOnly: false,
        chatEnabled: false,
        chatProviderAvailable: false
      });
    }
    if (method === 'GET' && path === '/api/recaps/status') {
      return json(route, 200, {
        enabled: false,
        reasoningEnabled: false,
        available: false,
        cacheOnly: false,
        chatEnabled: false,
        chatProviderAvailable: false
      });
    }
    if (method === 'GET' && path === '/api/quizzes/status') {
      return json(route, 200, {
        enabled: false,
        reasoningEnabled: false,
        available: false,
        cacheOnly: false,
        generationAvailable: false
      });
    }

    return json(route, 404, {
      error: `Unhandled API route in account e2e mock: ${method} ${path}`
    });
  });

  return state;
}

test('account register/login/logout flow runs one-time claim sync for anonymous state', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('reader_favoriteBooks', JSON.stringify(['book-1']));
    localStorage.setItem('reader_bookActivity', JSON.stringify({
      'book-1': {
        chapterCount: 10,
        lastChapterIndex: 1,
        lastPage: 1,
        totalPages: 2,
        progressRatio: 0.5,
        maxProgressRatio: 0.5,
        completed: false,
        openCount: 3,
        lastOpenedAt: '2026-02-18T00:00:00Z',
        lastReadAt: '2026-02-18T00:00:00Z',
        completedAt: null
      }
    }));
    localStorage.setItem('reader_readerPreferences', JSON.stringify({
      fontSize: 1.2,
      lineHeight: 1.7,
      columnGap: 4,
      theme: 'warm'
    }));
    localStorage.setItem('reader_readerPreferencesUpdatedAt', '2026-02-18T00:00:00Z');
    localStorage.setItem('reader_recapOptOut_book-1', 'true');
  });

  const mockState = await installApiMocks(page);

  await page.goto('/');
  await expect(page.locator('#account-toggle-library')).toBeVisible();
  await page.click('#account-toggle-library');

  await page.fill('#account-email', 'reader@example.com');
  await page.fill('#account-password', 'password123');
  await page.click('#account-register');

  await expect.poll(() => mockState.claimSyncRequests.length).toBe(1);
  expect(mockState.claimSyncRequests[0]?.state?.favoriteBookIds).toEqual(['book-1']);

  await expect(page.locator('#account-library-status')).toContainText('Signed in as reader@example.com');
  const favoritesAfterRegister = await page.evaluate(() => localStorage.getItem('reader_favoriteBooks'));
  expect(favoritesAfterRegister).toBe(JSON.stringify(['book-1']));

  await page.click('#account-toggle-library');
  await expect(page.locator('#account-signout')).toBeVisible();
  await page.click('#account-signout');
  await expect(page.locator('#account-library-status')).toBeHidden();

  await page.click('#account-toggle-library');
  await page.fill('#account-email', 'reader@example.com');
  await page.fill('#account-password', 'password123');
  await page.click('#account-signin');

  await expect.poll(() => mockState.claimSyncRequests.length).toBe(2);
  await expect(page.locator('#account-library-status')).toContainText('Signed in as reader@example.com');
});
