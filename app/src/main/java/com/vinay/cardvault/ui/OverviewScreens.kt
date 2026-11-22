package com.vinay.cardvault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.cardvault.AppState
import com.vinay.cardvault.Route
import com.vinay.cardvault.data.Card
import com.vinay.cardvault.data.inr
import com.vinay.cardvault.data.last4
import com.vinay.cardvault.data.name
import com.vinay.cardvault.data.ordinal
import com.vinay.cardvault.data.sorted
import com.vinay.cardvault.data.totalLimit

private data class Entry(val cards: List<Card>, val limit: Long)

@Composable
private fun Swatch(c: Card) {
    Box(Modifier.size(36.dp, 24.dp).clip(RoundedCornerShape(5.dp)).background(cardBrush(c.color)))
}

/** Tapped from "Credit cards" on Home: every credit card with its billing dates and limit. */
@Composable
fun CreditCardsScreen(st: AppState) {
    val v = st.vault
    val credit = v.sorted(v.cards.filter { it.type == "credit" })
    Column(Modifier.fillMaxSize()) {
        TopBar("Credit cards", onBack = { st.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("${credit.size} credit card${if (credit.size == 1) "" else "s"}. Tap one to open it.", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            if (credit.isEmpty()) Text("No credit cards yet.", color = Muted)
            else PanelBox {
                Rows {
                    credit.forEach { c ->
                        row {
                            Row(Modifier.fillMaxWidth().clickable { st.push(Route.Detail(c.id)) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Swatch(c)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name(), color = TextC, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val bits = listOfNotNull(
                                        c.bank.takeIf { it.isNotEmpty() && c.nickname.isNotEmpty() },
                                        c.last4().takeIf { it.isNotEmpty() }?.let { "··$it" },
                                    ).joinToString(" ")
                                    val dates = if (c.statementDay > 0 || c.dueDay > 0)
                                        listOfNotNull(
                                            if (c.statementDay > 0) "Bill ${ordinal(c.statementDay)}" else null,
                                            if (c.dueDay > 0) "Due ${ordinal(c.dueDay)}" else null,
                                        ).joinToString(" · ")
                                    else "No billing dates"
                                    Text(listOf(bits, dates).filter { it.isNotEmpty() }.joinToString("  ·  "), color = Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(c.limit.toLongOrNull()?.let { inr(it) } ?: "—", color = TextC, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Tapped from "Total credit limit" on Home: how the total is made up, with shared limits counted once. */
@Composable
fun LimitsScreen(st: AppState) {
    val v = st.vault
    val credit = v.sorted(v.cards.filter { it.type == "credit" })
    val total = v.totalLimit()
    // one entry per limit: a shared group counts once
    val seen = mutableSetOf<String>()
    val entries = mutableListOf<Entry>()
    val noLimit = mutableListOf<Card>()
    for (c in credit) {
        val l = c.limit.toLongOrNull() ?: 0L
        if (l <= 0) { noLimit += c; continue }
        val g = c.limitGroup
        if (g == null) entries += Entry(listOf(c), l)
        else if (seen.add(g)) entries += Entry(credit.filter { it.limitGroup == g }, l)
    }
    entries.sortByDescending { it.limit }

    Column(Modifier.fillMaxSize()) {
        TopBar("Total credit limit", onBack = { st.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            PanelBox {
                Column(Modifier.padding(16.dp)) {
                    Text("Across ${credit.size} credit card${if (credit.size == 1) "" else "s"}", color = Muted, fontSize = 13.sp)
                    Text(if (total > 0) inr(total) else "—", color = TextC, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    if (entries.any { it.cards.size > 1 }) Text("Cards that share one limit are counted once.", color = Muted, fontSize = 12.5.sp)
                }
            }
            if (entries.isNotEmpty()) {
                SectionTitle("By card")
                PanelBox {
                    Rows {
                        entries.forEach { e ->
                            row {
                                val first = e.cards.first()
                                Column(Modifier.fillMaxWidth().clickable { st.push(Route.Detail(first.id)) }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Swatch(first)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(e.cards.joinToString(" + ") { it.name() }, color = TextC, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            if (e.cards.size > 1) Text("Shared limit", color = Muted, fontSize = 12.5.sp)
                                            else if (first.last4().isNotEmpty()) Text("··" + first.last4(), color = Muted, fontSize = 12.5.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(inr(e.limit), color = TextC, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                            if (total > 0) Text("${Math.round(e.limit * 100.0 / total)}%", color = Muted, fontSize = 12.sp)
                                        }
                                    }
                                    if (total > 0) {
                                        Spacer(Modifier.height(8.dp))
                                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Panel2)) {
                                            Box(Modifier.fillMaxWidth((e.limit.toFloat() / total).coerceIn(0.02f, 1f)).height(5.dp).clip(RoundedCornerShape(50)).background(Accent))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (noLimit.isNotEmpty()) {
                SectionTitle("No limit added")
                PanelBox {
                    Rows {
                        noLimit.forEach { c ->
                            row {
                                Row(Modifier.fillMaxWidth().clickable { st.push(Route.Detail(c.id)) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Swatch(c)
                                    Spacer(Modifier.width(12.dp))
                                    Text(c.name() + if (c.last4().isNotEmpty()) " ··" + c.last4() else "", color = TextC, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    Text("Add in Edit", color = Muted, fontSize = 12.5.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
