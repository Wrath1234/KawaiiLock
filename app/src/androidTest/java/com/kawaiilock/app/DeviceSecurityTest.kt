package com.kawaiilock.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas

@RunWith(AndroidJUnit4::class)
class DeviceSecurityTest {
    private lateinit var repo:SettingsRepository
    @Before fun setup() {
        // A separate preference namespace prevents destructive changes to the user's settings.
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val isolated=object:android.content.ContextWrapper(target) {
            override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("test_"+name,mode)
        }
        repo=SettingsRepository(isolated);repo.prefs.edit().clear().commit()
    }
    @After fun cleanup() {repo.prefs.edit().clear().commit()}
    @Test fun pinVerifierIsSaltedAndNotPlaintext() {val auth=PinAuthentication(repo);val digits=charArrayOf('9','2','7','4');auth.set(digits);val first=repo.text("pinHash");assertFalse(first.contains("9274"));assertTrue(auth.verify(digits));auth.set(digits);assertNotEquals(first,repo.text("pinHash"));assertFalse(auth.verify(charArrayOf('1','2','3','4')))}
    @Test fun wrongAttemptsPersistCooldown() {val auth=PinAuthentication(repo);auth.set(charArrayOf('4','8','2','6'));repeat(5){assertFalse(auth.verify(charArrayOf('0','0','0','0')))};assertTrue(PinAuthentication(repo).remainingMs()>0);assertFalse(auth.verify(charArrayOf('4','8','2','6')))}
    @Test fun changingPinInvalidatesOldPin() {val auth=PinAuthentication(repo);auth.set(charArrayOf('1','3','5','7'));auth.set(charArrayOf('2','4','6','8'));assertFalse(auth.verify(charArrayOf('1','3','5','7')));assertTrue(auth.verify(charArrayOf('2','4','6','8')))}
    @Test fun settingsSurviveRepositoryRecreation() {repo.put("position",.65f);repo.put("shortcut",false);repo.protectedApps=setOf("example.app");val context=InstrumentationRegistry.getInstrumentation().targetContext;val isolated=object:android.content.ContextWrapper(context){override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("test_"+name,mode)};val restored=SettingsRepository(isolated);assertEquals(.65f,restored.position,.001f);assertFalse(restored.shortcut);assertEquals(setOf("example.app"),restored.protectedApps)}
    @Test fun protectionCannotEnableWithoutRecoveryPin() {repo.put("enabled",true);assertFalse(repo.enabled);PinAuthentication(repo).set(charArrayOf('4','7','3','9'));assertTrue(repo.enabled)}
    @Test fun rendererDeterministicAtEveryStageAndResolution() {
        val renderer=PaperRenderer(repo)
        for(width in listOf(320,412,600)) for(progress in listOf(0f,.25f,.5f,.75f,1f)) {
            val a=Bitmap.createBitmap(width,850,Bitmap.Config.ARGB_8888);val b=Bitmap.createBitmap(width,850,Bitmap.Config.ARGB_8888)
            renderer.render(Canvas(a),width.toFloat(),850f,progress,0f);renderer.render(Canvas(b),width.toFloat(),850f,progress,0f)
            assertTrue("pixels must be stable at $width/$progress",a.sameAs(b));a.recycle();b.recycle()
        }
    }
}
