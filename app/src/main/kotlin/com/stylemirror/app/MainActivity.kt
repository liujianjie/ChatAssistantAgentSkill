package com.stylemirror.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylemirror.app.ui.MainScreen
import com.stylemirror.app.ui.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

private enum class AppScreen { MAIN, SETTINGS }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    var screen by rememberSaveable { mutableStateOf(AppScreen.MAIN) }

                    val pasteText by viewModel.pasteText.collectAsStateWithLifecycle()
                    val generateState by viewModel.generateState.collectAsStateWithLifecycle()
                    val apiKeyHint by viewModel.apiKeyHint.collectAsStateWithLifecycle()

                    when (screen) {
                        AppScreen.MAIN ->
                            MainScreen(
                                pasteText = pasteText,
                                generateState = generateState,
                                onPasteChange = viewModel::onPasteTextChange,
                                onGenerate = viewModel::generate,
                                onAdopt = { item ->
                                    viewModel.adopt(item)
                                    copyToClipboard(item.candidate.text)
                                },
                                onModify = { item, edited ->
                                    viewModel.modify(item, edited)
                                    copyToClipboard(edited)
                                },
                                onDiscard = viewModel::discard,
                                onOpenSettings = { screen = AppScreen.SETTINGS },
                            )

                        AppScreen.SETTINGS ->
                            SettingsScreen(
                                apiKeyHint = apiKeyHint,
                                onSave = { key ->
                                    viewModel.saveApiKey(key)
                                    screen = AppScreen.MAIN
                                },
                                onClear = viewModel::clearApiKey,
                                onBack = { screen = AppScreen.MAIN },
                            )
                    }
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("候选回复", text))
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}
