package com.stylemirror.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylemirror.app.onboarding.OnboardingViewModel
import com.stylemirror.app.ui.MainScreen
import com.stylemirror.app.ui.OnboardingScreen
import com.stylemirror.app.ui.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

private enum class AppScreen { MAIN, SETTINGS }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val routeViewModel: AppRouteViewModel by viewModels()
    private val onboardingViewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val route by routeViewModel.route.collectAsStateWithLifecycle()
                    when (route) {
                        AppRoute.LOADING ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }

                        AppRoute.ONBOARDING -> OnboardingFlow()

                        AppRoute.MAIN -> MainFlow(onReprofile = routeViewModel::goToOnboarding)
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun OnboardingFlow() {
        val state by onboardingViewModel.state.collectAsStateWithLifecycle()
        val aliases by onboardingViewModel.aliases.collectAsStateWithLifecycle()
        val pasteText by onboardingViewModel.pasteText.collectAsStateWithLifecycle()
        OnboardingScreen(
            state = state,
            aliases = aliases,
            pasteText = pasteText,
            onAliasesChange = onboardingViewModel::onAliasesChange,
            onPasteChange = onboardingViewModel::onPasteChange,
            onConfirmAliases = onboardingViewModel::confirmAliases,
            onBackToAliases = onboardingViewModel::backToAliases,
            onRunProfiling = onboardingViewModel::runProfiling,
            onRetry = onboardingViewModel::resetToAskAliases,
            onFinish = routeViewModel::onProfileCreated,
        )
    }

    @androidx.compose.runtime.Composable
    private fun MainFlow(onReprofile: () -> Unit) {
        var screen by rememberSaveable { mutableStateOf(AppScreen.MAIN) }
        val pasteText by viewModel.pasteText.collectAsStateWithLifecycle()
        val generateState by viewModel.generateState.collectAsStateWithLifecycle()
        val apiKeyHint by viewModel.apiKeyHint.collectAsStateWithLifecycle()
        val screenshotState by viewModel.screenshotState.collectAsStateWithLifecycle()

        val photoPicker =
            androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
            ) { uri: android.net.Uri? ->
                if (uri != null) viewModel.captureScreenshot(uri)
            }

        when (screen) {
            AppScreen.MAIN ->
                MainScreen(
                    pasteText = pasteText,
                    generateState = generateState,
                    screenshotState = screenshotState,
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
                    onPickScreenshot = {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                mediaType =
                                    androidx.activity.result.contract.ActivityResultContracts
                                        .PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    onDismissScreenshotError = viewModel::dismissScreenshotError,
                    onOpenSettings = { screen = AppScreen.SETTINGS },
                    onReprofile = onReprofile,
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

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("候选回复", text))
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}
