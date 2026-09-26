// S Engine — native Landscape system: procedural heightmaps (fBm + ridged noise), island falloff,
// terraces and droplet-based hydraulic erosion, plus mesh building with smooth normals.
#pragma once

#include <cstdint>
#include <vector>

namespace sengine {

struct TerrainParams {
    int resolution = 129;     // vertices per side
    float size = 128;         // world units per side
    float height = 20;        // max height
    int seed = 1;
    int octaves = 6;
    float frequency = 2.5f;   // features per side
    float persistence = 0.5f;
    float lacunarity = 2.0f;
    float ridge = 0.35f;      // 0 = rolling hills, 1 = sharp mountain ridges
    float falloff = 0.0f;     // 0 = none, 1 = island (edges sink)
    float terraces = 0.0f;    // 0 = off, else number of terrace steps
    int erosion = 0;          // number of simulated rain droplets
    float waterLevel = 0.0f;  // 0..1, heights below are flattened slightly (beaches)
};

/** Fills [out] with resolution² heights in world units (row-major, z rows then x). Deterministic for a seed. */
void generateHeightmap(const TerrainParams& p, std::vector<float>& out);

/** Interleaved vertices (px,py,pz, nx,ny,nz, u,v) centred on the origin + triangle indices. */
void buildTerrainMesh(const TerrainParams& p, const std::vector<float>& heights, std::vector<float>& verts, std::vector<int>& indices);

/** Bilinear height at world (x, z) relative to the terrain centre. */
float sampleHeight(const TerrainParams& p, const std::vector<float>& heights, float x, float z);

/** Seamless 2D gradient noise in [-1, 1]. */
float perlin2(float x, float y, int seed);

}  // namespace sengine
