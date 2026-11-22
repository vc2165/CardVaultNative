package com.vinay.cardvault.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.cardvault.AppState
import com.vinay.cardvault.Route
import com.vinay.cardvault.data.Session
import com.vinay.cardvault.data.VaultCrypto
import com.vinay.cardvault.data.fmtDateYear
import com.vinay.cardvault.data.merge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Composable
fun SettingsScreen(st: AppState) {
    val scope = rememberCoroutineScope()
    var importBlob by remember { mutableStateOf<JSONObject?>(null) }
    var importPw by remember { mutableStateOf("") }
    var importBusy by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }
    var wipeText by remember { mutableStateOf("") }
    val lb = st.vault.meta.lastBackup

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack = { st.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            SectionTitle("Payment reminders")
            PanelBox {
                Rows {
                    row {
                        SetItem("Due date reminders", "Notification before each credit card's payment due date") {
                            VSwitch(st.remindOn) { on -> if (on) st.enableReminders(false) else st.setReminderPrefs(on = false) }
                        }
                    }
                    if (st.remindOn) {
                        row {
                            SetItem("Remind me") {
                                Picker(st.daysBefore, listOf(0 to "Only on due date", 1 to "1 day before", 2 to "2 days before", 3 to "3 days before", 5 to "5 days before", 7 to "7 days before")) { st.setReminderPrefs(before = it) }
                            }
                        }
                        row {
                            SetItem("Also on the due date") {
                                VSwitch(st.onDay || st.daysBefore == 0, enabled = st.daysBefore != 0) { st.setReminderPrefs(day = it) }
                            }
                        }
                    }
                    row {
                        SetItem("Statement-day alert", "Notification on the day each card's statement is generated") {
                            VSwitch(st.statement) { on -> if (on) st.enableReminders(true) else st.setReminderPrefs(stmt = false) }
                        }
                    }
                    if (st.remindOn || st.statement) {
                        row {
                            SetItem("Notification time") {
                                Picker(st.hour, listOf(7, 8, 9, 10, 12, 15, 18, 20, 21).map { h -> h to "${if (h % 12 == 0) 12 else h % 12}:00 ${if (h < 12) "AM" else "PM"}" }) {
                                    st.setReminderPrefs(h = it); st.toast("Saved")
                                }
                            }
                        }
                        row {
                            SetItem("Test notification", "Arrives in about 10 seconds") {
                                SmallButton("Send") { st.testReminder(); st.toast("Test notification in ~10 seconds") }
                            }
                        }
                    }
                }
            }

            SectionTitle("Feel")
            PanelBox {
                SetItem("Vibration", "Feedback on taps, copy and unlock") {
                    Picker(st.haptics, listOf("off" to "Off", "subtle" to "Subtle", "light" to "Light", "medium" to "Medium")) {
                        st.changeHaptics(it); st.haptic("success")
                        st.toast(if (it == "off") "Vibration off" else "Vibration: ${it.replaceFirstChar { ch -> ch.uppercase() }}")
                    }
                }
            }

            SectionTitle("Security")
            PanelBox {
                Rows {
                    row {
                        SetItem("Unlock with fingerprint", if (st.bioAvailable()) null else "Set up a fingerprint in phone Settings → Security first") {
                            VSwitch(st.bio) { on ->
                                if (!on) { st.disableBio(); st.toast("Fingerprint unlock is off") }
                                else if (!st.bioAvailable()) st.toast("Set up a fingerprint in phone Settings → Security first")
                                else st.enableBio()
                            }
                        }
                    }
                    row {
                        SetItem("Auto-lock after", "Also locks 30s after you leave the app") {
                            Picker(st.autolock, listOf(1, 3, 5, 10).map { it to "$it min" }) { st.changeAutolock(it); st.toast("Saved") }
                        }
                    }
                    row {
                        SetItem("Change master password") { SmallButton("Change") { st.push(Route.ChangePassword) } }
                    }
                }
            }

            SectionTitle("Backup")
            PanelBox {
                Rows {
                    row {
                        val last = if (lb > 0) "Last: " + fmtDateYear(Instant.ofEpochMilli(lb).atZone(ZoneId.systemDefault()).toLocalDate()) else "Never backed up"
                        SetItem("Export encrypted backup", "$last · locked with your master password") { SmallButton("Export") { exportBackup(st) } }
                    }
                    row {
                        SetItem("Import backup", "Adds cards from a Card Vault backup (old app or this one)") {
                            SmallButton("Import") {
                                st.pickBackup { b ->
                                    if (b == null) return@pickBackup
                                    if (!VaultCrypto.isBackup(b)) { st.toast("Not a Card Vault backup"); return@pickBackup }
                                    importPw = ""; importBlob = b
                                }
                            }
                        }
                    }
                }
            }

            SectionTitle("Danger zone")
            PanelBox {
                SetItem("Erase everything", "Deletes all cards from this phone", titleColor = Danger) {
                    SmallButton("Erase", color = Danger) { wipeText = ""; wipe = true }
                }
            }
            Text(
                "Card Vault (native). Cards are encrypted with AES-256 (key from your master password via PBKDF2, " +
                    NumberFormat.getNumberInstance(Locale("en", "IN")).format(VaultCrypto.ITER) +
                    " rounds) and stored only on this phone. Fingerprint unlock uses the phone's secure hardware. The app makes no network connections.",
                color = Muted, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 18.dp),
            )
        }
    }

    val blob = importBlob
    if (blob != null) AlertDialog(
        onDismissRequest = { if (!importBusy) importBlob = null },
        title = { Text("Backup password") },
        text = {
            Column {
                Text("Enter the master password that was used when this backup was made.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(importPw, { importPw = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !importBusy && importPw.isNotEmpty(), onClick = {
                importBusy = true
                scope.launch {
                    val s = try { withContext(Dispatchers.Default) { VaultCrypto.open(blob, importPw) } } catch (_: Exception) { null }
                    importBusy = false
                    if (s == null) { st.haptic("error"); st.toast("Wrong password for that backup"); return@launch }
                    var added = 0; var updated = 0
                    st.update(remind = true) { v -> val (nv, a, u) = v.merge(s.vault.cards); added = a; updated = u; nv }
                    importBlob = null; st.haptic("success"); st.toast("Imported: $added new, $updated updated")
                }
            }) { Text(if (importBusy) "Opening…" else "Import", color = Accent) }
        },
        dismissButton = { TextButton(onClick = { importBlob = null }, enabled = !importBusy) { Text("Cancel", color = TextC) } },
        containerColor = Panel, titleContentColor = TextC, textContentColor = Muted,
    )

    if (wipe) AlertDialog(
        onDismissRequest = { wipe = false },
        title = { Text("Erase everything?") },
        text = {
            Column {
                Text("This permanently deletes all cards on this phone. Type DELETE to confirm.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(wipeText, { wipeText = it }, singleLine = true, placeholder = { Text("DELETE") }, colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (wipeText.trim() != "DELETE") { st.toast("Not erased. You must type DELETE"); return@TextButton }
                wipe = false; st.erase(); st.toast("Vault erased")
            }) { Text("Erase", color = Danger) }
        },
        dismissButton = { TextButton(onClick = { wipe = false }) { Text("Cancel", color = TextC) } },
        containerColor = Panel, titleContentColor = TextC, textContentColor = Muted,
    )
}

@Composable
fun ChangePasswordScreen(st: AppState) {
    val scope = rememberCoroutineScope()
    var p0 by remember { mutableStateOf("") }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val pwKb = KeyboardOptions(keyboardType = KeyboardType.Password)

    Column(Modifier.fillMaxSize()) {
        TopBar("Change password", onBack = { st.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Field("Current password", p0, { p0 = it; err = "" }, keyboard = pwKb, visual = PasswordVisualTransformation())
            Field("New password", p1, { p1 = it; err = "" }, keyboard = pwKb, visual = PasswordVisualTransformation())
            Field("Confirm new password", p2, { p2 = it; err = "" }, keyboard = pwKb, visual = PasswordVisualTransformation())
            if (err.isNotEmpty()) Hint(err, Danger)
            Spacer(Modifier.height(18.dp))
            PrimaryButton(if (busy) "Changing…" else "Change password", Modifier.fillMaxWidth(), enabled = !busy && p0.isNotEmpty() && p1.isNotEmpty()) {
                if (p1.length < 6) { err = "Use at least 6 characters."; return@PrimaryButton }
                if (p1 != p2) { err = "New passwords do not match."; return@PrimaryButton }
                val cur = st.session ?: return@PrimaryButton
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { VaultCrypto.pbkdf2(p0, cur.salt, cur.iter).contentEquals(cur.key) }
                    if (!ok) { busy = false; st.haptic("error"); err = "Current password is wrong."; return@launch }
                    val salt = VaultCrypto.newSalt()
                    val key = withContext(Dispatchers.Default) { VaultCrypto.pbkdf2(p1, salt, VaultCrypto.ITER) }
                    st.install(Session(key, salt, VaultCrypto.ITER, st.vault))
                    busy = false; st.haptic("success"); st.pop(); st.toast("Master password changed")
                    if (st.bio) {
                        st.disableBio()
                        st.enableBio { ok2 -> if (!ok2) st.toast("Password changed. Turn fingerprint on again in Settings.") }
                    }
                }
            }
            Hint("Old backups will still need the old password.")
        }
    }
}
