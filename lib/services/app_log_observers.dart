import 'dart:async';

import 'package:flutter/material.dart';

import 'app_log_service.dart';

class AppLifecycleLogObserver with WidgetsBindingObserver {
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    unawaited(
      AppLogService.instance.info(
        'app_lifecycle_state_changed',
        'App lifecycle changed',
        extras: {'state': state.name},
      ),
    );
  }
}
