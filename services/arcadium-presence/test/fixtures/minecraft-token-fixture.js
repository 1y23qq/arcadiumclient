import {webcrypto} from 'node:crypto';

export async function signingKey() {
  const pair = await webcrypto.subtle.generateKey({name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256'}, true, ['sign', 'verify']);
  const spki = await webcrypto.subtle.exportKey('spki', pair.publicKey);
  return {privateKey: pair.privateKey, publicKey: Buffer.from(spki).toString('base64')};
}

export function segment(value) {
  const bytes = value instanceof Uint8Array ? value : Buffer.from(JSON.stringify(value));
  return Buffer.from(bytes).toString('base64url');
}

export async function signedToken(key, claims, header = {alg: 'RS256', typ: 'JWT'}) {
  return signedSegments(key, segment(header), segment(claims));
}

export async function signedSegments(key, header, payload) {
  const input = header + '.' + payload;
  const signature = await webcrypto.subtle.sign('RSASSA-PKCS1-v1_5', key.privateKey, Buffer.from(input));
  return input + '.' + Buffer.from(signature).toString('base64url');
}
