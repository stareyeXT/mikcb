# Timetable Performance Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce unnecessary work and rebuild scope on the timetable home screen without changing timetable behavior or persistence formats.

**Architecture:** Keep the existing `TimetableScreen` state model and provider APIs. Replace the one-second whole-screen `setState` tick with a local `ValueNotifier` rebuild, and pass one conflict map plus one course list through each week-grid build so all seven day columns reuse the same derived data.

**Tech Stack:** Flutter, Dart, `provider`, existing widget tests.

## Global Constraints

- Do not change timetable data models, serialized keys, or persistence behavior.
- Do not add dependencies or introduce a new abstraction outside `TimetableScreen`.
- Preserve existing week/day navigation and current-course refresh behavior.
- Run focused timetable tests, `flutter analyze`, and the full `flutter test` suite after implementation.

---

### Task 1: Localize Day-View Progress Rebuilds

**Files:**
- Modify: `lib/screens/timetable_screen.dart` near `_dayAgendaProgressTimer`, `initState`, `dispose`, and `_buildWeekPager`
- Test: existing `test/widgets/timetable_day_view_test.dart`

**Interfaces:**
- Consumes: existing day-view state and `_buildAnchoredDayViewOverlay`.
- Produces: a private integer `ValueNotifier` used only to refresh the day-view overlay once per second.

- [ ] **Step 1: Add a private progress tick notifier**

```dart
late final ValueNotifier<int> _dayAgendaProgressTick;
```

Initialize it to zero before starting the timer, increment it from the timer callback, and dispose it with the other notifiers.

- [ ] **Step 2: Replace timer-driven whole-screen `setState`**

Change the timer callback to increment `_dayAgendaProgressTick.value` only when the state is mounted and day view is active. Keep the existing one-second cadence and inactive-view guard.

- [ ] **Step 3: Wrap only the day-view overlay in `ValueListenableBuilder`**

In `_buildWeekPager`, keep the overlay condition in the parent build, but build `_buildAnchoredDayViewOverlay` inside a `ValueListenableBuilder<int>` keyed to `_dayAgendaProgressTick`. The builder must return the same `Positioned.fill` geometry and pass through the notifier child where possible.

- [ ] **Step 4: Run focused day-view tests**

Run: `flutter test test/widgets/timetable_day_view_test.dart`

Expected: all day-view tests pass; current-course and ongoing-status assertions remain unchanged.

### Task 2: Reuse Week-Grid Derived Data

**Files:**
- Modify: `lib/screens/timetable_screen.dart` in `_buildTimetableGrid` and `_buildHomeDayDisplayItems`
- Test: existing `test/widgets/timetable_conflict_badge_test.dart` and `test/widgets/timetable_day_view_test.dart`

**Interfaces:**
- Consumes: `TimetableProvider.courseConflictMapForWeek`, `TimetableProvider.courses`, and existing display-item builders.
- Produces: `_buildHomeDayDisplayItems` accepts an optional precomputed conflict map; week-grid callers supply one map for all visible days while day-view callers retain correct per-week computation.

- [ ] **Step 1: Capture courses and conflict map once per week grid**

At the start of `_buildTimetableGrid`, assign:

```dart
final courses = provider.courses;
final conflictMap = provider.courseConflictMapForWeek(week);
```

Use `courses` for all day filtering and pass `conflictMap` into each `_buildHomeDayDisplayItems` call.

- [ ] **Step 2: Add an optional conflict-map parameter to the display builder**

Add `Map<String, List<Course>>? conflictMap` to `_buildHomeDayDisplayItems`. Resolve it once inside the method with `conflictMap ?? provider.courseConflictMapForWeek(week)` so existing day-view callers remain behaviorally compatible.

- [ ] **Step 3: Run focused conflict and day-view tests**

Run: `flutter test test/widgets/timetable_conflict_badge_test.dart test/widgets/timetable_day_view_test.dart`

Expected: conflict badge visibility, overlapping-course selection, day navigation, and current-course styling pass.

### Task 3: Regression Verification

**Files:**
- Modify: none

- [ ] **Step 1: Run static analysis**

Run: `flutter analyze`

Expected: `No issues found!`.

- [ ] **Step 2: Run the full test suite**

Run: `flutter test`

Expected: no new failures. Record any pre-existing failure separately instead of changing unrelated behavior.

- [ ] **Step 3: Review the final diff**

Run: `git diff --check` and `git diff -- lib/screens/timetable_screen.dart`

Expected: only the two requested performance changes and no whitespace errors.
