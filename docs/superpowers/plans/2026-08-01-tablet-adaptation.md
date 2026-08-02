# Tablet Adaptation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android app usable on tablet portrait and landscape layouts while preserving the current phone layout and interaction behavior.

**Architecture:** Let Android report both orientations by removing the activity portrait lock and explicitly keeping the app resizable. Reuse the existing `HyperosListView` as the shared responsive boundary: on wide surfaces it adds symmetric side insets so settings/forms stay readable and centered, while narrow surfaces retain the existing padding and widths.

**Tech Stack:** Flutter, Dart, Android manifest, existing HyperOS widgets, Flutter widget tests.

## Global Constraints

- Preserve phone layouts below the wide-content breakpoint.
- Do not add dependencies or change persisted data formats.
- Keep the timetable home surface full-width so seven-day landscape viewing benefits from tablet width.
- Preserve existing `HyperosListView` scroll physics, page storage keys, header inset behavior, and lazy item building.
- Validate both a narrow phone surface and a 1200dp-wide tablet surface in widget tests.

---

### Task 1: Enable Tablet Orientation and Resizing

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml:60-75`

**Interfaces:**
- Consumes: Android activity configuration already listing orientation and screen-size changes.
- Produces: A resizable `MainActivity` without a portrait-only orientation request.

- [ ] **Step 1: Remove the portrait lock**

Delete `android:screenOrientation="portrait"` from `MainActivity`. Keep the existing `android:configChanges` list so Flutter receives orientation, screen-size, and density changes without activity recreation storms.

- [ ] **Step 2: Mark the application as resizable**

Add `android:resizeableActivity="true"` to the `<application>` element. Do not change widget receiver declarations or intent filters.

- [ ] **Step 3: Check the manifest diff**

Run: `git diff --check -- android/app/src/main/AndroidManifest.xml`

Expected: no whitespace errors and only the orientation/resizability changes are present in this file.

### Task 2: Constrain Wide HyperOS Lists

**Files:**
- Modify: `lib/ui/hyperos/hyperos_tokens.dart`
- Modify: `lib/ui/hyperos/hyperos_page.dart` in `_HyperosListViewState`

**Interfaces:**
- Consumes: Existing `HyperosTokens.listPadding`, `HyperosListView` item builder/children modes, and `HyperosBlurredHeaderScope` inset.
- Produces: A private padding resolver that adds equal horizontal centering inset only when the local list width exceeds `HyperosTokens.settingsContentMaxWidth`.

- [ ] **Step 1: Add the shared wide-content limit**

Add this token beside the existing settings list dimensions:

```dart
static const settingsContentMaxWidth = 720.0;
```

- [ ] **Step 2: Preserve existing list padding on narrow surfaces**

In `_HyperosListViewState`, resolve the current list padding first. Under `LayoutBuilder`, calculate:

```dart
final extraSideInset = constraints.hasBoundedWidth
    ? ((constraints.maxWidth - HyperosTokens.settingsContentMaxWidth) / 2)
          .clamp(0.0, double.infinity)
    : 0.0;
final padding = base.copyWith(
  left: base.left + extraSideInset,
  right: base.right + extraSideInset,
);
```

Use that padding for the existing `ListView.builder`. Keep the existing `itemCount`, `itemBuilder`, key, physics, overscroll behavior, and header inset unchanged.

- [ ] **Step 3: Add phone/tablet layout regression coverage**

Extend `test/ui/hyperos/hyperos_subpage_navigation_test.dart` with a widget test that:

```dart
await tester.binding.setSurfaceSize(const Size(1200, 800));
await tester.pumpWidget(
  const MaterialApp(
    home: HyperosSubpage(
      title: Text('Settings'),
      child: HyperosListView(
        children: [SizedBox(key: ValueKey('tablet-row'), height: 40)],
      ),
    ),
  ),
);
expect(tester.getSize(find.byKey(const ValueKey('tablet-row'))).width,
    lessThanOrEqualTo(688));
```

Also set the surface to `const Size(360, 800)` in the same test, pump again, and assert the row width is greater than `300` to confirm the existing phone layout remains usable. Reset the surface in `addTearDown`.

- [ ] **Step 4: Run HyperOS widget tests**

Run: `flutter test test/ui/hyperos/hyperos_subpage_navigation_test.dart test/ui/hyperos/hyperos_new_components_test.dart`

Expected: all tests pass, including lazy list construction and navigation behavior.

### Task 3: Full Verification

**Files:**
- Modify: none

- [ ] **Step 1: Format changed Dart files**

Run: `dart format lib/ui/hyperos/hyperos_page.dart lib/ui/hyperos/hyperos_tokens.dart test/ui/hyperos/hyperos_subpage_navigation_test.dart`

- [ ] **Step 2: Run static analysis**

Run: `flutter analyze`

Expected: `No issues found!`.

- [ ] **Step 3: Run the full test suite**

Run: `flutter test`

Expected: no new failures. The known existing live-activity failure may remain in the user-modified provider test and must be reported separately.

- [ ] **Step 4: Review the final diff**

Run: `git diff --check` and `git diff -- android/app/src/main/AndroidManifest.xml lib/ui/hyperos/hyperos_page.dart lib/ui/hyperos/hyperos_tokens.dart test/ui/hyperos/hyperos_subpage_navigation_test.dart`

Expected: only tablet orientation, wide-list centering, and its regression test are changed.
