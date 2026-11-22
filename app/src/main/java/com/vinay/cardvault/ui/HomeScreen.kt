package com.vinay.cardvault.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.cardvault.AppState
import com.vinay.cardvault.Route
import com.vinay.cardvault.data.Card
import com.vinay.cardvault.data.Expiry
import com.vinay.cardvault.data.NETWORKS
import com.vinay.cardvault.data.daysUntil
import com.vinay.cardvault.data.exp
import com.vinay.cardvault.data.expiryStatus
import com.vinay.cardvault.data.fmtDate
import com.vinay.cardvault.data.inr
import com.vinay.cardvault.data.isPaid
import com.vinay.cardvault.data.last4
import com.vinay.cardvault.data.name
import com.vinay.cardvault.data.net
import com.vinay.cardvault.data.nextDay
import com.vinay.cardvault.data.occurrence
import com.vinay.cardvault.data.rel
import com.vinay.cardvault.data.runwayRank
import com.vinay.cardvault.data.sorted
import com.vinay.cardvault.data.today
import com.vinay.cardvault.data.totalLimit

@Composable
fun HomeScreen(st: AppState) {
    val v = st.vault
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val focus = LocalFocusManager.current
    val cards = v.sorted(
        v.cards.filter { filter == "all" || it.type == filter }.filter { c ->
            val t = q.trim().lowercase()
            t.isEmpty() || listOf(c.nickname, c.bank, c.holder, NETWORKS[c.net()] ?: "", c.notes, c.rewards, c.last4()).joinToString(" ").lowercase().contains(t)
        },
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            flingBehavior = rememberCalmFling(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("My Cards", color = TextC, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("${v.cards.size} card${if (v.cards.size == 1) "" else "s"} · encrypted on this phone", color = Muted, fontSize = 13.sp)
                    }
                    IconAction(Icons.Outlined.Settings, "Settings") { st.push(Route.Settings) }
                    IconAction(Icons.Outlined.Lock, "Lock") { st.haptic("tap"); st.lock() }
                }
            }
            item(key = "summary") { Summary(st) }
            item(key = "tools") {
                Column {
                    OutlinedTextField(
                        value = q, onValueChange = { q = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search bank, name, last 4 digits…") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Muted) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        colors = fieldColors(), shape = RoundedCornerShape(14.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("all" to "All", "credit" to "Credit", "debit" to "Debit").forEach { (k, t) ->
                            ChipButton(t, filter == k) { filter = k; st.haptic("tap") }
                            Spacer(Modifier.width(8.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconAction(Icons.Outlined.SwapVert, "Arrange cards") {
                            if (v.cards.size < 2) st.toast("Add at least two cards to arrange") else st.push(Route.Arrange)
                        }
                    }
                }
            }
            if (cards.isEmpty()) item(key = "empty") {
                Text(
                    if (v.cards.isNotEmpty()) "No cards match." else "No cards yet.\nTap + to add your first card.",
                    color = Muted, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            items(cards, key = { it.id }) { c ->
                CardTile(
                    c, revealed = c.id in st.revealed,
                    modifier = Modifier.animateItem(),
                    onEye = { st.toggleReveal(c.id) },
                    onCopyNumber = { st.copy(c.number, true) },
                    onClick = { st.push(Route.Detail(c.id)) },
                )
            }
        }
        FloatingActionButton(
            onClick = { st.push(Route.Edit(null)) },
            containerColor = Accent, contentColor = Color.White, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp).size(60.dp),
        ) { Icon(Icons.Outlined.Add, "Add card", modifier = Modifier.size(28.dp)) }
    }

    if (st.offerBio) androidx.compose.material3.AlertDialog(
        onDismissRequest = { st.offerBio = false },
        title = { Text("Unlock with fingerprint?") },
        text = { Text("Open Card Vault with your fingerprint instead of typing the master password. The key is kept in the phone's secure hardware and is only released after a fingerprint check.") },
        confirmButton = { TextButton(onClick = { st.offerBio = false; st.enableBio() }) { Text("Turn on", color = Accent) } },
        dismissButton = { TextButton(onClick = { st.offerBio = false }) { Text("Not now", color = TextC) } },
        containerColor = Panel, titleContentColor = TextC, textContentColor = Muted,
    )
}

@Composable
private fun Summary(st: AppState) {
    val v = st.vault
    val credit = v.cards.filter { it.type == "credit" }
    if (v.cards.isEmpty()) return
    Column(Modifier.animateContentSize(tween(340, easing = Ease)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Stats
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Credit cards", credit.size.toString(), null, Modifier.weight(1f))
            val lim = v.totalLimit()
            Stat("Total credit limit", if (lim > 0) inr(lim) else "—", if (credit.any { it.limitGroup != null }) "shared limits counted once" else null, Modifier.weight(1f))
        }
        // Runway
        if (credit.isNotEmpty()) {
            val best = v.runwayRank(today()).firstOrNull()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Accent.copy(alpha = .22f), Accent2.copy(alpha = .14f))))
                    .border(1.dp, Accent.copy(alpha = .35f), RoundedCornerShape(16.dp))
                    .clickable { st.push(Route.Runway) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (best != null) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Flight, null, tint = Muted, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                            Text("RUNWAY · TODAY", color = Muted, fontSize = 11.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("${best.days} days", color = TextC, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(best.card.name() + (if (best.card.last4().isNotEmpty()) " ··" + best.card.last4() else ""), color = TextC, fontSize = 14.5.sp)
                        Text("pay by ${fmtDate(best.due)}", color = Muted, fontSize = 12.5.sp)
                    }
                } else Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Flight, null, tint = Muted, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                        Text("RUNWAY", color = Muted, fontSize = 11.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Add statement & due dates to your credit cards to see which card gives the most time to pay", color = TextC, fontSize = 13.5.sp)
                }
            }
        }
        // Upcoming payments: unpaid first (soonest on top), paid ones move to the bottom
        val dues = credit.filter { it.dueDay > 0 }.map { it to nextDay(it.dueDay)!! }
            .sortedWith(compareBy<Pair<Card, java.time.LocalDate>>({ v.isPaid(it.first, it.second) }, { it.second }))
        if (dues.isNotEmpty()) PanelBox {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Upcoming payments", color = TextC, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (!st.remindOn && !st.statement) TextButton(onClick = { st.enableReminders(false) }) {
                    Icon(Icons.Outlined.NotificationsActive, null, tint = Accent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text("Turn on reminders", color = Accent, fontSize = 13.sp)
                }
            }
            dues.take(6).forEach { (c, d) ->
                val n = daysUntil(d); val paid = v.isPaid(c, d)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(c.name(), color = TextC, fontSize = 15.sp)
                            if (c.last4().isNotEmpty()) Text(" ··" + c.last4(), color = Muted, fontSize = 15.sp)
                        }
                        Text(
                            if (paid) "Paid · next due ${occurrence(c.dueDay, 1)?.let { fmtDate(it) } ?: ""}" else "Due ${fmtDate(d)} · ${rel(n)}",
                            color = if (!paid && n <= 3) Warn else Muted, fontSize = 13.sp,
                        )
                    }
                    Text(
                        if (paid) "✓ Paid" else "Mark paid",
                        color = if (paid) Ok else TextC, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (paid) Ok.copy(alpha = .12f) else Panel2)
                            .border(1.dp, if (paid) Ok.copy(alpha = .4f) else Line, RoundedCornerShape(10.dp))
                            .clickable { togglePaid(st, c) }.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        // Expiring
        val expiring = v.cards.filter { it.expiryStatus() != null }
        if (expiring.isNotEmpty()) PanelBox {
            Text("Needs attention", color = TextC, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp))
            expiring.forEach { c ->
                val s = c.expiryStatus()!!
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name(), color = TextC, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Pill("${s.text} · ${c.exp()}", if (s == Expiry.EXPIRED) Danger else Warn)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        // Backup reminder
        val lb = v.meta.lastBackup
        if (lb == 0L || System.currentTimeMillis() - lb > 30L * 86_400_000L) PanelBox(border = Warn.copy(alpha = .35f)) {
            Column(Modifier.padding(14.dp)) {
                Text("Backup reminder", color = Warn, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    (if (lb > 0) "Your last backup was over a month ago." else "You haven't made a backup yet.") +
                        " Without one, uninstalling the app or changing phones loses your cards.",
                    color = TextC, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(onClick = { exportBackup(st) }, contentPadding = PaddingValues(0.dp)) { Text("Back up now", color = Accent) }
            }
        }
    }
}

@Composable
private fun Stat(k: String, value: String, sub: String?, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(k, color = Muted, fontSize = 12.5.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        if (sub != null) Text(sub, color = Muted, fontSize = 11.sp, maxLines = 1)
    }
}

fun togglePaid(st: AppState, c: Card) {
    val d = nextDay(c.dueDay) ?: return
    val paid = st.vault.isPaid(c, d)
    st.update(remind = true) { v ->
        val m = v.meta.paid.toMutableMap()
        if (paid) m.remove(c.id) else m[c.id] = d.toString()
        v.copy(meta = v.meta.copy(paid = m))
    }
    if (paid) st.haptic("tap") else { st.haptic("success"); st.toast("${c.name()} marked as paid") }
}

fun exportBackup(st: AppState) {
    val blob = st.blobForBackup() ?: return
    st.saveBackup("cardvault-backup-${today()}.json", blob.toString()) { ok ->
        if (ok) st.toast("Backup saved. Keep it somewhere safe")
    }
}
