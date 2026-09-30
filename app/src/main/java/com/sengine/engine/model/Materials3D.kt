package com.sengine.engine.model

/**
 * v7 3D material library: one-tap physically-based material presets for MeshRenderer.
 * Each preset sets colour + PBR metallic/roughness/emission (and a suggested texture style
 * name from the Texture Studio / Asset Library when one exists).
 */
object Materials3D {
    class Preset(
        val name: String,
        val color: Long,
        val metallic: Float,
        val roughness: Float,
        val emission: Float = 0f,
        val texture: String = "",
        val desc: String = "",
    )

    val PRESETS = listOf(
        Preset("Gold", 0xFFFFC94D, 1f, 0.22f, desc = "shiny metal"),
        Preset("Silver", 0xFFD9DEE6, 1f, 0.18f),
        Preset("Copper", 0xFFC97C4E, 1f, 0.3f),
        Preset("Chrome", 0xFFF2F5F8, 1f, 0.08f),
        Preset("Iron", 0xFF8E979E, 1f, 0.5f),
        Preset("Rust", 0xFF9C5A32, 0f, 0.85f, texture = "Rust"),
        Preset("Steel Painted", 0xFF3E6C9E, 0.2f, 0.45f),
        Preset("Plastic Red", 0xFFE23B3B, 0f, 0.35f),
        Preset("Plastic Blue", 0xFF2E6FE2, 0f, 0.35f),
        Preset("Rubber", 0xFF23262A, 0f, 0.95f),
        Preset("Wood", 0xFF9C6B3F, 0f, 0.7f, texture = "Wood"),
        Preset("Dark Wood", 0xFF5C3A21, 0f, 0.72f, texture = "Wood"),
        Preset("Marble", 0xFFEDEAE4, 0.05f, 0.2f, texture = "Marble"),
        Preset("Concrete", 0xFF9E9E96, 0f, 0.9f, texture = "Concrete"),
        Preset("Brick", 0xFFA0522D, 0f, 0.85f, texture = "Bricks"),
        Preset("Sand", 0xFFD9BC83, 0f, 1f, texture = "Desert Sand"),
        Preset("Grass", 0xFF57A64A, 0f, 0.95f, texture = "Grass Field"),
        Preset("Snow", 0xFFF4F9FF, 0f, 0.6f, texture = "Snow"),
        Preset("Ice", 0xFFBFE9FF, 0.1f, 0.12f),
        Preset("Glass", 0xFFCFEFFA, 0.1f, 0.05f),
        Preset("Water", 0xFF3E9EE2, 0.1f, 0.08f),
        Preset("Lava", 0xFFFF5A26, 0f, 0.8f, emission = 1.4f, texture = "Lava"),
        Preset("Neon Pink", 0xFFFF2E88, 0f, 0.4f, emission = 2f),
        Preset("Neon Cyan", 0xFF22D3EE, 0f, 0.4f, emission = 2f),
        Preset("Neon Lime", 0xFFB4F82C, 0f, 0.4f, emission = 1.6f),
        Preset("Hologram", 0xFF7CD8FF, 0.2f, 0.1f, emission = 1.2f),
        Preset("Obsidian", 0xFF14101C, 0.3f, 0.15f),
        Preset("Chalk", 0xFFF2EFE6, 0f, 1f),
        Preset("Fabric", 0xFF6E7FA3, 0f, 1f, texture = "Fabric"),
        Preset("Leather", 0xFF6B3F23, 0.05f, 0.65f),
        Preset("Carbon", 0xFF1A1D22, 0.4f, 0.35f),
        Preset("Emerald", 0xFF18A558, 0.2f, 0.1f),
        Preset("Ruby", 0xFFB3123C, 0.2f, 0.1f),
        Preset("Sapphire", 0xFF1546C8, 0.2f, 0.1f),
        Preset("Amethyst", 0xFF8A4FD3, 0.2f, 0.12f),
        Preset("Crystal", 0xFF7C4DFF, 0.1f, 0.08f, emission = 0.5f),
        Preset("Skin", 0xFFE8B08E, 0f, 0.6f),
        Preset("Fur", 0xFF7B5B3A, 0f, 1f),
        Preset("Scale", 0xFF3F7D6A, 0.15f, 0.45f),
        Preset("Bone", 0xFFE6DECd, 0f, 0.7f),
    )

    /** Applies a preset to a MeshRenderer (PBR on). Returns false when the name is unknown. */
    fun apply(name: String, mr: com.sengine.engine.core.MeshRenderer): Boolean {
        val p = PRESETS.firstOrNull { it.name.equals(name, true) } ?: return false
        mr.color = p.color.toInt()
        mr.pbr = true
        mr.metallic = p.metallic
        mr.roughness = p.roughness
        mr.emission = p.emission
        if (p.texture.isNotBlank()) mr.texture = "${p.texture}.png"
        return true
    }
}
