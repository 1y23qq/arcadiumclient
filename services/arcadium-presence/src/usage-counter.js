import {DurableObject} from 'cloudflare:workers';
import {tokenHash} from './protocol.js';
import {UsageLedger} from './usage-ledger.js';
import {usageBody, usageCredentials, usageResponse} from './usage-protocol.js';

export class ArcadiumUsage extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.ctx = ctx;
    this.ledger = new UsageLedger(ctx.storage);
    this.windows = new Map();
    this.rateSalt = crypto.randomUUID();
    this.ready = this.ctx.blockConcurrencyWhile(() => this.ledger.restore());
  }

  async fetch(request) {
    await this.ready;
    const path = new URL(request.url).pathname;
    if (path === '/v1/usage/count' && request.method === 'GET') {
      return usageResponse(200, this.ledger.snapshot(), request, true);
    }
    if (path !== '/v1/usage/heartbeat' || !['POST', 'DELETE'].includes(request.method)) {
      return usageResponse(405, {error: 'Method not allowed'}, request);
    }
    const credentials = usageCredentials(request);
    if (!credentials) return usageResponse(401, {error: 'Installation credentials required'}, request);
    const now = Date.now();
    const address = await tokenHash(this.rateSalt + (request.headers.get('CF-Connecting-IP') || 'local'));
    for (const [key, window] of this.windows) if (window.until <= now) this.windows.delete(key);
    let window = this.windows.get(address);
    if (!window) {
      if (this.windows.size >= 8192) return usageResponse(503, {error: 'Online service is busy'}, request);
      window = {until: now + 60_000, requests: 0, registrations: 0};
      this.windows.set(address, window);
    }
    if (++window.requests > 240) return usageResponse(429, {error: 'Please wait before retrying'}, request);
    let body;
    try { body = await usageBody(request); }
    catch { return usageResponse(400, {error: 'Invalid heartbeat body'}, request); }
    const [id, secretHash, session] = await Promise.all([
      tokenHash(body.installationId), tokenHash(credentials), tokenHash(body.sessionId)
    ]);
    const result = await this.ledger.serialize(async () => {
      const result = request.method === 'DELETE'
        ? await this.ledger.close(id, secretHash, session, body.client)
        : await this.ledger.heartbeat(id, secretHash, session, body.client, window.registrations < 12);
      if (result.registered) window.registrations++;
      if (result.status === 200 && this.ledger.installations.size && !(await this.ctx.storage.getAlarm())) {
        await this.ctx.storage.setAlarm(Date.now() + 30_000);
      }
      return result;
    });
    return usageResponse(result.status, result.body, request);
  }

  async alarm() {
    await this.ready;
    await this.ledger.serialize(async () => {
      await this.ledger.collect();
      if (this.ledger.installations.size) await this.ctx.storage.setAlarm(Date.now() + 30_000);
    });
  }
}
