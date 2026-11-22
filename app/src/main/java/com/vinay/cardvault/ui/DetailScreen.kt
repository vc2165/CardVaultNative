package com.vinay.cardvault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.cardvault.AppState
import com.vinay.cardvault.Route
import com.vinay.cardvault.data.cleanupGroups
import com.vinay.cardvault.data.daysUntil
import com.vinay.cardvault.data.exp
import com.vinay.cardvault.data.fmtDate
import com.vinay.cardvault.data.fmtDateYear
import com.vinay.cardvault.data.fmtNum
import com.vinay.cardvault.data.groupOthers
import com.vinay.cardvault.data.inr
import com.vinay.cardvault.data.isPaid
import com.vinay.cardvault.data.maskNum
import com.vinay.cardvault.data.name
import com.vinay.cardvault.data.net
import com.vinay.cardvault.data.nextDay
import com.vinay.cardvault.data.ordinal
import com.vinay.cardvault.data.rel
import java.time.Instant
import java.time.ZoneId

@Composable
fun DetailScreen(st: AppState, id: String) {
    val c = st.cardById(id)
    var confirmDelete by remember { mutableStateOf(false) }
    if (c == null) { Column(Modifier.fillMaxSize()) { TopBar("Card", onBack = { st.pop() }) }; return }
    val r = id in st.revealed
    val net = c.net()
    val num = if (r) fmtNum(c.number, net) else maskNum(c.number, net)
    val v = st.vault

    Column(Modifier.fillMaxSize()) {
        TopBar(c.name(), onBack = { st.pop() }) {
            IconButton(onClick = { st.toggleReveal(id) }) {
                Icon(if (r) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (r) "Hide numbers" else "Show numbers", tint = TextC)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            CardTile(c, revealed = r, showEye = false, onCopyNumber = { st.copy(c.number, true) })
            Spacer(Modifier.height(16.dp))
            PanelBox {
                Rows {
                    row { FieldRow("Card number", num, mono = true, onCopy = if (c.number.isNotEmpty()) ({ st.copy(c.number, true) }) else null) }
                    if (c.holder.isNotEmpty()) row { FieldRow("Name on card", c.holder, onCopy = { st.copy(c.holder, false) }) }
                    if (c.exp().isNotEmpty()) row { FieldRow("Expiry", c.exp(), mono = true, onCopy = { st.copy(c.exp(), false) }) }
                    if (c.cvv.isNotEmpty()) row { FieldRow("CVV", if (r) c.cvv else "•".repeat(c.cvv.length), mono = true, onCopy = { st.copy(c.cvv, true) }) }
                }
            }
            val fee = if (c.ltf) "Lifetime free" else c.annualFee.toLongOrNull()?.let { inr(it) } ?: ""
            if (c.type == "credit" && (c.limit.isNotEmpty() || c.statementDay > 0 || c.dueDay > 0 || fee.isNotEmpty())) {
                SectionTitle("Billing")
                val others = v.groupOthers(c)
                val nd = nextDay(c.dueDay); val ns = nextDay(c.statementDay)
                PanelBox {
                    Rows {
                        val l = c.limit.toLongOrNull()
                        if (l != null) row { FieldRow("Credit limit", inr(l) + if (others.isNotEmpty()) " · shared with ${others.joinToString { it.name() }}" else "") }
                        if (ns != null) row { FieldRow("Statement date", "${ordinal(c.statementDay)} of every month · next ${fmtDate(ns)}") }
                        if (nd != null) row { FieldRow("Payment due date", "${ordinal(c.dueDay)} of every month · next ${fmtDate(nd)} (${rel(daysUntil(nd))})" + if (v.isPaid(c, nd)) " · paid ✓" else "") }
                        if (fee.isNotEmpty()) row { FieldRow("Annual fee", fee) }
                        if (!c.ltf && c.feeWaiver.isNotEmpty()) row { FieldRow("Fee waiver", c.feeWaiver) }
                    }
                }
            }
            if (c.rewards.isNotEmpty() || c.care.isNotEmpty() || c.notes.isNotEmpty()) {
                SectionTitle("More")
                PanelBox {
                    Rows {
                        if (c.rewards.isNotEmpty()) row { FieldRow("Rewards / benefits", c.rewards) }
                        if (c.care.isNotEmpty()) row { FieldRow("Customer care", c.care, valueColor = Accent, onValueClick = { st.dial(c.care) }) }
                        if (c.notes.isNotEmpty()) row { FieldRow("Notes", c.notes) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Share", Modifier.weight(1f)) { st.push(Route.Share(id)) }
                SecondaryButton("Edit", Modifier.weight(1f)) { st.push(Route.Edit(id)) }
                SecondaryButton("Delete", Modifier.weight(1f), color = Danger) { confirmDelete = true }
            }
            val upd = c.updated.takeIf { it > 0 } ?: c.created.takeIf { it > 0 } ?: System.currentTimeMillis()
            Text(
                "Updated " + fmtDateYear(Instant.ofEpochMilli(upd).atZone(ZoneId.systemDefault()).toLocalDate()),
                color = Muted, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this card?") },
        text = { Text("\"${c.name()}\" will be removed from this phone. This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                st.update(remind = true) { vv -> vv.copy(cards = cleanupGroups(vv.cards.filter { it.id != id }), meta = vv.meta.copy(paid = vv.meta.paid - id)) }
                st.haptic("error"); st.pop(); st.toast("Card deleted")
            }) { Text("Delete", color = Danger) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TextC) } },
        containerColor = Panel, titleContentColor = TextC, textContentColor = Muted,
    )
}
