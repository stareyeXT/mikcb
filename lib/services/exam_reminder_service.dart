import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../logging/app_debug_log.dart';
import '../models/course.dart';
import '../models/exam.dart';
import '../models/schedule_item.dart';
import '../models/timetable_settings.dart';
import '../providers/timetable/live_activity_logic.dart';

/// One scheduled fire point for an exam reminder (local wall clock).
class ExamReminderFire {
  const ExamReminderFire({
    required this.examId,
    required this.offsetMinutes,
    required this.fireAtMillis,
    required this.examStartMillis,
    required this.title,
    required this.body,
    required this.requestCode,
    this.openRoute = '/exams',
    this.style = 'normal',
    this.tapAction = 'openApp',
    this.calendarHour = 8,
    this.calendarMinute = 0,
    this.calendarTitle = '',
    this.islandA = '',
    this.islandB = '',
    this.firstClassStartMillis = 0,
  });

  final String examId;
  final int offsetMinutes;
  final int fireAtMillis;
  final int examStartMillis;
  final String title;
  final String body;
  final int requestCode;
  final String openRoute;

  /// 'normal' → plain exam-reminder rendering; 'auto' → tomorrow-briefing
  /// rendering that adapts to the device (super island / live updates / plain).
  final String style;

  /// What tapping the notification does: 'openApp' or 'openCalendar'.
  final String tapAction;

  /// Calendar prefill for [tapAction] == 'openCalendar' (first course time).
  final int calendarHour;
  final int calendarMinute;
  final String calendarTitle;

  /// Super-island expanded text (A area) and compact/ticker text (B area).
  final String islandA;
  final String islandB;

  /// Tomorrow's first course start, used for live-update progress. 0 = absent.
  final int firstClassStartMillis;

  Map<String, dynamic> toNativeMap() {
    return {
      'examId': examId,
      'offsetMinutes': offsetMinutes,
      'fireAtMillis': fireAtMillis,
      'examStartMillis': examStartMillis,
      'title': title,
      'body': body,
      'requestCode': requestCode,
      'openRoute': openRoute,
      'style': style,
      'tapAction': tapAction,
      'calendarHour': calendarHour,
      'calendarMinute': calendarMinute,
      'calendarTitle': calendarTitle,
      'islandA': islandA,
      'islandB': islandB,
      'firstClassStartMillis': firstClassStartMillis,
    };
  }
}

/// Builds fire points and syncs them to the native AlarmManager scheduler.
class ExamReminderService {
  static const MethodChannel _channel = MethodChannel(
    'com.mutx163.qingyu/exam_reminder',
  );

  /// Namespace for PendingIntent request codes (must match native cancel).
  static const int requestCodeNamespace = 0x46000000;

  static final ExamReminderService _instance = ExamReminderService._internal();
  factory ExamReminderService() => _instance;
  ExamReminderService._internal();

  /// Stable request code shared with native cancel/schedule.
  static int stableRequestCode(String examId, int offsetMinutes) {
    final key = '$examId#$offsetMinutes';
    var hash = 0x811c9dc5;
    for (final codeUnit in key.codeUnits) {
      hash ^= codeUnit;
      hash = (hash * 0x01000193) & 0x7fffffff;
    }
    return requestCodeNamespace | (hash & 0x00ffffff);
  }

  /// Identifies one logical reminder fire, including its lead time.
  ///
  /// Keeping the offset in the key matters when a user changes a reminder
  /// from (for example) 30 minutes to 60 minutes: the old overdue fire must
  /// not be retried as if it were still part of the active schedule.
  static String fireKey(ExamReminderFire fire) =>
      '${fire.examId}#${fire.offsetMinutes}';

  static Set<String> buildActiveFireKeys(Iterable<ExamReminderFire> fires) {
    return fires.map(fireKey).toSet();
  }

  static const Duration _scheduleReminderHorizon = Duration(days: 366);
  static const Duration _scheduleReminderRetryWindow = Duration(days: 1);
  static const int _earlyClassStartHour = 8;
  static const int _earlyClassEndHour = 8;
  static const int _nightlyCheckHour = 22;
  static const int _briefingMaxCourses = 6;

  /// Builds one-shot fires for enabled schedule occurrences.
  ///
  /// The native scheduler already reconciles and persists one-shot fires, so
  /// recurring rules are expanded here into a bounded future window. The
  /// occurrence id is namespaced to keep it disjoint from exam ids while
  /// retaining deterministic cancellation across edits and restarts.
  static List<ExamReminderFire> buildScheduleFires({
    required List<ScheduleItem> scheduleItems,
    DateTime? now,
  }) {
    final referenceNow = now ?? DateTime.now();
    final fromDate = ScheduleItem.dateOnly(
      referenceNow.subtract(_scheduleReminderRetryWindow),
    );
    final toDate = ScheduleItem.dateOnly(
      referenceNow.add(_scheduleReminderHorizon),
    );
    final instancesById = <String, ScheduleItemInstance>{};

    for (final item in scheduleItems) {
      for (final instance in item.expandInstances(
        fromDate: fromDate,
        toDate: toDate,
      )) {
        final existing = instancesById[instance.occurrenceId];
        if (existing == null || instance.item.seriesId != null) {
          instancesById[instance.occurrenceId] = instance;
        }
      }
    }

    final fires = <ExamReminderFire>[];
    for (final instance in instancesById.values) {
      final item = instance.effectiveItem;
      final offsetMinutes = item.reminderMinutesBefore;
      if (offsetMinutes == null || offsetMinutes <= 0) {
        continue;
      }
      final start = _buildScheduleDateTime(instance.date, item.startTime);
      if (start == null) {
        continue;
      }
      final fireAt = start.subtract(Duration(minutes: offsetMinutes));
      if (!fireAt.isAfter(referenceNow.subtract(const Duration(seconds: 30)))) {
        continue;
      }
      final scheduleId = _scheduleFireId(instance.occurrenceId);
      fires.add(
        ExamReminderFire(
          examId: scheduleId,
          offsetMinutes: offsetMinutes,
          fireAtMillis: fireAt.millisecondsSinceEpoch,
          examStartMillis: start.millisecondsSinceEpoch,
          title: item.title.trim(),
          body: _buildScheduleBody(item),
          requestCode: stableRequestCode(scheduleId, offsetMinutes),
        ),
      );
    }

    fires.sort(
      (left, right) => left.fireAtMillis.compareTo(right.fireAtMillis),
    );
    return fires;
  }

  static Set<String> buildScheduleActiveIds({
    required List<ScheduleItem> scheduleItems,
    DateTime? now,
  }) {
    final referenceNow = now ?? DateTime.now();
    final fromDate = ScheduleItem.dateOnly(
      referenceNow.subtract(_scheduleReminderRetryWindow),
    );
    final toDate = ScheduleItem.dateOnly(
      referenceNow.add(_scheduleReminderHorizon),
    );
    final instancesById = <String, ScheduleItemInstance>{};
    for (final item in scheduleItems) {
      for (final instance in item.expandInstances(
        fromDate: fromDate,
        toDate: toDate,
      )) {
        final existing = instancesById[instance.occurrenceId];
        if (existing == null || instance.item.seriesId != null) {
          instancesById[instance.occurrenceId] = instance;
        }
      }
    }
    return instancesById.values
        .where((instance) {
          final item = instance.effectiveItem;
          return item.enabled &&
              item.reminderMinutesBefore != null &&
              item.reminderMinutesBefore! > 0;
        })
        .map((instance) => _scheduleFireId(instance.occurrenceId))
        .toSet();
  }

  /// Builds one 22:00 "tomorrow briefing" fire for each day in the next year.
  ///
  /// Fires only when tomorrow has active courses; the first course starting in
  /// the 08:00 hour marks an early class ("早八") and takes display priority,
  /// with the tap opening the system calendar to prefill a first-course event.
  /// Holiday entries are deliberately not consulted: a timetable can contain
  /// makeup classes during a statutory holiday.
  static List<ExamReminderFire> buildTomorrowCourseBriefingFires({
    required List<Course> courses,
    required DateTime? semesterStartDate,
    required int semesterWeekCount,
    List<SectionTime>? sections,
    DateTime? now,
  }) {
    final semesterStart = semesterStartDate;
    if (semesterStart == null || semesterWeekCount < 1) {
      return const <ExamReminderFire>[];
    }
    final referenceNow = now ?? DateTime.now();
    final startDate = ScheduleItem.dateOnly(referenceNow);
    final monday = ScheduleItem.dateOnly(
      semesterStart.subtract(Duration(days: semesterStart.weekday - 1)),
    );
    final fires = <ExamReminderFire>[];
    for (var dayOffset = 0; dayOffset <= _scheduleReminderHorizon.inDays; dayOffset++) {
      final checkDate = startDate.add(Duration(days: dayOffset));
      final fireAt = DateTime(
        checkDate.year,
        checkDate.month,
        checkDate.day,
        _nightlyCheckHour,
      );
      if (!fireAt.isAfter(referenceNow.subtract(const Duration(seconds: 30)))) {
        continue;
      }

      final targetDate = checkDate.add(const Duration(days: 1));
      final targetMondayWeek =
          ScheduleItem.dateOnly(targetDate)
              .difference(monday)
              .inDays ~/
          7;
      final targetWeek = targetMondayWeek + 1;
      final targetCourses = targetWeek < 1 || targetWeek > semesterWeekCount
          ? <Course>[]
          : courses
                .where(
                  (course) =>
                      course.dayOfWeek == targetDate.weekday &&
                      course.isActiveInWeek(targetWeek),
                )
                .map((course) => course)
                .toList();
      targetCourses.sort((left, right) {
        final sectionOrder = left.startSection.compareTo(right.startSection);
        return sectionOrder != 0 ? sectionOrder : left.id.compareTo(right.id);
      });
      if (targetCourses.isEmpty) {
        continue;
      }

      final firstCourse = targetCourses.first;
      final firstStartText = LiveActivityLogic.resolveRealTime(
        firstCourse,
        true,
        sections,
      );
      final firstStartDateTime = _buildScheduleDateTime(
        targetDate,
        firstStartText,
      );
      final firstStartMinutes = firstStartDateTime == null
          ? LiveActivityLogic.parseClockMinutes(firstStartText)
          : firstStartDateTime.hour * 60 + firstStartDateTime.minute;
      final hasEarlyClass =
          firstStartMinutes != null &&
          firstStartMinutes >= _earlyClassStartHour * 60 &&
          firstStartMinutes <= _earlyClassEndHour * 60 + 59;

      final checkId =
          'tomorrow_briefing:${ScheduleItem.formatCalendarDate(checkDate)}';
      fires.add(
        ExamReminderFire(
          examId: checkId,
          offsetMinutes: 0,
          fireAtMillis: fireAt.millisecondsSinceEpoch,
          examStartMillis: 0,
          title: hasEarlyClass
              ? '明天有早八 · ${_briefingCourseName(firstCourse)} $firstStartText'
              : '明天有 ${targetCourses.length} 门课程',
          body: _buildBriefingBody(targetCourses, sections),
          requestCode: stableRequestCode(checkId, 0),
          openRoute: '/',
          style: 'auto',
          tapAction: hasEarlyClass ? 'openCalendar' : 'openApp',
          calendarHour: firstStartDateTime?.hour ?? 8,
          calendarMinute: firstStartDateTime?.minute ?? 0,
          calendarTitle: '${_briefingCourseName(firstCourse)}（明日早八）',
          islandA: hasEarlyClass
              ? '早八 ${_briefingCourseName(firstCourse)}'
              : '${targetCourses.length} 门课程',
          islandB: hasEarlyClass
              ? '$firstStartText ${_briefingCourseName(firstCourse)}'
              : '首课 $firstStartText',
          firstClassStartMillis:
              firstStartDateTime?.millisecondsSinceEpoch ?? 0,
        ),
      );
    }
    return fires;
  }

  /// Short-name-first display name with a plain fallback to the full name.
  static String _briefingCourseName(Course course) {
    final shortName = course.shortName?.trim();
    if (shortName != null && shortName.isNotEmpty) {
      return shortName;
    }
    return course.name.trim();
  }

  static String _buildBriefingBody(
    List<Course> courses,
    List<SectionTime>? sections,
  ) {
    final lines = <String>[];
    for (final course in courses) {
      final start = LiveActivityLogic.resolveRealTime(course, true, sections);
      final end = LiveActivityLogic.resolveRealTime(course, false, sections);
      final location = course.location.trim();
      final name = _briefingCourseName(course);
      lines.add(
        location.isEmpty ? '$start-$end $name' : '$start-$end $name · $location',
      );
      if (lines.length >= _briefingMaxCourses) {
        break;
      }
    }
    final overflow = courses.length - lines.length;
    if (overflow > 0) {
      lines.add('等共 ${courses.length} 门课程');
    }
    return lines.join('\n');
  }

  static String _scheduleFireId(String occurrenceId) =>
      'schedule:$occurrenceId';

  static DateTime? _buildScheduleDateTime(DateTime date, String value) {
    final parts = value.split(':');
    if (parts.length != 2) {
      return null;
    }
    final hour = int.tryParse(parts[0]);
    final minute = int.tryParse(parts[1]);
    if (hour == null || minute == null || hour < 0 || hour > 23) {
      return null;
    }
    if (minute < 0 || minute > 59) {
      return null;
    }
    return DateTime(date.year, date.month, date.day, hour, minute);
  }

  static String _buildScheduleBody(ScheduleItem item) {
    final parts = <String>['${item.startTime.trim()}-${item.endTime.trim()}'];
    final location = item.location?.trim();
    if (location != null && location.isNotEmpty) {
      parts.add(location);
    }
    return parts.join(' · ');
  }

  /// Expands [exams] into future fire points. Pure function for unit tests.
  static List<ExamReminderFire> buildFires({
    required List<Exam> exams,
    required Course? Function(Exam exam) resolveCourse,
    DateTime? now,
  }) {
    final referenceNow = now ?? DateTime.now();
    final fires = <ExamReminderFire>[];

    for (final exam in exams) {
      final endParts = Exam.parseTimeOfDayParts(
        exam.endTime,
        fallbackHour: 23,
        fallbackMinute: 59,
      );
      final examEnd = DateTime(
        exam.dateTime.year,
        exam.dateTime.month,
        exam.dateTime.day,
        endParts.$1,
        endParts.$2,
      );
      if (!examEnd.isAfter(referenceNow)) {
        continue;
      }

      final offsets = exam.effectiveReminderMinutes.toSet().toList()..sort();
      if (offsets.isEmpty) {
        continue;
      }

      final examStart = exam.examStartDateTime;
      final examStartMillis = examStart.millisecondsSinceEpoch;
      final course = resolveCourse(exam);
      final body = _buildBody(exam, course);

      for (final offsetMinutes in offsets) {
        if (offsetMinutes <= 0) {
          continue;
        }
        final fireAt = examStart.subtract(Duration(minutes: offsetMinutes));
        // Skip fires already in the past (with a small grace for clock skew).
        // Native separately retains only points that actually fired but could
        // not post, so reconstructing past points here would cause duplicates.
        if (!fireAt.isAfter(
          referenceNow.subtract(const Duration(seconds: 30)),
        )) {
          continue;
        }
        // Empty title → native falls back to localized
        // notification_exam_reminder_default_title (do not hardcode zh-CN).
        fires.add(
          ExamReminderFire(
            examId: exam.id,
            offsetMinutes: offsetMinutes,
            fireAtMillis: fireAt.millisecondsSinceEpoch,
            examStartMillis: examStartMillis,
            title: exam.name.trim(),
            body: body,
            requestCode: stableRequestCode(exam.id, offsetMinutes),
          ),
        );
      }
    }

    fires.sort(
      (left, right) => left.fireAtMillis.compareTo(right.fireAtMillis),
    );
    return fires;
  }

  static String _buildBody(Exam exam, Course? course) {
    final parts = <String>['${exam.startTime}-${exam.endTime}'];
    final location = exam.location?.trim().isNotEmpty == true
        ? exam.location!.trim()
        : course?.location.trim();
    if (location != null && location.isNotEmpty) {
      parts.add(location);
    }
    final seatNumber = exam.seatNumber?.trim();
    if (seatNumber != null && seatNumber.isNotEmpty) {
      parts.add(seatNumber);
    }
    return parts.join(' · ');
  }

  /// Full reconcile: replaces all native exam-reminder alarms with [exams].
  Future<bool> reconcile({
    required List<Exam> exams,
    required Course? Function(Exam exam) resolveCourse,
    List<ScheduleItem> scheduleItems = const [],
    List<Course> courses = const [],
    DateTime? semesterStartDate,
    int semesterWeekCount = 0,
    bool tomorrowBriefingEnabled = false,
    List<SectionTime>? sections,
    DateTime? now,
  }) async {
    final referenceNow = now ?? DateTime.now();
    final fires = <ExamReminderFire>[
      ...buildFires(
        exams: exams,
        resolveCourse: resolveCourse,
        now: referenceNow,
      ),
      ...buildScheduleFires(scheduleItems: scheduleItems, now: referenceNow),
      if (tomorrowBriefingEnabled)
        ...buildTomorrowCourseBriefingFires(
          courses: courses,
          semesterStartDate: semesterStartDate,
          semesterWeekCount: semesterWeekCount,
          sections: sections,
          now: referenceNow,
        ),
    ];
    final activeExamIds = exams
        .where((exam) => !exam.isExpired)
        .map((exam) => exam.id)
        .toSet();
    activeExamIds.addAll(
      buildScheduleActiveIds(scheduleItems: scheduleItems, now: referenceNow),
    );
    return syncFires(
      fires,
      activeExamIds: activeExamIds,
      activeFireKeys: buildActiveFireKeys(fires),
    );
  }

  Future<bool> syncFires(
    List<ExamReminderFire> fires, {
    Set<String> activeExamIds = const {},
    Set<String>? activeFireKeys,
  }) async {
    if (defaultTargetPlatform != TargetPlatform.android) {
      return true;
    }
    try {
      final payload = <String, dynamic>{
        'fires': fires.map((fire) => fire.toNativeMap()).toList(),
        'activeExamIds': activeExamIds.toList(growable: false),
      };
      if (activeFireKeys != null) {
        payload['activeFireKeys'] = activeFireKeys.toList(growable: false);
      }
      await _channel.invokeMethod<void>('reconcile', payload);
      return true;
    } on MissingPluginException {
      if (kDebugMode) {
        return true;
      }
    } catch (error) {
      appDebugLog('ExamReminder', 'reconcile failed: $error');
    }
    return false;
  }

  Future<bool> clearAll() async {
    if (defaultTargetPlatform != TargetPlatform.android) {
      return true;
    }
    try {
      await _channel.invokeMethod<void>('clear');
      return true;
    } on MissingPluginException {
      if (kDebugMode) {
        return true;
      }
    } catch (error) {
      appDebugLog('ExamReminder', 'clear failed: $error');
    }
    return false;
  }

  /// Opens the system calendar writer with a pre-filled next occurrence.
  /// HyperOS 2's calendar writer owns the confirmation and needs no calendar
  /// permission in the app.
  Future<bool> openSystemCalendarEvent({
    required int hour,
    required int minute,
    String? message,
  }) async {
    if (defaultTargetPlatform != TargetPlatform.android) return false;
    try {
      final result = await _channel.invokeMethod<bool>(
        'openSystemCalendarEvent',
        {'hour': hour, 'minute': minute, 'message': message ?? '早八课程'},
      );
      return result ?? false;
    } on MissingPluginException {
      return kDebugMode;
    } catch (error) {
      appDebugLog('ExamReminder', 'open system calendar failed: $error');
      return false;
    }
  }

  /// Asks native to post a one-off tomorrow-briefing notification for testing.
  /// Returns the native hint text (mutual-exclusion warnings etc.), or null
  /// when the notification was posted cleanly.
  Future<String?> sendTomorrowBriefingTest({
    required String title,
    required String body,
    required bool hasEarlyClass,
    required int firstClassStartMillis,
    String islandA = '',
    String islandB = '',
    int calendarHour = 8,
    int calendarMinute = 0,
    String calendarTitle = '早八课程',
  }) async {
    if (defaultTargetPlatform != TargetPlatform.android) return null;
    try {
      final result = await _channel
          .invokeMethod<String>('sendTomorrowBriefingTest', {
            'title': title,
            'body': body,
            'hasEarlyClass': hasEarlyClass,
            'firstClassStartMillis': firstClassStartMillis,
            'islandA': islandA,
            'islandB': islandB,
            'calendarHour': calendarHour,
            'calendarMinute': calendarMinute,
            'calendarTitle': calendarTitle,
          });
      if (result == null || result.isEmpty) return null;
      return result;
    } on MissingPluginException {
      return kDebugMode ? null : '当前平台不支持';
    } catch (error) {
      appDebugLog('ExamReminder', 'briefing test failed: $error');
      return '发送失败：$error';
    }
  }
}
