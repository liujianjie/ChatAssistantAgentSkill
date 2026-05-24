# 画像生命周期 — Portability & Evolution

> Idea-refine 阶段产出。覆盖两个相邻但独立的真问题：跨设备的画像存续（P9），与基于旧画像的增量演化（P10）。

## 背景与动机

自用反馈暴露两个缺口：

1. **重装/换机即丢**：DB 在 internal storage + `allowBackup="false"`（隐私红线决定的合理选择），SQLCipher 密钥绑硬件 Keystore — 三连击下，画像无法跨设备/重装存续。用户上一次花费的导入时间 + LLM 调用费用全部蒸发。
2. **"重新画像"是从零重做**：当前唯一批量更新画像的方式是 onboarding 重做（产生新版本，但不继承旧画像的趋势），中间形态缺失。用户的真实诉求是"我又攒了一段新对话，希望基于现有画像 + 新对话继续演化"，而不是从头算。

两块之间的关系：P9 解决"画像出门带得走吗"，P10 解决"画像跟得上人变化吗"。前者是横向（跨设备/重装），后者是纵向（跨时间）。

---

## P9 画像导出 / 导入

### Problem Statement

**HMW** 在不破坏"数据不出境"红线、不引入云同步的前提下，让用户的画像跨重装/换机存续？

### Recommended Direction

**用户主动导出 JSON 文件 + 用户主动导入 JSON 文件**。文件不加密、不联网、不上传，完全交由用户自管（本地、网盘、微信文件传输都行）。

```
设置 → 「导出画像」 → SAF 选位置 → 写出 style-mirror-profile-{timestamp}.json
设置 → 「导入画像」 → SAF 选 JSON → schema 校验 → 写入新版本（不覆盖历史）
```

### 关键判断

- **零隐私让步**：JSON 仅含 6 维结构化画像数据（`StyleFingerprint` 现有 schema），不含任何原始聊天消息。即使被截获，最坏后果是别人知道你"语言风格偏 CASUAL/常用 emoji 是 😂"，不存在敏感信息泄漏。
- **不开 `allowBackup=true`**：EncryptedSharedPreferences 的密钥绑硬件 Keystore，DB 备份过去也解不开，徒增复杂度，零收益。
- **导入是"追加新版本"而不是"替换"**：用 `nextVersion()` 写入，旧版本仍保留在 HistoryScreen 里可回滚。这一点 prompt 用户在 UI 上看到导入后画像有"V12（新）"，避免误以为旧画像被覆盖。
- **schema 校验**：`FingerprintJson.fromJson` 已有 `ignoreUnknownKeys = true` + 字段默认值，跨版本最坏退化到默认值，不会崩。但要在导入前主动检查必要字段非空，让用户看到"这不是有效的画像文件"而不是"导入了空画像"。

### 不做（红线）

- **不加密导出文件**：用户拿到的就是明文 JSON，可读可编辑可备份。加密会引入"密钥跟谁走"的二级问题，不值。
- **不做云同步**：违反"数据不出境"红线，直接否决。
- **不附带导出聊天记录原文**：原始聊天属于隐私最敏感层，导出后用户大概率会传到不安全的地方（QQ 邮箱、网盘）。导出仅画像，明确告知用户"原始聊天记录无法导出，只能在本设备产生新画像"。

### 验收（高层）

- [ ] 设置页两个按钮：导出画像 / 导入画像
- [ ] 导出文件名 `style-mirror-profile-{yyyyMMdd-HHmmss}.json`
- [ ] JSON 内容与 Room DB 中最新版本逐字段一致
- [ ] 导入空文件 / 损坏 JSON / 字段全缺失三种情况有明确错误提示
- [ ] 导入成功后 HistoryScreen 出现新版本，旧版本仍在
- [ ] 单测覆盖：往返一致性（export → import → fingerprint 等价）

---

## P10 演化画像（Evolutionary Reprofile）

### Problem Statement

**HMW** 让用户在攒了一段新聊天记录后，让画像基于现有画像 + 新对话继续演化，而不是抛弃旧画像从零重算？

### Recommended Direction

新增"演化画像"入口（与"重新画像"并列，不替换它），让用户三选一：

| 入口 | 行为 | 适用场景 |
|---|---|---|
| 重新画像（已有）| 仅用新导入的对话从零算 | 旧画像彻底过时 / 换了完全不同的语境 |
| 演化画像（**新增**）| LLM 输入：旧画像 + 新对话 → 输出演化后的画像 | 攒了新对话、风格自然漂移 |
| 反馈反哺（已有，自动）| 单条采纳/修改/丢弃 → IncrementalLearner | 日常使用中的微调 |

技术实现：扩 `PersonaProfiler.profile()` 接受可选的 `priorFingerprint: StyleFingerprint?`。当存在时，prompt 里附旧画像的 6 维结构化总结，让 LLM 在此基础上基于新对话调整。

### 关键判断

- **不混 PersonaProfiler 与 IncrementalLearner**：前者吃"批量新对话 + 可选旧画像"，后者吃"反馈信号 + 旧画像"。两条路径数据形态不同，prompt 模板和 LLM 调用都不一样，硬合并会复杂化双方。
- **prompt 模板的关键约束**：明确告诉 LLM "不要简单平均新旧画像，按近期对话权重更高来漂移"。否则会出现"新对话风格变了 → LLM 不敢偏离旧画像 → 演化几乎无效"的副作用。
- **类型层红线**：`profile(priorFingerprint = ...)` 入参签名只允许 `StyleFingerprint`（已脱敏的结构化数据）+ `ProfilingInput`（type-safe 仅 Me 的消息），编译期保证 prompt 不可能混入对方消息。
- **结果同样写新版本**：旧画像保留可回滚，演化画像有问题用户能直接退回去。

### 不做（避免膨胀）

- **不做"自动检测画像过时"**：不要让系统主动提示"你的画像太旧了，请演化"。这是用户主动行为，App 不打扰。
- **不做"对比新旧画像差异"UI**：HistoryScreen 列出版本足够，每行的差异详情留 P2，不为这个特性单独做。
- **不引入第四种"半增量"路径**：批量演化（P10）与单条反馈（IncrementalLearner）已经够用，不要再为"中等批量"造一条路。

### 验收（高层）

- [ ] OnboardingViewModel 在已有画像时，显示 "重新画像 / 演化画像" 两个选项
- [ ] PersonaProfiler.profile(priorFingerprint = ...) 接口扩展 + 单测
- [ ] 演化画像的 prompt 包含旧画像 6 维结构化总结（用 readable 中文，不直接塞 JSON）
- [ ] 单测：构造旧画像（formality=FORMAL）+ 新对话（明显 CASUAL）→ 输出 fp.formality 偏向 CASUAL（被 LLM mock 验证 prompt 内容即可）
- [ ] 单测：priorFingerprint 为 null 时行为完全等价于现状 P15 的 reprofile（向后兼容）

### 开放问题（实现期定）

- 旧画像在 prompt 中的呈现形式（结构化中文 vs YAML vs 简化 JSON），影响 LLM 解读质量
- 当新对话条数 < 阈值（如 50 条）时，是否退化为"反馈反哺"路径而非演化？还是不限制让用户决定？
- 演化画像的 partnerScopeId 处理：旧画像如果是 partner-A 范围，新对话是 partner-B 范围，怎么合？倾向：保守做法是 partnerScopeId 必须一致才允许演化。

---

## 与现有架构的契合

- 不引入新模块，新代码全部落到 `feature-import/profiling`（PersonaProfiler 扩展）+ `app/onboarding`（UI）+ `app/settings`（导出导入入口）。
- 不破坏 5 大抽象接口，不修改 ADR-0001（fingerprint schema）；ADR-0003（加密存储）已覆盖"画像导出文件不加密"的合理性，无需新 ADR。
- P9 与 P10 在实现上独立，可分别交付（P9 ROI 更高，建议先做）。
