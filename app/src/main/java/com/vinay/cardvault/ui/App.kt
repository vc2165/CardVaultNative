package com.vinay.cardvault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vinay.cardvault.AppState
import com.vinay.cardvault.Route

private data class Screen(val depth: Int, val route: Route?)

@Composable
fun CardVaultApp(st: AppState) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        AnimatedContent(
            targetState = st.session != null,
            transitionSpec = { fadeIn(tween(380, easing = Ease)) togetherWith fadeOut(tween(250)) },
            label = "lock",
        ) { unlocked ->
            if (unlocked) Main(st) else LockScreen(st)
        }
        SnackbarHost(st.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 80.dp)) {
            Snackbar(it, containerColor = Color(0xFF2B2930), contentColor = Color.White)
        }
    }
}

@Composable
private fun Main(st: AppState) {
    BackHandler(enabled = st.stack.isNotEmpty()) { st.pop() }
    Box(Modifier.fillMaxSize()) {
        HomeScreen(st)
        AnimatedContent(
            targetState = Screen(st.stack.size, st.stack.lastOrNull()),
            transitionSpec = {
                val fwd = targetState.depth > initialState.depth
                if (fwd) (slideInHorizontally(tween(400, easing = Ease)) { it } + fadeIn(tween(290))) togetherWith
                    (slideOutHorizontally(tween(400, easing = Ease)) { -it / 5 } + fadeOut(tween(325)))
                else (slideInHorizontally(tween(400, easing = Ease)) { -it / 5 } + fadeIn(tween(325))) togetherWith
                    (slideOutHorizontally(tween(360, easing = Ease)) { it } + fadeOut(tween(290)))
            },
            contentKey = { it.depth.toString() + it.route.toString() },
            label = "nav",
        ) { s ->
            val r = s.route
            if (r != null) Box(Modifier.fillMaxSize().background(Bg)) { RouteScreen(st, r) }
        }
    }
}

@Composable
private fun RouteScreen(st: AppState, r: Route) {
    when (r) {
        is Route.Detail -> DetailScreen(st, r.id)
        is Route.Edit -> EditScreen(st, r.id)
        is Route.Share -> ShareScreen(st, r.id)
        Route.Runway -> RunwayScreen(st)
        Route.Arrange -> ArrangeScreen(st)
        Route.Settings -> SettingsScreen(st)
        Route.ChangePassword -> ChangePasswordScreen(st)
    }
}
