package com.stylemirror.feature.imports.source

import kotlinx.coroutines.flow.Flow

/**
 * A lazy source of raw chat messages for bulk import.
 *
 * Implementations must stream lazily — never buffer the full corpus in memory.
 * The caller can cancel the collection at any time via coroutine cancellation.
 *
 * Error handling: implementations should emit [kotlinx.coroutines.flow.catch]
 * compatible errors via [Flow] completion rather than throw synchronously.
 * [com.stylemirror.domain.error.DomainError.NotImplemented] is the correct
 * error for stub implementations that are not yet wired.
 *
 * Production implementations:
 *  - [PlainTextImportSource] — plain text file or pasted string (T10)
 *  - WeChatPcExportImportSource — WeChat PC export format (T10 stub → P1)
 *  - WeChatBackupImportSource  — WeChat mobile backup (T10 stub → P1)
 *  - BatchScreenshotImportSource — batch screenshot OCR (T19)
 *  - ThirdPartyToolImportSource — generic CSV/JSON exports (T10 stub)
 */
interface ImportSource {
    fun stream(): Flow<RawMessage>
}
