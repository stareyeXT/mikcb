### Task 2: Kotlin 依赖 — 添加 JitPack 和 HyperFocusApi

**Files:**
- Modify: `android/build.gradle` (line 10)
- Modify: `android/app/build.gradle` (line 142)

- [ ] **Step 1: 在 `android/build.gradle` 的 `allprojects.repositories` 中添加 JitPack**

```groovy
        maven { url 'https://jitpack.io' }
```

插入到 `maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }` 之后。

- [ ] **Step 2: 在 `android/app/build.gradle` 的 `dependencies` 中添加**

```groovy
    implementation 'com.github.ghhccghk:HyperFocusApi:2.0'
```

- [ ] **Step 3: 验证 Gradle 同步**

Run: `cd android && gradlew app:dependencies --configuration implementationClasspath`
Expected: 无错误，能看到 HyperFocusApi 依赖

- [ ] **Step 4: Commit**

```bash
git add android/build.gradle android/app/build.gradle
git commit -m "build: add JitPack repository and HyperFocusApi dependency"
```

---


