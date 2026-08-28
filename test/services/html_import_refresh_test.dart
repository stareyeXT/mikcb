import 'package:flutter_test/flutter_test.dart';
import 'package:university_timetable/models/course.dart';
import 'package:university_timetable/services/html_import_refresh.dart';
import 'package:university_timetable/services/html_import_service.dart';

class _FakeHtmlImportService extends HtmlImportService {
  _FakeHtmlImportService(this._courses);
  final List<Course> _courses;

  @override
  Future<List<Course>> fetchWeekCourses(
    String baseUrl,
    DateTime weekStartDate, {
    void Function(HtmlWeekFetchProgress progress)? onProgress,
    Duration timeout = const Duration(seconds: 30),
  }) async =>
      _courses;
}

Course _html({
  required String id,
  required String name,
  required int dayOfWeek,
  required int startSection,
  required int endSection,
  required int startWeek,
  required int endWeek,
  List<int>? customWeeks,
  String location = 'L',
}) {
  return Course(
    id: id,
    name: name,
    teacher: 'T',
    location: location,
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
  final weekStartDate = DateTime(2026, 3, 2); // 某周一

  group('refreshHtmlImportWeek', () {
    test('空抓取：保留原课程，changedCount 为 0', () async {
      final existing = [
        _html(
          id: 'manual-1',
          name: 'Local',
          dayOfWeek: 1,
          startSection: 1,
          endSection: 2,
          startWeek: 1,
          endWeek: 16,
        ),
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
      ];

      final result = await refreshHtmlImportWeek(
        url: 'https://example.com',
        weekStartDate: weekStartDate,
        week: 5,
        firstCourseWeek: 1,
        existingCourses: existing,
        service: _FakeHtmlImportService(const []),
      );

      expect(result.fetchedCount, 0);
      expect(result.changedCount, 0);
      expect(result.courses.map((c) => c.id), containsAll(['manual-1', 'html-a']));
    });

    test('刷新周内容变化：计入 changedCount 并更新字段', () async {
      final existing = [
        _html(
          id: 'html-a',
          name: 'Math',
          dayOfWeek: 1,
          startSection: 1,
          endSection: 2,
          startWeek: 5,
          endWeek: 5,
          customWeeks: const [5],
          location: 'OldRoom',
        ),
      ];
      final fetched = [
        _html(
          id: 'html-new',
          name: 'Math',
          dayOfWeek: 1,
          startSection: 1,
          endSection: 2,
          startWeek: 5,
          endWeek: 5,
          customWeeks: const [5],
          location: 'NewRoom',
        ),
      ];

      final result = await refreshHtmlImportWeek(
        url: 'https://example.com',
        weekStartDate: weekStartDate,
        week: 5,
        firstCourseWeek: 1,
        existingCourses: existing,
        service: _FakeHtmlImportService(fetched),
      );

      expect(result.changedCount, 2);
      final math = result.courses.singleWhere((c) => c.name == 'Math');
      expect(math.location, 'NewRoom');
    });

    test('周隔离：其他周 HTML 课程不被覆盖', () async {
      final existing = [
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
          id: 'html-new',
          name: 'Math',
          dayOfWeek: 1,
          startSection: 1,
          endSection: 2,
          startWeek: 5,
          endWeek: 5,
          customWeeks: const [5],
        ),
      ];

      final result = await refreshHtmlImportWeek(
        url: 'https://example.com',
        weekStartDate: weekStartDate,
        week: 5,
        firstCourseWeek: 1,
        existingCourses: existing,
        service: _FakeHtmlImportService(fetched),
      );

      final names = result.courses.map((c) => c.name).toList();
      expect(names, contains('Phys')); // 第 7 周保留
      expect(names, contains('Math')); // 第 5 周新增
      expect(result.changedCount, 1);
    });

    test('变化判定只统计刷新周：非刷新周变化不计入', () async {
      final existing = [
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
      // 仅刷第 5 周，返回与现有第 5 周一致 -> changedCount 应为 0
      final fetched = [
        _html(
          id: 'html-new',
          name: 'Math',
          dayOfWeek: 1,
          startSection: 1,
          endSection: 2,
          startWeek: 5,
          endWeek: 5,
          customWeeks: const [5],
        ),
      ];

      final result = await refreshHtmlImportWeek(
        url: 'https://example.com',
        weekStartDate: weekStartDate,
        week: 5,
        firstCourseWeek: 1,
        existingCourses: existing,
        service: _FakeHtmlImportService(fetched),
      );

      expect(result.changedCount, 0);
    });
  });

  group('mergeHtmlImportWeek (同步合并)', () {
    test('空抓取直接保留原课程，不丢课', () {
      final existing = [
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
      ];
      final result = mergeHtmlImportWeek(
        week: 5,
        firstCourseWeek: 1,
        existingCourses: existing,
        fetchedCourses: const [],
      );
      expect(result.fetchedCount, 0);
      expect(result.changedCount, 0);
      expect(result.courses.map((c) => c.id), contains('html-a'));
    });

    test('变化判定只统计刷新周：其它周课程不影响', () {
      final existing = [
        _html(
          id: 'html-a',
          name: 'Math',
          dayOfWeek: 1,
          startSection: 1,
          endSection: 2,
          startWeek: 5,
          endWeek: 5,
          customWeeks: const [5],
          location: 'OldRoom',
        ),
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
          id: 'html-new',
          name: 'Math',
          dayOfWeek: 1,
          startSection: 1,
          endSection: 2,
          startWeek: 5,
          endWeek: 5,
          customWeeks: const [5],
          location: 'NewRoom',
        ),
      ];
      final result = mergeHtmlImportWeek(
        week: 5,
        firstCourseWeek: 1,
        existingCourses: existing,
        fetchedCourses: fetched,
      );
      expect(result.fetchedCount, 1);
      expect(result.changedCount, 2);
      // 第 7 周 Phys 仍保留，且不计入变化
      final names = result.courses.map((c) => c.name).toList();
      expect(names, contains('Phys'));
      expect(result.courses.singleWhere((c) => c.name == 'Math').location, 'NewRoom');
    });
  });
}
