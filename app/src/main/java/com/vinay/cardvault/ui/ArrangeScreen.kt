package com.vinay.cardvault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vinay.cardvault.AppState
import com.vinay.cardvault.data.last4
import com.vinay.cardvault.data.name
import com.vinay.cardvault.data.sorted
import kotlin.math.roundToInt

@Composable
fun ArrangeScreen(st: AppState) {
    val list = remember { st.vault.sorted().map { it.id }.toMutableStateList() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val rowH = 66.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }

    fun save() {
        val order = list.withIndex().associate { (i, id) -> id to i }
        st.update { v -> v.copy(cards = v.cards.map { it.copy(order = order[it.id]) }, meta = v.meta.copy(customOrder = true)) }
    }
    fun move(from: Int, to: Int) {
        if (from == to || to !in list.indices) return
        list.add(to, list.removeAt(from)); st.haptic("tick")
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Arrange cards", onBack = { st.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = dragId == null, flingBehavior = rememberCalmFling()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Drag ⠿ or use the arrows. Your order is saved automatically.", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            PanelBox {
                list.forEachIndexed { i, id ->
                    val c = st.cardById(id) ?: return@forEachIndexed
                    val dragging = dragId == id
                    if (i > 0) HorizontalDivider(color = Line)
                    androidx.compose.runtime.key(id) {
                    Row(
                        Modifier.fillMaxWidth().height(rowH)
                            .zIndex(if (dragging) 1f else 0f)
                            .offset { IntOffset(0, if (dragging) dragDy.roundToInt() else 0) }
                            .then(if (dragging) Modifier.shadow(8.dp, RoundedCornerShape(12.dp)).background(Panel2) else Modifier)
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(36.dp, 24.dp).clip(RoundedCornerShape(5.dp)).background(cardBrush(c.color)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name(), color = TextC, fontSize = 15.sp, maxLines = 1)
                            Text(c.bank + if (c.last4().isNotEmpty()) " ··" + c.last4() else "", color = Muted, fontSize = 12.5.sp, maxLines = 1)
                        }
                        IconButton(onClick = { move(i, i - 1); save() }, enabled = i > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up", tint = if (i > 0) TextC else Line) }
                        IconButton(onClick = { move(i, i + 1); save() }, enabled = i < list.size - 1) { Icon(Icons.Outlined.KeyboardArrowDown, "Move down", tint = if (i < list.size - 1) TextC else Line) }
                        Box(
                            Modifier.size(44.dp).pointerInput(id) {
                                detectDragGestures(
                                    onDragStart = { dragId = id; dragDy = 0f; st.haptic("tap") },
                                    onDragEnd = { dragId = null; dragDy = 0f; save() },
                                    onDragCancel = { dragId = null; dragDy = 0f; save() },
                                ) { change, amount ->
                                    change.consume()
                                    dragDy += amount.y
                                    val cur = list.indexOf(id)
                                    if (dragDy > rowPx / 2 && cur < list.size - 1) { move(cur, cur + 1); dragDy -= rowPx }
                                    else if (dragDy < -rowPx / 2 && cur > 0) { move(cur, cur - 1); dragDy += rowPx }
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.DragIndicator, "Drag to reorder", tint = Muted) }
                    }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Reset to A–Z", Modifier.weight(1f)) {
                    st.update { v -> v.copy(cards = v.cards.map { it.copy(order = null) }, meta = v.meta.copy(customOrder = false)) }
                    list.clear(); list.addAll(st.vault.sorted().map { it.id })
                    st.haptic("tap"); st.toast("Sorted A–Z")
                }
                PrimaryButton("Done", Modifier.weight(1f)) { st.pop() }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
