import {DurableObject} from 'cloudflare:workers';
import {bearer, MAX_BODY, playerId, response, SESSION_SECONDS, sessionToken, tokenHash, watchedPlayers} from './protocol.js';
import {verifyMinecraftToken} from './minecraft-token.js';
import {USAGE_ORIGINS, usageResponse} from './usage-protocol.js';
export {ArcadiumUsage} from './usage-counter.js';

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === '/health' && request.method === 'GET') return response(200, {service: 'Arcadium presence', version: '0.3.0'});
    if (['/v1/usage/heartbeat', '/v1/usage/count'].includes(url.pathname)) {
      if (url.search) return usageResponse(400, {error: 'Query parameters are not supported'}, request);
      const origin = request.headers.get('Origin');
      if (origin && !USAGE_ORIGINS.has(origin)) return usageResponse(403, {error: 'Origin is not allowed'}, request);
      if (request.method === 'OPTIONS') {
        if (!USAGE_ORIGINS.has(origin) || url.pathname !== '/v1/usage/count'
          || request.headers.get('Access-Control-Request-Method') !== 'GET'
          || request.headers.get('Access-Control-Request-Headers')) {
          return usageResponse(403, {error: 'Cross-origin request is not allowed'}, request);
        }
        return new Response(null, {status: 204, headers: {
          'Access-Control-Allow-Origin': origin, 'Access-Control-Allow-Methods': 'GET',
          'Access-Control-Max-Age': '600', 'Vary': 'Origin', 'Cache-Control': 'no-store'
        }});
      }
      const stub = env.USAGE.getByName('arcadium-online-v1');
      if (url.pathname === '/v1/usage/count' && request.method === 'GET') {
        const key = new Request(url.origin + url.pathname);
        const cached = await caches.default.match(key);
        if (cached) return usageResponse(200, await cached.json(), request, true);
        const result = await stub.fetch(request);
        if (!result.ok) return result;
        const value = await result.json();
        const entry = Response.json(value, {headers: {'Cache-Control': 'public, max-age=15'}});
        ctx.waitUntil(caches.default.put(key, entry));
        return usageResponse(200, value, request, true);
      }
      return stub.fetch(request);
    }
    if (!['/v1/session', '/v1/socket'].includes(url.pathname)) return response(404, {error: 'Not found'});
    if (url.search) return response(400, {error: 'Query parameters are not supported'});
    if (!bearer(request)) return response(401, {error: 'Authentication required'});
    const stub = env.PRESENCE.getByName('arcadium-presence-test-v1');
    return stub.fetch(request);
  }
};

export class ArcadiumPresence extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.ctx = ctx;
    this.env = env;
    this.authAttempts = new Map();
    this.messageWindows = new WeakMap();
    this.ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair('ping', 'pong'));
  }

  async fetch(request) {
    const path = new URL(request.url).pathname;
    if (path === '/v1/session' && request.method === 'POST') return this.createSession(request);
    if (path === '/v1/session' && request.method === 'DELETE') return this.revokeSession(request);
    if (path === '/v1/socket' && request.method === 'GET') return this.connect(request);
    return response(405, {error: 'Method not allowed'});
  }

  async createSession(request) {
    if (Number(request.headers.get('Content-Length') || 0) > 0) return response(400, {error: 'Session requests have no body'});
    const minecraftToken = bearer(request);
    if (!minecraftToken) return response(401, {error: 'Authentication required'});
    const now = Date.now();
    const origin = request.headers.get('CF-Connecting-IP') || 'local';
    const window = this.authAttempts.get(origin);
    if (window && window.until > now && window.count >= 8) return response(429, {error: 'Please wait before retrying'});
    this.authAttempts.set(origin, window && window.until > now
      ? {count: window.count + 1, until: window.until}
      : {count: 1, until: now + 60_000});
    for (const [key, value] of this.authAttempts) if (value.until <= now) this.authAttempts.delete(key);
    const verified = await verifyMinecraftToken(minecraftToken, now);
    let uuid = verified?.uuid;
    if (!uuid) try {
      const upstream = await fetch('https://api.minecraftservices.com/minecraft/profile', {
        headers: {
          Authorization: `Bearer ${minecraftToken}`,
          Accept: 'application/json',
          'User-Agent': 'Arcadium 0.2.8 (https://github.com/0aurorq3/arcadiumclient)'
        },
        redirect: 'manual', signal: AbortSignal.timeout(10_000)
      });
      if (!upstream.ok) {
        return response(upstream.status === 401 ? 401 : 503, {error: 'Minecraft account verification failed'});
      }
      const profile = await upstream.json();
      uuid = playerId(profile?.id);
      if (!uuid || typeof profile?.name !== 'string') return response(401, {error: 'Minecraft account verification failed'});
    } catch {
      return response(503, {error: 'Minecraft account verification is unavailable'});
    }
    const token = sessionToken();
    const hash = await tokenHash(token);
    const expiresAt = Math.min(now + SESSION_SECONDS * 1000, verified?.expiresAt ?? Infinity);
    const admission = await this.ctx.blockConcurrencyWhile(async () => {
      const retained = await this.ctx.storage.list({prefix: 'session:', limit: 513});
      for (const [key, value] of retained) {
        if (value.expiresAt <= Date.now()) {
          await this.ctx.storage.delete(key);
          retained.delete(key);
        }
      }
      const own = [...retained].filter(([, value]) => value.uuid === uuid);
      if (own.length >= 3) {
        const connected = new Set(this.ctx.getWebSockets().map(socket => socket.deserializeAttachment()?.hash));
        const idle = own.find(([key]) => !connected.has(key.slice('session:'.length)));
        if (!idle) return 429;
        await this.ctx.storage.delete(idle[0]);
        retained.delete(idle[0]);
      }
      if (retained.size >= 512) return 503;
      await this.ctx.storage.put('session:' + hash, {uuid, expiresAt});
      return 200;
    });
    if (admission !== 200) return response(admission, {error: 'Test service session limit reached'});
    const alarm = await this.ctx.storage.getAlarm();
    if (!alarm) await this.ctx.storage.setAlarm(now + 60 * 60 * 1000);
    return response(200, {token, uuid, expiresAt});
  }

  async authenticated(request) {
    const token = bearer(request, 64);
    if (!token || !/^[0-9a-f]{64}$/.test(token)) return null;
    const hash = await tokenHash(token);
    const session = await this.ctx.storage.get('session:' + hash);
    return session && session.expiresAt > Date.now() ? {...session, hash} : null;
  }

  async revokeSession(request) {
    const session = await this.authenticated(request);
    if (!session) return response(401, {error: 'Invalid or expired session'});
    await this.ctx.storage.delete('session:' + session.hash);
    for (const socket of this.ctx.getWebSockets()) {
      if (socket.deserializeAttachment()?.hash === session.hash) socket.close(1000, 'Signed out');
    }
    this.broadcast();
    return response(200, {revoked: true});
  }

  async connect(request) {
    if (request.headers.get('Upgrade')?.toLowerCase() !== 'websocket') return response(426, {error: 'WebSocket connection required'});
    const session = await this.authenticated(request);
    if (!session) return response(401, {error: 'Invalid or expired session'});
    const sockets = this.ctx.getWebSockets();
    if (sockets.length >= 200) return response(503, {error: 'Test service is at capacity'});
    const own = sockets.filter(socket => socket.deserializeAttachment()?.uuid === session.uuid);
    if (own.length >= 3) return response(429, {error: 'Too many account connections'});
    const pair = new WebSocketPair();
    pair[1].serializeAttachment({...session, watched: []});
    this.ctx.acceptWebSocket(pair[1]);
    this.broadcast(session.uuid);
    return new Response(null, {status: 101, webSocket: pair[0]});
  }

  webSocketMessage(socket, message) {
    if (typeof message !== 'string' || new TextEncoder().encode(message).length > MAX_BODY) {
      socket.close(1009, 'Message too large');
      return;
    }
    const now = Date.now();
    const window = this.messageWindows.get(socket);
    if (window && window.until > now && window.count >= 12) {
      socket.close(1008, 'Too many messages');
      return;
    }
    this.messageWindows.set(socket, window && window.until > now
      ? {count: window.count + 1, until: window.until}
      : {count: 1, until: now + 10_000});
    const attachment = socket.deserializeAttachment();
    if (!attachment || attachment.expiresAt <= now) {
      socket.close(1008, 'Session expired');
      return;
    }
    try {
      const packet = JSON.parse(message);
      if (packet.type !== 'watch') throw new Error();
      const watched = watchedPlayers(packet.players);
      socket.serializeAttachment({...attachment, watched});
      this.broadcast();
    } catch {
      socket.close(1008, 'Invalid message');
    }
  }

  activePlayers() {
    return new Set(this.ctx.getWebSockets().filter(socket => socket.readyState === 1)
      .map(socket => socket.deserializeAttachment()).filter(value => value?.expiresAt > Date.now()).map(value => value.uuid));
  }

  sendSnapshot(socket, active) {
    if (socket.readyState !== 1) return;
    const attachment = socket.deserializeAttachment();
    if (!attachment || attachment.expiresAt <= Date.now()) {
      socket.close(1008, 'Session expired');
      return;
    }
    const watched = attachment.watched || [];
    try { socket.send(JSON.stringify({type: 'players', players: watched.filter(uuid => active.has(uuid))})); }
    catch { socket.close(1011, 'Connection unavailable'); }
  }

  broadcast(joiningUuid = null) {
    const active = this.activePlayers();
    if (joiningUuid) active.add(joiningUuid);
    for (const socket of this.ctx.getWebSockets()) this.sendSnapshot(socket, active);
  }

  webSocketClose() { this.broadcast(); }
  webSocketError(socket) { socket.close(1011, 'Connection unavailable'); this.broadcast(); }

  async alarm() {
    const now = Date.now();
    let cursor;
    let remaining = false;
    while (true) {
      const sessions = await this.ctx.storage.list({prefix: 'session:', limit: 128, ...(cursor ? {startAfter: cursor} : {})});
      for (const [key, session] of sessions) {
        cursor = key;
        if (session.expiresAt <= now) await this.ctx.storage.delete(key);
        else remaining = true;
      }
      if (sessions.size < 128) break;
    }
    for (const socket of this.ctx.getWebSockets()) {
      if (socket.deserializeAttachment()?.expiresAt <= now) socket.close(1008, 'Session expired');
    }
    if (remaining || this.ctx.getWebSockets().length) await this.ctx.storage.setAlarm(now + 60 * 60 * 1000);
  }
}
