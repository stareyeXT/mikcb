import 'package:http/http.dart' as http;
import 'package:uuid/uuid.dart';

import '../models/course.dart';

class HtmlImportResult {
  final List<Course> courses;
  final String? sourceUrl;

  const HtmlImportResult({required this.courses, this.sourceUrl});
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

  /// 进程级共享客户端：复用 TCP/TLS 连接（http 顶层 get() 每次新建连接，
  /// 7 天页面就是 7 次完整握手）。仅用于本服务，不手动关闭。
  static final http.Client _sharedClient = http.Client();

  /// 按 host 复用 JSESSIONID 会话，避免 7 个并行请求各自新建会话。
  static final Map<String, String> _hostCookies = {};

  /// 同时进行的天数请求数：降低移动网络下并行 TLS 握手争用导致的个别慢请求。
  static const int _maxConcurrent = 5;

  static Future<List<R>> _concurrentMap<E, R>(
    List<E> items,
    int maxConcurrent,
    Future<R> Function(E) task,
  ) async {
    final results = List<R?>.filled(items.length, null);
    var next = 0;
    Future<void> worker() async {
      while (true) {
        final i = next;
        if (i >= items.length) return;
        next++;
        results[i] = await task(items[i]);
      }
    }

    final n = maxConcurrent < 1 ? 1 : maxConcurrent;
    await Future.wait(
      List.generate(n < items.length ? n : items.length, (_) => worker()),
    );
    return results.cast<R>();
  }

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
    Duration timeout = const Duration(seconds: 30),
  }) async {
    final totalDays = 7;

    final closures = <Future<List<Course>> Function()>[];
    for (var i = 0; i < totalDays; i++) {
      final idx = i;
      closures.add(
        () => _fetchDayCourses(baseUrl, weekStartDate, idx, timeout: timeout),
      );
    }

    final results = await _concurrentMap(closures, _maxConcurrent, (c) => c());
    final allCourses = <Course>[];
    for (final courses in results) {
      allCourses.addAll(courses);
    }

    onProgress?.call(
      HtmlWeekFetchProgress(
        completedDays: totalDays,
        totalDays: totalDays,
        currentDayLabel: '',
      ),
    );

    return allCourses;
  }

  Future<List<Course>> _fetchDayCourses(
    String baseUrl,
    DateTime weekStartDate,
    int dayIndex, {
    Duration timeout = const Duration(seconds: 30),
    int maxRetries = 3,
  }) async {
    final date = weekStartDate.add(Duration(days: dayIndex));
    final dateStr =
        '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    final url = buildUrlWithDate(baseUrl, dateStr);

    for (var attempt = 0; attempt < maxRetries; attempt++) {
      try {
        final htmlContent = await fetchHtmlContent(url, timeout: timeout);
        final result = parseHtml(htmlContent, sourceUrl: url);
        // 解析成功但当天无课表条目：视为该天确无课，不再重试
        final dayOfWeek = dayIndex + 1;
        return result.courses
            .map((c) => c.copyWith(dayOfWeek: dayOfWeek))
            .toList();
      } catch (e) {
        if (attempt < maxRetries - 1) {
          await Future.delayed(Duration(seconds: attempt + 1));
          continue;
        }
        return const [];
      }
    }
    return const [];
  }

  Future<String> fetchHtmlContent(
    String url, {
    Duration timeout = const Duration(seconds: 30),
  }) async {
    final uri = Uri.tryParse(url.trim());
    if (uri == null || uri.host.isEmpty) {
      throw const FormatException('网址格式不正确');
    }
    if (!uri.scheme.startsWith('http')) {
      throw const FormatException('仅支持 http 或 https 网址');
    }

    final headers = <String, String>{};
    final cachedCookie = _hostCookies[uri.host];
    if (cachedCookie != null && cachedCookie.isNotEmpty) {
      headers['Cookie'] = cachedCookie;
    }

    final response = await _sharedClient.get(uri, headers: headers).timeout(timeout);

    if (response.statusCode != 200) {
      throw FormatException('请求失败，状态码：${response.statusCode}');
    }

    final contentType = response.headers['content-type'] ?? '';
    if (!contentType.contains('text/html') &&
        !contentType.contains('text/') &&
        !contentType.contains('application/xhtml')) {
      throw const FormatException('返回的内容不是 HTML 页面');
    }

    // 服务器用 JSESSIONID 会话；http.Client 不自动存 cookie，
    // 这里复用同一会话，避免 7 个并行请求各自新建会话（设备侧新建会话可能很慢）。
    // 页面链接里直接带 ;jsessionid=，比解析 set-cookie 头更可靠。
    final sidMatch =
        RegExp(r'jsessionid=([0-9A-Fa-f]+)').firstMatch(response.body);
    if (sidMatch != null) {
      _hostCookies[uri.host] = 'JSESSIONID=${sidMatch.group(1)}';
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
    // The ECJTU page has changed whitespace/attributes several times. Read
    // the centered heading when present, then fall back to the document text.
    final centerMatch = RegExp(
      r"""<div\b[^>]*\bclass\s*=\s*["'][^"']*\bcenter\b[^"']*["'][^>]*>.*?<p\b[^>]*>(.*?)</p>""",
      dotAll: true,
      caseSensitive: false,
    ).firstMatch(html);
    final text = _stripHtmlTags(centerMatch?.group(1) ?? html).trim();
    final dateMatch = RegExp(r'(\d{4})-(\d{2})-(\d{2})').firstMatch(text);
    if (dateMatch != null) {
      final year = int.parse(dateMatch.group(1)!);
      final month = int.parse(dateMatch.group(2)!);
      final day = int.parse(dateMatch.group(3)!);
      return DateTime(year, month, day).weekday;
    }

    final weekdayMap = {
      '星期一': 1,
      '周一': 1,
      '星期二': 2,
      '周二': 2,
      '星期三': 3,
      '周三': 3,
      '星期四': 4,
      '周四': 4,
      '星期五': 5,
      '周五': 5,
      '星期六': 6,
      '周六': 6,
      '星期日': 7,
      '周日': 7,
    };
    for (final entry in weekdayMap.entries) {
      if (text.contains(entry.key)) {
        return entry.value;
      }
    }
    return null;
  }

  List<String> _extractLiEntries(String html) {
    final ulMatch = RegExp(
      r"""<ul\b[^>]*\bclass\s*=\s*["'][^"']*\brl_info\b[^"']*["'][^>]*>(.*?)</ul>""",
      dotAll: true,
      caseSensitive: false,
    ).firstMatch(html);
    if (ulMatch == null) return const [];

    final ulContent = ulMatch.group(1)!;
    final liRegExp = RegExp(
      r'<li\b[^>]*>(.*?)</li>',
      dotAll: true,
      caseSensitive: false,
    );
    return liRegExp
        .allMatches(ulContent)
        .map((match) => match.group(1)!)
        .toList();
  }

  Course? _parseCourseEntry(String liContent, int dayOfWeek, int index) {
    final pMatch = RegExp(
      r'<p\b[^>]*>(.*?)</p>',
      dotAll: true,
      caseSensitive: false,
    ).firstMatch(liContent);
    if (pMatch == null) return null;

    final pContent = pMatch.group(1)!;
    final spanMatch = RegExp(
      r"""<span\b[^>]*\bclass\s*=\s*["'][^"']*\bclass_span\b[^"']*["'][^>]*>(.*?)</span>""",
      dotAll: true,
      caseSensitive: false,
    ).firstMatch(pContent);
    if (spanMatch == null) return null;

    final sectionText = _stripHtmlTags(spanMatch.group(1)!).trim();
    final sectionMatch = RegExp(
      r'(\d+)\s*[-—~～至]\s*(\d+)\s*节',
    ).firstMatch(sectionText);
    if (sectionMatch == null) return null;

    final startSection = int.parse(sectionMatch.group(1)!);
    final endSection = int.parse(sectionMatch.group(2)!);

    final afterSpan = pContent.replaceFirst(
      RegExp(
        r"""<span\b[^>]*\bclass\s*=\s*["'][^"']*\bclass_span\b[^"']*["'][^>]*>.*?</span>""",
        dotAll: true,
        caseSensitive: false,
      ),
      '',
    );
    final plainText = _stripHtmlTags(afterSpan).trim();
    final lines = plainText
        .split(RegExp(r'\n'))
        .map((l) => l.trim())
        .where((l) => l.isNotEmpty)
        .toList();

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

    // Accept both the compact form (1-8) and labels such as "第1-8周".
    final weekPart = parts[0]
        .replaceAll('第', '')
        .replaceAll('周', '')
        .replaceAll('[', '')
        .replaceAll(']', '')
        .replaceAll('（', '')
        .replaceAll('）', '')
        .replaceAll('(', '')
        .replaceAll(')', '')
        .trim();
    final weekRanges = weekPart.split(RegExp(r'[,，、]'));

    final allWeeks = <int>[];
    for (final range in weekRanges) {
      final rangeTrimmed = range.trim();
      final rangeMatch = RegExp(
        r'^(\d+)\s*[-—]\s*(\d+)$',
      ).firstMatch(rangeTrimmed);
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

    final isAllOdd =
        uniqueWeeks.isNotEmpty && uniqueWeeks.every((w) => w.isOdd);
    final isAllEven =
        uniqueWeeks.isNotEmpty && uniqueWeeks.every((w) => w.isEven);

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

    final isContiguous =
        uniqueWeeks.length == (uniqueWeeks.last - uniqueWeeks.first + 1);
    if (isContiguous) {
      return _WeekInfo(startWeek: uniqueWeeks.first, endWeek: uniqueWeeks.last);
    }

    return _WeekInfo(
      startWeek: uniqueWeeks.first,
      endWeek: uniqueWeeks.last,
      customWeeks: uniqueWeeks,
    );
  }

  String _stripHtmlTags(String html) {
    var text = html
        .replaceAll(RegExp(r'<br\s*/?>', caseSensitive: false), '\n')
        .replaceAll(RegExp(r'<[^>]*>'), '')
        .replaceAll('&amp;', '&')
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .replaceAll('&nbsp;', ' ')
        .replaceAll('&#39;', "'")
        .replaceAll('&quot;', '"');
    text = text.replaceAllMapped(
      RegExp(r'&#x([0-9a-f]+);', caseSensitive: false),
      (m) => String.fromCharCode(int.parse(m.group(1)!, radix: 16)),
    );
    text = text.replaceAllMapped(
      RegExp(r'&#(\d+);'),
      (m) => String.fromCharCode(int.parse(m.group(1)!)),
    );
    return text;
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
