// End-to-end check of a RUNNING stack, through the API gateway (needs Node 18+).
//   docker compose up --build        (wait until it settles)
//   node scripts/smoke-test.mjs
//
// It exercises every service, Feign (names resolved via user-service) and Kafka
// (a comment in one account becomes a notification in another).

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL || 'admin@bookhive.local';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'Admin@12345';
const run = Math.random().toString(36).slice(2, 8);

async function call(method, path, { token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* not json */ }
  return { status: res.status, json, text };
}

const results = [];
async function step(name, fn) {
  try {
    await fn();
    results.push({ name, ok: true });
    console.log(`PASS  ${name}`);
  } catch (e) {
    results.push({ name, ok: false, error: e.message });
    console.log(`FAIL  ${name}\n      ${e.message}`);
  }
}
function expect(cond, message) { if (!cond) throw new Error(message); }
function expectStatus(res, status, what) {
  expect(res.status === status, `${what}: expected ${status}, got ${res.status} ${res.text.slice(0, 200)}`);
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function waitFor(what, check, timeoutMs = 30000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await check()) return;
    await sleep(1000);
  }
  throw new Error(`timed out waiting for ${what}`);
}

const ctx = {};

console.log(`Smoke test against ${BASE}\n`);

await step('gateway and user-service are reachable (waits up to 2 minutes)', async () => {
  await waitFor('login endpoint', async () => {
    try {
      const res = await call('POST', '/api/auth/login', { body: { email: 'nobody@example.com', password: 'wrong-password' } });
      return res.status === 401;
    } catch { return false; }
  }, 120000);
});

await step('protected endpoints reject anonymous callers (401)', async () => {
  expectStatus(await call('GET', '/api/books/paginated'), 401, 'books');
  expectStatus(await call('GET', '/api/clubs/grouped/paged?type=mine'), 401, 'clubs');
  expectStatus(await call('GET', '/api/notifications/me/unread-count'), 401, 'notifications');
});

await step('/internal endpoints are not exposed through the gateway', async () => {
  const res = await call('POST', '/internal/users/batch', { body: [] });
  expect(res.status === 404 || res.status === 401, `expected 404/401, got ${res.status}`);
});

await step('signup validates input and rejects duplicates', async () => {
  expectStatus(await call('POST', '/api/auth/signup', { body: { name: '', email: 'x', password: '1' } }), 400, 'bad signup');
  ctx.annEmail = `ann-${run}@example.com`;
  const ann = await call('POST', '/api/auth/signup', { body: { name: 'Ann', email: ctx.annEmail, password: 'secret1' } });
  expectStatus(ann, 200, 'signup Ann');
  ctx.ann = ann.json;
  expectStatus(await call('POST', '/api/auth/signup', { body: { name: 'Ann', email: ctx.annEmail, password: 'secret1' } }), 400, 'duplicate');
  const bob = await call('POST', '/api/auth/signup', { body: { name: 'Bob', email: `bob-${run}@example.com`, password: 'secret1' } });
  expectStatus(bob, 200, 'signup Bob');
  ctx.bob = bob.json;
});

await step('login works and a wrong password is 401', async () => {
  expectStatus(await call('POST', '/api/auth/login', { body: { email: ctx.annEmail, password: 'nope-nope' } }), 401, 'wrong password');
  const ok = await call('POST', '/api/auth/login', { body: { email: ctx.annEmail, password: 'secret1' } });
  expectStatus(ok, 200, 'login');
  expect(ok.json.token, 'no token returned');
});

await step('the seeded admin can log in', async () => {
  const res = await call('POST', '/api/auth/login', { body: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD } });
  expectStatus(res, 200, 'admin login');
  expect(res.json.role === 'ADMIN', `expected ADMIN, got ${res.json.role}`);
  ctx.admin = res.json;
});

await step('only admins can add books', async () => {
  const body = { title: `Dune ${run}`, author: 'Frank Herbert', totalChapters: 20 };
  expectStatus(await call('POST', '/api/books', { token: ctx.ann.token, body }), 403, 'member adds book');
  const res = await call('POST', '/api/books', { token: ctx.admin.token, body });
  expectStatus(res, 200, 'admin adds book');
  ctx.book = res.json;
});

await step('the catalog lists the book (Redis-backed, paginated)', async () => {
  const res = await call('GET', '/api/books/paginated?page=0&size=50', { token: ctx.ann.token });
  expectStatus(res, 200, 'catalog');
  expect(res.json.content.some((b) => b.id === ctx.book.id), 'new book missing from catalog');
  const again = await call('GET', '/api/books/paginated?page=0&size=50', { token: ctx.ann.token });
  expect(again.json.content.some((b) => b.id === ctx.book.id), 'book missing on the second (cached) read');
});

await step('Ann starts a club, Bob joins, Ann picks the book', async () => {
  const club = await call('POST', '/api/clubs', { token: ctx.ann.token, body: { name: `Sci-Fi ${run}`, description: 'smoke test club' } });
  expectStatus(club, 200, 'create club');
  ctx.club = club.json;
  expect(club.json.createdByName === 'Ann', `creator name should be Ann, got ${club.json.createdByName}`);
  expectStatus(await call('POST', `/api/clubs/${ctx.club.id}/join`, { token: ctx.bob.token }), 200, 'join');
  expectStatus(await call('PUT', `/api/clubs/${ctx.club.id}/current-book/${ctx.book.id}`, { token: ctx.bob.token }), 403, 'member picks book');
  expectStatus(await call('PUT', `/api/clubs/${ctx.club.id}/current-book/${ctx.book.id}`, { token: ctx.ann.token }), 200, 'admin picks book');
});

await step('Feign: the club page shows the creator name resolved by user-service', async () => {
  const res = await call('GET', `/api/clubs/${ctx.club.id}`, { token: ctx.bob.token });
  expectStatus(res, 200, 'get club');
  expect(res.json.createdByName === 'Ann', `expected "Ann" from user-service, got "${res.json.createdByName}" (is the Feign call or user-service failing?)`);
  expect(res.json.isCurrentUserMember === true, 'Bob should be a member');
});

await step('Kafka: Bob joining and picking the book created notifications for the right people', async () => {
  await waitFor("Ann's 'joined' notification", async () => {
    const list = await call('GET', '/api/notifications/me', { token: ctx.ann.token });
    return list.status === 200 && list.json.some((n) => n.message.includes('Bob joined'));
  }, 30000);
  await waitFor("Bob's 'now reading' notification", async () => {
    const list = await call('GET', '/api/notifications/me', { token: ctx.bob.token });
    return list.status === 200 && list.json.some((n) => n.message.includes('is now reading'));
  }, 30000);
});

await step('chapter discussion: members only, replies notify the other participants', async () => {
  const outsider = await call('POST', '/api/auth/signup', { body: { name: 'Zed', email: `zed-${run}@example.com`, password: 'secret1' } });
  expectStatus(outsider, 200, 'signup outsider');
  expectStatus(await call('POST', `/api/clubs/${ctx.club.id}/comments`, { token: outsider.json.token, body: { chapterNumber: 1, content: 'let me in' } }), 403, 'outsider comments');
  expectStatus(await call('POST', `/api/clubs/${ctx.club.id}/comments`, { token: ctx.ann.token, body: { chapterNumber: 1, content: 'What an opening' } }), 200, 'Ann comments');
  expectStatus(await call('POST', `/api/clubs/${ctx.club.id}/comments`, { token: ctx.bob.token, body: { chapterNumber: 1, content: 'Agreed!' } }), 200, 'Bob comments');
  await waitFor("Ann's reply notification", async () => {
    const list = await call('GET', '/api/notifications/me', { token: ctx.ann.token });
    return list.json.some((n) => n.message.includes('Bob commented on Chapter 1'));
  }, 30000);
  const comments = await call('GET', `/api/clubs/${ctx.club.id}/comments?chapter=1&page=0&size=10`, { token: ctx.bob.token });
  expectStatus(comments, 200, 'read comments');
  const names = comments.json.content.map((c) => c.userName);
  expect(names.includes('Ann') && names.includes('Bob'), `author names should come from user-service, got ${JSON.stringify(names)}`);
});

await step('reading progress validates the chapter range', async () => {
  expectStatus(await call('PUT', `/api/clubs/${ctx.club.id}/progress/me`, { token: ctx.bob.token, body: { currentChapter: 99 } }), 400, 'out of range');
  expectStatus(await call('PUT', `/api/clubs/${ctx.club.id}/progress/me`, { token: ctx.bob.token, body: { currentChapter: 7 } }), 200, 'update');
  const all = await call('GET', `/api/clubs/${ctx.club.id}/progress`, { token: ctx.ann.token });
  expectStatus(all, 200, 'club progress');
  expect(all.json.some((p) => p.userName === 'Bob' && p.currentChapter === 7), 'Bob chapter 7 missing');
});

await step('reviews: one per person, and the admin who added the book is notified', async () => {
  expectStatus(await call('POST', `/api/books/${ctx.book.id}/reviews`, { token: ctx.bob.token, body: { rating: 9 } }), 400, 'bad rating');
  expectStatus(await call('POST', `/api/books/${ctx.book.id}/reviews`, { token: ctx.bob.token, body: { rating: 5, reviewText: 'A masterpiece' } }), 200, 'review');
  expectStatus(await call('POST', `/api/books/${ctx.book.id}/reviews`, { token: ctx.bob.token, body: { rating: 4 } }), 400, 'second review');
  const list = await call('GET', `/api/books/${ctx.book.id}/reviews?page=0&size=5`, { token: ctx.ann.token });
  expectStatus(list, 200, 'list reviews');
  expect(list.json.averageRating === 5, `average should be 5, got ${list.json.averageRating}`);
  await waitFor("admin's review notification", async () => {
    const n = await call('GET', '/api/notifications/me', { token: ctx.admin.token });
    return n.json.some((x) => x.message.includes('Bob reviewed'));
  }, 30000);
});

await step('notifications can be marked read and dismissed', async () => {
  const before = await call('GET', '/api/notifications/me/unread-count', { token: ctx.ann.token });
  expect(before.json.count >= 1, 'Ann should have unread notifications');
  expectStatus(await call('PUT', '/api/notifications/read-all', { token: ctx.ann.token }), 200, 'read-all');
  const after = await call('GET', '/api/notifications/me/unread-count', { token: ctx.ann.token });
  expect(after.json.count === 0, `unread should be 0, got ${after.json.count}`);
  const list = await call('GET', '/api/notifications/me', { token: ctx.ann.token });
  const first = list.json[0];
  expectStatus(await call('DELETE', `/api/notifications/${first.id}`, { token: ctx.bob.token }), 404, "Bob dismisses Ann's notification");
  expectStatus(await call('DELETE', `/api/notifications/${first.id}`, { token: ctx.ann.token }), 200, 'Ann dismisses');
});

await step('the three club tabs and their pagination', async () => {
  const mine = await call('GET', '/api/clubs/grouped/paged?type=mine&page=0&size=6', { token: ctx.ann.token });
  const joined = await call('GET', '/api/clubs/grouped/paged?type=joined&page=0&size=6', { token: ctx.bob.token });
  expect(mine.json.content.some((c) => c.id === ctx.club.id), 'club missing from "mine"');
  expect(joined.json.content.some((c) => c.id === ctx.club.id), 'club missing from "joined"');
  expectStatus(await call('GET', '/api/clubs/grouped/paged?type=bogus', { token: ctx.ann.token }), 400, 'bad tab');
});

await step('cleanup: only the creator deletes the club, then the book can go', async () => {
  expectStatus(await call('DELETE', `/api/books/${ctx.book.id}`, { token: ctx.admin.token }), 400, 'delete book in use');
  expectStatus(await call('DELETE', `/api/clubs/${ctx.club.id}`, { token: ctx.bob.token }), 403, 'non-creator deletes club');
  expectStatus(await call('DELETE', `/api/clubs/${ctx.club.id}`, { token: ctx.ann.token }), 200, 'creator deletes club');
  expectStatus(await call('DELETE', `/api/books/${ctx.book.id}`, { token: ctx.admin.token }), 200, 'admin deletes book');
});

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} steps passed`);
process.exit(failed.length ? 1 : 0);
