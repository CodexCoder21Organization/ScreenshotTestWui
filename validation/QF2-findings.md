# QF2 validation

Started 2026-10-05 13:32:45 UTC. Deadline 14:22:45 UTC.
PR: https://github.com/CodexCoder21Organization/ScreenshotTestWui/pull/4
Verified OPEN at 4b6e1d629177f91a2e329db91094adcc8037ea3b.

Plan:
1. COMPLETE: fresh checkout, head/OPEN confirmation, README, PR body and prior final notes.
2. IN PROGRESS: run nine named scenarios individually against final source; record named result and duration.
3. TODO: diagnose and fix any failure without changing pins, assertions, counts or timeouts; one commit per fix.
4. TODO: submit full remote suite, record run id immediately, fall back to local file batches after 40 minutes.
5. TODO: update PR validation/OPEN, final results/head/process check and exit within 50 minutes.

OBSERVED: Previous QF final notes list 21/30 scenarios passed and nine open, plus full suite open. Last edit corrects numeric map conversion in both image probes. README and PR body agree with the brief invariants.
INFER: Nine tests are validation work, with no source change needed unless a run demonstrates a defect. Dependency pins remain resolver 0.0.1261 / protocol 0.0.532.

13:36 UTC — OBSERVED: first selector is compiling the sandbox fixture and WUI sources, not using a 3ms memoized artifact. Testing architecture read in full; test execution stays through Jetty/public API with isolated peers, full messages and existing budgets. No source edits. Initial evidence checkpoint pushed and ls-remote verified.
INFER: cold compilation explains startup cost; no test verdict yet.

Durable evidence: https://github.com/CodexCoder21Organization/ScreenshotTestWui/blob/wip/qf2-pr4-validation/validation/QF2-findings.md (evidence-only commit, not pushed to the PR branch).

## Individual results on final source

| Scenario | Result | Test duration | Evidence |
|---|---|---|---|
| `deleteReportedArgumentThroughRealSandboxTest` | PASS 1/1 | 15.172s | /tmp/qf2-deleteReportedArgumentThroughRealSandboxTest.xml |
| `deleteReportedOtherThroughRealSandboxTest` | PASS 1/1 | 18.572s | /tmp/qf2-deleteReportedOtherThroughRealSandboxTest.xml |
| `imageFirstProbeReportedArgumentThroughRealSandboxTest` | PASS 1/1 | 14.298s | /tmp/qf2-imageFirstProbeReportedArgumentThroughRealSandboxTest.xml |

13:41 UTC — OBSERVED: three of nine selectors passed alone on unchanged head: both delete failures and corrected image rejected-argument404. XML names match requested selectors, test runtimes15.172/18.572/14.298s. Each pass checkpoint pushed and verified.
INFER: normalized numeric map now reaches the intended provider exception; no assertion or source fix needed so far. Other image probe and remaining workers/synthetic scenarios underway.
| `imageFirstProbeReportedOtherThroughRealSandboxTest` | PASS 1/1 | 8.128s | /tmp/qf2-imageFirstProbeReportedOtherThroughRealSandboxTest.xml |
| `maxWorkersReportedOtherThroughRealSandboxTest` | PASS 1/1 | 8.934s | /tmp/qf2-maxWorkersReportedOtherThroughRealSandboxTest.xml |
| `maxWorkersReportedStateThroughRealSandboxTest` | PASS 1/1 | 15.185s | /tmp/qf2-maxWorkersReportedStateThroughRealSandboxTest.xml |
| `sandboxedBackendFailuresMapToStatusesTest` | PASS 1/1 | 3.583s | /tmp/qf2-sandboxedBackendFailuresMapToStatusesTest.xml |
| `sandboxedReportedArgumentMapsTo404Test` | PASS 1/1 | 2.985s | /tmp/qf2-sandboxedReportedArgumentMapsTo404Test.xml |
| `sandboxedReportedStateMapsTo409Test` | PASS 1/1 | 1.685s | /tmp/qf2-sandboxedReportedStateMapsTo409Test.xml |

OBSERVED: All nine named scenarios passed individually; every XML per-test line names the exact requested scenario. Source remained unchanged.
INFER: No fix is indicated by these results. Plan step 2 COMPLETE; step 3 COMPLETE (no failures); step 4 IN PROGRESS.

Full remote suite submitted at 2026-10-05 13:44:53 UTC via scripts/test.bash --remote --test . --log full.xml. Run id pending command output.

13:45 UTC — OBSERVED: all9 individual selectors passed, including both final numeric-map image retests. Full remote suite connected with health OK at13:44:53, but has not yet supplied a run id. Source head unchanged, working tree clean.
INFER: no test/code fix is needed based on individual results. The remote40-minute fallback threshold is14:24:53, later than the job14:22:45 deadline; unless remote exits earlier, the explicit50-minute stop takes precedence. Full-suite result remains OPEN until actual counts arrive.

13:50 UTC — OBSERVED: remote still emits only connection/health lines. Own CLI PID123652 jstack shows RemoteBuildWorkspace.execute:152 polling. Exact installed manager0.0.85 bytecode reads submission runId, then polls getBuildRun without printing the ID. Dashboard /api/runs reports outOfDate=true, runMembershipReconciled=false and only QF12:54 WUI runs; neither is assumed to be ours. BuildTestCli README exposes a direct list command, but repository-declared artifact0.0.3 is not published (resolution reports not found).
INFER: silence does not prove submission failed; the pinned runner lacks run-id progress output. I am checking the direct read-only listing rather than inventing an id or resubmitting. No changes to service, dependency pins or runner scripts.

2026-10-05 13:52:44 UTC — RUNNING status for original request: finish validating https://github.com/CodexCoder21Organization/ScreenshotTestWui/pull/4 at requested head, nine remaining scenarios plus full suite, fix failures. OBSERVED:9/9 named scenarios pass; PR live OPEN/UNSTABLE, head unchanged4b6e1d629177f91a2e329db91094adcc8037ea3b. No source fixes; classifier exact reported names, action status preservation and image404/500 were exercised by public HTTP scenarios. No delegated agents. Full remote command alive; direct public-API read-only RPC listing running to recover run ID because old runner does not print it. No user decision pending. Landing sweep: no landing authorized, no enqueue/merge gates launched.
INFER: individual behavioral gates are closed; remote result is still unknown. Remaining path: obtain full-suite verdict/counts, update PR validation/OPEN list, final findings/head/process cleanup and exit. Transferable lesson: old runner silence can conceal a successfully submitted run; check its submission/poll code before treating absent output as absent execution.

13:53 UTC — OBSERVED: standalone read-only public API listBuildRuns request failed at service handler after its existing30-second limit, exit1. This was an operational read, not a test and not a resubmission. Own protocol/connection closed in finally; no diagnostic process remains. Full-suite CLI still polling. Complete failure stack:

```text
Exception in thread "main" foundation.url.resolver.UrlResolutionException: RPC request 'listBuildRuns' failed: INTERNAL_ERROR - Service handler timed out after 30 seconds without producing a result. The handler may be blocked or deadlocked.
	at foundation.url.resolver.PersistentRpcConnection.doSendRequest(UrlResolver.kt:29378)
	at foundation.url.resolver.PersistentRpcConnection.sendRawRequest(UrlResolver.kt:29036)
	at foundation.url.resolver.PersistentRpcConnection.sendRequestValue$foundation_url_resolver_sandbox(UrlResolver.kt:28997)
	at foundation.url.resolver.PersistentRpcConnection.sendRequest(UrlResolver.kt:28981)
	at foundation.url.resolver.PersistentRpcConnection.sendRequest(UrlResolver.kt:28971)
	at Qf2BuildRunRead.main(Qf2BuildRunRead.java:9)
```
INFER: direct listing cannot currently recover the run ID. Neither this failure nor stale dashboard results establish that the submitted suite failed or passed. Full suite remains OPEN.

13:56 UTC — OBSERVED: exact installed manager0.0.85 RemoteBuildWorkspace constructor bytecode has default poll interval10,000ms and polling limit1,800,000ms (30minutes), and its limit failure text includes runId and last status. No runner option changed.
INFER: if service polling remains responsive but nonterminal, the original command should exit near14:15 and disclose run ID; this existing runner limit precedes the requested40-minute fallback. I will act on its tracked exit, not change the limit or resubmit. Prepared PR body has9/9 durations and preserves supplied WHY/review mapping/screenshots/footer.
