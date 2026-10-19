import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import {fileURLToPath, pathToFileURL} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dist = path.join(root, 'sites', 'arcadium-official', 'dist');
const releaseVersion = JSON.parse(await fs.readFile(path.join(dist, 'release.json'), 'utf8')).version;
const evidence = path.join(root, 'build', 'website-online-count');
const modulePath = process.env.ARCADIUM_PLAYWRIGHT_MODULE || path.join(os.homedir(), '.cache', 'codex-runtimes', 'codex-primary-runtime', 'dependencies', 'node', 'node_modules', 'playwright', 'index.mjs');
const {chromium} = await import(pathToFileURL(modulePath).href);
const executablePath = process.env.ARCADIUM_CHROMIUM || path.join(os.homedir(), 'AppData', 'Local', 'ms-playwright', 'chromium_headless_shell-1243', 'chrome-headless-shell-win64', 'chrome-headless-shell.exe');
const browser = await chromium.launch({headless: true, executablePath});
const mime = {'.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.ttf': 'font/ttf', '.woff2': 'font/woff2'};
const csp = (await fs.readFile(path.join(dist, '_headers'), 'utf8')).split('\n').find(line => line.includes('Content-Security-Policy:')).trim().slice('Content-Security-Policy:'.length).trim();
const cases = [];
await fs.mkdir(evidence, {recursive: true});
try {
  for (const scenario of [
    {name: 'desktop', width: 1440, online: 1234, expected: '1,234 online now'},
    {name: 'compact-desktop', width: 1024, online: 16000, expected: '16,000 online now'},
    {name: 'laptop', width: 1100, online: 16000, expected: '16,000 online now'},
    {name: 'medium-desktop', width: 1280, online: 16000, expected: '16,000 online now'},
    {name: 'tablet', width: 720, online: 13, expected: '13 online now'},
    {name: 'mobile', width: 390, online: 13, expected: '13 online now'},
    {name: 'small-mobile', width: 320, online: 0, expected: '0 online now'},
    {name: 'unavailable', width: 390, status: 503, expected: 'Online count unavailable'},
    {name: 'malformed', width: 390, online: -1, expected: 'Online count unavailable'},
    {name: 'stale', width: 390, online: 20, age: 121000, expected: 'Online count unavailable'},
    {name: 'future', width: 390, online: 20, age: -31000, expected: 'Online count unavailable'},
    {name: 'too-large', width: 390, online: 16001, expected: 'Online count unavailable'},
    {name: 'slow-clock', width: 1440, online: 9, clockOffset: -31536000000, expected: '9 online now'},
    {name: 'fast-clock', width: 390, online: 9, clockOffset: 31536000000, expected: '9 online now'}
  ]) {
    const page = await browser.newPage({viewport: {width: scenario.width, height: 1000}});
    if (scenario.clockOffset) await page.clock.install({time: new Date(Date.now() + scenario.clockOffset)});
    const errors = [];
    let requests = 0;
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin === 'https://presence-test.arcadiumclient.com') {
        requests++;
        assert.equal(url.pathname, '/v1/usage/count');
        assert.equal(route.request().headers().authorization, undefined);
        assert.equal(route.request().headers().cookie, undefined);
        return route.fulfill({status: scenario.status || 200, headers: {'Content-Type': 'application/json', 'Access-Control-Allow-Origin': 'https://arcadiumclient.com',
          'Access-Control-Expose-Headers': 'Date', 'Date': new Date().toUTCString()},
          body: JSON.stringify({online: scenario.online, updatedAt: Date.now() - (scenario.age || 0)})});
      }
      if (url.origin !== 'https://arcadiumclient.com') throw new Error('Unexpected external request: ' + url.origin);
      const relative = decodeURIComponent(url.pathname).replace(/^\/+/, '') + (url.pathname.endsWith('/') ? 'index.html' : '');
      const file = path.resolve(dist, relative);
      assert.ok(file.startsWith(dist + path.sep));
      return route.fulfill({status: 200, headers: {'Content-Type': mime[path.extname(file)] || 'application/octet-stream', 'Content-Security-Policy': csp}, body: await fs.readFile(file)});
    });
    await page.goto('https://arcadiumclient.com/');
    await page.waitForFunction(expected => document.querySelector('[data-online-count]').textContent === expected, scenario.expected);
    await page.evaluate(() => document.fonts.ready);
    const count = page.locator('[data-online-count]');
    await assert.doesNotReject(() => count.waitFor({state: 'visible'}));
    const countBox = await count.boundingBox();
    assert.ok(countBox.width <= scenario.width);
    assert.ok(countBox.x >= 0 && countBox.x + countBox.width <= scenario.width);
    const discordBox = await page.locator('.site-header a[href="https://discord.gg/QbNfaCHjUz"]').boundingBox();
    const headerBox = await page.locator('.header-inner').boundingBox();
    if (await page.locator('.site-nav').isVisible()) {
      const navBox = await page.locator('.site-nav').boundingBox();
      assert.ok(navBox.x + navBox.width <= countBox.x, 'Navigation must not overlap the online count');
    }
    assert.ok(countBox.x + countBox.width <= discordBox.x, 'Count belongs immediately before the header buttons');
    assert.ok(countBox.y >= headerBox.y && countBox.y + countBox.height <= headerBox.y + headerBox.height);
    assert.equal(await page.locator('.site-header a[href="https://discord.gg/QbNfaCHjUz"]').count(), 1);
    assert.equal(await page.locator('.site-footer a[href="https://discord.gg/QbNfaCHjUz"]').count(), 1);
    assert.equal(await page.locator('[data-release-version]').first().textContent(), releaseVersion);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    assert.deepEqual(errors, []);
    assert.equal(requests, 1);
    if (['desktop', 'mobile', 'unavailable'].includes(scenario.name)) await page.screenshot({path: path.join(evidence, scenario.name + '.png')});
    cases.push({name: scenario.name, width: scenario.width, text: await count.textContent(), requests, errors});
    await page.close();
  }

  for (const width of [320, 1440]) for (const pagePath of ['/download/', '/privacy/', '/terms/', '/404.html']) {
    const page = await browser.newPage({viewport: {width, height: 900}});
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin === 'https://presence-test.arcadiumclient.com') {
        return route.fulfill({status: 200, headers: {'Content-Type': 'application/json', 'Access-Control-Allow-Origin': 'https://arcadiumclient.com'}, body: JSON.stringify({online: 16000, updatedAt: Date.now()})});
      }
      assert.equal(url.origin, 'https://arcadiumclient.com');
      const relative = decodeURIComponent(url.pathname).replace(/^\/+/, '') + (url.pathname.endsWith('/') ? 'index.html' : '');
      const file = path.resolve(dist, relative);
      assert.ok(file.startsWith(dist + path.sep));
      return route.fulfill({status: 200, headers: {'Content-Type': mime[path.extname(file)] || 'application/octet-stream', 'Content-Security-Policy': csp}, body: await fs.readFile(file)});
    });
    await page.goto('https://arcadiumclient.com' + pagePath);
    await page.waitForFunction(() => document.querySelector('[data-online-count]').textContent === '16,000 online now');
    await page.evaluate(() => document.fonts.ready);
    const count = await page.locator('.header-actions [data-online-count]').boundingBox();
    const button = await page.locator('.site-header a[href="https://discord.gg/QbNfaCHjUz"]').boundingBox();
    assert.ok(count.x >= 0 && count.x + count.width <= button.x);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    cases.push({name: 'header-' + pagePath, width, passed: true});
    await page.close();
  }

  const page = await browser.newPage({viewport: {width: 390, height: 1000}});
  await page.clock.install();
  let failed = true;
  let requests = 0;
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.origin === 'https://presence-test.arcadiumclient.com') {
      requests++;
      return route.fulfill({status: failed ? 503 : 200,
        headers: {'Content-Type': 'application/json', 'Access-Control-Allow-Origin': 'https://arcadiumclient.com'},
        body: JSON.stringify({online: 7, updatedAt: Date.now()})});
    }
    assert.equal(url.origin, 'https://arcadiumclient.com');
    const file = path.join(dist, url.pathname === '/' ? 'index.html' : url.pathname);
    return route.fulfill({status: 200, headers: {'Content-Type': mime[path.extname(file)] || 'application/octet-stream'}, body: await fs.readFile(file)});
  });
  await page.goto('https://arcadiumclient.com/');
  await page.waitForFunction(() => document.querySelector('[data-online-count]').textContent === 'Online count unavailable');
  await page.waitForFunction(() => !document.querySelector('[data-online-count] strong'));
  await page.waitForTimeout(20);
  assert.equal(requests, 1);
  failed = false;
  await page.clock.runFor(30000);
  await page.waitForFunction(() => document.querySelector('[data-online-count]').textContent === '7 online now');
  assert.equal(requests, 2);
  await page.evaluate(() => Object.defineProperty(document, 'hidden', {configurable: true, value: true}));
  await page.clock.runFor(30000);
  assert.equal(requests, 2, 'Hidden pages must not poll the service');
  await page.evaluate(() => { delete document.hidden; document.dispatchEvent(new Event('visibilitychange')); });
  await page.waitForFunction(() => document.querySelector('[data-online-count]').textContent === '7 online now');
  await page.waitForTimeout(20);
  assert.equal(requests, 3, 'Returning to a visible page must resume updates');
  failed = true;
  await page.clock.runFor(30000);
  await page.waitForFunction(() => document.querySelector('[data-online-count]').textContent === 'Online count unavailable');
  assert.equal(requests, 4, 'Service failure must clear an earlier count instead of keeping it as live');
  cases.push({name: 'polling-recovery-and-visibility', requests, passed: true});
  await page.close();
  await fs.writeFile(path.join(evidence, 'verification.json'), JSON.stringify({passed: true, cases}, null, 2) + '\n');
  process.stdout.write(JSON.stringify({passed: true, cases: cases.length, evidence}) + '\n');
} finally { await browser.close(); }
