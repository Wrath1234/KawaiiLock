package com.kawaiilock.app

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

class SettingsRepository(context: Context) {
    val prefs = context.getSharedPreferences("stationery", Context.MODE_PRIVATE)
    fun bool(key:String, default:Boolean=false)=prefs.getBoolean(key,default)
    fun number(key:String, default:Float)=prefs.getFloat(key,default)
    fun text(key:String, default:String="")=prefs.getString(key,default) ?: default
    fun put(key:String,value:Boolean) { prefs.edit().putBoolean(key,value).apply() }
    fun put(key:String,value:Float) { prefs.edit().putFloat(key,value).apply() }
    fun put(key:String,value:String) { prefs.edit().putString(key,value).apply() }
    var protectedApps: Set<String>
        get() = prefs.getStringSet("apps", emptySet())!!.toSet()
        set(value) { prefs.edit().putStringSet("apps",value.toSet()).apply() }
    val enabled get()=bool("enabled") && PinAuthentication(this).isSet
    val shortcut get()=bool("shortcut",true)
    val position get()=number("position",.30f)
    val warning get()=text("warning","DON'T USE MY\nF*CKING PHONE")
}

class PinAuthentication(private val settings:SettingsRepository) {
    val isSet get()=settings.text("pinHash").isNotEmpty()
    private fun derive(pin:CharArray,salt:ByteArray):ByteArray {
        val spec=PBEKeySpec(pin,salt,210_000,256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    fun set(pin:CharArray) {
        require(pin.size in 4..12 && pin.all { it.isDigit() })
        val salt=ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hash=derive(pin,salt)
        settings.prefs.edit().putString("pinSalt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("pinHash",Base64.encodeToString(hash,Base64.NO_WRAP)).putInt("pinFailures",0).remove("pinBlockedUntil").commit()
    }
    fun remainingMs(now:Long=System.currentTimeMillis())=(settings.prefs.getLong("pinBlockedUntil",0)-now).coerceAtLeast(0)
    @Synchronized fun verify(pin:CharArray):Boolean {
        if(!isSet || remainingMs()>0) return false
        val valid=try { MessageDigest.isEqual(derive(pin,Base64.decode(settings.text("pinSalt"),Base64.NO_WRAP)),Base64.decode(settings.text("pinHash"),Base64.NO_WRAP)) } catch(_:IllegalArgumentException) { false }
        val failures=if(valid) 0 else settings.prefs.getInt("pinFailures",0)+1
        val delay=if(failures>=5) (30_000L shl (failures-5).coerceAtMost(5)) else 0L
        settings.prefs.edit().putInt("pinFailures",failures).putLong("pinBlockedUntil",if(valid) 0 else System.currentTimeMillis()+delay).commit()
        return valid
    }
}
