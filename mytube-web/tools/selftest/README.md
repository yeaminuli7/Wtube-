# Wtube inject.js self-test

Headless DOM tests for `assets/inject.js` — the YouTube-navigation hiding logic
(`mtHideYtNav()` / `ensureNavCss()`).

These tests build a synthetic `m.youtube.com` DOM (signed-in, with the 2026
"swipeable top tabs" feed layout) and run the **real** `assets/inject.js`
inside it, then assert which elements end up hidden.

## Run

```sh
cd tools/selftest
npm install        # needs network (jsdom only, dev dependency)
npm test
```

## What is covered

`test.js`
- bottom pivot bar (`ytm-pivot-bar-renderer`) hidden
- a brand-new, unknown top tab strip hidden **by link signature** (no class names)
- feed chip bar: `Home` / `Subscriptions` chips hidden, `All` / `Music` / `Gaming` filter chips kept
- search box, our brand (`#__mtbrand`), our bell (`#__mtbell`), top bar and feed content kept
- watch page: related-chips bar (`ytm-chip-cloud-renderer.chip-bar`) kept
- setting `navhide=false` restores YouTube's nav and removes the stylesheet

`test2.js`
- class-less `role="tablist"` strip hidden by pure signature
- channel page tabs (Home/Videos/Shorts/Live) are **not** hidden
- desktop layout guide (`ytd-guide-renderer`) hidden, desktop search kept
- a top strip that appears *after* login is hidden automatically (MutationObserver path)
- a top strip that appears without a mutation is caught by the 600 ms sweep
- `accounts.google.com` sign-in page is never touched (host guard)
