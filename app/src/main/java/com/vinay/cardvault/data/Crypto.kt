package com.vinay.cardvault.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Same encryption as the original Card Vault (Web Crypto):
 * key = PBKDF2-HMAC-SHA256(password as UTF-8, 16-byte salt, 310,000 rounds) → 256-bit AES-GCM key,
 * blob = {app, v, kdf, iter, salt, iv, ct} with standard Base64. Old backups open here and new ones open there.
 */
object VaultCrypto {
    const val ITER = 310_000
    private val rnd = SecureRandom()
    private val b64e = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun pbkdf2(pass: String, salt: ByteArray, iter: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pass.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val t = u.copyOf()
        for (i in 1 until iter) {
            u = mac.doFinal(u)
            for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
        }
        return t
    }

    fun newSalt(): ByteArray = ByteArray(16).also { rnd.nextBytes(it) }

    fun encrypt(key: ByteArray, salt: ByteArray, iter: Int, plain: String): JSONObject {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("app", "cardvault"); put("v", 1); put("kdf", "PBKDF2-SHA256"); put("iter", iter)
            put("salt", b64e.encodeToString(salt)); put("iv", b64e.encodeToString(iv)); put("ct", b64e.encodeToString(ct))
        }
    }

    /** Throws if the key is wrong. */
    fun decryptWithKey(blob: JSONObject, key: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, b64d.decode(blob.getString("iv"))))
        return String(c.doFinal(b64d.decode(blob.getString("ct"))), Charsets.UTF_8)
    }

    fun saltOf(blob: JSONObject): ByteArray = b64d.decode(blob.getString("salt"))
    fun iterOf(blob: JSONObject): Int = blob.optInt("iter", ITER)

    /** Derives the key from the password and opens the blob. Throws on a wrong password. Slow: call off the main thread. */
    fun open(blob: JSONObject, pass: String): Session {
        val salt = saltOf(blob); val iter = iterOf(blob)
        val key = pbkdf2(pass, salt, iter)
        return Session(key, salt, iter, parseVault(decryptWithKey(blob, key)))
    }

    fun isBackup(o: JSONObject?): Boolean = o != null && o.optString("app") == "cardvault" && o.has("ct") && o.has("salt") && o.has("iv")
}

/** An unlocked vault: the AES key lives only in memory while unlocked. */
class Session(val key: ByteArray, val salt: ByteArray, val iter: Int, val vault: Vault) {
    fun with(v: Vault) = Session(key, salt, iter, v)
}

/** The encrypted vault file in the app's private storage. */
class VaultFile(ctx: Context) {
    private val file = File(ctx.filesDir, "vault.json")
    fun exists() = file.exists()
    fun read(): JSONObject? = try { if (file.exists()) JSONObject(file.readText()) else null } catch (_: Exception) { null }
    fun write(blob: JSONObject) {
        val tmp = File(file.parentFile, "vault.json.tmp")
        tmp.writeText(blob.toString())
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }
    fun delete() { file.delete() }
}

/** Plain settings (nothing secret). */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("cardvault.settings", Context.MODE_PRIVATE)
    var autolock: Int get() = p.getInt("autolock", 3); set(v) = p.edit().putInt("autolock", v).apply()
    var haptics: String get() = p.getString("haptics", "light") ?: "light"; set(v) = p.edit().putString("haptics", v).apply()
    var bio: Boolean get() = p.getBoolean("bio", false); set(v) = p.edit().putBoolean("bio", v).apply()
    var bioAsked: Boolean get() = p.getBoolean("bioAsked", false); set(v) = p.edit().putBoolean("bioAsked", v).apply()
    var bioCt: String get() = p.getString("bioCt", "") ?: ""; set(v) = p.edit().putString("bioCt", v).apply()
    var bioIv: String get() = p.getString("bioIv", "") ?: ""; set(v) = p.edit().putString("bioIv", v).apply()
    var remindOn: Boolean get() = p.getBoolean("remindOn", false); set(v) = p.edit().putBoolean("remindOn", v).apply()
    var daysBefore: Int get() = p.getInt("daysBefore", 3); set(v) = p.edit().putInt("daysBefore", v).apply()
    var onDay: Boolean get() = p.getBoolean("onDay", true); set(v) = p.edit().putBoolean("onDay", v).apply()
    var hour: Int get() = p.getInt("hour", 9); set(v) = p.edit().putInt("hour", v).apply()
    var statement: Boolean get() = p.getBoolean("statement", false); set(v) = p.edit().putBoolean("statement", v).apply()
}
