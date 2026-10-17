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
   `*.keystore`. The real release keystore lives outside the repo.

## After building

```sh
cd mytube-web
./build.sh Wtube_v2.01.apk 78 2.01      # also lands in apk/
git add -f apk/Wtube_v2.01.apk
git commit -m "Add built Wtube v2.01 APK"
git push origin <branch>
```

Direct download link (works on a phone):

```
https://github.com/<owner>/<repo>/raw/<branch>/mytube-web/apk/Wtube_v2.01.apk
```

## Signing caveat

APKs built in this sandbox are signed with a **generated** key, not the release
keystore. Android refuses to update an app signed with a different key, so
**uninstall the existing Wtube before installing** — or rebuild locally, where
`build.sh` uses the real keystore.
