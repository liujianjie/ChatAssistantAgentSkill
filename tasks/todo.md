# 任务跟踪 — Style Mirror Copilot MVP P0

> 详细描述、验收标准、验证步骤见 [plan.md](./plan.md)。
> 本文件只跟踪状态，避免重复内容。

状态符号：`[ ]` 待办 / `[~]` 进行中 / `[x]` 完成 / `[!]` 阻塞

---

## Phase 1 — M0 项目骨架

- [~] **T01** build-logic + 多模块 Gradle 骨架 + CI [M] — deps: 无
  - [x] S1 root Gradle skeleton + wrapper (Tencent mirror, GRADLE_USER_HOME=F:/.gradle-cache)
  - [x] S2 build-logic convention plugins (KotlinJvm/AndroidLibrary/AndroidApplication/AndroidCompose + ktlint/detekt + libs.versions.toml)
  - [ ] S3 注册 13 模块
  - [ ] S4 app 空 Application + Activity
  - [ ] S5 CI workflow + ./gradlew check 全绿
- [ ] **T02** core-domain 数据模型 + DomainError [M] — deps: T01
- [ ] **T03** Golden fixtures + 测试基础设施 [S] — deps: T02

**Checkpoint M0**：`./gradlew check` 全绿、APK 装机、fixture 入库无真实数据、人工评审

## Phase 2 — M1 最短闭环

- [ ] **T04** infra-net + SecureKeyStore [M] — deps: T01, T02
- [ ] **T05** LLMProvider 抽象 + DeepSeek 实现 + Fake [M] — deps: T04
- [ ] **T06** InputAdapter 抽象 + PasteInput [S] — deps: T02, T03
- [ ] **T07** 候选生成器 + Fake StyleEngine + 隐私护栏 [M] — deps: T05, T06
- [ ] **T08** app Compose UI 最短闭环 + 反馈占位 [M] — deps: T05, T06, T07

**Checkpoint M1**：端到端冒烟、P95 ≤ 3s、隐私护栏单测、人工评审

## Phase 3 — M2 StyleEngine v1

- [ ] **T09** core-data Room + SQLCipher [M] — deps: T02, T04
- [ ] **T10** ImportSource 抽象 + 纯文本导入 [M] — deps: T02, T09
- [ ] **T11** 数据清洗 [S] — deps: T03, T10
- [ ] **T12** 说话人对齐（大规模） [M] — deps: T11
- [ ] **T13** 采样与聚合 [S] — deps: T12
- [ ] **T14** 性格画像提取 + 真 StyleEngine [M] — deps: T05, T13
- [ ] **T15** Onboarding UI 串联 + 主页接真画像 [M] — deps: T08, T14

**Checkpoint M2**：错位率 < 2%、画像命中率 ≥ 75%、类型层面隐私保护、人工评审

## Phase 4 — M3 OCR + Soul

- [ ] **T16** OcrProvider 抽象 + ML Kit 实现 [M] — deps: T02
- [ ] **T17** ScreenshotInput 实装 [S] — deps: T08, T16
- [ ] **T18** Soul PlatformAdapter [M] — deps: T16, T17
- [ ] **T19** 批量截图 ImportSource [S] — deps: T10, T16, T18

**Checkpoint M3**：Soul 截图错位率 < 5%、端到端通、人工评审

## Phase 5 — M4 反馈环

- [ ] **T20** 反馈信号持久化 + UI 完善 [S] — deps: T08, T09
- [ ] **T21** 增量学习写回 StyleFingerprint [M] — deps: T14, T20

**Checkpoint M4**：反馈环闭合、人工评审

## Phase 6 — M5 自用验证

- [ ] **T22** 性能基线（MacroBenchmark） [S] — deps: T15, T19
- [ ] **T23** 自用 1 周观察 + 日报 [S, 跨度 1 周] — deps: T22
- [ ] **T24** 调优收口（不开新功能） [S–M] — deps: T23

**Checkpoint M5（MVP 交付）**：成功判据全达标、红线零违反、人工决策是否进 P1

---

## 并行启动建议

最早可并行：
- T02 ↔ T03（T03 在 T02 接口骨架定义后即可启动）
- T04 ↔ T06（互不依赖）
- T11 ↔ T13（按 fixture 分组并行）
- T16 ↔ T18（T18 用 FakeOcrProvider 起手）

## 关键里程碑节奏目标（理想，非硬性）

| 阶段 | 周次 | 任务 |
|---|---|---|
| W1 | M0 | T01–T03 |
| W2 | M1 | T04–T08 |
| W3–W4 | M2 | T09–T15 |
| W5 | M3 | T16–T19 |
| W6 | M4 | T20–T21 |
| W7 | M5 | T22 + 自用观察启动 |
| W8 | M5 | T23–T24 收口 |
