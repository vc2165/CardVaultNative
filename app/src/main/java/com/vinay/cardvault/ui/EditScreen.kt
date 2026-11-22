package com.vinay.cardvault.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.cardvault.AppState
import com.vinay.cardvault.Route
import com.vinay.cardvault.data.BANKS
import com.vinay.cardvault.data.COLORS
import com.vinay.cardvault.data.Card
import com.vinay.cardvault.data.NETWORKS
import com.vinay.cardvault.data.bankLabel
import com.vinay.cardvault.data.cleanupGroups
import com.vinay.cardvault.data.detectNetwork
import com.vinay.cardvault.data.exp
import com.vinay.cardvault.data.fmtNum
import com.vinay.cardvault.data.groupOthers
import com.vinay.cardvault.data.last4
import com.vinay.cardvault.data.luhn
import com.vinay.cardvault.data.name
import com.vinay.cardvault.data.net
import com.vinay.cardvault.data.ordinal
import com.vinay.cardvault.data.uid

@Composable
fun EditScreen(st: AppState, id: String?) {
    val isNew = id == null
    val orig = remember(id) { id?.let { st.cardById(it) } ?: Card(id = "", color = COLORS[st.vault.cards.size % COLORS.size]) }
    var type by remember { mutableStateOf(orig.type) }
    var network by remember { mutableStateOf(orig.network) }
    var bank by remember { mutableStateOf(orig.bank) }
    var nickname by remember { mutableStateOf(orig.nickname) }
    var number by remember { mutableStateOf(TextFieldValue(fmtNum(orig.number, orig.net()))) }
    var holder by remember { mutableStateOf(orig.holder) }
    var expTv by remember { mutableStateOf(TextFieldValue(orig.exp())) }
    var cvv by remember { mutableStateOf(orig.cvv) }
    var limit by remember { mutableStateOf(orig.limit) }
    var shareWith by remember { mutableStateOf(st.vault.groupOthers(orig).firstOrNull()?.id ?: "") }
    var stmtDay by remember { mutableStateOf(orig.statementDay) }
    var dueDay by remember { mutableStateOf(orig.dueDay) }
    var ltf by remember { mutableStateOf(orig.ltf || (isNew && orig.annualFee.isEmpty())) }
    var fee by remember { mutableStateOf(orig.annualFee) }
    var waiver by remember { mutableStateOf(orig.feeWaiver) }
    var rewards by remember { mutableStateOf(orig.rewards) }
    var care by remember { mutableStateOf(orig.care) }
    var notes by remember { mutableStateOf(orig.notes) }
    var color by remember { mutableStateOf(orig.color) }

    val raw = number.text.filter { it.isDigit() }
    val net = network.ifEmpty { detectNetwork(raw) }
    val others = st.vault.cards.filter { it.type == "credit" && it.id != id }
    val next = KeyboardOptions(imeAction = ImeAction.Next)
    val nums = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)

    fun save() {
        var expMonth = ""; var expYear = ""
        val e = expTv.text.trim()
        if (e.isNotEmpty()) {
            val m = Regex("^(\\d{1,2})/?(\\d{2})$").find(e)
            val mm = m?.groupValues?.get(1)?.toIntOrNull()
            if (m == null || mm == null || mm !in 1..12) { st.toast("Expiry should look like 08/29"); st.haptic("error"); return }
            expMonth = mm.toString().padStart(2, '0'); expYear = "20" + m.groupValues[2]
        }
        if (bank.isBlank() && nickname.isBlank() && raw.isEmpty()) { st.toast("Add at least a bank, nickname or number"); st.haptic("error"); return }
        val now = System.currentTimeMillis()
        val newId = orig.id.ifEmpty { uid() }
        var card = orig.copy(
            id = newId, type = type, network = network.ifEmpty { detectNetwork(raw) },
            bank = bank.trim(), nickname = nickname.trim(), number = raw, holder = holder.trim().uppercase(),
            expMonth = expMonth, expYear = expYear, cvv = cvv.trim(),
            limit = limit, statementDay = stmtDay, dueDay = dueDay,
            ltf = ltf, annualFee = if (ltf) "" else fee, feeWaiver = if (ltf) "" else waiver.trim(),
            rewards = rewards.trim(), care = care.trim(), notes = notes.trim(), color = color,
            created = orig.created.takeIf { it > 0 } ?: now, updated = now, limitGroup = null,
        )
        st.update(remind = true) { v ->
            var list = v.cards
            // shared limit
            val partner = if (type == "credit" && shareWith.isNotEmpty()) list.firstOrNull { it.id == shareWith } else null
            if (partner != null) {
                val grp = partner.limitGroup ?: ("g" + uid())
                card = card.copy(limitGroup = grp, limit = card.limit.ifEmpty { partner.limit })
                list = list.map { if (it.id == partner.id) it.copy(limitGroup = grp) else it }
            }
            if (isNew && v.meta.customOrder) card = card.copy(order = (list.mapNotNull { it.order }.maxOrNull() ?: -1) + 1)
            list = if (isNew) list + card else list.map { if (it.id == card.id) card else it }
            card.limitGroup?.let { g -> list = list.map { if (it.limitGroup == g) it.copy(limit = card.limit) else it } }
            v.copy(cards = cleanupGroups(list))
        }
        st.haptic("success")
        if (isNew) st.replaceTop(Route.Detail(card.id)) else st.pop()
        st.toast(if (isNew) "Card added" else "Saved")
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (isNew) "Add card" else "Edit card", onBack = { st.pop() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormPicker("Type", type, listOf("credit" to "Credit", "debit" to "Debit", "prepaid" to "Prepaid / Forex"), Modifier.weight(1f)) { type = it }
                FormPicker("Network", network, listOf("" to "Auto-detect") + NETWORKS.filterKeys { it != "other" }.toList() + ("other" to "Other"), Modifier.weight(1f)) { network = it }
            }
            Field("Bank / issuer", bank, { bank = it }, placeholder = "e.g. HDFC Bank", keyboard = next.copy(capitalization = KeyboardCapitalization.Words))
            val sugg = if (bank.length >= 1) BANKS.filter { it.contains(bank, ignoreCase = true) && !it.equals(bank, ignoreCase = true) }.take(5) else emptyList()
            if (sugg.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sugg.forEach { b -> ChipButton(b, false) { bank = b } }
            }
            Field("Card nickname", nickname, { nickname = it }, placeholder = "e.g. Regalia Gold, Amazon Pay", keyboard = next.copy(capitalization = KeyboardCapitalization.Words))
            FieldTv("Card number", number, { tv ->
                val digits = tv.text.filter { it.isDigit() }.take(19)
                val f = fmtNum(digits, network.ifEmpty { detectNetwork(digits) })
                // keep the cursor after the same number of digits
                val digitsBefore = tv.text.take(tv.selection.end).count { it.isDigit() }
                var pos = 0; var seen = 0
                while (pos < f.length && seen < digitsBefore) { if (f[pos].isDigit()) seen++; pos++ }
                number = TextFieldValue(f, TextRange(pos))
            }, placeholder = "1234 5678 9012 3456", keyboard = nums)
            val bad = raw.length >= 12 && !luhn(raw)
            val det = NETWORKS[detectNetwork(raw)].orEmpty()
            if (bad) Hint("This number fails the checksum. Double-check for a typo.", Warn)
            else if (raw.isNotEmpty() && det.isNotEmpty()) Hint("Detected: $det")
            Field("Name on card", holder, { holder = it.uppercase() }, placeholder = "As printed", keyboard = next.copy(capitalization = KeyboardCapitalization.Characters))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldTv("Expiry (MM/YY)", expTv, { tv ->
                    var d = tv.text.filter { it.isDigit() }.take(4)
                    if (d.length > 2) d = d.take(2) + "/" + d.drop(2)
                    expTv = TextFieldValue(d, TextRange(d.length))
                }, Modifier.weight(1f), placeholder = "08/29", keyboard = nums)
                Field("CVV (optional)", cvv, { cvv = it.filter { ch -> ch.isDigit() }.take(4) }, Modifier.weight(1f), placeholder = "•••", keyboard = nums, mono = true)
            }

            AnimatedVisibility(type == "credit") {
                Column {
                    SectionTitle("Billing")
                    Field("Credit limit (₹)", limit, { limit = it.filter { ch -> ch.isDigit() } }, placeholder = "300000", keyboard = nums)
                    FormPicker("Shares limit with", shareWith, listOf("" to "Not shared, own limit") + others.map { it.id to it.bankLabel() }) {
                        shareWith = it
                        val o = others.firstOrNull { x -> x.id == it }
                        if (o != null && o.limit.isNotEmpty() && limit.isEmpty()) limit = o.limit
                    }
                    Hint("Pick a card that uses the same credit limit (e.g. two cards from one bank). The limit is kept in sync and counted once in your total.")
                    val days = listOf(0 to "—") + (1..31).map { it to ordinal(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormPicker("Statement date", stmtDay, days, Modifier.weight(1f)) { stmtDay = it }
                        FormPicker("Payment due date", dueDay, days, Modifier.weight(1f)) { dueDay = it }
                    }
                    Row(Modifier.padding(top = 12.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).clickable { ltf = !ltf }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(ltf, { ltf = it }, colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = Muted))
                        Text("Lifetime free (no annual fee)", color = TextC, fontSize = 15.sp)
                    }
                    AnimatedVisibility(!ltf) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Field("Annual fee (₹)", fee, { fee = it.filter { ch -> ch.isDigit() } }, Modifier.weight(1f), placeholder = "2500", keyboard = nums)
                            Field("Fee waiver", waiver, { waiver = it }, Modifier.weight(1f), placeholder = "e.g. ₹3L spend", keyboard = next)
                        }
                    }
                }
            }

            SectionTitle("More")
            Field("Rewards / benefits", rewards, { rewards = it }, placeholder = "e.g. 5% on Amazon, lounge access", keyboard = next.copy(capitalization = KeyboardCapitalization.Sentences))
            Field("Customer care number", care, { care = it }, placeholder = "1800 …", keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
            Field("Notes", notes, { notes = it }, placeholder = "Anything else", singleLine = false, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Text("Card colour", color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                COLORS.forEach { col ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(cardBrush(col))
                            .border(if (col == color) 3.dp else 1.dp, if (col == color) TextC else Line, CircleShape)
                            .clickable { color = col; st.haptic("tick") },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(
            Modifier.fillMaxWidth().background(Bg).navigationBarsPadding().imePadding().padding(16.dp, 10.dp, 16.dp, 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton("Cancel", Modifier.weight(1f)) { st.pop() }
            PrimaryButton("Save", Modifier.weight(1f)) { save() }
        }
    }
}
