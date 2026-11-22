package com.vinay.cardvault

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vinay.cardvault.data.Prefs
import com.vinay.cardvault.data.Vault
import com.vinay.cardvault.data.fmtDate
import com.vinay.cardvault.data.isPaid
import com.vinay.cardvault.data.label
import com.vinay.cardvault.data.occurrence
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/**
 * Payment and statement reminders.
 * Card details are encrypted, so the reminder texts for the next six months are worked out
 * while the vault is unlocked and kept in the app's private storage until they're due.
 */
object Reminders {
    private const val CHANNEL = "dues"
    private const val REQ = 7300

    data class Notice(val at: Long, val id: Int, val title: String, val body: String)

    private fun hashId(s: String): Int {
        var h = 5381
        for (ch in s) h = (h shl 5) + h + ch.code
        return (Math.abs(h.toLong()) % 2_000_000_000L).toInt() + 1
    }

    fun compute(v: Vault, p: Prefs): List<Notice> {
        if (!p.remindOn && !p.statement) return emptyList()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val out = mutableListOf<Notice>()
        for (c in v.cards) {
            if (c.type != "credit") continue
            val label = c.label()
            if (p.statement && c.statementDay > 0) for (k in 0 until 6) {
                val sd = occurrence(c.statementDay, k) ?: continue
                val at = sd.atTime(p.hour, 0).atZone(zone).toInstant().toEpochMilli()
                if (at > now + 60_000) out += Notice(at, hashId(c.id + sd + "/stmt"), "Statement day", "$label: today's statement is being generated. Check your bill amount.")
            }
            if (!p.remindOn || c.dueDay <= 0) continue
            for (k in 0 until 6) {
                val due = occurrence(c.dueDay, k) ?: continue
                if (v.isPaid(c, due)) continue
                fun add(before: Int, title: String, body: String) {
                    val at = due.minusDays(before.toLong()).atTime(p.hour, 0).atZone(zone).toInstant().toEpochMilli()
                    if (at > now + 60_000) out += Notice(at, hashId(c.id + due + "/" + before), title, body)
                }
                val b = p.daysBefore
                if (b > 0) add(b, "Card payment due in $b day${if (b > 1) "s" else ""}", "$label: due ${fmtDate(due)}. Open Card Vault and tap \"Mark paid\" once done.")
                if (p.onDay || b == 0) add(0, "Card payment due today", "$label: pay today to avoid late fees.")
            }
        }
        return out.sortedBy { it.at }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cardvault.reminders", Context.MODE_PRIVATE)

    private fun saveList(ctx: Context, list: List<Notice>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("at", it.at).put("id", it.id).put("title", it.title).put("body", it.body)) }
        prefs(ctx).edit().putString("list", a.toString()).apply()
    }

    private fun loadList(ctx: Context): List<Notice> = try {
        val a = JSONArray(prefs(ctx).getString("list", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Notice(o.getLong("at"), o.getInt("id"), o.getString("title"), o.getString("body")) }
    } catch (_: Exception) { emptyList() }

    /** Called whenever cards, paid status or reminder settings change (only while unlocked). */
    fun update(ctx: Context, v: Vault, p: Prefs) {
        val keep = loadList(ctx).filter { it.id < 100 } // test notifications
        saveList(ctx, (compute(v, p) + keep).sortedBy { it.at })
        scheduleNext(ctx)
    }

    fun clear(ctx: Context) { saveList(ctx, emptyList()); scheduleNext(ctx) }

    fun test(ctx: Context) {
        val list = loadList(ctx).filter { it.id != 7 } + Notice(System.currentTimeMillis() + 10_000, 7, "Card Vault reminders work", "You'll get alerts like this before payment due dates.")
        saveList(ctx, list.sortedBy { it.at })
        scheduleNext(ctx)
    }

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, REQ, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun scheduleNext(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(ctx))
        val next = loadList(ctx).minByOrNull { it.at } ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(next.at, System.currentTimeMillis() + 1000), pending(ctx))
    }

    fun fire(ctx: Context) {
        val now = System.currentTimeMillis()
        val all = loadList(ctx)
        val due = all.filter { it.at <= now + 30_000 }
        // Skip anything more than a day late (e.g. the phone was off); just drop it
        due.filter { now - it.at < 24 * 3600_000L }.forEach { show(ctx, it) }
        saveList(ctx, all - due.toSet())
        scheduleNext(ctx)
    }

    private fun show(ctx: Context, n: Notice) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Payment reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Credit card payment due dates and statement days"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        })
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_card)
            .setColor(0xFF7C5CFF.toInt())
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(n.id, notif) } catch (_: SecurityException) {}
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Reminders.fire(ctx)
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Reminders.scheduleNext(ctx)
}
