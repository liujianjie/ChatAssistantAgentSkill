# ADR-0003: 加密存储策略 (Encryption and Storage Strategy)

**状态**：已接受（Accepted）  
**日期**：2026-05-24  
**上下文模块**：`core-data`  

---

## 背景

风格镜像应用在本地持久化四类敏感数据：

1. 用户聊天消息快照（用于说话人对齐和画像提取）
2. 风格指纹（基于个人聊天行为的 6 维向量）
3. 反馈信号（用户对候选回复的采纳/修改/丢弃行为）
4. 导入会话元数据

上述数据属于 SPEC §6.3 隐私红线范围：不出境、不上传、不存入明文文件。

---

## 决策

### 1. 数据库加密 — SQLCipher

使用 [SQLCipher for Android](https://www.zetetic.net/sqlcipher/sqlcipher-for-android/) 对 Room 数据库文件进行透明加密。

**理由**：
- 透明加密：Room/SQLite API 无感，只需替换 `SupportFactory`。
- AES-256-CBC，业界标准，满足移动端合规要求。
- 密钥由 `DatabasePassphraseProvider` 管理，存放于 `SecureKeyStore`（EncryptedSharedPreferences / AES256-GCM），不出 Keystore。

**加密文件**：`style_mirror.db`（设备存储路径由 Android 系统决定，通常在 app 私有目录下）。

**验证方式**：`adb pull` 后用 `sqlite3` 尝试打开，预期报 `file is not a database`。

### 2. API Key 存储 — EncryptedSharedPreferences

DeepSeek API Key、数据库 Passphrase 等所有短字符串秘密走 `SecureKeyStore` 接口，生产实现为 `SharedPrefsSecureKeyStore`（AndroidX Security `EncryptedSharedPreferences`，AES256-SIV + AES256-GCM）。

**不做**：不写入 `settings.json`、不进 git、不出现在 log 或 crash report。

### 3. 数据库 Schema 设计约束

- **禁止** schema 字段名包含 `wechat_id`、`phone`、`real_name`、`id_card` 等明文身份标识。
- `partner_id` 是 app 内部分配的不透明 UUID，不等于任何平台账号。
- `speaker` 用枚举字符串 `"ME"` / `"THEIRS"`，类型隔离在域模型层（`Message.Mine` / `Message.Theirs`）。

### 4. 测试策略

- **单元测试**使用 `Room.inMemoryDatabaseBuilder`（不带 `SupportFactory`），因为 Robolectric 没有 SQLCipher 原生库。
- **集成测试**（手动验证）：在真机上 `adb pull` 并用 sqlite3 验证文件为加密格式。

---

## 已考虑但未采纳的替代方案

| 方案 | 理由弃用 |
|---|---|
| 明文 SQLite + 应用层字段加密 | 代码侵入性高，DAO 层要做 Codec 映射，风险高于 SQLCipher 整库加密 |
| 云端 KMS 加密密钥 | 增加网络依赖，违反"不出境"红线 |
| 不加密（仅 Android 沙盒隔离） | 不满足 SPEC §6.3 要求；Root 设备或 ADB backup 可读 |

---

## 影响

- `core-data/build.gradle.kts` 引入 `net.zetetic:android-database-sqlcipher`。
- App DI 层（`AppModule`）需在调用 `StyleMirrorDatabase.create()` 前先 `suspend` 获取 passphrase（`DatabasePassphraseProvider.getOrCreate`），需在 IO 协程调用。
- Room schema 文件导出至 `core-data/schemas/`，版本迁移可通过 `MigrationTestHelper` 验证。
