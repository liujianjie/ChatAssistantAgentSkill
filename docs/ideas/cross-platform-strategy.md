# 跨平台扩展策略（决策记录）

> 配套 SPEC：[../../SPEC.md](../../SPEC.md) §1.6
> 上游讨论：2026/05/01 会话
> 状态：方向已定，时机推后到 P1 完成 + 自用验证后再启动

---

## 1. 决策一句话

**P0/P1 阶段仅做 Android Kotlin 原生**，把自用版跑稳；**P2 推广阶段**在 Android Kotlin 主干**旁边**新建 UniApp/Taro 项目做小程序 + Web/PWA 轻量版，**共享契约层而非代码层**。Android 永远是 Kotlin 这条线，不切换。

## 2. 目标平台与时间线

| 端 | 形态 | 技术栈 | 阶段 |
|---|---|---|---|
| Android | 完整版（悬浮窗 / 截屏 OCR / 实时识别 / 复盘） | Kotlin + Jetpack Compose（当前 SPEC §3.1 多模块架构） | P0 / P1 |
| 微信小程序 | 轻量版（粘贴 / 主动上传图 → 候选 → 复制） | UniApp 或 Taro（推后到 P2 时再敲定） | P2 |
| Web / PWA | 轻量版（同小程序） | 跟随小程序栈复用（Taro 可双端共享 ~70%） | P2 |
| iOS 原生 | 暂不在目标内 | — | — |
| Windows / macOS 桌面 | 暂不在目标内 | — | — |

## 3. 三端能力矩阵（关键约束）

| 能力（SPEC 中位置） | Android 原生 | 微信小程序 | Web / PWA |
|---|---|---|---|
| Accessibility 悬浮窗（P1 核心） | ✅ | ❌ 沙箱禁止 | ❌ |
| 截屏自动 OCR（T16/T17/T19） | ✅ ML Kit | ❌ 仅用户主动上传图 | ⚠️ 仅用户主动上传图 |
| 剪贴板监听（P2 设想） | ✅ | ❌ | ⚠️ 受限，需授权焦点页 |
| SQLCipher 加密 DB（T09） | ✅ | ❌ 仅 10MB storage | ⚠️ IndexedDB 自加密 |
| 主动粘贴 → 候选（T06–T08） | ✅ | ✅ | ✅ |
| 主动上传图 → OCR → 候选 | ✅（本地 ML Kit） | ✅（云端 OCR） | ✅（云端 OCR） |
| 本地优先红线（§1.3 ②） | ✅ | ❌ 必须依赖云开发 | ⚠️ 难维持 |

**核心结论**：小程序 / Web 上**只有 P0 最短闭环（粘贴 → 候选）能跑**，P1 / P2 的 Android 专属能力在小程序 / Web 都做不了。

## 4. 为什么不是"切换"，而是"双轨并存"

Android 端 P1 的核心能力（悬浮窗、截屏 OCR、剪贴板监听、SQLCipher）在 UniApp 也做不了 — 这些必须留在 Kotlin。如果用 UniApp 重写 Android，等于"UniApp 壳 + Android 原生骨"，工作量比保留 Kotlin 还大。

未来真正的形态：

```
[阶段 1 产出 — 保留]                    [P2 阶段新增]
Android Kotlin 完整版                   UniApp / Taro 项目（小程序 + H5/PWA）
（含悬浮窗/截屏/SQLCipher/Soul 适配）    （仅 P0 最短闭环：粘贴 → 候选 → 复制）
        │                                       │
        └──────── 共享契约层 ───────────────────┘
                  - StyleFingerprint JSON schema
                  - Prompt 模板（文本文件）
                  - LLM API 调用约定
                  - 黄金 fixture YAML
                  - ADR 决策记录
```

## 5. 真实代码复用率：~20-30%

| 资产 | 复用情况 |
|---|---|
| StyleFingerprint schema（6 维结构） | ⚠️ 类型定义重写，**JSON 契约可双向** |
| Prompt 模板 | ✅ 原样复用（如果按下方 §7 铺垫做了文件化） |
| 黄金 fixture YAML | ✅ 原样复用 |
| ADR 文档 | ✅ 原样复用 |
| LLM 请求/响应约定 | ⚠️ 代码重写，**JSON schema 复用** |
| 5 大抽象层接口形状 | ⚠️ 形状借鉴（"LLMProvider.generateCandidates"），实现重写 |
| 清洗 / 对齐 / 采样 / 候选生成算法 | ❌ 重写（但算法思路 + 测试用例复用） |
| Compose UI | ❌ 完全重写 |
| Room + SQLCipher | ❌ 持久化方案完全不同 |
| ML Kit OCR / Accessibility / 剪贴板监听 | ❌ 也不需要重写 — 本就是 Android 专属 |
| ktlint / detekt / Hilt / Gradle 工具链 | ❌ 不存在 |

代码层无法共享，**真正能跨端共享的是规范/契约/Prompt/测试样本/决策记录**。

## 6. 画像跨端的处理：手动 JSON 导入导出

不做云同步（踩 SPEC §6.3 红线 ②"不上传聊天/画像"）。

机制：
1. Android 端用户在主页/设置页可"导出风格画像 JSON"（结构化、不含原始消息）
2. 小程序 / Web 端用户可"导入画像 JSON"（粘贴或上传文件）
3. 各端反馈循环独立，画像版本号各自维护
4. 用户主动选择是否在某端用导入的画像，不强制同步

**红线兜底**：导出 JSON 中只能有 StyleFingerprint 字段，禁止任何 Speaker.Other 内容、原始 Message 文本。导出/导入逻辑必须有单测断言这一点。

## 7. 阶段 1 期间可做的"无悔铺垫"

5 个小动作，每个 < 半天，**不影响 P0 进度**，做了将来扩 UniApp 时省事；不做也对 Android 项目无害（甚至有助于可测试性）。

| 动作 | 对应 plan.md 任务 | 跨端收益 |
|---|---|---|
| StyleFingerprint schema 避免 Kotlin 特有类型（`sealed class` 嵌套）的复杂表达，保持能干净序列化为 JSON | T02 | TS 直接 `interface FingerprintV1` |
| Prompt 模板独立成 `core-domain/src/main/resources/prompts/*.txt`，不在代码里硬编码 | T07 | UniApp 可直接 fork 这些文件 |
| LLM 请求/响应 JSON schema 写进 ADR-0002，作为跨语言契约 | T05 | UniApp 写 TS 实现时按 ADR 抄即可 |
| 算法层（清洗 / 对齐 / 采样 / 候选）写成纯函数 + 不依赖 Android Context | T11 / T12 / T13 / T14 | TS 重写时翻译即可，不需重设计 |
| 黄金 fixture YAML 字段命名 snake_case，与 JSON schema 风格对齐 | T03 | 双语言 loader 复杂度都低 |

**当前状态**：尚未把这 5 条加入 plan.md 子任务。等 build 阶段到对应 T 任务时再决定要不要细化为 sub-task。

## 8. 已评估并淘汰的方案

| 方案 | 否决原因 |
|---|---|
| Unity | 游戏引擎做聊天工具 App 全方位错配（包体 / 启动 / UI / 系统集成） |
| 用 UniApp 做 Android 主干 | Android P1 关键能力（悬浮窗 / ML Kit / SQLCipher / Soul 文本盒识别）都要原生插件，等于"UniApp 壳 + Android 原生骨"，工作量大于直接 Kotlin |
| KMP（Kotlin Multiplatform） | 在没有 iOS 目标时价值低；小程序 / Web 也不在 KMP 主流支持范围 |
| Compose Multiplatform | 同上，主要价值在 iOS / 桌面 |
| 云同步画像 / 云存原始聊天 | 踩 SPEC §6.3 红线 ② |

## 9. CloudBase-MCP 评估

CloudBase-MCP 是给 AI IDE（Claude Code / Cursor 等）通过 MCP 协议操作腾讯云开发能力的**开发期工具**，不是 App 内 SDK。

| 维度 | 结论 |
|---|---|
| 作为开发工具（部署小程序后端 / 云函数 / 静态网站） | ✅ 可作为未来 P2 工具备选，加速小程序版后端搭建 |
| 作为产品后端：存配置 / Prompt 模板 / LLM 代理（隐藏 Key） | ⚠️ 可考虑 — 不含用户聊天/画像数据时不踩红线 |
| 作为产品后端：存聊天原文 / 画像 JSON / 跨端同步 | ❌ 踩 SPEC §6.3 红线 ② |
| 当前 P0 引入与否 | ❌ 不引入。本地优先红线优先 |

**当前决定**：保留为"P2 阶段的工具备选"，不进入 SPEC 主体。届时若做小程序版，可重新评估是否用云开发作为非敏感后端（Prompt 模板分发、LLM API 代理）。

## 10. 切换时机

| 时机 | 适合启动 P2 跨端吗 | 原因 |
|---|---|---|
| P0 完成时 | ❌ 别 | 还没自用验证产品形态对不对，盲目扩端等于摊大饼 |
| P1 完成时（自用验证 + 悬浮窗稳了） | ✅ 分水岭 | 产品价值已确认，Android 主干稳定，分支起 UniApp 项目 |
| P2 推广期 | ✅ 合适 | 此时跨端是真的为获客服务 |

## 11. 红线复述（迁移到任何端都不可破）

继承 SPEC §1.3 / §6.3：

1. ❌ 把对方消息聚合进 StyleFingerprint
2. ❌ 把原始聊天数据（含 OCR 结果原图、纯文本对话）上云
3. ❌ 把 API Key 写进 git / commit / log
4. ❌ 生成"讨好"导向的候选话术
5. ❌ 把可共存的能力做成互斥取舍
6. ❌ 跨端时取消"本地优先"哲学，换成"全部上云"

任何 P2 阶段的 UniApp / 小程序 / Web 实现，必须在自身端内重新论证如何满足上述红线（小程序的 storage 限制 / Web 的 IndexedDB 加密 / 云开发可触碰边界等），不得以"小程序生态如此"为由削弱。

---

## 12. 后续动作

- [ ] **现在**：SPEC §1.6 加一行指针指向本文件
- [ ] **build 阶段到 T02/T03/T05/T07/T11-T14 时**：决定是否把 §7 的 5 条无悔铺垫加为子任务
- [ ] **P1 完成 + 自用验证 ≥ 1 周后**：评审本文件，决定 P2 是否启动 UniApp / Taro 实现
- [ ] **P2 启动时**：本文件升级为 ADR `docs/adr/NNNN-cross-platform-impl.md`，含 UniApp vs Taro 的最终选型
