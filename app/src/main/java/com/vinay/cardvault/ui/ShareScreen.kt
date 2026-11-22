package com.vinay.cardvault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.cardvault.AppState
import com.vinay.cardvault.data.NETWORKS
import com.vinay.cardvault.data.exp
import com.vinay.cardvault.data.fmtNum
import com.vinay.cardvault.data.last4
import com.vinay.cardvault.data.name
import com.vinay.cardvault.data.net
import com.vinay.cardvault.data.ordinal

@Composable
fun ShareScreen(st: AppState, id: String) {
    val c = st.cardById(id)
    if (c == null) { Column(Modifier.fillMaxSize()) { TopBar("Share card details", onBack = { st.pop() }) }; return }
    val o = remember { mutableStateMapOf("card" to true, "number" to true, "last4" to false, "holder" to true, "exp" to true, "cvv" to false, "billing" to false) }
    var confirmCvv by remember { mutableStateOf(false) }
    val net = c.net()
    val exp = c.exp()

    val lines = buildList {
        if (o["card"] == true) {
            val n = listOf(c.bank, c.nickname).filter { it.isNotEmpty() }.joinToString(" – ")
            val nw = NETWORKS[net].orEmpty()
            add(n + if (nw.isNotEmpty()) " ($nw ${c.type})" else "")
        }
        if (o["number"] == true && c.number.isNotEmpty()) add("Card number: " + if (o["last4"] == true) "ending ${c.last4()}" else fmtNum(c.number, net))
        if (o["holder"] == true && c.holder.isNotEmpty()) add("Name on card: ${c.holder}")
        if (o["exp"] == true && exp.isNotEmpty()) add("Expiry: $exp")
        if (o["cvv"] == true && c.cvv.isNotEmpty()) add("CVV: ${c.cvv}")
        if (o["billing"] == true && c.type == "credit") {
            if (c.statementDay > 0) add("Statement date: ${ordinal(c.statementDay)} of every month")
            if (c.dueDay > 0) add("Payment due: ${ordinal(c.dueDay)} of every month")
        }
    }.filter { it.isNotBlank() }
    val text = lines.joinToString("\n")
    val secret = (o["number"] == true && o["last4"] != true && c.number.isNotEmpty()) || (o["cvv"] == true && c.cvv.isNotEmpty())

    val toggle: (String) -> Unit = { k -> o[k] = o[k] != true; st.haptic("tick") }

    Column(Modifier.fillMaxSize()) {
        TopBar("Share card details", onBack = { st.pop() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Choose what to include. It's sent as a text message through any app you pick (WhatsApp, SMS, email…).", color = Muted, fontSize = 13.5.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(6.dp))
            ShareOpt(o["card"] == true, { toggle("card") }, "Bank and card name")
            ShareOpt(o["number"] == true, { toggle("number") }, "Card number", enabled = c.number.isNotEmpty())
            if (c.number.isNotEmpty()) ShareOpt(o["last4"] == true, { toggle("last4") }, "Only the last 4 digits", "Enough for someone to identify the card", enabled = o["number"] == true, indent = true)
            ShareOpt(o["holder"] == true, { toggle("holder") }, "Name on card", enabled = c.holder.isNotEmpty())
            ShareOpt(o["exp"] == true, { toggle("exp") }, "Expiry date", enabled = exp.isNotEmpty())
            ShareOpt(o["cvv"] == true, { toggle("cvv") }, "CVV", if (c.cvv.isNotEmpty()) "Not recommended. Anyone with the number, expiry and CVV can make online payments" else null, enabled = c.cvv.isNotEmpty())
            if (c.type == "credit") ShareOpt(o["billing"] == true, { toggle("billing") }, "Statement and due dates", enabled = c.statementDay > 0 || c.dueDay > 0)
            SectionTitle("Preview")
            Text(
                text.ifEmpty { "Nothing selected" }, color = if (text.isEmpty()) Muted else TextC, fontSize = 14.5.sp, lineHeight = 21.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(14.dp),
            )
            if (o["cvv"] == true && c.cvv.isNotEmpty() && o["number"] == true && o["last4"] != true) Hint("⚠ Number + expiry + CVV together is everything needed to use this card online.", Warn)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Copy", Modifier.weight(1f), enabled = text.isNotEmpty()) { st.copy(text, secret) }
                PrimaryButton("Share", Modifier.weight(1f), enabled = text.isNotEmpty()) {
                    if (o["cvv"] == true && c.cvv.isNotEmpty()) confirmCvv = true else st.shareText(c.name(), text)
                }
            }
            Text(
                "Once shared, the details leave Card Vault's encryption. Share only with people you trust, and delete the message after they've used it.",
                color = Muted, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 14.dp),
            )
        }
    }

    if (confirmCvv) AlertDialog(
        onDismissRequest = { confirmCvv = false },
        title = { Text("Share the CVV?") },
        text = { Text("With the card number, expiry and CVV, anyone can make online payments on this card. Only continue if you trust this person completely.") },
        confirmButton = { TextButton(onClick = { confirmCvv = false; st.shareText(c.name(), text) }) { Text("Share anyway", color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmCvv = false }) { Text("Cancel", color = TextC) } },
        containerColor = Panel, titleContentColor = TextC, textContentColor = Muted,
    )
}

@Composable
private fun ShareOpt(checked: Boolean, onToggle: () -> Unit, label: String, hint: String? = null, enabled: Boolean = true, indent: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (indent) 30.dp else 0.dp, top = 4.dp).alpha(if (enabled) 1f else .4f)
            .clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled) { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked && enabled, { onToggle() }, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = Muted))
        Column {
            Text(label, color = TextC, fontSize = 15.sp)
            if (hint != null) Text(hint, color = Muted, fontSize = 12.5.sp)
        }
    }
}
