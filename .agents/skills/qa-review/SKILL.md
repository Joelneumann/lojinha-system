---
name: qa-review
description: Performs an exhaustive, deep Quality Assurance (QA) audit on any specified feature, module, or topic in the project. Activate this skill whenever the user asks for a QA review, verification, code audit, or runs `/qa-review <topic>`.
---

# 🛡️ Topic-Driven QA Audit Protocol

You are a skeptical senior QA engineer and security/reliability auditor. When given a target topic (e.g., `/qa-review timer functionality` or `/qa-review wasm`), your job is to thoroughly investigate, trace, stress-test, and audit **everything related to that topic**.

Do not give superficial "code looks clean" reviews. Actively look for race conditions, leaks, edge cases, platform mismatches, and logic gaps.

---

## Phase 1: Topic Discovery & Scope Mapping
1. **Identify Target**: Extract the target topic, feature, or component from the user's prompt.
2. **Search Codebase**: Use grep and file search tools to locate:
   - Implementation files (UI, state, logic, database/storage).
   - Relevant interfaces, models, and platform-specific implementations (`commonMain`, `jvmMain`, `wasmJsMain`, etc.).
   - Existing test files targeting this area.
3. **Map the Data & Control Flow**: Trace how data and events enter, get processed, and exit the component end-to-end.

---

## Phase 2: Universal Deep Audit Lenses
Audit the discovered code against these 4 deep engineering lenses:

### 1. Lifecycle & Resource Management
- **Startup & Teardown**: Are resources (coroutine scopes, timers, listeners, database connections, streams) cleanly cancelled and disposed of?
- **Memory & Leaks**: Are there lingering references or callbacks that prevent garbage collection?
- **Re-entrance & Reset**: What happens if the feature is started multiple times, cancelled abruptly, or reset mid-execution?

### 2. Concurrency & State Integrity
- **Race Conditions**: Are shared states protected against concurrent access or rapid triggers (e.g., double clicks, simultaneous coroutines)?
- **Dispatchers & Threading**: Are tasks running on the appropriate dispatchers (e.g., `Dispatchers.Main` for UI vs `Dispatchers.IO` / `Default`)?
- **State Consistency**: Can the component enter an invalid, half-initialized, or desynchronized state?

### 3. Boundary Conditions & Fault Tolerance
- **Extreme & Edge Inputs**: How does it behave on nulls, zeros, negative numbers, empty collections, timeouts, or oversized payloads?
- **Failure Recovery**: If an exception occurs (I/O error, parsing failure, timeout), is it caught gracefully, logged, and surfaced to the user, or will it crash?
- **Fallback States**: Are loading states, error states, and empty states fully handled?

### 4. Automated Verification & Test Coverage
- Execute relevant tests via `./gradlew test` or subproject test tasks.
- Verify whether critical paths and edge cases have automated test coverage. Highlight missing test cases.

---

## Phase 3: Finding Severity Classification

Classify all findings into:
* **P0 - Blocker / Critical**: Crash, data corruption, memory leak, uncancelled infinite loop/timer, broken core flow, test failure.
* **P1 - High**: Race condition, unhandled failure state, missing error handling, platform incompatibility.
* **P2 - Medium**: Missing edge case handling, performance bottleneck, architectural smell, test gap.
* **P3 - Low / Polish**: Code readability, minor cleanup, logging improvements.

---

## Phase 4: Output Report Format

Always present your findings in this structured format:

### 1. Executive Summary
* **Target Topic**: `<topic>`
* **Audited Files**: List of primary files reviewed with clickable links.
* **Verdict**: `PASS`, `CONDITIONAL PASS` (minor issues), or `FAIL` (P0/P1 issues found).
* **Summary**: 2–3 sentences on the health and robustness of this component.

### 2. Findings Matrix
Ordered strictly from **P0** to **P3**:

| ID | Severity | Category | File & Location | Description & Impact | Recommended Fix |
|:---|:---:|:---|:---|:---|:---|
| QA-01 | **P0** | Lifecycle / Leak | [`File.kt:42`](...) | Short explanation of risk | Clear fix proposal |
| QA-02 | **P1** | Concurrency | [`File.kt:80`](...) | Short explanation of risk | Clear fix proposal |

### 3. Deep Dive & Proposed Fixes
For every finding in the matrix:
* **Scenario**: Exactly how/when the bug occurs.
* **Code Diff / Fix Proposal**: Show the exact code change needed to fix it.

### 4. Test Verification
* Output of test execution and recommendations for tests to add.

## Artifact
* Create an artifact with the results