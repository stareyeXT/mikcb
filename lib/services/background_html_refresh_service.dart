import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';
import 'package:workmanager/workmanager.dart';

import '../models/course.dart';
import 'html_import_refresh.dart';
import 'html_import_service.dart';

@pragma('vm:entry-point')
void backgroundHtmlRefreshCallback() {
  Workmanager().executeTask((taskName, inputData) async {
    try {
      final prefs = await SharedPreferences.getInstance();

      // Read the active profile ID
      final activeProfileId = prefs.getString('active_profile_id');
      if (activeProfileId == null || activeProfileId.isEmpty) return true;

      // Read the HTML import URL (profile-scoped key)
      const baseUrlKey = 'html_import_base_url';
      final urlKey = '${activeProfileId}_$baseUrlKey';
      final url = prefs.getString(urlKey);
      if (url == null || url.isEmpty) return true;

      // Read existing profiles to resolve semester start / current week.
      final rawProfiles = prefs.getString('timetable_profiles');
      if (rawProfiles == null || rawProfiles.isEmpty) return true;

      final profiles = (jsonDecode(rawProfiles) as List<dynamic>)
          .map((p) => Map<String, dynamic>.from(p as Map))
          .toList();

      final profileIndex = profiles.indexWhere(
        (p) => p['id'] as String == activeProfileId,
      );
      if (profileIndex == -1) return true;

      final profile = Map<String, dynamic>.from(profiles[profileIndex]);

      final semesterStartStr =
          (profile['settings'] as Map<String, dynamic>?)?['semesterStartDate'] as String?;
      final currentWeek = _calculateCurrentWeek(semesterStartStr);
      final semesterStart = semesterStartStr != null
          ? DateTime.tryParse(semesterStartStr)
          : null;
      final weekStartDate = semesterStart != null
          ? HtmlImportService.startOfWeek(semesterStart)
              .add(Duration(days: 7 * (currentWeek - 1)))
          : HtmlImportService.startOfWeek(DateTime.now());

      final firstCourseWeekKey =
          '${activeProfileId}_html_import_first_course_week';
      final firstCourseWeek = prefs.getInt(firstCourseWeekKey) ?? 1;

      final existingCourses = (profile['courses'] as List<dynamic>? ?? const [])
          .cast<Map<String, dynamic>>()
          .map((m) => Course.fromJson(Map<String, dynamic>.from(m)))
          .toList();

      // 复用与前台一致的刷新合并逻辑（fetch + 周隔离 merge + 变化判定），
      // 避免后台 worker 自己再实现一遍 merge。
      final result = await refreshHtmlImportWeek(
        url: url,
        weekStartDate: weekStartDate,
        week: currentWeek,
        firstCourseWeek: firstCourseWeek,
        existingCourses: existingCourses,
      );

      if (result.fetchedCount == 0) return true;

      profile['courses'] = result.courses.map((c) => c.toJson()).toList();
      profiles[profileIndex] = profile;

      // Save updated profiles
      await prefs.setString('timetable_profiles', jsonEncode(profiles));

      // Update the fetch time
      final fetchTimesKey = '${activeProfileId}_html_import_week_fetch_times';
      final now = DateTime.now();
      final fetchTimesRaw = prefs.getString(fetchTimesKey);
      final fetchTimes = fetchTimesRaw != null
          ? Map<String, dynamic>.from(jsonDecode(fetchTimesRaw) as Map)
          : <String, dynamic>{};
      fetchTimes[currentWeek.toString()] = now.toIso8601String();
      await prefs.setString(fetchTimesKey, jsonEncode(fetchTimes));

      // Trigger the native side to rebuild snapshots
      await _triggerSnapshotRefresh(prefs, activeProfileId, profiles, profile);
    } catch (_) {
      // Silently fail — next app open will refresh anyway
    }
    return true;
  });
}

int _calculateCurrentWeek(String? semesterStartStr) {
  if (semesterStartStr == null) return 1;
  final semesterStart = DateTime.tryParse(semesterStartStr);
  if (semesterStart == null) return 1;
  final now = DateTime.now();
  final normalizedNow = DateTime(now.year, now.month, now.day);
  final normalizedStart = DateTime(
    semesterStart.year,
    semesterStart.month,
    semesterStart.day,
  );
  final week = (normalizedNow.difference(normalizedStart).inDays ~/ 7) + 1;
  return week < 1 ? 1 : week;
}

Future<void> _triggerSnapshotRefresh(
  SharedPreferences prefs,
  String activeProfileId,
  List<Map<String, dynamic>> profiles,
  Map<String, dynamic> activeProfile,
) async {
  // Write a flag that tells the LiveUpdateScheduler to reschedule from
  // the stored data on next alarm. This is a best-effort approach —
  // the scheduler's existing alarms will pick up the new data.
  await prefs.setBool('background_html_refreshed', true);

  // Also try to directly update the live schedule snapshot
  try {
    final snapshotCourses = (activeProfile['courses'] as List<dynamic>?)
            ?.cast<Map<String, dynamic>>() ??
        const [];
    final settings = activeProfile['settings'] as Map<String, dynamic>?;
    final currentWeek = _calculateCurrentWeek(
      settings?['semesterStartDate'] as String?,
    );

    final snapshot = {
      'profileId': activeProfileId,
      'currentWeek': currentWeek,
      'semesterStartDate': settings?['semesterStartDate'],
      'settings': settings,
      'courses': snapshotCourses,
    };

    await prefs.setString(
      'live_update_schedule_snapshot',
      jsonEncode(snapshot),
    );

    // Also update home widget snapshot
    await _buildAndSaveWidgetSnapshot(
      prefs,
      activeProfileId,
      settings,
      snapshotCourses,
      currentWeek,
    );
  } catch (_) {}
}

Future<void> _buildAndSaveWidgetSnapshot(
  SharedPreferences prefs,
  String profileId,
  Map<String, dynamic>? settings,
  List<Map<String, dynamic>> courses,
  int currentWeek,
) async {
  // Build a minimal widget snapshot
  final now = DateTime.now();
  final todayDayOfWeek = now.weekday;

  final todayCourses = courses
      .where((c) {
        final dayOk = c['dayOfWeek'] as int == todayDayOfWeek;
        final startW = c['startWeek'] as int;
        final endW = c['endWeek'] as int;
        final weeksOk = currentWeek >= startW && currentWeek <= endW;
        return dayOk && weeksOk;
      })
      .toList()
    ..sort((a, b) => (a['startSection'] as int).compareTo(b['startSection'] as int));

  final courseList = todayCourses.map((c) => {
    'id': c['id'],
    'name': c['name'],
    'shortName': c['shortName'],
    'location': c['location'],
    'teacher': c['teacher'],
    'startSection': c['startSection'],
    'endSection': c['endSection'],
    'startTime': c['startTime'],
    'endTime': c['endTime'],
    'dayOfWeek': c['dayOfWeek'],
    'color': c['color'] ?? '#2563EB',
  }).toList();

  final widgetSnapshot = {
    'profileId': profileId,
    'profileName': settings?['profileName'] ?? '',
    'currentWeek': currentWeek,
    'courses': courseList,
    'generatedAt': now.toIso8601String(),
  };

  await prefs.setString(
    'last_home_widget_snapshot',
    jsonEncode(widgetSnapshot),
  );
}
