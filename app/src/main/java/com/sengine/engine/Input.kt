package com.sengine.engine

import android.view.KeyEvent
import com.sengine.engine.render.View2D
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Input state. Raw values are written from the UI thread (touch controls, keys, pointers);
 * [beginFrame] latches them for scripts on the engine thread.
 *
 * Virtual controls are data driven (see [com.sengine.engine.controls.ControlLayout]):
 *  - joystick "move" drives axisX/axisY, joystick "aim" drives axis2X/axis2Y
 *  - buttons are addressed by id: input.button("Fire"); "A" and "B" also drive a/b
 *  - "look" pads accumulate drag deltas into lookX/lookY (first-person cameras)
 */
class Input {
    // raw state written from the UI thread
    @Volatile var joyX = 0f
    @Volatile var joyY = 0f
    @Volatile var rawA = false
    @Volatile var rawB = false
    @Volatile var rawTouching = false
    @Volatile var rawTouchSX = 0f
    @Volatile var rawTouchSY = 0f
    @Volatile var tapPending = false
    val keys: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    val rawButtons = ConcurrentHashMap<String, Boolean>()
    val rawSticks = ConcurrentHashMap<String, FloatArray>()
    @Volatile var rawLookX = 0f
    @Volatile var rawLookY = 0f
    @Volatile var tilt = 0f
    /** Script requests for the on-screen controller: a preset name, "project", or "none". */
    @Volatile var controlsRequest: String? = null
    @Volatile var controlsHidden = false

    /** Pointer events for the in-game UI system: (action 0 down / 1 move / 2 up, pointer id, screen x, y). */
    class Pointer(val action: Int, val id: Int, val x: Float, val y: Float)
    val pointers = ConcurrentLinkedQueue<Pointer>()
    /** Screen rectangles (l,t,r,b) of interactive in-game UI, published by the UI system each frame. */
    @Volatile var uiRects = FloatArray(0)

    fun overUI(x: Float, y: Float): Boolean {
        val r = uiRects
        var i = 0
        while (i + 3 < r.size) { if (x >= r[i] && y >= r[i + 1] && x <= r[i + 2] && y <= r[i + 3]) return true; i += 4 }
        return false
    }

    /** Primary touch from the game view (UI thread). action: 0 down, 1 move, 2 up/cancel. */
    fun touch(action: Int, x: Float, y: Float, id: Int = 0) {
        when (action) {
            0 -> { rawTouching = true; rawTouchSX = x; rawTouchSY = y; tapPending = true }
            1 -> { rawTouchSX = x; rawTouchSY = y }
            else -> rawTouching = false
        }
        pointers.add(Pointer(action, id, x, y))
        while (pointers.size > 64) pointers.poll()
    }

    // per-frame state read by scripts
    var axisX = 0f; private set
    var axisY = 0f; private set
    var axis2X = 0f; private set
    var axis2Y = 0f; private set
    var lookX = 0f; private set
    var lookY = 0f; private set
    var a = false; private set
    var b = false; private set
    var aDown = false; private set
    var bDown = false; private set
    var touching = false; private set
    var tapped = false; private set
    var touchX = 0f; private set
    var touchY = 0f; private set
    var touchScreenX = 0f; private set
    var touchScreenY = 0f; private set
    /** True when the current tap started on a UI button (so games can ignore it). */
    var uiCaptured = false

    private val buttons = HashMap<String, Boolean>()
    private val prevButtons = HashMap<String, Boolean>()
    private val sticks = HashMap<String, FloatArray>()

    fun beginFrame(view: View2D) {
        var kx = 0f
        var ky = 0f
        if (KeyEvent.KEYCODE_A in keys || KeyEvent.KEYCODE_DPAD_LEFT in keys) kx -= 1f
        if (KeyEvent.KEYCODE_D in keys || KeyEvent.KEYCODE_DPAD_RIGHT in keys) kx += 1f
        if (KeyEvent.KEYCODE_S in keys || KeyEvent.KEYCODE_DPAD_DOWN in keys) ky -= 1f
        if (KeyEvent.KEYCODE_W in keys || KeyEvent.KEYCODE_DPAD_UP in keys) ky += 1f
        axisX = (joyX + kx).coerceIn(-1f, 1f)
        axisY = (joyY + ky).coerceIn(-1f, 1f)
        val aim = rawSticks["aim"]
        var ax = aim?.get(0) ?: 0f; var ay = aim?.get(1) ?: 0f
        if (KeyEvent.KEYCODE_J in keys) ax -= 1f
        if (KeyEvent.KEYCODE_L in keys) ax += 1f
        if (KeyEvent.KEYCODE_K in keys) ay -= 1f
        if (KeyEvent.KEYCODE_I in keys) ay += 1f
        axis2X = ax.coerceIn(-1f, 1f); axis2Y = ay.coerceIn(-1f, 1f)
        lookX = rawLookX; lookY = rawLookY; rawLookX = 0f; rawLookY = 0f

        prevButtons.clear(); prevButtons.putAll(buttons)
        buttons.clear()
        for ((k, v) in rawButtons) buttons[k] = v
        // keyboard shortcuts for common buttons
        if (KeyEvent.KEYCODE_SPACE in keys || KeyEvent.KEYCODE_BUTTON_A in keys) buttons["A"] = true
        if (KeyEvent.KEYCODE_ENTER in keys || KeyEvent.KEYCODE_BUTTON_B in keys) buttons["B"] = true
        if (KeyEvent.KEYCODE_F in keys || KeyEvent.KEYCODE_BUTTON_R1 in keys) buttons["Fire"] = true
        if (KeyEvent.KEYCODE_R in keys) buttons["Reload"] = true
        if (KeyEvent.KEYCODE_SHIFT_LEFT in keys) buttons["Brake"] = true
        if (KeyEvent.KEYCODE_W in keys || KeyEvent.KEYCODE_DPAD_UP in keys) buttons["Gas"] = true
        sticks.clear(); for ((k, v) in rawSticks) sticks[k] = floatArrayOf(v[0], v[1])

        val na = rawA || buttons["A"] == true
        val nb = rawB || buttons["B"] == true
        aDown = na && !a
        bDown = nb && !b
        a = na; b = nb
        buttons["A"] = na; buttons["B"] = nb
        touching = rawTouching
        tapped = tapPending && !uiCaptured
        tapPending = false
        uiCaptured = false
        touchScreenX = rawTouchSX; touchScreenY = rawTouchSY
        touchX = view.screenToWorldX(rawTouchSX)
        touchY = view.screenToWorldY(rawTouchSY)
    }

    fun button(id: String): Boolean = buttons[id] == true
    fun buttonDown(id: String): Boolean = buttons[id] == true && prevButtons[id] != true
    fun buttonUp(id: String): Boolean = !button(id) && prevButtons[id] == true
    fun stickX(id: String): Float = if (id == "move") axisX else sticks[id]?.get(0) ?: 0f
    fun stickY(id: String): Float = if (id == "move") axisY else sticks[id]?.get(1) ?: 0f

    fun clear() {
        joyX = 0f; joyY = 0f; rawA = false; rawB = false; rawTouching = false; tapPending = false
        keys.clear(); rawButtons.clear(); rawSticks.clear(); rawLookX = 0f; rawLookY = 0f; pointers.clear()
        buttons.clear(); prevButtons.clear(); sticks.clear()
        a = false; b = false; aDown = false; bDown = false; tapped = false; touching = false
        axis2X = 0f; axis2Y = 0f; lookX = 0f; lookY = 0f; uiCaptured = false
    }
}
