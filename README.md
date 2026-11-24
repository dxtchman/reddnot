# Reddnot

A custom Reddit reader for Android, in the spirit of Apollo. Reddit closed its API to new apps, so Reddnot wraps the real reddit.com in a native app and adds its own look, swipes and bookmarks on top.

## Swipes

Defaults (change any of them in **Me → Swipe actions**):

| | Short swipe → | Long swipe → → | Short swipe ← | Long swipe ← ← |
|---|---|---|---|---|
| **Posts** | Upvote | Downvote | Bookmark | Hide |
| **Comments** | Upvote | Downvote | Collapse | Bookmark |

Other actions you can assign: reply, share, or nothing. You feel a tick as you cross into each action. Swiping something you already bookmarked removes the bookmark. Bookmarks, hides and removals all have **Undo**.

## What's in it

- **Feed**: For You · Following · News tabs. Each tab keeps its own page, so switching doesn't reload anything. Tap the tab you're on to jump back to its feed, then to the top; tap again at the top to refresh (the bottom Feed button does the same). News opens Reddit's own News feed when the site offers it, otherwise news subreddits you choose in Me. Rainbow comment threads like Apollo. Reddit with your theme, no Reddit header, no promoted posts and no "open in app" nags. Pull to refresh; tap the title to jump to the top. The top bar has sort, search, share and a bookmark button on posts.
- **Subreddits**: Home, Popular and All, plus your own shortcuts (add from the box, or from ⋮ while viewing a sub). Swipe a shortcut to remove it.
- **Bookmarks**: posts and comments, saved on the phone and searchable. Swipe left to delete, right to share.
- **Me**: profile, notifications, messages, log in/out, and customization: background theme (True black, Midnight, Charcoal, Grape, Forest, Reddit dark), accent color, thread-line colors (Rainbow with 4 palettes, Accent, or Reddit's), text size, swipe actions and the News tab's subreddits.
- Fullscreen video, image uploads, and links to other sites open in your browser.

Log in with your Reddit username and password (or email code). Google and Apple sign-in are removed because they can't work inside apps like this.

To open Reddit links from other apps in Reddnot: Android Settings → Apps → Reddnot → Open by default → add the links.

## Fixing things when Reddit changes its site

Everything that touches Reddit's pages lives in two files:

- `app/src/main/assets/reddnot.css`: colors and what gets hidden
- `app/src/main/assets/reddnot.js`: swipes (the `ACTIONS` table at the top sets what each swipe does), bookmarks and hiding

## Install

Every push to `main` builds the app on GitHub Actions. Open **Releases**, tap the newest `Reddnot.apk` and install it.

Requires Android 11 or newer.
