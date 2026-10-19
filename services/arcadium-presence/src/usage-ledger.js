import {HEARTBEAT_SECONDS, LEASE_MILLISECONDS, MAX_INSTALLATIONS, MAX_SESSIONS} from './usage-protocol.js';

export class UsageLedger {
  constructor(storage, clock = Date.now) {
    this.storage = storage;
    this.clock = clock;
    this.installations = new Map();
    this.tail = Promise.resolve();
  }

  async restore() {
    let cursor;
    while (true) {
      const records = await this.storage.list({prefix: 'usage:', limit: 1000, ...(cursor ? {startAfter: cursor} : {})});
      for (const [key, value] of records) {
        cursor = key;
        this.installations.set(key.slice(6), value);
      }
      if (records.size < 1000) break;
    }
    await this.collect();
  }

  serialize(action) {
    const pending = this.tail.then(action);
    this.tail = pending.catch(() => {});
    return pending;
  }

  async collect() {
    const now = this.clock();
    const removed = [];
    const updated = [];
    for (const [id, record] of this.installations) {
      const sessions = Object.fromEntries(Object.entries(record.sessions).filter(([, lease]) => lease.expiresAt > now));
      if (!Object.keys(sessions).length) removed.push(id);
      else if (Object.keys(sessions).length !== Object.keys(record.sessions).length) updated.push([id, {...record, sessions}]);
    }
    for (let offset = 0; offset < removed.length; offset += 128) {
      await this.storage.delete(removed.slice(offset, offset + 128).map(id => 'usage:' + id));
    }
    for (let offset = 0; offset < updated.length; offset += 128) {
      await this.storage.put(Object.fromEntries(updated.slice(offset, offset + 128).map(([id, value]) => ['usage:' + id, value])));
    }
    for (const id of removed) this.installations.delete(id);
    for (const [id, value] of updated) this.installations.set(id, value);
  }

  async heartbeat(id, secretHash, session, client, registrationAllowed = true) {
    const now = this.clock();
    let previous = this.installations.get(id);
    if (previous && !Object.values(previous.sessions).some(lease => lease.expiresAt > now)) {
      await this.storage.delete('usage:' + id);
      this.installations.delete(id);
      previous = undefined;
    }
    if (previous && previous.secretHash !== secretHash) return {status: 403, body: {error: 'Invalid installation credentials'}};
    if (!previous) {
      if (!registrationAllowed) return {status: 429, body: {error: 'Please wait before retrying'}};
      if (this.installations.size >= MAX_INSTALLATIONS) await this.collect();
      if (this.installations.size >= MAX_INSTALLATIONS) return {status: 503, body: {error: 'Online service is at capacity'}};
    }
    const sessions = {...previous?.sessions};
    for (const [key, lease] of Object.entries(sessions)) if (lease.expiresAt <= now) delete sessions[key];
    if (!sessions[session] && Object.keys(sessions).length >= MAX_SESSIONS) {
      return {status: 429, body: {error: 'Too many active installation sessions'}};
    }
    const existing = sessions[session];
    if (existing && existing.client !== client) return {status: 409, body: {error: 'Session type cannot change'}};
    if (existing && now - existing.lastBeat < 5000) {
      return {status: 200, body: {accepted: true, expiresAt: existing.expiresAt, heartbeatSeconds: HEARTBEAT_SECONDS}, registered: false};
    }
    const expiresAt = now + LEASE_MILLISECONDS;
    sessions[session] = {client, expiresAt, lastBeat: now};
    const record = {secretHash, sessions};
    await this.storage.put('usage:' + id, record);
    this.installations.set(id, record);
    return {status: 200, body: {accepted: true, expiresAt, heartbeatSeconds: HEARTBEAT_SECONDS}, registered: !previous};
  }

  async close(id, secretHash, session, client) {
    const record = this.installations.get(id);
    if (!record) return {status: 200, body: {accepted: true}};
    if (record.secretHash !== secretHash) return {status: 403, body: {error: 'Invalid installation credentials'}};
    if (record.sessions[session] && record.sessions[session].client !== client) {
      return {status: 409, body: {error: 'Session type cannot change'}};
    }
    const sessions = {...record.sessions};
    delete sessions[session];
    if (Object.keys(sessions).length) {
      const updated = {...record, sessions};
      await this.storage.put('usage:' + id, updated);
      this.installations.set(id, updated);
    } else {
      await this.storage.delete('usage:' + id);
      this.installations.delete(id);
    }
    return {status: 200, body: {accepted: true}};
  }

  snapshot() {
    const updatedAt = this.clock();
    let online = 0;
    for (const record of this.installations.values()) {
      if (Object.values(record.sessions).some(lease => lease.expiresAt > updatedAt)) online++;
    }
    return {online, updatedAt};
  }
}
