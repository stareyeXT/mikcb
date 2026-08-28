import '../models/course.dart';
import 'import_week_alignment_service.dart';

const String htmlImportIdPrefix = 'html-';

/// Content signature for an HTML-imported course. Excludes the parser-generated
/// id and locally-maintained fields (color/notes) so week-to-week refreshes
/// only register real timetable changes.
String htmlCourseSignature(Course c) => [
      c.name,
      c.teacher,
      c.location,
      c.dayOfWeek,
      c.startSection,
      c.endSection,
      c.startTime,
      c.endTime,
      c.startWeek,
      c.endWeek,
      c.isOddWeek,
      c.isEvenWeek,
      c.customWeeks,
      c.suspendedWeeks,
    ].join('\u241F');

/// Merge freshly fetched HTML courses for [refreshWeek] into [existingCourses].
///
/// Only the HTML courses ACTIVE in [refreshWeek] are replaced; every other
/// week's HTML courses and all non-HTML courses are preserved. This fixes the
/// previous behaviour where a single-week refresh wiped the entire imported
/// timetable (switching weeks made other weeks disappear).
///
/// Fetched courses carry raw 教务 week numbers, so they are shifted into
/// semester-week space via [firstCourseWeek] to stay aligned with the original
/// import mapping.
///
/// 周隔离（修法 1）：HTML 课程解析出的周次通常是整学期范围（如 1–20 周都上），
/// 若直接按 `isActiveInWeek` 隔离，刷新任意一周都会把整份课程当成"该周活跃"而整批
/// 替换，导致后面周的变化污染前面周。因此这里把 fetched 课程的生效周**强制钳制为
  /// 仅 [refreshWeek] 单周**，使每一周的数据完全独立、互不串。每次刷新只拉
  /// 当前周（见 [TimetableProvider._refreshHtmlOnSwitch]），逐周独立合并，
  /// 某周变化不影响其他周。
List<Course> mergeHtmlImportCourses({
  required List<Course> existingCourses,
  required List<Course> fetchedCourses,
  required int refreshWeek,
  required int firstCourseWeek,
}) {
  final shifted = ImportWeekAlignmentService().shiftCoursesToSemesterWeeks(
    fetchedCourses,
    firstCourseWeek: firstCourseWeek,
  );

  // 钳制：生效周仅 [refreshWeek]，清除跨周周次信息，保证每周独立。
  final shiftedNew = shifted.map((course) {
    return course.copyWith(
      startWeek: refreshWeek,
      endWeek: refreshWeek,
      customWeeks: [refreshWeek],
      isOddWeek: false,
      isEvenWeek: false,
    );
  }).toList();

  final nonHtmlCourses = existingCourses
      .where((c) => !c.id.startsWith(htmlImportIdPrefix))
      .toList();

  final keptHtmlCourses = existingCourses.where((c) {
    if (!c.id.startsWith(htmlImportIdPrefix)) return false;
    return !c.isActiveInWeek(refreshWeek);
  }).toList();

  final newSignatures = shiftedNew.map(htmlCourseSignature).toSet();
  final dedupedKeptHtml = keptHtmlCourses
      .where((c) => !newSignatures.contains(htmlCourseSignature(c)))
      .toList();

  return [...nonHtmlCourses, ...dedupedKeptHtml, ...shiftedNew];
}
