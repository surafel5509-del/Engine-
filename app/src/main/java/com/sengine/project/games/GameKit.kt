package com.sengine.project.games

import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.core.UIButton
import com.sengine.engine.core.UIPanel
import com.sengine.engine.core.UIProgress
import com.sengine.project.AssetLibrary
import com.sengine.project.Project

/** Scene-building helpers shared by the sample games (2D, 3D and game UI). */
internal object GameKit {
    // anchors: 0 Center, 1 Top, 2 Bottom, 3 Left, 4 Right, 5 TL, 6 TR, 7 BL, 8 BR
    const val CENTER = 0; const val TOP = 1; const val BOTTOM = 2; const val LEFT = 3; const val RIGHT = 4
    const val TL = 5; const val TR = 6; const val BL = 7; const val BR = 8

    fun install(p: Project, vararg titles: String) {
        for (t in titles) {
            val item = AssetLibrary.items.firstOrNull { it.title == t } ?: continue
            if (item.installed(p)) continue
            // Bitmap generation needs android.graphics — ignore failures in JVM unit tests.
            try { item.install(p) } catch (_: Throwable) {}
        }
    }

    fun obj(s: Scene, name: String, x: Float = 0f, y: Float = 0f, sx: Float = 1f, sy: Float = 1f, parent: GameObject? = null): GameObject =
        s.create(name, parent).also { it.x = x; it.y = y; it.scaleX = sx; it.scaleY = sy }

    fun obj3(s: Scene, name: String, x: Float, y: Float, z: Float, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f, parent: GameObject? = null): GameObject =
        s.create(name, parent).also { it.x = x; it.y = y; it.z = z; it.scaleX = sx; it.scaleY = sy; it.scaleZ = sz }

    fun GameObject.sprite(color: Long, shape: Int = 0, texture: String = ""): GameObject {
        add(SpriteRenderer().also { it.color = color.toInt(); it.shape = shape; it.texture = texture }); return this
    }
    fun GameObject.box(trigger: Boolean = false, sx: Float = 1f, sy: Float = 1f): GameObject {
        add(Collider2D().also { it.isTrigger = trigger; it.width = sx; it.height = sy }); return this
    }
    fun GameObject.circle(trigger: Boolean = false, r: Float = 0.5f): GameObject {
        add(Collider2D().also { it.shape = 1; it.isTrigger = trigger; it.radius = r }); return this
    }
    fun GameObject.body(type: Int = 0, gravity: Float = 0f, friction: Float = 0.2f, bounce: Float = 0f): GameObject {
        add(Rigidbody2D().also { it.bodyType = type; it.gravityScale = gravity; it.friction = friction; it.bounciness = bounce }); return this
    }
    fun GameObject.script(name: String, params: String = ""): GameObject {
        add(ScriptComponent().also { it.script = name; it.params = params }); return this
    }
    fun GameObject.label(t: String, size: Float, color: Long = 0xFFFFFFFF, align: Int = 1, screen: Boolean = false, anchor: Int = 0): GameObject {
        add(TextRenderer().also { it.text = t; it.size = size; it.color = color.toInt(); it.bold = true; it.align = align; it.screenSpace = screen; it.anchor = anchor }); return this
    }
    fun GameObject.particles(cfg: ParticleEmitter.() -> Unit): GameObject { add(ParticleEmitter().also(cfg)); return this }
    fun GameObject.mesh(kind: Int, color: Long, texture: String = "", tiling: Float = 1f, model: String = ""): GameObject {
        add(MeshRenderer().also { it.mesh = kind; it.color = color.toInt(); it.texture = texture; it.tiling = tiling; it.model = model }); return this
    }
    fun GameObject.col3(sphere: Boolean = false, trigger: Boolean = false): GameObject {
        add(Collider3D().also { it.shape = if (sphere) 1 else 0; it.isTrigger = trigger }); return this
    }
    fun GameObject.body3(type: Int = 0, bounce: Float = 0f, friction: Float = 0.5f): GameObject {
        add(Rigidbody3D().also { it.bodyType = type; it.bounciness = bounce; it.friction = friction }); return this
    }
    fun GameObject.off(): GameObject { active = false; return this }

    fun camera2D(s: Scene, size: Float, bg: Long, follow: String = ""): GameObject =
        obj(s, "Main Camera").also { it.add(Camera2D().also { c -> c.size = size; c.background = bg.toInt(); c.follow = follow }) }

    // ------------------------------------------------------------------ game UI
    fun panel(s: Scene, name: String, x: Float, y: Float, w: Float, h: Float, color: Long = 0xE6141A2E, parent: GameObject? = null, anchor: Int = 0,
              border: Long = 0x665B7CFF, corner: Float = 0.35f, texture: String = ""): GameObject =
        obj(s, name, x, y, parent = parent).also {
            it.order = 200
            it.add(UIPanel().also { p -> p.width = w; p.height = h; p.color = color.toInt(); p.anchor = anchor; p.borderColor = border.toInt(); p.corner = corner; p.texture = texture })
        }

    fun button(s: Scene, name: String, text: String, x: Float, y: Float, w: Float = 3.2f, h: Float = 0.85f, color: Long = 0xFF4C6FFF,
               action: String = "", parent: GameObject? = null, anchor: Int = 0, textSize: Float = 0.38f): GameObject =
        obj(s, name, x, y, parent = parent).also {
            it.order = 210
            it.add(UIButton().also { b ->
                b.text = text; b.width = w; b.height = h; b.color = color.toInt(); b.pressedColor = darker(color).toInt(); b.action = action; b.anchor = anchor; b.textSize = textSize
            })
        }

    fun text(s: Scene, name: String, t: String, x: Float, y: Float, size: Float = 0.4f, color: Long = 0xFFFFFFFF, parent: GameObject? = null, anchor: Int = 0, align: Int = 1): GameObject =
        obj(s, name, x, y, parent = parent).also { it.order = 220; it.label(t, size, color, align, screen = true, anchor = anchor) }

    fun bar(s: Scene, name: String, x: Float, y: Float, w: Float, h: Float, fill: Long, parent: GameObject? = null, anchor: Int = 0, value: Float = 1f): GameObject =
        obj(s, name, x, y, parent = parent).also {
            it.order = 215
            it.add(UIProgress().also { b -> b.width = w; b.height = h; b.fillColor = fill.toInt(); b.anchor = anchor; b.value = value })
        }

    fun darker(c: Long): Long {
        fun ch(sh: Int) = (((c shr sh) and 0xFF) * 3 / 4)
        return (c and 0xFF000000) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Standard pause button + pause panel (Resume / Restart / Menu). */
    fun pauseMenu(s: Scene, menuScene: String, accent: Long = 0xFF4C6FFF) {
        button(s, "PauseBtn", "II", -0.7f, -0.6f, 0.9f, 0.9f, 0x99101828, "pause;show:PausePanel", anchor = TR, textSize = 0.4f)
        val pp = panel(s, "PausePanel", 0f, 0f, 6f, 5.2f).off()
        text(s, "PauseTitle", "PAUSED", 0f, 1.7f, 0.7f, parent = pp)
        button(s, "ResumeBtn", "Resume", 0f, 0.55f, 4f, 0.9f, accent, "resume;hide:PausePanel", pp)
        button(s, "RestartBtn", "Restart", 0f, -0.55f, 4f, 0.9f, 0xFF3A4566, "resume;reload", pp)
        button(s, "MenuBtn", "Main Menu", 0f, -1.65f, 4f, 0.9f, 0xFF3A4566, "resume;scene:$menuScene", pp)
    }
}
