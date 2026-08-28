import '../models/course.dart';
import 'html_import_merge.dart';
import 'html_import_service.dart';

/// 一次 HTML 周刷新的结果。
class HtmlWeekRefreshResult {
  const HtmlWeekRefreshResult({
    required this.courses,
    required this.changedCount,
    required this.fetchedCount,
  });

  /// 合并后的全量课程（含非 HTML 课程与未受影响的其它周 HTML 课程）。
  final List<Course> courses;

  /// 本次刷新周实际新增或变化的课程条数（无变化为 0）。
  final int changedCount;

  /// 本次从网络抓取到的原始课程条数。
  final int fetchedCount;
}

/// 合并指定周的已抓取 HTML 课程（不抓取），返回合并后的全量课程与该周变化条数。
///
/// 纯函数：只做合并与变化判定，不触网络、不依赖 [TimetableProvider] /
/// [SharedPreferences]。前台「并行抓取 + 串行合并」与后台 worker 都复用它，
/// 保证合并 / 变化判定逻辑唯一且一致。
///
/// 变化判定只比对「该周生效」的 HTML 课程签名（见 [htmlCourseSignature]），
/// 而非全量周次 diff，避免跨周误判、也更省。
///
/// [fetchedCourses] 为空时直接保留原课程（merge 会在刷新周无新数据时
/// 删除该周已有 HTML 课程，导致空周丢课），与原 provider 行为一致。
HtmlWeekRefreshResult mergeHtmlImportWeek({
  required int week,
  required int firstCourseWeek,
  required List<Course> existingCourses,
  required List<Course> fetchedCourses,
}) {
  if (fetchedCourses.isEmpty) {
    return HtmlWeekRefreshResult(
      courses: existingCourses,
      changedCount: 0,
      fetchedCount: 0,
    );
  }

  String sig(Course c) => htmlCourseSignature(c);

  final previousWeekSigs = existingCourses
      .where(
        (c) => c.id.startsWith(htmlImportIdPrefix) && c.isActiveInWeek(week),
      )
      .map(sig)
      .toSet();

  final merged = mergeHtmlImportCourses(
    existingCourses: existingCourses,
    fetchedCourses: fetchedCourses,
    refreshWeek: week,
    firstCourseWeek: firstCourseWeek,
  );

  final newWeekSigs = merged
      .where(
        (c) => c.id.startsWith(htmlImportIdPrefix) && c.isActiveInWeek(week),
      )
      .map(sig)
      .toSet();

  final changedCount =
      previousWeekSigs.difference(newWeekSigs).length +
          newWeekSigs.difference(previousWeekSigs).length;

  return HtmlWeekRefreshResult(
    courses: merged,
    changedCount: changedCount,
    fetchedCount: fetchedCourses.length,
  );
}

/// 抓取并合并指定周的 HTML 导入课程，返回合并后的全量课程与该周变化条数。
///
/// 等价于「[HtmlImportService.fetchWeekCourses] + [mergeHtmlImportWeek]」。
/// 前台刷新（[TimetableProvider]）与后台刷新 worker 共用同一实现，
/// 保证两套路径的合并 / 变化判定逻辑完全一致。
Future<HtmlWeekRefreshResult> refreshHtmlImportWeek({
  required String url,
  required DateTime weekStartDate,
  required int week,
  required int firstCourseWeek,
  required List<Course> existingCourses,
  Duration timeout = const Duration(seconds: 10),
  HtmlImportService? service,
}) async {
  final importService = service ?? HtmlImportService();
  final fetchedCourses = await importService.fetchWeekCourses(
    url,
    weekStartDate,
    timeout: timeout,
  );

  return mergeHtmlImportWeek(
    week: week,
    firstCourseWeek: firstCourseWeek,
    existingCourses: existingCourses,
    fetchedCourses: fetchedCourses,
  );
}
