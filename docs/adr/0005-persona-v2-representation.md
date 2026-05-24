# ADR-0005: 画像 v2 表示从结构化字段升级为 Persona + 语料

- 状态: Proposed
- 日期: 2026-05-24
- 决策者: liujianjie
- 相关任务: T29（P11 画像 v2 — 待 spec 评审通过后排期）
- 相关 spec: `docs/ideas/persona-v2.md`
- 取代: 部分覆盖 ADR-0001 的 6 维 schema 定位（不删除，降级为 UI 展示与隐私护栏）

## 上下文

ADR-0001 定下的 6 维结构化 fingerprint（formality / tone / humor / avoidance / pacing / sensitive）在自用阶段暴露问题：枚举字段把丰富的风格信号压缩到 5–10 个固定值，候选生成 prompt 中的画像段落只有约 70 个字，LLM 拿到无法生成具体到话术的"像我"的回复。

研究两个公开项目（`zhangxuefeng-skill` / `ex-skill-web`）后发现共同 insight：风格的有效载体是**大段非结构化文本 + 真实原话/示例**，而不是结构化 enum。两者均为"一次性扮演 chatbot"形态、token 不限、无演化要求、无严格隐私红线。我们场景四点都不同，不能直接照搬。

## 决策

画像存储与 LLM 输入分离为三件套，三者独立可演进：

### 1. 保留 6 维结构化字段（A）— 但降级用途

- 不再是 LLM 输入主力
- 仅用于：UI 可读化展示、历史版本结构化 diff、类型层隐私护栏（`FingerprintAggregator` 仍只接 `Message.Mine`）
- ADR-0001 的隐私护栏决策完全保留

### 2. 新增"行为规则文本"（B）— 200–500 字 markdown

- PersonaProfiler 第二段输出，从用户本人消息归纳出具体话术规则
- 例："表达不满时倾向先说『行吧』+ 转移话题，几乎不直接反驳"
- 落 `StyleFingerprintEntity.behaviorRules: String` 字段（与 fingerprintJson 同行）
- 候选生成 prompt 常驻塞入

### 3. 新增"语料样本库"（C）— Room 独立表

- 新增 `style_corpus_samples` 表：(rowId, fingerprintVersion, text, scenario, createdAtEpochMs, deletedAt)
- 30–80 条本人真实消息，按场景分类标签（日常问候/调侃/拒绝/解释/安慰/冷处理/...）
- 候选生成时根据对方当前消息做关键词/BM25 检索，挑 5–10 条最相关的作为 few-shot 进 prompt
- 软删除（deletedAt），便于"删了又后悔"回滚

## 拒绝的方案

### a. 直接照搬 ex-skill-web 的"全文 markdown 注入 system prompt"

**否决理由**：
- 我们每条候选都付费，input token 1500 vs 10000 的 6× 差距在自用 100 次/天就显著（DeepSeek input 0.001/千 tokens）
- 1 万字 markdown 会把对方最近消息的"权重"稀释，LLM 容易忽略当前上下文只复读老内容
- 原始聊天长期常驻 LLM 上下文违反"原话仅运行时短暂入 prompt"的隐私偏好

### b. 上向量检索（ObjectBox-vector / Annoy / 本地 sentence-transformer）

**否决理由**：
- 50–80 条语料量级用 BM25/关键词检索完全够，召回率不是瓶颈
- 引入向量库 + Embedding 模型显著增加 APK 体积（≥ 50 MB）和冷启动时间
- 让"语料库"这个特性的最小 viable 形态变重，违反"先验证假设再优化"原则
- 等数据量到 1000+ 条且 BM25 召回明显不够时再升级（YAGNI）

### c. 上 fine-tune / LoRA

**否决理由**：
- DeepSeek 不开放 fine-tune
- 第三方平台或本地训练违反"数据不出境 + 自用先行"红线
- Few-shot prompting + 检索增强在 50–80 条数据量级效果等价，零训练成本
- 用户行为持续演化，每次重训成本远高于 prompt 重组

### d. 单点扩字段（只加 B 不加 C）

**否决理由**：
- B 是"AI 总结的我"，C 是"我自己说过的原话"，前者经过 LLM 一次抽象后不可避免失真
- 缺 C 的话，B 即使写得很好也是"作文本"，LLM 还是无法看到具体话术，提升有限
- 工程量上 C 不显著大于 B（多一张 Room 表 + 一段采样逻辑），ROI 高

## 后果

### 正面

- 候选质量预期跃迁（待 spec 验收的"自评 5 段"实测验证）
- 演化画像（原 P10）天然并入：旧 B/C 一起喂 LLM 产出新 B/C，无需独立设计
- 语料是数据资产，未来如果接入 fine-tune / RAG / 端侧小模型，C 都是直接复用的训练集

### 负面

- 数据库 schema 加 `behaviorRules` 字段 + 新表 → migration 1→2
- 端到端候选生成成本从 ~300 token 涨到 ~1500–2500 token（自用 100 次/天约 ¥0.15-0.25，可接受）
- BM25 实现需要中文分词（用 Jieba-Android 或 HanLP-lite），增加~5 MB 依赖
- v1 老用户的画像没有 B/C，需要"补建画像"流程（让用户重新画像或演化画像生成 v2 数据）

### 中性

- ADR-0001 不撤销但作用域收窄；后续若有 v3 重新评估两份 ADR 一起看
- 隐私红线复杂度持平：FingerprintAggregator 类型护栏不变，新增 CorpusStore 同样只接 `Message.Mine`，编译期保证

## 迁移路径

1. Migration 1→2：`style_fingerprints` 加 `behavior_rules TEXT NOT NULL DEFAULT ''`；新建 `style_corpus_samples` 表
2. v1 老画像的 `behavior_rules` 留空；UI 上提示用户"演化画像即可生成 v2 数据"
3. 候选生成 prompt 在 behaviorRules 为空时退化为 v1 prompt 形态（保持向后兼容，不强迫所有用户立即重做画像）
4. T29 的 spec 评审通过后排独立 plan 任务序列（PersonaProfiler 改造 / CorpusStore / 检索 / Prompt 重做 / 演化合并）
