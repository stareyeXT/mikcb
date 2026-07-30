### Task 6: UI — HyperFocusApi 专属设置页 + 测试页

**Files:**
- Modify: `lib/screens/live_settings_subpages.dart`

- [ ] **Step 1: 在文件末尾添加 `HyperFocusTimingScreen`**

```dart
class HyperFocusTimingScreen extends StatefulWidget {
  const HyperFocusTimingScreen({super.key});

  @override
  State<HyperFocusTimingScreen> createState() => _HyperFocusTimingScreenState();
}

class _HyperFocusTimingScreenState extends State<HyperFocusTimingScreen> {
  late TimetableSettings _draft;

  @override
  void initState() {
    super.initState();
    _draft = context.read<TimetableProvider>().settings;
  }

  void _updateDraft(TimetableSettings next) {
    final provider = context.read<TimetableProvider>();
    provider.updateTimetableSettings(next);
    setState(() => _draft = next);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: const Text('提醒时机'),
      child: HyperosListView(
        itemCount: 1,
        itemBuilder: (context, index) => HyperosListGroup(
          children: [
            HyperosSwitchTile(
              title: '课前提醒',
              value: _draft.hfEnableBeforeClass,
              onChanged: (v) => _updateDraft(
                _draft.copyWith(hfEnableBeforeClass: v),
              ),
            ),
            HyperosSwitchTile(
              title: '课中提醒',
              value: _draft.hfEnableDuringClass,
              onChanged: (v) => _updateDraft(
                _draft.copyWith(hfEnableDuringClass: v),
              ),
            ),
            HyperosSwitchTile(
              title: '课后提醒',
              value: _draft.hfEnableBeforeEnd,
              onChanged: (v) => _updateDraft(
                _draft.copyWith(hfEnableBeforeEnd: v),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 2: 添加 `HyperFocusDisplayScreen`**

```dart
class HyperFocusDisplayScreen extends StatefulWidget {
  const HyperFocusDisplayScreen({super.key});

  @override
  State<HyperFocusDisplayScreen> createState() => _HyperFocusDisplayScreenState();
}

class _HyperFocusDisplayScreenState extends State<HyperFocusDisplayScreen> {
  late TimetableSettings _draft;

  @override
  void initState() {
    super.initState();
    _draft = context.read<TimetableProvider>().settings;
  }

  void _updateDraft(TimetableSettings next) {
    final provider = context.read<TimetableProvider>();
    provider.updateTimetableSettings(next);
    setState(() => _draft = next);
  }

  @override
  Widget build(BuildContext context) {
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: const Text('显示设置'),
      child: HyperosListView(
        itemCount: 1,
        itemBuilder: (context, index) => HyperosListGroup(
          children: [
            HyperosSwitchTile(
              title: '显示课名',
              value: _draft.hfShowCourseName,
              onChanged: (v) => _updateDraft(
                _draft.copyWith(hfShowCourseName: v),
              ),
            ),
            HyperosSwitchTile(
              title: '显示地点',
              value: _draft.hfShowLocation,
              onChanged: (v) => _updateDraft(
                _draft.copyWith(hfShowLocation: v),
              ),
            ),
            HyperosSwitchTile(
              title: '显示倒计时',
              value: _draft.hfShowCountdown,
              onChanged: (v) => _updateDraft(
                _draft.copyWith(hfShowCountdown: v),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 3: 添加 `HyperFocusTestScreen`**

```dart
class HyperFocusTestScreen extends StatefulWidget {
  const HyperFocusTestScreen({super.key});

  @override
  State<HyperFocusTestScreen> createState() => _HyperFocusTestScreenState();
}

class _HyperFocusTestScreenState extends State<HyperFocusTestScreen> {
  final List<String> _logs = [];
  bool _isSending = false;

  void _addLog(String msg) {
    final time = TimeOfDay.now().format(context);
    setState(() => _logs.insert(0, '[$time] $msg'));
  }

  Future<void> _sendTestNotification() async {
    setState(() => _isSending = true);
    _addLog('正在发送测试通知...');
    final service = MiuiLiveActivitiesService();
    try {
      final success = await service.sendTestFocusNotification();
      if (success) {
        _addLog('测试通知已发送 ✓');
      } else {
        _addLog('发送失败 ✗');
      }
    } catch (e) {
      _addLog('发送异常：$e');
    }
    setState(() => _isSending = false);
  }

  @override
  Widget build(BuildContext context) {
    return HyperosSubpage(
      onBack: () => Navigator.pop(context),
      title: const Text('测试'),
      child: HyperosListView(
        itemCount: 1,
        itemBuilder: (context, index) => Column(
          children: [
            HyperosListGroup(
              children: [
                HyperosListTile(
                  icon: Icons.send_rounded,
                  title: '发送测试通知',
                  subtitle: '发送一条硬编码的焦点通知到超级岛',
                  onTap: _isSending ? null : _sendTestNotification,
                ),
              ],
            ),
            const HyperosSectionGap(),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: HyperosSectionLabel('操作日志'),
            ),
            Expanded(
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 16),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: _logs.isEmpty
                    ? const Center(
                        child: Text(
                          '暂无日志，点击上方按钮发送测试通知',
                          style: TextStyle(color: Colors.grey, fontSize: 13),
                        ),
                      )
                    : ListView.separated(
                        itemCount: _logs.length,
                        separatorBuilder: (_, __) => const Divider(height: 1),
                        itemBuilder: (context, index) => Padding(
                          padding: const EdgeInsets.symmetric(vertical: 4),
                          child: Text(
                            _logs[index],
                            style: const TextStyle(
                              fontFamily: 'monospace',
                              fontSize: 12,
                            ),
                          ),
                        ),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 4: 验证**

Run: `dart analyze lib/screens/live_settings_subpages.dart`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add lib/screens/live_settings_subpages.dart
git commit -m "feat: add HyperFocusApi timing/display/test settings screens"
```


