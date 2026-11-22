package com.vinay.cardvault.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.cardvault.data.Card
import com.vinay.cardvault.data.NETWORKS
import com.vinay.cardvault.data.exp
import com.vinay.cardvault.data.expiryStatus
import com.vinay.cardvault.data.fmtNum
import com.vinay.cardvault.data.maskNum
import com.vinay.cardvault.data.net
import com.vinay.cardvault.data.Expiry

val Mono = FontFamily.Monospace

/* ---------- Card picture ---------- */

@Composable
fun CardTile(
    c: Card,
    revealed: Boolean,
    modifier: Modifier = Modifier,
    showEye: Boolean = true,
    onEye: () -> Unit = {},
    onCopyNumber: () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val net = c.net()
    val s = c.expiryStatus()
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .clip(shape)
            .background(cardBrush(c.color))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.bank.ifEmpty { "Card" }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (s != null) Pill(s.text, if (s == Expiry.EXPIRED) Danger else Warn)
            Spacer(Modifier.width(6.dp))
            Text(c.type.uppercase(), color = Color.White.copy(alpha = .75f), fontSize = 11.sp, letterSpacing = 1.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp, 30.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFF5D77A)))
            Spacer(Modifier.weight(1f))
            if (showEye) IconButton(onClick = onEye, modifier = Modifier.size(36.dp)) {
                Icon(if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (revealed) "Hide details" else "Show details", tint = Color.White.copy(alpha = .85f))
            }
        }
        val numText = if (revealed) fmtNum(c.number, net).ifEmpty { "—" } else maskNum(c.number, net)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (revealed && c.number.isNotEmpty()) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCopyNumber) else Modifier,
        ) {
            Text(numText, color = Color.White, fontFamily = Mono, fontSize = 19.sp, letterSpacing = 1.sp, maxLines = 1)
            if (revealed && c.number.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text("COPY", color = Color.White.copy(alpha = .7f), fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val left = if (revealed) listOfNotNull(c.exp().takeIf { it.isNotEmpty() }?.let { "EXP $it" }, c.cvv.takeIf { it.isNotEmpty() }?.let { "CVV $it" }).joinToString("  ·  ").ifEmpty { c.holder }
            else c.nickname.ifEmpty { c.holder }
            Text(left, color = Color.White.copy(alpha = .85f), fontSize = 13.sp, fontFamily = if (revealed) Mono else FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(NETWORKS[net] ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .16f)).padding(horizontal = 8.dp, vertical = 2.dp))
}

/* ---------- Layout ---------- */

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, onClose: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = TextC) }
        else Spacer(Modifier.width(12.dp))
        Text(title, color = TextC, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
        if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close", tint = TextC) }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
fun PanelBox(modifier: Modifier = Modifier, border: Color = Line, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).border(1.dp, border, RoundedCornerShape(16.dp)), content = content)
}

/** Rows inside a panel with a divider between each: `Rows { row { … }; if (x) row { … } }`. */
class RowsScope { var n = 0 }

@Composable
fun RowsScope.row(content: @Composable () -> Unit) {
    if (n++ > 0) HorizontalDivider(color = Line, thickness = 1.dp)
    content()
}

@Composable
fun Rows(content: @Composable RowsScope.() -> Unit) {
    RowsScope().content()
}

@Composable
fun FieldRow(label: String, value: String, mono: Boolean = false, valueColor: Color = TextC, onValueClick: (() -> Unit)? = null, onCopy: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Muted, fontSize = 12.sp)
            Text(value, color = valueColor, fontSize = 15.sp, fontFamily = if (mono) Mono else FontFamily.Default,
                modifier = if (onValueClick != null) Modifier.clickable(onClick = onValueClick) else Modifier)
        }
        if (onCopy != null) TextButton(onClick = onCopy) { Text("Copy", color = Accent) }
    }
}

@Composable
fun SetItem(title: String, sub: String? = null, titleColor: Color = TextC, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = titleColor, fontSize = 15.sp)
            if (sub != null) Text(sub, color = Muted, fontSize = 12.5.sp)
        }
        trailing()
    }
}

@Composable
fun VSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(checked = checked, onCheckedChange = onChange, enabled = enabled,
        colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White, uncheckedTrackColor = Panel2, uncheckedBorderColor = Line, uncheckedThumbColor = Muted))
}

/** Compact dropdown: shows the current choice; tap to pick another. */
@Composable
fun <T> Picker(value: T, options: List<Pair<T, String>>, modifier: Modifier = Modifier, minHeight: androidx.compose.ui.unit.Dp = 40.dp, fill: Boolean = false, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            (if (fill) Modifier.fillMaxWidth() else Modifier).heightIn(min = minHeight).clip(RoundedCornerShape(12.dp)).background(Panel2).border(1.dp, Line, RoundedCornerShape(12.dp)).clickable { open = true }.padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(options.firstOrNull { it.first == value }?.second ?: "—", color = TextC, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = fill))
            Icon(Icons.Outlined.ExpandMore, null, tint = Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, t) -> DropdownMenuItem(text = { Text(t) }, onClick = { open = false; onPick(v) }) }
        }
    }
}

/** Full-width labelled picker used in forms. */
@Composable
fun <T> FormPicker(label: String, value: T, options: List<Pair<T, String>>, modifier: Modifier = Modifier, onPick: (T) -> Unit) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp, top = 10.dp))
        Picker(value, options, Modifier.fillMaxWidth(), minHeight = 56.dp, fill = true, onPick = onPick)
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent, unfocusedBorderColor = Line, focusedContainerColor = Panel2, unfocusedContainerColor = Panel2,
    focusedTextColor = TextC, unfocusedTextColor = TextC, cursorColor = Accent, focusedLabelColor = Accent, unfocusedLabelColor = Muted,
    focusedPlaceholderColor = Muted, unfocusedPlaceholderColor = Muted,
)

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    visual: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    mono: Boolean = false,
) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp, top = 10.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) }, singleLine = singleLine, minLines = if (singleLine) 1 else 3,
            keyboardOptions = keyboard, visualTransformation = visual, colors = fieldColors(), shape = RoundedCornerShape(12.dp),
            textStyle = TextStyle(fontSize = 16.sp, fontFamily = if (mono) Mono else FontFamily.Default),
        )
    }
}

/** Same as [Field] but keeps the cursor where it is while the text is reformatted (card number, expiry). */
@Composable
fun FieldTv(label: String, value: TextFieldValue, onChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier, placeholder: String = "", keyboard: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), mono: Boolean = true) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp, top = 10.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) }, singleLine = true, keyboardOptions = keyboard, colors = fieldColors(), shape = RoundedCornerShape(12.dp),
            textStyle = TextStyle(fontSize = 16.sp, fontFamily = if (mono) Mono else FontFamily.Default),
        )
    }
}

/* ---------- Buttons ---------- */

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(50.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White, disabledContainerColor = Accent.copy(alpha = .4f))) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, color: Color = TextC, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(50.dp), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (color == TextC) Line else color.copy(alpha = .5f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Panel2, contentColor = color)) {
        Text(text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

@Composable
fun SmallButton(text: String, color: Color = TextC, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(38.dp), shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        border = BorderStroke(1.dp, if (color == TextC) Line else color.copy(alpha = .5f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Panel2, contentColor = color)) {
        Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun ChipButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text, color = if (selected) Color.White else TextC, fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
        maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(50)).background(if (selected) Accent else Panel2)
            .border(1.dp, if (selected) Accent else Line, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
fun IconAction(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = TextC, modifier = Modifier.size(20.dp)) }
}

@Composable
fun Hint(text: String, color: Color = Muted) {
    Text(text, color = color, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
}
