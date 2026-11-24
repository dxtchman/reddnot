/*
 * Reddnot page script. Runs at the start of every reddit.com page.
 * - Theme colors, rainbow threads (CSS from reddnot.css, passed in as window.__RN_CSS)
 * - Apollo-style swipe actions on posts and comments (set in the app's Me tab)
 * - Marks bookmarked posts, removes hidden ones
 * - Drives Reddit's For You / Following tabs from the app's own tab row
 * - Removes Google and Apple sign-in buttons
 *
 * Talks to the app through window.reddnotBridge.postMessage(json).
 * The app talks back through window.reddnot.setState({...}) and window.reddnot.feedTab(name).
 */
(function () {
  'use strict';
  if (window.__reddnotLoaded) return;
  window.__reddnotLoaded = true;

  // ---------- swipe actions ----------
  var ICONS = {
    up: '<svg viewBox="0 0 24 24"><path d="M12 4l-8 8h5v8h6v-8h5z"/></svg>',
    down: '<svg viewBox="0 0 24 24"><path d="M12 20l8-8h-5V4H9v8H4z"/></svg>',
    save: '<svg viewBox="0 0 24 24"><path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg>',
    hide: '<svg viewBox="0 0 24 24"><path d="M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92A11.8 11.8 0 0 0 23 12c-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46A11.8 11.8 0 0 0 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55A2.8 2.8 0 0 0 9 12a3 3 0 0 0 3 3c.22 0 .44-.03.65-.08l1.55 1.55A5 5 0 0 1 7 12c0-.79.2-1.53.53-2.2z"/></svg>',
    collapse: '<svg viewBox="0 0 24 24"><path d="M7.4 15.4L12 10.8l4.6 4.6L18 14l-6-6-6 6z"/></svg>',
    reply: '<svg viewBox="0 0 24 24"><path d="M10 9V5l-7 7 7 7v-4.1c5 0 8.5 1.6 11 5.1-1-5-4-10-11-11z"/></svg>',
    share: '<svg viewBox="0 0 24 24"><path d="M18 16.1c-.8 0-1.4.3-2 .8l-7.1-4.2c.1-.2.1-.5.1-.7s0-.5-.1-.7L16 7.2c.5.5 1.2.8 2 .8 1.7 0 3-1.3 3-3s-1.3-3-3-3-3 1.3-3 3c0 .2 0 .5.1.7L8 9.8C7.5 9.3 6.8 9 6 9c-1.7 0-3 1.3-3 3s1.3 3 3 3c.8 0 1.5-.3 2-.8l7.1 4.2c-.1.2-.1.4-.1.6 0 1.6 1.3 2.9 2.9 2.9s2.9-1.3 2.9-2.9-1.2-2.9-2.8-2.9z"/></svg>'
  };
  var A = {
    up: { id: 'up', label: 'Upvote', color: '#ff6a1a' },
    down: { id: 'down', label: 'Downvote', color: '#6a8cff' },
    save: { id: 'save', label: 'Bookmark', color: '#34c759' },
    hide: { id: 'hide', label: 'Hide', color: '#8e8e93' },
    collapse: { id: 'collapse', label: 'Collapse', color: '#5e5ce6' },
    reply: { id: 'reply', label: 'Reply', color: '#0a84ff' },
    share: { id: 'share', label: 'Share', color: '#bf5af2' }
  };
  // Defaults; the app sends the user's choices in settings.swipes.
  var DEFAULT_SWIPES = {
    post: { right: ['up', 'down'], left: ['save', 'hide'] },
    comment: { right: ['up', 'down'], left: ['collapse', 'save'] }
  };
  var SHORT = 0.2;  // fraction of screen width for a short swipe
  var LONG = 0.48;  // fraction of screen width for a long swipe
  var EDGE = 28;    // px at the screen edges left alone for Android's back gesture

  // ---------- state from the app ----------
  var state = {
    saved: {},
    hidden: {},
    settings: { noHeader: true, noAds: true, rainbow: true, theme: null, threads: [], swipes: DEFAULT_SWIPES }
  };

  function send(msg) {
    try {
      if (window.reddnotBridge) window.reddnotBridge.postMessage(JSON.stringify(msg));
    } catch (e) { /* not in the app */ }
  }

  function toSet(list) {
    var s = {};
    (list || []).forEach(function (id) { s[id] = true; });
    return s;
  }

  function applySettings() {
    var html = document.documentElement;
    if (!html) return;
    var st = state.settings;
    html.classList.toggle('rn-noheader', !!st.noHeader);
    html.classList.toggle('rn-noads', !!st.noAds);
    html.classList.toggle('rn-rainbow', !!st.rainbow);
    html.classList.toggle('rn-theme', !!st.theme);
    if (st.theme) {
      html.style.setProperty('--rn-bg', st.theme.bg);
      html.style.setProperty('--rn-bg2', st.theme.bg2);
      html.style.setProperty('--rn-bg3', st.theme.bg3);
    }
    (st.threads || []).forEach(function (c, i) { html.style.setProperty('--rn-t' + i, c); });
  }

  // ---------- CSS ----------
  function addStyle() {
    if (document.getElementById('rn-style') || !document.documentElement) return;
    var s = document.createElement('style');
    s.id = 'rn-style';
    s.textContent = window.__RN_CSS || '';
    (document.head || document.documentElement).appendChild(s);
  }

  // Reddit sets its colors again inside posts' and comments' shadow DOM, so the theme
  // variables are repeated there (only while a theme is on).
  var THEME_VARS = {
    '--color-neutral-background': 'var(--rn-bg)',
    '--color-neutral-background-weak': 'var(--rn-bg2)',
    '--color-neutral-background-container': 'var(--rn-bg)',
    '--color-neutral-background-container-strong': 'var(--rn-bg2)',
    '--shreddit-content-background': 'var(--rn-bg)',
    '--color-background': 'var(--rn-bg)'
  };
  var SHADOW_CSS = ':host-context(html.rn-theme),:host-context(html.rn-theme) *{' +
    Object.keys(THEME_VARS).map(function (k) { return k + ':' + THEME_VARS[k] + '!important'; }).join(';') + '}';

  function styleShadow(el) {
    var root = el.shadowRoot;
    if (!root || root.__rnStyled) return;
    root.__rnStyled = true;
    var s = document.createElement('style');
    s.textContent = SHADOW_CSS;
    root.appendChild(s);
  }

  // ---------- reading posts and comments ----------
  function attr(el, name) { return (el && el.getAttribute && el.getAttribute(name)) || ''; }
  function text(el) { return ((el && el.textContent) || '').replace(/\s+/g, ' ').trim(); }

  function absUrl(path) {
    if (!path) return location.href;
    try { return new URL(path, 'https://www.reddit.com').href; } catch (e) { return path; }
  }

  function postId(el) { return attr(el, 'id') || attr(el, 'thingid') || attr(el, 'permalink'); }
  function commentId(el) { return attr(el, 'thingid') || attr(el, 'id') || attr(el, 'permalink'); }

  function thumbOf(el) {
    var img = el.querySelector('img[src*="redd.it"], img[src*="redditmedia"], img[src*="external-preview"]');
    return img ? img.src : '';
  }

  function postData(el) {
    return {
      id: postId(el),
      kind: 'post',
      title: attr(el, 'post-title') || text(el.querySelector('[slot="title"]')) || 'Post',
      subreddit: attr(el, 'subreddit-prefixed-name'),
      author: attr(el, 'author'),
      url: absUrl(attr(el, 'permalink')),
      thumb: thumbOf(el),
      snippet: ''
    };
  }

  function pagePost() { return document.querySelector('shreddit-post'); }

  function commentData(el) {
    var body = el.querySelector(':scope > [slot="comment"]') || el.querySelector('[slot="comment"]');
    var t = body ? body.innerText.trim().replace(/\s+/g, ' ') : '';
    var post = pagePost();
    return {
      id: commentId(el),
      kind: 'comment',
      title: post ? attr(post, 'post-title') : document.title,
      subreddit: post ? attr(post, 'subreddit-prefixed-name') : '',
      author: attr(el, 'author'),
      url: absUrl(attr(el, 'permalink')),
      thumb: '',
      snippet: t.slice(0, 400)
    };
  }

  function idOf(el) { return el.tagName === 'SHREDDIT-POST' ? postId(el) : commentId(el); }

  // Walks an element and its shadow roots, depth first. skip(n) stops it going into n.
  function deepFind(root, test, skip) {
    var stack = [root];
    var seen = 0;
    while (stack.length) {
      var n = stack.pop();
      if (++seen > 20000) return null;
      if (n !== root && skip && skip(n)) continue;
      if (n.nodeType === 1 && test(n)) return n;
      if (n.shadowRoot) stack.push(n.shadowRoot);
      var kids = n.children || [];
      for (var i = kids.length - 1; i >= 0; i--) stack.push(kids[i]);
    }
    return null;
  }

  function deepAll(root, test, skip) {
    var out = [];
    deepFind(root, function (n) { if (test(n)) out.push(n); return false; }, skip);
    return out;
  }

  function notNestedComment(n) { return n.tagName === 'SHREDDIT-COMMENT'; }

  function actionScope(el) {
    return el.tagName === 'SHREDDIT-COMMENT'
      ? (el.querySelector(':scope > shreddit-comment-action-row, :scope > [slot="actionRow"]') || el)
      : el;
  }

  function voteButton(el, dir) {
    var re = dir === 'up' ? /upvote/i : /downvote/i;
    return deepFind(actionScope(el), function (n) {
      return n.tagName === 'BUTTON' &&
        (n.hasAttribute(dir === 'up' ? 'upvote' : 'downvote') || re.test(attr(n, 'aria-label')));
    }, notNestedComment);
  }

  function replyButton(el) {
    return deepFind(actionScope(el), function (n) {
      return (n.tagName === 'BUTTON' || n.tagName === 'A') &&
        (/^reply$/i.test(text(n)) || /^reply/i.test(attr(n, 'aria-label')));
    }, notNestedComment);
  }

  function toggleCollapse(el) {
    var btn = deepFind(el, function (n) {
      return n.tagName === 'BUTTON' && /(collapse|expand|toggle)/i.test(attr(n, 'aria-label'));
    }, notNestedComment);
    if (btn) {
      btn.click();
    } else {
      var details = el.shadowRoot && el.shadowRoot.querySelector('details');
      if (details) details.open = !details.open;
      else el.toggleAttribute('collapsed');
    }
    var top = el.getBoundingClientRect().top;
    if (top < 0) window.scrollBy(0, top - 8);
  }

  // ---------- rainbow threads ----------
  // Reddit draws thread lines with thin borders inside each comment. Rather than guess its
  // class names, find anything shaped like a line and recolor it by depth.
  var lineVer = 0;

  function threadColor(depth) {
    var t = state.settings.threads || [];
    if (!state.settings.rainbow || !t.length) return null;
    return t[depth % t.length];
  }

  var SKIP_SLOTS = /^(comment|commentMeta|commentAvatar|actionRow|credit-bar)$/;

  function lineCandidates(c) {
    var out = [];
    if (c.shadowRoot) out = out.concat(Array.prototype.slice.call(c.shadowRoot.querySelectorAll('*')));
    var kids = c.children;
    for (var i = 0; i < kids.length; i++) {
      var k = kids[i];
      if (k.tagName === 'SHREDDIT-COMMENT' || k.tagName === 'SHREDDIT-COMMENT-ACTION-ROW' ||
          SKIP_SLOTS.test(attr(k, 'slot')) || k.querySelector('shreddit-comment')) continue;
      out.push(k);
      var sub = k.querySelectorAll('*');
      if (sub.length < 80) out = out.concat(Array.prototype.slice.call(sub));
      if (k.shadowRoot) out = out.concat(Array.prototype.slice.call(k.shadowRoot.querySelectorAll('*')));
    }
    return out;
  }

  function px(v) { return parseFloat(v) || 0; }

  function lineKind(el, cs) {
    var tag = el.tagName;
    if (tag === 'STYLE' || tag === 'SLOT' || tag === 'svg' || tag === 'SVG' || tag === 'path') return null;
    var bl = px(cs.borderLeftWidth), bb = px(cs.borderBottomWidth), bt = px(cs.borderTopWidth), br = px(cs.borderRightWidth);
    if (bt === 0 && br === 0 && bl > 0 && bl <= 3 && cs.borderLeftStyle !== 'none') return 'border';
    if (bt === 0 && br === 0 && bb > 0 && bb <= 3 && cs.borderBottomStyle !== 'none' && bl > 0) return 'border';
    var w = el.offsetWidth, h = el.offsetHeight;
    var bg = cs.backgroundColor;
    if (w > 0 && w <= 3 && h >= 6 && bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)') return 'bar';
    return null;
  }

  function paintLines(c) {
    var now = Date.now();
    var info = c.__rnLines;
    if (info && info.ver === lineVer) {
      if (info.n > 0 || now - info.first > 5000 || now - info.last < 500) return;
    }
    (c.__rnPainted || []).forEach(function (p) { p.el.style.removeProperty(p.prop); });
    var painted = [];
    var n = 0;
    var color = threadColor(parseInt(attr(c, 'depth'), 10) || 0);
    var width = state.settings.threadWidth || 0;
    function set(el, prop, val) { el.style.setProperty(prop, val, 'important'); painted.push({ el: el, prop: prop }); }
    if (color) {
      lineCandidates(c).forEach(function (el) {
        var cs = getComputedStyle(el);
        var kind = lineKind(el, cs);
        if (!kind) return;
        n++;
        if (kind === 'border') {
          set(el, 'border-color', color);
          if (width) {
            if (px(cs.borderLeftWidth) > 0) set(el, 'border-left-width', width + 'px');
            if (px(cs.borderBottomWidth) > 0) set(el, 'border-bottom-width', width + 'px');
          }
        } else {
          set(el, 'background-color', color);
          if (width) set(el, 'width', width + 'px');
        }
      });
    }
    c.__rnPainted = painted;
    c.__rnLines = {
      ver: lineVer, n: color ? n : 1,
      first: info && info.ver === lineVer ? info.first : now, last: now
    };
  }

  // ---------- "Get the Reddit app" banners ----------
  function skipThings(n) { return n.tagName === 'SHREDDIT-POST' || n.tagName === 'SHREDDIT-COMMENT'; }

  function hideEl(n) {
    if (n && n.style && n.style.display !== 'none') n.style.setProperty('display', 'none', 'important');
  }

  function removeAppBanners() {
    deepAll(document.body || document.documentElement, function (n) {
      return /^XPROMO-/.test(n.tagName) && !/NSFW/.test(n.tagName);
    }, skipThings).forEach(hideEl);

    var hit = deepFind(document.body || document.documentElement, function (n) {
      if (n.childElementCount > 4) return false;
      var t = text(n);
      return t.length < 140 && /get the reddit app|open in (the )?app|use the app/i.test(t);
    }, skipThings);
    if (!hit) return;
    // Hide the floating box it lives in.
    var el = hit;
    for (var i = 0; i < 14 && el; i++) {
      var pos = getComputedStyle(el).position;
      if (pos === 'fixed' || pos === 'sticky') { hideEl(el); return; }
      el = el.parentElement || (el.getRootNode && el.getRootNode().host) || null;
    }
  }

  // ---------- marks (bookmarked / hidden / thread styles) ----------
  function setFlag(el, name, on) {
    if (on && !el.hasAttribute(name)) el.setAttribute(name, '');
    else if (!on && el.hasAttribute(name)) el.removeAttribute(name);
  }

  function markAll() {
    var nodes = document.querySelectorAll('shreddit-post, shreddit-comment');
    var onPost = location.pathname.indexOf('/comments/') >= 0;
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var id = idOf(el);
      setFlag(el, 'data-rn-saved', !!state.saved[id]);
      if (el.tagName === 'SHREDDIT-POST') {
        var box = el.closest('article') || el;
        setFlag(box, 'data-rn-hidden', !!state.hidden[id] && !onPost);
        if (el.hasAttribute('promoted') || attr(el, 'post-type') === 'promoted') setFlag(box, 'data-rn-ad', true);
      } else {
        paintLines(el);
      }
      styleShadow(el);
    }
  }

  // ---------- Google / Apple sign-in ----------
  var SSO_TEXT = /^(continue|sign in|log in|sign up) with (google|apple)$/i;

  function authVisible() {
    return /\/(login|register|account)/.test(location.pathname) ||
      !!document.querySelector('auth-flow-modal, auth-flow-manager, shreddit-async-loader[bundlename*="auth"], faceplate-dialog[open]');
  }

  function removeSso() {
    if (!authVisible()) return;
    deepAll(document.documentElement, function (n) {
      var tag = n.tagName;
      if (tag === 'IFRAME') return /accounts\.google\.com|appleid\.apple\.com/.test(n.src || '');
      if (/(GOOGLE|APPLE)/.test(tag)) return true;
      if (tag === 'BUTTON' || tag === 'A' || attr(n, 'role') === 'button') return SSO_TEXT.test(text(n));
      return false;
    }, function (n) { return n.tagName === 'SHREDDIT-POST' || n.tagName === 'SHREDDIT-COMMENT'; })
      .forEach(function (n) {
        if (n.style.display !== 'none') n.style.setProperty('display', 'none', 'important');
      });
  }

  // ---------- For You / Following ----------
  var TAB_NAMES = { 'for you': 'foryou', 'following': 'following' };
  var lastTab = null;
  var pendingTab = null;
  var pendingUntil = 0;

  function isHomeFeed() {
    return /^\/((best|hot|new|top|rising)\/?)?$/.test(location.pathname);
  }

  function findTabs() {
    return deepAll(document.body || document.documentElement, function (n) {
      var tag = n.tagName;
      if (!(attr(n, 'role') === 'tab' || tag === 'BUTTON' || tag === 'A' || tag === 'LI' || /TAB/.test(tag))) return false;
      return !!TAB_NAMES[text(n).toLowerCase()];
    }, function (n) {
      return n.tagName === 'SHREDDIT-POST' || n.tagName === 'SHREDDIT-COMMENT';
    });
  }

  function isSelected(n) {
    return attr(n, 'aria-selected') === 'true' || attr(n, 'aria-current') === 'page' ||
      attr(n, 'aria-current') === 'true' || n.hasAttribute('selected') || n.hasAttribute('active') ||
      /(^|\s)(selected|active|is-active)(\s|$)/.test(n.className || '');
  }

  function syncTabs() {
    if (!isHomeFeed()) return;
    var tabs = findTabs();
    if (!tabs.length) return;

    // Hide Reddit's own row; the app shows its own above the feed.
    if (state.settings.noHeader) {
      var row = tabs[0].closest('[role="tablist"]');
      if (!row && tabs.length > 1) {
        row = tabs[0].parentElement;
        while (row && !row.contains(tabs[1])) row = row.parentElement;
      }
      // Only hide something that is clearly just the tab row, never part of the feed.
      if (row && row !== document.body && row !== document.documentElement &&
          !row.querySelector('shreddit-post, shreddit-feed, article') && text(row).length < 80) {
        setFlag(row, 'data-rn-hidden', true);
      }
    }

    if (pendingTab) {
      var target = tabs.filter(function (n) { return TAB_NAMES[text(n).toLowerCase()] === pendingTab; })[0];
      if (target) {
        target.click();
        lastTab = pendingTab;
        send({ type: 'feedTab', tab: pendingTab });
        pendingTab = null;
        return;
      }
    }

    var sel = tabs.filter(isSelected)[0];
    var now = sel ? TAB_NAMES[text(sel).toLowerCase()] : null;
    if (now && now !== lastTab) {
      lastTab = now;
      send({ type: 'feedTab', tab: now });
    }
  }

  // ---------- batching work after page changes ----------
  var queued = false;
  var lastSlow = 0;
  function queueMark() {
    if (queued) return;
    queued = true;
    requestAnimationFrame(function () {
      queued = false;
      markAll();
      var t = Date.now();
      if (t - lastSlow > 600 || pendingTab) {
        lastSlow = t;
        syncTabs();
        removeSso();
        removeAppBanners();
      }
      if (pendingTab && t > pendingUntil) {
        pendingTab = null;
        send({ type: 'toast', text: "Couldn't find Reddit's feed tabs. Are you logged in?" });
      }
    });
  }

  // ---------- swipe gesture ----------
  var g = null;
  var strip = null;

  function blocksSwipe(n) {
    if (!(n instanceof Element)) return false;
    var tag = n.tagName;
    if (tag === 'GALLERY-CAROUSEL' || tag === 'SHREDDIT-PLAYER' || tag === 'SHREDDIT-PLAYER-2' ||
        tag === 'VIDEO' || tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' ||
        n.isContentEditable || attr(n, 'role') === 'slider') return true;
    if (n.scrollWidth > n.clientWidth + 2) {
      var ox = getComputedStyle(n).overflowX;
      if (ox === 'auto' || ox === 'scroll') return true;
    }
    return false;
  }

  function actionsFor(g) {
    var cfg = (state.settings.swipes || DEFAULT_SWIPES)[g.kind] || DEFAULT_SWIPES[g.kind];
    var ids = cfg[g.dx > 0 ? 'right' : 'left'] || [];
    return [A[ids[0]] || null, A[ids[1]] || null];
  }

  function labelFor(action, el) {
    if (action.id === 'save' && state.saved[idOf(el)]) return 'Remove bookmark';
    return action.label;
  }

  function begin() {
    document.documentElement.classList.add('rn-swiping');
    var r = g.el.getBoundingClientRect();
    g.top = Math.max(r.top, 0);
    g.bottom = Math.min(r.bottom, window.innerHeight);
    g.left = r.left;
    g.right = r.right;
    g.el.style.transition = 'none';
    g.el.style.willChange = 'transform';
    strip = document.createElement('div');
    strip.id = 'rn-strip';
    strip.innerHTML = '<div class="rn-chip"></div>';
    document.documentElement.appendChild(strip);
  }

  function update() {
    var W = window.innerWidth;
    var dx = Math.max(-W * 0.9, Math.min(W * 0.9, g.dx));
    var abs = Math.abs(dx);
    var list = actionsFor(g);
    var level = abs >= W * LONG ? 2 : abs >= W * SHORT ? 1 : 0;
    // An empty long slot keeps the short action; an empty short slot needs a long swipe.
    if (level === 2 && !list[1]) level = list[0] ? 1 : 0;
    if (level === 1 && !list[0]) level = 0;
    var side = dx > 0 ? 'right' : 'left';
    if (level !== g.level || side !== g.side) {
      if (level > g.level && level > 0) send({ type: 'haptic', strong: level === 2 });
      g.level = level;
      g.side = side;
      var shown = level ? list[level - 1] : (list[0] || list[1]);
      strip.firstChild.innerHTML = shown ? ICONS[shown.id] + '<span>' + labelFor(shown, g.el) + '</span>' : '';
    }
    g.el.style.transform = 'translateX(' + dx + 'px)';

    var color = level ? list[level - 1].color : '#2c2c2e';
    strip.className = dx > 0 ? 'rn-left' : 'rn-right';
    strip.style.top = g.top + 'px';
    strip.style.height = Math.max(g.bottom - g.top, 0) + 'px';
    strip.style.width = abs + 'px';
    strip.style.left = (dx > 0 ? g.left : g.right - abs) + 'px';
    strip.style.backgroundColor = color;
    strip.firstChild.style.opacity = level ? '1' : String(Math.min(abs / (W * SHORT), 1) * 0.6);
    strip.firstChild.style.transform = level === 2 ? 'scale(1.12)' : 'scale(1)';
  }

  function finish() {
    var el = g.el;
    var action = g.level > 0 ? actionsFor(g)[g.level - 1] : null;
    var s = strip;
    strip = null;
    document.documentElement.classList.remove('rn-swiping');

    if (action && action.id === 'hide') {
      el.style.transition = 'transform 200ms ease';
      el.style.transform = 'translateX(' + (g.dx > 0 ? '' : '-') + '110%)';
    } else {
      el.style.transition = 'transform 200ms cubic-bezier(.2,.8,.2,1)';
      el.style.transform = 'translateX(0)';
    }
    if (s) {
      s.style.transition = 'opacity 200ms ease';
      s.style.opacity = '0';
    }
    setTimeout(function () {
      if (s && s.parentNode) s.parentNode.removeChild(s);
      el.style.transition = '';
      el.style.willChange = '';
      if (!action || action.id !== 'hide') el.style.transform = '';
    }, 220);

    // A finished swipe should not also count as a tap on whatever was under the finger.
    var swallow = function (e) {
      if (!e.isTrusted) return; // our own vote/reply clicks must go through
      e.stopPropagation();
      e.preventDefault();
    };
    window.addEventListener('click', swallow, true);
    setTimeout(function () { window.removeEventListener('click', swallow, true); }, 400);

    if (action) setTimeout(function () { perform(action, el); }, action.id === 'hide' ? 200 : 0);
  }

  function perform(action, el) {
    var isPost = el.tagName === 'SHREDDIT-POST';
    var id = idOf(el);
    switch (action.id) {
      case 'up':
      case 'down': {
        var btn = voteButton(el, action.id);
        if (btn) btn.click();
        else send({ type: 'toast', text: 'Could not find the vote button. Are you logged in?' });
        break;
      }
      case 'save': {
        var data = isPost ? postData(el) : commentData(el);
        var nowSaved = !state.saved[id];
        if (nowSaved) state.saved[id] = true; else delete state.saved[id];
        markAll();
        send({ type: 'bookmark', saved: nowSaved, data: data });
        break;
      }
      case 'hide': {
        if (!isPost) break;
        state.hidden[id] = true;
        var box = el.closest('article') || el;
        box.setAttribute('data-rn-hidden', '');
        el.style.transform = '';
        send({ type: 'hide', id: id, title: attr(el, 'post-title') });
        break;
      }
      case 'collapse':
        if (!isPost) toggleCollapse(el);
        break;
      case 'reply': {
        var r = isPost ? null : replyButton(el);
        if (r) r.click();
        else send({ type: 'toast', text: 'Could not find the reply button.' });
        break;
      }
      case 'share': {
        var d = isPost ? postData(el) : commentData(el);
        send({ type: 'share', url: d.url, title: d.title });
        break;
      }
    }
  }

  function onStart(e) {
    g = null;
    if (e.touches.length !== 1) return;
    var t = e.touches[0];
    if (t.clientX < EDGE || t.clientX > window.innerWidth - EDGE) return;
    var path = e.composedPath ? e.composedPath() : [];
    var target = null;
    for (var i = 0; i < path.length; i++) {
      var n = path[i];
      if (blocksSwipe(n)) return;
      if (n.tagName === 'SHREDDIT-COMMENT' || n.tagName === 'SHREDDIT-POST') { target = n; break; }
    }
    if (!target) return;
    g = {
      el: target,
      kind: target.tagName === 'SHREDDIT-POST' ? 'post' : 'comment',
      x0: t.clientX, y0: t.clientY, dx: 0, level: -1, side: null, active: false
    };
  }

  function onMove(e) {
    if (!g) return;
    var t = e.touches[0];
    var dx = t.clientX - g.x0;
    var dy = t.clientY - g.y0;
    if (!g.active) {
      if (Math.abs(dy) > 10 && Math.abs(dy) >= Math.abs(dx)) { g = null; return; }
      if (Math.abs(dx) < 14 || Math.abs(dx) < Math.abs(dy) * 1.4) return;
      g.active = true;
      g.x0 = t.clientX - (dx > 0 ? 1 : -1); // start from here so the item doesn't jump
      begin();
      dx = t.clientX - g.x0;
    }
    if (e.cancelable) e.preventDefault();
    g.dx = dx;
    update();
  }

  function onEnd() {
    if (g && g.active) finish();
    g = null;
  }

  // ---------- API for the app ----------
  window.reddnot = {
    setState: function (s) {
      if (s.saved) state.saved = toSet(s.saved);
      if (s.hidden) state.hidden = toSet(s.hidden);
      if (s.settings) {
        var before = JSON.stringify([state.settings.threads, state.settings.rainbow, state.settings.threadWidth]);
        state.settings = s.settings;
        if (JSON.stringify([s.settings.threads, s.settings.rainbow, s.settings.threadWidth]) !== before) lineVer++;
      }
      applySettings();
      markAll();
      syncTabs();
    },
    // The post on a comments page, for the top bar's bookmark button.
    currentPost: function () {
      var p = pagePost();
      return p && location.pathname.indexOf('/comments/') >= 0 ? postData(p) : null;
    },
    // Switch Reddit's home feed tab: 'foryou' or 'following'. Keeps trying while the page loads.
    feedTab: function (name) {
      pendingTab = name;
      pendingUntil = Date.now() + 5000;
      lastSlow = 0;
      queueMark();
      setTimeout(queueMark, 800);
      setTimeout(queueMark, 2000);
      setTimeout(queueMark, 5200);
    }
  };

  // ---------- start ----------
  function init() {
    addStyle();
    applySettings();
    markAll();
    queueMark();
  }

  document.addEventListener('touchstart', onStart, { passive: true, capture: true });
  document.addEventListener('touchmove', onMove, { passive: false, capture: true });
  document.addEventListener('touchend', onEnd, { passive: true, capture: true });
  document.addEventListener('touchcancel', onEnd, { passive: true, capture: true });

  if (document.documentElement) {
    addStyle();
    applySettings();
  }
  new MutationObserver(function () {
    if (!document.getElementById('rn-style')) addStyle();
    queueMark();
  }).observe(document, { childList: true, subtree: true });

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();

  send({ type: 'ready' });
})();
