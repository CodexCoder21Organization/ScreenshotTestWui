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
