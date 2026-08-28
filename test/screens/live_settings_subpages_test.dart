import 'package:flutter_test/flutter_test.dart';
import 'package:university_timetable/screens/live_settings_subpages.dart';

void main() {
  // 与 HyperFocusTemplates.resolveTemplate 的 Kotlin 单测同构的变量表，
  // 两侧语义必须逐条一致（同步纪律：预览 = 正式渲染）。
  const variables = <String, String>{
    '课名': '高等数学',
    '短课名': '高数',
    '教室': 'A101',
    '教师': '张老师',
    '开始': '08:00',
    '结束': '09:40',
    '倒计时': '45:00',
    '正计时': '',
  };

  group('resolveHyperFocusPreviewTemplate 花括号模式', () {
    test('替换全部已知变量', () {
      expect(
        resolveHyperFocusPreviewTemplate('{课名} {开始}-{结束}', variables),
        '高等数学 08:00-09:40',
      );
      expect(
        resolveHyperFocusPreviewTemplate('{短课名}@{教室}', variables),
        '高数@A101',
      );
    });

    test('空值变量解析为空串且不剔除周围文字（与 Kotlin replaceAll 一致）', () {
      expect(
        resolveHyperFocusPreviewTemplate('上课中 {正计时}', variables),
        '上课中 ',
      );
    });

    test('未知花括号内容原样保留', () {
      expect(resolveHyperFocusPreviewTemplate('{未知}课', variables), '{未知}课');
    });
  });

  group('resolveHyperFocusPreviewTemplate 逗号列表模式', () {
    test('按空格连接非空解析结果', () {
      expect(
        resolveHyperFocusPreviewTemplate('距离下课,倒计时', variables),
        '距离下课 45:00',
      );
    });

    test('剔除空 token 与解析为空的项', () {
      expect(resolveHyperFocusPreviewTemplate(' 正计时 ,,课名 ', variables), '高等数学');
    });

    test('无逗号的普通文本原样返回', () {
      expect(resolveHyperFocusPreviewTemplate('已下课', variables), '已下课');
    });

    test('纯 token 列表全部命中变量', () {
      expect(
        resolveHyperFocusPreviewTemplate('教师,教室,开始,结束', variables),
        '张老师 A101 08:00 09:40',
      );
    });
  });
}
