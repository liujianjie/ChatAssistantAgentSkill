# 性能基线 — Style Mirror Copilot

> 任务：[plan.md T22](../../tasks/plan.md#t22)
> 三项 SPEC §1.4 性能目标：
> 1. 候选生成 P95 ≤ 3s（端到端，含 LLM）
> 2. 批量导入 10k 条 ≤ 60s（不含画像 LLM）
> 3. 应用冷启动 < 2s

---

## 1. 测量分层

性能压力分布**不均**，应当分层量测，不要试图用单一基准笼统覆盖：

| 层 | 占比（粗估） | 测量手段 | 当前状态 |
|---|---|---|---|
| LLM 调用（候选生成 / 画像合并） | ~95% 候选生成耗时 | 真机 + 真 API stopwatch | 待 T23 自用阶段抽样 |
| OCR 推理（截图导入） | ~70% 截图链路 | MacroBenchmark + 真机 | 模板已就位（§3） |
| 算法 CPU（cleaner / aligner / sampler） | < 1% | JVM 微基准（CI 跑） | ✅ 已基线（§2） |
| 应用冷启动 | — | MacroBenchmark + 真机 | 模板已就位（§3） |
| Room 加密读写 | 少（典型 < 10ms） | 单测计时 | 暂不优先 |

## 2. JVM 微基准（已基线）

**测试**：`feature-import/src/test/.../benchmark/ImportPipelineBenchmarkTest.kt`
**跑法**：`./gradlew :feature-import:test --info | grep "\[bench\]"`
**机型**：开发机 Windows 10 / JVM 17

| 流水线 | 输入 | 阶段耗时 | 总耗时 | 预算 | 状态 |
|---|---|---|---|---|---|
| cleaner→aligner→sampler | 10 000 条 | clean=60ms, align=7ms, sample=8ms | **75ms** | 3 000ms | ✅ 远超达标 |
| cleaner→aligner→sampler | 1 000 条 | — | **6ms** | 500ms | ✅ |

**结论**：CPU 算法层不是瓶颈。MessageCleaner 占了 ~80% 的 CPU 时间（filter+merge+normalize 三轮遍历）；如果未来确实要优化，先动它，但目前完全没必要。

**护栏**：上述阈值（10k < 3s, 1k < 500ms）落在测试断言里，回归会 CI 失败。

## 3. MacroBenchmark — 真机基线（待跑）

> 这部分需要一台 connected Android 设备（实机或硬件加速 + Profileable 的模拟器），当前开发机暂时无设备。下面是设备就位后**一次性**拉起 macrobench 的完整模板。

### 3.1 新增 :benchmark 模块

**`settings.gradle.kts`**：
```kotlin
include(":benchmark")
```

**`benchmark/build.gradle.kts`**（新建）：
```kotlin
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.stylemirror.benchmark"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
}
```

**`app/build.gradle.kts`** 追加：
```kotlin
android {
    buildTypes {
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isMinifyEnabled = false
            isProfileable = true
        }
    }
}
```

### 3.2 StartupBench

**`benchmark/src/main/kotlin/com/stylemirror/benchmark/StartupBench.kt`**：
```kotlin
package com.stylemirror.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBench {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun coldStartup() = rule.measureRepeated(
        packageName = "com.stylemirror.app",
        metrics = listOf(androidx.benchmark.macro.StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
```

### 3.3 CandidateGenerationBench

> 候选生成走真实 LLM，对耗时的方差极大（网络 + 服务器负载）。建议 macrobench 跑的是 **App 内的 UI 操作链路**（点击"生成" → 候选展示），不要用 macrobench 验证 LLM 自身延迟，那个用 T23 自用阶段的人工 stopwatch 抽 20 次更现实。

```kotlin
@Test fun candidatePresentationLatency() = rule.measureRepeated(
    packageName = "com.stylemirror.app",
    metrics = listOf(FrameTimingMetric()),
    iterations = 3,
    startupMode = StartupMode.WARM,
) {
    startActivityAndWait()
    device.findObject(By.text("生成候选回复")).click()
    device.wait(Until.hasObject(By.textContains("候选回复")), 8_000)
}
```

### 3.4 跑测命令

```bash
# 1. 装 benchmark variant：
./gradlew :app:assembleBenchmark

# 2. 装基准 APK：
./gradlew :benchmark:connectedBenchmarkAndroidTest -Pmacrobench=true

# 3. 报告路径：
# benchmark/build/outputs/connected_android_test_additional_output/
```

## 4. 三项目标的当前评估

| 目标 | 状态 | 说明 |
|---|---|---|
| 候选生成 P95 ≤ 3s | 待真机抽样 | 受 DeepSeek 服务端延迟主导，CPU 部分忽略不计 |
| 10k 导入 ≤ 60s（不含画像） | ✅ JVM 75ms — 远超达标 | OCR 截图批量导入未量测，T19 已限流但未压测 |
| 冷启动 < 2s | 待 macrobench | 主要看 Hilt 启动 + Room SQLCipher key 派生 |

## 5. 已知潜在风险

1. **Room SQLCipher 首次 key 派生**：`AppModule.provideDatabase` 用 `runBlocking` 同步取 passphrase。Tink I/O 通常 < 50ms，但首启动有 keystore 初始化开销。如果冷启动测出 > 1.5s，先看这里。
2. **Onboarding 的 LLM 画像调用**：不在 P95 候选目标内，但用户主观感受强。建议 T23 自用时单独记一笔时间。
3. **OCR 批量截图**：50 张未压测，T19 acceptance 是"≤ 60s"，麻烦的话先做 5/10/20 张三档抽样而不是直接打满 50。

## 6. CI 接入

JVM 微基准（§2）随 `:feature-import:test` 在每次 push 自动跑，断言阈值挂在测试里。MacroBenchmark **不进 CI**（按 plan.md T22 决策），只在每个里程碑或可见性能改动后人工跑一次。
