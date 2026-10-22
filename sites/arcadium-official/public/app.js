(() => {
  const benchmark = document.querySelector('.benchmark-chart');
  if (benchmark && 'IntersectionObserver' in window) {
    let visible = false;
    const updateAnimation = () => benchmark.classList.toggle('is-visible', visible && !document.hidden);
    new IntersectionObserver(entries => {
      visible = entries[0].isIntersecting;
      updateAnimation();
    }, {threshold: 0.15}).observe(benchmark);
    document.addEventListener('visibilitychange', updateAnimation);
  }

  function setupPicker(name, panelAttribute) {
    const choices = [...document.querySelectorAll(`input[name="${name}"]`)];
    if (!choices.length) return;
    const select = () => {
      const selected = choices.find(choice => choice.checked);
      if (!selected) return;
      document.querySelectorAll(`[${panelAttribute}]`).forEach(panel => {
        panel.hidden = panel.getAttribute(panelAttribute) !== selected.value;
      });
    };
    if (name === 'platform' && /Mac/i.test(navigator.platform)) {
      const macos = choices.find(choice => choice.value === 'macos');
      if (macos && !macos.disabled) macos.checked = true;
    }
    choices.forEach(choice => choice.addEventListener('change', select));
    select();
  }
  setupPicker('platform', 'data-platform-panel');
  setupPicker('architecture', 'data-architecture-panel');

  if (navigator.clipboard && window.isSecureContext) {
    for (const copyButton of document.querySelectorAll('[data-copy-checksum]')) {
      copyButton.hidden = false;
      copyButton.addEventListener('click', async () => {
        const content = copyButton.closest('.checksum-content');
        const status = content.querySelector('[data-copy-status]');
        try {
          await navigator.clipboard.writeText(content.querySelector('[data-release-sha256]').textContent.trim());
          status.textContent = 'Copied.';
        } catch {
          status.textContent = 'Select the checksum to copy it.';
        }
      });
    }
  }

  const onlineCount = document.querySelector('[data-online-count]');
  if (onlineCount) {
    const onlineStatus = onlineCount.closest('[data-online-status]');
    let pending = false;
    let poll;
    const refreshOnline = async () => {
      if (document.hidden || pending) return;
      clearTimeout(poll);
      pending = true;
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 8000);
      try {
        const result = await fetch('https://presence-test.arcadiumclient.com/v1/usage/count', {
          credentials: 'omit', referrerPolicy: 'no-referrer', signal: controller.signal
        });
        if (!result.ok) throw new Error('Online count unavailable');
        const count = await result.json();
        const responseTime = Date.parse(result.headers.get('Date') || '');
        const now = Number.isFinite(responseTime) ? responseTime : Date.now();
        if (!Number.isSafeInteger(count.online) || count.online < 0 || count.online > 16000
          || !Number.isSafeInteger(count.updatedAt) || now - count.updatedAt > 120000
          || count.updatedAt > now + 30000) throw new Error('Invalid online count');
        const formatted = new Intl.NumberFormat('en').format(count.online);
        if (onlineCount.textContent !== `${formatted} online now`) {
          const number = document.createElement('strong');
          number.textContent = formatted;
          onlineCount.replaceChildren(number, document.createTextNode(' online now'));
        }
        if (onlineStatus) onlineStatus.dataset.onlineStatus = 'available';
      } catch {
        if (onlineCount.textContent !== 'Online count unavailable') onlineCount.textContent = 'Online count unavailable';
        if (onlineStatus) onlineStatus.dataset.onlineStatus = 'unavailable';
      } finally {
        clearTimeout(timeout);
        pending = false;
        poll = setTimeout(refreshOnline, 30000);
      }
    };
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refreshOnline(); });
    window.addEventListener('online', refreshOnline);
    refreshOnline();
  }

  if (!document.querySelector('[data-release-version]')) return;
  fetch('/release.json', {cache: 'no-cache'})
    .then(response => {
      if (!response.ok) throw new Error('Release metadata unavailable');
      return response.json();
    })
    .then(release => {
      if (!/^\d+\.\d+\.\d+$/.test(release.version)) return;
      const values = {
        'version': release.version,
        'label': `${release.version}${release.prerelease ? ' Beta' : ''}`,
        'channel': release.prerelease ? 'Beta' : 'Stable'
      };
      for (const [key, value] of Object.entries(values)) {
        document.querySelectorAll(`[data-release-${key}]`).forEach(element => { element.textContent = value; });
      }
      const platforms = release.platforms || {
        'windows-x64': {...release, file: `ArcadiumClientSetup-${release.version}-x64.exe`}
      };
      const names = {
        'windows-x64': `ArcadiumClientSetup-${release.version}-x64.exe`,
        'macos-arm64': `ArcadiumClient-${release.version}-macos-arm64.dmg`,
        'macos-x64': `ArcadiumClient-${release.version}-macos-x64.dmg`
      };
      for (const [platform, file] of Object.entries(names)) {
        const download = platforms[platform];
        if (!download || download.file !== file) continue;
        const values = {'file': file};
        if (Number.isSafeInteger(download.bytes) && download.bytes > 0) values.size = `${(download.bytes / 1000000).toFixed(1)} MB`;
        if (/^[a-f0-9]{64}$/.test(download.sha256)) values.sha256 = download.sha256;
        for (const [key, value] of Object.entries(values)) {
          document.querySelectorAll(`[data-release-${key}="${platform}"]`).forEach(element => { element.textContent = value; });
        }
      }
      document.querySelectorAll('[data-release-notes]').forEach(link => {
        link.href = `https://github.com/0aurorq3/arcadium-public/releases/tag/v${release.version}`;
      });
    })
    .catch(() => {});
})();
