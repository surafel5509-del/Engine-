package com.sengine.engine.core

/**
 * v7 components: character controller, 3D joints, ragdolls, 2D joints and the new game-UI
 * widgets (slider, toggle, radar minimap). All serialize through the standard [Prop] system.
 */

/** Unity-style character controller for 3D: WASD/stick movement with grounded jumping, slopes and stepping. */
class CharacterController3D : Component() {
    override val type = "CharacterController3D"
    var speed = 5f
    var jump = 6f
    var rotateToMove = true
    var airControl = 0.4f
    var slopeLimit = 45f
    var stepOffset = 0.35f
    var radius = 0.4f
    var height = 1.7f

    // runtime
    var grounded = false
    var moving = false
    var yaw = 0f

    override fun resetRuntime() { grounded = false; moving = false }

    override fun props() = listOf(
        Prop.F("Speed", { speed }, { speed = it.coerceAtLeast(0f) }),
        Prop.F("Jump Speed", { jump }, { jump = it.coerceAtLeast(0f) }),
        Prop.B("Rotate To Move", { rotateToMove }, { rotateToMove = it }),
        Prop.F("Air Control", { airControl }, { airControl = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Slope Limit", { slopeLimit }, { slopeLimit = it.coerceIn(5f, 80f) }, 1f),
        Prop.F("Step Offset", { stepOffset }, { stepOffset = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Radius", { radius }, { radius = it.coerceAtLeast(0.05f) }, 0.05f),
        Prop.F("Height", { height }, { height = it.coerceAtLeast(0.2f) }, 0.1f),
    )
}

/**
 * Constraint between this object's Rigidbody3D and another body (by name).
 * Kinds: Fixed (weld), Spring (keeps a distance with a springy pull), Hinge (rotates around an axis).
 */
class Joint3D : Component() {
    override val type = "Joint3D"
    var kind = 0 // 0 Fixed, 1 Spring, 2 Hinge
    var target = ""
    var breakForce = 0f // 0 = never
    var springFrequency = 4f
    var springDamping = 0.5f
    var targetDistance = 1.5f
    var axisX = 0f; var axisY = 1f; var axisZ = 0f // hinge axis (local)
    var motorSpeed = 0f // degrees/sec around the axis
    var motorTorque = 0f

    // runtime
    var broken = false
    var angle = 0f

    override fun resetRuntime() { broken = false; angle = 0f }

    override fun props() = listOf(
        Prop.Choice("Kind", listOf("Fixed", "Spring", "Hinge"), { kind }, { kind = it }),
        Prop.S("Connected To", { target }, { target = it }),
        Prop.F("Break Force", { breakForce }, { breakForce = it.coerceAtLeast(0f) }, 1f),
        Prop.F("Spring Frequency", { springFrequency }, { springFrequency = it.coerceIn(0.1f, 30f) }, 0.5f),
        Prop.F("Spring Damping", { springDamping }, { springDamping = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Target Distance", { targetDistance }, { targetDistance = it.coerceAtLeast(0.01f) }, 0.1f),
        Prop.F("Axis X", { axisX }, { axisX = it }, 0.1f),
        Prop.F("Axis Y", { axisY }, { axisY = it }, 0.1f),
        Prop.F("Axis Z", { axisZ }, { axisZ = it }, 0.1f),
        Prop.F("Motor Speed", { motorSpeed }, { motorSpeed = it }, 10f),
        Prop.F("Motor Torque", { motorTorque }, { motorTorque = it.coerceAtLeast(0f) }, 1f),
    )
}

/**
 * Ragdoll generator: at play start (or when triggered by a script via `self.ragdoll()`), the
 * object's rigged model is split into physics bodies chained with joints and given an impulse —
 * instant believable knock-downs for characters, zombies and animals.
 */
class Ragdoll : Component() {
    override val type = "Ragdoll"
    var trigger = 0 // 0 On message "ragdoll", 1 On start
    var strength = 5f // initial impulse magnitude
    var upBias = 0.6f
    var lifetime = 0f // 0 = forever
    var useModelRig = true

    override fun props() = listOf(
        Prop.Choice("Trigger", listOf("Script message", "On start"), { trigger }, { trigger = it }),
        Prop.F("Impulse", { strength }, { strength = it.coerceAtLeast(0f) }, 0.5f),
        Prop.F("Up Bias", { upBias }, { upBias = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.F("Lifetime", { lifetime }, { lifetime = it.coerceAtLeast(0f) }, 0.5f),
        Prop.B("Use Model Rig", { useModelRig }, { useModelRig = it }),
    )
}

/** Keeps two 2D bodies at a fixed distance (rope / pendulum). */
class DistanceJoint2D : Component() {
    override val type = "DistanceJoint2D"
    var target = ""
    var distance = 2f
    var stiffness = 0.5f

    override fun props() = listOf(
        Prop.S("Connected To", { target }, { target = it }),
        Prop.F("Distance", { distance }, { distance = it.coerceAtLeast(0.01f) }, 0.1f),
        Prop.F("Stiffness", { stiffness }, { stiffness = it.coerceIn(0.01f, 1f) }, 0.05f),
    )
}

/** Pins a 2D body to another (or to the world) around a pivot — wheels, levers, swings, ragdolls. */
class RevoluteJoint2D : Component() {
    override val type = "RevoluteJoint2D"
    var target = "" // empty = pinned to the world
    var pivotX = 0f
    var pivotY = 0f
    var motorSpeed = 0f // degrees/sec
    var maxTorque = 0f  // 0 = free spin

    override fun props() = listOf(
        Prop.S("Connected To", { target }, { target = it }),
        Prop.F("Pivot X", { pivotX }, { pivotX = it }, 0.05f),
        Prop.F("Pivot Y", { pivotY }, { pivotY = it }, 0.05f),
        Prop.F("Motor Speed", { motorSpeed }, { motorSpeed = it }, 10f),
        Prop.F("Max Torque", { maxTorque }, { maxTorque = it.coerceAtLeast(0f) }, 1f),
    )
}

/** Draggable slider for settings / volume / health. Scripts receive `onSlider(name, value)`. */
class UISlider : Component() {
    override val type = "UISlider"
    var width = 4f
    var height = 0.4f
    var value = 0.7f
    var fillColor = 0xFF4C8DFF.toInt()
    var backColor = 0x99000000.toInt()
    var knobColor = 0xFFFFFFFF.toInt()
    var anchor = 0
    var action = "" // e.g. "call:onVolume"

    // runtime
    var pointer = -1
    var dragging = false

    override fun resetRuntime() { pointer = -1; dragging = false }

    override fun props() = listOf(
        Prop.F("Width", { width }, { width = it.coerceAtLeast(0.05f) }),
        Prop.F("Height", { height }, { height = it.coerceAtLeast(0.05f) }),
        Prop.F("Value", { value }, { value = it.coerceIn(0f, 1f) }, 0.05f),
        Prop.Color("Fill Color", { fillColor }, { fillColor = it }),
        Prop.Color("Back Color", { backColor }, { backColor = it }),
        Prop.Color("Knob Color", { knobColor }, { knobColor = it }),
        Prop.Choice("Anchor", UI_ANCHORS, { anchor }, { anchor = it }),
        Prop.S("Action", { action }, { action = it }),
    )
}

/** Boolean checkbox / switch. Scripts receive `onToggle(name, checked)`; optional button-style action. */
class UIToggle : Component() {
    override val type = "UIToggle"
    var width = 0.55f
    var style = 0 // 0 checkbox, 1 switch
    var checked = false
    var onColor = 0xFF57D16A.toInt()
    var offColor = 0x66FFFFFF
    var anchor = 0
    var action = ""

    // runtime
    var pressed = false
    var pointer = -1

    override fun resetRuntime() { pressed = false; pointer = -1 }

    override fun props() = listOf(
        Prop.F("Size", { width }, { width = it.coerceAtLeast(0.1f) }, 0.05f),
        Prop.Choice("Style", listOf("Checkbox", "Switch"), { style }, { style = it }),
        Prop.B("Checked", { checked }, { checked = it }),
        Prop.Color("On Color", { onColor }, { onColor = it }),
        Prop.Color("Off Color", { offColor }, { offColor = it }),
        Prop.Choice("Anchor", UI_ANCHORS, { anchor }, { anchor = it }),
        Prop.S("Action", { action }, { action = it }),
    )
}

/**
 * Minimap radar: plots live objects by tag around a centre point on a circular or square frame.
 * Set `track` to a tag (or comma-separated tags) and `range` to the world radius shown.
 */
class UIRadar : Component() {
    override val type = "UIRadar"
    var size = 2.2f
    var track = "Enemy,Pickup,Player"
    var range = 40f
    var circle = true
    var backColor = 0xAA0A0F1E.toInt()
    var ringColor = 0x664C8DFF.toInt()
    var dotColor = 0xFFFF5C6C.toInt()
    var selfColor = 0xFF57D16A.toInt()
    var anchor = 8 // bottom-right by default
    var rotate = true // rotate the map with the camera's 3D yaw

    override fun props() = listOf(
        Prop.F("Size", { size }, { size = it.coerceAtLeast(0.2f) }, 0.1f),
        Prop.S("Track Tags", { track }, { track = it }),
        Prop.F("Range", { range }, { range = it.coerceAtLeast(1f) }, 1f),
        Prop.B("Circle", { circle }, { circle = it }),
        Prop.Color("Back Color", { backColor }, { backColor = it }),
        Prop.Color("Ring Color", { ringColor }, { ringColor = it }),
        Prop.Color("Dot Color", { dotColor }, { dotColor = it }),
        Prop.Color("Self Color", { selfColor }, { selfColor = it }),
        Prop.Choice("Anchor", UI_ANCHORS, { anchor }, { anchor = it }),
        Prop.B("Rotate With Camera", { rotate }, { rotate = it }),
    )
}
