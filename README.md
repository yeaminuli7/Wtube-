# vr — Wtube 2.0 source & analysis

Repository for **Wtube 2.0** (`com.mytube.web`, versionCode 77) — a YouTube front-end for
Android with a self-written ExoPlayer/Media3 engine, a self-written parallel downloader
(MP3 / SRT), and a layered ad-blocking pipeline — plus a full source-code audit of it.

| Path | What it is |
|---|---|
| [`ANALYSIS.md`](ANALYSIS.md) | **The deliverable.** Complete source analysis: architecture, module-by-module review, 20 prioritised findings with line references, security/privacy review, performance, build engineering, native-layer and licensing assessment, test strategy, 5-phase roadmap. |
| `mytube-web/` | The extracted source of `Wtube_source_2.0.zip.xml` (unmodified). 12,187 lines of Java across 16 classes, 5 asset blobs, the Gradle-free `build.sh`, and the native/C/Python tooling. |

## Headline findings

Four P0 defects, all in the shipped 2.0 build:

1. **Download truncation race** — `DownloadService.parFetch()` has 12 chunk threads each calling
   `RandomAccessFile.setLength()` on the *same* file, so a later chunk can truncate bytes an
   earlier chunk already wrote. Corrupt MP4s on every download ≥ 3 MB. → `DownloadService.java:1171`
2. **`PlayerService` NPE** — `START_STICKY` restart delivers a null Intent, and
   `onStartCommand` dereferences it. Crash loop. → `PlayerService.java:247`
3. **`PlayerService` foreground-service violation** — `startForeground()` is only called on the
   `ACT_PLAY` branch, so notification actions can trigger
   `ForegroundServiceDidNotStartInTimeException`. → `PlayerService.java:258`
4. **Download service never stops** — cancelling a *queued* job never calls `pump()`, leaving the
   foreground service (and its notification) alive forever. → `DownloadService.java:122`

Plus the dominant maintainability issue: **~168 of ~330 `catch (Throwable)` blocks are
completely empty**, which is why the 1,214-line DEVLOG is largely a record of bugs found by
reading source rather than by tests.

See [`ANALYSIS.md`](ANALYSIS.md) for the full report, the ~10 P1/P2 findings, and the
phased fix plan.

## Building the app

```bash
cd mytube-web
./build.sh Wtube_v2.0.apk 77 2.0
```

Needs Linux + JDK + `curl`/`unzip`/`zip` + `python3`. The script downloads `android.jar`,
`aapt2`, R8/D8 and the Media3 AARs itself. **The signing keystore is not in this repo**
(see ANALYSIS.md §8.1) — the keystore path/password are referenced from an absolute local path.
