package com.kawaiilock.app

import android.graphics.*
import kotlin.math.*
import java.util.Random

/** Shared by Compose previews and the accessibility overlay. Coordinates are dp. */
class PaperRenderer(private val settings: SettingsRepository) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private val ink=Color.rgb(37,39,34)
    private val botanical=Color.rgb(93,112,75)
    private val paper=Color.rgb(245,240,229)
    private val texture=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas=Canvas(bitmap); val p=Paint(Paint.ANTI_ALIAS_FLAG); val random=Random(73489)
        repeat(58000) { p.color=if(random.nextBoolean()) Color.argb(24,91,77,48) else Color.argb(55,255,255,249); val x=random.nextFloat()*256; val y=random.nextFloat()*256; p.strokeWidth=.35f; canvas.drawLine(x,y,x+random.nextFloat()*1.4f,y+random.nextFloat()*.6f,p) }
        repeat(900) { p.color=Color.argb(18,99,86,62); p.strokeWidth=.3f; val x=random.nextFloat()*256; val y=random.nextFloat()*256; canvas.drawLine(x,y,x+random.nextFloat()*8,y+random.nextFloat()*3,p) }
    }
    private val shader=BitmapShader(texture,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT)
    fun lineY(h:Float)=h*.5f
    fun scissorsX(w:Float)=w-57f
    fun specialX(w:Float)=28f+(w-106f)*CloverLayout.fraction(CloverLayout.specialIndex(settings.position))
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int=ink,face:Typeface=Typeface.create("sans-serif",Typeface.NORMAL),align:Paint.Align=Paint.Align.LEFT) {
        paint.shader=null; paint.color=color; paint.style=Paint.Style.FILL; paint.textSize=size; paint.typeface=face; paint.textAlign=align; c.drawText(s,x,y,paint)
    }
    private fun outer(w:Float,h:Float):Path {
        val p=Path(); p.moveTo(13f,40f)
        var x=13f; while(x<=w-13f) { p.lineTo(x,40f+PaperCutGeometry.edge(x,.6f)*.55f); x+=2f }; p.lineTo(w-13f,40f)
        var y=40f; while(y<=h-34) { p.lineTo(w-13+PaperCutGeometry.edge(y)*.3f,y); y+=3 }
        x=w-13; while(x>=13) { p.lineTo(x,h-34+PaperCutGeometry.edge(x,3f)*.6f); x-=2 }
        p.close(); return p
    }
    fun render(c:Canvas,w:Float,h:Float,progress:Float,blade:Float) {
        paint.reset(); paint.isAntiAlias=true
        val green=when(settings.text("green","Classic")) { "Forest"->Color.rgb(27,76,40); "Botanical"->Color.rgb(54,112,47); else->Color.rgb(36,107,53) }
        c.drawColor(green)
        val outline=outer(w,h); val mid=lineY(h); val sx=scissorsX(w); val cutX=sx-(sx-18f)*progress
        paint.shader=null; paint.color=paper; paint.style=Paint.Style.FILL; paint.setShadowLayer(7f,0f,3f,Color.argb(90,0,0,0)); c.drawPath(outline,paint); paint.clearShadowLayer()
        c.save(); c.clipPath(outline)
        paint.color=Color.rgb(235,228,211); c.drawRect(0f,0f,w,h,paint)
        texture(c,w,h)
        warning(c,w,h)
        // Separate sheets follow a continuous, deterministic cut contour. The warning exists underneath.
        val maxGap=min(165f,h*.235f)*settings.number("animation",1f).coerceAtLeast(.65f)
        for(upper in listOf(true,false)) {
            path.reset()
            if(upper) { path.moveTo(0f,0f); path.lineTo(w,0f) } else { path.moveTo(0f,h); path.lineTo(w,h) }
            var x=w
            while(x>=0) {
                val fraction=((x-18)/(sx-18)).coerceIn(0f,1f)
                val opening=PaperCutGeometry.opening(fraction,progress)
                val jag=if(x>=cutX && progress>0) PaperCutGeometry.edge(x,if(upper) 0f else .8f) else 0f
                val gap=opening*maxGap
                val yy=mid+(if(upper) -gap else gap)+jag
                path.lineTo(x,yy); x-=2f
            }
            path.close()
            c.save(); c.clipPath(path)
            paint.shader=null; paint.color=if(settings.text("paper","Warm")=="Bright") Color.rgb(250,247,240) else paper; c.drawRect(0f,0f,w,h,paint); texture(c,w,h)
            val displacement=progress*maxGap*.65f*(if(upper) -1 else 1)
            if(upper) {
                text(c,"K A W A I I L O C K",34f,77f,9f)
                text(c,"ロック中",w-34f,77f,9f,align=Paint.Align.RIGHT)
            } else {
                text(c,"切り取ってください",34f,h-104f+displacement*.1f,11f,Color.rgb(101,101,87))
                text(c,"Cut along the clover line",34f,h-85f+displacement*.1f,10f,Color.rgb(119,117,107))
            }
            c.restore()
            if(progress>0) {
                // A narrow fold at each free edge supplies depth without an artificial wipe.
                paint.shader=null; paint.style=Paint.Style.STROKE; paint.strokeWidth=1.1f; paint.color=if(upper) Color.argb(130,255,253,245) else Color.argb(70,97,85,60)
                val edge=Path(); x=cutX.coerceAtLeast(13f)
                while(x<w-13) { val f=((x-18)/(sx-18)).coerceIn(0f,1f); val g=PaperCutGeometry.opening(f,progress)*maxGap; val yy=mid+(if(upper) -g else g)+PaperCutGeometry.edge(x,if(upper)0f else .8f); if(x==cutX.coerceAtLeast(13f)) edge.moveTo(x,yy) else edge.lineTo(x,yy); x+=2 }
                c.drawPath(edge,paint); paint.style=Paint.Style.FILL
            }
        }
        c.restore()
        clovers(c,w,mid,cutX,progress)
        scissors(c,cutX,mid,settings.number("scissors",36f),blade)
        text(c,"PIN",w/2,h-13f,9f,Color.rgb(229,237,219),align=Paint.Align.CENTER)
    }
    private fun texture(c:Canvas,w:Float,h:Float) { paint.style=Paint.Style.FILL; paint.shader=shader; paint.alpha=(settings.number("texture",.65f)*255).toInt().coerceIn(0,255); c.drawRect(0f,0f,w,h,paint); paint.shader=null; paint.alpha=255 }
    private fun warning(c:Canvas,w:Float,h:Float) {
        val mid=h*.5f
        text(c,"K A W A I I L O C K",w/2,mid-93,10f,align=Paint.Align.CENTER)
        text(c,"ACCESS DENIED",w/2,mid-77,9f,align=Paint.Align.CENTER)
        paint.color=botanical; paint.strokeWidth=.65f; c.drawLine(w*.32f,mid-65,w*.68f,mid-65,paint)
        val face=Typeface.create(if(settings.text("typography","Condensed")=="Condensed") "sans-serif-condensed" else "serif",Typeface.BOLD)
        val lines=settings.warning.split('\n').flatMap { line -> if(line.length>24) line.chunked(24) else listOf(line) }.take(4)
        var size=34f
        paint.typeface=face
        for(line in lines) { paint.textSize=size; if(paint.measureText(line)>w-62) size*=((w-62)/paint.measureText(line)) }
        lines.forEachIndexed { i,line -> text(c,line,w/2,mid+(i-(lines.size-1)/2f)*(size*1.16f)+size*.3f,size,face=face,align=Paint.Align.CENTER) }
        paint.color=botanical; c.drawLine(w*.24f,mid+70,w*.76f,mid+70,paint)
        if(settings.bool("subtitle",true)) text(c,"私のスマホに触らないで。",w/2,mid+94,11f,align=Paint.Align.CENTER)
    }
    fun clovers(c:Canvas,w:Float,y:Float,cutX:Float=Float.MAX_VALUE,progress:Float=0f) {
        val end=w-78f; val start=28f
        paint.shader=null; paint.color=botanical; paint.strokeWidth=.7f
        for(i in 0 until CloverLayout.count) {
            val x=start+(end-start)*CloverLayout.fraction(i)
            if(progress>0 && x>cutX-12f) continue
            if(i<16) { val next=start+(end-start)*CloverLayout.fraction(i+1); var mark=x+6; while(mark<next-5) { c.drawLine(mark,y,mark+1.5f,y,paint); mark+=4f } }
            clover(c,x,y,settings.number("clover",4.1f),CloverLayout.leaves(i,settings.shortcut,settings.position),i)
        }
    }
    private fun clover(c:Canvas,x:Float,y:Float,r:Float,leaves:Int,index:Int) {
        c.save(); c.translate(x,y); c.rotate((index%3-1)*5f)
        paint.color=botanical; paint.style=if(settings.text("cloverStyle","Classic")=="Outline") Paint.Style.STROKE else Paint.Style.FILL; paint.strokeWidth=.65f
        repeat(leaves) { c.save(); c.rotate(it*360f/leaves); c.drawOval(-r*.50f,-r*1.23f,r*.50f,-r*.1f,paint); c.restore() }
        paint.style=Paint.Style.FILL; c.drawCircle(0f,0f,r*.2f,paint); c.restore()
    }
    private fun scissors(c:Canvas,x:Float,y:Float,size:Float,phase:Float) {
        c.save(); c.translate(x,y); c.scale(size/36f,size/36f)
        paint.color=ink; paint.style=Paint.Style.STROKE; paint.strokeWidth=2.7f; paint.strokeCap=Paint.Cap.ROUND
        val angle=if(phase==0f) 25f else 15f+abs(sin(phase))*19f
        for(sign in listOf(-1,1)) {
            c.save(); c.rotate(sign*angle)
            c.drawLine(-18f,0f,9f,0f,paint)
            c.drawOval(7f,-4.6f,19f,4.6f,paint)
            c.restore()
        }
        paint.style=Paint.Style.FILL; c.drawCircle(0f,0f,2.2f,paint); paint.color=paper; c.drawCircle(0f,0f,.75f,paint); c.restore()
    }
}
