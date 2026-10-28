package com.kawaiilock.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.media.AudioManager
import android.media.ToneGenerator
import kotlin.math.abs

class PaperView(context:Context, private val settings:SettingsRepository, private val unlock:()->Unit, private val pin:()->Unit):android.view.View(context) {
    private val renderer=PaperRenderer(settings)
    private val density=resources.displayMetrics.density
    private var recognizer:GestureRecognizer?=null
    var progress=0f; private set
    private var phase=0f
    private var animator:ValueAnimator?=null
    private var complete=false
    private var downY=0f
    private var downX=0f
    private var pinTouch=false
    private var activePointer=-1
    private val resetRunnable=Runnable { reset() }
    init { isFocusable=true; isFocusableInTouchMode=true; isClickable=true; contentDescription="KawaiiLock paper lock. Activate for PIN authentication."; setLayerType(LAYER_TYPE_SOFTWARE,null) }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) { recognizer=GestureRecognizer(w/density,renderer.lineY(h/density),renderer.scissorsX(w/density),settings.shortcut,renderer.specialX(w/density),settings.number("sensitivity",1f)) }
    override fun onDraw(canvas:Canvas) { canvas.save(); canvas.scale(density,density); renderer.render(canvas,width/density,height/density,progress,phase); canvas.restore() }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        val x=event.x/density; val y=event.y/density
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if(complete || animator?.isRunning==true) return true
                activePointer=event.getPointerId(0); downY=y; downX=x; pinTouch=y>height/density-44 && abs(x-width/density/2)<45
                recognizer?.down(Point(x,y)); parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> { recognizer?.cancel(); activePointer=-1; reset() }
            MotionEvent.ACTION_MOVE -> {
                if(activePointer<0 || event.getPointerId(0)!=activePointer || complete || animator?.isRunning==true) return true
                consumePosition(x,y)
            }
            MotionEvent.ACTION_UP -> { if(activePointer>=0 && !complete && animator?.isRunning!=true) consumePosition(x,y); if(pinTouch && abs(x-downX)<12 && abs(y-downY)<12) { performClick() }; activePointer=-1; recognizer?.cancel(); if(!complete) { removeCallbacks(resetRunnable); postDelayed(resetRunnable,100) } }
            MotionEvent.ACTION_CANCEL -> { activePointer=-1; recognizer?.cancel(); if(!complete) reset() }
        }
        return true
    }
    private fun consumePosition(x:Float,y:Float) {
                when(recognizer?.move(Point(x,y))) {
                    GestureResult.CUT -> { val next=recognizer!!.progress; phase+=abs(next-progress)*70f; progress=next; invalidate(); if(progress>=.97f) { progress=1f; complete=true; feedback(true); if(settings.bool("sound")) sound(); removeCallbacks(resetRunnable); postDelayed(resetRunnable,(settings.number("duration",2f)*1000).toLong()) } }
                    GestureResult.UNLOCK_RIGHT,GestureResult.UNLOCK_CLOVER -> { feedback(false); activePointer=-1; unlock() }
                    else -> Unit
                }
    }
    override fun dispatchKeyEvent(event:KeyEvent):Boolean { if(event.keyCode==KeyEvent.KEYCODE_BACK) return true; return super.dispatchKeyEvent(event) }
    override fun performClick():Boolean { super.performClick(); pin(); return true }
    private fun feedback(firm:Boolean) { if(settings.number("haptics",1f)>0f) performHapticFeedback(if(firm && settings.number("haptics",1f)>.5f) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK) }
    private fun sound() { try { val tone=ToneGenerator(AudioManager.STREAM_SYSTEM,18); tone.startTone(ToneGenerator.TONE_PROP_BEEP,35); postDelayed({ tone.release() },100) } catch(_:RuntimeException) { } }
    private fun reset() { animator?.cancel(); phase=0f; animator=ValueAnimator.ofFloat(progress,0f).apply { duration=(450*settings.number("animation",1f)).toLong(); interpolator=DecelerateInterpolator(); addUpdateListener { progress=it.animatedValue as Float; invalidate() }; addListener(object:android.animation.AnimatorListenerAdapter(){ override fun onAnimationEnd(a:android.animation.Animator) { complete=false } }); start() } }
    override fun onDetachedFromWindow() { removeCallbacks(resetRunnable); animator?.cancel(); super.onDetachedFromWindow() }
}
