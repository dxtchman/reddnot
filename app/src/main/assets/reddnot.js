/*
 * Reddnot page script. Runs at the start of every reddit.com page.
 * - Adds the Reddnot look (CSS from reddnot.css, passed in as window.__RN_CSS)
 * - Apollo-style swipe actions on posts and comments
 * - Marks bookmarked posts, removes hidden ones
 *
 * Talks to the app through window.reddnotBridge.postMessage(json).
 * The app talks back through window.reddnot.setState({...}).
 */
(function () {
  'use strict';
  if (window.__reddnotLoaded) return;
  window.__reddnotLoaded = true;

  // ---------- swipe actions (edit here to change what each swipe does) ----------
  var ICONS = {
    up: '<svg viewBox="0 0 24 24"><path d="M12 4l-8 8h5v8h6v-8h5z"/></svg>',
    down: '<svg viewBox="0 0 24 24"><path d="M12 20l8-8h-5V4H9v8H4z"/></svg>',
    save: '<svg viewBox="0 0 24 24"><path d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg>',
    hide: '<svg viewBox="0 0 24 24"><path d="M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92A11.8 11.8 0 0 0 23 12c-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46A11.8 11.8 0 0 0 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55A2.8 2.8 0 0 0 9 12a3 3 0 0 0 3 3c.22 0 .44-.03.65-.08l1.55 1.55A5 5 0 0 1 7 12c0-.79.2-1.53.53-2.2z"/></svg>',
    collapse: '<svg viewBox="0 0 24 24"><path d="M7.4 15.4L12 10.8l4.6 4.6L18 14l-6-6-6 6z"/></svg>'
  };
  var A = {
    up: { id: 'up', label: 'Upvote', color: '#ff6a1a' },
    down: { id: 'down', label: 'Downvote', color: '#6a8cff' },
    save: { id: 'save', label: 'Bookmark', color: '#34c759' },
    hide: { id: 'hide', label: 'Hide', color: '#8e8e93' },
    collapse: { id: 'collapse', label: 'Collapse', color: '#5e5ce6' }
  };
  // [short swipe, long swipe] for each direction. "right" means the finger moves right.
  var ACTIONS = {
    post: { right: [A.up, A.down], left: [A.save, A.hide] },
    comment: { right: [A.up, A.down], left: [A.collapse, A.save] }
  };
  var SHORT = 0.2;  // fraction of screen width for a short swipe
  var LONG = 0.48;  // fraction of screen width for a long swipe
  var EDGE = 28;    // px at the screen edges left alone for Android's back gesture

  // ---------- state from the app ----------
  var state = {
    saved: {},
    hidden: {},
    settings: { black: true, noHeader: true, noAds: true }
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
    html.classList.toggle('rn-black', !!state.settings.black);
    html.classList.toggle('rn-noheader', !!state.settings.noHeader);
    html.classList.toggle('rn-noads', !!state.settings.noAds);
  }

  // ---------- CSS ----------
  function addStyle() {
    if (document.getElementById('rn-style') || !document.documentElement) return;
    var s = document.createElement('style');
    s.id = 'rn-style';
    s.textContent = window.__RN_CSS || '';
    (document.head || document.documentElement).appendChild(s);
  }

  // ---------- reading posts and comments ----------
  function attr(el, name) { return (el && el.getAttribute(name)) || ''; }

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
      title: attr(el, 'post-title') || (el.querySelector('[slot="title"]') || {}).textContent || 'Post',
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
    var text = body ? body.innerText.trim().replace(/\s+/g, ' ') : '';
    var post = pagePost();
    return {
      id: commentId(el),
      kind: 'comment',
      title: post ? attr(post, 'post-title') : document.title,
      subreddit: post ? attr(post, 'subreddit-prefixed-name') : '',
      author: attr(el, 'author'),
      url: absUrl(attr(el, 'permalink')),
      thumb: '',
      snippet: text.slice(0, 400)
    };
  }

  function idOf(el) { return el.tagName === 'SHREDDIT-POST' ? postId(el) : commentId(el); }

  // Walks an element and its shadow roots. Skips nested comments so a comment
  // never acts on one of its replies.
  function deepFind(root, test) {
    var stack = [root];
    while (stack.length) {
      var n = stack.pop();
      if (n !== root && n.tagName === 'SHREDDIT-COMMENT') continue;
      if (n.nodeType === 1 && test(n)) return n;
      if (n.shadowRoot) stack.push(n.shadowRoot);
      var kids = n.children || [];
      for (var i = kids.length - 1; i >= 0; i--) stack.push(kids[i]);
    }
    return null;
  }

  function voteButton(el, dir) {
    var re = dir === 'up' ? /upvote/i : /downvote/i;
    var scope = el.tagName === 'SHREDDIT-COMMENT'
      ? (el.querySelector(':scope > shreddit-comment-action-row, :scope > [slot="actionRow"]') || el)
      : el;
    return deepFind(scope, function (n) {
      return n.tagName === 'BUTTON' &&
        (n.hasAttribute(dir === 'up' ? 'upvote' : 'downvote') || re.test(attr(n, 'aria-label')));
    });
  }

  function toggleCollapse(el) {
    var btn = deepFind(el, function (n) {
      return n.tagName === 'BUTTON' && /(collapse|expand|toggle)/i.test(attr(n, 'aria-label'));
    });
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

  // ---------- marks (bookmarked / hidden) ----------
  function setFlag(el, name, on) {
    if (on && !el.hasAttribute(name)) el.setAttribute(name, '');
    else if (!on && el.hasAttribute(name)) el.removeAttribute(name);
  }

  function markAll() {
    var nodes = document.querySelectorAll('shreddit-post, shreddit-comment');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var id = idOf(el);
      setFlag(el, 'data-rn-saved', !!state.saved[id]);
      if (el.tagName === 'SHREDDIT-POST') {
        var box = el.closest('article') || el;
        setFlag(box, 'data-rn-hidden', !!state.hidden[id] && location.pathname.indexOf('/comments/') < 0);
        if (el.hasAttribute('promoted') || attr(el, 'post-type') === 'promoted') setFlag(box, 'data-rn-ad', true);
      }
    }
  }

  var markQueued = false;
  function queueMark() {
    if (markQueued) return;
    markQueued = true;
    requestAnimationFrame(function () { markQueued = false; markAll(); });
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

  function actionsFor(g) { return ACTIONS[g.kind][g.dx > 0 ? 'right' : 'left']; }

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
    var level = abs >= W * LONG ? 2 : abs >= W * SHORT ? 1 : 0;
    var list = actionsFor(g);
    var side = dx > 0 ? 'right' : 'left';
    if (level !== g.level || side !== g.side) {
      if (level > g.level && level > 0) send({ type: 'haptic', strong: level === 2 });
      g.level = level;
      g.side = side;
      var shown = list[Math.max(level, 1) - 1];
      strip.firstChild.innerHTML = ICONS[shown.id] + '<span>' + labelFor(shown, g.el) + '</span>';
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
    var action = g.level ? actionsFor(g)[g.level - 1] : null;
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
    var swallow = function (e) { e.stopPropagation(); e.preventDefault(); };
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
        state.hidden[id] = true;
        var box = el.closest('article') || el;
        box.setAttribute('data-rn-hidden', '');
        el.style.transform = '';
        send({ type: 'hide', id: id, title: attr(el, 'post-title') });
        break;
      }
      case 'collapse':
        toggleCollapse(el);
        break;
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
      x0: t.clientX, y0: t.clientY, dx: 0, level: -1, active: false
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
      if (s.settings) state.settings = s.settings;
      applySettings();
      markAll();
    },
    // The post on a comments page, for the top bar's bookmark button.
    currentPost: function () {
      var p = pagePost();
      return p && location.pathname.indexOf('/comments/') >= 0 ? postData(p) : null;
    }
  };

  // ---------- start ----------
  function init() {
    addStyle();
    applySettings();
    markAll();
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
