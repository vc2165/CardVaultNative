@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vinay.cardvault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.cardvault.AppState
import com.vinay.cardvault.data.fmtDate
import com.vinay.cardvault.data.fmtDateYear
import com.vinay.cardvault.data.last4
import com.vinay.cardvault.data.name
import com.vinay.cardvault.data.runwayRank
import com.vinay.cardvault.data.today
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun RunwayScreen(st: AppState) {
    var p by remember { mutableStateOf(today()) }
    var picking by remember { mutableStateOf(false) }
    val v = st.vault
    val ranked = v.runwayRank(p)
    val missing = v.cards.filter { it.type == "credit" && (it.statementDay <= 0 || it.dueDay <= 0) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Runway", onBack = { st.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Pick the day you'll spend. Cards are ranked by how long you get before payment is due.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel2).clickable { picking = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = Accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Spending on", color = Muted, fontSize = 12.sp)
                    Text(if (p == today()) "Today, ${fmtDate(p)}" else fmtDateYear(p), color = TextC, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Text("Change", color = Accent, fontSize = 14.sp)
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Today" to 0L, "Tomorrow" to 1L, "+7 days" to 7L, "+15 days" to 15L).forEach { (t, n) ->
                    val d = today().plusDays(n)
                    ChipButton(t, p == d) { p = d; st.haptic("tick") }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (ranked.isEmpty() && missing.isEmpty()) Text("Add a credit card first.", color = Muted)
            else PanelBox {
                Rows {
                    ranked.forEachIndexed { i, r ->
                        row {
                            Row(Modifier.fillMaxWidth().background(if (i == 0) Accent.copy(alpha = .10f) else Color.Transparent).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(if (i == 0) Accent else Panel2), contentAlignment = Alignment.Center) {
                                    Text("${i + 1}", color = if (i == 0) Color.White else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row {
                                        Text(r.card.name(), color = TextC, fontSize = 15.sp)
                                        if (r.card.last4().isNotEmpty()) Text(" ··" + r.card.last4(), color = Muted, fontSize = 15.sp)
                                    }
                                    Text(
                                        (if (r.edge) "⚠ Statement day, may land on this bill" else "Bill ${fmtDate(r.stmt)}") + " → pay by ${fmtDate(r.due)}",
                                        color = if (r.edge) Warn else Muted, fontSize = 12.5.sp,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${r.days}", color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("days", color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    missing.forEach { c ->
                        row {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Text("–", color = Muted) }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(c.name() + if (c.last4().isNotEmpty()) " ··" + c.last4() else "", color = TextC, fontSize = 15.sp)
                                    Text("Add statement & due dates to rank", color = Muted, fontSize = 12.5.sp)
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "Based on the statement and due dates saved on each card. Purchases on a statement day can go on either bill depending on the bank, so they're counted on the earlier one.",
                color = Muted, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 14.dp),
            )
        }
    }

    if (picking) {
        val init = p.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val ps = rememberDatePickerState(initialSelectedDateMillis = init)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    ps.selectedDateMillis?.let { p = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false; st.haptic("tick")
                }) { Text("OK", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel", color = TextC) } },
        ) { DatePicker(ps) }
    }
}

@Suppress("unused")
private fun LocalDate.isTodayish() = this == today()
