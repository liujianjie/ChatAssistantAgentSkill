package com.stylemirror.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylemirror.core.data.repository.StyleFingerprintStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Top-level routing state for the app.
 *
 * Decides whether to land on onboarding or the main screen by querying the
 * latest [com.stylemirror.domain.style.StyleFingerprint] in the encrypted Room
 * DB on startup. Onboarding completion calls [onProfileCreated] to flip the
 * route to [AppRoute.MAIN] without a DB round-trip.
 */
@HiltViewModel
class AppRouteViewModel
    @Inject
    constructor(
        private val store: StyleFingerprintStore,
    ) : ViewModel() {
        private val _route = MutableStateFlow<AppRoute>(AppRoute.LOADING)
        val route: StateFlow<AppRoute> = _route.asStateFlow()

        init {
            viewModelScope.launch {
                _route.value = if (store.findLatest() != null) AppRoute.MAIN else AppRoute.ONBOARDING
            }
        }

        fun onProfileCreated() {
            _route.value = AppRoute.MAIN
        }

        fun goToOnboarding() {
            _route.value = AppRoute.ONBOARDING
        }
    }

enum class AppRoute { LOADING, ONBOARDING, MAIN }
