package com.stylemirror.feature.imports.source

import com.stylemirror.domain.error.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private fun notImplemented(): Nothing = throw UnsupportedOperationException(DomainError.NotImplemented.toString())

/** WeChat PC client export format — not yet implemented; planned for P1. */
class WeChatPcExportImportSource : ImportSource {
    override fun stream(): Flow<RawMessage> = flow { notImplemented() }
}

/** WeChat mobile backup format — not yet implemented; planned for P1. */
class WeChatBackupImportSource : ImportSource {
    override fun stream(): Flow<RawMessage> = flow { notImplemented() }
}

/** Generic third-party tool export (CSV/JSON) — not yet implemented. */
class ThirdPartyToolImportSource : ImportSource {
    override fun stream(): Flow<RawMessage> = flow { notImplemented() }
}
