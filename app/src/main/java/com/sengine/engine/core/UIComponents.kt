package com.sengine.engine.core

/** Screen anchors for UI elements. The UI canvas is 10 units tall; its width follows the screen aspect. */
val UI_ANCHORS = listOf("Center", "Top", "Bottom", "Left", "Right", "Top Left", "Top Right", "Bottom Left", "Bottom Right")

/** Offset of an anchor point on a UI canvas of half-size [hw] x [hh]. */
fun anchorOffset(anchor: Int, hw: Float, hh: Float): Pair<Float, Float> = when (anchor) {
    1 -> 0f to hh
    2 -> 0f to -hh
    3 -> -hw to 0f
    4 -> hw to 0f
    5 -> -hw to hh
    6 -> hw to hh
    7 -> -hw to -hh
    8 -> hw to -hh
    else -> 0f to 0f
}

/** Rounded panel / image for menus and HUDs (always screen space). */
class UIPanel : Component() {
    override val type = "UIPanel"
    var width = 4f
    var height = 3f
    var color = 0xCC1E2230.toInt()
    var texture = ""
    var corner = 0.3f
    var borderColor = 0x55FFFFFF
    var border = 0.04f
    var anchor = 0

    override fun props() = listOf(
        Prop.F("Width", { width }, { width = it.coerceAtLeast(0.01f) }),
        Prop.F("Height", { height }, { height = it.coerceAtLeast(0.01f) }),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Asset("Image", AssetKind.TEXTURE, { texture }, { texture = it }),
        Prop.F("Corner Radius", { corner }, { corner = it.coerceAtLeast(0f) }, 0.05f),
        Prop.Color("Border Color", { borderColor }, { borderColor = it }),
        Prop.F("Border", { border }, { border = it.coerceAtLeast(0f) }, 0.01f),
        Prop.Choice("Anchor", UI_ANCHORS, { anchor }, { anchor = it }),
    )
}

/**
 * Tappable button. Action examples:
 *   "scene:Level1"   load a scene
 *   "call:startGame" call startGame(buttonName) on every script that defines it
 *   "reload" / "quit" / "resume" / "pause"
 * Every click also calls onUIClick(name) on all scripts.
 */
class UIButton : Component() {
    override val type = "UIButton"
    var text = "Button"
    var width = 3f
    var height = 0.9f
    var textSize = 0.4f
    var color = 0xFF4C6FFF.toInt()
    var pressedColor = 0xFF3450C8.toInt()
    var textColor = 0xFFFFFFFF.toInt()
    var texture = ""
    var corner = 0.45f
    var anchor = 0
    var action = ""
    var sound = ""
    var interactable = true

    // runtime
    var pressed = false
    var hover = false
    var clicks = 0
    var pointer = -1

    override fun resetRuntime() { pressed = false; clicks = 0; pointer = -1 }

    override fun props() = listOf(
        Prop.S("Text", { text }, { text = it }),
        Prop.F("Width", { width }, { width = it.coerceAtLeast(0.01f) }),
        Prop.F("Height", { height }, { height = it.coerceAtLeast(0.01f) }),
        Prop.F("Text Size", { textSize }, { textSize = it.coerceAtLeast(0.05f) }, 0.05f),
        Prop.Color("Color", { color }, { color = it }),
        Prop.Color("Pressed Color", { pressedColor }, { pressedColor = it }),
        Prop.Color("Text Color", { textColor }, { textColor = it }),
        Prop.Asset("Image", AssetKind.TEXTURE, { texture }, { texture = it }),
        Prop.F("Corner Radius", { corner }, { corner = it.coerceAtLeast(0f) }, 0.05f),
        Prop.Choice("Anchor", UI_ANCHORS, { anchor }, { anchor = it }),
        Prop.S("Action", { action }, { action = it }),
        Prop.Asset("Click Sound", AssetKind.SOUND, { sound }, { sound = it }),
        Prop.B("Interactable", { interactable }, { interactable = it }),
    )
}

/** Health / loading bar. */
class UIProgress : Component() {
    override val type = "UIProgress"
    var width = 4f
    var height = 0.35f
    var value = 1f
    var fillColor = 0xFF57D16A.toInt()
    var backColor = 0x99000000.toInt()
    var corner = 0.5f
    var anchor = 0
    var vertical = false

    override fun props() = listOf(
        Prop.F("Width", { width }, { width = it.coerceAtLeast(0.01f) }),
        Prop.F("Height", { height }, { height = it.coerceAtLeast(0.01f) }),
        Prop.F("Value", { value }, { value = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.Color("Fill Color", { fillColor }, { fillColor = it }),
        Prop.Color("Back Color", { backColor }, { backColor = it }),
        Prop.F("Corner Radius", { corner }, { corner = it.coerceAtLeast(0f) }, 0.05f),
        Prop.Choice("Anchor", UI_ANCHORS, { anchor }, { anchor = it }),
        Prop.B("Vertical", { vertical }, { vertical = it }),
    )
}

/** Block world (voxel terrain) with procedural generation, meshing, collision and editing. */
class VoxelWorld : Component() {
    override val type = "VoxelWorld"
    var chunksX = 6
    var chunksZ = 6
    var height = 48
    var seed = 1337
    var waterLevel = 14
    var terrainHeight = 22f
    var roughness = 1f
    var trees = true
    var texture = ""

    // runtime (built by the engine)
    @Transient var data: com.sengine.engine.voxel.VoxelData? = null

    @Transient var dataKey = ""
    fun genKey() = "$chunksX,$chunksZ,$height,$seed,$waterLevel,$terrainHeight,$roughness,$trees"

    override fun resetRuntime() { data = null }

    override fun props() = listOf(
        Prop.I("Chunks X", { chunksX }, { chunksX = it.coerceIn(1, 32) }),
        Prop.I("Chunks Z", { chunksZ }, { chunksZ = it.coerceIn(1, 32) }),
        Prop.I("Height", { height }, { height = it.coerceIn(8, 128) }),
        Prop.I("Seed", { seed }, { seed = it }),
        Prop.I("Water Level", { waterLevel }, { waterLevel = it.coerceAtLeast(0) }),
        Prop.F("Terrain Height", { terrainHeight }, { terrainHeight = it.coerceAtLeast(1f) }, 0.5f),
        Prop.F("Roughness", { roughness }, { roughness = it.coerceIn(0.1f, 4f) }, 0.05f),
        Prop.B("Trees", { trees }, { trees = it }),
        Prop.Asset("Block Atlas", AssetKind.TEXTURE, { texture }, { texture = it }),
    )
}
