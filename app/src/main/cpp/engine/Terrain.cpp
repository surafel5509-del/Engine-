#include "Terrain.h"

#include <algorithm>
#include <cmath>

namespace sengine {

static inline uint32_t hash2(int x, int y, int seed) {
    uint32_t h = (uint32_t) x * 374761393u + (uint32_t) y * 668265263u + (uint32_t) seed * 2246822519u;
    h = (h ^ (h >> 13)) * 1274126177u;
    return h ^ (h >> 16);
}

static inline float grad(int ix, int iy, int seed, float dx, float dy) {
    uint32_t h = hash2(ix, iy, seed) & 7u;
    static const float GX[8] = {1, -1, 0, 0, 0.7071f, -0.7071f, 0.7071f, -0.7071f};
    static const float GY[8] = {0, 0, 1, -1, 0.7071f, 0.7071f, -0.7071f, -0.7071f};
    return GX[h] * dx + GY[h] * dy;
}

static inline float fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }

float perlin2(float x, float y, int seed) {
    int x0 = (int) std::floor(x), y0 = (int) std::floor(y);
    float fx = x - (float) x0, fy = y - (float) y0;
    float u = fade(fx), v = fade(fy);
    float a = grad(x0, y0, seed, fx, fy);
    float b = grad(x0 + 1, y0, seed, fx - 1, fy);
    float c = grad(x0, y0 + 1, seed, fx, fy - 1);
    float d = grad(x0 + 1, y0 + 1, seed, fx - 1, fy - 1);
    float r = (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v;
    return std::max(-1.0f, std::min(1.0f, r * 1.414f));
}

namespace {

struct Rng {
    uint64_t s;
    explicit Rng(uint64_t seed) : s(seed * 6364136223846793005ull + 1442695040888963407ull) {}
    float next() {
        s ^= s << 13; s ^= s >> 7; s ^= s << 17;
        return (float) ((s >> 40) & 0xFFFFFF) / 16777216.0f;
    }
};

/** Height + gradient at a fractional grid position (bilinear), used by the erosion droplets. */
inline void heightGrad(const std::vector<float>& h, int n, float x, float y, float& height, float& gx, float& gy) {
    int ix = (int) x, iy = (int) y;
    float u = x - (float) ix, v = y - (float) iy;
    size_t i = (size_t) iy * n + ix;
    float nw = h[i], ne = h[i + 1], sw = h[i + n], se = h[i + n + 1];
    gx = (ne - nw) * (1 - v) + (se - sw) * v;
    gy = (sw - nw) * (1 - u) + (se - ne) * u;
    height = nw * (1 - u) * (1 - v) + ne * u * (1 - v) + sw * (1 - u) * v + se * u * v;
}

/** Classic droplet hydraulic erosion on a normalised (0..1) heightmap. */
void erode(std::vector<float>& h, int n, int droplets, int seed) {
    const float inertia = 0.05f, capacityK = 4.0f, minCapacity = 0.01f, depositK = 0.3f, erodeK = 0.3f, evaporate = 0.01f, gravity = 4.0f;
    const int radius = 2, maxSteps = 48;
    // precomputed erosion brush
    std::vector<int> bx, by;
    std::vector<float> bw;
    float wsum = 0;
    for (int y = -radius; y <= radius; y++)
        for (int x = -radius; x <= radius; x++) {
            float d = std::sqrt((float) (x * x + y * y));
            if (d > radius) continue;
            float w = 1 - d / (float) radius;
            bx.push_back(x); by.push_back(y); bw.push_back(w);
            wsum += w;
        }
    for (float& w : bw) w /= wsum;
    Rng rng((uint64_t) seed + 99);
    for (int it = 0; it < droplets; it++) {
        float x = rng.next() * (float) (n - 2), y = rng.next() * (float) (n - 2);
        float dx = 0, dy = 0, speed = 1, water = 1, sediment = 0;
        for (int step = 0; step < maxSteps; step++) {
            int ix = (int) x, iy = (int) y;
            float u = x - (float) ix, v = y - (float) iy;
            float hOld, gx, gy;
            heightGrad(h, n, x, y, hOld, gx, gy);
            dx = dx * inertia - gx * (1 - inertia);
            dy = dy * inertia - gy * (1 - inertia);
            float len = std::sqrt(dx * dx + dy * dy);
            if (len < 1e-7f) break;
            dx /= len; dy /= len;
            x += dx; y += dy;
            if (x < 0 || y < 0 || x >= (float) (n - 2) || y >= (float) (n - 2)) break;
            float hNew, g2x, g2y;
            heightGrad(h, n, x, y, hNew, g2x, g2y);
            float dh = hNew - hOld;
            float capacity = std::max(-dh * speed * water * capacityK, minCapacity);
            size_t i = (size_t) iy * n + ix;
            if (sediment > capacity || dh > 0) {
                float amount = dh > 0 ? std::min(dh, sediment) : (sediment - capacity) * depositK;
                sediment -= amount;
                h[i] += amount * (1 - u) * (1 - v);
                h[i + 1] += amount * u * (1 - v);
                h[i + n] += amount * (1 - u) * v;
                h[i + n + 1] += amount * u * v;
            } else {
                float amount = std::min((capacity - sediment) * erodeK, -dh);
                for (size_t k = 0; k < bw.size(); k++) {
                    int px = ix + bx[k], py = iy + by[k];
                    if (px < 0 || py < 0 || px >= n || py >= n) continue;
                    float& cell = h[(size_t) py * n + px];
                    float d = std::min(cell, amount * bw[k]);
                    cell -= d;
                    sediment += d;
                }
            }
            speed = std::sqrt(std::max(0.0f, speed * speed + dh * gravity * -1.0f));
            water *= 1 - evaporate;
        }
    }
}

}  // namespace

void generateHeightmap(const TerrainParams& pIn, std::vector<float>& out) {
    TerrainParams p = pIn;
    p.resolution = std::max(2, std::min(p.resolution, 1025));
    int n = p.resolution;
    out.assign((size_t) n * n, 0.0f);
    float lo = 1e9f, hi = -1e9f;
    for (int y = 0; y < n; y++)
        for (int x = 0; x < n; x++) {
            float fx = (float) x / (float) (n - 1), fy = (float) y / (float) (n - 1);
            float amp = 1, freq = p.frequency, sum = 0, norm = 0;
            for (int o = 0; o < std::max(1, p.octaves); o++) {
                float nv = perlin2(fx * freq + 17.3f * (float) o, fy * freq - 9.1f * (float) o, p.seed + o * 131);
                float ridged = 1 - std::fabs(nv);
                ridged = ridged * ridged * 2 - 1;
                sum += amp * (nv * (1 - p.ridge) + ridged * p.ridge);
                norm += amp;
                amp *= p.persistence;
                freq *= p.lacunarity;
            }
            float v = sum / norm;
            out[(size_t) y * n + x] = v;
            lo = std::min(lo, v);
            hi = std::max(hi, v);
        }
    float range = std::max(1e-6f, hi - lo);
    for (int y = 0; y < n; y++)
        for (int x = 0; x < n; x++) {
            float& v = out[(size_t) y * n + x];
            v = (v - lo) / range;
            if (p.falloff > 0) {
                float cx = (float) x / (float) (n - 1) * 2 - 1, cy = (float) y / (float) (n - 1) * 2 - 1;
                float d = std::min(1.0f, std::sqrt(cx * cx + cy * cy));
                float f = 1 - std::pow(d, 3.0f) * (3 - 2 * d) * 0.5f * 2;  // smooth edge sink
                v = v * (1 - p.falloff) + v * std::max(0.0f, f) * p.falloff;
            }
        }
    if (p.erosion > 0 && n >= 8) erode(out, n, std::min(p.erosion, 500000), p.seed);
    for (float& v : out) {
        v = std::max(0.0f, std::min(1.0f, v));
        if (p.terraces >= 1) {
            float t = v * p.terraces;
            float base = std::floor(t);
            float f = t - base;
            v = (base + std::pow(f, 4.0f)) / p.terraces;
        }
        if (p.waterLevel > 0 && v < p.waterLevel) v = p.waterLevel - (p.waterLevel - v) * 0.6f;
        v *= p.height;
    }
}

void buildTerrainMesh(const TerrainParams& p, const std::vector<float>& h, std::vector<float>& verts, std::vector<int>& idx) {
    int n = std::max(2, std::min(p.resolution, 1025));
    if ((int) h.size() != n * n) return;
    float step = p.size / (float) (n - 1);
    float half = p.size * 0.5f;
    verts.assign((size_t) n * n * 8, 0.0f);
    for (int y = 0; y < n; y++)
        for (int x = 0; x < n; x++) {
            size_t i = (size_t) y * n + x;
            float* o = &verts[i * 8];
            o[0] = -half + (float) x * step;
            o[1] = h[i];
            o[2] = -half + (float) y * step;
            float hl = h[(size_t) y * n + std::max(0, x - 1)], hr = h[(size_t) y * n + std::min(n - 1, x + 1)];
            float hd = h[(size_t) std::max(0, y - 1) * n + x], hu = h[(size_t) std::min(n - 1, y + 1) * n + x];
            float nx = (hl - hr), nz = (hd - hu), ny = 2 * step;
            float len = std::sqrt(nx * nx + ny * ny + nz * nz);
            o[3] = nx / len; o[4] = ny / len; o[5] = nz / len;
            o[6] = (float) x / (float) (n - 1);
            o[7] = (float) y / (float) (n - 1);
        }
    idx.clear();
    idx.reserve((size_t) (n - 1) * (n - 1) * 6);
    for (int y = 0; y < n - 1; y++)
        for (int x = 0; x < n - 1; x++) {
            int a = y * n + x, b = a + 1, c = a + n, d = c + 1;
            // counter-clockwise when seen from above (+y)
            idx.push_back(a); idx.push_back(c); idx.push_back(b);
            idx.push_back(b); idx.push_back(c); idx.push_back(d);
        }
}

float sampleHeight(const TerrainParams& p, const std::vector<float>& h, float wx, float wz) {
    int n = std::max(2, std::min(p.resolution, 1025));
    if ((int) h.size() != n * n) return 0;
    float gx = (wx / p.size + 0.5f) * (float) (n - 1), gy = (wz / p.size + 0.5f) * (float) (n - 1);
    gx = std::max(0.0f, std::min((float) (n - 1) - 1e-4f, gx));
    gy = std::max(0.0f, std::min((float) (n - 1) - 1e-4f, gy));
    int ix = (int) gx, iy = (int) gy;
    float u = gx - (float) ix, v = gy - (float) iy;
    int ix1 = std::min(n - 1, ix + 1), iy1 = std::min(n - 1, iy + 1);
    float a = h[(size_t) iy * n + ix], b = h[(size_t) iy * n + ix1], c = h[(size_t) iy1 * n + ix], d = h[(size_t) iy1 * n + ix1];
    return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v;
}

}  // namespace sengine
