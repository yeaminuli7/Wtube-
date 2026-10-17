(function () {
  if (window.__MT) { return; }
  window.__MT = 1;

  /* লোগো-লুকানো-CSS সবার আগে (হেডার জন্মানোর আগেই) — ঝলক বন্ধ */
  function ensureHideCss() {
    if (document.getElementById('__mtbrandcss')) { return; }
    try {
      var st = document.createElement('style');
      st.id = '__mtbrandcss';
      st.textContent = 'ytm-home-logo,ytm-home-logo-renderer,ytm-logo,'
        + 'ytd-logo,.mobile-topbar-logo,c3-icon.mobile-topbar-logo,'
        + '.mobile-topbar-header-endpoint[key="logo"]{display:none!important}';
      (document.head || document.documentElement).appendChild(st);
    } catch (e) { }
  }
  ensureHideCss();

  /* ---- Trusted Types মোকাবিলা (ইউটিউবে innerHTML/DOMParser-ই নিষিদ্ধ) ---- */
  var __ttp = null;
  function ttHtml(el, html) {
    try {
      if (__ttp === null) {
        __ttp = (window.trustedTypes && trustedTypes.createPolicy)
          ? trustedTypes.createPolicy('wtx', { createHTML: function (x) { return x; } })
          : false;
      }
    } catch (e) { __ttp = false; }
    if (__ttp) {
      try { el.innerHTML = __ttp.createHTML(html); return true; } catch (e) { }
    }
    return false;
  }
  function svgNode(markup) {
    try {
      var d = document.createElement('div');
      if (!ttHtml(d, markup)) { return null; }
      return d.firstElementChild;
    } catch (e) { return null; }
  }
  /* ব্র্যান্ড-লোগো: সম্পূর্ণ স্ট্রিং-পার্স-মুক্ত নির্মাণ (সর্বোচ্চ নির্ভরযোগ্য) */
  function makeBrandSvg() {
    var NS = 'http://www.w3.org/2000/svg';
    var svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', '0 0 512 512');
    svg.setAttribute('width', '26');
    svg.setAttribute('height', '26');
    svg.style.display = 'block';
    function add(tag, attrs) {
      var el = document.createElementNS(NS, tag);
      for (var k in attrs) { el.setAttribute(k, attrs[k]); }
      svg.appendChild(el);
      return el;
    }
    add('rect', { width: '512', height: '512', rx: '116', fill: '#0F1110' });
    add('path', { d: 'M86 170 L142 362 L198 214 L254 362 L310 170', fill: 'none',
      stroke: '#F2F2F2', 'stroke-width': '46', 'stroke-linecap': 'round',
      'stroke-linejoin': 'round' });
    add('rect', { x: '294', y: '294', width: '132', height: '132', rx: '38', fill: '#34D399' });
    add('path', { d: 'M342 330 L342 390 L392 360 Z', fill: '#0F1110' });
    return svg;
  }

  /* ---- Background playback helpers ---- */
  try {
    Object.defineProperty(document, 'hidden', { get: function () { return false; }, configurable: true });
    Object.defineProperty(document, 'visibilityState', { get: function () { return 'visible'; }, configurable: true });
    Object.defineProperty(document, 'webkitHidden', { get: function () { return false; }, configurable: true });
    Object.defineProperty(document, 'webkitVisibilityState', { get: function () { return 'visible'; }, configurable: true });
  } catch (e) { }
  ['visibilitychange', 'webkitvisibilitychange'].forEach(function (ev) {
    document.addEventListener(ev, function (e) { e.stopImmediatePropagation(); }, true);
  });

  /* ---- Cosmetic ad hiding ---- */
  try {
    var st = document.createElement('style');
    st.textContent =
        'ytm-promoted-video-renderer,ytm-promoted-sparkles-web-renderer,'
      + 'ytm-promoted-sparkles-shorts-renderer,ytm-ad-slot-renderer,'
      + 'ytm-companion-slot-renderer,ytm-banner-promo-renderer,'
      + 'ytm-mobile-app-promo-renderer,.mobile-app-promo,'
      + 'ytd-promoted-video-renderer,ytd-promoted-sparkles-web-renderer,'
      + 'ytd-promoted-sparkles-text-search-renderer,ytd-ad-slot-renderer,'
      + 'ytd-companion-slot-renderer,ytd-guide-signin-promo-renderer,'
      + 'ytd-banner-promo-renderer,#masthead-ad,#player-ads,#merch-shelf,'
      + '.ytp-ad-image-overlay,.ad-badge,[class*="ad-badge"]'
      + '{display:none!important}';
    document.documentElement.appendChild(st);
  } catch (e) { }

  /* ---- SPA navigation reporter + video click intercept (multi-hook) ---- */
  var lastVid = null;
  function vidFrom(href) {
    // শুধু সাধারণ ভিডিও (watch / youtu.be) — Shorts ওয়েবেই চলে (আগের মতো)
    var m = href.match(/[?&]v=([A-Za-z0-9_-]{11})/)
         || href.match(/youtu\.be\/([A-Za-z0-9_-]{11})/);
    return m ? m[1] : null;
  }
  function isShort(href) {
    return /\/shorts\//.test(href || '');
  }
  function tryOpen(id, short) {
    if (!id) { lastVid = null; return; }
    if (id === lastVid) { return; }
    lastVid = id;
    window.__mtpause = Date.now() + 5000;
    try {
      MTApp.openVideo(id, (document.title || '').replace(/\s*[-|]\s*YouTube\s*$/, ''), !!short);
    } catch (e) { }
  }
  var homeHref = null; // শেষ নন-ওয়াচ পেজ — ফাঁস হলে এখানে ফেরাই
  var lastClickOpen = 0;
  function report() {
    try { MTApp.nav(location.href); } catch (e) { }
    var href = location.href || '';
    var vid = vidFrom(href);
    if (!vid && href.indexOf('youtube.com') >= 0 && href.indexOf('/watch') < 0) {
      homeHref = href;
    }
    if (vid && homeHref && (Date.now() - lastClickOpen) > 1500) {
      // নেভিগেশন ফাঁস করেছে (ক্লিক-ইন্টারসেপ্ট ধরেনি) —
      // আমাদের প্লেয়ার খুলে ইউজারকে আগের পেজে ফিরিয়ে দিই
      tryOpen(vid, isShort(href));
      setTimeout(function () { try { history.back(); } catch (e) { } }, 350);
      return;
    }
    tryOpen(vid, isShort(href));
  }
  document.addEventListener('yt-navigate-finish', report);
  document.addEventListener('popstate', report);
  // হুক ১: পেজ-লোড হলেই যাচাই (ফুল-পেজ নেভিগেশনে ইভেন্ট আসতে পারে না)
  report();
  // হুক ২: পোলিং-ওয়াচার — যেকোনো নেভিগেশন-মেকানিজমই URL বদল লুকাতে পারে না
  setInterval(report, 900);

  /* ---- ভিডিও-লিংক ট্যাপ ক্যাপচার-ফেজে আটকানো: ওয়েব /watch-এ যাবেই না ---- */
  document.addEventListener('click', function (e) {
    try {
      var t = e.target;
      var a = t && t.closest ? t.closest('a[href]') : null;
      if (!a) { return; }
      var vid = vidFrom(a.href || '');
      if (!vid) { return; }
      e.preventDefault();
      e.stopImmediatePropagation();
      lastClickOpen = Date.now();
      tryOpen(vid, isShort(a.href || ''));
    } catch (err) { }
  }, true);

  /* Shorts-অডিও-কিক চালু/বন্ধ (Settings → Web → Shorts audio fix) */
  function mtKickOn() {
    try {
      return !(window.__MTFLAGS && window.__MTFLAGS.skick === false);
    } catch (e) {
      return true;
    }
  }

  /* লগইন-অ্যাকাউন্টের অ্যাভাটার+নাম ধরা (Profile-এ দেখায়) */
  var __accLast = '';
  function mtScanAccount() {
    try {
      var im = null;
      var hdr = document.querySelector('.mobile-topbar-header')
          || document.querySelector('header');
      if (hdr) {
        var c = hdr.querySelectorAll('img');
        for (var i = 0; i < c.length; i++) {
          var src = c[i].getAttribute('src') || '';
          if (/googleusercontent|ggpht/.test(src)) { im = c[i]; break; }
        }
      }
      if (!im) {
        var all = document.querySelectorAll(
            'button img[src*="ggpht"], img[src*="yt3.googleusercontent"]');
        if (all.length) { im = all[0]; }
      }
      if (!im) { return; }
      var src = im.getAttribute('src') || '';
      var nm = (im.getAttribute('alt') || '').trim();
      if (!nm && im.closest) {
        var b = im.closest('button');
        if (b) {
          nm = (b.getAttribute('aria-label') || '')
              .replace(/^Account:?\s*/i, '').trim();
        }
      }
      var sig = src + '|' + nm;
      if (sig === __accLast) { return; }
      __accLast = sig;
      if (window.MTApp && MTApp.account) { MTApp.account(nm, src); }
    } catch (e) { }
  }

  /* ---- Play/pause events -> app ---- */
  function bindVideos() {
    try {
      var vs = document.querySelectorAll('video');
      for (var i = 0; i < vs.length; i++) {
        var v = vs[i];
        if (window.__mtpause && Date.now() < window.__mtpause) {
          try { v.pause(); } catch (e) { }
        }
        if (!v.__mt) {
          v.__mt = 1;
          /* Shorts: স্ক্যানের আগেই চলছে-এমন ভিডিও (লিসেনার-জানালা মিস) → এখনই কিক */
          if (mtKickOn() && /\/shorts\//.test(location.pathname || '') && !v.paused && !v.__mtkick) {
            v.__mtkick = 1;
            try { v.pause(); v.play(); } catch (e) { }
          }
          v.addEventListener('play', function () {
            if (window.__mtpause && Date.now() < window.__mtpause) {
              try { this.pause(); } catch (e) { }
              return;
            }
            /* Shorts: WebView-এর প্রথম-প্লেতে অডিও-ট্র্যাক লাগে না —
               পজ-প্লে কিক নিজেই করে দিই (ইউজারের হাতে-যা-করা-কাজ, স্বয়ংক্রিয়) */
            if (mtKickOn() && /\/shorts\//.test(location.pathname || '') && !this.__mtkick) {
              this.__mtkick = 1;
              try { var sv = this; sv.pause(); sv.play(); } catch (e) { }
            }
            try { MTApp.play(); } catch (e) { }
          });
          v.addEventListener('pause', function () { try { MTApp.pause(); } catch (e) { } });
          v.addEventListener('ended', function () { try { MTApp.pause(); } catch (e) { } });
        }
      }
    } catch (e) { }
    setTimeout(bindVideos, 900);
  }
  bindVideos();

  /* ---- Position reporter (audio-mode handoff) ---- */
  setInterval(function () {
    try {
      var v = document.querySelector('video');
      if (v && !v.paused && !v.ended) {
        MTApp.pos(v.currentTime, isFinite(v.duration) ? v.duration : 0,
                  (document.title || '').substring(0, 120), location.href);
      }
    } catch (e) { }
  }, 1000);

  /* ---- Ad skipper + overlay closer ---- */
  setInterval(function () {
    try {
      var s = document.querySelector('.ytp-ad-skip-button-modern,.ytp-ad-skip-button,'
        + 'button[class*="skip-button"]');
      if (s) { s.click(); }
      var c = document.querySelector('.ytp-ad-overlay-close-button,'
        + '.ytp-ad-overlay-close-container button');
      if (c) { c.click(); }
    } catch (e) { }
  }, 400);

  /* ---- "Open in YouTube" promo removal (all languages, structural) ---- */
  var APPRX = /open in youtube|open in app|open the app|open app|open youtube|in the youtube app|use the youtube app|watch on youtube|get the app|download youtube|install the app|switch to the app|(?:\u0987\u0989\u099f\u09bf\u0989\u09ac|youtube)[^\n]{0,30}(?:\u0985\u09cd\u09af\u09be\u09aa|\u0996\u09c1\u09b2|\u0993\u09aa\u09c7\u09a8)|\u0985\u09cd\u09af\u09be\u09aa(?:\u09c7|\u09c7\u09b0)[^\n]{0,20}(?:\u0996\u09c1\u09b2|\u09a6\u09c7\u0996|\u09a8)|\u0993\u09aa\u09c7\u09a8/i;
  try {
    var st3 = document.createElement('style');
    st3.textContent = 'ytm-smartbanner-renderer,ytm-promo-banner-renderer,'
      + 'ytm-app-banner-renderer,ytm-banner-promo-renderer{display:none!important}';
    document.documentElement.appendChild(st3);
  } catch (e) { }
  function iCount(el) {
    try { return el.querySelectorAll('a,button,input,textarea,select').length; }
    catch (e) { return 999; }
  }
  /* surgical: এমন পূর্বপুরুষই লুকায় যার ভেতরে প্রোমো ছাড়া আর কোনো interactive নেই —
     সার্চ/বেল-বোতামসহ কন্টেইনার কখনো লুকাবে না */
  function hideBanner(n) {
    try {
      var el = n, best = n;
      var base = iCount(n);
      for (var h = 0; h < 6 && el && el !== document.body; h++) {
        el = el.parentElement;
        if (!el) { break; }
        if (iCount(el) === base) { best = el; } else { break; }
      }
      best.style.display = 'none';
    } catch (e) {
      try { n.style.display = 'none'; } catch (e2) { }
    }
  }
  /* ---- player top-right "Open App" CTA (positional killer) ---- */
  function killPlayerCta() {
    try {
      var pl = document.querySelector('.player-container')
             || document.querySelector('#player');
      if (!pl) { return; }
      var pr = pl.getBoundingClientRect();
      var els = pl.querySelectorAll('button, a, ytm-*, div[class], span[class]');
      for (var i = 0; i < els.length; i++) {
        var el = els[i];
        if (el.__mtcta) { continue; }
        var er = el.getBoundingClientRect();
        if (er.width <= 0 || er.height <= 0 || er.width > 240 || er.height > 72) { continue; }
        if (er.right < pr.right - pr.width * 0.4 || er.top > pr.top + 110) { continue; }
        var t = (el.textContent || '').trim();
        var al = el.getAttribute('aria-label') || '';
        var s = (t + ' ' + al).toLowerCase();
        if (s.length > 0 && s.length < 80
            && (APPRX.test(t) || APPRX.test(al))) {
          el.__mtcta = 1;
          el.style.display = 'none';
        }
      }
    } catch (e) { }
  }

  /* ---- Notifications bell (topbar-এ, সার্চের পাশে) ---- */
  function addBell() {
    try {
      var bar = document.querySelector('ytm-mobile-topbar-renderer');
      if (!bar || document.getElementById('__mtbell')) { return; }
      var hdr = bar.querySelector('header.mobile-topbar-header') || bar;
      var a = document.createElement('a');
      a.id = '__mtbell';
      a.href = '/feed/notifications';
      a.setAttribute('aria-label', 'Notifications');
      a.style.cssText = 'display:-webkit-flex;display:flex;-webkit-align-items:center;'
        + 'align-items:center;margin:0 2px;padding:8px;-webkit-flex:0 0 auto;flex:0 0 auto;';
      var bs = svgNode('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="21" height="21" fill="#fff">'
        + '<path d="M12 22a2.3 2.3 0 0 0 2.3-2.3H9.7A2.3 2.3 0 0 0 12 22zm7-5.3v-1l-1.7-1.7v-4'
        + 'A5.5 5.5 0 0 0 13 4.6V3.5a1 1 0 1 0-2 0v1.1a5.5 5.5 0 0 0-4.3 5.4v4L5 15.7v1z"/></svg>');
      if (bs) { a.appendChild(bs); }
      /* ফোন-টাচ: YT-জেসচার-স্তর click জন্মাতে দেয় না — touchend/pointerup-এই নেভিগেট */
      var bellGo = function (ev) {
        try {
          if (Date.now() - (a.__mtgo || 0) < 600) { return; }
          a.__mtgo = Date.now();
          if (ev.cancelable) { ev.preventDefault(); }
          ev.stopPropagation();
          if (ev.stopImmediatePropagation) { ev.stopImmediatePropagation(); }
          location.assign('/feed/notifications');
        } catch (e2) { }
      };
      a.addEventListener('touchend', bellGo, false);
      a.addEventListener('pointerup', bellGo, false);
      a.addEventListener('click', bellGo, false);
      hdr.appendChild(a); /* হেডারের শেষ সন্তান = ডান-প্রান্ত */
    } catch (e) { }
  }

  /* ---- Popup (PiP) + Background buttons in action row ---- */
  var SVGP = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="20" height="20" fill="currentColor">'
    + '<path d="M19 11h-8v6h8v-6zm2-8H3a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h18a2 2 0 0 0 2-2V5'
    + 'a2 2 0 0 0-2-2zm0 16H3V6.98h18V19z"/></svg>';
  var SVGB = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="20" height="20" fill="currentColor">'
    + '<path d="M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z"/></svg>';
  function mtActBtn(id, svg, label) {
    var b = document.createElement('button');
    b.id = id;
    b.setAttribute('aria-label', label);
    b.style.cssText = 'display:-webkit-flex;display:flex;-webkit-flex-direction:column;'
      + 'flex-direction:column;-webkit-align-items:center;align-items:center;'
      + 'background:none;border:0;color:#fff;padding:4px 12px;font-size:11px;line-height:1.4';
    var sv = svgNode(svg);
    if (sv) { b.appendChild(sv); }
    var sp = document.createElement('span');
    sp.textContent = label;
    b.appendChild(sp);
    return b;
  }
  function addActions() {
    try {
      if (document.getElementById('__mtpip')) { return; }
      var host = document.querySelector('ytm-video-actions-renderer')
        || document.querySelector('ytm-watch-metadata-renderer')
        || document.querySelector('ytm-watch');
      if (!host) { return; }
      var row = document.createElement('div');
      row.style.cssText = 'display:-webkit-flex;display:flex;padding:2px 4px 8px;';
      row.appendChild(mtActBtn('__mtpip', SVGP, 'Popup'));
      row.appendChild(mtActBtn('__mtbg', SVGB, 'Background'));
      host.appendChild(row);
      document.getElementById('__mtpip').addEventListener('click', function () {
        try { MTApp.pip(); } catch (e) { }
      });
      document.getElementById('__mtbg').addEventListener('click', function () {
        try { MTApp.bgaudio(); } catch (e) { }
      });
    } catch (e) { }
  }

  function killAppPromo() {
    try {
      var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
      var n;
      while ((n = w.nextNode())) {
        var t = n.nodeValue;
        if (!t) { continue; }
        t = t.trim();
        if (t.length === 0 || t.length > 60) { continue; }
        if (!APPRX.test(t)) { continue; }
        var el = n.parentElement;
        if (!el || el.__mtp) { continue; }
        el.__mtp = 1;
        hideBanner(el);
      }
    } catch (e) { }
  }

  /* ---- লোগো রিব্র্যান্ড (Wtube: আইকন-B + নাম) ---- */

  function rebrand() {
    try {
      if (location.pathname.indexOf('/watch') === 0) { return; }
      var bar = document.querySelector('ytm-mobile-topbar-renderer')
        || document.querySelector('ytm-header');
      if (!bar) { return; }
      /* আসল হেডার-সারি (প্রমাণিত গঠন: ytm-mobile-topbar-renderer > header.mobile-topbar-header) */
      var hdr = bar.querySelector('header.mobile-topbar-header')
        || bar.querySelector('header') || bar;
      /* ১) নিজেদের ব্র্যান্ড হেডার-সারির একদম বাঁয়ে */
      if (!document.getElementById('__mtbrand')) {
        var a = document.createElement('a');
        a.id = '__mtbrand';
        a.href = '/';
        a.setAttribute('aria-label', 'Wtube');
        a.title = 'Wtube';
        a.appendChild(makeBrandSvg());
        var sp = document.createElement('span');
        sp.textContent = 'Wtube';
        sp.style.cssText = 'margin-left:7px;font-weight:600;font-size:17px;letter-spacing:.2px';
        a.appendChild(sp);
        a.style.cssText = 'display:-webkit-flex;display:flex;-webkit-align-items:center;'
          + 'align-items:center;text-decoration:none;color:inherit;margin:0 10px 0 4px;'
          + '-webkit-flex:0 0 auto;flex:0 0 auto;align-self:center;';
        hdr.insertBefore(a, hdr.firstChild);
      }
      ensureHideCss();
      /* ৩) রানটাইম-ফলব্যাক: হেডারের ভেতরে YouTube-লেবেল/লোগো-শ্রেণির যে-কোনো জিনিস */
      var els = hdr.querySelectorAll('[aria-label],[title],[class*="logo"],[key]');
      for (var i = 0; i < els.length; i++) {
        var el = els[i];
        if (!el || el.id === '__mtbrand' || el.id === '__mtbell' || el.id === '__mtset') { continue; }
        var aria = (el.getAttribute('aria-label') || '') + ' ' + (el.getAttribute('title') || '');
        var cls = ' ' + ((el.getAttribute('class') || '') + ' ' + (el.className || ''));
        var tag = (el.tagName || '').toLowerCase();
        var isLogo = /youtube\s+home|go\s+to\s+home|^youtube$/i.test(aria.trim())
          || /(^|\s)(mobile-topbar-logo|full-logo|home-logo|topbar-logo)(\s|$)/i.test(cls)
          || tag === 'ytm-home-logo' || tag === 'ytm-logo' || tag === 'ytd-logo';
        if (isLogo && el.style.display !== 'none') {
          el.style.display = 'none';
          el.setAttribute('hidden', '');
        }
      }
    } catch (e) { }
  }

  /* ---- তাৎক্ষণিক প্রোমো-কিলার: DOM-এ ঢোকামাত্রই লুকায় (৬০০ms-দেরি বাতিল) ---- */
  var __mtFlush = false;
  function flushK() {
    if (__mtFlush) { return; }
    __mtFlush = true;
    setTimeout(function () {
      __mtFlush = false;
      try { killAppPromo(); killPlayerCta(); rebrand(); } catch (e) { }
    }, 30);
  }
  try {
    var MO = window.MutationObserver || window.WebKitMutationObserver;
    if (MO) {
      new MO(function () { flushK(); })
        .observe(document.documentElement, { childList: true, subtree: true });
    }
  } catch (e) { }

  setInterval(function () { killAppPromo(); killPlayerCta(); addBell(); rebrand(); addActions(); mtScanAccount(); }, 600);

  /* ---- ইউটিউবের নিজের ট্যাব-বার লুকানো (আমাদের ন্যাভ-বার ব্যবহৃত) ---- */
  try {
    var st2 = document.createElement('style');
    st2.textContent = 'ytm-pivot-bar-renderer,ytd-mini-guide-renderer,'
      + '#appbar-guide-menu{display:none!important}'
      + 'body{padding-bottom:58px!important}';
    document.documentElement.appendChild(st2);
  } catch (e) { }
})();
