export const HEARTBEAT_SECONDS = 30;
export const LEASE_MILLISECONDS = 90_000;
export const MAX_USAGE_BODY = 512;
export const MAX_INSTALLATIONS = 16_000;
export const MAX_SESSIONS = 8;
export const USAGE_ORIGINS = new Set(['https://arcadiumclient.com', 'https://www.arcadiumclient.com']);

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export function usageCredentials(request) {
  const authorization = request.headers.get('Authorization') || '';
  return /^Bearer [0-9a-f]{64}$/.test(authorization) ? authorization.slice(7) : null;
}

export async function usageBody(request) {
  if (request.headers.get('Content-Type')?.split(';', 1)[0].trim().toLowerCase() !== 'application/json') {
    throw new Error('A JSON body is required');
  }
  const declared = Number(request.headers.get('Content-Length') || 0);
  if (declared > MAX_USAGE_BODY) throw new Error('Request body is too large');
  const reader = request.body?.getReader();
  if (!reader) throw new Error('A JSON body is required');
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_USAGE_BODY) {
        await reader.cancel();
        throw new Error('Request body is too large');
      }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  const value = JSON.parse(new TextDecoder('utf-8', {fatal: true}).decode(bytes));
  if (!value || Array.isArray(value) || Object.keys(value).sort().join(',') !== 'client,installationId,sessionId'
    || !uuid.test(value.installationId) || !uuid.test(value.sessionId)
    || !['launcher', 'game'].includes(value.client)) throw new Error('Invalid heartbeat');
  return value;
}

export function usageResponse(status, value, request, cache = false) {
  const origin = request.headers.get('Origin');
  const headers = {
    'Cache-Control': cache ? 'public, max-age=15, s-maxage=15' : 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Vary': 'Origin'
  };
  if (cache) {
    headers.Date = new Date().toUTCString();
    headers['Access-Control-Expose-Headers'] = 'Date';
  }
  if (USAGE_ORIGINS.has(origin)) headers['Access-Control-Allow-Origin'] = origin;
  if (status === 429) headers['Retry-After'] = '60';
  return Response.json(value, {status, headers});
}
