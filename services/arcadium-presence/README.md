# Arcadium presence

The badge service verifies Minecraft sessions. The separate usage Durable Object
counts active installations without Minecraft authentication. Neither service
logs credentials or request bodies. Worker observability stays disabled.

## Online count protocol

Base URL: `https://presence-test.arcadiumclient.com`

`POST /v1/usage/heartbeat` renews a 90-second process lease. Clients send a
heartbeat every 30 seconds. `DELETE /v1/usage/heartbeat` closes only that process
session and is idempotent. Both requests use `Content-Type: application/json`,
`Authorization: Bearer <64 lowercase hex characters>`, and this exact body:

```json
{
  "installationId": "11111111-1111-4111-8111-111111111111",
  "sessionId": "22222222-2222-4222-8222-222222222222",
  "client": "launcher"
}
```

Both identifiers are random version 4 UUIDs. The installation identifier and
256-bit secret are shared by the launcher and game through a private local
identity file. Each process creates its own session identifier. The `client`
value is `launcher` or `game` and cannot change for an existing session. No
Minecraft access token, account name or player UUID is sent to this endpoint.

A heartbeat returns `{accepted:true, expiresAt:<epoch milliseconds>,
heartbeatSeconds:30}`. Closing returns `{accepted:true}`. A live installation's
secret cannot be replaced. Each installation counts once while at least one of
its process leases is active. Crashes expire automatically. Turning off the
launcher's Online Count setting stops both clients and closes their leases.

`GET /v1/usage/count` returns only `{online:<integer>, updatedAt:<epoch
milliseconds>}`. A response can be cached at the edge for 15 seconds. CORS is
restricted to the official apex and www origins. Browser heartbeat preflights
are denied. Count responses expose their HTTP Date header so the website can
validate freshness independently of the visitor's computer clock. Native
requests omit Origin. Queries are not accepted.

The service stores hashes of installation identifiers, secrets and process
identifiers, plus lease expiry times and process types. It keeps no activity
history. Expired sessions are collected every 30 seconds while records remain.
It retains at most 16,000 installations and eight process sessions per
installation. A transient salted address hash limits new registrations to 12
per minute and requests to 240 per minute per connection address. These
in-memory rate buckets expire after one minute, with at most 8,192 buckets.

Anonymous clients cannot establish that an installation belongs to a unique
person. The number measures installations reporting activity, with bounded
abuse controls. Legacy clients without this heartbeat are not counted.

## Test and deploy

`npm test` runs protocol, storage, lease, rate limit and workerd integration
tests. Workerd tests cover actual Durable Object eviction and restoration,
separate launcher/game sessions, cache/CORS behavior and badge authentication
isolation. They use generated fixture credentials rather than real accounts.

`npm run deploy` deploys the Worker and both SQLite Durable Object bindings.
Deploying this backend does not publish a client build or change download links.
The website's CSP explicitly permits only the usage service for the online count.
