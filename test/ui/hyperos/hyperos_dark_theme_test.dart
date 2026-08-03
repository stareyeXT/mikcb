import 'package:flutter/material.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:university_timetable/ui/hyperos/hyperos.dart';

/// Simulates the app root: MaterialApp builder wraps the tree in a
/// [MiuixTheme] matched to the Material brightness (see lib/main.dart).
Widget _appWithMiuixTheme({required ThemeMode themeMode, required Widget home}) {
  return MaterialApp(
    themeMode: themeMode,
    theme: ThemeData(brightness: Brightness.light),
    darkTheme: ThemeData(brightness: Brightness.dark),
    builder: (context, child) {
      return Builder(
        builder: (context) {
          return MiuixTheme(
            data: MiuixThemeData.of(Theme.of(context).brightness),
            child: child!,
          );
        },
      );
    },
    home: home,
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('HyperosColors dark mode', () {
    testWidgets('scaffoldBackground resolves dark value in dark theme', (
      tester,
    ) async {
      Color? background;
      Brightness? brightness;

      await tester.pumpWidget(
        _appWithMiuixTheme(
          themeMode: ThemeMode.dark,
          home: Builder(
            builder: (context) {
              brightness = Theme.of(context).brightness;
              background = HyperosColors.scaffoldBackground(context);
              return const SizedBox.shrink();
            },
          ),
        ),
      );

      expect(brightness, Brightness.dark);
      expect(background, HyperosMiuixDarkColors.background);
    });

    testWidgets('primaryText resolves dark value in dark theme', (
      tester,
    ) async {
      Color? textColor;

      await tester.pumpWidget(
        _appWithMiuixTheme(
          themeMode: ThemeMode.dark,
          home: Builder(
            builder: (context) {
              textColor = HyperosColors.primaryText(context);
              return const SizedBox.shrink();
            },
          ),
        ),
      );

      expect(textColor, HyperosMiuixDarkColors.onBackground);
    });

    testWidgets('scaffoldBackground resolves light value in light theme', (
      tester,
    ) async {
      Color? background;

      await tester.pumpWidget(
        _appWithMiuixTheme(
          themeMode: ThemeMode.light,
          home: Builder(
            builder: (context) {
              background = HyperosColors.scaffoldBackground(context);
              return const SizedBox.shrink();
            },
          ),
        ),
      );

      expect(background, HyperosTokens.background);
    });
  });
}
