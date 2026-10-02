package com.sengine.project.games

import com.sengine.engine.core.*
import com.sengine.project.Project

/** A complete, replayable vertical slice of Price of Freedom built with S Engine's 2D systems. */
object PrisonEscapeGame {
    private fun obj(s: Scene, n: String, x: Float, y: Float, sx: Float = 1f, sy: Float = 1f): GameObject = s.create(n).also { it.x=x; it.y=y; it.scaleX=sx; it.scaleY=sy }
    private fun GameObject paint(c: Long, shape: Int=0) = apply { add(SpriteRenderer().also { it.color=c.toInt(); it.shape=shape }) }
    private fun GameObject solid() = apply { add(Collider2D()) }
    private fun GameObject trigger() = apply { add(Collider2D().also { it.isTrigger=true }) }
    private fun GameObject script(file: String, params: String="") = apply { add(ScriptComponent().also { it.script=file; it.params=params }) }
    private fun GameObject label(t: String, size: Float) = apply { add(TextRenderer().also { it.text=t; it.size=size; it.bold=true }) }

    fun build(p: Project) {
        p.writeAsset("Prisoner.js", """var energy=78; var health=100; var knowledge=0; var suspicion=0; var inventory=[]; var message='';
function start(){ scene.find('Status').text='DAY 1  |  06:00  |  Quiet morning'; log('You were framed. Find proof and escape.'); }
function update(dt){ self.vx=input.axisX*speed; self.vy=input.axisY*speed; energy=clamp(energy+dt*1.8,0,100); if(input.aDown) energy=clamp(energy-18*dt,0,100); var h=scene.find('Status'); if(h) h.text='DAY '+day+'  |  '+clock+'  |  ENERGY '+Math.floor(energy)+'  HEALTH '+Math.floor(health)+'  KNOWLEDGE '+knowledge; if(energy<=0){health-=dt*4; message='You are exhausted.';} if(health<=0){ scene.find('Status').text='HOSPITALIZED — press R to restart'; self.vx=0; self.vy=0; }}
function onTrigger(o){ if(o.tag=='Clue'){knowledge+=o.value; o.destroy(); scene.find('Objective').text='Evidence found. Knowledge '+knowledge+'/50'; audio.beep();} if(o.tag=='Food'){energy=clamp(energy+20,0,100);o.destroy();} if(o.tag=='Friend'){scene.find('Objective').text='Samuel: Meet me at the workshop after lights out.';knowledge+=10;} if(o.tag=='Exit'){ if(knowledge>=50){scene.find('Objective').text='ESCAPED — the evidence reaches the press. You are free.'; scene.find('Status').text='VICTORY  |  PRICE OF FREEDOM';} else scene.find('Objective').text='The gate is locked. Learn more first.'; }}
""")
        p.writeAsset("Guard.js", """var homeX; var phase=0; function start(){homeX=transform.x;} function update(dt){ phase+=dt; transform.x=homeX+Math.sin(phase*speed)*range; if(distanceTo(scene.find('Player'))<2.2) scene.find('Objective').text='GUARD ALERT — keep your distance'; }""")
        p.writeAsset("Clock.js", """var minutes=360; var day=1; function update(dt){minutes+=dt*0.55;if(minutes>=1440){minutes=360;day++;} var h=Math.floor(minutes/60)%24;var m=Math.floor(minutes%60);clock=(h<10?'0':'')+h+':'+(m<10?'0':'')+m;}""")
        p.writeAsset("Interact.js", """function onTap(){scene.find('Objective').text=hint; }""")
        p.writeAsset("PriceOfFreedom.glsl", """vec4 effect(vec4 color, vec2 uv){ float vignette=1.0-0.35*length(uv-vec2(0.5)); return vec4(color.rgb*vignette,color.a); }""")
        val s=Scene("Main")
        val cam=obj(s,"Main Camera",0f,0f).apply { add(Camera2D().also { it.size=14f;it.background=0xFF151A1F.toInt();it.follow="Player" }) }
        val hud=obj(s,"HUD",0f,6f,1f,1f).apply { order=100; label("PRICE OF FREEDOM",0.58f) }
        obj(s,"Status",0f,5.35f).apply { order=100; label("DAY 1  |  06:00",0.38f) }
        obj(s,"Objective",0f,4.72f).apply { order=100; label("Objective: collect 50 knowledge and find the exit",0.34f) }
        // Main yard and room cards form a readable prison sandbox map.
        obj(s,"Floor",0f,0f,18f,8f).paint(0xFF3E4643).solid()
        val rooms=listOf("CELL BLOCK A" to (-10f to 2.5f),"KITCHEN" to (-3.5f to 2.5f),"HOSPITAL" to (3f to 2.5f),"WORKSHOP" to (9.5f to 2.5f),"LIBRARY" to (-3.5f to -3f),"GUARD QUARTERS" to (5f to -3f))
        for((name,pos) in rooms) { obj(s,name,pos.first,pos.second,3.2f,1.5f).paint(if(name=="GUARD QUARTERS")0xFF49383A else 0xFF59615F).solid(); obj(s,name+" label",pos.first,pos.second+0.1f).label(name,0.28f).apply{order=5} }
        val player=obj(s,"Player",-10f,0f,.55f,.75f).paint(0xFFD8B08A).solid().script("Prisoner.js","speed=4.2");player.tag="Player";player.order=10
        val guards=listOf("Sergeant Tesfaye" to (0f to 0f),"Guard Dawit" to (8f to 1f),"Sergeant Marta" to (-1f to -3f))
        for((n,pos) in guards){val g=obj(s,n,pos.first,pos.second,.55f,.8f).paint(0xFFB64B4B).solid().script("Guard.js","speed=0.7, range=2");g.tag="Guard"}
        fun clue(n:String,x:Float,y:Float,v:Int,h:String){val c=obj(s,n,x,y,.38f,.38f).paint(0xFFE2B84D,1).trigger().script("Interact.js","hint=$h, value=$v");c.tag="Clue"}
        clue("Schedule",-10f,2.5f,10,"Schedule learned: roll call at 07:00."); clue("Map",-3.5f,-3f,20,"Mulu's map reveals the old sewer."); clue("Ledger",9.5f,2.5f,25,"A forged ledger proves Abebe took bribes.")
        val food=obj(s,"Lunch",-3.5f,1f,.3f,.3f).paint(0xFFECE5D0,1).trigger();food.tag="Food"
        val friend=obj(s,"Engineer Samuel",9.5f,1f,.5f,.7f).paint(0xFF6BA4A8).trigger();friend.tag="Friend"
        val exit=obj(s,"OUTER GATE",15f,0f,.5f,3f).paint(0xFFB7C1C4).trigger();exit.tag="Exit"
        obj(s,"Time",0f,0f).script("Clock.js")
        p.saveScene(s);p.startScene="Main"
    }
}
