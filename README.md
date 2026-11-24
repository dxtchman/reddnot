# Reddnot

A custom Reddit reader for Android, in the spirit of Apollo. Reddit closed its API to new apps, so Reddnot wraps the real reddit.com in a native app and adds its own look, swipes and bookmarks on top.

## Swipes

| | Short swipe → | Long swipe → → | Short swipe ← | Long swipe ← ← |
|---|---|---|---|---|
| **Posts** | Upvote | Downvote | Bookmark | Hide |
| **Comments** | Upvote | Downvote | Collapse | Bookmark |

You feel a tick as you cross into each action. Swiping something you already bookmarked removes the bookmark. Bookmarks, hides and removals all have **Undo**.

## What's in it

- **Feed**: reddit.com with true black, no Reddit header, no promoted posts and no "open in app" nags. Pull to refresh; tap the title to jump to the top. The top bar has sort, search, share and a bookmark button on posts.
- **Subreddits**: Home, Popular and All, plus your own shortcuts (add from the box, or from ⋮ while viewing a sub). Swipe a shortcut to remove it.
- **Bookmarks**: posts and comments, saved on the phone and searchable. Swipe left to delete, right to share.
- **Me**: profile, notifications, messages, log in/out, look settings and hidden posts.
- Fullscreen video, image uploads, and links to other sites open in your browser.

Log in with your Reddit username and password (or email code). Google/Apple sign-in pop-ups don't work inside apps like this.

To open Reddit links from other apps in Reddnot: Android Settings → Apps → Reddnot → Open by default → add the links.

## Fixing things when Reddit changes its site

Everything that touches Reddit's pages lives in two files:

- `app/src/main/assets/reddnot.css`: colors and what gets hidden
- `app/src/main/assets/reddnot.js`: swipes (the `ACTIONS` table at the top sets what each swipe does), bookmarks and hiding

## Install

Every push to `main` builds the app on GitHub Actions. Open **Releases**, tap the newest `Reddnot.apk` and install it.

Requires Android 11 or newer.
