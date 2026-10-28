package com.kawaiilock.app

import org.junit.Assert.*
import org.junit.Test

class LockEngineTest {
    private fun recognizer(enabled:Boolean=true)=GestureRecognizer(400f,400f,343f,enabled,137f)
    @Test fun rightUnlockIsDeliberate() {val r=recognizer();r.down(Point(343f,400f));assertEquals(GestureResult.NONE,r.move(Point(355f,400f)));assertEquals(GestureResult.UNLOCK_RIGHT,r.move(Point(388f,403f)))}
    @Test fun leftAlwaysCuts() {val r=recognizer();r.down(Point(343f,400f));assertEquals(GestureResult.CUT,r.move(Point(180f,400f)));assertEquals(.5015f,r.progress,.005f);assertEquals(GestureResult.CUT,r.move(Point(18f,400f)));assertEquals(1f,r.progress,0f)}
    @Test fun reversalDoesNotUnlock() {val r=recognizer();r.down(Point(343f,400f));r.move(Point(250f,400f));assertEquals(GestureResult.CUT,r.move(Point(399f,400f)));assertEquals(0f,r.progress,0f)}
    @Test fun tapsDoNothing() {val r=recognizer();r.down(Point(343f,400f));assertEquals(GestureResult.NONE,r.move(Point(343f,400f)))}
    @Test fun scissorsMustBeTouched() {val r=recognizer();r.down(Point(220f,400f));assertEquals(GestureResult.NONE,r.move(Point(390f,400f)))}
    @Test fun downwardCloverUnlocks() {val r=recognizer();r.down(Point(137f,350f));assertEquals(GestureResult.UNLOCK_CLOVER,r.move(Point(145f,440f)))}
    @Test fun cloverDisabledDoesNothing() {val r=recognizer(false);r.down(Point(137f,350f));assertEquals(GestureResult.NONE,r.move(Point(137f,450f)))}
    @Test fun cloverNeedsCrossing() {val r=recognizer();r.down(Point(137f,310f));assertEquals(GestureResult.NONE,r.move(Point(137f,390f)))}
    @Test fun sidewaysSwipeRejected() {val r=recognizer();r.down(Point(137f,350f));assertEquals(GestureResult.NONE,r.move(Point(180f,440f)))}
    @Test fun wrongCloverDoesNothing() {val r=recognizer();r.down(Point(210f,350f));assertEquals(GestureResult.NONE,r.move(Point(210f,450f)))}
    @Test fun upwardDoesNotUnlock() {val r=recognizer();r.down(Point(137f,350f));assertEquals(GestureResult.NONE,r.move(Point(137f,250f)))}
    @Test fun verticalScissorsDoesNotUnlock() {val r=recognizer();r.down(Point(343f,400f));assertEquals(GestureResult.NONE,r.move(Point(390f,490f)))}
    @Test fun cancellationResets() {val r=recognizer();r.down(Point(343f,400f));r.move(Point(100f,400f));r.cancel();assertEquals(0f,r.progress,0f);assertEquals(GestureResult.NONE,r.move(Point(399f,400f)))}
    @Test fun successConsumedOnce() {val r=recognizer();r.down(Point(343f,400f));r.move(Point(390f,400f));assertEquals(GestureResult.NONE,r.move(Point(398f,400f)))}
    @Test fun progressClamps() {val r=recognizer();r.down(Point(343f,400f));r.move(Point(-100f,400f));assertEquals(1f,r.progress,0f)}
    @Test fun edgesDeterministicAndRestrained() { for(i in 0..500) {val x=i*.77f;assertEquals(PaperCutGeometry.edge(x),PaperCutGeometry.edge(x),0f);assertTrue(kotlin.math.abs(PaperCutGeometry.edge(x))<2.76f)} }
    @Test fun cutOpeningGrowsBehindTip() {assertEquals(0f,PaperCutGeometry.opening(.4f,.5f),0f);assertTrue(PaperCutGeometry.opening(.8f,.75f)>PaperCutGeometry.opening(.8f,.25f));assertEquals(1f,PaperCutGeometry.opening(1f,1f),0f)}
    @Test fun exactlyOneLandmarkAtEveryPosition() {for(position in listOf(.12f,.3f,.5f,.78f)) {assertEquals(1,(0 until 17).count {CloverLayout.leaves(it,true,position)==5});assertEquals(0,(0 until 17).count {CloverLayout.leaves(it,false,position)==5})} }
    @Test fun landmarkWithinOneSymbolOfRequestedPosition() {assertTrue(kotlin.math.abs(CloverLayout.fraction(CloverLayout.specialIndex(.3f))-.3f)<.04f)}
    @Test fun unlockedActivityTransitionsStayUnlocked() {val p=RelockPolicy();assertTrue(p.enter("a",100,true,0));p.unlock("a",100);assertFalse(p.enter("a",150,true,0));assertFalse(p.isExpired("a",150,0))}
    @Test fun appSwitchRelocks() {val p=RelockPolicy();p.enter("a",100,true,0);p.unlock("a",100);p.enter("b",120,true,0);assertTrue(p.enter("a",130,true,0))}
    @Test fun graceAllowsQuickReturn() {val p=RelockPolicy();p.enter("a",100,true,500);p.unlock("a",100);p.enter("b",120,true,500);assertFalse(p.enter("a",130,true,500))}
    @Test fun switchDisabledKeepsSession() {val p=RelockPolicy();p.enter("a",100,false,0);p.unlock("a",100);p.enter("b",200,false,0);assertFalse(p.enter("a",300,false,0))}
    @Test fun screenOffClearLocksEverything() {val p=RelockPolicy();p.unlock("a",100);p.clear();assertTrue(p.isExpired("a",101,0))}
    @Test fun processRestartFailsClosed() {val old=RelockPolicy();old.unlock("a",100);val fresh=RelockPolicy();assertTrue(fresh.enter("a",101,false,0))}
    @Test fun timeoutExpiresExactlyAtBoundary() {val p=RelockPolicy();p.unlock("a",100);assertFalse(p.isExpired("a",599,500));assertTrue(p.isExpired("a",600,500))}
    @Test fun returningToSessionKeepsOriginalDeadline() {val p=RelockPolicy();p.unlock("a",100);p.enter("b",200,false,0);p.enter("a",300,false,0);assertEquals(300L,p.timeUntilExpiry("a",300,500));assertEquals(0L,p.timeUntilExpiry("a",600,500));assertEquals(0L,p.timeUntilExpiry("a",300,0))}

}
