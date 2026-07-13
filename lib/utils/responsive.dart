import 'package:flutter/material.dart';

/// Breakpoints for tablet / wide-tablet detection.
/// Inspired by Material Design 3 layout guidelines.
class AppBreakpoints {
  AppBreakpoints._();

  /// Width >= 600dp: small tablet (portrait) or large phone landscape.
  static const double tablet = 600;

  /// Width >= 900dp: large tablet portrait or small tablet landscape.
  static const double wideTablet = 900;
}

/// Convenience extensions on [BuildContext] for responsive queries.
extension ResponsiveContext on BuildContext {
  /// Current screen width in logical pixels.
  double get screenWidth => MediaQuery.of(this).size.width;

  /// Current screen height in logical pixels.
  double get screenHeight => MediaQuery.of(this).size.height;

  /// Whether the device is in landscape orientation.
  bool get isLandscape =>
      MediaQuery.of(this).orientation == Orientation.landscape;

  /// Whether the screen is at least tablet width (>= 600dp).
  bool get isTablet => screenWidth >= AppBreakpoints.tablet;

  /// Whether the screen is at least wide-tablet width (>= 900dp).
  bool get isWideTablet => screenWidth >= AppBreakpoints.wideTablet;

  /// True when the device should use a dual-pane / master-detail layout:
  /// wide enough (>= 900dp) OR landscape on a tablet (>= 600dp wide).
  bool get useDualPane =>
      screenWidth >= AppBreakpoints.wideTablet ||
      (isLandscape && isTablet);
}

/// A wrapper that constrains content to a readable width on large screens.
///
/// On screens narrower than [maxWidth] the content fills available space.
/// On wider screens it is centered with a max-width, optionally with
/// horizontal padding.
class AdaptiveMaxWidth extends StatelessWidget {
  const AdaptiveMaxWidth({
    super.key,
    this.maxWidth = 700,
    this.horizontalPadding = 24,
    required this.child,
  });

  final double maxWidth;
  final double horizontalPadding;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.of(context).size.width;
    if (width <= maxWidth) {
      return child;
    }
    return Center(
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        child: Padding(
          padding: EdgeInsets.symmetric(horizontal: horizontalPadding),
          child: child,
        ),
      ),
    );
  }
}

/// A Scaffold wrapper that switches between single-pane and dual-pane
/// layouts based on screen width.
///
/// Use [dualPane] as a builder that receives the NarrowContent widget
/// to use as the detail pane on wide screens.
class AdaptiveScaffold extends StatelessWidget {
  const AdaptiveScaffold({
    super.key,
    required this.narrowBody,
    required this.dualPaneDetail,
    this.appBar,
    this.backgroundColor,
    this.resizeToAvoidBottomInset,
    this.drawer,
    this.endDrawer,
    this.floatingActionButton,
  });

  /// The body for narrow (single-pane) screens.
  final Widget narrowBody;

  /// Builder for wide dual-pane screens. Receives a pre-built
  /// [NarrowLayoutDetail] wrapper that renders [narrowBody] as the
  /// left/primary pane, and the returned widget as the right/secondary pane.
  ///
  /// Example:
  /// ```dart
  /// AdaptiveScaffold(
  ///   narrowBody: settingsList,
  ///   dualPaneDetail: SettingsDetailScreen(...),
  /// )
  /// ```
  final Widget dualPaneDetail;

  final PreferredSizeWidget? appBar;
  final Color? backgroundColor;
  final bool? resizeToAvoidBottomInset;
  final Widget? drawer;
  final Widget? endDrawer;
  final Widget? floatingActionButton;

  @override
  Widget build(BuildContext context) {
    final isWide = context.useDualPane;

    if (!isWide) {
      return Scaffold(
        appBar: appBar,
        backgroundColor: backgroundColor,
        resizeToAvoidBottomInset: resizeToAvoidBottomInset,
        drawer: drawer,
        endDrawer: endDrawer,
        floatingActionButton: floatingActionButton,
        body: narrowBody,
      );
    }

    // Dual-pane: narrowBody in a constrained left column, detail on the right.
    return Scaffold(
      backgroundColor: backgroundColor,
      resizeToAvoidBottomInset: resizeToAvoidBottomInset,
      drawer: drawer,
      endDrawer: endDrawer,
      floatingActionButton: floatingActionButton,
      body: Row(
        children: [
          SizedBox(
            width: 320,
            child: Column(
              children: [
                if (appBar != null) appBar!,
                Expanded(child: narrowBody),
              ],
            ),
          ),
          const VerticalDivider(width: 1),
          Expanded(child: dualPaneDetail),
        ],
      ),
    );
  }
}

/// A simple helper that constrains content to a max width and pads
/// horizontally on wide screens. Useful inside ListView items or
/// simple single-column pages.
///
/// Prefer [AdaptiveMaxWidth] for top-level page wrapping.
Widget wrapWithMaxWidth(BuildContext context, Widget child,
    {double maxWidth = 700}) {
  return AdaptiveMaxWidth(maxWidth: maxWidth, child: child);
}
