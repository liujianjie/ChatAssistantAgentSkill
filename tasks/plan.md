# 实施计划：Style Mirror Copilot（MVP P0）

> 来源：[SPEC.md](../SPEC.md) §7 推荐路径 — Onboarding + 复制粘贴 + DeepSeek 候选 + 反馈环这条最短闭环优先。
> 工作流：spec ✓ → **plan**（本文件） → build。
> 平台/语言：Android、Kotlin 2.x + Jetpack Compose、Java 17 toolchain。

---

## 1. Overview

把 SPEC 拆成 6 个里程碑、24 个垂直切片任务。每个里程碑结束有显式 checkpoint 与人工评审节点。整体策略是：

1. **抽象先行**：5 大跨边界接口（`InputAdapter` / `PlatformAdapter` / `LLMProvider` / `OcrProvider` / `ImportSource`）在第一次需要时立刻定义 + Fake + 测试，再接真实实现。
2. **垂直切片**：每个任务跑通"输入 → 处理 → 输出 → 测试"的完整一段，而不是先搭满数据库再搭满 API。
3. **最短闭环优先**：先用 Fake StyleEngine 跑通 paste→候选→反馈 这条线，再回头做真正的 StyleEngine v1，再补 OCR / Soul。
4. **隐私红线测试化**：FingerprintAggregator 的"绝不混入对方消息"用单测兜底；上送 LLM 的 payload 在测试中断言敏感字段被脱敏。

## 2. Architecture Decisions（沿用 SPEC §1.6 / §3.2）

- **多模块**：10 个应用模块（详见 SPEC §3.1）+ build-logic，按层 + 按能力切分，core-domain 不依赖 Android。
- **DI**：Hilt（与 Compose/ViewModel 集成成熟）；模块间通过接口暴露，build-logic 控制可见性。
- **持久化**：Room + SQLCipher（加密 DB），API Key 走 EncryptedSharedPreferences；不进 settings.json/log/git。
- **LLM 默认 DeepSeek，保留 Claude/Qwen 切换**：通过 `LLMProvider` 抽象 + 运行时可切换的实现注册表。
- **OCR 默认 ML Kit，PaddleOCR/云端 stub 保留接口**：通过 `OcrProvider` 抽象。
- **测试金字塔**：unit（FakeProvider，CI 必跑） + Robolectric/Room in-memory（DB） + Compose UI test（关键路径） + 手动 integrationTest（真实 LLM/OCR，本地手动）。

## 3. Dependency Graph

```
build-logic (gradle convention plugins)
   │
   ├── core-domain ────────────────────────┐
   │      │                                │
   │      ├── core-data (Room+SQLCipher)   │
   │      │      │                         │
   │      │      └── feature-realtime ─────┤
   │      │             │                  │
   │      │             ├── infra-net      │
   │      │             ├── infra-llm ─────┤
   │      │             ├── platform-soul  │
   │      │             └── platform-stub  │
   │      │                                │
   │      └── feature-import ──────────────┤
   │             │                         │
   │             └── infra-ocr ────────────┤
   │                                       │
   └────────────────────── app（Compose UI + DI 装配）
```

构建顺序遵循"叶子模块先行"：build-logic → core-domain → infra-net/infra-llm 抽象 → feature-realtime 最短闭环 → app 装配 → core-data/Room → feature-import → infra-ocr → platform-soul → 反馈环。

## 4. Phase / Task 总览

| Phase | 里程碑 | 任务数 | 预计完成验收 |
|---|---|---|---|
| 1 | M0 项目骨架 | T01–T03 | `./gradlew check` 全绿 + golden fixture 入库 |
| 2 | M1 最短闭环 | T04–T08 | 粘贴对话 → 3 候选 → 一键复制 → 反馈占位写库 |
| 3 | M2 StyleEngine v1 | T09–T15 | 纯文本导入 → 画像 v1 → 实时使用接真画像 |
| 4 | M3 OCR + Soul | T16–T19 | 截图导入 + Soul 实时识别 |
| 5 | M4 反馈环闭合 | T20–T21 | 反馈信号增量更新画像 |
| 6 | M5 自用验证 | T22–T24 | 性能基线 + 自用 1 周观察期 |

---

## 5. 任务清单

### Phase 1 — M0 项目骨架

#### T01：build-logic + 多模块 Gradle 骨架 + CI
**Description**：建立 10 模块的 Gradle 骨架，配 Kotlin 2.x、Java 17 toolchain、ktlint、detekt、Android Lint，CI 入口为 `./gradlew check`。`app` 模块只放空 `Application` + 单一空 Activity。
**Acceptance criteria**：
- [ ] `./gradlew assembleDebug` 成功，APK 可装到模拟器但仅显示空白页
- [ ] `./gradlew check` 全绿（ktlint/detekt/lint/test 已跑，0 失败）
- [ ] 所有模块通过 `build-logic` 的 convention plugin 共享配置（避免每个 build.gradle.kts 重复）
- [ ] `.gitignore` 覆盖 `local.properties` / `*.jks` / `.env*` / `secrets.*`
**Verification**：
- [ ] `./gradlew check` exit 0
- [ ] `./gradlew :app:installDebug` 可装机
- [ ] `git status` 不显示构建产物，且无密钥风险文件
**Dependencies**：None
**Files**：`settings.gradle.kts`、`build.gradle.kts`、`build-logic/**`、各模块 `build.gradle.kts`、`.gitignore`、`.editorconfig`
**Scope**：M

#### T02：core-domain 数据模型 + DomainError
**Description**：在 `core-domain` 实现 SPEC §3 描述的核心数据结构 — `Message`、`Speaker`、`ConversationContext`、`StyleFingerprint` schema（含语言风格/情感表达/幽默类型/回避模式/节奏特征/敏感话题处理 6 维）、`FeedbackSignal`（采纳/修改/丢弃）、`DomainError` sealed class。纯 Kotlin，无 Android 依赖。**禁止把对方消息字段塞进 StyleFingerprint**（用类型隔离，不是注释约束）。
**Acceptance criteria**：
- [ ] 6 维 StyleFingerprint schema 落地为 data class，每维有清晰字段（不用 `Map<String, Any>` 兜底）
- [ ] `ConversationContext` 内 `Message.speaker` 是 `Speaker.Me | Speaker.Other`，聚合到 `StyleFingerprint` 的输入只接受 `List<Message<Speaker.Me>>` 或等价类型保护
- [ ] DomainError 覆盖：LLM 调用失败 / OCR 失败 / 导入失败 / 画像不足 / 用户配额超限
- [ ] 单测覆盖率 ≥ 85%（含等价类划分、边界、不变量）
**Verification**：
- [ ] `./gradlew :core-domain:test` 全绿
- [ ] `./gradlew :core-domain:jacocoTestReport` 覆盖率 ≥ 85%
- [ ] 新增 ADR `docs/adr/0001-style-fingerprint-schema.md` 记录 schema 决策
**Dependencies**：T01
**Files**：`core-domain/src/main/kotlin/com/stylemirror/domain/**`、`core-domain/src/test/...`、`docs/adr/0001-*.md`
**Scope**：M

#### T03：Golden fixtures + 测试基础设施
**Description**：搭建 `core-domain/src/test/resources/golden/` 黄金样本目录，手工构造（非真实聊天）5–10 段标注对话覆盖：跨设备 / 群聊 / 昵称变更 / 长短消息混合 / 表情符号。引入 Kotest assertions、Turbine、coroutines-test 到测试 classpath。
**Acceptance criteria**：
- [ ] 至少 7 段 golden fixture，每段 30+ 条消息，YAML 格式（含 expected speaker / expected style cues）
- [ ] 提供 `GoldenLoader` 工具类供后续算法层复用
- [ ] 写入 `.gitattributes`：fixture 用 LF 换行，避免跨 OS 测试漂移
- [ ] README 说明"禁止把真实聊天纳入 fixture"
**Verification**：
- [ ] `./gradlew :core-domain:test` 包含至少一个加载 fixture 的烟雾测试
- [ ] 人工抽查 fixture：无真实人名 / 手机号 / 身份证号
**Dependencies**：T02
**Files**：`core-domain/src/test/resources/golden/**`、`core-domain/src/test/kotlin/.../GoldenLoader.kt`、`.gitattributes`
**Scope**：S

#### Checkpoint：M0 完成
- [ ] `./gradlew check` 全绿
- [ ] APK 可装机
- [ ] golden fixture 已入库且不含真实数据
- [ ] **人工评审**：模块边界、ADR-0001、隐私类型保护是否合理

---

### Phase 2 — M1 最短闭环（粘贴 → DeepSeek → 3 候选 → 反馈占位）

#### T04：infra-net 基础设施 + 安全密钥存储
**Description**：在 `infra-net` 配 OkHttp/Retrofit、统一超时（候选生成 8s，其它 30s）、重试策略、统一日志拦截器（命中 `key|token|secret|password|authorization` 字段一律 `[REDACTED]`）。在 `core-data/secure` 实现 `SecureKeyStore` 接口，基于 AndroidX Security `EncryptedSharedPreferences`。
**Acceptance criteria**：
- [ ] OkHttp 日志拦截器对敏感 header/body 字段脱敏（单测断言 `[REDACTED]` 出现）
- [ ] `SecureKeyStore.put / get / clear` API + 单测（Robolectric）
- [ ] DeepSeek API Key 仅可通过 `SecureKeyStore` 读取，不暴露给业务层
**Verification**：
- [ ] `./gradlew :infra-net:test :core-data:test` 全绿
- [ ] 单测专门校验"日志中不出现明文 key"
**Dependencies**：T01、T02
**Files**：`infra-net/**`、`core-data/secure/**`
**Scope**：M

#### T05：LLMProvider 抽象 + DeepSeek 实现 + Fake
**Description**：在 `infra-llm` 定义 `LLMProvider` 接口（`generateCandidates(prompt, maxTokens, n): Result<List<Candidate>, DomainError>`），实现：① `FakeLLMProvider`（CI 用，返回固定文本）② `DeepSeekProvider`（调真实 API，从 `SecureKeyStore` 取 Key）③ `ClaudeProvider` / `QwenProvider` 留 TODO stub（保持构造可用，调用抛 `DomainError.NotImplemented`）。
**Acceptance criteria**：
- [ ] `LLMProvider` 接口 provider-agnostic（不暴露 DeepSeek 特有字段）
- [ ] `DeepSeekProvider` 单测用 MockWebServer 覆盖：成功 / 429 / 5xx / 超时 / Key 缺失
- [ ] `FakeLLMProvider` 默认产出 3 候选，可注入自定义响应供其它测试用
- [ ] 集成测试 `:infra-llm:integrationTest`：真 API 调用，需 `STYLEMIRROR_DEEPSEEK_KEY` 环境变量；缺失时 skip 而非 fail
- [ ] ADR `docs/adr/0002-llm-provider-strategy.md`
**Verification**：
- [ ] `./gradlew :infra-llm:test` 全绿
- [ ] CI 不跑 `:integrationTest`（默认 skip 验证）
- [ ] grep 全仓库无明文 API Key
**Dependencies**：T04
**Files**：`infra-llm/**`、`docs/adr/0002-*.md`
**Scope**：M

#### T06：feature-realtime InputAdapter 抽象 + PasteInput 实现
**Description**：在 `feature-realtime/input` 定义 `InputAdapter` 接口（`receive(): Flow<ConversationContext>`）。实现：① `PasteInput`（从剪贴板/输入框文本解析为 ConversationContext，含说话人推断启发式：以"我："开头视为 Me，否则视为 Other，提供用户手工切换钩子）。② `ScreenshotInput` / `OverlayInput` / `ShareSheetInput` 留接口位 + `NotImplemented` 占位（不实现）。
**Acceptance criteria**：
- [ ] `PasteInput` 能解析多行混合发言（包括"对方："/"小张："/无前缀）至 `ConversationContext`
- [ ] 边界覆盖：空输入、单行、超长、含 Emoji、含中英文混排
- [ ] 单测 ≥ 80%
**Verification**：
- [ ] `./gradlew :feature-realtime:test`
- [ ] 用 golden fixture 中至少 2 段验证
**Dependencies**：T02、T03
**Files**：`feature-realtime/input/**`
**Scope**：S

#### T07：候选生成器 + Fake StyleEngine + Prompt 模板
**Description**：在 `feature-realtime/candidate` 实现 `CandidateGenerator`（拼装 prompt → 调 `LLMProvider` → 返回 3 候选 + 风格匹配度占位）。`StyleEngine` 接口先用 `FakeStyleEngine`（返回固定的 stub 风格指纹），让最短闭环跑起来。Prompt 模板做成 provider-agnostic（不写 DeepSeek/Claude 特定 token）。隐私护栏：上送 LLM 的 payload 中对方消息只保留最近 N 条（默认 10），手机号/身份证/银行卡正则脱敏。
**Acceptance criteria**：
- [ ] 输入 ConversationContext + FakeStyleEngine 可在 < 8s 出 3 候选（用 FakeLLMProvider 验证逻辑）
- [ ] 单测断言：发给 LLM 的最终 prompt 中不含手机号/身份证/银行卡数字
- [ ] 单测断言：发给 LLM 的最终 prompt 中对方消息条数 ≤ 10
- [ ] 候选每条带 `styleMatchScore: Float`（来自 FakeStyleEngine）
**Verification**：
- [ ] `./gradlew :feature-realtime:test`
- [ ] 用脱敏单测覆盖隐私护栏
**Dependencies**：T05、T06
**Files**：`feature-realtime/candidate/**`、`feature-realtime/matching/StyleEngine.kt`、`feature-realtime/matching/FakeStyleEngine.kt`
**Scope**：M

#### T08：app Compose UI 最短闭环 + 反馈占位
**Description**：app 模块实装最短闭环 UI：① 设置页输入 DeepSeek API Key（写入 `SecureKeyStore`）② 主页粘贴对话 + "生成候选" 按钮 ③ 候选卡片（3 张，含一键复制）④ 反馈按钮（采纳 / 修改 / 丢弃，本任务先把信号写到内存仓库占位，T20 再接真实持久化）。Hilt 装配；ViewModel 暴露 `StateFlow<UiState>`；Compose 用 `collectAsStateWithLifecycle`。
**Acceptance criteria**：
- [ ] 真机或模拟器跑通：输入 API Key → 粘贴对话 → 看到 3 候选 → 点采纳 → 候选写入剪贴板
- [ ] 反馈信号写入 in-memory `FeedbackBuffer`（接口已对齐 T20）
- [ ] Compose UI test 覆盖关键路径：候选展示、采纳交互
- [ ] `@Preview` 用 fake state 不依赖真实 ViewModel
**Verification**：
- [ ] `./gradlew :app:test :app:connectedDebugAndroidTest`
- [ ] 手动验证：用 FakeLLMProvider 跑通端到端（不耗费真实 API）
- [ ] **手动**用真实 DeepSeek Key 跑一次端到端冒烟（不算 CI 必经）
**Dependencies**：T05、T06、T07
**Files**：`app/src/main/kotlin/com/stylemirror/app/**`、`app/src/main/res/**`、`app/src/androidTest/...`
**Scope**：M

#### Checkpoint：M1 最短闭环完成
- [ ] 端到端冒烟通过（含真实 DeepSeek Key）
- [ ] 候选生成 P95 ≤ 3s（手动用 FakeLLMProvider + 真 API 各跑 20 次抽样）
- [ ] 隐私护栏单测全绿（敏感数字脱敏 + 对方消息条数限流）
- [ ] **人工评审**：UI/UX、Prompt 质量、密钥处理流程

---

### Phase 3 — M2 StyleEngine v1（纯文本导入 + 真画像）

#### T09：core-data Room + SQLCipher 加密 DB
**Description**：实装 Room schema：`messages`（含发送方枚举、对话所属 partner、时间戳）、`style_fingerprints`（带版本号）、`feedback_signals`、`import_sessions`。SQLCipher 派生密钥存 Keystore。第一次启动生成密钥；提供导出空表的 migration 0→1。**禁止 schema 中字段名包含明文 user 信息（如 wechat_id、phone）**。
**Acceptance criteria**：
- [ ] Room migration 0→1 单测（用 in-memory DB）
- [ ] DB 文件在设备上是加密的（手动用 sqlite3 验证打不开）
- [ ] Repository 单测覆盖率 ≥ 70%
- [ ] ADR `docs/adr/0003-encryption-and-storage.md`
**Verification**：
- [ ] `./gradlew :core-data:test`
- [ ] 手动 adb pull DB 用 sqlite3 验证为加密文件
**Dependencies**：T02、T04
**Files**：`core-data/db/**`、`core-data/repository/**`、`docs/adr/0003-*.md`
**Scope**：M

#### T10：ImportSource 抽象 + 纯文本导入
**Description**：在 `feature-import/source` 定义 `ImportSource` 接口（`stream(): Flow<RawMessage>`）。实现 ① `PlainTextImportSource`（用户粘贴或选择 `.txt`）。② 微信 PC 导出 / 微信备份 / 批量截图 / 第三方工具 留接口位 + `NotImplemented` 占位。
**Acceptance criteria**：
- [ ] 纯文本导入支持常见格式（"YYYY-MM-DD HH:mm 昵称：内容"、"昵称: 内容"、纯独立行）
- [ ] 流式处理 10k 条消息内存峰值 ≤ 200MB（用 fixture 跑基准）
- [ ] 5 路 ImportSource 接口定义清晰，未实现的明确抛 `DomainError.NotImplemented`
**Verification**：
- [ ] `./gradlew :feature-import:test`
- [ ] benchmark 任务跑 10k 条消息
**Dependencies**：T02、T09
**Files**：`feature-import/source/**`
**Scope**：M

#### T11：数据清洗（去重、合并、过滤噪声）
**Description**：在 `feature-import/cleaning` 实现 `MessageCleaner`：相邻同发送方消息合并、转账/红包/系统提示/链接卡片过滤、表情符号归一化。规则配置化（YAML 加载，按 source 不同走不同规则）。
**Acceptance criteria**：
- [ ] 至少 4 段 golden fixture 跑清洗前后对比断言
- [ ] 规则配置可热更（测试中替换 YAML）
- [ ] 单测覆盖率 ≥ 80%
**Verification**：
- [ ] `./gradlew :feature-import:test`
**Dependencies**：T03、T10
**Files**：`feature-import/cleaning/**`、`core-domain/src/main/resources/cleaning_rules/*.yaml`
**Scope**：S

#### T12：说话人对齐（大规模）
**Description**：实现 `SpeakerAligner`：跨设备 / 昵称变更 / 群聊场景下识别"我" vs "别人"。算法可先用启发式（用户在 onboarding 指认自己的昵称别名集合）+ 后续可替换 ML。错位率目标 < 2%。**这是 SPEC §1.4 与 P0 头号风险假设**。
**Acceptance criteria**：
- [ ] 7 段 golden fixture（覆盖跨设备/群聊/昵称变更）错位率 < 2%
- [ ] 提供"用户指认昵称"接口供 onboarding 调用
- [ ] 单测覆盖核心算法 ≥ 85%
**Verification**：
- [ ] `./gradlew :feature-import:test`
- [ ] 跑 golden fixture 全集，输出错位率报告
**Dependencies**：T11
**Files**：`feature-import/alignment/**`
**Scope**：M

#### T13：采样与聚合
**Description**：实现 `MessageSampler`：按对象 / 时间窗口 / 话题分桶采样，避免单一对话主导画像。采样后输出 `ProfilingInput`（仅 Me 的消息，附最小情境）。
**Acceptance criteria**：
- [ ] 用 3 个不同对象的子集分别跑，画像核心维度差异 < 阈值（事后由 T14 验证）
- [ ] 采样总条数可配置上限（默认 2000，控制 LLM 成本）
- [ ] 单测覆盖分桶策略
**Verification**：
- [ ] `./gradlew :feature-import:test`
**Dependencies**：T12
**Files**：`feature-import/sampling/**`
**Scope**：S

#### T14：性格画像提取 + 真 StyleEngine
**Description**：在 `feature-import/profiling` 实现 `PersonaProfiler`：拿 `ProfilingInput` 调 `LLMProvider`，按 6 维 schema 输出结构化 `StyleFingerprint`。同时把 `feature-realtime/matching` 中的 `FakeStyleEngine` 替换为 `RoomBackedStyleEngine`（从 DB 读最新版本指纹）。**红线护栏**：构造 LLM payload 时，从类型层面只能拿到 Me 的消息（编译期保护，不靠运行时检查）。
**Acceptance criteria**：
- [ ] 跑 golden fixture：6 维字段全部产出非空
- [ ] 自评命中率 ≥ 75%（人工抽 3 段对照）
- [ ] StyleFingerprint 写入 DB 带版本号；可查询历史版本
- [ ] 单测断言：profiler 的 LLM payload 不含任何 Speaker.Other 消息
**Verification**：
- [ ] `./gradlew :feature-import:test :feature-realtime:test`
- [ ] 手动用真实 DeepSeek Key 跑一段 fixture，肉眼验画像
**Dependencies**：T05、T13
**Files**：`feature-import/profiling/**`、`feature-realtime/matching/RoomBackedStyleEngine.kt`
**Scope**：M

#### T15：Onboarding UI 串联 + 主页接真画像
**Description**：app 加 onboarding 流程：① 引导用户选择 `PlainTextImportSource` ② 用户指认自己的昵称别名 ③ 跑导入 → 清洗 → 对齐 → 采样 → 画像 ④ 展示画像可读化总结（可校准） ⑤ 进入主页。主页候选生成接 `RoomBackedStyleEngine`。
**Acceptance criteria**：
- [ ] 端到端 UI test：导入 fixture → 看到画像总结 → 进入主页 → 候选生成
- [ ] 画像总结可读化（中文，覆盖 6 维）
- [ ] 用户可后续追加导入（不强制重置画像）
**Verification**：
- [ ] `./gradlew :app:connectedDebugAndroidTest`
- [ ] 手动跑通 onboarding（用真实 DeepSeek Key）
**Dependencies**：T08、T14
**Files**：`app/src/main/kotlin/com/stylemirror/app/onboarding/**`
**Scope**：M

#### Checkpoint：M2 完成
- [ ] 7 段 golden fixture 说话人对齐错位率 < 2%
- [ ] 画像自评命中率 ≥ 75%（人工抽样）
- [ ] 隐私编译期保护通过（FingerprintAggregator 类型签名只接受 Me）
- [ ] **人工评审**：画像质量、采样策略合理性、ADR-0001 是否需要修订

---

### Phase 4 — M3 OCR + Soul 平台适配

#### T16：OcrProvider 抽象 + ML Kit 实现
**Description**：在 `infra-ocr` 定义 `OcrProvider` 接口。实现 ① `MlKitOcrProvider`（默认）② `PaddleOcrProvider` / `CloudOcrProvider` 留接口位 + `NotImplemented`。结果用统一 `OcrResult(textBoxes: List<TextBox>)`。
**Acceptance criteria**：
- [ ] 用 5 张 Soul 截图测试图（自制，非真实聊天）识别准确率 ≥ 95%
- [ ] FakeOcrProvider 供 CI 用
- [ ] ADR `docs/adr/0004-ocr-provider-strategy.md`
**Verification**：
- [ ] `./gradlew :infra-ocr:test`
- [ ] 真机跑 5 张测试图
**Dependencies**：T02
**Files**：`infra-ocr/**`、`docs/adr/0004-*.md`
**Scope**：M

#### T17：ScreenshotInput 实现 + 实时使用接 OCR
**Description**：在 `feature-realtime/input` 实装 `ScreenshotInput`（调用 `OcrProvider`）。主页加截图选择/分享入口（系统分享 sheet 留 TODO，本任务只做相册选择）。
**Acceptance criteria**：
- [ ] 用户选截图 → OCR → 进入候选生成流程
- [ ] OCR 失败有用户可读的错误提示（不暴露异常 message）
- [ ] UI test 覆盖
**Verification**：
- [ ] `./gradlew :feature-realtime:test :app:connectedDebugAndroidTest`
**Dependencies**：T08、T16
**Files**：`feature-realtime/input/ScreenshotInput.kt`、`app/...`
**Scope**：S

#### T18：Soul PlatformAdapter（实时说话人识别）
**Description**：在 `platform-soul` 实装 `PlatformAdapter`：基于 OCR 文本盒坐标 + 颜色/对齐方向，识别"我" vs "别人"。错位率目标 < 5%。
**Acceptance criteria**：
- [ ] 5 张 Soul 截图（自制）错位率 < 5%
- [ ] 接口签名能让 P1 微信适配器直接 plug-in
- [ ] 单测覆盖核心算法
**Verification**：
- [ ] `./gradlew :platform-soul:test`
**Dependencies**：T16、T17
**Files**：`platform-soul/**`
**Scope**：M

#### T19：批量截图 ImportSource
**Description**：在 `feature-import/source` 实装 `BatchScreenshotImportSource`：批量选图 → 复用 OCR 流水线 → 复用清洗/对齐/采样/画像。
**Acceptance criteria**：
- [ ] 50 张测试截图批量处理 ≤ 60s（不含 LLM 画像提取）
- [ ] 进度回调正确；可取消
- [ ] 单测覆盖
**Verification**：
- [ ] `./gradlew :feature-import:test`
- [ ] 真机跑 50 张
**Dependencies**：T10、T16、T18
**Files**：`feature-import/source/BatchScreenshotImportSource.kt`
**Scope**：S

#### Checkpoint：M3 完成
- [ ] OCR + Soul 截图错位率 < 5%
- [ ] 端到端：截图 → OCR → 说话人识别 → 候选生成
- [ ] **人工评审**：OCR/识别准确率达标，是否需要切 PaddleOCR

---

### Phase 5 — M4 反馈环闭合

#### T20：反馈信号持久化 + UI 反馈交互完善
**Description**：把 T08 的 in-memory `FeedbackBuffer` 替换为 `RoomFeedbackRepository`。UI 加"修改候选"输入框；丢弃需要确认。每条反馈记录关联当前 `StyleFingerprint` 版本号。
**Acceptance criteria**：
- [ ] 反馈写库；可查询历史
- [ ] 修改/丢弃 UX 流畅（无误触；丢弃二次确认）
- [ ] 单测覆盖 Repository
**Verification**：
- [ ] `./gradlew :core-data:test :app:connectedDebugAndroidTest`
**Dependencies**：T08、T09
**Files**：`core-data/repository/FeedbackRepository.kt`、`app/...`
**Scope**：S

#### T21：增量学习写回 StyleFingerprint
**Description**：实现 `IncrementalLearner`：周期性（如累积 N 条反馈后）调 LLM，把反馈信号合并到当前 `StyleFingerprint`，产出新版本。版本可回滚。**红线**：合并入参类型层面只能含 Me 的修改后内容，不能含对方消息。
**Acceptance criteria**：
- [ ] 自构造的 20 条反馈跑一轮，画像版本号递增
- [ ] 单测断言：合并 LLM payload 不含 Speaker.Other
- [ ] 用户可在 UI 回滚到上一个版本
**Verification**：
- [ ] `./gradlew :feature-realtime:test`
- [ ] 手动跑 20 条反馈，肉眼对比新旧画像
**Dependencies**：T14、T20
**Files**：`feature-realtime/feedback/**`
**Scope**：M

#### Checkpoint：M4 完成
- [ ] 反馈环闭合：采纳/修改/丢弃 → 增量学习 → 画像更新 → 下一次候选受影响
- [ ] **人工评审**：合并节奏（N 条触发）是否合理、回滚交互

---

### Phase 6 — M5 自用验证 + 调优

#### T22：性能基线（MacroBenchmark）+ 性能护栏
**Description**：用 MacroBenchmark 抽样：① 候选生成 P95 ≤ 3s ② 批量导入 10k 条 ≤ 60s（不含画像 LLM）③ App 冷启动 < 2s。结果落 `docs/perf/baseline.md`。
**Acceptance criteria**：
- [ ] 三项指标基线测出，达标或有明确改进任务
- [ ] CI 不跑 macrobenchmark（手动），但 micro benchmark 进 CI
**Verification**：
- [ ] `./gradlew :app:connectedDebugAndroidTest -Pmacrobench=true`（手动）
**Dependencies**：T15、T19
**Files**：`app/src/androidTest/.../MacroBenchmark.kt`、`docs/perf/baseline.md`
**Scope**：S

#### T23：自用 1 周观察 + 反馈收集
**Description**：作者每天用 ≥ 30min 真实场景。每天记录：采纳率、风格命中率（事后回看）、Bug、不顺的 UX。每天结束写 `docs/dogfood/dayN.md`。
**Acceptance criteria**：
- [ ] 7 天日报全部写完
- [ ] 采纳率 ≥ 50%、风格命中率 ≥ 75%
- [ ] 累积 Bug 列表 + 每条 issue 状态
**Verification**：
- [ ] 7 个日报 + 1 个总结
**Dependencies**：T22
**Files**：`docs/dogfood/**`
**Scope**：S（但跨度 1 周）

#### T24：调优收口（基于 T23 反馈）
**Description**：从 T23 累积的 issue 中挑高 ROI 项修：可能是 prompt 微调、采样策略调整、UI 误触修复。**不开新功能**。
**Acceptance criteria**：
- [ ] 至少修 5 条来自 dogfood 的 issue
- [ ] 没有引入新功能（grep diff 无新模块/新 ImportSource/新 PlatformAdapter）
- [ ] 修后 1–2 天再跑一次端到端
**Verification**：
- [ ] `./gradlew check`
- [ ] 手动端到端复测
**Dependencies**：T23
**Files**：跨模块小改
**Scope**：S–M

#### Checkpoint：M5 收口（MVP 交付）
- [ ] 所有 SPEC §1.4 成功判据复测达标
- [ ] 红线零违反（grep + 单测）
- [ ] 自用日报 7 天全部写完
- [ ] **人工决策**：是否进入 P1（悬浮窗 / 微信适配 / 复盘报告）

---

## 6. 并行机会

| 阶段 | 可并行 |
|---|---|
| M0 | T02 与 T03 几乎并行（T03 等 T02 接口骨架定义即可启动 fixture 设计） |
| M1 | T04（infra-net）与 T06（PasteInput）并行；T05 等 T04 |
| M2 | T11（清洗）与 T13（采样）按 fixture 分组并行；T15 UI 等 T14 |
| M3 | T16（OCR）与 T18（Soul 适配）可在 OCR 接口稳定后并行（T18 用 FakeOcrProvider） |
| M5 | 自用阶段无并行；T24 收口可按 issue 拆并行 PR |

## 7. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 说话人对齐错位率难达标（< 2%） | 高 | 头号风险。先用启发式 + 用户指认；fixture 校验先行；如不达标 P1 再上 ML |
| DeepSeek 中文风格质量不达标 | 中 | LLMProvider 抽象保 1 天内可切 Claude；ADR-0002 已记录决策树 |
| OCR 在低质量截图上错位率高 | 中 | T16 接口设计支持切 PaddleOCR / 云端；P0 仅做 Soul，控制变量 |
| Room+SQLCipher 性能瓶颈（10k 写入） | 中 | T19 提前压测；必要时改用 batch insert |
| 隐私红线被未来贡献者无意打破 | 高 | 不靠 review；T02 与 T14 用类型签名 + 单测兜底 |
| API Key 泄漏到 git/log | 高 | T04 日志拦截器 + grep CI step + ktlintFormat hook |
| 自用 1 周后采纳率不达标 | 中 | T24 收口允许调 prompt 与采样；如 < 30% 进入"画像策略复盘"暂停里程碑 |

## 8. 开放问题（Plan 阶段不阻塞，Build 时再敲定）

- 风格画像 6 维 schema 的具体字段：在 T02 ADR-0001 落地时确认
- 微信导出格式解析：P1 任务，本计划不展开
- 批量截图 OCR 是否启用云端：T16 ADR-0004 决定
- 数据清洗规则配置 schema：T11 设计时定
- 采样策略具体算法（时间窗 vs 对象 vs 混合）：T13 实现时基于 fixture 实验定
- 画像版本回滚 UI：T20 / T21 设计时定
- 反馈合并触发阈值（N 条）：T21 调参定

---

## 9. 验证清单（开始 Build 前）

- [ ] 每个任务有 Acceptance criteria（最少 3 条）
- [ ] 每个任务有 Verification 命令
- [ ] 每个任务规模 ≤ M（无 L/XL）
- [ ] 依赖图无环
- [ ] 6 个 Checkpoint 与人工评审节点齐全
- [ ] 隐私红线在 T02、T07、T14、T21 有显式测试守卫
- [ ] 5 大抽象层在第一次需要时引入（不延后到"重构"阶段）
- [ ] 人工已 review 本计划
