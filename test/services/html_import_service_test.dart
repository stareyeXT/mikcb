import 'package:flutter_test/flutter_test.dart';
import 'package:university_timetable/services/html_import_service.dart';

void main() {
  group('HtmlImportService', () {
    late HtmlImportService service;

    setUp(() {
      service = HtmlImportService();
    });

    test('parses ecjtu calendar html with multiple courses', () {
      const html = '''
<!DOCTYPE html>
<html lang="en">
<head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>我的日历</title>
</head>
<body>
<div class="center">
  <p>2026-04-01 星期三（第5周）</p>
</div>
<div class="top">
  <div class="calendar">
    <ul class="rl_info">
      <li>
        <p>
          <span class="class_span">1-2节<br> </span>
          软件测试技术(上课)
          <br>
          时间：1-8 1,2
          <br>
          地点：31-301
          <br>
          教师：吕敬钦
          <br>
        </p>
      </li>
      <li>
        <p>
          <span class="class_span">7-8节<br> </span>
          体育IⅤ(上课)
          <br>
          时间：1-8,10-19 7,8
          <br>
          地点：北区田径场10
          <br>
          教师：周丽英
          <br>
        </p>
      </li>
      <li>
        <p>
          <span class="class_span">9-10节<br> </span>
          软件测试技术(实验)
          <br>
          时间：5 9,10
          <br>
          地点：软件测试室(教35栋102)
          <br>
          教师：吕敬钦
          <br>
        </p>
      </li>
    </ul>
  </div>
</div>
</body>
</html>
''';

      final result = service.parseHtml(html, sourceUrl: 'https://example.com');

      expect(result.courses.length, 3);

      final course1 = result.courses[0];
      expect(course1.name, '软件测试技术(上课)');
      expect(course1.dayOfWeek, 3);
      expect(course1.startSection, 1);
      expect(course1.endSection, 2);
      expect(course1.startWeek, 1);
      expect(course1.endWeek, 8);
      expect(course1.isOddWeek, false);
      expect(course1.isEvenWeek, false);
      expect(course1.customWeeks, isNull);
      expect(course1.teacher, '吕敬钦');
      expect(course1.location, '31-301');

      final course2 = result.courses[1];
      expect(course2.name, '体育IⅤ(上课)');
      expect(course2.dayOfWeek, 3);
      expect(course2.startSection, 7);
      expect(course2.endSection, 8);
      expect(course2.startWeek, 1);
      expect(course2.endWeek, 19);
      expect(course2.customWeeks, isNotNull);
      expect(course2.customWeeks!.length, 18);
      expect(course2.customWeeks!.first, 1);
      expect(course2.customWeeks!.last, 19);
      expect(course2.customWeeks!.contains(9), false);
      expect(course2.teacher, '周丽英');
      expect(course2.location, '北区田径场10');

      final course3 = result.courses[2];
      expect(course3.name, '软件测试技术(实验)');
      expect(course3.startSection, 9);
      expect(course3.endSection, 10);
      expect(course3.startWeek, 5);
      expect(course3.endWeek, 5);
      expect(course3.isOddWeek, true);
      expect(course3.customWeeks, isNull);
    });

    test('returns empty when no rl_info ul found', () {
      const html = '<html><body><p>No courses here</p></body></html>';
      final result = service.parseHtml(html);
      expect(result.courses, isEmpty);
    });

    test('returns empty when no date found', () {
      const html = '''
<html><body>
<div class="calendar">
  <ul class="rl_info">
    <li><p><span class="class_span">1-2节</span>Test<br>时间：1-8 1,2<br>地点：A101<br>教师：张三</p></li>
  </ul>
</div>
</body></html>
''';
      final result = service.parseHtml(html);
      expect(result.courses, isEmpty);
    });

    test('parses weekday from Chinese text when no date available', () {
      const html = '''
<html><body>
<div class="center"><p>星期五（第3周）</p></div>
<div class="calendar">
  <ul class="rl_info">
    <li><p><span class="class_span">3-4节<br> </span>高等数学<br>时间：1-16 3,4<br>地点：A101<br>教师：李四</p></li>
  </ul>
</div>
</body></html>
''';
      final result = service.parseHtml(html);
      expect(result.courses.length, 1);
      expect(result.courses[0].dayOfWeek, 5);
    });

    test('accepts attributes, full-width punctuation, and labelled weeks', () {
      const html = '''
<html><body>
<section><div id="date" class="calendar center"><p>2026-08-31 星期一（第1周）</p></div></section>
<ul data-role="courses" class="foo rl_info bar">
  <li data-index="1"><p><span data-x="1" class="foo class_span">1～2节<br></span>
    操作系统&nbsp;(上课)<br>时间：第1-8周 1,2<br>地点：31-530<br>教师：叶云青</p></li>
</ul>
</body></html>''';

      final result = service.parseHtml(html);
      expect(result.courses, hasLength(1));
      expect(result.courses.single.name, '操作系统 (上课)');
      expect(result.courses.single.startSection, 1);
      expect(result.courses.single.endSection, 2);
      expect(result.courses.single.startWeek, 1);
      expect(result.courses.single.endWeek, 8);
    });

    test('parses single week as odd week', () {
      const html = '''
<html><body>
<div class="center"><p>2026-04-01 星期三（第5周）</p></div>
<div class="calendar">
  <ul class="rl_info">
    <li><p><span class="class_span">1-2节<br> </span>选修课<br>时间：3 1,2<br>地点：B202<br>教师：王五</p></li>
  </ul>
</div>
</body></html>
''';
      final result = service.parseHtml(html);
      expect(result.courses.length, 1);
      expect(result.courses[0].startWeek, 3);
      expect(result.courses[0].endWeek, 3);
      expect(result.courses[0].isOddWeek, true);
    });

    test('parses even week range correctly', () {
      const html = '''
<html><body>
<div class="center"><p>2026-04-01 星期三（第5周）</p></div>
<div class="calendar">
  <ul class="rl_info">
    <li><p><span class="class_span">1-2节<br> </span>双周课<br>时间：2,4,6,8 1,2<br>地点：C303<br>教师：赵六</p></li>
  </ul>
</div>
</body></html>
''';
      final result = service.parseHtml(html);
      expect(result.courses.length, 1);
      expect(result.courses[0].startWeek, 2);
      expect(result.courses[0].endWeek, 8);
      expect(result.courses[0].isEvenWeek, true);
    });
  });
}
