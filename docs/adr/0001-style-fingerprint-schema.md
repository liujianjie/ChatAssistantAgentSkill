# ADR-0001: StyleFingerprint Schema 与 Speaker / Message 类型隔离

- 状态: Accepted
- 日期: 2026-05-01
- 决策者: liujianjie
- 相关任务: T02 (core-domain 数据模型)
- 相关 SPEC 段落: §1.5（核心要素）、§3.2（多模块边界）、§4.4（错误处理）、§6.3（红线）

## 上下文

T02 要求在纯 Kotlin 的 `core-domain` 模块里落地三件事：

1. 6 维 StyleFingerprint 的 schema —— 不能用 `Map<String, Any>` 兜底，否则后续模块会无声地丢字段。
2. SPEC §6.3 第 1 条红线 ——「绝不把对方消息内容聚合进 StyleFingerprint」要在编译期可被工具发现。
3. `DomainError` 沿 SPEC §4.4 暴露 typed 错误通道，配合 `Outcome<T, E>` 取代 stdlib `Result`。

因为这一层是其他 9 个模块的基底，schema 形状定下来之后改起来代价高。

## 决策

### 1. 用 sealed `Message` + `Mine` / `Theirs` 做说话人隔离（不用 phantom 类型）

```kotlin
sealed interface Message {
    val id: MessageId
    val content: String
    val sentAt: Instant

    data class Mine(...) : Message
    data class Theirs(displayName: String, ...) : Message
}

fun interface FingerprintAggregator {
    fun aggregate(messages: List<Message.Mine>): StyleFingerprint
}
```

调用方用 `messages.filterIsInstance<Message.Mine>()` 收窄。

**为什么不用 phantom 类型 `Message<S : Speaker>`？**
- 表达力相同（编译期都能阻止把 Theirs 喂给 aggregator），但 phantom 版本要写一次 `inline fun List<Message<Speaker>>.filterMe()` + unchecked cast 才能从混合列表里抽 Me。
- 日常 ConversationContext 操作要带泛型参数，签名复杂度上升一层，跨模块阅读成本变高。
- sealed + filterIsInstance 是 Kotlin 最惯用的模式，detekt/ktlint 都不会卡。

**为什么不用两个完全分开的 `MyMessage` / `TheirMessage`？**
- 失去公共 `List<Message>` 视图，ConversationContext 要么变两个 list，要么用一个 sealed wrapper —— 等价于现在的方案，绕远路。

### 2. 6 维风格指纹用具体 data class，每维独立类型

每维一个 data class，所有可枚举字段用 enum，所有数值用 `NormalizedScore`（[0,1]）或 `NonNegativeFloat` value class。

| 维度 | 数据类 | 关键字段 |
|---|---|---|
| 1. 语言风格 | `LinguisticStyle` | formality / vocabularyComplexity / sentencePattern / signaturePhrases |
| 2. 情感表达 | `EmotionalExpression` | emojiDensityPer100Chars / exclamationFrequency / tone / preferredEmojis |
| 3. 幽默类型 | `HumorStyle` | frequency / types: Set<HumorType> |
| 4. 回避模式 | `AvoidancePatterns` | topicsAvoided / hedgingFrequency / deflectionStrategy |
| 5. 节奏特征 | `PacingTraits` | avgMessageLengthChars / avgMessagesPerTurn / responseDelay |
| 6. 敏感话题处理 | `SensitiveTopicHandling` | directness / approach |

`StyleFingerprint` 顶层带 `version: Int` 与 `createdAt: Instant`，并保留可选的 `partnerScope: PartnerId?` —— 后续支持「按对象切画像」。

**为什么不用 `Map<String, Any>` 或 JSON Schema？**
- 下游消费者（prompt 拼装、UI 总结、相似度评分）需要 schema 漂移时编译失败，而不是运行期 NPE。
- LLM 输出后做结构化校验时，data class + kotlinx.serialization 比 Map 反射更简单。

### 3. 红线工程化 —— 编译期阻止「对方消息进画像」

所有「输入是用户自己消息」的入口（aggregator、incremental learner、prompt builder）签名都声明 `List<Message.Mine>`。误传 `List<Message>` 或 `List<Message.Theirs>` 在编译期就是类型不兼容。

代码评审或 lint 不再需要「请检查一下有没有混进对方消息」这条人工规则；规则被翻译成类型签名。

### 4. 错误通道用 `Outcome<T, E>`，不用 stdlib `Result`

`kotlin.Result` 把错误锁死成 `Throwable`，无法表达 `Outcome<List<Candidate>, DomainError.LlmFailure>` 这种窄化。
`Outcome` 是简单的 sealed interface（`Ok` / `Err`），配 `map`、`mapError`、`flatMap`、`getOrElse` 顶层函数。

`DomainError` 覆盖 plan 里要求的 5 类：LLM 失败 / OCR 失败 / 导入失败 / 画像不足 / 配额超限，再加一个 `NotImplemented` 给 P1 stub 用。每个失败子类带枚举原因（`LlmFailureReason`、`OcrFailureReason`、`ImportFailureReason`），方便 UI 做用户可读映射。

### 5. 时间戳用 `java.time.Instant`

`kotlinx-datetime` 不在 libs catalog，且 KotlinJvmConventionPlugin 用的是 `kotlin.jvm`（不是 multiplatform），JDK 17 自带的 `java.time.Instant` 够用。如果将来要把 core-domain 升级到 multiplatform，这一项需要回滚。

## 替代方案与未采纳原因

| 方案 | 不采纳原因 |
|---|---|
| Phantom 类型 `Message<S : Speaker>` | 工程感更复杂，强制泛型蔓延，收益（运行时一致性）已被 sealed 方案完全覆盖 |
| 完全分开的 MyMessage / TheirMessage | 失去公共视图，ConversationContext 设计回退 |
| `Map<String, Any>` 风格指纹 | 漂移检测靠运行期，违背"红线工程化"目标 |
| 用 `kotlin.Result` | 错误通道锁死 Throwable，与 typed DomainError 冲突 |
| 用 `kotlinx.datetime` | 依赖未引入，且当前没有跨平台诉求 |

## 后果

**正向**：
- 隐私红线（SPEC §6.3 #1）由编译器守门，不需要 review 或 lint 兜底。
- schema 漂移立刻在调用方编译失败，不会无声丢字段。
- 新增 DomainError 变体强制所有 `when` 分支显式处理。

**负向 / 待观察**：
- 某条用户消息的风格元素如果天然依赖上下文（"对方在问 A，所以我用 B 答"），现版本无法直接表达；T14（PersonaProfiler）要在 prompt 层补这部分语境，而不是把 Theirs 泄进 schema。
- `signaturePhrases`、`preferredEmojis`、`topicsAvoided` 三个 List 字段都设了上限（30/20/20），超过会抛 `IllegalArgumentException`。如果 LLM 输出超长需要在解析层截断，而不是改 schema。
- `partnerScope: PartnerId?` 是预留扩展点；P0 全局画像不带 partner 维度，P1 接入按对象切画像时再实例化。

## 红线测试守卫

- `core-domain` 单测覆盖率 ≥ 85%（CI 由 jacoco 报告，当前实测 95% INSTRUCTION / 98% BRANCH）。
- T07（CandidateGenerator）会再补一条单测：构造 prompt 时上送 LLM 的 payload 不含 `Message.Theirs`，与本 ADR 的类型守卫互为冗余。
- T14（PersonaProfiler）的 LLM payload 同样必须只读 `List<Message.Mine>`，单测断言。
