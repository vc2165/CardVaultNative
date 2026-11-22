package com.vinay.cardvault

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vinay.cardvault.data.Card
import com.vinay.cardvault.data.Prefs
import com.vinay.cardvault.data.Session
import com.vinay.cardvault.data.Vault
import com.vinay.cardvault.data.VaultCrypto
import com.vinay.cardvault.data.VaultFile
import com.vinay.cardvault.data.toJson
import com.vinay.cardvault.ui.CardVaultApp
import com.vinay.cardvault.ui.CardVaultTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Base64

sealed interface Route {
    data class Detail(val id: String) : Route
    data class Edit(val id: String?) : Route
    data class Share(val id: String) : Route
    data object Runway : Route
    data object Arrange : Route
    data object Settings : Route
    data object ChangePassword : Route
    data object CreditCards : Route
    data object Limits : Route
}

/** Everything the screens need. The vault key exists only while unlocked. */
class AppState(private val act: MainActivity) {
    val prefs = Prefs(act)
    val file = VaultFile(act)
    var session by mutableStateOf<Session?>(null)
        private set
    val stack = mutableStateListOf<Route>()
    val snackbar = SnackbarHostState()
    val revealed = mutableStateListOf<String>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    // settings mirrored as state so screens update
    var haptics by mutableStateOf(prefs.haptics)
    var autolock by mutableStateOf(prefs.autolock)
    var bio by mutableStateOf(prefs.bio)
    var remindOn by mutableStateOf(prefs.remindOn)
    var daysBefore by mutableStateOf(prefs.daysBefore)
    var onDay by mutableStateOf(prefs.onDay)
    var hour by mutableStateOf(prefs.hour)
    var statement by mutableStateOf(prefs.statement)

    /** Lock-screen message set from outside (e.g. after fingerprint was reset). */
    var lockMessage by mutableStateOf("")
    var showPasswordForm by mutableStateOf(false)
    /** Ask once whether to turn on fingerprint unlock. */
    var offerBio by mutableStateOf(false)
    private fun maybeOfferBio() {
        if (!bio && !prefs.bioAsked && act.bioAvailable()) { prefs.bioAsked = true; offerBio = true }
    }

    val vault: Vault get() = session?.vault ?: Vault()

    fun push(r: Route) { stack.add(r) }
    /** Set by the screen stack so a pop slides the screen away before removing it. */
    var animatedPop: (() -> Unit)? = null
    fun pop() { val a = animatedPop; if (a != null && stack.isNotEmpty()) a() else popNow() }
    fun popNow() { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1) }
    fun replaceTop(r: Route) { if (stack.isNotEmpty()) stack[stack.size - 1] = r else stack.add(r) }

    fun toast(msg: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    /* ---------- vault ---------- */

    fun onUnlocked(s: Session) {
        session = s
        lockMessage = ""; showPasswordForm = false
        bump()
        Reminders.update(act, s.vault, prefs)
        maybeOfferBio()
    }

    private fun persist(s: Session) {
        file.write(VaultCrypto.encrypt(s.key, s.salt, s.iter, s.vault.toJson()))
    }

    /** Change the vault and save it straight away. */
    fun update(remind: Boolean = false, f: (Vault) -> Vault) {
        val s = session ?: return
        val n = s.with(f(s.vault))
        session = n
        persist(n)
        if (remind) Reminders.update(act, n.vault, prefs)
    }

    /** New vault (or a new password for the current one). */
    fun install(s: Session) {
        val first = session == null
        session = s; persist(s); bump(); Reminders.update(act, s.vault, prefs)
        if (first) maybeOfferBio()
    }

    fun blobForBackup(): JSONObject? {
        update { it.copy(meta = it.meta.copy(lastBackup = System.currentTimeMillis())) }
        return file.read()
    }

    fun lock() {
        session = null
        stack.clear(); revealed.clear()
        handler.removeCallbacks(lockRunnable)
    }

    fun erase() {
        lock()
        file.delete()
        disableBio()
        prefs.bioAsked = false
        prefs.remindOn = false; remindOn = false
        prefs.statement = false; statement = false
        Reminders.clear(act)
    }

    /* ---------- auto-lock ---------- */

    private val lockRunnable = Runnable { if (session != null) { lock(); toast("Locked after inactivity") } }
    fun bump() {
        handler.removeCallbacks(lockRunnable)
        if (session != null) handler.postDelayed(lockRunnable, autolock * 60_000L)
    }

    /* ---------- reveal (auto-hides after 30 s) ---------- */

    fun toggleReveal(id: String) {
        if (revealed.remove(id)) { haptic("tap"); return }
        revealed.add(id); haptic("tap")
        handler.postDelayed({ revealed.remove(id) }, 30_000)
    }

    /* ---------- settings ---------- */

    fun changeHaptics(v: String) { haptics = v; prefs.haptics = v }
    fun changeAutolock(v: Int) { autolock = v; prefs.autolock = v; bump() }
    fun setReminderPrefs(on: Boolean = remindOn, stmt: Boolean = statement, before: Int = daysBefore, day: Boolean = onDay, h: Int = hour) {
        remindOn = on; statement = stmt; daysBefore = before; onDay = day; hour = h
        prefs.remindOn = on; prefs.statement = stmt; prefs.daysBefore = before; prefs.onDay = day; prefs.hour = h
        session?.let { Reminders.update(act, it.vault, prefs) }
    }
    fun testReminder() = Reminders.test(act)

    /** Asks for notification permission when needed, then turns a reminder kind on. */
    fun enableReminders(statementKind: Boolean, done: (Boolean) -> Unit = {}) {
        act.notificationPermission { ok ->
            if (!ok) { toast("Allow notifications for Card Vault in phone Settings → Apps"); done(false); return@notificationPermission }
            if (statementKind) setReminderPrefs(stmt = true) else setReminderPrefs(on = true)
            haptic("success")
            toast(if (statementKind) "Statement-day alerts are on" else "Payment reminders are on")
            done(true)
        }
    }

    /* ---------- fingerprint ---------- */

    fun bioAvailable(): Boolean = act.bioAvailable()

    fun enableBio(done: (Boolean) -> Unit = {}) {
        val s = session ?: return
        act.bioEncrypt(s.key) { ok ->
            if (ok) { bio = true; prefs.bio = true; haptic("success"); toast("Fingerprint unlock is on") }
            done(ok)
        }
    }

    fun disableBio() {
        bio = false; prefs.bio = false; prefs.bioCt = ""; prefs.bioIv = ""
        Bio.delete()
    }

    fun bioUnlock() = act.bioUnlock()

    /* ---------- phone features ---------- */

    fun haptic(kind: String = "tap") = act.haptic(haptics, kind)
    fun copy(text: String, secret: Boolean) = act.copy(text, secret)
    fun shareText(title: String, text: String) = act.shareText(title, text)
    fun dial(number: String) = act.dial(number)
    fun pickBackup(cb: (JSONObject?) -> Unit) = act.pickBackup(cb)
    fun saveBackup(name: String, text: String, cb: (Boolean) -> Unit) = act.saveBackup(name, text, cb)
    fun cardById(id: String): Card? = vault.cards.firstOrNull { it.id == id }
}

class MainActivity : FragmentActivity() {
    lateinit var state: AppState
    private var hiddenAt = 0L
    private var picking = false
    private val handler = Handler(Looper.getMainLooper())

    private var permCb: ((Boolean) -> Unit)? = null
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        picking = false; permCb?.invoke(ok); permCb = null
    }
    private var openCb: ((JSONObject?) -> Unit)? = null
    private val openLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val cb = openCb; openCb = null
        if (uri == null) { cb?.invoke(null); return@registerForActivityResult }
        val o = try {
            contentResolver.openInputStream(uri)?.use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        } catch (_: Exception) { null }
        if (o == null) state.toast("That file is not a valid backup")
        cb?.invoke(o)
    }
    private var saveText = ""
    private var saveCb: ((Boolean) -> Unit)? = null
    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val cb = saveCb; saveCb = null
        if (uri == null) { cb?.invoke(false); return@registerForActivityResult }
        val ok = try {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(saveText.toByteArray(Charsets.UTF_8)) }; true
        } catch (e: Exception) { state.toast("Could not save backup: ${e.message}"); false }
        saveText = ""
        cb?.invoke(ok)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // No screenshots, and a blank preview in recent apps
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        state = AppState(this)
        setContent { CardVaultTheme { CardVaultApp(state) } }
        if (state.file.exists() && state.bio) handler.postDelayed({ bioUnlock() }, 350)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) state.bump()
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        hiddenAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (hiddenAt == 0L || picking) return
        val away = SystemClock.elapsedRealtime() - hiddenAt > 30_000
        if (!away) return
        if (state.session != null) state.lock()
        if (state.file.exists() && state.bio && !state.showPasswordForm) handler.postDelayed({ bioUnlock() }, 300)
    }

    /* ---------- haptics ---------- */

    fun haptic(level: String, kind: String) {
        if (level == "off") return
        if (level == "medium") {
            val vib = getSystemService(Vibrator::class.java) ?: return
            try {
                if (kind == "error") vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 18, 50, 18), -1))
                else vib.vibrate(VibrationEffect.createOneShot(if (kind == "success") 24 else 16, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {}
            return
        }
        val c = when {
            level == "subtle" || kind == "tick" -> HapticFeedbackConstants.CLOCK_TICK
            kind == "success" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
            kind == "error" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            else -> HapticFeedbackConstants.CONTEXT_CLICK
        }
        window.decorView.performHapticFeedback(c)
    }

    /* ---------- clipboard, sharing, dialer ---------- */

    fun copy(text: String, secret: Boolean) {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("Card Vault", text)
        if (secret && Build.VERSION.SDK_INT >= 24) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
            }
        }
        cm.setPrimaryClip(clip)
        haptic(state.haptics, "tap")
        state.toast(if (secret) "Copied · clipboard clears in 45s" else "Copied")
        if (secret) handler.postDelayed({
            try {
                val cur = cm.primaryClip?.getItemAt(0)?.text?.toString()
                if (cur == text) {
                    if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", " "))
                }
            } catch (_: Exception) {}
        }, 45_000)
    }

    fun shareText(title: String, text: String) {
        picking = true
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, title); putExtra(Intent.EXTRA_TEXT, text) }
        startActivity(Intent.createChooser(send, "Share card details"))
        handler.postDelayed({ picking = false }, 1500)
    }

    fun dial(number: String) {
        try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' }))) } catch (_: Exception) {}
    }

    /* ---------- files ---------- */

    fun pickBackup(cb: (JSONObject?) -> Unit) {
        picking = true; openCb = { o -> handler.postDelayed({ picking = false }, 800); cb(o) }
        openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
    }

    fun saveBackup(name: String, text: String, cb: (Boolean) -> Unit) {
        picking = true; saveText = text; saveCb = { ok -> handler.postDelayed({ picking = false }, 800); cb(ok) }
        saveLauncher.launch(name)
    }

    fun notificationPermission(cb: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) { cb(true); return }
        picking = true; permCb = cb
        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /* ---------- fingerprint ---------- */

    fun bioAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun prompt(title: String, subtitle: String, negative: String, cipher: javax.crypto.Cipher, onOk: (javax.crypto.Cipher) -> Unit, onFail: (Int, String) -> Unit) {
        picking = true
        val p = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                handler.postDelayed({ picking = false }, 800)
                val c = result.cryptoObject?.cipher
                if (c != null) onOk(c) else onFail(-1, "Fingerprint check failed")
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                handler.postDelayed({ picking = false }, 800)
                onFail(errorCode, errString.toString())
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title).setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(negative)
            .build()
        try { p.authenticate(info, BiometricPrompt.CryptoObject(cipher)) } catch (e: Exception) { picking = false; onFail(-2, e.message ?: "Fingerprint isn't available") }
    }

    fun bioEncrypt(key: ByteArray, done: (Boolean) -> Unit) {
        if (!bioAvailable()) { state.toast("Set up a fingerprint in phone Settings → Security first"); done(false); return }
        val cipher = try { Bio.encryptCipher() } catch (e: Exception) { state.toast("Fingerprint not turned on: ${e.message}"); done(false); return }
        prompt("Confirm your fingerprint", "To unlock Card Vault with it", "Cancel", cipher, { c ->
            try {
                val ct = c.doFinal(key)
                state.prefs.bioCt = Base64.getEncoder().encodeToString(ct)
                state.prefs.bioIv = Base64.getEncoder().encodeToString(c.iv)
                done(true)
            } catch (e: Exception) { state.toast("Fingerprint not turned on: ${e.message}"); done(false) }
        }, { code, msg ->
            if (code != BiometricPrompt.ERROR_NEGATIVE_BUTTON && code != BiometricPrompt.ERROR_USER_CANCELED) state.toast("Fingerprint not turned on: $msg")
            done(false)
        })
    }

    private var bioBusy = false
    fun bioUnlock() {
        if (bioBusy || state.session != null || !state.bio) return
        val blob = state.file.read() ?: return
        val ct = state.prefs.bioCt; val iv = state.prefs.bioIv
        val cipher = if (ct.isEmpty() || iv.isEmpty()) null else Bio.decryptCipher(Base64.getDecoder().decode(iv))
        if (cipher == null) {
            state.disableBio()
            state.showPasswordForm = true
            state.lockMessage = "Fingerprint unlock was reset (a fingerprint may have been added to the phone). Use your master password, then turn it on again in Settings."
            return
        }
        bioBusy = true
        prompt("Unlock Card Vault", "Touch the fingerprint sensor", "Use password", cipher, { c ->
            bioBusy = false
            try {
                val key = c.doFinal(Base64.getDecoder().decode(ct))
                val json = VaultCrypto.decryptWithKey(blob, key)
                haptic(state.haptics, "tap")
                state.onUnlocked(Session(key, VaultCrypto.saltOf(blob), VaultCrypto.iterOf(blob), com.vinay.cardvault.data.parseVault(json)))
            } catch (e: Exception) {
                state.disableBio()
                state.showPasswordForm = true
                state.lockMessage = "Fingerprint key is out of date. Unlock with your password, then turn fingerprint on again in Settings."
            }
        }, { code, msg ->
            bioBusy = false
            state.showPasswordForm = true
            state.lockMessage = when (code) {
                BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> ""
                BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Too many fingerprint attempts. Use your master password."
                else -> msg
            }
        })
    }
}
