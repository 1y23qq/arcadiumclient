(() => {
  'use strict';

  const openLinkedDetails = () => {
    let id;
    try { id = decodeURIComponent(location.hash.slice(1)); } catch { return; }
    const target = id && document.getElementById(id);
    if (target?.tagName === 'DETAILS') target.open = true;
  };
  openLinkedDetails();
  window.addEventListener('hashchange', openLinkedDetails);

  const menuButton = document.querySelector('[data-menu-toggle]');
  const mobileMenu = document.getElementById('mobile-navigation');
  if (menuButton && mobileMenu) {
    const closeMenu = (restoreFocus = false) => {
      mobileMenu.hidden = true;
      menuButton.setAttribute('aria-expanded', 'false');
      menuButton.setAttribute('aria-label', 'Open navigation');
      menuButton.querySelector('use').setAttribute('href', '/assets/interface-4c33e1210fdf.svg#menu');
      if (restoreFocus) menuButton.focus();
    };
    menuButton.hidden = false;
    menuButton.addEventListener('click', () => {
      const open = menuButton.getAttribute('aria-expanded') !== 'true';
      mobileMenu.hidden = !open;
      menuButton.setAttribute('aria-expanded', String(open));
      menuButton.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
      menuButton.querySelector('use').setAttribute('href', `/assets/interface-4c33e1210fdf.svg#${open ? 'close' : 'menu'}`);
    });
    mobileMenu.addEventListener('click', event => {
      if (event.target.closest('a')) closeMenu();
    });
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape' && !mobileMenu.hidden) closeMenu(true);
    });
    document.addEventListener('click', event => {
      if (!mobileMenu.hidden && !event.target.closest('.site-header')) closeMenu();
    });
    const desktop = matchMedia('(min-width: 1001px)');
    desktop.addEventListener('change', () => { if (desktop.matches) closeMenu(); });
  }

  const showcase = document.querySelector('[data-showcase]');
  if (showcase) {
    const tabs = [...showcase.querySelectorAll('[role="tab"]')];
    const panel = showcase.querySelector('[role="tabpanel"]');
    const currentImage = showcase.querySelector('[data-showcase-image]');
    const expand = showcase.querySelector('[data-expand-image]');
    let requestId = 0;
    const selectTab = (tab, moveFocus = false) => {
      const id = ++requestId;
      tabs.forEach(item => {
        const selected = item === tab;
        item.setAttribute('aria-selected', String(selected));
        item.tabIndex = selected ? 0 : -1;
      });
      panel.setAttribute('aria-labelledby', tab.id);
      if (moveFocus) tab.focus();
      panel.setAttribute('aria-busy', 'true');
      const nextImage = new Image();
      nextImage.onload = () => {
        if (id !== requestId) return;
        currentImage.src = tab.dataset.showcaseSrc;
        currentImage.alt = tab.dataset.showcaseAlt;
        currentImage.width = Number(tab.dataset.showcaseWidth);
        currentImage.height = Number(tab.dataset.showcaseHeight);
        expand.href = tab.dataset.showcaseSrc;
        showcase.querySelector('[data-showcase-caption]').textContent = tab.textContent.trim();
        const context = showcase.querySelector('[data-showcase-context]');
        if (context) context.textContent = tab.id === 'shot-launcher' ? 'Arcadium Launcher' : 'Minecraft 1.8.9';
        panel.setAttribute('aria-busy', 'false');
      };
      nextImage.onerror = () => {
        if (id !== requestId) return;
        panel.setAttribute('aria-busy', 'false');
        const previousTab = tabs.find(item => item.dataset.showcaseSrc === currentImage.getAttribute('src'));
        if (previousTab) {
          tabs.forEach(item => {
            item.setAttribute('aria-selected', String(item === previousTab));
            item.tabIndex = item === previousTab ? 0 : -1;
          });
          panel.setAttribute('aria-labelledby', previousTab.id);
        }
      };
      nextImage.src = tab.dataset.showcaseSrc;
    };
    tabs.forEach((tab, index) => {
      tab.addEventListener('click', () => selectTab(tab));
      tab.addEventListener('keydown', event => {
        let next;
        if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
        if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
        if (event.key === 'Home') next = 0;
        if (event.key === 'End') next = tabs.length - 1;
        if (next === undefined) return;
        event.preventDefault();
        selectTab(tabs[next], true);
      });
    });
    showcase.querySelector('[data-showcase-controls]').hidden = false;
    showcase.classList.add('is-enhanced');
  }

  const imageDialog = document.querySelector('[data-image-dialog]');
  if (imageDialog && typeof imageDialog.showModal === 'function') {
    let opener;
    document.querySelectorAll('[data-expand-image]').forEach(link => {
      link.addEventListener('click', event => {
        if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        const image = link.querySelector('img') || link.closest('[data-showcase]')?.querySelector('[data-showcase-image]');
        const source = image?.currentSrc || link.href;
        const caption = link.closest('[data-showcase]')?.querySelector('[aria-selected="true"]')?.textContent.trim() || image?.alt || 'Screenshot';
        const target = imageDialog.querySelector('[data-dialog-image]');
        target.src = source;
        target.alt = image?.alt || caption;
        imageDialog.querySelector('[data-dialog-caption]').textContent = caption;
        const original = imageDialog.querySelector('[data-dialog-original]');
        if (original) original.href = source;
        try {
          imageDialog.showModal();
          event.preventDefault();
          opener = link;
          document.body.classList.add('dialog-open');
        } catch {}
      });
    });
    imageDialog.querySelector('[data-dialog-close]').addEventListener('click', () => imageDialog.close());
    imageDialog.addEventListener('click', event => {
      if (event.target === imageDialog) {
        const bounds = imageDialog.getBoundingClientRect();
        if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) imageDialog.close();
      }
    });
    imageDialog.addEventListener('close', () => {
      document.body.classList.remove('dialog-open');
      if (opener?.isConnected) opener.focus({preventScroll: true});
    });
  }

  // Reveal once and animate the gradient only while visible.
  const benchmark = document.querySelector('.benchmark-chart');
  if (benchmark && 'IntersectionObserver' in window && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
    let visible = false;
    let revealed = false;
    const updateAnimation = () => benchmark.classList.toggle('is-visible', visible && !document.hidden);
    benchmark.classList.add('is-pending');
    const observer = new IntersectionObserver(entries => {
      visible = entries[0].isIntersecting;
      if (visible && !revealed) {
        revealed = true;
        requestAnimationFrame(() => benchmark.classList.remove('is-pending'));
      }
      updateAnimation();
    }, {threshold: 0.15});
    observer.observe(benchmark);
    document.addEventListener('visibilitychange', updateAnimation);
  }

  const platformChoices = [...document.querySelectorAll('input[name="platform"]')];
  if (platformChoices.length) {
    const selectPlatform = () => {
      const value = platformChoices.find(choice => choice.checked)?.value;
      document.querySelectorAll('[data-platform-panel]').forEach(panel => { panel.hidden = panel.dataset.platformPanel !== value; });
    };
    const platform = navigator.userAgentData?.platform || navigator.platform || '';
    if (/Mac/i.test(platform)) {
      const mac = platformChoices.find(choice => choice.value === 'macos');
      if (mac) mac.checked = true;
    }
    platformChoices.forEach(choice => choice.addEventListener('change', selectPlatform));
    selectPlatform();
    document.querySelector('.platform-picker').hidden = false;
  }

  if (navigator.clipboard && window.isSecureContext) {
    document.querySelectorAll('[data-copy-checksum]').forEach(button => {
      button.hidden = false;
      button.addEventListener('click', async () => {
        const content = button.closest('.checksum-content');
        const status = content.querySelector('[data-copy-status]');
        try {
          await navigator.clipboard.writeText(content.querySelector('[data-release-sha256]').textContent.trim());
          status.textContent = 'Copied.';
        } catch {
          status.textContent = 'Select the checksum to copy it.';
        }
      });
    });
  }

  const onlineCounters = [...document.querySelectorAll('[data-online-count]')];
  if (onlineCounters.length) {
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
        if (!Number.isSafeInteger(count.online) || count.online < 0 || count.online > 16000 || !Number.isSafeInteger(count.updatedAt)
          || now - count.updatedAt > 120000 || count.updatedAt > now + 30000) throw new Error('Invalid online count');
        const label = `${new Intl.NumberFormat('en').format(count.online)} online`;
        onlineCounters.forEach(counter => {
          if (counter.textContent !== label) counter.textContent = label;
          counter.closest('[data-online-status]').dataset.onlineStatus = 'available';
        });
      } catch {
        onlineCounters.forEach(counter => {
          counter.textContent = 'Unavailable';
          counter.closest('[data-online-status]').dataset.onlineStatus = 'unavailable';
        });
      } finally {
        clearTimeout(timeout);
        pending = false;
        if (!document.hidden) poll = setTimeout(refreshOnline, 30000);
      }
    };
    document.addEventListener('visibilitychange', () => {
      clearTimeout(poll);
      if (!document.hidden) refreshOnline();
    });
    window.addEventListener('online', refreshOnline);
    refreshOnline();
  }

  if (!document.querySelector('[data-release-version], [data-release-notes]')) return;
  fetch('/release.json', {cache: 'no-cache'})
    .then(response => {
      if (!response.ok) throw new Error('Release metadata unavailable');
      return response.json();
    })
    .then(release => {
      if (!/^\d+\.\d+\.\d+$/.test(release.version)) return;
      const globalValues = {version: release.version, label: `${release.version}${release.prerelease ? ' Beta' : ''}`, channel: release.prerelease ? 'Beta' : 'Stable'};
      Object.entries(globalValues).forEach(([key, value]) => {
        document.querySelectorAll(`[data-release-${key}=""]`).forEach(element => { element.textContent = value; });
      });
      const platforms = release.platforms || {};
      ['windows-x64', 'macos-arm64', 'macos-x64'].forEach(platform => {
        const download = platforms[platform];
        const version = download?.version || release.version;
        if (!/^\d+\.\d+\.\d+$/.test(version)) return;
        const file = platform === 'windows-x64' ? `ArcadiumClientSetup-${version}-x64.exe` : `ArcadiumClient-${version}-${platform}.dmg`;
        if (!download || download.file !== file) return;
        const prerelease = download.prerelease ?? release.prerelease;
        const values = {file, version, label: `${version}${prerelease ? ' Beta' : ''}`, channel: prerelease ? 'Beta' : 'Stable'};
        if (Number.isSafeInteger(download.bytes) && download.bytes > 0) values.size = `${(download.bytes / 1000000).toFixed(1)} MB`;
        if (/^[a-f0-9]{64}$/.test(download.sha256)) values.sha256 = download.sha256;
        Object.entries(values).forEach(([key, value]) => {
          document.querySelectorAll(`[data-release-${key}="${platform}"]`).forEach(element => { element.textContent = value; });
        });
      });
      document.querySelectorAll('[data-release-notes]').forEach(link => {
        const version = platforms[link.dataset.releaseNotes]?.version || release.version;
        if (/^\d+\.\d+\.\d+$/.test(version)) link.href = `https://github.com/1y23qq/arcadium-public/releases/tag/v${version}`;
      });
    })
    .catch(() => {});
})();
