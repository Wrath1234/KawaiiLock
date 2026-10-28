package com.kawaiilock.app

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Emulator-only end-to-end suite: temporarily changes accessibility and restores its original value. */
@RunWith(AndroidJUnit4::class)
class OverlayIntegrationTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val device=run { Configurator.getInstance().setUiAutomationFlags(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES); UiDevice.getInstance(instrumentation) }
    private lateinit var repo:SettingsRepository
    private var previousServices=""
    private var previousEnabled=""
    private var saved:Map<String,* > = emptyMap<String,Any>()
    private val target="com.google.android.deskclock"
    private fun shell(cmd:String):String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(cmd)).bufferedReader().use {it.readText().trim()}
    private fun pause(ms:Long=700) {Thread.sleep(ms)}
    private fun paper()=device.findObject(By.descContains("KawaiiLock paper lock"))
    private fun awaitPaper() {assertTrue("Real blocking overlay must appear",device.wait(Until.hasObject(By.descContains("KawaiiLock paper lock")),7000))}
    private fun launch(pkg:String=target) {context.startActivity(context.packageManager.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));pause();awaitPaper()}
    private fun swipeRight() {val b=paper()!!.visibleBounds;val d=context.resources.displayMetrics.density;device.swipe((b.right-57*d).toInt(),b.centerY(),b.right-8,b.centerY(),16);assertTrue("Right gesture removes overlay",device.wait(Until.gone(By.descContains("KawaiiLock paper lock")),3000))}
    private fun screenshot(name:String) {val dir=File(context.getExternalFilesDir(null),"qa").apply {mkdirs()};assertTrue(device.takeScreenshot(File(dir,"$name.png")));shell("mkdir -p /sdcard/Download/KawaiiLock-QA");shell("cp "+File(dir,"$name.png").absolutePath+" /sdcard/Download/KawaiiLock-QA/$name.png")}
    @Before fun setup() {
        repo=SettingsRepository(context);saved=repo.prefs.all.toMap();previousServices=shell("settings get secure enabled_accessibility_services");previousEnabled=shell("settings get secure accessibility_enabled")
        repo.prefs.edit().clear().commit();PinAuthentication(repo).set(charArrayOf('4','8','2','6'));repo.protectedApps=setOf(target,"com.android.chrome");repo.put("enabled",true)
        val services=previousServices.takeUnless {it=="null"||it.isBlank()}?.split(':').orEmpty().filterNot {it.startsWith("com.kawaiilock.app/")}
        shell("settings put secure enabled_accessibility_services "+(services+"com.kawaiilock.app/.ProtectedAppMonitor").joinToString(":"));shell("settings put secure accessibility_enabled 1");assertTrue("Accessibility setting must persist: "+shell("settings get secure enabled_accessibility_services"),shell("settings get secure enabled_accessibility_services").contains("com.kawaiilock.app"));pause(1400);device.pressHome();pause()
    }
    @After fun restore() {
        device.pressHome();repo.put("enabled",false)
        if(previousServices=="null") shell("settings delete secure enabled_accessibility_services") else shell("settings put secure enabled_accessibility_services $previousServices")
        if(previousEnabled=="null") shell("settings delete secure accessibility_enabled") else shell("settings put secure accessibility_enabled '$previousEnabled'")
        val e=repo.prefs.edit().clear();saved.forEach {(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Float->e.putFloat(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Set<*>->e.putStringSet(k,v.filterIsInstance<String>().toSet())}};e.commit();pause()
    }
    @Test fun rightUnlockAndSwitchRelock() {launch();screenshot("real-overlay");swipeRight();screenshot("right-unlocked");device.pressHome();pause();launch();assertNotNull(paper())}
    @Test fun wrongCutAndBackCannotDismiss() {launch();val b=paper()!!.visibleBounds;val d=context.resources.displayMetrics.density;device.swipe((b.right-57*d).toInt(),b.centerY(),(20*d).toInt(),b.centerY(),35);pause(250);screenshot("wrong-cut");assertNotNull(paper());device.pressBack();pause(2600);assertNotNull(paper());screenshot("reset")}
    @Test fun downwardSpecialCloverUnlocks() {launch();val b=paper()!!.visibleBounds;val d=context.resources.displayMetrics.density;val w=b.width()/d;val x=((28+(w-106)*CloverLayout.fraction(CloverLayout.specialIndex(.3f)))*d).toInt();device.swipe(x,(b.centerY()-65*d).toInt(),x,(b.centerY()+45*d).toInt(),22);assertTrue(device.wait(Until.gone(By.descContains("KawaiiLock paper lock")),3000))}
    @Test fun pinAuthenticatesRealOverlay() {launch();val b=paper()!!.visibleBounds;val d=context.resources.displayMetrics.density;device.click(b.centerX(),b.bottom-(15*d).toInt());assertTrue("PIN panel must open",device.wait(Until.hasObject(By.text("KAWAIILOCK / PIN")),3000));for(digit in listOf("0","0","0","0")) device.findObject(By.text(digit)).click();device.findObject(By.text("Verify")).click();val rejected=device.wait(Until.hasObject(By.text("Incorrect PIN")),10000);screenshot("pin-after-wrong");assertTrue("Wrong PIN must be rejected. Labels: "+device.findObjects(By.clazz("android.widget.TextView")).map {it.text}.joinToString(" / "),rejected);for(digit in listOf("4","8","2","6")) device.findObject(By.text(digit)).click();device.findObject(By.text("Verify")).click();assertTrue("Correct PIN must remove panel",device.wait(Until.gone(By.text("KAWAIILOCK / PIN")),10000));assertNull(paper())}
    @Test fun timeoutRelocksForegroundApp() {repo.put("timeout",2f);pause();launch();swipeRight();awaitPaper()}
    @Test fun disabledShortcutCannotUnlock() {repo.put("shortcut",false);pause();launch();val b=paper()!!.visibleBounds;val d=context.resources.displayMetrics.density;val x=((28+(b.width()/d-106)*.3125f)*d).toInt();device.swipe(x,(b.centerY()-65*d).toInt(),x,(b.centerY()+45*d).toInt(),22);pause();assertNotNull(paper())}
    @Test fun screenOffClearsAuthenticatedSession() {
        launch();swipeRight();device.sleep();pause();device.wakeUp();device.pressMenu();pause();device.swipe(device.displayWidth/2,device.displayHeight-100,device.displayWidth/2,100,20);awaitPaper()
    }
    @Test fun serviceReconnectFailsClosed() {
        launch();swipeRight();shell("settings delete secure enabled_accessibility_services");pause();shell("settings put secure enabled_accessibility_services com.kawaiilock.app/.ProtectedAppMonitor");shell("settings put secure accessibility_enabled 1");pause(1400);awaitPaper()
    }
    @Test fun rapidProtectedAppSwitchingKeepsOneOverlay() {
        repeat(3) { launch();launch("com.android.chrome");assertEquals(1,device.findObjects(By.descContains("KawaiiLock paper lock")).size) }
    }

}
