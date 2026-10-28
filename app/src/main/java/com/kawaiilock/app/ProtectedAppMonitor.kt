package com.kawaiilock.app

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.*
import java.util.concurrent.Executors

/** Event-driven foreground detection. Never reads view text or sends data off-device. */
class ProtectedAppMonitor:AccessibilityService() {
    private lateinit var settings:SettingsRepository
    private lateinit var wm:WindowManager
    private val policy=RelockPolicy()
    private val handler=Handler(Looper.getMainLooper())
    private var overlay:FrameLayout?=null
    private var lockedPackage:String?=null
    private var currentPackage:String?=null
    private val executor=Executors.newSingleThreadExecutor()
    private var registered=false
    private val expiry=Runnable { currentPackage?.let { if(settings.enabled && it in settings.protectedApps && policy.isExpired(it,SystemClock.elapsedRealtime(),timeout())) show(it) } }
    private val receiver=object:BroadcastReceiver() {
        override fun onReceive(c:Context,i:Intent) {
            if(i.action==Intent.ACTION_SCREEN_OFF) { if(settings.bool("screenOff",true)) policy.clear(); hide(); currentPackage=null }
            if(i.action==Intent.ACTION_USER_PRESENT) refreshForeground()
        }
    }
    private val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
        handler.post { if(key !in setOf("pinFailures","pinBlockedUntil")) { policy.clear(); hide(); refreshForeground() } }
    }
    override fun onServiceConnected() {
        settings=SettingsRepository(this); wm=getSystemService(WINDOW_SERVICE) as WindowManager
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        val filter=IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,filter); registered=true
        refreshForeground()
    }
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(!::settings.isInitialized) return
        if(event?.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event?.eventType==AccessibilityEvent.TYPE_WINDOWS_CHANGED) refreshForeground()
    }
    private fun refreshForeground() {
        if(!settings.enabled) { hide(); return }
        // Ignore our overlay and IME. Use an actual application window, not notification shade events.
        val application=windows.filter { it.type==AccessibilityWindowInfo.TYPE_APPLICATION }.sortedByDescending { it.layer }.firstOrNull() ?: return
        val node=application.root ?: return
        val pkg=node.packageName?.toString()
        if(pkg==null) return
        if(pkg==packageName || pkg=="com.android.systemui" || pkg=="com.android.permissioncontroller" || pkg=="com.google.android.permissioncontroller" || pkg=="com.android.settings") {
            if(pkg!="com.android.systemui") { currentPackage=pkg; policy.enter(pkg,SystemClock.elapsedRealtime(),settings.bool("switch",true),grace()); hide() }; return
        }
        if(currentPackage!=pkg) { currentPackage=pkg; handler.removeCallbacks(expiry); if(lockedPackage!=pkg) hide() }
        val needs=policy.enter(pkg,SystemClock.elapsedRealtime(),settings.bool("switch",true),grace())
        if(pkg in settings.protectedApps && (needs || policy.isExpired(pkg,SystemClock.elapsedRealtime(),timeout()))) show(pkg) else {
            hide()
            handler.removeCallbacks(expiry)
            if(pkg in settings.protectedApps) {
                val remaining=policy.timeUntilExpiry(pkg,SystemClock.elapsedRealtime(),timeout())
                if(remaining>0) handler.postDelayed(expiry,remaining)
            }
        }
    }
    private fun grace()=(settings.number("grace",0f)*1000).toLong()
    private fun timeout()=(settings.number("timeout",0f)*1000).toLong()
    private fun show(pkg:String) {
        if(overlay!=null && lockedPackage==pkg) return
        hide(); lockedPackage=pkg
        val frame=FrameLayout(this)
        frame.setBackgroundColor(android.graphics.Color.rgb(36,107,53))
        frame.setOnApplyWindowInsetsListener { v,insets ->
            if(Build.VERSION.SDK_INT>=30) { val safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()); v.setPadding(safe.left,safe.top,safe.right,safe.bottom) }
            else v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }
        frame.addView(PaperView(this,settings,{ unlock() },{ showPin(frame) }),FrameLayout.LayoutParams(-1,-1))
        frame.setOnKeyListener { _,code,_ -> code==KeyEvent.KEYCODE_BACK }
        val params=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or (if(BuildConfig.DEBUG) 0 else WindowManager.LayoutParams.FLAG_SECURE),PixelFormat.OPAQUE)
        params.title="KawaiiLock protection"
        // Respect system bars so Home and permission recovery always remain available.
        if(Build.VERSION.SDK_INT>=30) params.setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        try { wm.addView(frame,params); overlay=frame; frame.requestFocus() } catch(_:WindowManager.BadTokenException) { lockedPackage=null }
    }
    private fun unlock() {
        lockedPackage?.let { policy.unlock(it,SystemClock.elapsedRealtime()) }
        hide(); handler.removeCallbacks(expiry); if(timeout()>0) handler.postDelayed(expiry,timeout())
    }
    private fun hide() { overlay?.let { try { wm.removeViewImmediate(it) } catch(_:IllegalArgumentException){} }; overlay=null; lockedPackage=null }
    private fun showPin(frame:FrameLayout) {
        if(frame.childCount>1) return
        val pin=PinAuthentication(settings)
        val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(40,40,40,40); setBackgroundColor(android.graphics.Color.rgb(245,240,229)) }
        val title=TextView(this).apply { text="KAWAIILOCK / PIN"; textSize=18f; setTextColor(android.graphics.Color.rgb(37,39,34)); gravity=Gravity.CENTER }
        val status=TextView(this).apply { text="Enter your PIN"; setTextColor(android.graphics.Color.rgb(37,39,34)); gravity=Gravity.CENTER; setPadding(0,24,0,24) }
        panel.addView(title); panel.addView(status)
        val digits=StringBuilder()
        fun update() { status.text="•".repeat(digits.length).ifEmpty { "Enter your PIN" } }
        for(row in listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf("⌫","0","Verify"))) {
            val buttons=LinearLayout(this)
            for(key in row) buttons.addView(Button(this).apply { text=key; setTextColor(android.graphics.Color.rgb(37,39,34)); backgroundTintList=android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(232,227,214)); stateListAnimator=null; elevation=0f; contentDescription=if(key=="⌫") "Delete PIN digit" else key; setOnClickListener {
                when(key) {
                    "⌫" -> { if(digits.isNotEmpty()) digits.deleteCharAt(digits.lastIndex); update() }
                    "Verify" -> {
                        if(pin.remainingMs()>0) status.text="Try again in ${(pin.remainingMs()+999)/1000} seconds"
                        else {
                            val entered=digits.toString().toCharArray(); digits.clear(); isEnabled=false; status.text="Verifying…"
                            executor.execute { val valid=pin.verify(entered); entered.fill('\u0000'); this@ProtectedAppMonitor.handler.post { isEnabled=true; if(this@ProtectedAppMonitor.overlay===frame) { if(valid) unlock() else status.text=if(pin.remainingMs()>0) "Too many attempts. Wait ${(pin.remainingMs()+999)/1000}s" else "Incorrect PIN" } } }
                        }
                    }
                    else -> { if(digits.length<12) digits.append(key); update() }
                }
            } },LinearLayout.LayoutParams(0,64.dp(),1f))
            panel.addView(buttons)
        }
        panel.addView(Button(this).apply { text="Return to paper"; setOnClickListener { digits.clear(); frame.removeView(panel) } })
        frame.addView(panel,FrameLayout.LayoutParams(-1,-1))
    }
    private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
    override fun onInterrupt() { hide() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); hide(); if(registered) unregisterReceiver(receiver); if(::settings.isInitialized) settings.prefs.unregisterOnSharedPreferenceChangeListener(listener); executor.shutdown(); super.onDestroy() }
}
