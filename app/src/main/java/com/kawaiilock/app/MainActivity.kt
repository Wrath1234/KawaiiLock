package com.kawaiilock.app

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private val Green=Color(0xFF246B35)
private val Cream=Color(0xFFF5F0E5)
private val Ink=Color(0xFF252722)
private val Muted=Color(0xFF77756B)

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme=lightColorScheme(primary=Green,background=Cream,surface=Cream,onSurface=Ink), typography=Typography(bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=15.sp),titleLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Serif,fontSize=26.sp))) {
            StationeryApp(SettingsRepository(this),if(BuildConfig.DEBUG) intent.getStringExtra("screen") else null,if(BuildConfig.DEBUG && intent.hasExtra("progress")) intent.getFloatExtra("progress",0f) else null)
        } }
    }
}

@Composable private fun StationeryApp(repo:SettingsRepository, initial:String?,fixedProgress:Float?) {
    var screen by rememberSaveableCompat(initial ?: "Home")
    var revision by remember { mutableIntStateOf(0) }
    var pinDialog by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(repo,lifecycle) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->revision++ }
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) revision++ }
        repo.prefs.registerOnSharedPreferenceChangeListener(listener); lifecycle.lifecycle.addObserver(observer)
        onDispose { repo.prefs.unregisterOnSharedPreferenceChangeListener(listener); lifecycle.lifecycle.removeObserver(observer) }
    }
    revision.let { }
    BackHandler(screen!="Home") { screen="Home" }
    if(screen=="Preview" || screen=="Gesture test") {
        val renderer=remember(revision) { PaperRenderer(repo) }
        Box(Modifier.fillMaxSize().background(Green).windowInsetsPadding(WindowInsets.systemBars)) {
            if(fixedProgress!=null) Canvas(Modifier.fillMaxSize()) { val d=density; drawContext.canvas.nativeCanvas.apply { save(); scale(d,d); renderer.render(this,size.width/d,size.height/d,fixedProgress,if(fixedProgress>0)1f else 0f); restore() } }
            else key(revision) { AndroidView(factory={ c->PaperView(c,repo,{notice="Gesture accepted. Preview unlocked.";screen="Home"},{pinDialog=true}) },modifier=Modifier.fillMaxSize()) }
            TextButton(onClick={screen="Home"},modifier=Modifier.align(Alignment.TopEnd)) { Text("Close preview",color=Cream,fontSize=11.sp) }
        }
    } else {
        Column(Modifier.fillMaxSize().background(Green).windowInsetsPadding(WindowInsets.systemBars).padding(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                if(screen!="Home") TextButton(onClick={screen="Home"}) { Text("‹",color=Cream,fontSize=26.sp) }
                Text("K A W A I I L O C K",color=Cream,fontSize=12.sp,modifier=Modifier.weight(1f))
                Text("文具 / 02",color=Cream,fontSize=10.sp)
            }
            Surface(Modifier.fillMaxSize(),shape=RoundedCornerShape(4.dp),color=Cream) {
                Column(Modifier.padding(horizontal=20.dp)) {
                    Spacer(Modifier.height(24.dp)); Text(if(screen=="Home") "A little privacy.\nA sheet of paper." else screen,style=MaterialTheme.typography.titleLarge,lineHeight=31.sp)
                    Text(if(screen=="Home") "APP PROTECTION / お守り" else "KAWAIILOCK / PREFERENCES",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=12.dp,bottom=20.dp))
                    if(notice.isNotEmpty()) { Text(notice,color=Green,fontSize=13.sp); TextButton(onClick={notice=""}) { Text("Dismiss") } }
                    when(screen) {
                        "Home" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            Toggle("Enable app lock","Protect selected applications",repo.enabled) {
                                if(it && !PinAuthentication(repo).isSet) { notice="Set a fallback PIN before enabling protection.";pinDialog=true } else repo.put("enabled",it)
                            }
                            val connected=serviceEnabled(context)
                            Entry(if(connected) "Protection service connected" else "Connect protection service",if(connected) "Accessibility permission is enabled" else "Read the disclosure in Security before enabling") {screen="Security"}
                            for((name,detail) in listOf("Protected applications" to "Choose the apps behind the paper", "Lock preview" to "Try the scissors and paper", "Secret shortcut" to "Five-leaf clover • optional", "Appearance" to "Paper, ink and small details", "Warning message" to "Edit the printed message", "Lock behaviour" to "When the paper returns", "Security" to "PIN, permissions and recovery", "About" to "KawaiiLock 0.3.0")) Entry(name,detail) {screen=if(name=="Lock preview") "Preview" else name}
                            Spacer(Modifier.height(24.dp))
                        }
                        "Protected applications" -> AppSelection(repo)
                        "Secret shortcut" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            Toggle("Downward clover swipe","Disabled: every clover has four leaves",repo.shortcut) {repo.put("shortcut",it)}
                            CloverPreview(repo,revision)
                            Range("Position from left",repo.number("position",.3f),.12f.. .78f,"${(repo.position*100).toInt()}%") {repo.put("position",it)}
                            Range("Gesture sensitivity",repo.number("sensitivity",1f),.8f..1.3f,"${"%.2f".format(repo.number("sensitivity",1f))}×") {repo.put("sensitivity",it)}
                            Text("Begin 12–100 dp above the five-leaf clover. Swipe down through the line; sideways travel cancels the shortcut.",fontSize=13.sp,color=Muted,modifier=Modifier.padding(vertical=16.dp))
                            Entry("Test gestures","Preview only; protected applications stay unchanged") {screen="Gesture test"}
                        }
                        "Appearance" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            Choices("Green backdrop","green",listOf("Classic","Forest","Botanical"),repo)
                            Choices("Paper tone","paper",listOf("Warm","Bright"),repo)
                            Range("Paper texture",repo.number("texture",.65f),0f..1f) {repo.put("texture",it)}
                            Choices("Clover style","cloverStyle",listOf("Classic","Outline"),repo)
                            CloverPreview(repo,revision)
                            Range("Clover size",repo.number("clover",4.1f),3f..5f) {repo.put("clover",it)}
                            Range("Scissors size",repo.number("scissors",36f),30f..40f,"${repo.number("scissors",36f).toInt()} dp") {repo.put("scissors",it)}
                            Toggle("Cut completion sound","A short, quiet feedback tone",repo.bool("sound")) {repo.put("sound",it)}
                            Choices("Haptic feedback","hapticChoice",listOf("Off","Gentle","Firm"),repo) {repo.put("haptics",when(it){"Off"->0f;"Gentle"->.4f;else->1f})}
                            Range("Animation intensity",repo.number("animation",1f),.65f..1.15f) {repo.put("animation",it)}
                            Spacer(Modifier.height(24.dp))
                        }
                        "Warning message" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            var message by remember {mutableStateOf(repo.warning)}
                            OutlinedTextField(message,{message=it.take(96)},label={Text("Printed warning")},minLines=2,maxLines=4,modifier=Modifier.fillMaxWidth())
                            Button(onClick={repo.put("warning",message.ifBlank {"DON'T USE MY\nF*CKING PHONE"});notice="Warning saved."}) {Text("Save message")}
                            Choices("Typography","typography",listOf("Condensed","Serif"),repo)
                            Toggle("Japanese subtitle","私のスマホに触らないで。",repo.bool("subtitle",true)) {repo.put("subtitle",it)}
                            Range("Warning display duration",repo.number("duration",2f),1f..5f,"${"%.1f".format(repo.number("duration",2f))} seconds") {repo.put("duration",it)}
                            Entry("Preview warning","Try a complete leftward cut") {screen="Preview"}
                        }
                        "Lock behaviour" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            Toggle("Relock on screen-off","Clears all authenticated sessions",repo.bool("screenOff",true)) {repo.put("screenOff",it)}
                            Toggle("Relock on application switch","Internal activity transitions stay unlocked",repo.bool("switch",true)) {repo.put("switch",it)}
                            Range("Switch grace period",repo.number("grace",0f),0f..60f,"${repo.number("grace",0f).toInt()} seconds") {repo.put("grace",it)}
                            Range("Relock after timeout",repo.number("timeout",0f),0f..600f,if(repo.number("timeout",0f)<1) "Off" else "${repo.number("timeout",0f).toInt()} seconds") {repo.put("timeout",it)}
                            Text("Changing settings resets authenticated sessions. Process restarts always lock again. A timeout can relock an app while it is open.",fontSize=13.sp,color=Muted,modifier=Modifier.padding(vertical=20.dp))
                        }
                        "Security" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            Entry(if(PinAuthentication(repo).isSet) "Change fallback PIN" else "Set fallback PIN","4–12 digits • stored as a salted PBKDF2 verifier") {pinDialog=true}
                            Text("ACCESSIBILITY DISCLOSURE",fontSize=11.sp,color=Green,modifier=Modifier.padding(top=20.dp,bottom=10.dp))
                            Text("KawaiiLock observes foreground application changes using AccessibilityService and displays a touch-blocking overlay over selected apps. It does not read or save screen text. No data is sent off-device. Enable this service only if you consent.",fontSize=14.sp,lineHeight=21.sp)
                            Entry("Accessibility: ${if(serviceEnabled(context)) "connected" else "not connected"}","Open Android accessibility settings") {context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
                            Text("Overlay permission: supplied by AccessibilityService. A separate “display over other apps” permission is not required.",fontSize=13.sp,color=Muted,modifier=Modifier.padding(vertical=12.dp))
                            Text("FORGOTTEN PIN / RECOVERY",fontSize=11.sp,color=Green,modifier=Modifier.padding(vertical=12.dp))
                            Text("Use either configured secret gesture to unlock. If all recovery methods are forgotten, leave using Home and disable KawaiiLock in Android Accessibility settings, then clear its app data or reinstall. This erases the PIN and protected-app choices. There is no master PIN. This app cannot prevent a device owner from disabling its service.\n\nHyperOS: allow background operation and disable battery restrictions for KawaiiLock if the service is stopped. Sideloaded apps may require “Allow restricted settings” from Android’s app info menu before accessibility can be enabled.",fontSize=14.sp,lineHeight=21.sp)
                            Spacer(Modifier.height(24.dp))
                        }
                        "About" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text("KawaiiLock\nVersion 0.3.0\n\nA Japanese stationery inspired app lock.\n\nThe scissors are a small secret. The paper keeps the rest.\n\nProtection runs locally, without networking, advertisements or analytics. App-lock overlays provide casual privacy, not system-level security.\n\nSource: github.com/Wrath1234/KawaiiLock\n\nThe supplied design reference guides this rebuild. Graphics are generated in code.",fontSize=15.sp,lineHeight=24.sp)
                        }
                    }
                }
            }
        }
    }
    if(pinDialog) PinDialog(repo,verifyOnly=(screen=="Preview"||screen=="Gesture test"),onDismiss={pinDialog=false},onVerified={ if(screen=="Preview"||screen=="Gesture test") {screen="Home";notice="PIN accepted. Preview unlocked."} })
}

@Composable private fun rememberSaveableCompat(initial:String)=androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(initial)}
@Composable private fun Entry(title:String,subtitle:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=15.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {Text(title,fontSize=15.sp);Text(subtitle,fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=3.dp))};Text("›",fontSize=22.sp,color=Green)
    }; HorizontalDivider(color=Color(0xFFE4DFD2),thickness=.5.dp)
}
@Composable private fun Toggle(title:String,subtitle:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f).padding(end=8.dp)) {Text(title,fontSize=14.sp);Text(subtitle,fontSize=11.sp,color=Muted)};Switch(value,onChange)}
    HorizontalDivider(color=Color(0xFFE4DFD2),thickness=.5.dp)
}
@Composable private fun Range(title:String,value:Float,range:ClosedFloatingPointRange<Float>,detail:String="",onChange:(Float)->Unit) {
    Column(Modifier.padding(top=18.dp)) {Text(title,fontSize=14.sp);Slider(value,onChange,valueRange=range);if(detail.isNotEmpty()) Text(detail,fontSize=11.sp,color=Muted)}
}
@Composable private fun Choices(title:String,key:String,options:List<String>,repo:SettingsRepository,extra:(String)->Unit={}) {
    Text(title,fontSize=14.sp,modifier=Modifier.padding(top=18.dp,bottom=6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {for(option in options) FilterChip(repo.text(key,if(key=="hapticChoice") "Firm" else options.first())==option,{repo.put(key,option);extra(option)},label={Text(option,fontSize=12.sp)})}
}
@Composable private fun CloverPreview(repo:SettingsRepository,revision:Int) {
    val renderer=remember(revision) {PaperRenderer(repo)}
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {val d=density; drawContext.canvas.nativeCanvas.apply {save();scale(d,d);renderer.clovers(this,size.width/d+50f,28f);restore()}}
}
private data class AppInfo(val label:String,val pkg:String,val icon:android.graphics.drawable.Drawable)
@Composable private fun AppSelection(repo:SettingsRepository) {
    val context=LocalContext.current
    var apps by remember {mutableStateOf<List<AppInfo>?>(null)}
    var query by remember {mutableStateOf("")}
    LaunchedEffect(Unit) {apps=withContext(Dispatchers.IO) {context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0).filter {it.activityInfo.packageName !in setOf(context.packageName,"com.android.settings","com.android.permissioncontroller","com.google.android.permissioncontroller","com.android.systemui")}.distinctBy {it.activityInfo.packageName}.map {AppInfo(it.loadLabel(context.packageManager).toString(),it.activityInfo.packageName,it.loadIcon(context.packageManager))}.sortedBy {it.label.lowercase()} }}
    OutlinedTextField(query,{query=it},placeholder={Text("Search apps…")},singleLine=true,modifier=Modifier.fillMaxWidth())
    if(apps==null) Text("Loading launchable applications…",modifier=Modifier.padding(16.dp))
    LazyColumn {items(apps.orEmpty().filter {it.label.contains(query,true)},key={it.pkg}) {app ->
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            AndroidView(factory={android.widget.ImageView(it)},update={it.setImageDrawable(app.icon)},modifier=Modifier.size(28.dp))
            Text(app.label,modifier=Modifier.weight(1f).padding(horizontal=12.dp),fontSize=14.sp)
            Switch(app.pkg in repo.protectedApps,{checked->repo.protectedApps=if(checked) repo.protectedApps+app.pkg else repo.protectedApps-app.pkg})
        };HorizontalDivider(color=Color(0xFFE4DFD2),thickness=.5.dp)
    }}
}
@Composable private fun PinDialog(repo:SettingsRepository,verifyOnly:Boolean,onDismiss:()->Unit,onVerified:()->Unit) {
    val auth=remember {PinAuthentication(repo)}
    var phase by remember {mutableIntStateOf(if(auth.isSet) 0 else 1)}
    var first by remember {mutableStateOf("")}
    var entered by remember {mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(when(phase){0->"Verify current PIN";1->"Choose a PIN";else->"Confirm your PIN"})},text={Column {
        Text("Use 4–12 digits. Keep your PIN somewhere safe.",fontSize=13.sp)
        OutlinedTextField(entered,{entered=it.filter(Char::isDigit).take(12)},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),singleLine=true,label={Text("PIN")},enabled=!busy)
        if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
    }},confirmButton={TextButton(enabled=!busy,onClick={
        if(entered.length<4) {error="Enter at least four digits";return@TextButton}
        when(phase) {
            0 -> {busy=true;val chars=entered.toCharArray();entered="";scope.launch {val ok=withContext(Dispatchers.Default){auth.verify(chars).also {chars.fill('\u0000')}};busy=false;if(ok) {if(verifyOnly) {onVerified();onDismiss()} else {phase=1;error=""}} else error=if(auth.remainingMs()>0) "Wait ${(auth.remainingMs()+999)/1000} seconds" else "Incorrect PIN"}}
            1 -> {first=entered;entered="";phase=2;error=""}
            2 -> if(first!=entered) {error="PINs differ. Choose again.";phase=1;entered="";first=""} else {busy=true;val chars=entered.toCharArray();entered="";first="";scope.launch {withContext(Dispatchers.Default){auth.set(chars);chars.fill('\u0000')};busy=false;onDismiss()}}
        }
    }) {Text(if(busy) "Working…" else "Continue")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("Cancel")}})
}
private fun serviceEnabled(context:Context):Boolean=(context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {it.resolveInfo.serviceInfo.packageName==context.packageName}
