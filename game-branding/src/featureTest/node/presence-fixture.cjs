const {Miniflare, Response, convertV4MiniflareOptions} = require('../../../../services/arcadium-presence/node_modules/miniflare');
const http = require('node:http');
const path = require('node:path');
const fs = require('node:fs');

const repo = path.resolve(__dirname, '../../../..');
const alice = '11111111111141118111111111111111';
const bob = '22222222222242228222222222222222';
const charlie = '33333333333343338333333333333333';
const profiles = new Map([
  ['arcadium-presence-qa-alice-token-00001', {id: alice, name: 'PresenceAlice'}],
  ['arcadium-presence-qa-bob-token-0000001', {id: bob, name: 'PresenceBob'}],
  ['arcadium-presence-qa-charlie-token-001', {id: charlie, name: 'PresenceCharlie'}]
]);
const verified = new Map();
const reportFile = process.argv[2];
let bobSession;
let bobSocket;
let bobOnline = false;
let stopping = false;
let control;

const runtime = new Miniflare(convertV4MiniflareOptions({workers: [{
  name: 'arcadium-presence-game-fixture',
  modules: [
    {type: 'ESModule', path: path.join(repo, 'services/arcadium-presence/src/worker.js')},
    {type: 'ESModule', path: path.join(repo, 'services/arcadium-presence/src/protocol.js')}
  ],
  compatibilityDate: '2026-10-01',
  durableObjects: {PRESENCE: {className: 'ArcadiumPresence', useSQLite: true}},
  outboundService: request => {
    const url = new URL(request.url);
    if (url.origin + url.pathname !== 'https://api.minecraftservices.com/minecraft/profile' || url.search) return new Response(null, {status: 403});
    const profile = profiles.get(request.headers.get('Authorization')?.replace(/^Bearer /, ''));
    if (!profile) return Response.json({error: 'Fixture credentials required'}, {status: 401});
    verified.set(profile.id, (verified.get(profile.id) || 0) + 1);
    return Response.json(profile);
  }
}]}));

const call = (route, init) => runtime.dispatchFetch('https://presence-fixture.test' + route, init);

async function connectBob() {
  if (bobSocket?.readyState === 1) return;
  if (!bobSession) {
    const response = await call('/v1/session', {method: 'POST', headers: {Authorization: 'Bearer arcadium-presence-qa-bob-token-0000001'}});
    if (response.status !== 200) throw new Error('Fixture peer authentication failed');
    bobSession = await response.json();
  }
  const response = await call('/v1/socket', {headers: {Upgrade: 'websocket', Authorization: 'Bearer ' + bobSession.token}});
  if (response.status !== 101) throw new Error('Fixture peer connection failed');
  bobSocket = response.webSocket;
  bobSocket.addEventListener('close', () => { bobOnline = false; });
  bobSocket.accept();
  bobOnline = true;
  bobSocket.send(JSON.stringify({type: 'watch', players: [alice, charlie]}));
}

function state() {
  return {bobOnline, verified: Object.fromEntries(verified)};
}

async function shutdown() {
  if (stopping) return;
  stopping = true;
  if (bobSocket?.readyState === 1) bobSocket.close(1000, 'Fixture finished');
  control?.close();
  await runtime.dispose();
}

(async () => {
  await connectBob();
  const origin = (await runtime.ready).origin;
  control = http.createServer(async (request, response) => {
    const route = new URL(request.url, 'http://127.0.0.1').pathname;
    try {
      if (route === '/bob/disconnect' && request.method === 'POST') {
        if (bobSocket?.readyState === 1) bobSocket.close(1000, 'Fixture peer disconnected');
        bobOnline = false;
      } else if (route === '/bob/connect' && request.method === 'POST') {
        await connectBob();
      } else if (route !== '/status') {
        response.writeHead(404).end();
        return;
      }
      response.writeHead(200, {'Content-Type': 'application/json', 'Cache-Control': 'no-store'}).end(JSON.stringify(state()));
    } catch {
      response.writeHead(500).end('Fixture control failed');
    }
  });
  await new Promise(resolve => control.listen(0, '127.0.0.1', resolve));
  const controlOrigin = 'http://127.0.0.1:' + control.address().port;
  const report = {apiOrigin: origin, controlOrigin, ...state()};
  if (reportFile) fs.writeFileSync(reportFile, JSON.stringify(report, null, 2));
  console.log(JSON.stringify({apiOrigin: origin, controlOrigin, bobOnline}));
})().catch(async () => { console.error('Presence fixture startup failed'); await shutdown(); process.exitCode = 1; });
process.on('SIGINT', () => shutdown());
process.on('SIGTERM', () => shutdown());
