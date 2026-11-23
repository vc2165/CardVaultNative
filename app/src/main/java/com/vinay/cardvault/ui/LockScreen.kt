package com.vinay.cardvault.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.vinay.cardvault.AppState
import com.vinay.cardvault.data.Session
import com.vinay.cardvault.data.Vault
import com.vinay.cardvault.data.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable
fun Logo(size: Int = 84) {
    // Same picture as the app icon: fanned cards with a shield, on purple
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.width / 72f
        fun o(x: Float, y: Float) = Offset((x - 18f) * s, (y - 18f) * s)
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFF4B2BB8)), Offset.Zero, Offset(this.size.width, this.size.height)),
            cornerRadius = CornerRadius(18 * s),
        )
        val pivot = o(51f, 56f)
        fun card(deg: Float, color: Color, front: Boolean) = rotate(deg, pivot) {
            drawRoundRect(color, topLeft = o(31f, 42f), size = Size(40 * s, 26 * s), cornerRadius = CornerRadius(4 * s))
            if (front) {
                drawRect(Color(0xD95B2FC4), topLeft = o(31f, 47f), size = Size(40 * s, 4.6f * s))
                drawRoundRect(Color(0xFFF5D77A), topLeft = o(35f, 56f), size = Size(8.5f * s, 6 * s), cornerRadius = CornerRadius(1.2f * s))
            }
        }
        card(-14f, Color(0xFFF5B94A), false)
        card(-5f, Color(0xFF22C3A6), false)
        card(5f, Color.White, true)
        fun X(x: Float) = (x - 18f) * s
        fun Y(y: Float) = (y - 18f) * s
        val shield = Path().apply {
            moveTo(X(69f), Y(55.5f)); lineTo(X(78f), Y(58.7f)); lineTo(X(78f), Y(65.1f))
            cubicTo(X(78f), Y(71f), X(74f), Y(75.3f), X(69f), Y(77.5f))
            cubicTo(X(64f), Y(75.3f), X(60f), Y(71f), X(60f), Y(65.1f))
            lineTo(X(60f), Y(58.7f)); close()
        }
        drawPath(shield, Color.White)
        drawPath(shield, Color(0xFF4B2BB8), style = Stroke(1.6f * s))
        val tick = Path().apply { moveTo(X(65.2f), Y(66.2f)); lineTo(X(67.9f), Y(68.9f)); lineTo(X(72.9f), Y(63.7f)) }
        drawPath(tick, Color(0xFF5B2FC4), style = Stroke(2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun LockScreen(st: AppState) {
    val scope = rememberCoroutineScope()
    var restoreBlob by remember { mutableStateOf<JSONObject?>(null) }
    val exists = st.file.exists()
    val mode = when { restoreBlob != null -> "restore"; exists -> "unlock"; else -> "create" }
    val useBio = mode == "unlock" && st.bio && !st.showPasswordForm
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var fails by remember { mutableIntStateOf(0) }
    var waitUntil by remember { mutableLongStateOf(0L) }
    val shake = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }
    val message = err.ifEmpty { st.lockMessage }

    LaunchedEffect(useBio, mode) { if (!useBio) try { focus.requestFocus() } catch (_: Exception) {} }

    fun fail(msg: String) {
        err = msg; busy = false; st.haptic("error")
        scope.launch { for (x in listOf(10f, -10f, 7f, -7f, 3f, 0f)) shake.animateTo(x, androidx.compose.animation.core.tween(45)) }
    }

    fun submit() {
        if (busy) return
        err = ""
        if (System.currentTimeMillis() < waitUntil) { fail("Too many attempts. Wait ${((waitUntil - System.currentTimeMillis()) / 1000) + 1}s."); return }
        if (mode == "create") {
            if (pw.length < 6) { fail("Use at least 6 characters."); return }
            if (pw != pw2) { fail("Passwords do not match."); return }
        }
        busy = true
        scope.launch {
            try {
                when (mode) {
                    "create" -> {
                        val salt = VaultCrypto.newSalt()
                        val key = withContext(Dispatchers.Default) { VaultCrypto.pbkdf2(pw, salt, VaultCrypto.ITER) }
                        st.install(Session(key, salt, VaultCrypto.ITER, Vault()))
                    }
                    else -> {
                        val blob = restoreBlob ?: st.file.read() ?: throw IllegalStateException("No vault")
                        val s = try { withContext(Dispatchers.Default) { VaultCrypto.open(blob, pw) } }
                        catch (e: Exception) {
                            fails++; if (fails >= 5) { waitUntil = System.currentTimeMillis() + 30_000; fails = 0 }
                            fail("Wrong password."); return@launch
                        }
                        fails = 0
                        if (mode == "restore") { st.install(s); st.toast("Restored ${s.vault.cards.size} card(s)") } else st.onUnlocked(s)
                    }
                }
                st.haptic("tap")
                busy = false
            } catch (e: Exception) { fail(e.message ?: "Something went wrong.") }
        }
    }

    val text = when (mode) {
        "unlock" -> if (useBio) "Locked" else "Enter your master password"
        "create" -> "Create a master password. It encrypts everything stored on this phone. If you forget it, the data cannot be recovered, so keep a backup."
        else -> "Enter the master password that was used when this backup was made."
    }
    val btn = when (mode) { "unlock" -> "Unlock"; "create" -> "Create vault"; else -> "Restore backup" }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(60.dp))
        Logo()
        Spacer(Modifier.height(16.dp))
        Text("Card Vault", color = TextC, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(text, color = Muted, fontSize = 14.5.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        Spacer(Modifier.height(22.dp))
        if (useBio) {
            IconButton(
                onClick = { st.bioUnlock() },
                modifier = Modifier.size(84.dp).background(Accent.copy(alpha = .14f), CircleShape).border(1.dp, Accent.copy(alpha = .4f), CircleShape),
            ) { Icon(Icons.Outlined.Fingerprint, "Unlock with fingerprint", tint = Accent, modifier = Modifier.size(46.dp)) }
            Spacer(Modifier.height(10.dp))
            Text("Tap to unlock with fingerprint", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { st.showPasswordForm = true }) { Text("Use master password", color = Accent) }
        } else {
            Column(Modifier.fillMaxWidth().offset { IntOffset(shake.value.roundToInt(), 0) }) {
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it; err = "" }, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("Master password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (mode == "create") ImeAction.Next else ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }), colors = fieldColors(), shape = RoundedCornerShape(14.dp),
                )
                if (mode == "create") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it; err = "" }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Confirm master password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }), colors = fieldColors(), shape = RoundedCornerShape(14.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton(if (busy) (if (mode == "create") "Creating…" else "Unlocking…") else btn, Modifier.fillMaxWidth(), enabled = !busy && pw.isNotEmpty()) { submit() }
            }
            if (mode == "unlock" && st.bio) TextButton(onClick = { st.showPasswordForm = false; st.lockMessage = ""; st.bioUnlock() }) { Text("Use fingerprint", color = Accent) }
        }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = Danger, fontSize = 13.5.sp, textAlign = TextAlign.Center)
        }
        if (mode == "create") TextButton(onClick = {
            st.pickBackup { b ->
                if (b == null) return@pickBackup
                if (!VaultCrypto.isBackup(b)) { st.toast("Not a Card Vault backup"); return@pickBackup }
                restoreBlob = b; pw = ""; err = ""
            }
        }) { Text("Restore from a backup file", color = Accent) }
        if (mode == "restore") TextButton(onClick = { restoreBlob = null; pw = ""; err = "" }) { Text("Cancel", color = Muted) }
        Spacer(Modifier.height(60.dp))
    }
}
