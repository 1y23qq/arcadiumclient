import assert from 'node:assert/strict';
import {test} from 'node:test';
import {fileURLToPath} from 'node:url';
import {build} from 'esbuild';
import {convertV4MiniflareOptions, Miniflare, Response as MiniflareResponse} from 'miniflare';
import {bearer, playerId, watchedPlayers} from '../src/protocol.js';
import {signedToken, signingKey} from './fixtures/minecraft-token-fixture.js';

const alice = '11111111111141118111111111111111';
const bob = '22222222222242228222222222222222';
const absent = '33333333333343338333333333333333';

test('untrusted identifiers and bearer inputs stay bounded', () => {
  assert.equal(playerId('11111111-1111-4111-8111-111111111111'), alice);
  assert.equal(playerId('not a player'), null);
  assert.deepEqual(watchedPlayers([alice, alice, bob]), [alice, bob]);
  assert.throws(() => watchedPlayers(Array(257).fill(alice)));
  assert.throws(() => watchedPlayers([alice, 'invalid']));
  assert.equal(bearer(new Request('https://example.com', {headers: {Authorization: 'Bearer a b'}})), null);
});

const inboxes = new WeakMap();

function observe(socket, allowed) {
  const queued = [];
  const waiting = new Set();
  let failure;
  const fail = error => {
    failure = error;
    for (const waiter of waiting) {
      clearTimeout(waiter.timer);
      waiter.reject(error);
    }
    waiting.clear();
  };
  const drain = () => {
    for (const waiter of waiting) {
      const index = queued.findIndex(waiter.matches);
      if (index < 0) continue;
      const packet = queued.splice(0, index + 1).at(-1);
      waiting.delete(waiter);
      clearTimeout(waiter.timer);
      waiter.resolve(packet);
    }
  };
  socket.addEventListener('message', event => {
    try {
      const packet = JSON.parse(event.data);
      assert.deepEqual(Object.keys(packet).sort(), ['players', 'type']);
      assert.equal(packet.type, 'players');
      assert.ok(Array.isArray(packet.players));
      assert.ok(packet.players.every(id => allowed.includes(id)), 'A snapshot exposed an unrelated player');
      assert.equal(new Set(packet.players).size, packet.players.length);
      queued.push(packet);
      drain();
    } catch (error) { fail(error); }
  });
  socket.addEventListener('close', () => fail(new Error('Presence socket closed')));
  inboxes.set(socket, (players, send) => {
    if (failure) return Promise.reject(failure);
    if (send) queued.length = 0;
    const result = new Promise((resolve, reject) => {
      const waiter = {resolve, reject, matches: packet =>
        packet.players.length === players.length && packet.players.every((id, index) => id === players[index])};
      waiter.timer = setTimeout(() => {
        waiting.delete(waiter);
        reject(new Error('Presence message timed out'));
      }, 5000);
      waiting.add(waiter);
      drain();
    });
    send?.();
    return result;
  });
}

function message(socket, players, send) { return inboxes.get(socket)(players, send); }

function closed(socket, send) {
  return new Promise((resolve, reject) => {
    const listener = event => {
      clearTimeout(timer);
      resolve(event);
    };
    const timer = setTimeout(() => {
      socket.removeEventListener('close', listener);
      reject(new Error('Presence close timed out'));
    }, 5000);
    socket.addEventListener('close', listener, {once: true});
    send?.();
  });
}

const fixtureModule = `
import worker, {ArcadiumPresence} from './worker.js';
import {tokenHash} from './protocol.js';
export default worker;
export class FixturePresence extends ArcadiumPresence {
  async fetch(request) {
    const path = new URL(request.url).pathname;
    if (path === '/collect-expired-fixture-sessions') {
      await this.alarm();
      return Response.json({sessions: (await this.ctx.storage.list({prefix: 'session:'})).size});
    }
    if (path === '/seed-fixture-leases') {
      const {count, expired} = await request.json();
      await this.ctx.storage.deleteAll();
      for (let offset = 0; offset < count; offset += 128) {
        const entries = {};
        for (let index = offset; index < Math.min(count, offset + 128); index++) {
          entries['session:' + (index + 1).toString(16).padStart(64, '0')] = {
            uuid: index.toString(16).padStart(8, '0') + '111141118111111111111111',
            expiresAt: expired ? 0 : Date.now() + 6 * 60 * 60 * 1000
          };
        }
        await this.ctx.storage.put(entries);
      }
      return Response.json({sessions: (await this.ctx.storage.list({prefix: 'session:'})).size});
    }
    if (path === '/has-fixture-lease') {
      const hash = await tokenHash(request.headers.get('Authorization').slice(7));
      return Response.json({exists: !!(await this.ctx.storage.get('session:' + hash))});
    }
    if (path === '/reset-fixture-auth-attempts') {
      this.authAttempts.clear();
      return Response.json({reset: true});
    }
    if (path !== '/expire-fixture-session') return super.fetch(request);
    const hash = await tokenHash(request.headers.get('Authorization').slice(7));
    const key = 'session:' + hash;
    const session = await this.ctx.storage.get(key);
    if (!session) return new Response(null, {status: 404});
    await this.ctx.storage.put(key, {...session, expiresAt: 0});
    for (const socket of this.ctx.getWebSockets()) {
      const attachment = socket.deserializeAttachment();
      if (attachment?.hash === hash) socket.serializeAttachment({...attachment, expiresAt: 0});
    }
    return Response.json({expired: true});
  }
}`;

async function createRuntime(name, outboundService, authenticationKeys) {
  const bundled = await build({
    stdin: {contents: fixtureModule, sourcefile: 'fixture.js', resolveDir: fileURLToPath(new URL('../src/', import.meta.url))},
    bundle: true, write: false, format: 'esm', platform: 'neutral', target: 'es2022', external: ['cloudflare:workers'],
    plugins: authenticationKeys ? [{name: 'fixture-authentication-keys', setup(builder) {
      builder.onLoad({filter: /mojang-authentication-keys\.json$/}, () => ({
        contents: JSON.stringify({keys: authenticationKeys.map(({publicKey}) => ({publicKey}))}), loader: 'json'
      }));
    }}] : []
  });
  return new Miniflare(convertV4MiniflareOptions({workers: [{
    name,
    modules: [
      {type: 'ESModule', path: fileURLToPath(new URL('../src/fixture.js', import.meta.url)), contents: bundled.outputFiles[0].text}
    ],
    compatibilityDate: '2026-10-01',
    durableObjects: {PRESENCE: {className: 'FixturePresence', useSQLite: true}},
    outboundService
  }]}));
}

test('workerd verifies signed identities without a profile request and caps leases at JWT expiry', async () => {
  const key = await signingKey();
  let upstreamRequests = 0;
  const runtime = await createRuntime('arcadium-presence-signed-test', () => {
    upstreamRequests++;
    return MiniflareResponse.json({error: 'Unauthorized'}, {status: 401});
  }, [key]);
  const socketResponses = [];
  try {
    const exp = Math.floor(Date.now() / 1000) + 300;
    const profile = {mc: '11111111-1111-4111-8111-111111111111'};
    const token = await signedToken(key, {iss: 'authentication', profiles: profile, exp});
    const call = (path, init) => runtime.dispatchFetch('https://presence.test' + path, init);
    const response = await call('/v1/session', {method: 'POST', headers: {Authorization: 'Bearer ' + token}});
    assert.equal(response.status, 200);
    const session = await response.json();
    assert.equal(session.uuid, alice);
    assert.equal(session.expiresAt, exp * 1000);
    assert.match(session.token, /^[a-f0-9]{64}$/);
    assert.notEqual(session.token, token);
    assert.equal(upstreamRequests, 0, 'A valid local signature must not disclose its token to a profile endpoint');
    const connected = await call('/v1/socket', {headers: {Upgrade: 'websocket', Authorization: 'Bearer ' + session.token}});
    assert.equal(connected.status, 101);
    connected.webSocket.accept();
    socketResponses.push(connected.webSocket);
    const wrongIssuer = await signedToken(key, {iss: 'profile', profiles: profile, exp});
    assert.equal((await call('/v1/session', {method: 'POST', headers: {Authorization: 'Bearer ' + wrongIssuer}})).status, 401);
    assert.equal(upstreamRequests, 1, 'A signed token from another issuer must not create a local session');
    const parts = token.split('.');
    const badSignature = Buffer.from(parts[2], 'base64url');
    badSignature[128] ^= 1;
    parts[2] = badSignature.toString('base64url');
    assert.equal((await call('/v1/session', {method: 'POST', headers: {Authorization: 'Bearer ' + parts.join('.')}})).status, 401);
    assert.equal(upstreamRequests, 2, 'An invalid signature must use the authenticated profile fallback');
    assert.equal((await call('/v1/session', {method: 'DELETE', headers: {Authorization: 'Bearer ' + session.token}})).status, 200);
  } finally {
    for (const socket of socketResponses) if (socket.readyState === 1) socket.close();
    await runtime.dispose();
  }
});

test('verified accounts discover watched peers and remove disconnected, revoked and expired sessions', async () => {
  const upstreamRequests = [];
  const runtime = await createRuntime('arcadium-presence-test', request => {
      const url = new URL(request.url);
      upstreamRequests.push({origin: url.origin, path: url.pathname, method: request.method});
      assert.equal(url.origin + url.pathname, 'https://api.minecraftservices.com/minecraft/profile');
      assert.ok(!url.search, 'Profile verification uses no query string');
      const token = request.headers.get('Authorization');
      if (token === 'Bearer fixture-redirect') return new MiniflareResponse(null, {
        status: 302, headers: {Location: 'https://untrusted.example/minecraft/profile'}
      });
      if (token === 'Bearer fixture-alice') return MiniflareResponse.json({id: alice, name: 'ArcadiumFixtureA'});
      if (token === 'Bearer fixture-bob') return MiniflareResponse.json({id: bob, name: 'ArcadiumFixtureB'});
      return MiniflareResponse.json({error: 'Unauthorized'}, {status: 401});
  });
  const sockets = [];
  try {
    const call = (path, init) => runtime.dispatchFetch('https://presence.test' + path, init);
    assert.equal((await call('/v1/session', {method: 'POST'})).status, 401);
    const unauthorized = await call('/v1/session', {method: 'POST', headers: {Authorization: 'Bearer invalid'}});
    assert.equal(unauthorized.status, 401, JSON.stringify({body: await unauthorized.json(), upstreamRequests}));
    const requestsBeforeRedirect = upstreamRequests.length;
    assert.equal((await call('/v1/session', {method: 'POST', headers: {Authorization: 'Bearer fixture-redirect'}})).status, 503);
    assert.equal(upstreamRequests.length, requestsBeforeRedirect + 1, 'Account tokens must not follow redirects');
    assert.equal((await call('/v1/socket?token=secret', {headers: {Authorization: 'Bearer fake'}})).status, 400);
    const login = async name => {
      const response = await call('/v1/session', {method: 'POST', headers: {Authorization: 'Bearer fixture-' + name}});
      assert.equal(response.status, 200);
      const session = await response.json();
      assert.ok(typeof session.token === 'string' && /^[a-f0-9]{64}$/.test(session.token), 'Session token format');
      return session;
    };
    const one = await login('alice');
    const two = await login('bob');
    assert.equal(one.uuid, alice);
    assert.equal(two.uuid, bob);
    const connect = async (token, uuid) => {
      const response = await call('/v1/socket', {headers: {Upgrade: 'websocket', Authorization: 'Bearer ' + token}});
      assert.equal(response.status, 101);
      observe(response.webSocket, uuid === alice ? [bob, absent] : [alice]);
      response.webSocket.accept();
      sockets.push(response.webSocket);
      return response.webSocket;
    };
    assert.equal((await call('/v1/socket', {headers: {Upgrade: 'websocket', Authorization: 'Bearer ' + 'a'.repeat(64)}})).status, 401);
    const watch = (socket, players, expected) => message(socket, expected, () => socket.send(JSON.stringify({type: 'watch', players})));
    const first = await connect(one.token, alice);
    assert.deepEqual(await watch(first, [bob, absent], []), {type: 'players', players: []});
    const joined = message(first, [bob]);
    const second = await connect(two.token, bob);
    assert.deepEqual(await watch(second, [alice], [alice]), {type: 'players', players: [alice]});
    assert.deepEqual(await joined, {type: 'players', players: [bob]});
    assert.deepEqual(await watch(first, [absent], []), {type: 'players', players: []});
    assert.deepEqual(await watch(first, [bob, absent], [bob]), {type: 'players', players: [bob]});

    const disconnected = message(first, []);
    second.close(1000, 'Fixture disconnected');
    assert.deepEqual(await disconnected, {type: 'players', players: []});
    const rejoined = message(first, [bob]);
    const third = await connect(two.token, bob);
    assert.deepEqual(await watch(third, [alice], [alice]), {type: 'players', players: [alice]});
    assert.deepEqual(await rejoined, {type: 'players', players: [bob]});

    const left = message(first, []);
    const revoked = await call('/v1/session', {method: 'DELETE', headers: {Authorization: 'Bearer ' + two.token}});
    assert.equal(revoked.status, 200);
    assert.deepEqual(await left, {type: 'players', players: []});
    assert.equal((await call('/v1/socket', {headers: {Upgrade: 'websocket', Authorization: 'Bearer ' + two.token}})).status, 401);

    const namespace = await runtime.getDurableObjectNamespace('PRESENCE', 'arcadium-presence-test');
    const fixture = namespace.getByName('arcadium-presence-test-v1');
    assert.equal((await call('/expire-fixture-session')).status, 404);
    assert.equal((await fixture.fetch('https://fixture.test/expire-fixture-session', {
      headers: {Authorization: 'Bearer ' + one.token}
    })).status, 200);
    assert.equal((await call('/v1/socket', {headers: {Upgrade: 'websocket', Authorization: 'Bearer ' + one.token}})).status, 401);
    assert.equal((await call('/v1/session', {method: 'DELETE', headers: {Authorization: 'Bearer ' + one.token}})).status, 401);
    const expiredClose = closed(first);
    const renewed = await login('bob');
    await connect(renewed.token, bob);
    assert.equal((await expiredClose).code, 1008);
    assert.equal((await call('/v1/session', {method: 'DELETE', headers: {Authorization: 'Bearer ' + renewed.token}})).status, 200);
    assert.deepEqual(await (await fixture.fetch('https://fixture.test/collect-expired-fixture-sessions')).json(), {sessions: 0});

    const pacedSession = await login('alice');
    const paced = await connect(pacedSession.token, alice);
    let pacedClose = null;
    paced.addEventListener('close', event => { pacedClose = event.code; });
    for (let index = 0; index < 15; index++) {
      if (index) await new Promise(resolve => setTimeout(resolve, 1000));
      assert.equal(paced.readyState, 1, 'A valid paced subscriber must remain connected');
      assert.deepEqual(await watch(paced, index % 2 ? [bob, absent] : [bob], []), {type: 'players', players: []});
    }
    assert.equal(paced.readyState, 1);
    assert.equal(pacedClose, null);
    assert.equal((await call('/v1/session', {method: 'DELETE', headers: {Authorization: 'Bearer ' + pacedSession.token}})).status, 200);
  } finally {
    try {
      for (const socket of sockets) if (socket.readyState === 1) socket.close();
    } finally { await runtime.dispose(); }
  }
});

test('session admission evicts only inactive leases and bounds retained storage', async () => {
  const name = 'arcadium-presence-lease-test';
  const runtime = await createRuntime(name, request => {
    const url = new URL(request.url);
    assert.equal(url.origin + url.pathname, 'https://api.minecraftservices.com/minecraft/profile');
    if (request.headers.get('Authorization') === 'Bearer fixture-alice') {
      return MiniflareResponse.json({id: alice, name: 'ArcadiumFixtureA'});
    }
    return MiniflareResponse.json({error: 'Unauthorized'}, {status: 401});
  });
  const sockets = [];
  try {
    const call = (path, init) => runtime.dispatchFetch('https://presence.test' + path, init);
    const mint = () => call('/v1/session', {method: 'POST', headers: {Authorization: 'Bearer fixture-alice'}});
    const login = async () => {
      const response = await mint();
      assert.equal(response.status, 200);
      const session = await response.json();
      assert.equal(session.uuid, alice);
      assert.ok(typeof session.token === 'string' && /^[a-f0-9]{64}$/.test(session.token), 'Session token format');
      return session;
    };
    const socketRequest = token => call('/v1/socket', {headers: {Upgrade: 'websocket', Authorization: 'Bearer ' + token}});
    const connect = async token => {
      const response = await socketRequest(token);
      assert.equal(response.status, 101);
      response.webSocket.accept();
      sockets.push(response.webSocket);
      return response.webSocket;
    };
    const namespace = await runtime.getDurableObjectNamespace('PRESENCE', name);
    const fixture = namespace.getByName('arcadium-presence-test-v1');
    const exists = async token => (await fixture.fetch('https://fixture.test/has-fixture-lease', {
      headers: {Authorization: 'Bearer ' + token}
    })).json();
    const seed = async expired => {
      const response = await fixture.fetch('https://fixture.test/seed-fixture-leases', {
        method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({count: 512, expired})
      });
      assert.equal(response.status, 200);
      assert.deepEqual(await response.json(), {sessions: 512});
    };

    const idle = await login();
    const second = await login();
    const third = await login();
    const secondSocket = await connect(second.token);
    const thirdSocket = await connect(third.token);
    const fourth = await login();
    assert.deepEqual(await exists(idle.token), {exists: false});
    assert.equal((await socketRequest(idle.token)).status, 401);
    for (const session of [second, third, fourth]) assert.deepEqual(await exists(session.token), {exists: true});
    assert.equal(secondSocket.readyState, 1);
    assert.equal(thirdSocket.readyState, 1);
    const fourthSocket = await connect(fourth.token);
    assert.equal((await mint()).status, 429);
    for (const socket of [secondSocket, thirdSocket, fourthSocket]) assert.equal(socket.readyState, 1);
    for (const session of [second, third, fourth]) {
      assert.equal((await call('/v1/session', {method: 'DELETE', headers: {Authorization: 'Bearer ' + session.token}})).status, 200);
    }

    assert.equal((await call('/seed-fixture-leases')).status, 404);
    await seed(false);
    assert.equal((await mint()).status, 503);
    await seed(true);
    assert.deepEqual(await (await fixture.fetch('https://fixture.test/collect-expired-fixture-sessions')).json(), {sessions: 0});
    const recovered = await login();
    assert.equal((await call('/v1/session', {method: 'DELETE', headers: {Authorization: 'Bearer ' + recovered.token}})).status, 200);
  } finally {
    try {
      for (const socket of sockets) if (socket.readyState === 1) socket.close();
    } finally { await runtime.dispose(); }
  }
});
