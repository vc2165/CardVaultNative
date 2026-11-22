package com.vinay.cardvault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.vinay.cardvault.AppState
import com.vinay.cardvault.Route
import kotlinx.coroutines.launch

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

private const val OPEN_MS = 400
private const val CLOSE_MS = 420

/**
 * Screens stack on top of Home like Android's own navigation.
 * Back (swipe or arrow) slides the top screen away to the right.
 */
@Composable
private fun Main(st: AppState) {
    val scope = rememberCoroutineScope()
    val offsets = remember { mutableStateMapOf<Int, Animatable<Float, AnimationVector1D>>() }
    var closing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        st.animatedPop = {
            val a = offsets[st.stack.lastIndex]
            if (a == null || closing) st.popNow()
            else scope.launch {
                closing = true
                try { a.animateTo(1f, tween(CLOSE_MS, easing = Ease)) } finally { st.popNow(); closing = false }
            }
        }
        onDispose { st.animatedPop = null }
    }

    // Any back swipe closes the screen straight away (easy to trigger); the screen then slides out smoothly
    BackHandler(enabled = st.stack.isNotEmpty()) { st.pop() }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(st)
        st.stack.forEachIndexed { i, r ->
            key(i, r) {
                val a = remember { Animatable(1f) }
                DisposableEffect(Unit) {
                    offsets[i] = a
                    onDispose { if (offsets[i] === a) offsets.remove(i) }
                }
                LaunchedEffect(Unit) { a.animateTo(0f, tween(OPEN_MS, easing = Ease)) }
                val isTop = i == st.stack.lastIndex
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            translationX = a.value * size.width
                            shadowElevation = if (a.value > 0f) 24f else 0f
                        }
                        .background(Bg)
                        // swallow taps so nothing underneath reacts
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = isTop) {},
                ) { RouteScreen(st, r) }
            }
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
