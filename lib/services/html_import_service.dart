import 'package:http/http.dart' as http;
import 'package:uuid/uuid.dart';

import '../models/course.dart';

class HtmlImportResult {
  final List<Course> courses;
  final String? sourceUrl;

  const HtmlImportResult({
    required this.courses,
    this.sourceUrl,
  });
}

class HtmlWeekFetchProgress {
  final int completedDays;
  final int totalDays;
  final String currentDayLabel;

  const HtmlWeekFetchProgress({
    required this.completedDays,
    required this.totalDays,
    required this.currentDayLabel,
  });
}

class HtmlImportService {
  static final _uuid = const Uuid();

  static String buildUrlWithDate(String baseUrl, String dateStr) {
    final uri = Uri.tryParse(baseUrl.trim());
    if (uri == null) return baseUrl;
    final newQueryParams = Map<String, String>.from(uri.queryParameters);
    newQueryParams['date'] = dateStr;
    return uri.replace(queryParameters: newQueryParams).toString();
  }

  static DateTime startOfWeek(DateTime date) {
    final normalized = DateTime(date.year, date.month, date.day);
    return normalized.subtract(Duration(days: normalized.weekday - 1));
  }

  Future<List<Course>> fetchWeekCourses(
    String baseUrl,
    DateTime weekStartDate, {
    void Function(HtmlWeekFetchProgress progress)? onProgress,
  }) async {
    final totalDays = 7;

    final futures = <Future<List<Course>>>[];
    for (var i = 0; i < totalDays; i++) {
      futures.add(_fetchDayCourses(baseUrl, weekStartDate, i));
    }

    final results = await Future.wait(futures);
    final allCourses = <Course>[];
    for (final courses in results) {
      allCourses.addAll(courses);
    }

    onProgress?.call(HtmlWeekFetchProgress(
      completedDays: totalDays,
      totalDays: totalDays,
      currentDayLabel: '',
    ));

    return allCourses;
  }

  Future<List<Course>> _fetchDayCourses(
    String baseUrl,
    DateTime weekStartDate,
    int dayIndex,
  ) async {
    final date = weekStartDate.add(Duration(days: dayIndex));
    final dateStr =
        '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    final url = buildUrlWithDate(baseUrl, dateStr);

    try {
      final htmlContent = await fetchHtmlContent(url);
      final result = parseHtml(htmlContent, sourceUrl: url);
      final dayOfWeek = dayIndex + 1;
      return result.courses.map((c) {
        return c.copyWith(dayOfWeek: dayOfWeek);
      }).toList();
    } catch (_) {
      return const [];
    }
  }

  Future<String> fetchHtmlContent(String url) async {
    final uri = Uri.tryParse(url.trim());
    if (uri == null || uri.host.isEmpty) {
      throw const FormatException('网址格式不正确');
    }
    if (!uri.scheme.startsWith('http')) {
      throw const FormatException('仅支持 http 或 https 网址');
    }

    final response = await http.get(uri).timeout(const Duration(seconds: 30));

    if (response.statusCode != 200) {
      throw FormatException('请求失败，状态码：${response.statusCode}');
    }

    final contentType = response.headers['content-type'] ?? '';
    if (!contentType.contains('text/html') &&
        !contentType.contains('text/') &&
        !contentType.contains('application/xhtml')) {
      throw const FormatException('返回的内容不是 HTML 页面');
    }

    return response.body;
  }

  HtmlImportResult parseHtml(String htmlContent, {String? sourceUrl}) {
    final dayOfWeek = _parseDayOfWeek(htmlContent);
    if (dayOfWeek == null) {
      return HtmlImportResult(courses: const [], sourceUrl: sourceUrl);
    }

    final liEntries = _extractLiEntries(htmlContent);
    if (liEntries.isEmpty) {
      return HtmlImportResult(courses: const [], sourceUrl: sourceUrl);
    }

    final courses = <Course>[];
    for (var i = 0; i < liEntries.length; i++) {
      final entry = liEntries[i];
      final course = _parseCourseEntry(entry, dayOfWeek, i);
      if (course != null) {
        courses.add(course);
      }
    }

    return HtmlImportResult(courses: courses, sourceUrl: sourceUrl);
  }

  int? _parseDayOfWeek(String html) {
    final centerMatch = RegExp(r'<div\s+class="center">.*?<p>(.*?)</p>', dotAll: true).firstMatch(html);
    if (centerMatch == null) return null;

    final text = _stripHtmlTags(centerMatch.group(1)!).trim();
    final dateMatch = RegExp(r'(\d{4})-(\d{2})-(\d{2})').firstMatch(text);
    if (dateMatch != null) {
      final year = int.parse(dateMatch.group(1)!);
      final month = int.parse(dateMatch.group(2)!);
      final day = int.parse(dateMatch.group(3)!);
      return DateTime(year, month, day).weekday;
    }

    final weekdayMap = {
      '星期一': 1, '周一': 1,
      '星期二': 2, '周二': 2,
      '星期三': 3, '周三': 3,
      '星期四': 4, '周四': 4,
      '星期五': 5, '周五': 5,
      '星期六': 6, '周六': 6,
      '星期日': 7, '周日': 7,
    };
    for (final entry in weekdayMap.entries) {
      if (text.contains(entry.key)) {
        return entry.value;
      }
    }
    return null;
  }

  List<String> _extractLiEntries(String html) {
    final ulMatch = RegExp(r'<ul\s+class="rl_info">(.*?)</ul>', dotAll: true).firstMatch(html);
    if (ulMatch == null) return const [];

    final ulContent = ulMatch.group(1)!;
    final liRegExp = RegExp(r'<li>(.*?)</li>', dotAll: true);
    return liRegExp
        .allMatches(ulContent)
        .map((match) => match.group(1)!)
        .toList();
  }

  Course? _parseCourseEntry(String liContent, int dayOfWeek, int index) {
    final pMatch = RegExp(r'<p>(.*?)</p>', dotAll: true).firstMatch(liContent);
    if (pMatch == null) return null;

    final pContent = pMatch.group(1)!;
    final spanMatch = RegExp(r'<span[^>]*class="class_span"[^>]*>(.*?)</span>', dotAll: true).firstMatch(pContent);
    if (spanMatch == null) return null;

    final sectionText = _stripHtmlTags(spanMatch.group(1)!).trim();
    final sectionMatch = RegExp(r'(\d+)\s*[-—]\s*(\d+)\s*节').firstMatch(sectionText);
    if (sectionMatch == null) return null;

    final startSection = int.parse(sectionMatch.group(1)!);
    final endSection = int.parse(sectionMatch.group(2)!);

    final afterSpan = pContent.replaceFirst(RegExp(r'<span[^>]*class="class_span"[^>]*>.*?</span>', dotAll: true), '');
    final plainText = _stripHtmlTags(afterSpan).trim();
    final lines = plainText.split(RegExp(r'\n')).map((l) => l.trim()).where((l) => l.isNotEmpty).toList();

    if (lines.isEmpty) return null;

    final name = lines[0];

    String? timeValue;
    String? locationValue;
    String? teacherValue;

    for (final line in lines) {
      if (line.startsWith('时间：') || line.startsWith('时间:')) {
        timeValue = line.replaceFirst(RegExp(r'时间[：:]'), '').trim();
      } else if (line.startsWith('地点：') || line.startsWith('地点:')) {
        locationValue = line.replaceFirst(RegExp(r'地点[：:]'), '').trim();
      } else if (line.startsWith('教师：') || line.startsWith('教师:')) {
        teacherValue = line.replaceFirst(RegExp(r'教师[：:]'), '').trim();
      }
    }

    if (timeValue == null) return null;

    final weekInfo = _parseWeekInfo(timeValue);
    if (weekInfo == null) return null;

    return Course(
      id: 'html-${_uuid.v4().substring(0, 8)}-$index',
      name: name,
      teacher: teacherValue ?? '',
      location: locationValue ?? '',
      dayOfWeek: dayOfWeek,
      startSection: startSection,
      endSection: endSection,
      startTime: '',
      endTime: '',
      startWeek: weekInfo.startWeek,
      endWeek: weekInfo.endWeek,
      isOddWeek: weekInfo.isOddWeek,
      isEvenWeek: weekInfo.isEvenWeek,
      customWeeks: weekInfo.customWeeks,
    );
  }

  _WeekInfo? _parseWeekInfo(String timeValue) {
    final parts = timeValue.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return null;

    final weekPart = parts[0];
    final weekRanges = weekPart.split(',');

    final allWeeks = <int>[];
    for (final range in weekRanges) {
      final rangeTrimmed = range.trim();
      final rangeMatch = RegExp(r'^(\d+)\s*[-—]\s*(\d+)$').firstMatch(rangeTrimmed);
      if (rangeMatch != null) {
        final start = int.parse(rangeMatch.group(1)!);
        final end = int.parse(rangeMatch.group(2)!);
        for (var w = start; w <= end; w++) {
          allWeeks.add(w);
        }
      } else {
        final singleWeek = int.tryParse(rangeTrimmed);
        if (singleWeek != null) {
          allWeeks.add(singleWeek);
        }
      }
    }

    if (allWeeks.isEmpty) return null;

    allWeeks.sort();
    final uniqueWeeks = allWeeks.toSet().toList()..sort();

    final isAllOdd = uniqueWeeks.isNotEmpty && uniqueWeeks.every((w) => w.isOdd);
    final isAllEven = uniqueWeeks.isNotEmpty && uniqueWeeks.every((w) => w.isEven);

    if (isAllOdd) {
      return _WeekInfo(
        startWeek: uniqueWeeks.first,
        endWeek: uniqueWeeks.last,
        isOddWeek: true,
      );
    }
    if (isAllEven) {
      return _WeekInfo(
        startWeek: uniqueWeeks.first,
        endWeek: uniqueWeeks.last,
        isEvenWeek: true,
      );
    }

    final isContiguous = uniqueWeeks.length == (uniqueWeeks.last - uniqueWeeks.first + 1);
    if (isContiguous) {
      return _WeekInfo(
        startWeek: uniqueWeeks.first,
        endWeek: uniqueWeeks.last,
      );
    }

    return _WeekInfo(
      startWeek: uniqueWeeks.first,
      endWeek: uniqueWeeks.last,
      customWeeks: uniqueWeeks,
    );
  }

  String _stripHtmlTags(String html) {
    return html.replaceAll(RegExp(r'<[^>]*>'), '').replaceAll('&amp;', '&').replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&nbsp;', ' ').replaceAll('&#39;', "'").replaceAll('&quot;', '"');
  }
}

class _WeekInfo {
  final int startWeek;
  final int endWeek;
  final bool isOddWeek;
  final bool isEvenWeek;
  final List<int>? customWeeks;

  const _WeekInfo({
    required this.startWeek,
    required this.endWeek,
    this.isOddWeek = false,
    this.isEvenWeek = false,
    this.customWeeks,
  });
}
