import assert from 'node:assert/strict';
import {test} from 'node:test';
import {fileURLToPath} from 'node:url';
import {build} from 'esbuild';
import {convertV4MiniflareOptions, Miniflare} from 'miniflare';

const installationId = '11111111-1111-4111-8111-111111111111';
const sessionId = '22222222-2222-4222-8222-222222222222';
const gameSession = '33333333-3333-4333-8333-333333333333';
const secret = 'a'.repeat(64);
const name = 'arcadium-usage-test';

async function createRuntime() {
  const bundled = await build({
    stdin: {contents: `
      import worker, {ArcadiumPresence} from './worker.js';
      import {ArcadiumUsage} from './usage-counter.js';
      export default worker;
      export {ArcadiumPresence};
      export class FixtureUsage extends ArcadiumUsage {
        async fetch(request) {
          if (new URL(request.url).pathname === '/fixture/expire') {
            await this.ready;
            this.ledger.clock = () => Date.now() + 91_000;
            await this.alarm();
            return Response.json({records: (await this.ctx.storage.list({prefix: 'usage:'})).size});
          }
          if (new URL(request.url).pathname === '/fixture/storage') {
            await this.ready;
            return Response.json([...await this.ctx.storage.list({prefix: 'usage:'})]);
          }
          return super.fetch(request);
        }
      }`, sourcefile: 'usage-fixture.js', resolveDir: fileURLToPath(new URL('../src/', import.meta.url))},
    bundle: true, write: false, format: 'esm', platform: 'neutral', target: 'es2022', external: ['cloudflare:workers']
  });
  return new Miniflare(convertV4MiniflareOptions({workers: [{
    name, modules: [{type: 'ESModule', path: fileURLToPath(new URL('../src/usage-fixture.js', import.meta.url)), contents: bundled.outputFiles[0].text}],
    compatibilityDate: '2026-10-01', durableObjects: {
      PRESENCE: {className: 'ArcadiumPresence', useSQLite: true},
      USAGE: {className: 'FixtureUsage', useSQLite: true}
    }
  }]}));
}

function beat(runtime, {installation = installationId, session = sessionId, client = 'launcher', token = secret, method = 'POST', extraHeaders = {}} = {}) {
  return runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/usage/heartbeat', {
    method, headers: {'Content-Type': 'application/json', Authorization: 'Bearer ' + token, ...extraHeaders},
    body: JSON.stringify({installationId: installation, sessionId: session, client})
  });
}

test('actual worker counts launcher and game once, preserves live game after launcher closes and removes expired leases', async () => {
  const runtime = await createRuntime();
  try {
    const get = async () => {
      await runtime.purgeCache();
      return runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/usage/count');
    };
    assert.equal((await (await get()).json()).online, 0);
    const accepted = await beat(runtime);
    assert.equal(accepted.status, 200);
    assert.deepEqual(Object.keys(await accepted.json()).sort(), ['accepted', 'expiresAt', 'heartbeatSeconds']);
    assert.equal((await beat(runtime, {client: 'game', session: gameSession})).status, 200);
    const first = await (await get()).json();
    assert.deepEqual(Object.keys(first).sort(), ['online', 'updatedAt']);
    assert.equal(first.online, 1);
    assert.equal((await beat(runtime, {method: 'DELETE'})).status, 200);
    assert.equal((await (await get()).json()).online, 1);

    await runtime.unsafeEvictDurableObject(name, 'FixtureUsage', {name: 'arcadium-online-v1'});
    assert.equal((await (await get()).json()).online, 1, 'An object restart must retain a live lease');

    const namespace = await runtime.getDurableObjectNamespace('USAGE', name);
    const fixture = namespace.getByName('arcadium-online-v1');
    const stored = JSON.stringify(await (await fixture.fetch('https://fixture.test/fixture/storage')).json());
    for (const sensitive of [secret, installationId, sessionId, gameSession, 'CF-Connecting-IP']) {
      assert.ok(!stored.includes(sensitive), 'Storage retained an unhashed credential or identifier');
    }
    assert.deepEqual(await (await fixture.fetch('https://fixture.test/fixture/expire')).json(), {records: 0});
    assert.equal((await (await get()).json()).online, 0);
    assert.equal((await runtime.dispatchFetch('https://presence-test.arcadiumclient.com/fixture/storage')).status, 404);
  } finally { await runtime.dispose(); }
});

test('usage credentials and CORS cannot bypass badge authentication or revoke another session', async () => {
  const runtime = await createRuntime();
  try {
    assert.equal((await beat(runtime)).status, 200);
    assert.equal((await beat(runtime, {token: 'b'.repeat(64)})).status, 403);
    assert.equal((await beat(runtime, {token: 'b'.repeat(64), method: 'DELETE'})).status, 403);
    assert.equal((await beat(runtime, {extraHeaders: {Origin: 'https://evil.example'}})).status, 403);
    const publicCount = await runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/usage/count', {
      headers: {Origin: 'https://arcadiumclient.com'}
    });
    assert.equal(publicCount.status, 200);
    assert.equal(publicCount.headers.get('Access-Control-Allow-Origin'), 'https://arcadiumclient.com');
    assert.equal(publicCount.headers.get('Cache-Control'), 'public, max-age=15, s-maxage=15');
    assert.equal(publicCount.headers.get('Access-Control-Expose-Headers'), 'Date');
    assert.ok(Math.abs(Date.parse(publicCount.headers.get('Date')) - Date.now()) < 2000);
    assert.equal((await publicCount.json()).online, 1);
    const caches = await runtime.getCaches();
    const cached = await caches.default.match('https://presence-test.arcadiumclient.com/v1/usage/count');
    assert.equal((await cached.json()).online, 1);
    assert.equal(cached.headers.get('Access-Control-Allow-Origin'), null);
    const www = await runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/usage/count', {
      headers: {Origin: 'https://www.arcadiumclient.com'}
    });
    assert.equal(www.headers.get('Access-Control-Allow-Origin'), 'https://www.arcadiumclient.com');
    assert.equal(www.headers.get('Access-Control-Expose-Headers'), 'Date');
    assert.ok(Math.abs(Date.parse(www.headers.get('Date')) - Date.now()) < 2000);
    assert.equal((await www.json()).online, 1);
    assert.equal((await beat(runtime, {method: 'DELETE'})).status, 200);
    const retained = await runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/usage/count');
    assert.equal(retained.headers.get('Access-Control-Allow-Origin'), null);
    assert.equal(retained.headers.get('Access-Control-Expose-Headers'), 'Date');
    assert.ok(Math.abs(Date.parse(retained.headers.get('Date')) - Date.now()) < 2000);
    assert.equal((await retained.json()).online, 1, 'A public response may reuse the short edge cache');
    await runtime.purgeCache();
    assert.equal((await (await runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/usage/count')).json()).online, 0);
    const evil = await runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/usage/count', {headers: {Origin: 'https://evil.example'}});
    assert.equal(evil.status, 403);
    assert.equal(evil.headers.get('Access-Control-Allow-Origin'), null);
    const preflight = await runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/usage/count', {
      method: 'OPTIONS', headers: {Origin: 'https://www.arcadiumclient.com', 'Access-Control-Request-Method': 'GET'}
    });
    assert.equal(preflight.status, 204);
    assert.equal(preflight.headers.get('Access-Control-Allow-Origin'), 'https://www.arcadiumclient.com');
    assert.equal((await runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/usage/heartbeat', {
      method: 'OPTIONS', headers: {Origin: 'https://arcadiumclient.com', 'Access-Control-Request-Method': 'POST'}
    })).status, 403);
    assert.equal((await runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/usage/count?leak=1')).status, 400);
    assert.equal((await runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/session', {method: 'POST'})).status, 401);
    assert.equal((await beat(runtime, {token: 'minecraft.jwt.token'})).status, 401);
    assert.equal((await runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/socket', {
      headers: {Authorization: 'Bearer ' + secret, Upgrade: 'websocket'}
    })).status, 401);
  } finally { await runtime.dispose(); }
});

test('registration abuse is bounded while existing installations may renew and close', async () => {
  const runtime = await createRuntime();
  try {
    for (let index = 1; index <= 12; index++) {
      const installation = index.toString(16).padStart(8, '0') + '-1111-4111-8111-111111111111';
      assert.equal((await beat(runtime, {installation})).status, 200);
    }
    const limited = await beat(runtime);
    assert.equal(limited.status, 429);
    assert.equal(limited.headers.get('Retry-After'), '60');
    const existing = '00000001-1111-4111-8111-111111111111';
    assert.equal((await beat(runtime, {installation: existing})).status, 200);
    assert.equal((await beat(runtime, {installation: existing, method: 'DELETE'})).status, 200);
    const response = await runtime.dispatchFetch('https://presence-test.arcadiumclient.com/v1/usage/heartbeat', {
      method: 'POST', headers: {Authorization: 'Bearer ' + secret, 'Content-Type': 'application/json'}, body: 'a'.repeat(513)
    });
    assert.equal(response.status, 400);
  } finally { await runtime.dispose(); }
});
