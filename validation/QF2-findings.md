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

13:59 UTC — COURSE-CHANGING OBSERVED: bounded listBuildRunsPaginated(offset0,limit100) succeeded. Job full-suite run recovered immediately on response: b4c1371b, label ScreenshotTestWui, submitted2026-10-05 13:44:59.258 UTC (matches own13:44:53 command), PENDING,70 scenarios,0passed/0failed. https://buildtest.kotlin.build/run?id=b4c1371b . Whole-list read had failed; paginated read avoided that failure without changing a timeout.

Also OBSERVED: canonical CI run46141aba at exact requested4b6e1d629177f91a2e329db91094adcc8037ea3b is COMPLETED,70passed/0failed/70total, submitted13:31:39.903 and completed13:48:25.627. Its notes name the full PR/commit URLs and say one shard did not finish provisioning, surviving shards completed queued tests. https://buildtest.kotlin.build/run?id=46141aba . Informational route run16ab88c7 at same head is PENDING and is not used as the required gate. The previously OPEN check-start premise is refuted by this live completed CI evidence.
INFER: final-source full-suite proof already exists in canonical CI; own submitted suite remains pending and is still watched separately. No run has been rerun or deleted.

## FINAL — 2026-10-05 14:06:29 UTC

Final source head: 4b6e1d629177f91a2e329db91094adcc8037ea3b
Commit: https://github.com/CodexCoder21Organization/ScreenshotTestWui/commit/4b6e1d629177f91a2e329db91094adcc8037ea3b
PR: https://github.com/CodexCoder21Organization/ScreenshotTestWui/pull/4 (live OPEN; body PATCHed and re-read/verified, including required footer).

The complete nine-scenario PASS1/1 table and durations are above. Every named per-test XML record matches the requested scenario. No testcase failed and no source change was needed.

| Full-suite run | Attribution | Result |
|---|---|---|
| https://buildtest.kotlin.build/run?id=46141aba | Required CI explicitly names exact final head 4b6e1d629177f91a2e329db91094adcc8037ea3b | COMPLETED:70passed/0failed/70total; GitHub completed/success, All tests passing(70/70) |
| https://buildtest.kotlin.build/run?id=b4c1371b | QF2 prescribed command submitted once at13:44:53, service start13:44:59.258UTC | Last observed PENDING:0passed/0failed/70total (in-flight counts, NOT final); client exited1 on getBuildRun RPC30-second failure; terminal verdict OPEN |

Brief item states:
1. DONE: all9 final-source individual selectors passed; complete durations/results recorded above.
2. DONE: no failing testcase required a fix. No assertion, timeout, count, sleep, skip or pin change.
3. DONE for final-source validation: exact-head required full suite70/70 is proven by live completed CI run46141aba and GitHub check metadata. The previous OPEN premise that final-source full-suite validation/check start is absent is REFUTED by this evidence. QF2 separately submitted the prescribed full command once and recovered its ID at the first successful bounded page; that submission has no observed final verdict, is recorded OPEN and is never claimed70/70. The40-minute fallback threshold14:24:53 is after the50-minute deadline14:22:45, and the explicit stop condition says not to perform further verification after the gate exists; no local fallback batches launched.
4. DONE: source unchanged; only PR body updated with9results, exact-head full counts, resolved prior full/check-start entries and separate-submission OPEN. Supplied WHY, review mapping, screenshots and required footer retained. No PR-branch push needed. Evidence-only commits on wip/qf2-pr4-validation were pushed and ls-remote checked at each milestone.
5. DONE: final head and complete results in FINDINGS; clean worktree; every own local test/probe process exited and watcher stopped (tracked exit130). ps -eo pid,ppid,args -ww captured in /tmp/qf2-processes-final.txt; no own qf2-targets, Qf2BuildRunRead or b4c1371b watcher remains. Remote run itself was not cancelled/deleted/restarted/resubmitted.

OPEN execution record: QF2 remote runb4c1371b has no confirmed terminal verdict. Final-source validation itself is complete via nine individual local passes and required canonical CI70/70 on the unchanged requested head.

Observed monitoring limitations: installed runner does not print run ID; whole-list RPC hit service30-second handler limit (full stack above); stale dashboard made watchman report not-found even though bounded RPC proved the run exists; repository-declared BuildTestCli0.0.3 is unpublished. The bounded public RPC page was the successful workaround. report-challenge auto-merges to PlanRepository, so it was excluded by this job's explicit no-merging rule.

Documents read: project README; PR body/review mapping/OPEN; prior QF findings including final section and cache correction; Testing architecture in full; exact installed manager0.0.85 bytecode; relevant BuildTestCli README/list implementation, BuildTestApi listing/lifecycle sections and BuildTestServerService paginated-route implementation.

No source/test/README/pin edits. Resolver0.0.1261/protocol0.0.532 retained. No CI rerun, merge, enqueue, artifact publication, deployment or service restart. No further verification started after the exact-head full gate was proven. Status loop was not scheduled under this one-job exit brief; local watcher stopped.
