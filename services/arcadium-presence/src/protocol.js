export const MAX_PLAYERS = 256;
export const MAX_BODY = 12_288;
export const SESSION_SECONDS = 6 * 60 * 60;

export function playerId(value) {
  if (typeof value !== 'string') return null;
  const normalized = value.toLowerCase().replaceAll('-', '');
  return /^[0-9a-f]{32}$/.test(normalized) ? normalized : null;
}

export function watchedPlayers(value) {
  if (!Array.isArray(value) || value.length > MAX_PLAYERS) throw new Error('Invalid player list');
  const players = value.map(playerId);
  if (players.some(id => id === null)) throw new Error('Invalid player identifier');
  return [...new Set(players)];
}

export function bearer(request, maxLength = 8192) {
  const header = request.headers.get('Authorization');
  if (!header || !header.startsWith('Bearer ')) return null;
  const token = header.slice(7);
  return token.length > 0 && token.length <= maxLength && !/\s/.test(token) ? token : null;
}

export function response(status, value) {
  return Response.json(value, {status, headers: {'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff'}});
}

export async function tokenHash(token) {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(token));
  return [...new Uint8Array(bytes)].map(byte => byte.toString(16).padStart(2, '0')).join('');
}

export function sessionToken() {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('');
}
