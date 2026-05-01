# 风格镜像副驾（Style Mirror Copilot）— Specification

> 项目代号：Style Mirror Copilot
> 工作流：agent-skills（spec → plan → build）
> 上游：`docs/ideas/style-mirror-copilot.md`（idea-refine 产出）
> 状态：v0.1（MVP P0 范围）

---

## 1. Objective

### 1.1 一句话定位
帮用户在社交软件聊天时，实时获得"高度匹配自己真实聊天风格"的候选回复，从而以最贴近真我的方式表达，**筛选真正喜欢自己本人风格的人**。

### 1.2 目标用户
- **首发（自用先行）**：作者本人，Android 用户，主用 Soul，希望在聊天中保持自我风格、避免讨好。
- **MVP 阶段后**：有同样诉求的安卓社交软件用户（微信、小红书、陌陌等）。

### 1.3 核心哲学（红线，不可妥协）
1. **筛选喜欢真我的人** ≠ 让对方喜欢上我。任何"讨好"导向的话术生成都不在本项目范围。
2. **本地优先**。原始聊天数据全本地存储；仅"我"发出的消息进入风格画像聚合；对方消息只作为情境上下文，最小必要时才送入 LLM。
3. **架构包容多种实现**。输入方式、LLM 提供商、目标平台均通过抽象层解耦，主推一个实现 + 保留扩展点，不做非黑即白的二选一。

### 1.4 成功判据（MVP）
| 指标 | 目标 |
|---|---|
| 自评候选采纳率（采纳/直接复制 ÷ 总生成轮次） | ≥ 50% |
| 自评候选风格命中率（事后回看一致性） | ≥ 75% |
| 候选生成 P95 延迟 | ≤ 3 s |
| OCR + 说话人识别错位率（Soul 截图） | ≤ 5% |
| 批量导入说话人识别错位率（跨设备/含群聊） | ≤ 2% |
| 自用 1 周后仍每天打开 | ✅ |

### 1.5 范围（MVP P0）
- 安卓 App，Kotlin + Jetpack Compose。
- 输入：截图导入（OCR）+ 复制粘贴（共用 `ConversationContext` 抽象）。
- 平台：Soul（`PlatformAdapter` 抽象，可扩展）。
- LLM：DeepSeek 主推 + Claude/Qwen 切换接口（`LLMProvider` 抽象）。
- 批量导入引擎：微信 PC 端导出 / 微信安卓备份 / 批量截图 / 纯文本 / 第三方工具产物（5 路 `ImportSource`，至少先实现 2 路：批量截图、纯文本）。
- 流程：onboarding 导入 → 风格指纹 v1 → 实时 3 候选 → 采纳/修改/丢弃 → 增量更新指纹。

### 1.6 显式 Phase 2 / Phase 3（不是"不做"，是"延后做"）
- **P1**：Accessibility 悬浮窗、系统分享 sheet、微信日常使用层适配、复盘报告、多账号画像合并。
- **P2**：虚拟人预演、反向筛选器、PDF 学习、其他平台、本地小模型选项。

---

## 2. Commands

> Gradle Wrapper 已就位后使用 `./gradlew`，Windows 下使用 `gradlew.bat`。

### 2.1 构建
```bash
./gradlew assembleDebug              # 调试包
./gradlew assembleRelease            # 发布包（需配置签名）
./gradlew installDebug               # 装到连接的设备/模拟器
./gradlew :app:bundleRelease         # AAB（上架用，MVP 暂不需要）
```

### 2.2 测试
```bash
./gradlew test                       # 全模块单元测试
./gradlew :core-style:test           # 单模块单测
./gradlew connectedDebugAndroidTest  # Instrumented + Compose UI test
./gradlew jacocoTestReport           # 覆盖率报告（HTML 输出 build/reports/jacoco/）
```

### 2.3 静态检查与格式化
```bash
./gradlew ktlintCheck                # ktlint 检查
./gradlew ktlintFormat               # ktlint 自动格式化
./gradlew detekt                     # Detekt 静态分析
./gradlew lint                       # Android Lint
```

### 2.4 一键校验（CI 入口）
```bash
./gradlew check                      # ktlint + detekt + lint + test
```

### 2.5 调试辅助
```bash
adb logcat -s StyleMirror            # 仅看本项目日志（统一 TAG = StyleMirror）
./gradlew dependencies --configuration releaseRuntimeClasspath  # 依赖树
```

---

## 3. Project Structure

### 3.1 多模块设计（按层 + 按能力切分，便于后续 P1 增量）
```
ChatAssistantAgentSkill/
├── app/                              # Compose UI + Navigation + DI 装配
│   └── src/main/kotlin/com/stylemirror/app/
├── core-domain/                      # 纯 Kotlin，无 Android 依赖
│   ├── conversation/                 # ConversationContext、Message、Speaker
│   ├── style/                        # StyleFingerprint schema、画像维度定义
│   └── feedback/                     # 采纳/修改/丢弃信号建模
├── core-data/                        # Room/SQLCipher、EncryptedSharedPreferences
│   ├── db/                           # Entity / Dao / Migration
│   ├── secure/                       # Keystore 派生密钥、加密 SP 封装
│   └── repository/                   # 实现 domain 层接口
├── feature-import/                   # 批量导入流水线
│   ├── source/                       # ImportSource 抽象 + 5 路实现位
│   ├── cleaning/                     # 去重/合并/过滤
│   ├── alignment/                    # 大规模说话人对齐
│   ├── sampling/                     # 采样与聚合
│   └── profiling/                    # 性格画像提取（调 LLMProvider）
├── feature-realtime/                 # 日常使用流水线
│   ├── input/                        # InputAdapter 抽象 + ScreenshotInput / PasteInput
│   ├── recognition/                  # 实时说话人识别（Soul 适配器）
│   ├── matching/                     # StyleEngine 匹配
│   ├── candidate/                    # 候选生成（默认 3）
│   └── feedback/                     # 增量学习写回
├── platform-soul/                    # PlatformAdapter Soul 实现
├── platform-stub/                    # 仅供测试用的 fake 实现，便于注入
├── infra-llm/                        # LLMProvider 抽象 + DeepSeek/Claude/Qwen
├── infra-ocr/                        # OcrProvider 抽象 + 本地（ML Kit）+ 云端 stub
├── infra-net/                        # OkHttp/Retrofit、超时/重试、密钥注入
└── build-logic/                      # convention plugins，统一编译/测试配置
```

### 3.2 抽象层（架构红线）
所有跨边界处必须通过下列接口之一调用，禁止直接 new 实现：
- `InputAdapter`：截图、复制粘贴、（P1）悬浮窗、（P1）分享 sheet
- `PlatformAdapter`：Soul、（P1）微信、（P2）其他
- `LLMProvider`：DeepSeek、Claude、Qwen、（P2）本地小模型
- `OcrProvider`：本地（ML Kit / PaddleOCR 二选一，先用 ML Kit）、云端
- `ImportSource`：5 路批量导入入口

### 3.3 命名约定
- 包名：`com.stylemirror.<module>.<layer>`
- 模块名：`<层>-<能力>`（如 `core-domain`、`feature-import`）
- 接口与实现：`Foo` ←→ `DefaultFoo` / `DeepSeekFoo`（按差异化前缀）

---

## 4. Code Style

### 4.1 语言与风格
- Kotlin 2.x，Java 17 toolchain。
- ktlint（官方风格） + Detekt（默认规则集 + 项目自定义）作为强制门禁。
- 公共 API 强制 KDoc；私有函数仅在 *why* 非显然时加一行注释。
- 严禁 `!!` 兜底；用 `requireNotNull` / `checkNotNull` 表达不变量，或返回 `Result` / sealed class。

### 4.2 Compose 与 UI
- 单向数据流：UI → ViewModel(Intent) → UseCase → Repository → DB/Network。
- ViewModel 暴露 `StateFlow<UiState>`；Compose 用 `collectAsStateWithLifecycle()`。
- `@Preview` 必须用 fake state，不依赖真实数据源。
- 主题与设计 token 集中在 `app/ui/theme`，不在 Composable 内硬编码颜色/间距。

### 4.3 协程与并发
- 所有 IO 走 `Dispatchers.IO`，CPU 密集走 `Dispatchers.Default`，UI 用 `Dispatchers.Main.immediate`。
- 长生命周期任务挂在 `viewModelScope` 或 `applicationScope`（自定义 supervisor）；禁用 `GlobalScope`。
- LLM 调用、OCR 调用必须可取消、可超时；缺省超时 30s（候选生成场景缩到 8s）。

### 4.4 错误处理
- 业务错误：sealed `DomainError`，UseCase 返回 `Result<T, DomainError>`。
- 系统异常：仅在最外层 ViewModel 兜底转 UiState.Error。
- 不吞异常；不把异常 message 直接展示给用户，要做用户可读的映射。

### 4.5 密钥处理（继承全局铁律）
- API Key 仅存放于 `EncryptedSharedPreferences`；不进 git，不进日志，不进 toast。
- `LLMProvider` 实现层从安全存储取 Key，调用方不感知 Key。
- 任何打印/日志看到 `key|token|secret|password` 字段一律 `[REDACTED]`。
- 新增配置文件前必须同步检查 `.gitignore`。

### 4.6 注释与文档
- 默认不写注释；写就解释"为什么"，不解释"是什么"。
- 不写 task/PR 的来源（"为 issue #X 添加"），那属于 commit message。
- ADR 放 `docs/adr/NNNN-title.md`，每条架构决策一份。

---

## 5. Testing Strategy

### 5.1 分层覆盖目标
| 层 | 覆盖率目标 | 工具 | 重点 |
|---|---|---|---|
| `core-domain`（纯 Kotlin） | ≥ 85% | JUnit5 + Kotest assertions | 业务规则、风格指纹 schema、采样算法 |
| `feature-import` 算法层 | ≥ 80% | JUnit5 + 黄金样本 fixture | 说话人对齐、清洗、采样的回归测试 |
| `feature-realtime` 算法层 | ≥ 80% | JUnit5 + fake provider | 实时识别、匹配、候选拼装 |
| `core-data` Repository | ≥ 70% | Robolectric / Room in-memory | DB 迁移、加密读写正确性 |
| `infra-llm` / `infra-ocr` 适配层 | ≥ 60% | MockWebServer / fake provider | 重试、超时、错误码映射 |
| ViewModel | ≥ 70% | Turbine + coroutines-test | StateFlow 转换 |
| Compose UI（关键路径） | 选择性 | Compose UI Test | 候选展示、采纳交互、onboarding |

### 5.2 关键路径必须有端到端验证（Instrumented Test）
1. Onboarding：导入纯文本样本 → 生成画像 v1 → 进入主页。
2. 实时使用：粘贴对话 → 生成 3 候选 → 一键复制 → 反馈写回。
3. OCR：截图导入 → 识别消息序列 → 进入候选生成。

### 5.3 黄金样本（Golden Fixtures）
- `core-domain/src/test/resources/golden/`：手工标注的 5–10 段对话，包含跨设备/群聊/昵称变更场景。
- 说话人对齐、采样策略、画像提取都必须用同一组黄金样本回归。
- 黄金样本视为"真实数据脱敏后的代理"，禁止把真实聊天记录纳入仓库（即便脱敏）。

### 5.4 LLM/OCR 测试边界
- 单测一律用 `FakeLLMProvider` / `FakeOcrProvider`，不打真实 API。
- 真实 API 验证走 `./gradlew :infra-llm:integrationTest`，由本地开发者手动运行（CI 不跑），需 `STYLEMIRROR_DEEPSEEK_KEY` 环境变量；缺失则 skip。

### 5.5 性能验证
- 候选生成 P95 ≤ 3 s：通过 fake provider 模拟 + MacroBenchmark 在真机抽样。
- 批量导入吞吐：10k 条消息处理 ≤ 60 s（不含 LLM 画像提取）。

---

## 6. Boundaries

### 6.1 Always Do（默认行为）
1. **本地优先**：原始数据落本地加密 DB；上送 LLM 的内容必须最小化、必须脱敏（手机号/身份证/银行卡数字遮蔽）。
2. **抽象层先于实现**：新增能力（输入源、LLM、平台、OCR、导入源）必须先有接口 + Fake 实现 + 测试，再接真实实现。
3. **主推 + 扩展点**：当一个能力可有多种实现，给出"主推 A + 保留 B 入口"，禁止做成互斥决策。
4. **每个 PR 跑通 `./gradlew check`**；新代码必须带相应层级的测试。
5. **密钥处理铁律**：见 `~/.claude/CLAUDE.md` 全局规则，本项目继承且不可削弱。
7. **ADR 记录架构决策**：抽象层、加密方案、LLM 调用 schema 等关键变更必须落 `docs/adr/`。

### 6.2 Ask First（先问用户再动手）
1. **改变隐私边界**：新增任何"会把数据上送云端"的路径（包括日志、崩溃上报、画像云同步）。
2. **改动核心抽象**：`LLMProvider` / `PlatformAdapter` / `InputAdapter` / `OcrProvider` / `ImportSource` 接口签名变更。
3. **新增云端依赖**：引入第三方 SaaS（云 OCR、远程画像服务等）。
4. **修改红线项**：任何"画像中混入对方数据"、"上传原始聊天"的设计动议。
5. **大规模重构**：跨模块的目录搬迁、模块拆并、构建系统替换。
6. **触碰已发布的数据库 schema**：任何 Room migration 必须先讨论再写。
7. **第三方工具集成（如 WeChatMsg）**：涉及法律/平台 ToS 风险，先确认。

### 6.3 Never Do（红线，不可逾越）
1. ❌ 把对方消息内容聚合进 `StyleFingerprint`。
2. ❌ 把原始聊天数据（含 OCR 结果原图、纯文本对话）上传云端，无论以何种"匿名化"名义。
3. ❌ 把 API Key、用户原文、脱敏前的对话内容写进 git、commit message、issue/PR 评论、日志。
4. ❌ 生成"讨好"导向的候选话术。
5. ❌ 把"可共存的能力"在产品决策上做成互斥取舍（架构铁律）。
6. ❌ 跳过 git pre-commit hook（`--no-verify`），跳过签名（`--no-gpg-sign`）。
7. ❌ 在测试里打真实 LLM/OCR API 而不加 skip 守卫。
9. ❌ 在公网仓库（包括 issue/讨论区）粘贴任何用户聊天原文，即便用于 bug 复现。

### 6.4 决策升级路径
- 影响红线 → 直接拒绝 + 解释。
- 触碰 Ask First → 暂停并向用户确认。
- 仅 Always Do 范畴 → 自主推进，按 plan/build 流程交付。

---

## 7. 后续工作流

1. **`/agent-skills:plan`** — 把本 SPEC 拆成可验证任务（建议先拆 P0 的 Onboarding + 复制粘贴流水线 + DeepSeek 候选生成 + 反馈环这条最短闭环），按依赖排序。
2. **`/agent-skills:build`** — 按任务增量实现 + 测试 + 验证 + 提交。
3. 关键里程碑（Plan 应覆盖）：
   - M0：项目骨架（多模块 + ktlint/detekt/CI）+ `core-domain` 数据模型 + golden fixture
   - M1：复制粘贴 → 候选生成最短闭环（DeepSeek + Fake StyleEngine）
   - M2：StyleEngine v1（采样 + 画像提取，纯文本导入源跑通）
   - M3：截图 OCR 流水线 + Soul 平台适配
   - M4：反馈环 + 增量学习写回
   - M5：自用 1 周验证 + 调优
