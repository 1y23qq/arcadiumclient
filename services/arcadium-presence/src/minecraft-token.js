import published from './mojang-authentication-keys.json' with {type: 'json'};

const decoder = new TextDecoder('utf-8', {fatal: true});

function decode(value) {
  if (!value || !/^[A-Za-z0-9_-]+$/.test(value)) throw new Error();
  const bytes = atob(value.replaceAll('-', '+').replaceAll('_', '/') + '='.repeat((4 - value.length % 4) % 4));
  return Uint8Array.from(bytes, character => character.charCodeAt(0));
}

export async function importAuthenticationKeys(keys) {
  return Promise.all(keys.map(({publicKey}) => crypto.subtle.importKey('spki',
    Uint8Array.from(atob(publicKey), character => character.charCodeAt(0)),
    {name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256'}, false, ['verify'])));
}

let trustedKeys;

export async function verifyMinecraftToken(token, now = Date.now(), keys) {
  try {
    if (typeof token !== 'string' || token.length > 8192) return null;
    const parts = token.split('.');
    if (parts.length !== 3 || parts[0].length > 2048) return null;
    const header = JSON.parse(decoder.decode(decode(parts[0])));
    if (header.alg !== 'RS256' || (header.crit != null && (!Array.isArray(header.crit) || header.crit.length))) return null;
    const signature = decode(parts[2]);
    if (signature.length > 1024) return null;
    const signed = new TextEncoder().encode(parts[0] + '.' + parts[1]);
    const approved = keys || await (trustedKeys ||= importAuthenticationKeys(published.keys));
    let valid = false;
    for (const key of approved) {
      if (await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, signature, signed)) { valid = true; break; }
    }
    if (!valid) return null;
    const claims = JSON.parse(decoder.decode(decode(parts[1])));
    const seconds = now / 1000;
    if (!Number.isSafeInteger(claims.exp) || claims.exp <= seconds) return null;
    if (claims.nbf != null && (!Number.isSafeInteger(claims.nbf) || claims.nbf > seconds + 30)) return null;
    if (claims.iat != null && (!Number.isSafeInteger(claims.iat) || claims.iat > seconds + 30)) return null;
    if (claims.iss !== 'authentication') return null;
    const profile = claims.profiles?.mc;
    if (typeof profile !== 'string' || !/^(?:[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/.test(profile)) return null;
    const uuid = profile.replaceAll('-', '');
    return {uuid, expiresAt: claims.exp * 1000};
  } catch { return null; }
}
