





## Permissions and APIs Audit

### Hint: Deducing Core Functionality

Since you must determine if certain permissions are justified by the app's "core
purpose", use these fast heuristics:
1. **The "Broken" Test**: Is the feature essential to the app's primary purpose?
   If the app would still be functional and useful without the feature, it is
   NOT core functionality.
2. **Manifest Intent**: Review `AndroidManifest.xml`. The name of the `LAUNCHER`
   Activity and specialized `<intent-filter>` declarations (like default SMS
   handlers) strongly indicate the app's main purpose.
3. **Naming**: The package name (`{{PACKAGE_NAME}}`) and app label
   (`app_name`) often describe the app's purpose explicitly.
4. **Execution Context**: Usage in classes like `BackupManager` suggest core
   functionality, whereas usage in `AdHelper`, `CrashReporter`, or
   `AnalyticsManager` indicates secondary features.
5. **Mandatory Rule**: Secondary features like **advertising, analytics, or
   social sharing never justify** restricted permissions like Background
   Location, All Files Access, or Broad Media Access.

---

### Policies to Verify

#### Accessibility API Policy (Policy ID: accessibility_api_policy)

- **Goal**: Verify the configuration and justification of Accessibility
  Services.
- **The Policy Spirit**: Accessibility APIs provide deep system access to assist
  users with disabilities. Using these APIs for non-accessibility tasks (like UI
  automation, screen scraping, background monitoring, or ad blocking) is
  strictly prohibited by Google Play and causes immediate app rejection.
- **Evidence**:
  - `app/src/main/java/de/ithandwerkstuttgart/nibra/MainActivity.kt (Pattern: AccessibilityService)`
- `app/src/main/java/de/ithandwerkstuttgart/nibra/dienst/DiktatBedienungshilfenDienst.kt (Pattern: AccessibilityService)`
  **Relevant Permissions Requested**:
  - `android.permission.BIND_ACCESSIBILITY_SERVICE`
- **Common Evaluation Matrix**:
  | Service Configuration | Real Code Usage | Justified? | Severity | Direct Actionable Recommendation |
  | :--- | :--- | :--- | :--- | :--- |
  | **`isAccessibilityTool="true"`** | Code performs screen scraping, ad blocking, or automated click routines for standard users. | No (Violation) | `CRITICAL` | **High Risk of Play Store Rejection**: Remove accessibility helper configs. Migrate UI automation to standard Android testing libraries. |
  | **`isAccessibilityTool` false/missing** | Code implements an accessibility listener but is not a dedicated helper app. | No prominent disclosure | `CRITICAL` | **High Risk of Play Store Rejection**: Because you request accessibility permission for a standard utility, you MUST implement an in-app **Prominent Disclosure and Affirmative Consent screen** before asking the user to enable the service, otherwise your app will be rejected. |

- **Domain-Specific Heuristics (Strictly Bounded)**:
  Trace the ingestion of accessibility events:
  1. **Telemetry Siphoning**: Scan the Accessibility Service methods
     (`onAccessibilityEvent`). If the service captures on-screen text,
     notifications, or keystrokes and routes them to local caches, shared
     preferences, or off-device network logging endpoints, flag a `CRITICAL`
     violation under the Accessibility API policy.

#### Audio Recording Policy (Policy ID: audio_recording_policy)

- **Goal**: Evaluate if the app's core functionality justifies broad access to
  audio recording.
- **The Policy Spirit**: Unprompted audio recording is a severe privacy breach.
  Apps should request microphone access strictly for user-visible, time-bounded
  actions. Target SDK 34+ encourages using the system-managed Microphone Button
  for temporary needs.
- **Evidence**:
  - `app/src/forschung/java/de/ithandwerkstuttgart/nibra/forschung/Absichtsversuch.kt (Pattern: SpeechRecognizer)`
- `app/src/forschung/java/de/ithandwerkstuttgart/nibra/forschung/Biasingversuch.kt (Pattern: SpeechRecognizer)`
- `app/src/forschung/java/de/ithandwerkstuttgart/nibra/forschung/Dauerversuch.kt (Pattern: SpeechRecognizer)`
  **Relevant Permissions Requested**:
  - `android.permission.RECORD_AUDIO`
  
- **Common Evaluation Matrix**:
  | Target SDK | Audio Recording Trigger Context | Severity | Direct Actionable Recommendation |
  | :--- | :--- | :--- | :--- |
  | **34 or higher** | App requests broad `RECORD_AUDIO` permission for occasional, user-initiated vocal input or short recording. | `IMPORTANT` | Migrate to the **Android Microphone Button** API to process temporary audio securely. |
  | **Any** | App captures audio for secondary features (analytics, user-agent details, etc.) without explicit user control. | `IMPORTANT` | Remove microphone permissions. For general searches, integrate standard Android Speech Recognizer intents. |

- **Domain-Specific Heuristics (Strictly Bounded)**:
  Verify recording indicators and threads:
  1. **Continuous Capture**: Scan for active recorder threads (`AudioRecord` or
     `MediaRecorder`). If recording loops can be active when the app is
     minimized or without a visible user-facing indicator, flag this as a
     `CRITICAL` violation.

## Output schema

Save final JSON output to `/home/belkis/Loqui/.scratch/play_policy_insights_800fc5a5-4bdb-43f7-8d55-c095e29a8e95/worker_{{GOAL_NAME}}.json`.

```json
{
  "domain": "Permissions and APIs",
  "findings": [
    {
      "policy_id": "STRING_VALUE (The exact Policy ID, e.g., photo_video_access_policy)",
      "issue_summary": "STRING_VALUE",
      "severity": "CRITICAL | IMPORTANT | SUGGESTION",
      "files_involved": ["STRING_VALUE"],
      "evidence": "STRING_VALUE",
      "recommendation": "STRING_VALUE"
    }
  ]
}
```

# Execution Mandates

### Technical Rules

1.  **Absolute Paths Only**: Always resolve and use absolute paths.
2.  **Containment**: Write all artifacts strictly within `/home/belkis/Loqui/.scratch/play_policy_insights_800fc5a5-4bdb-43f7-8d55-c095e29a8e95`.
3.  **Fail-fast**: If any required input file is missing, stop immediately and
    report the failure.

### Surgical Input Protocol & Efficient Search (MANDATORY)

-   **Direct Evidence First**: Prioritize files listed in the **Context &
    Evidence** sections. Use the provided file/line evidence (e.g., from Data
    Sources or Sinks) to jump directly to the relevant code. Do not perform
    broad workspace searches if these surgical starting points are available.
-   **Path Filtering Over File Crawling**: Locate target files by name, path, or
    extension *first* using directory/file listing tools before performing any
    text/content-based searches. Restrict searches and file reads strictly to
    the target `/home/belkis/Loqui`.
-   **Strict Exclusions (The Noise Wall)**: Configure search, glob, and find
    tools to ignore build, cache, dependencies, and testing folders. You MUST
    exclude matches from: `**/build/**`, `**/.gradle/**`, `**/.scratch/**`,
    `**/androidTest/**`, `**/test/**`, `**/node_modules/**`.
-   **Targeted Extensions**: Restrict content searches and file reads strictly
    to source and configuration files: `.java`, `.kt`, `.xml`, `.gradle`, `.kts`
    (and `.js`, `.ts`, `.jsx`, `.tsx`, `.dart` if a hybrid/cross-platform
    environment is analyzed). Never search or read inside compiled `.class`
    files, binary resources, or output assets.
-   **Surgical Queries & Limiters**: Use highly specific search patterns (e.g.,
    search for `getLastKnownLocation` or `deleteAccount` instead of general
    words like `location` or `delete`). If search tools support limits or
    pagination, cap results at a maximum of 50 matches. Do not load unlimited
    search results into your context window.
-   **Parallel Reading Required (Turn Efficiency)**: You are operating under a
    strict maximum turn limit. To prevent timeouts, you MUST request to read
    multiple target files concurrently in a single response. Do not read the
    evidence files sequentially one-by-one. Issue all of your file-reading tool
    calls simultaneously whenever possible.

### Evidentiary Standard & Guardrails (CRITICAL)

To prevent over-auditing, false positives, and speculative "prosecution" of
compliant code during extrapolation:

1.  **Presumption of Compliance**: Treat code as compliant unless there is
    *definitive, visible evidence* in the provided files of a policy violation.
    If code is ambiguous, or if network/database logic is hidden behind
    abstractions (e.g., calling an interface or repository method like
    `clearSession()`), you must assume standard compliant behavior. Do NOT guess
    or speculate about what happens behind interfaces.
2.  **Benefit of the Doubt**: When compliance cannot be strictly verified due to
    code abstractions or missing source file contexts, you must downgrade your
    finding:
    -   Never flag a `🔴 Critical` or `🟡 Important` finding based on suspicion or
        lack of context.
    -   Instead, output a `🔵 Suggestion` (informational) to advise the developer
        on what to double-check in their backend or configuration.
3.  **Exclusion of Local State**: Local-only processing (e.g., caching theme
    settings, user-selected visual configurations, or on-device-only database
    operations) is explicitly exempt from Data Safety collection or Account
    Deletion mandates.
4.  **Concrete Attributions**: Every `🔴 Critical` or `🟡 Important` finding must
    cite the exact file, line number, or configuration block containing the
    direct violation. If you cannot cite the exact line of code containing the
    violation, you cannot flag it as a violation.
5.  **Empty-List Discipline**: If no policy violations, discrepancies, or review
    items are identified during your audit, you MUST represent this as an empty
    array `[]` for that field (e.g., `"findings": []`, `"verified_findings":
    []`, or `"manual_verification_required": []`). **DO NOT** populate arrays
    with "dummy" objects, placeholder strings, or `"N/A"` / `"None"` values.
6.  **Heuristics & Extrapolation Boundaries**: Whenever applying specific
    heuristics defined in your goal (e.g., searching for implicit logger leaks
    or
    SDK siphoning), you must strictly bound them to the provided evidence and
    their immediate callers. You are strictly forbidden from initiating broad,
    unbounded searches for custom paths or variables across the wider codebase.
    Base your extrapolation only within the specific files already provided to
    you in the prompt.

### Finalization & Output Mandates (CRITICAL)

-   **Iterative Saving**: If your investigation requires multiple steps, save
    partial or intermediate JSON states to disk as you progress. Do not hold all
    data in memory until the very end to prevent data loss upon interruption.
-   **Strict File Output (NO TRIPLE BACKTICKS)**: You MUST save your final JSON
    output to disk at the exact path specified in the goal schema using your
    file-writing capabilities.
    -   **CRITICAL: The content written to the file MUST be pure, raw JSON. DO
        NOT wrap the contents inside the JSON file with Markdown code blocks
        (such as triple backticks `json ...`). Writing markdown blocks into the
        file makes the JSON unparseable by the compiler.**
-   **NO Chat Summaries**: **MANDATORY: DO NOT summarize your findings, explain
    your reasoning, or output JSON in your final chat response.** Your chat
    output wastes context and is ignored by the orchestrator.
-   **Verification Before Termination**: You MUST only terminate and return the
    "SUCCESS" string *after* you have explicitly verified that your JSON file
    successfully wrote to disk and contains valid JSON (e.g., by reading the
    file back or checking the directory contents).
-   **Final response**: Your final response MUST be exactly the word: "SUCCESS"
    and nothing else.
