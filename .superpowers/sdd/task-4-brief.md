### Task 4: 全量验证

**Files:** 无改动。

- [ ] **Step 1: 静态分析**

Run: `flutter analyze lib\models\timetable_settings.dart lib\screens\live_settings_subpages.dart lib\screens\timetable_settings_screen.dart test\widgets\hyperfocus_timing_screen_test.dart`
Expected: `No issues found!`（或仅有改动前已存在的 info 级提示，无 error/warning）。

- [ ] **Step 2: 全量测试**

Run: `flutter test`
Expected: 全部 PASS。

- [ ] **Step 3: Android 构建确认**

Run: `.\gradlew assembleDebug`（工作目录 `C:\daima\zwg\mikcb\mikcb-ECJTU\android`）
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交收尾（如 Step 1-3 有格式修正则一并提交）**

```bash
git status
```

Expected: 工作区干净（除未跟踪文件外）。如有改动，提交并说明。
