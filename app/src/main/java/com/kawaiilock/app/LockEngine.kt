package com.kawaiilock.app

import kotlin.math.*

/** All distances passed to the recognizer are dp. No Android dependencies. */
data class Point(val x: Float, val y: Float)
enum class GestureResult { NONE, CUT, UNLOCK_RIGHT, UNLOCK_CLOVER }
class GestureRecognizer(private val width: Float, private val lineY: Float, private val scissorsX: Float,
    private val shortcut: Boolean, private val specialX: Float, private val sensitivity: Float = 1f) {
    private var start: Point? = null
    private var scissors = false
    private var clover = false
    private var direction = 0
    var progress = 0f; private set
    fun down(p: Point) {
        start = p; direction = 0; progress = 0f
        scissors = abs(p.x - scissorsX) <= 32f && abs(p.y - lineY) <= 34f
        clover = shortcut && abs(p.x-specialX) <= 26f && p.y in (lineY-100f)..(lineY-12f)
    }
    fun move(p: Point): GestureResult {
        val s = start ?: return GestureResult.NONE
        val dx = p.x-s.x; val dy = p.y-s.y
        if (scissors) {
            if (direction == 0 && abs(dx)>12f && abs(dx)>abs(dy)*1.35f) direction = if (dx>0) 1 else -1
            if (direction == 1 && dx >= min(48f, max(20f, (width-s.x)*0.72f)) / sensitivity && abs(dy) < max(32f, dx*0.65f)) {
                start=null; return GestureResult.UNLOCK_RIGHT
            }
            if (direction == -1) { progress = (-dx / (scissorsX-18f)).coerceIn(0f,1f); return GestureResult.CUT }
        }
        if (clover && dy >= 64f/sensitivity && p.y > lineY+18f && abs(dx) <= 28f && abs(dx)<dy*0.4f) {
            start=null; return GestureResult.UNLOCK_CLOVER
        }
        return GestureResult.NONE
    }
    fun cancel() { start=null; scissors=false; clover=false; direction=0; progress=0f }
}

object CloverLayout {
    const val count = 17
    fun specialIndex(position: Float): Int = (position.coerceIn(.12f,.78f)*(count-1)).roundToInt()
    fun fraction(index: Int): Float = index.toFloat()/(count-1)
    fun leaves(index: Int, enabled: Boolean, position: Float): Int = if (enabled && index==specialIndex(position)) 5 else 4
}
object PaperCutGeometry {
    /** Stable band-limited detail plus tiny fibre serrations, sampled at fixed x positions. */
    fun edge(x: Float, seed: Float = 0f): Float =
        sin(x*.073f+seed)*1.5f + sin(x*.237f+seed*2f)*.8f + sin(x*1.19f+seed)*.45f
    fun opening(xFraction: Float, progress: Float): Float {
        val tip = 1f-progress
        if (xFraction<tip || progress<=0f) return 0f
        return sqrt(((xFraction-tip)/max(progress,.001f)).coerceIn(0f,1f))*progress
    }
}
class RelockPolicy {
    private val unlocked = mutableMapOf<String,Long>()
    var foreground: String? = null; private set
    fun enter(pkg: String, now: Long, switch: Boolean, grace: Long): Boolean {
        val previous=foreground
        if(previous!=pkg && previous!=null && switch && now-(unlocked[previous] ?: Long.MIN_VALUE/2)>=grace) unlocked.remove(previous)
        foreground=pkg
        val time=unlocked[pkg] ?: return true
        return now-time >= grace && switch && previous!=pkg
    }
    fun isExpired(pkg:String, now:Long, timeout:Long):Boolean = unlocked[pkg]?.let { timeout>0 && now-it>=timeout } ?: true
    fun unlock(pkg:String, now:Long) { unlocked[pkg]=now }
    fun clear() { unlocked.clear() }
    fun forget(pkg:String) { unlocked.remove(pkg) }
}
