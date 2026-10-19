import assert from 'node:assert/strict';
import {before, test} from 'node:test';
import {importAuthenticationKeys, verifyMinecraftToken} from '../src/minecraft-token.js';
import {segment, signedSegments, signedToken, signingKey} from './fixtures/minecraft-token-fixture.js';

const uuid = '11111111111141118111111111111111';
const now = 1_800_000_000_000;
const seconds = now / 1000;
let signer, otherSigner, keys, otherKeys;
const claims = overrides => ({iss: 'authentication', profiles: {mc: uuid}, iat: seconds - 60,
  exp: seconds + 3600, ...overrides});

before(async () => {
  [signer, otherSigner] = await Promise.all([signingKey(), signingKey()]);
  [keys, otherKeys] = await Promise.all([importAuthenticationKeys([signer]), importAuthenticationKeys([otherSigner])]);
});

test('RS256 verifies exported RSA2048 SPKI keys and returns only the signed Minecraft identity and expiry', async () => {
  assert.equal(keys[0].algorithm.modulusLength, 2048);
  assert.equal(keys[0].extractable, false);
  assert.deepEqual(keys[0].usages, ['verify']);
  const token = await signedToken(signer, claims({unrelated: 'must not escape verification'}));
  assert.deepEqual(await verifyMinecraftToken(token, now, keys), {uuid, expiresAt: (seconds + 3600) * 1000});
  assert.deepEqual(await verifyMinecraftToken(token, now, [...otherKeys, ...keys]), {uuid, expiresAt: (seconds + 3600) * 1000});
  assert.equal(await verifyMinecraftToken(token, now, otherKeys), null);
  assert.equal(await verifyMinecraftToken(token, now, []), null);
  assert.equal(await verifyMinecraftToken(await signedToken(otherSigner, claims()), now, keys), null);
});

test('payload, header and signature tampering cannot change the authenticated identity', async () => {
  const token = await signedToken(signer, claims());
  const [header, payload, signature] = token.split('.');
  const changedSignature = Buffer.from(signature, 'base64url');
  changedSignature[128] ^= 1;
  for (const tampered of [
    [header, segment(claims({profiles: {mc: '22222222222242228222222222222222'}})), signature].join('.'),
    [segment({alg: 'RS256', typ: 'JWT', kid: 'changed'}), payload, signature].join('.'),
    [header, payload, changedSignature.toString('base64url')].join('.'),
    [header, payload, Buffer.from(signature, 'base64url').subarray(1).toString('base64url')].join('.')
  ]) assert.equal(await verifyMinecraftToken(tampered, now, keys), null);
});

test('only RS256 without unsupported critical headers is accepted even with a valid RSA signature', async () => {
  for (const alg of [undefined, 'none', 'HS256', 'RS512', 'PS256', 'rs256']) {
    assert.equal(await verifyMinecraftToken(await signedToken(signer, claims(), {alg}), now, keys), null);
  }
  for (const crit of [['b64'], 'b64', true, {}]) {
    assert.equal(await verifyMinecraftToken(await signedToken(signer, claims(), {alg: 'RS256', crit}), now, keys), null);
  }
  assert.deepEqual(await verifyMinecraftToken(await signedToken(signer, claims(), {alg: 'RS256', crit: []}), now, keys),
    {uuid, expiresAt: (seconds + 3600) * 1000});
});

test('profiles.mc accepts compact and canonical hyphenated Minecraft UUIDs and rejects malformed identifiers', async () => {
  for (const profiles of [undefined, null, [], {}, {mc: null}, {mc: 111}, {mc: {}},
    {mc: '1111111-11111-4111-8111-111111111111'}, {mc: '11111111--1111-4111-8111-111111111111'},
    {mc: '111111111111-4111-8111-111111111111'}, {mc: '11111111-1111-4111-8111-11111111111z'},
    {mc: 'ABCDEF0123454ABC8DEF0123456789AB'}, {mc: 'ABCDEF01-2345-4ABC-8DEF-0123456789AB'},
    {mc: uuid + '0'}, {mc: uuid.slice(1)}]) {
    assert.equal(await verifyMinecraftToken(await signedToken(signer, claims({profiles})), now, keys), null);
  }
  const alternate = 'abcdef0123454abc8def0123456789ab';
  assert.deepEqual(await verifyMinecraftToken(await signedToken(signer, claims({profiles: {mc: alternate}})), now, keys),
    {uuid: alternate, expiresAt: (seconds + 3600) * 1000});
  assert.deepEqual(await verifyMinecraftToken(await signedToken(signer, claims({profiles: {mc: '11111111-1111-4111-8111-111111111111'}})), now, keys),
    {uuid, expiresAt: (seconds + 3600) * 1000});
  for (const profile of ['11111111111111118111111111111111', '11111111111141117111111111111111',
    '11111111-1111-1111-7111-111111111111']) {
    assert.deepEqual(await verifyMinecraftToken(await signedToken(signer, claims({profiles: {mc: profile}})), now, keys),
      {uuid: profile.replaceAll('-', ''), expiresAt: (seconds + 3600) * 1000});
  }
});

test('a valid signature cannot authorize a token from a different issuer', async () => {
  for (const iss of ['minecraftservices', 'profile', 'Authentication', 'AUTHENTICATION', 'authentication ',
    'https://api.minecraftservices.com', 'untrusted']) {
    assert.equal(await verifyMinecraftToken(await signedToken(signer, claims({iss})), now, keys), null);
  }
});

test('expiry is required, integral and strictly later than the current clock', async () => {
  for (const exp of [undefined, null, seconds - 1, seconds, seconds + 0.5, String(seconds + 3600), true,
    Number.MAX_SAFE_INTEGER + 1, {}, []]) {
    assert.equal(await verifyMinecraftToken(await signedToken(signer, claims({exp})), now, keys), null);
  }
  const token = await signedToken(signer, claims({exp: seconds + 1}));
  assert.deepEqual(await verifyMinecraftToken(token, now + 999, keys), {uuid, expiresAt: now + 1000});
  assert.equal(await verifyMinecraftToken(token, now + 1000, keys), null);
});

test('not-before and issued-at accept the defined clock tolerance and reject future or malformed claims', async () => {
  for (const field of ['nbf', 'iat']) {
    for (const value of [undefined, seconds - 3600, seconds, seconds + 30]) {
      assert.deepEqual(await verifyMinecraftToken(await signedToken(signer, claims({[field]: value})), now, keys),
        {uuid, expiresAt: (seconds + 3600) * 1000});
    }
    for (const value of [seconds + 31, seconds + 0.5, String(seconds), false, {}, [], Number.MAX_SAFE_INTEGER + 1]) {
      assert.equal(await verifyMinecraftToken(await signedToken(signer, claims({[field]: value})), now, keys), null);
    }
  }
});

test('issuer and payload shapes must be well formed after signature verification', async () => {
  for (const iss of [undefined, null, '', 123, false, {}, [], 'x'.repeat(257)]) {
    assert.equal(await verifyMinecraftToken(await signedToken(signer, claims({iss})), now, keys), null);
  }
  for (const value of [null, [], 'claims', 42]) {
    assert.equal(await verifyMinecraftToken(await signedToken(signer, value), now, keys), null);
  }
});

test('malformed compact serialization, base64url, UTF8 and JSON return rejection without throwing', async () => {
  const token = await signedToken(signer, claims());
  const [header, payload, signature] = token.split('.');
  for (const malformed of [null, undefined, 1, {}, [], '', 'a.b', token + '.extra', '.' + payload + '.' + signature,
    header + '..' + signature, header + '.' + payload + '.', header + '=' + '.' + payload + '.' + signature,
    header + '.' + payload + '.' + '*', header + '.' + payload + '.' + 'A', 'x'.repeat(8193),
    'x'.repeat(2049) + '.' + payload + '.' + signature, header + '.' + payload + '.' + Buffer.alloc(1025).toString('base64url')]) {
    assert.equal(await verifyMinecraftToken(malformed, now, keys), null);
  }
  for (const [badHeader, badPayload] of [
    [segment(new Uint8Array([0xc3, 0x28])), payload],
    [header, segment(new Uint8Array([0xc3, 0x28]))],
    [segment(Buffer.from('{')), payload],
    [header, segment(Buffer.from('{'))],
    [segment(null), payload]
  ]) assert.equal(await verifyMinecraftToken(await signedSegments(signer, badHeader, badPayload), now, keys), null);
});

test('malformed SPKI material cannot become a trusted verification key', async () => {
  await assert.rejects(importAuthenticationKeys([{publicKey: Buffer.from('not an SPKI key').toString('base64')}]));
  await assert.rejects(importAuthenticationKeys([{publicKey: '*'}]));
});
