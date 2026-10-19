import assert from 'node:assert/strict';
import {test} from 'node:test';
import {UsageLedger} from '../src/usage-ledger.js';
import {LEASE_MILLISECONDS, MAX_INSTALLATIONS, MAX_SESSIONS, usageBody, usageCredentials} from '../src/usage-protocol.js';

function fixture() {
  let time = 100_000;
  const saved = new Map();
  const storage = {
    writes: 0,
    async put(key, value) {
      this.writes++;
      if (typeof key === 'object') for (const [id, record] of Object.entries(key)) saved.set(id, structuredClone(record));
      else saved.set(key, structuredClone(value));
    },
    async delete(keys) { for (const key of Array.isArray(keys) ? keys : [keys]) saved.delete(key); },
    async list({prefix, limit, startAfter}) {
      return new Map([...saved].sort(([a], [b]) => a.localeCompare(b))
        .filter(([key]) => key.startsWith(prefix) && (!startAfter || key > startAfter)).slice(0, limit)
        .map(([key, value]) => [key, structuredClone(value)]));
    }
  };
  const clock = () => time;
  return {ledger: new UsageLedger(storage, clock), storage, saved, clock, advance: amount => { time += amount; }};
}

test('launcher and game share one count, and closing either preserves the other', async () => {
  const {ledger} = fixture();
  for (const [session, client] of [['launcher', 'launcher'], ['game', 'game']]) {
    const result = await ledger.heartbeat('installation', 'secret', session, client);
    assert.equal(result.status, 200);
    assert.equal(result.body.heartbeatSeconds, 30);
  }
  assert.equal(ledger.snapshot().online, 1);
  assert.equal((await ledger.close('installation', 'secret', 'launcher', 'launcher')).status, 200);
  assert.equal(ledger.snapshot().online, 1);
  assert.equal((await ledger.close('installation', 'secret', 'game', 'game')).status, 200);
  assert.equal(ledger.snapshot().online, 0);
  assert.equal((await ledger.close('installation', 'secret', 'game', 'game')).status, 200);
});

test('repeated heartbeats do not inflate counts or generate excessive storage writes', async () => {
  const {ledger, storage, advance} = fixture();
  await ledger.heartbeat('one', 'secret', 'a', 'game');
  for (let index = 0; index < 30; index++) await ledger.heartbeat('one', 'secret', 'a', 'game');
  assert.equal(storage.writes, 1);
  assert.equal(ledger.snapshot().online, 1);
  advance(30_000);
  await ledger.heartbeat('one', 'secret', 'a', 'game');
  assert.equal(storage.writes, 2);
  await ledger.heartbeat('two', 'different', 'b', 'launcher');
  assert.equal(ledger.snapshot().online, 2);
});

test('crashed sessions expire exactly after their lease even before collection runs', async () => {
  const {ledger, saved, advance} = fixture();
  await ledger.heartbeat('one', 'secret', 'launcher', 'launcher');
  advance(30_000);
  await ledger.heartbeat('one', 'secret', 'game', 'game');
  advance(LEASE_MILLISECONDS - 30_000);
  assert.equal(ledger.snapshot().online, 1);
  await ledger.collect();
  assert.deepEqual(Object.keys(saved.get('usage:one').sessions), ['game']);
  advance(30_000);
  assert.equal(ledger.snapshot().online, 0);
  await ledger.collect();
  assert.equal(saved.size, 0);
});

test('another credential cannot extend or revoke a live installation', async () => {
  const {ledger, advance} = fixture();
  await ledger.heartbeat('one', 'secret', 'a', 'launcher');
  assert.equal((await ledger.heartbeat('one', 'wrong', 'b', 'game')).status, 403);
  assert.equal((await ledger.close('one', 'wrong', 'a', 'launcher')).status, 403);
  assert.equal(ledger.snapshot().online, 1);
  advance(LEASE_MILLISECONDS);
  assert.equal((await ledger.heartbeat('one', 'new-secret', 'b', 'game')).status, 200);
});

test('sessions cannot change type and installation session counts are bounded', async () => {
  const {ledger} = fixture();
  for (let index = 0; index < MAX_SESSIONS; index++) {
    assert.equal((await ledger.heartbeat('one', 'secret', String(index), 'game')).status, 200);
  }
  assert.equal((await ledger.heartbeat('one', 'secret', 'extra', 'game')).status, 429);
  assert.equal((await ledger.heartbeat('one', 'secret', '0', 'launcher')).status, 409);
  assert.equal((await ledger.close('one', 'secret', '0', 'launcher')).status, 409);
  assert.equal(ledger.snapshot().online, 1);
});

test('service capacity recovers after expired installations are collected', async () => {
  const {ledger, advance} = fixture();
  for (let index = 0; index < MAX_INSTALLATIONS; index++) {
    ledger.installations.set(String(index), {secretHash: 'secret', sessions: {a: {client: 'game', expiresAt: 190_000}}});
  }
  assert.equal((await ledger.heartbeat('extra', 'secret', 'b', 'game')).status, 503);
  advance(LEASE_MILLISECONDS);
  assert.equal((await ledger.heartbeat('extra', 'secret', 'b', 'game')).status, 200);
  assert.equal(ledger.snapshot().online, 1);
});

test('registration limits do not interrupt an existing installation', async () => {
  const {ledger} = fixture();
  await ledger.heartbeat('one', 'secret', 'a', 'launcher');
  assert.equal((await ledger.heartbeat('new', 'secret', 'b', 'launcher', false)).status, 429);
  assert.equal((await ledger.heartbeat('one', 'secret', 'b', 'game', false)).status, 200);
});

test('restore retains active sessions and removes expired data after a process restart', async () => {
  const {ledger, storage, clock, advance, saved} = fixture();
  await ledger.heartbeat('one', 'secret', 'a', 'game');
  await ledger.heartbeat('two', 'secret-2', 'b', 'launcher');
  advance(30_000);
  const restarted = new UsageLedger(storage, clock);
  await restarted.restore();
  assert.equal(restarted.snapshot().online, 2);
  await restarted.close('one', 'secret', 'a', 'game');
  assert.equal(restarted.snapshot().online, 1);
  advance(LEASE_MILLISECONDS);
  const expired = new UsageLedger(storage, clock);
  await expired.restore();
  assert.equal(expired.snapshot().online, 0);
  assert.equal(saved.size, 0);
});

test('mutations serialize so concurrent launchers do not drop one another', async () => {
  const {ledger} = fixture();
  await Promise.all(Array.from({length: 8}, (_, index) => ledger.serialize(() =>
    ledger.heartbeat('one', 'secret', String(index), 'game'))));
  assert.equal(Object.keys(ledger.installations.get('one').sessions).length, 8);
});

test('restore follows storage pages instead of dropping installations after the first thousand', async () => {
  const {ledger, storage, clock, saved} = fixture();
  for (let index = 0; index < 1001; index++) {
    saved.set('usage:' + index.toString().padStart(5, '0'), {
      secretHash: 'secret', sessions: {game: {client: 'game', lastBeat: clock(), expiresAt: clock() + LEASE_MILLISECONDS}}
    });
  }
  await ledger.restore();
  assert.equal(ledger.snapshot().online, 1001);
  const restarted = new UsageLedger(storage, clock);
  await restarted.restore();
  assert.equal(restarted.snapshot().online, 1001);
});

test('failed storage writes never create an in-memory phantom installation', async () => {
  const {ledger, storage} = fixture();
  storage.put = async () => { throw new Error('Unavailable'); };
  await assert.rejects(ledger.serialize(() => ledger.heartbeat('one', 'secret', 'a', 'game')), /Unavailable/);
  assert.equal(ledger.snapshot().online, 0);
});

test('heartbeat body validation accepts exact native requests and bounds untrusted data', async () => {
  const body = {
    installationId: '11111111-1111-4111-8111-111111111111',
    sessionId: '22222222-2222-4222-8222-222222222222', client: 'launcher'
  };
  const request = value => new Request('https://example.test/v1/usage/heartbeat', {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(value)
  });
  assert.deepEqual(await usageBody(request(body)), body);
  for (const value of [null, [], 'text', {...body, token: 'not allowed'}, {...body, installationId: 'invalid'}, {...body, client: 'browser'}]) {
    await assert.rejects(usageBody(request(value)));
  }
  await assert.rejects(usageBody(request({...body, extra: 'a'.repeat(513)})));
  await assert.rejects(usageBody(new Request('https://example.test', {method: 'POST', body: JSON.stringify(body)})));
  await assert.rejects(usageBody(new Request('https://example.test', {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: new ReadableStream({start(controller) { controller.enqueue(new Uint8Array(513)); controller.close(); }}), duplex: 'half'
  })));
  assert.equal(usageCredentials(new Request('https://example.test', {headers: {Authorization: 'Bearer ' + 'a'.repeat(64)}})), 'a'.repeat(64));
  for (const token of ['A'.repeat(64), 'x', 'a'.repeat(65), 'a b', 'ey.minecraft.access.token']) {
    assert.equal(usageCredentials(new Request('https://example.test', {headers: {Authorization: 'Bearer ' + token}})), null);
  }
});
