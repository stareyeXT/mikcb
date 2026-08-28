import 'package:flutter/material.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:university_timetable/ui/hyperos/hyperos.dart';

Widget _wrap(Widget child) => MiuixTheme(
      data: MiuixThemeData.light(),
      child: MaterialApp(
        home: Scaffold(body: Center(child: SizedBox(width: 320, child: child))),
      ),
    );

/// hint 由 InputDecorator 包在 AnimatedOpacity 里淡入淡出，子树始终在树中，
/// 只有 opacity 能区分"看得见"和"已隐藏"。
Finder _hintFade(String text) => find.ancestor(
      of: find.text(text),
      matching: find.byType(AnimatedOpacity),
    );

void main() {
  testWidgets('只传 hint 时：空输入显示灰色提示', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _wrap(
        HyperosTextField(
          controller: controller,
          hint: '例如：复习高等数学',
        ),
      ),
    );

    expect(tester.widget<AnimatedOpacity>(_hintFade('例如：复习高等数学')).opacity, 1.0);
  });

  testWidgets('只传 hint 时：输入后提示隐藏，不与输入内容重叠', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _wrap(HyperosTextField(controller: controller, hint: '课程名称')),
    );
    expect(tester.widget<AnimatedOpacity>(_hintFade('课程名称')).opacity, 1.0);

    controller.text = '高等数学';
    await tester.pumpAndSettle();

    // 回归点：修复前灰色提示残留原位，与输入的文字叠在一起。
    expect(tester.widget<AnimatedOpacity>(_hintFade('课程名称')).opacity, 0.0);
    expect(find.text('高等数学'), findsOneWidget);
  });

  testWidgets('只传 label 时：输入后标签浮动到输入行上方', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _wrap(HyperosTextField(controller: controller, label: '课程名称')),
    );
    expect(find.text('课程名称'), findsOneWidget);

    controller.text = '高等数学';
    await tester.pumpAndSettle();

    expect(find.text('课程名称'), findsOneWidget);
    final labelTop = tester.getTopLeft(find.text('课程名称')).dy;
    final inputTop = tester.getTopLeft(find.byType(EditableText)).dy;
    expect(labelTop, lessThan(inputTop));
  });

  testWidgets('多行输入框同样不残留提示', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _wrap(
        HyperosTextField(
          controller: controller,
          hint: '备注',
          minLines: 1,
          maxLines: 4,
        ),
      ),
    );
    expect(tester.widget<AnimatedOpacity>(_hintFade('备注')).opacity, 1.0);

    controller.text = '带教材';
    await tester.pumpAndSettle();

    expect(tester.widget<AnimatedOpacity>(_hintFade('备注')).opacity, 0.0);
    expect(find.text('带教材'), findsOneWidget);
  });
}
