# APK deliverables

Every build output lives here and **is committed and pushed to GitHub**.

## Rules

1. **Build into this directory.** `build.sh` copies its output here automatically
   (see the "Publish" step at the end).
2. **Never build into `build/`.** Directories named `build/` are excluded from
   workspace snapshots — files written there are silently discarded and never
   reach anyone. This has already cost one lost APK.
3. **Commit and push after every build.** The remote is the only durable store;
   the sandbox's local git state can be reset between sessions.
4. **Never commit signing keys.** `.gitignore` excludes `*.p12`, `*.jks` and
   `*.keystore` — **except `debug.keystore`**, which is deliberately tracked so
   successive sandbox builds share one key and install over each other. The
   real release keystore lives outside the repo.
5. **Publish a GitHub release for every build.** The Releases page is what
   users actually look at, so the APK is not "shipped" until a release exists
   with a download link.

## After building

```sh
cd mytube-web
./build.sh Wtube_v2.03.apk 80 2.03      # also lands in apk/
git add -f apk/Wtube_v2.03.apk
git commit -m "Add built Wtube v2.03 APK"
git push origin <branch>
```

Then publish the release:

```sh
gh release create v2.03 --target <branch> \
  --title "Wtube v2.03 — <summary>" --notes-file /tmp/notes.md
```

Direct download link (works on a phone):

```
https://github.com/<owner>/<repo>/raw/<branch>/mytube-web/apk/Wtube_v2.03.apk
```

## Releases

Published builds live on the
[Releases page](https://github.com/yeaminuli7/vr/releases), newest first:

| Release | Version | Notes |
| --- | --- | --- |
| [v2.06](https://github.com/yeaminuli7/vr/releases/tag/v2.06) | 2.06 (vc 83) | Telegram-style in-app browser (Custom Tabs): Subs/History/Your channel open signed in; offline build script |
| [v2.05](https://github.com/yeaminuli7/vr/releases/tag/v2.06) | 2.05 (vc 82) | Sign-in flow hardened: clean injection on YouTube sign-in pages, Chrome brands in JS, intent:// fallback |
| [v2.04](https://github.com/yeaminuli7/vr/releases/tag/v2.06) | 2.04 (vc 81) | Account buttons respond; WebView identity hardened against Google's sign-in block |
| [v2.03](https://github.com/yeaminuli7/vr/releases/tag/v2.03) | 2.03 (vc 80) | Sign-in actually responds (adds the missing `WebChromeClient` popup support); ad blocking strengthened |
| [v2.02](https://github.com/yeaminuli7/vr/releases/tag/v2.02) | 2.02 (vc 79) | Sign in with your Google account |
| [v2.01](https://github.com/yeaminuli7/vr/releases/tag/v2.01) | 2.01 (vc 78) | Hides YouTube's duplicate top nav bar |

Each release notes page has the direct `.apk` download link at the top.

> **Release assets vs. repo files.** Release *asset* uploads go to
> `uploads.github.com`, which is not reachable from the sandbox. Releases
> therefore link to the APK committed in the repo rather than hosting a
> separate copy. Both resolve to the same file.

## Signing caveat

Builds from v2.02 onward share the committed `debug.keystore`
(alias `mytube`), so they **install over each other** without uninstalling.

v2.01 and earlier were signed with generated keys that no longer exist, so
moving from those to v2.02+ requires **uninstalling first**.

Neither is the real release keystore — rebuild locally with `build.sh` for a
properly signed production build.
