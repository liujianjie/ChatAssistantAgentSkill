# P1 悬浮窗 / 自动读取交互

> Idea-refine 阶段产出。本 spec 来自自用反馈："手动粘贴对话太拉，原本核心需求是悬浮窗自动读取生成回复"。
> 范围：P1（不在 P0 MVP 里），但根据用户优先级判断，P0 收尾后立即进入。

## Problem Statement

**HMW** 让用户在 Soul/微信等社交 App 里收到消息时，**不离开聊天界面、不复制粘贴**，就能拿到 3 条候选回复并采纳？

当前体验断点：
1. 收到消息 → 切到风格镜像 App
2. 手动复制对话内容、粘贴到输入框
3. 点"生成候选" → 复制候选 → 切回 Soul → 粘贴发送

每条消息要切 4 次 App、做 3 次手工搬运。这不是"AI 副驾"，是"AI 对话框"。

## 三阶演进路径（不要一步到位）

按工程量从轻到重，每一阶都能独立交付，不阻塞下一阶：

### P1.a 系统分享 sheet 接收（最轻，~1 周）

**做什么**：注册 `ACTION_SEND` / `ACTION_PROCESS_TEXT` Intent filter，让用户在 Soul 长按消息 → 「分享」 → 选风格镜像 → 直达候选生成。

**优点**：
- 工程量小，不需要任何特殊权限
- 不依赖 Accessibility / MediaProjection，零隐私争议
- Android 系统级支持，所有 App 都能用

**缺点**：
- 还是要"长按 + 选分享"两步，不是真正的悬浮窗
- 只能分享一条消息，无法带上下文

**收益评估**：把"切 4 次"压到"长按 + 1 次"，体验跃迁明显，但仍非终态。

### P1.b 截屏触发 OCR（中等，~2 周）

**做什么**：用户在 Soul 截屏（系统级动作） → 风格镜像监听截图新增（`MediaStore` 观察者，仅访问截屏目录） → 弹通知 → 点击通知直达候选生成（OCR + Soul PlatformAdapter 现有路径）。

**优点**：
- 不需要 Accessibility，权限模型干净（仅 `READ_MEDIA_IMAGES`）
- 复用 P0 已有的 OCR + SoulPlatformAdapter 链路，工程量集中在前台服务和通知交互
- 用户主动截屏才触发，符合"主动召唤副驾"的心智

**缺点**：
- 多一步截屏动作，仍非"无缝"
- 需要用户允许通知权限

**收益评估**：从"长按分享"到"截屏即得"，是体验的最后一公里。但还不是悬浮窗。

### P1.c Accessibility 实时悬浮窗（重，~3-4 周）

**做什么**：申请 `BIND_ACCESSIBILITY_SERVICE`，监听 Soul 包名的对话窗口节点 → 悬浮气泡按需展开 → 实时拿到对方消息文本 → 候选回复展示在悬浮窗内 → 一键复制/直接注入输入框。

**优点**：
- 真正的"零切换"体验，符合产品愿景
- 文本来自 Accessibility 节点而非 OCR，准确率更高、延迟更低
- 与 InputAdapter 抽象天然契合（OverlayInput 在 T06 已留 stub）

**缺点**：
- Accessibility 是 Android 上**最敏感的权限**，用户警惕度高，引导文案要谨慎
- Soul 的 UI 节点结构如果改版，Adapter 要跟改（与 OCR 路径一样脆弱，但失败模式不同）
- Google Play 对 Accessibility 用途审查严格（自用阶段不上架可暂忽略，但 P2 推广阶段要面对）
- 悬浮窗权限（`SYSTEM_ALERT_WINDOW`）也要单独申请

**收益评估**：体验终态。但风险/工程量都最大，应在 P1.a + P1.b 实证用户使用频率高再启动。

## 关键判断

- **不要跳过 P1.a 直接做 P1.c**：P1.a 在 1 周内能交付，立刻验证"用户是否真的会在收到消息时召唤副驾"这个核心假设。如果连 P1.a 都用得少，P1.c 投入是浪费。
- **三阶共用同一条 ConversationContext 管线**：T06 的 `ShareSheetInput`（P1.a）+ `ScreenshotInput`（P1.b 复用 T17 的）+ `OverlayInput`（P1.c）都已在 InputAdapter 抽象下留 stub，新代码不破坏 P0 架构。
- **隐私红线全程不松**：Accessibility 拿到的对方消息一样要过 PrivacyGuard 脱敏；Me 的消息绝不上送 LLM；候选生成的 prompt 经过统一 redaction（与 P0 一致）。
- **P1.a 与 P9（画像导出）正交**：体验提升和数据可控是两条独立的用户价值线，不要排队。

## 不做（避免范围膨胀）

- **不做悬浮窗的"自动主动弹出"**：必须用户主动召唤（长按分享 / 截屏 / 点悬浮气泡），App 不在用户没要求的时候出现在屏幕上。这是产品哲学红线（"筛选喜欢真我的人"，不是"AI 替我说话"）。
- **不做实时输入框注入**：候选回复给用户复制，不直接帮用户敲到输入框里。理由同上：用户对每条出口的话有最终编辑权。
- **不在 P1 阶段适配除 Soul 之外的平台**：微信/QQ 适配是新的 PlatformAdapter，不算 P1 范围；P1 三阶都先以 Soul 为唯一目标。

## 与已有抽象的对接

| 抽象 | 已有 stub | P1 实现位置 |
|---|---|---|
| `InputAdapter` | `ShareSheetInput` / `ScreenshotInput` / `OverlayInput`（T06）| P1.a / P1.b / P1.c 各填一个 |
| `PlatformAdapter` | `SoulPlatformAdapter`（T18）| 三阶都复用，无需新增 |
| `OcrProvider` | `MlKitOcrProvider`（T16）| P1.b 直接用 |
| 候选生成 | `CandidateGenerator`（T07）| 三阶共用，零修改 |

## 开放问题（spec 阶段不阻塞）

- 通知通道的优先级（P1.b 触发提示用 IMPORTANCE_HIGH 还是 DEFAULT？影响打扰程度）
- 悬浮气泡的视觉形态（圆形头像气泡 vs 半透明侧边条），P1.c 设计期定
- Accessibility Service 在 Soul 后台时的电池开销，P1.c 落地后压测
- 系统分享 sheet 是否同时接收图片（拓展到 OCR 路径），P1.a 实现时决定

## 验收（每阶高层）

**P1.a 完成判据**：从 Soul 长按消息分享到风格镜像，候选生成在 < 5s 内出现，用户点采纳后剪贴板就绪可粘贴回 Soul。

**P1.b 完成判据**：在 Soul 截屏后 ≤ 3s 收到通知，点通知直达候选生成（OCR + 说话人识别错位率 < 5%，与 T18 一致）。

**P1.c 完成判据**：在 Soul 内打开对话，悬浮气泡可见 → 展开 → ≤ 3s 看到 3 候选；关闭对话气泡消失；电池消耗增加 < 5% / 24h（手动观察）。

## 推进顺序

1. P1.a 先做（1 周交付）+ 自用 1 周观察使用频次
2. 频次达标（每天 ≥ 5 次主动召唤）→ 启动 P1.b
3. P1.b 自用稳定 → 评估是否进 P1.c（结合 Google Play 上架计划）
