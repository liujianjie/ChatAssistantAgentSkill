# 画像 v2 — Persona + 语料 + 检索式 few-shot

> 触发：自用反馈"画像太简单，根本不像我"。研究两个公开项目（zhangxuefeng-skill / ex-skill-web）后，发现共同 insight 是「风格的载体是大段非结构化文本 + 真实原话/示例」，不是结构化 enum 字段。当前 v1 的 6 维枚举把丰富信号压缩到了 5–10 个固定值，丢失了 99% 的风格信息。
>
> 本 spec 不照搬两个参考项目（它们都是"一次性扮演 chatbot"，我们是"实时副驾、有 token 预算、有隐私红线、要演化"），给出适配场景的折中方案。

## Problem Statement

**HMW** 让 LLM 拿到画像后，生成的候选回复**具体到话术层面**像用户本人，而不只是粗粒度的"语气大概对"？

当前痛点：候选生成 prompt 里的画像只有这点：

```
语言风格：CASUAL / MIXED
情感表达：BALANCED，常用表情：😄
幽默类型：OBSERVATIONAL
回避模式：REDIRECT
节奏：平均消息长度 25 字符
敏感话题处理：INDIRECT / EMPATHETIC
```

LLM 看 `tone=BALANCED` 不知道用户具体怎么说。生成出来的候选感觉"像 AI 写的"。

## Recommended Direction

**画像 v2 = 三件套**（保留 v1 作为可读总结，新增两件作为 LLM 输入主力）：

```
┌─────────────────────────────────────────────────────────────┐
│ A. 6 维结构化总结（v1 保留）                                 │
│   作用：UI 可读化展示 + 历史版本对比 + 类型层隐私护栏        │
│   不再是 LLM 输入主力                                         │
├─────────────────────────────────────────────────────────────┤
│ B. 行为规则文本（v2 新增）                                    │
│   PersonaProfiler 输出的一段 200–500 字中文 markdown          │
│   覆盖：高频口头禅 / 表达不满的具体说法 / 句尾习惯 /          │
│         接道歉的标准回复 / 冷处理触发条件 etc.                │
│   作用：LLM 候选生成 system prompt 主力                       │
├─────────────────────────────────────────────────────────────┤
│ C. 语料样本库（v2 新增，最关键）                              │
│   30–80 条用户本人真实消息，按场景分类：                      │
│     日常问候 / 调侃 / 拒绝 / 解释 / 安慰 / 冷处理 / ...        │
│   存本地加密 Room 表，候选生成时按对方消息检索 5–10 条进 prompt │
│   作用：让 LLM 看到"用户真的说过的话"作为 few-shot            │
└─────────────────────────────────────────────────────────────┘
```

候选生成新 prompt 形态：

```
[系统提示：模仿用户风格生成候选]
[B 行为规则文本，约 300 字]
[C 检索来的 5 条 few-shot 真实"我"的回复，标场景]
[对方最近 3 条消息]
[A 6 维总结，1 段精简版，约 80 字]
请输出 3 条候选回复...
```

## 关键判断

- **不上 fine-tune**：DeepSeek 不开放 fine-tune；上 LoRA/微调要本地训练或第三方平台，违反"自用先行 + 数据不出境"红线。Few-shot prompting + 检索增强是同等效果但零训练的等价路径。
- **检索用关键词/BM25 起步，不上向量库**：50–100 条语料的检索用 Lucene-Lite 或者纯 Kotlin BM25 实现就够，不引入 ObjectBox-vector / Annoy / Faiss。等数据量到 1000+ 再考虑升级。隐私红线：检索过程在端侧，永远不外发。
- **行为规则文本 vs 6 维结构化**：保留 6 维不是冗余，它做三件事 v2 文本做不了 ① 用户在 UI 看一眼就知道自己被刻画成什么样 ② 历史版本可结构化 diff（v3 -> v4 emoji 频率从 1.0 降到 0.3）③ 编译期类型隔离守隐私红线（FingerprintAggregator 只接 `Message.Mine`）。
- **演化画像（原 P10）天然并入**：PersonaProfiler 改造后，"基于旧画像 + 新对话演化"就是把旧的 B + C 一起喂给 LLM 让它产出新的 B + C。原 P10 不需要独立设计，并入本 spec。
- **语料库的"采样代表性"是新难点**：80 条要覆盖用户在不同场景的典型回复，不能全都是"嗯""好""收到"这种短消息。需要 PersonaProfiler 调 LLM 时显式要求"按场景挑选有代表性的原话"，而不是简单按长度排序。

## 与参考项目的关键差异

| 点 | ex-skill-web | zhangxuefeng-skill | 我们 v2 |
|---|---|---|---|
| Prompt 注入 | 一次性全文 markdown | 一次性全文 markdown + Agentic | 行为规则常驻 + 检索式 few-shot |
| Token 预算 | 不限 | 不限 | ~1500 input/次（DeepSeek 实时副驾） |
| 数据规模 | 一次性导入 | 一次性整理 | 持续累积 + 演化 |
| 隐私 | 用户自担 | 公开人物 | 严格本地，原话不长期出现在 prompt |
| 场景检索 | 无 | 无 | **核心** — 让 prompt 与当前对方消息高度相关 |

## 不做（避免范围膨胀）

- **不上向量检索**：BM25/关键词 + 场景分类标签足够。50–80 条语料量级用向量是过度工程。
- **不做"语料编辑 UI"**：用户不应该手工改自己的原话。语料只能从导入对话里采样产生；用户只能"删掉某条不喜欢的语料"（UI 上做 ⊖ 按钮，写个 deleted_at 字段）。
- **不做多人格切换**：partnerScopeId 字段保留但 v2 阶段不实现"对不同对象用不同语料"，先做全局画像。
- **不做语料自动演化**：语料更新走"重做画像/演化画像"两个明确入口，不背地里自动加减条目。

## 数据模型增量（高层）

新增 Room 表：

```kotlin
@Entity("style_corpus_samples")
data class CorpusSampleEntity(
  @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
  val fingerprintVersion: Int,        // 关联到哪个画像版本
  val text: String,                    // 用户的原话，已脱敏
  val scenario: String,                // 场景标签：日常问候/调侃/拒绝/解释/安慰/冷处理/...
  val createdAtEpochMs: Long,
  val deletedAt: Long? = null,         // 软删除，便于回滚
)
```

`StyleFingerprintEntity` 新增字段：

```kotlin
val behaviorRules: String  // 200–500 字 markdown，B 件
```

ADR-0005 落实存储/隔离决策（独立文档）。

## 候选生成流程（高层）

```
用户输入对话 → ConversationContext
        ↓
RetrieveCorpus(theirsLatestMsg, scenarioGuess) → 5–10 条候选语料
        ↓
buildPrompt(behaviorRules + retrievedSamples + structuredSummary + recentTheirs)
        ↓
LLMProvider.generateCandidates → 3 候选
```

`scenarioGuess` 第一版用关键词正则（"对不起" → 接道歉；"在吗" → 日常问候；"为什么" → 解释）。后续可上小型分类器。

## 演化画像合并（原 P10 并入）

PersonaProfiler 接受可选 `prior: Pair<StyleFingerprint, BehaviorRulesText, List<CorpusSample>>` —— 把旧的 B/C 也喂 LLM，让它在这个基础上对新对话做"漂移合并"，输出新的 B/C/6 维。语料库追加新条目，旧条目保留（用户可在 UI 删）。

## 验收（高层）

- [ ] PersonaProfiler 输出三件套：6 维 / 行为规则文本 / 语料样本（≥ 30 条，覆盖 ≥ 5 个场景）
- [ ] CandidateGenerator 改造为新 prompt 模板，候选生成端到端通
- [ ] 候选自评：自构造 5 段对话，对照 v1 prompt 与 v2 prompt 的输出，人工抽样判定 v2 至少 3 段更像"我会说的话"
- [ ] Token 占用：每次 candidate 调用 input ≤ 2500 tokens
- [ ] 隐私单测：语料库的字段在序列化/日志中过 PrivacyGuard 脱敏；候选 prompt 的 retrieved samples 全部脱敏
- [ ] 演化画像：构造 v1 画像 + 风格漂移明显的新对话 → 演化产出 v2 画像，行为规则文本可见地反映新风格

## 开放问题（实现期定）

- 行为规则文本的 prompt 模板细节（让 LLM 输出哪些维度才能既有用又不臃肿）
- 语料采样上限是 50 条还是 80 条（成本 vs 覆盖度）
- 场景分类是 6 个还是 10 个（粗细粒度）
- 候选生成 prompt 是用 system role 注入 behaviorRules，还是 user role
- 当语料库为空（v1 老用户升级到 v2）时的回退路径
- 用户删除某条语料后，演化画像是否仍以"它存在过"为参考？建议否
