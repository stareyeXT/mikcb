# Live Update Notification Renderers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate Android 16 Live Updates and Xiaomi Super Island into independent notification renderers while making both consume the same time-scheme-resolved course stage.

**Architecture:** `LiveUpdateService` remains the lifecycle and clock owner. It produces one immutable notification state, then selects either an Android Live Updates renderer or a Xiaomi Super Island renderer; neither renderer decides course timing. Flutter resolves effective section times before persisting the native scheduler snapshot so background stage transitions use the same template as foreground selection.

**Tech Stack:** Kotlin/Android notifications, Xiaomi HyperFocus Focus API, Flutter/Dart provider and MethodChannel, JUnit and Flutter tests.

## Global Constraints

- Preserve existing notification IDs, channels, actions, templates, diagnostic fields, and MethodChannel contracts.
- Do not add dependencies or change package IDs.
- Android Live Updates must not import Xiaomi Focus API types.
- Xiaomi Super Island must not request Android promoted ongoing display when a Xiaomi focus payload is attached.
- Course stage and timestamps are resolved before renderer selection and shared by both renderers.

---

### Task 1: Resolve Native Snapshot Times From the Effective Time Scheme

**Files:**
- Modify: `lib/providers/timetable/live_activity_controller.dart`
- Modify: `lib/services/miui_live_activities_service.dart`
- Test: `test/providers/timetable_provider_profiles_test.dart`

**Interfaces:**
- Consumes: `TimetableProvider._syncCourseWithEffectiveTimeScheme(Course, {DateTime? onDate})` and diagnostic-fixture clock preservation.
- Produces: `List<Course> resolvedScheduleCourses`, passed unchanged to `syncScheduleSnapshot` and included in its deduplication signature.

- [ ] **Step 1: Write a failing provider test**

Create an active time scheme whose section 1 is `17:30-18:10`, add a course carrying stale `08:00-08:45` clocks, call `updateLiveActivityForTesting()`, and assert the fake live service receives `17:30-18:10`.

- [ ] **Step 2: Run the focused Flutter test and verify it fails**

Run: `flutter test test/providers/timetable_provider_profiles_test.dart --plain-name "native live snapshot uses effective time scheme clocks"`

Expected: FAIL because the fake snapshot currently receives the course's stored clocks.

- [ ] **Step 3: Resolve snapshot courses before signature and MethodChannel sync**

In `_liveSyncScheduleSnapshot`, map display-name-resolved courses through the effective scheme for `now`, preserving diagnostic fixture clocks. Use that same list in both `snapshotSignature['courses']` and `syncScheduleSnapshot(courses: ...)`.

- [ ] **Step 4: Run the focused Flutter test and adjacent live-activity tests**

Run: `flutter test test/providers/timetable_provider_profiles_test.dart --plain-name "native live snapshot uses effective time scheme clocks"`

Run: `flutter test test/providers/timetable_provider_profiles_test.dart --plain-name "live"`

Expected: PASS.

### Task 2: Introduce a Shared Notification State

**Files:**
- Create: `android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateNotificationState.kt`
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateService.kt`
- Test: `android/app/src/test/kotlin/com/mutx163/qingyu/LiveUpdateNotificationStateTest.kt`

**Interfaces:**
- Produces: `internal enum class LiveUpdateNotificationStage`, `internal data class LiveUpdateNotificationState`, `internal data class LiveUpdateProgressState`, and `internal data class LiveUpdateRenderResult`.
- Consumes: only primitive values and Android-independent collections from the service.

- [ ] **Step 1: Add a failing stage-policy test**

Assert that `beforeClass` and `beforeEnd` request promotion, `duringClass` follows `promoteDuringClass`, and `duringClassStatusBar` always uses a standard notification.

- [ ] **Step 2: Run the focused Android unit test and verify it fails**

Run: `cd android && ./gradlew app:testDevDebugUnitTest --tests '*LiveUpdateNotificationStateTest'`

Expected: FAIL because the shared model does not exist.

- [ ] **Step 3: Add immutable shared state and centralize stage display policy**

The model exposes `shouldPromote`, `showStandardNotification`, `isUpcoming`, `isDuringClass`, and `isEndingSoon`. It carries resolved `startAtMillis/endAtMillis`, already-formatted course content, display switches, progress, and Xiaomi presentation settings.

- [ ] **Step 4: Make `LiveUpdateService` construct the model once per tick**

Replace string-condition duplication at the start of `buildNotification` with `LiveUpdateNotificationStage.fromWireValue(resolveStage(now))` and a `LiveUpdateNotificationState` instance.

- [ ] **Step 5: Run the focused Android unit test**

Run: `cd android && ./gradlew app:testDevDebugUnitTest --tests '*LiveUpdateNotificationStateTest'`

Expected: PASS.

### Task 3: Extract the Xiaomi Super Island Renderer

**Files:**
- Create: `android/app/src/main/kotlin/com/mutx163/qingyu/XiaomiSuperIslandNotificationRenderer.kt`
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateService.kt`
- Test: `android/app/src/test/kotlin/com/mutx163/qingyu/XiaomiSuperIslandNotificationRendererTest.kt`

**Interfaces:**
- Consumes: `XiaomiSuperIslandNotificationRenderer.render(base: Notification, state: LiveUpdateNotificationState): XiaomiSuperIslandRenderResult`.
- Produces: a notification containing either HyperFocus extras or legacy `miui.focus.param`, plus `isIslandReady` and diagnostic metadata.

- [ ] **Step 1: Add failing pure payload-selection tests**

Test `selectPayloadMode(isXiaomi=true, shouldPromote=true, statusBarOnly=false, engine="hyperFocusApi") == HYPER_FOCUS`, `engine="builtIn" == LEGACY_FOCUS`, and all non-Xiaomi/non-promoted/status-bar cases return `NONE`.

- [ ] **Step 2: Run the focused renderer test and verify it fails**

Run: `cd android && ./gradlew app:testDevDebugUnitTest --tests '*XiaomiSuperIslandNotificationRendererTest'`

Expected: FAIL because the selector and renderer do not exist.

- [ ] **Step 3: Move Xiaomi-only implementation into the renderer**

Move Xiaomi device detection, Focus API bundle generation, legacy focus JSON, island summary, island-label bitmap generation, custom/launcher icon rendering, Xiaomi timeout selection, and Xiaomi diagnostic readiness into the new file. All `com.xzakota.hyper.notification.*` imports leave `LiveUpdateService.kt`.

- [ ] **Step 4: Delegate from the service without changing payload fields**

The service supplies the resolved shared state and base notification. It attaches the renderer result and uses the returned readiness metadata in the existing debug snapshot.

- [ ] **Step 5: Run renderer and existing scheduler unit tests**

Run: `cd android && ./gradlew app:testDevDebugUnitTest --tests '*XiaomiSuperIslandNotificationRendererTest' --tests '*LiveUpdateSchedulerTest'`

Expected: PASS.

### Task 4: Extract the Android Live Updates Renderer

**Files:**
- Create: `android/app/src/main/kotlin/com/mutx163/qingyu/AndroidLiveUpdateNotificationRenderer.kt`
- Modify: `android/app/src/main/kotlin/com/mutx163/qingyu/LiveUpdateService.kt`
- Test: `android/app/src/test/kotlin/com/mutx163/qingyu/AndroidLiveUpdateNotificationRendererTest.kt`

**Interfaces:**
- Consumes: `AndroidLiveUpdateNotificationRenderer.render(state, actions): LiveUpdateRenderResult`.
- Produces: the ordinary/foreground notification, Android 16 promoted-ongoing request, and `Notification.ProgressStyle` without Xiaomi extras.

- [ ] **Step 1: Add failing renderer-routing tests**

Assert that Android promotion is requested only when the shared state promotes and Xiaomi payload mode is `NONE`; status-bar-only stages never request promotion.

- [ ] **Step 2: Run the focused renderer test and verify it fails**

Run: `cd android && ./gradlew app:testDevDebugUnitTest --tests '*AndroidLiveUpdateNotificationRendererTest'`

Expected: FAIL because routing is still embedded in the service.

- [ ] **Step 3: Move generic Android notification construction**

Move title/body/expanded text, `ProgressStyle`, critical text, promoted ongoing extras, base icons, notification intent, and progress diagnostics into the Android renderer. It must contain no Xiaomi imports or focus payload strings.

- [ ] **Step 4: Reduce the service to orchestration**

`LiveUpdateService` computes stage/countdown/progress, constructs the shared state, asks the Android renderer for the base notification, and conditionally decorates it through the Xiaomi renderer. Service lifecycle, scheduler validation, ticker cadence, and quick actions remain in the service.

- [ ] **Step 5: Run Android unit tests and compile the dev APK**

Run: `cd android && ./gradlew app:testDevDebugUnitTest app:assembleDevDebug`

Expected: PASS; `LiveUpdateService.kt` has no Xiaomi Focus API imports and the two renderer files compile independently.

### Task 5: Device Regression Test Across Notification Forms

**Files:**
- No source changes expected.

**Interfaces:**
- Consumes: the built dev APK and the existing connected Xiaomi device.
- Produces: screenshots/log evidence for Xiaomi Super Island plus `dumpsys notification` evidence that Android promotion flags and Xiaomi focus payloads are mutually exclusive.

- [ ] **Step 1: Install the dev debug APK without clearing app data**

Run: `adb install -r android/app/build/outputs/apk/dev/debug/app-dev-debug.apk`

Expected: success and existing debug timetable retained.

- [ ] **Step 2: Trigger the existing test course and inspect notification payload**

Run the app, let the scheduler reach the test course window, and inspect `adb shell dumpsys notification --noredact` plus relevant `logcat` entries.

- [ ] **Step 3: Verify Xiaomi form**

Expected: notification contains one Xiaomi focus payload, does not request simultaneous Android promoted ongoing display, FocusPlugin logs `onAuthSuccess`, and the island is visible in a screenshot.

- [ ] **Step 4: Run full focused verification**

Run: `flutter test test/providers/timetable_provider_profiles_test.dart`

Run: `cd android && ./gradlew app:testDevDebugUnitTest`

Expected: PASS.
