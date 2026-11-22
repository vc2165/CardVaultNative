package com.vinay.cardvault.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale

/** One card. Same fields (and JSON shape) as the original Card Vault, so backups move both ways. */
data class Card(
    val id: String,
    val type: String = "credit",
    val network: String = "",
    val bank: String = "",
    val nickname: String = "",
    val number: String = "",
    val holder: String = "",
    val expMonth: String = "",
    val expYear: String = "",
    val cvv: String = "",
    val limit: String = "",
    val statementDay: Int = 0,
    val dueDay: Int = 0,
    val ltf: Boolean = false,
    val annualFee: String = "",
    val feeWaiver: String = "",
    val rewards: String = "",
    val care: String = "",
    val notes: String = "",
    val color: String = "midnight",
    val created: Long = 0,
    val updated: Long = 0,
    val limitGroup: String? = null,
    val order: Int? = null,
)

data class Meta(
    val created: Long = System.currentTimeMillis(),
    val paid: Map<String, String> = emptyMap(),
    val customOrder: Boolean = false,
    val lastBackup: Long = 0,
)

data class Vault(val cards: List<Card> = emptyList(), val meta: Meta = Meta())

/* ---------- JSON ---------- */

private fun JSONObject.s(k: String): String = if (has(k) && !isNull(k)) get(k).toString() else ""
private fun JSONObject.i(k: String): Int = s(k).toDoubleOrNull()?.toInt() ?: 0
private fun JSONObject.l(k: String): Long = s(k).toDoubleOrNull()?.toLong() ?: 0L

fun parseCard(o: JSONObject): Card = Card(
    id = o.s("id").ifEmpty { uid() },
    type = o.s("type").ifEmpty { "credit" },
    network = o.s("network"),
    bank = o.s("bank"),
    nickname = o.s("nickname"),
    number = o.s("number").filter { it.isDigit() },
    holder = o.s("holder"),
    expMonth = o.s("expMonth"),
    expYear = o.s("expYear"),
    cvv = o.s("cvv"),
    limit = o.s("limit").filter { it.isDigit() },
    statementDay = o.i("statementDay"),
    dueDay = o.i("dueDay"),
    ltf = o.optBoolean("ltf", false),
    annualFee = o.s("annualFee"),
    feeWaiver = o.s("feeWaiver"),
    rewards = o.s("rewards"),
    care = o.s("care"),
    notes = o.s("notes"),
    color = o.s("color").ifEmpty { "midnight" },
    created = o.l("created"),
    updated = o.l("updated"),
    limitGroup = o.s("limitGroup").ifEmpty { null },
    order = if (o.has("order") && !o.isNull("order")) o.i("order") else null,
)

fun Card.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("type", type); put("network", network); put("bank", bank); put("nickname", nickname)
    put("number", number); put("holder", holder); put("expMonth", expMonth); put("expYear", expYear); put("cvv", cvv)
    put("limit", limit)
    put("statementDay", if (statementDay > 0) statementDay.toString() else "")
    put("dueDay", if (dueDay > 0) dueDay.toString() else "")
    put("ltf", ltf); put("annualFee", annualFee); put("feeWaiver", feeWaiver)
    put("rewards", rewards); put("care", care); put("notes", notes); put("color", color)
    put("created", created); put("updated", updated)
    limitGroup?.let { put("limitGroup", it) }
    order?.let { put("order", it) }
}

fun parseVault(json: String): Vault {
    val o = JSONObject(json)
    val arr = o.optJSONArray("cards") ?: JSONArray()
    val cards = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::parseCard) }
    val m = o.optJSONObject("meta") ?: JSONObject()
    val paidObj = m.optJSONObject("paid")
    val paid = mutableMapOf<String, String>()
    paidObj?.keys()?.forEach { k -> paid[k] = paidObj.optString(k) }
    return Vault(
        cards,
        Meta(
            created = m.l("created").takeIf { it > 0 } ?: System.currentTimeMillis(),
            paid = paid,
            customOrder = m.optBoolean("customOrder", false),
            lastBackup = m.l("lastBackup"),
        ),
    )
}

fun Vault.toJson(): String = JSONObject().apply {
    put("cards", JSONArray().apply { cards.forEach { put(it.toJson()) } })
    put("meta", JSONObject().apply {
        put("created", meta.created)
        put("paid", JSONObject().apply { meta.paid.forEach { (k, v) -> put(k, v) } })
        put("customOrder", meta.customOrder)
        if (meta.lastBackup > 0) put("lastBackup", meta.lastBackup)
    })
}.toString()

/* ---------- Card helpers ---------- */

val COLORS = listOf("midnight", "ocean", "emerald", "violet", "crimson", "rose", "gold", "sunset", "slate", "metal")
val NETWORKS = linkedMapOf(
    "visa" to "VISA", "mastercard" to "Mastercard", "rupay" to "RuPay", "amex" to "AMEX",
    "diners" to "Diners", "discover" to "Discover", "maestro" to "Maestro", "jcb" to "JCB", "other" to "",
)
val BANKS = listOf(
    "HDFC Bank", "ICICI Bank", "SBI Card", "Axis Bank", "Kotak Mahindra Bank", "IDFC FIRST Bank", "American Express",
    "AU Small Finance Bank", "IndusInd Bank", "Yes Bank", "RBL Bank", "Standard Chartered", "HSBC", "Federal Bank",
    "OneCard", "Bank of Baroda", "Union Bank", "Canara Bank",
)

private val rnd = SecureRandom()
fun uid(): String {
    val b = ByteArray(9); rnd.nextBytes(b)
    return Base64.getEncoder().encodeToString(b).replace(Regex("[+/=]"), "x")
}

fun detectNetwork(raw: String): String {
    val n = raw.filter { it.isDigit() }
    if (n.isEmpty()) return ""
    return when {
        Regex("^3[47]").containsMatchIn(n) -> "amex"
        Regex("^(508|60|65|81|82|353|356)").containsMatchIn(n) -> "rupay"
        n.startsWith("4") -> "visa"
        Regex("^(5[1-5]|222[1-9]|22[3-9]|2[3-6]|27[01]|2720)").containsMatchIn(n) -> "mastercard"
        Regex("^3(0[0-5]|[689])").containsMatchIn(n) -> "diners"
        n.startsWith("35") -> "jcb"
        Regex("^(50|5[6-9]|6)").containsMatchIn(n) -> "maestro"
        else -> "other"
    }
}

fun luhn(raw: String): Boolean {
    val n = raw.filter { it.isDigit() }
    if (n.length < 12) return false
    var s = 0; var d = false
    for (i in n.length - 1 downTo 0) {
        var x = n[i] - '0'
        if (d) { x *= 2; if (x > 9) x -= 9 }
        s += x; d = !d
    }
    return s % 10 == 0
}

fun fmtNum(raw: String, net: String): String {
    val n = raw.filter { it.isDigit() }
    if (net == "amex") return listOf(n.take(4), n.drop(4).take(6), n.drop(10).take(5)).filter { it.isNotEmpty() }.joinToString(" ")
    return n.chunked(4).joinToString(" ")
}

fun maskNum(raw: String, net: String): String {
    val n = raw.filter { it.isDigit() }
    if (n.isEmpty()) return "•••• •••• •••• ••••"
    return if (net == "amex") "•••• •••••• •" + n.takeLast(4) else "•••• •••• •••• " + n.takeLast(4)
}

fun Card.net(): String = network.ifEmpty { detectNetwork(number) }
fun Card.name(): String = nickname.ifEmpty { bank.ifEmpty { "Card" } }
fun Card.last4(): String = number.takeLast(4)
fun Card.exp(): String = if (expMonth.isNotEmpty() && expYear.isNotEmpty()) "$expMonth/${expYear.takeLast(2)}" else ""
fun Card.label(): String = name() + if (last4().isNotEmpty()) " ··" + last4() else ""
fun Card.bankLabel(): String = name() + (if (bank.isNotEmpty() && nickname.isNotEmpty()) " · $bank" else "") + (if (last4().isNotEmpty()) " ··" + last4() else "")

/* ---------- Dates ---------- */

fun today(): LocalDate = LocalDate.now()
private fun atDay(y: Int, m0: Int, day: Int): LocalDate {
    val ym = YearMonth.of(y, 1).plusMonths(m0.toLong())
    return ym.atDay(minOf(day, ym.lengthOfMonth()))
}

/** Next date (today or later) that falls on [day] of the month. */
fun nextDay(day: Int): LocalDate? {
    if (day <= 0) return null
    val t = today()
    var d = atDay(t.year, t.monthValue - 1, day)
    if (d.isBefore(t)) d = atDay(t.year, t.monthValue, day)
    return d
}

fun occurrence(day: Int, k: Int): LocalDate? {
    val f = nextDay(day) ?: return null
    return atDay(f.year, f.monthValue - 1 + k, day)
}

fun daysUntil(d: LocalDate): Int = ChronoUnit.DAYS.between(today(), d).toInt()

private val IN = Locale("en", "IN")
fun fmtDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", IN))
fun fmtDateYear(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM yyyy", IN))
fun rel(n: Int): String = when (n) { 0 -> "today"; 1 -> "tomorrow"; else -> "in $n days" }
fun ordinal(n: Int): String {
    val v = n % 100
    val suf = if (v in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suf"
}

private val nf = NumberFormat.getNumberInstance(IN).apply { maximumFractionDigits = 0 }
fun inr(n: Long): String = "₹" + nf.format(n)

enum class Expiry(val text: String) { EXPIRED("Expired"), SOON("Expires soon") }
fun Card.expiryStatus(): Expiry? {
    val m = expMonth.toIntOrNull() ?: return null
    val y = expYear.toIntOrNull() ?: return null
    if (m !in 1..12) return null
    val end = YearMonth.of(y, m).atEndOfMonth()
    val days = daysUntil(end)
    return when { days < 0 -> Expiry.EXPIRED; days <= 60 -> Expiry.SOON; else -> null }
}

/* ---------- Runway ---------- */

data class Runway(val card: Card, val stmt: LocalDate, val due: LocalDate, val days: Int, val edge: Boolean)

/** Buy on [p] → lands on the next statement (the statement day itself counts as that bill) → pay by the due date after it. */
fun runwayFor(c: Card, p: LocalDate): Runway? {
    if (c.type != "credit" || c.statementDay <= 0 || c.dueDay <= 0) return null
    var stmt = atDay(p.year, p.monthValue - 1, c.statementDay)
    if (stmt.isBefore(p)) stmt = atDay(p.year, p.monthValue, c.statementDay)
    var due = atDay(stmt.year, stmt.monthValue - 1, c.dueDay)
    if (!due.isAfter(stmt)) due = atDay(stmt.year, stmt.monthValue, c.dueDay)
    return Runway(c, stmt, due, ChronoUnit.DAYS.between(p, due).toInt(), stmt == p)
}

fun Vault.runwayRank(p: LocalDate): List<Runway> =
    cards.mapNotNull { runwayFor(it, p) }.sortedWith(compareByDescending<Runway> { it.days }.thenBy { it.card.name() })

/* ---------- Vault helpers ---------- */

fun Vault.isPaid(c: Card, d: LocalDate): Boolean = meta.paid[c.id] == d.toString()

fun Vault.groupOthers(c: Card): List<Card> =
    if (c.limitGroup == null) emptyList() else cards.filter { it.limitGroup == c.limitGroup && it.id != c.id }

fun cleanupGroups(cards: List<Card>): List<Card> {
    val count = cards.mapNotNull { it.limitGroup }.groupingBy { it }.eachCount()
    return cards.map { if (it.limitGroup != null && (count[it.limitGroup] ?: 0) < 2) it.copy(limitGroup = null) else it }
}

fun Vault.totalLimit(): Long {
    val seen = mutableSetOf<String>()
    var t = 0L
    for (c in cards) {
        val l = c.limit.toLongOrNull() ?: 0L
        if (c.type != "credit" || l <= 0) continue
        val g = c.limitGroup
        if (g != null && !seen.add(g)) continue
        t += l
    }
    return t
}

fun Vault.sorted(list: List<Card> = cards): List<Card> {
    val byName = compareBy<Card>({ it.bank.lowercase() }, { it.nickname.lowercase() })
    return if (meta.customOrder) list.sortedWith(compareBy<Card> { it.order ?: 1_000_000_000 }.then(byName))
    else list.sortedWith(byName)
}

fun Vault.merge(incoming: List<Card>): Triple<Vault, Int, Int> {
    var added = 0; var updated = 0
    val list = cards.toMutableList()
    for (c in incoming) {
        val i = list.indexOfFirst { it.id == c.id || (it.number.isNotEmpty() && it.number == c.number) }
        if (i < 0) { list.add(c); added++ }
        else if (c.updated > list[i].updated) { list[i] = c.copy(id = list[i].id); updated++ }
    }
    return Triple(copy(cards = cleanupGroups(list)), added, updated)
}
