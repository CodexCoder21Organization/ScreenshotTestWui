# ScreenshotTestWui

The session gallery and diff viewer for the **ScreenshotTest** golden-screenshot service
([`url://screenshottest/`](https://github.com/CodexCoder21Organization/ScreenshotTestServerService)) —
part of the [ScreenshotTest workstream](https://github.com/CodexCoder21Organization/PlanRepository/blob/main/workstreams/ScreenshotTest.md).

It is a deliberately minimal, read-only web UI (Jetty 11, `org.json`, dark GitHub theme) that connects
to `url://screenshottest/` as a typed
[`ScreenshotTestApi`](https://github.com/CodexCoder21Organization/ScreenshotTestApi) proxy and renders:

- **`/`** — the sessions list: every render session newest-first (id, label, mode, state, created,
  renderer).
- **`/session?id=<id>`** — one session's detail: its status, the per-key verdict table (key, verdict,
  diff pixels / total, max channel delta, golden & actual dimensions), and, for each captured key,
  inline `actual` / `golden` / `diff` thumbnails.
- **`/image?id=<id>&key=<key>&kind=<actual|golden|diff>`** — streams the PNG for one image key,
  pulled from the service in bounded chunks and written straight to the response so the WUI never
  buffers a whole image in its `-Xmx128m` heap.
- **`/health`** — a backend-free liveness probe returning `200 OK`.

It is also the **first WUI to dogfood the ScreenshotTest service on itself**: its own pages are
captured as golden screenshots against the live `url://screenshottest/` renderer and committed under
[`screenshots/`](screenshots/) (see [Golden screenshots](#golden-screenshots-dogfooding)).

## Building

The project builds with [kompile-cli](https://github.com/CodexCoder21Organization/kompile) via the
wrapper in `scripts/`:

```bash
scripts/build.bash screenshottest.wui.buildMaven                              # publishable skinny artifact
scripts/build.bash screenshottest.wui.buildFatJar build-fats/screenshottest-wui.jar   # runnable fat jar
```

`buildFatJar` produces the runnable jar with `Main-Class` `screenshottest.wui.MainKt`.

## Running

Run the fat jar with `java -jar`. The WUI reads its listen port from `PORT` (default `8080`) and
connects to `url://screenshottest/` lazily on the first request:

```bash
PORT=8080 java -jar build-fats/screenshottest-wui.jar
# then open http://localhost:8080/
```

In production it is deployed to [ContainerNursery](https://github.com/CodexCoder21Organization/ContainerNursery)
as the HTTPS route **https://screenshottest.nursery.wasmserver.com** (an `-Xmx128m`
`danger_jarfile` container, the standard WUI pattern), which sets `PORT` for it.

## Programmatic use

Every page is produced by the `createServer(port, api, clock)` seam, so the whole WUI can be driven
in-process against any `ScreenshotTestApi` — no P2P connection, no browser — which is exactly what the
hermetic tests and the golden-screenshot fixture do:

```kotlin
import screenshottest.api.ScreenshotTestApi
import screenshottest.wui.createServer
import community.kotlin.clocks.simple.ManualClock

// Any ScreenshotTestApi — a live proxy, or a fake returning canned JSON.
val api: ScreenshotTestApi = myFakeApi()

// Inject a fixed clock so relative "created N ... ago" times are byte-stable (0 = random free port).
val server = createServer(port = 0, api = api, clock = ManualClock(1_735_689_600_000L))
server.start()
// ... issue HTTP requests against server.connectors[0].localPort ...
server.stop()
```

`createServer` defaults the clock to a real `SystemClock`
([`community.kotlin.clocks.simple`](https://github.com/CodexCoder21Organization)); the injected clock
anchors the browser-side relative-time math to the server's render instant, which is what makes a
fixed-clock render reproducible enough to golden-screenshot.

For production wiring, `main()` connects to the live service through `ScreenshotTestClient`, which
opens a sandboxed typed proxy:

```kotlin
UrlResolver().openSandboxedConnection("url://screenshottest/", ScreenshotTestApi::class)
```

## Testing

```bash
scripts/test.bash --test .                                      # all hermetic e2e tests
scripts/test.bash --test tests/imageEndpointStreamsExactBytesTest.kts   # a single scenario
```

The tests are end-to-end and self-contained: each starts a real Jetty server via `createServer`
against an inline fake `ScreenshotTestApi` (no mocks) and exercises the real HTTP surface — the
sessions list, the verdict table and thumbnail gallery, the chunk-streamed `/image` endpoint
(including byte-for-byte streaming of a multi-MiB image and descriptive `400`/`404` errors), the
frozen-clock time-stability property, and `/health`.

## Golden screenshots (dogfooding)

`tests/goldenScreenshots.kts` renders this WUI's own pages through the pinned
`url://screenshottest/` renderer and compares them against the goldens committed under
[`screenshots/`](screenshots/). It launches `ScreenshotFixtureServer` — a deterministic, frozen-clock
build of this WUI serving a fixed set of sessions (one compare session with a MATCH and a DIFF key,
one record session, one failed session) with small in-memory PNGs — on a hosted worker, captures the
sessions list and a session detail page (plus the results-table crop), and asserts every verdict is
`MATCH`.

- **Compare (CI default):**
  ```bash
  scripts/test.bash --test tests/goldenScreenshots.kts
  ```
- **Record (regenerate goldens after an intentional visual change):**
  ```bash
  SCREENSHOTTEST_RECORD=1 scripts/test.bash --test tests/goldenScreenshots.kts
  ```
  then inspect the new `screenshots/*.png` and commit them together with `screenshots/RENDERER_VERSION`
  (the renderer identity the goldens were produced by — a mismatch fails the compare fast with both
  versions).

## Related modules

- [ScreenshotTestApi](https://github.com/CodexCoder21Organization/ScreenshotTestApi) — the
  `url://screenshottest/` contract this WUI consumes.
- [ScreenshotTestServerService](https://github.com/CodexCoder21Organization/ScreenshotTestServerService)
  — hosts `url://screenshottest/`.
- [ScreenshotTestEmbedded](https://github.com/CodexCoder21Organization/ScreenshotTestEmbedded) /
  [ScreenshotTestRunner](https://github.com/CodexCoder21Organization/ScreenshotTestRunner) — session
  orchestration and the on-worker pinned renderer.
- [BuildTestWui](https://github.com/CodexCoder21Organization/BuildTestWui) — the architectural
  template and the first adopter of golden screenshots.
