import 'package:flutter_test/flutter_test.dart';
import 'package:university_timetable/models/course.dart';
import 'package:university_timetable/services/html_import_merge.dart';

Course _html({
  required String id,
  required String name,
  required int dayOfWeek,
  required int startSection,
  required int endSection,
  required int startWeek,
  required int endWeek,
  List<int>? customWeeks,
}) {
  return Course(
    id: id,
    name: name,
    teacher: 'T',
    location: 'L',
    dayOfWeek: dayOfWeek,
    startSection: startSection,
    endSection: endSection,
    startTime: '08:00',
    endTime: '09:40',
    startWeek: startWeek,
    endWeek: endWeek,
    customWeeks: customWeeks,
  );
}

void main() {
  group('mergeHtmlImportCourses', () {
    test('keeps other weeks and non-html courses when refreshing one week', () {
      final existing = [
        // non-html course must survive
        _html(
          id: 'manual-1',
          name: 'Local',
          dayOfWeek: 1,
          startSection: 1,
          endSection: 2,
          startWeek: 1,
          endWeek: 16,
        ),
        // week-5 html course (will be replaced)
        _html(
          id: 'html-a',
          name: 'Math',
          dayOfWeek: 1,
          startSection: 1,
          endSection: 2,
          startWeek: 5,
          endWeek: 5,
          customWeeks: const [5],
        ),
        // week-7 html course must NOT disappear
        _html(
          id: 'html-b',
          name: 'Phys',
          dayOfWeek: 3,
          startSection: 3,
          endSection: 4,
          startWeek: 7,
          endWeek: 7,
          customWeeks: const [7],
        ),
      ];
      final fetched = [
        _html(
          id: 'html-c',
          name: 'Math',
          dayOfWeek: 1,
          startSection: 1,
          endSection: 2,
          startWeek: 5,
          endWeek: 5,
          customWeeks: const [5],
        ),
      ];

      final merged = mergeHtmlImportCourses(
        existingCourses: existing,
        fetchedCourses: fetched,
        refreshWeek: 5,
        firstCourseWeek: 1,
      );

      final names = merged.map((c) => c.name).toList();
      expect(names, contains('Local')); // non-html preserved
      expect(names, contains('Phys')); // week-7 preserved
      expect(names, contains('Math')); // week-5 refreshed
      // exactly one Math (old html-a replaced, not duplicated)
      expect(names.where((n) => n == 'Math').length, 1);
      // week-7 html id retained (it was kept, not re-fetched)
      expect(merged.any((c) => c.id == 'html-b'), isTrue);
    });

    test('does not duplicate when source returns full recurring timetable', () {
      final existing = [
        _html(
          id: 'html-old',
          name: 'Math',
          dayOfWeek: 1,
          startSection: 1,
          endSection: 2,
          startWeek: 1,
          endWeek: 16,
          customWeeks: const [1, 7],
        ),
      ];
      // full-timetable source returns the same course spanning many weeks
      final fetched = [
        _html(
          id: 'html-new',
          name: 'Math',
          dayOfWeek: 1,
          startSection: 1,
          endSection: 2,
          startWeek: 1,
          endWeek: 16,
          customWeeks: const [1, 7],
        ),
      ];

      final merged = mergeHtmlImportCourses(
        existingCourses: existing,
        fetchedCourses: fetched,
        refreshWeek: 1,
        firstCourseWeek: 1,
      );

      // old duplicate removed, single fresh entry remains
      expect(merged.where((c) => c.name == 'Math').length, 1);
      expect(merged.any((c) => c.id == 'html-new'), isTrue);
    });

    test('shifts fetched 教务 weeks into semester space', () {
      final existing = <Course>[];
      // 教务 week 3 maps to semester week 5 when firstCourseWeek == 3
      final fetched = [
        _html(
          id: 'html-x',
          name: 'Chem',
          dayOfWeek: 2,
          startSection: 1,
          endSection: 2,
          startWeek: 3,
          endWeek: 3,
          customWeeks: const [3],
        ),
      ];

      final merged = mergeHtmlImportCourses(
        existingCourses: existing,
        fetchedCourses: fetched,
        refreshWeek: 5,
        firstCourseWeek: 3,
      );

      final chem = merged.singleWhere((c) => c.name == 'Chem');
      expect(chem.customWeeks, const [5]);
      expect(chem.isActiveInWeek(5), isTrue);
      expect(chem.isActiveInWeek(3), isFalse);
    });
  });
}
