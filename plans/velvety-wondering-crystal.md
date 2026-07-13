# 文件结构优化方案

## Context

对"轻屿课表"Flutter项目进行文件结构优化，提升项目可维护性和组织清晰度。项目整体结构良好，主要问题包括：自动生成的Flutter工具文件被git追踪、docs目录混合网站内容和内部文档、根目录存在重复文件、部分代码文件过大需要拆分。

---

## Phase 1: Git 清理 — 取消追踪自动生成的文件

**.gitignore** 新增两行（Flutter/Dart/Pub related 区块）：
```
.flutter
.flutter_tool_state
```

然后从 git 追踪中移除：
```
git rm --cached .flutter .flutter_tool_state
```

这些文件由 Flutter 工具自动生成，每次运行都会变化，不应纳入版本控制。

---

## Phase 2: 拆分 `lib/main.dart` — 提取主题函数和观察者

`main.dart` 当前 616 行，包含 5 个独立关注点。提取以下部分为新文件：

### 2a. 创建 `lib/theme/app_theme.dart`

从 main.dart 提取 5 个主题辅助函数，改为公开函数：
- `colorFromHex`（原 `_colorFromHex`）
- `themeModeFromSettings`（原 `_themeModeFromSettings`）
- `fontFamilyFromSettings`（原 `_fontFamilyFromSettings`）
- `localeFromSettings`（原 `_localeFromSettings`）
- `buildAppTheme`（原 `_buildAppTheme`）

### 2b. 创建 `lib/services/app_log_observers.dart`

提取两个观察者类，改为公开：
- `AppLifecycleLogObserver`（原 `_AppLifecycleLogObserver`）
- `AppRouteLogObserver`（原 `_AppRouteLogObserver`）

### 2c. 更新 main.dart

删除已提取的代码和旧 import，添加新 import。`main.dart` 从 616 行缩减至 ~440 行。

> **不提取 AppEntryScreen**（启动流程 ~290 行）：它与 Navigator 上下文紧耦合，提取需要复杂接口，风险大于收益。

---

## Phase 3: 将内部规划文档移出 `docs/`

`docs/` 同时作为 GitHub Pages 网站源目录和项目文档目录。内部实现计划不应公开发布在网站上。

创建根目录 `plans/`，将 `docs/plans/` 下的 5 个规划文件移入：
- `plans/2026-03-22-multi-timetable-implementation-plan.md`
- `plans/2026-03-22-school-adaptation-plan.md`
- `plans/2026-03-22-time-scheme-and-conflict-badge-plan.md`
- `plans/2026-03-24-home-widget-implementation-plan.md`
- `plans/2026-04-05-qingyu-warehouse-integration-plan.md`

删除 `docs/plans/` 目录。

---

## Phase 4: 删除重复的 `docs/CONTRIBUTING.md`

`docs/CONTRIBUTING.md` 与根目录 `CONTRIBUTING.md` 内容完全一致（byte-identical），是重复文件。网站已有 `contributing.html` 作为规范页面。

---

## Phase 5 (可选): 分组导入服务

将 4 个课程导入相关服务移入子目录 `lib/services/import/`：

- `ai_course_import_service.dart`
- `html_import_service.dart`
- `ics_import_service.dart`
- `import_week_alignment_service.dart`

需要更新 6 个源文件的 import 路径（`lib/providers/timetable_provider.dart`、`lib/screens/course_import_screen.dart` 和 4 个对应测试文件）。

---

## 不动的内容

| 内容 | 不动的原因 |
|------|-----------|
| `lib/screens/` 分组为子目录 | 17 个文件尚可管理，跨文件 import 多，分组后 import 路径更长 |
| `docs/releases/` | GitHub Actions 工作流明确引用这些路径 |
| 网站文件移出 `docs/` | GitHub Pages 配置为从 `docs/` 部署，`CNAME` 文件确认 |
| 其他文档移出 `docs/` | 这些文件作为网站内容通过 GitHub Pages 发布 |
| `lib/providers/`、`widgets/`、`utils/`、`models/` | 各 1-6 个文件，规模适中 |
| `android/release-key.jks` | 已在 `.gitignore` 中，未追踪（仅存在于工作目录） |

---

## 验证步骤

每个阶段完成后运行：
```
flutter analyze --no-fatal-infos
flutter test
```

Phase 5 还需额外验证所有 import 路径正确解析。
