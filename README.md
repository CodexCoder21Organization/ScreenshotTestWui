# ScreenshotTestWui

The session gallery and diff viewer for the **ScreenshotTest** golden-screenshot service
([`url://screenshottest/`](https://github.com/CodexCoder21Organization/ScreenshotTestServerService)) —
part of the [ScreenshotTest workstream](https://github.com/CodexCoder21Organization/PlanRepository/blob/main/workstreams/ScreenshotTest.md).

It is a small management web UI (Jetty 11, `org.json`, dark GitHub theme) that connects to
`url://screenshottest/` as a typed
[`ScreenshotTestApi`](https://github.com/CodexCoder21Organization/ScreenshotTestApi) proxy
(`screenshottest.api:screenshottest-api:0.0.3`; the worker-pool API arrived in 0.0.2)
and serves:

- **`/`** — the sessions list: every render session newest-first (id, label, mode, state, created,
  **duration**) with a per-row action; the renderer identity is the id link's tooltip. A `RUNNING`
  session also shows its **phase**, derived from its queue position: **Queued #n** (waiting for a
  free render worker, n = place in the queue) or **Rendering**. A cancelled session is `FAILED`. The
  duration is finished − started for finished sessions, a live "running for" for rendering ones, and
  "waiting" since creation for queued ones — measured against the server's clock, with the absolute
  UTC instants in the cell's tooltip.
- **`/workers`** — the render worker pool: the maximum number of sessions rendered at once, how many
  workers are busy, how many sessions wait; a table of the sessions rendering now (started, running
  for) and of the queue in service order (position, created, waiting for); a form to change the pool
  size; and a **Cancel** action per session. It reloads itself every 15 seconds; `/workers?refresh=0`
  is the same page without the reload.
- **`/session?id=<id>`** — one session's detail: its state, phase, queue position, started / finished
  instants and duration, the action its state allows (Cancel with an editable reason, or Delete), the
  per-key verdict table (key, verdict, diff pixels / total, max channel delta, golden & actual
  dimensions), and, for each captured key, inline thumbnails of the images the service produced:
  `actual` always, `golden` for `MATCH` and `DIFF` keys, and the `diff` heatmap for `DIFF` keys. For a queued
  session, its waiting duration comes from the recent sessions list; if it is no longer listed, the
  duration shows `-` with an explanation that the creation time is unavailable.
- **`/image?id=<id>&key=<key>&kind=<actual|golden|diff>`** — streams the PNG for one image key,
  pulled from the service in bounded chunks and written straight to the response so the WUI never
  buffers a whole image in its `-Xmx128m` heap.
- **`/health`** — a backend-free liveness probe returning `200 OK`.

### Management actions

The actions are plain HTML form POSTs, so they work without JavaScript:

| Action | Form | Allowed for | Service call |
|---|---|---|---|
| Cancel | `POST /session/cancel` — `id`, `reason` (defaults to "Cancelled from the management UI"; at most 512 characters), `returnTo` | `RUNNING` sessions, queued or rendering | `cancelSession(id, reason)` — the session becomes `FAILED` with "Cancelled before rendering started: …" or "Cancelled while rendering: …" |
| Delete | `POST /session/delete` — `id`, `returnTo` | `COMPLETED` / `FAILED` sessions | `deleteSession(id)` |
| Set pool size | `POST /workers/max` — `maxWorkers` | — | `setMaxWorkers(n)`; the service enforces the allowed range |

On success the WUI answers `303 See Other` back to the page named by `returnTo` (`list`, `workers`, or
`session`; deleting from a session's own page returns to the list), which shows a dismissible
status banner. The redirect carries only an enumerated `notice` code and an id or requested pool size.
The page checks current service data before showing a banner: the current pool size must match, a
cancelled session must be FAILED with its recorded cancellation error, and a deleted session must no
longer be listed. An unmatched selector shows no banner. Deleting an already absent session is
idempotent in the service, so it may still return `303`; the banner says only that it is no longer
listed. On failure, the originating page is rendered directly with an error banner holding the
service's full message:
`400` for a missing or malformed field or a locally typed value rejection, `404` for a typed unknown
session, `409` for a typed wrong-state response, and `502` for a connection or other backend failure.
The currently pinned UrlResolver wraps exceptions raised inside the remote service as
`SandboxException` without a structured remote exception type, so those remote validation and state
errors currently use `502` while still showing the full service message. This transport limitation
needs a typed exception field before the WUI can distinguish them reliably.
If the WUI cannot establish its API connection while loading `/`, `/workers`, or `/session`, that page
also returns `502` and shows the connection error.
A POST that the browser labels as coming from another site (`Sec-Fetch-Site: cross-site` or
`same-site`) is refused with `403`.

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
sessions list with its phases, durations and actions, the worker pool page, the cancel / delete /
set-max-workers actions (redirects, notices, and every error status with the service's message), the
verdict table and thumbnail gallery, the chunk-streamed `/image` endpoint
(including byte-for-byte streaming of a multi-MiB image and descriptive `400`/`404` errors), the
frozen-clock time-stability property, and `/health`.

## Golden screenshots (dogfooding)

`tests/goldenScreenshots.kts` renders this WUI's own pages through the pinned
`url://screenshottest/` renderer and compares them against the goldens committed under
[`screenshots/`](screenshots/). It launches `ScreenshotFixtureServer` — a deterministic, frozen-clock
build of this WUI serving a fixed set of sessions (two rendering and two queued on a full two-worker pool, one compare session
with a MATCH and a DIFF key, one record session, one failed session) with small in-memory PNGs — on a
hosted worker, captures the sessions list, a completed session's detail page (plus the results-table
crop), a queued session's detail page, and the worker pool page (`/workers?refresh=0`), and asserts
every verdict is `MATCH`.

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
