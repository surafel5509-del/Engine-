// S Engine — native 3D Model & Animation Studio kernel (see ModelKit.h).
#include "ModelKit.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <functional>
#include <map>
#include <stdexcept>
#include <unordered_map>

namespace sengine {

void PolyMesh::normalize() {
    color.resize(f.size(), 0);
    group.resize(f.size(), 0);
    uv.resize(f.size());
    for (size_t i = 0; i < f.size(); i++)
        if (!uv[i].empty() && uv[i].size() != f[i].size() * 2) uv[i].clear();
}

namespace modelkit {
namespace {

const float kPi = 3.14159265358979f;

struct V3 {
    float x = 0, y = 0, z = 0;
    V3() = default;
    V3(float a, float b, float c) : x(a), y(b), z(c) {}
    V3 operator+(const V3& o) const { return {x + o.x, y + o.y, z + o.z}; }
    V3 operator-(const V3& o) const { return {x - o.x, y - o.y, z - o.z}; }
    V3 operator*(float s) const { return {x * s, y * s, z * s}; }
    float dot(const V3& o) const { return x * o.x + y * o.y + z * o.z; }
    V3 cross(const V3& o) const { return {y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x}; }
    float len() const { return std::sqrt(x * x + y * y + z * z); }
    V3 norm() const { float l = len(); return l > 1e-12f ? V3(x / l, y / l, z / l) : V3(0, 0, 0); }
};

V3 vert(const PolyMesh& m, int i) { return {m.v[i * 3], m.v[i * 3 + 1], m.v[i * 3 + 2]}; }
int addV(PolyMesh& m, const V3& p) { return m.addVertex(p.x, p.y, p.z); }

uint64_t edgeKey(int a, int b) { return a < b ? ((uint64_t) a << 32) | (uint32_t) b : ((uint64_t) b << 32) | (uint32_t) a; }

V3 centroid(const PolyMesh& m, int face) {
    V3 c;
    for (int i : m.f[face]) c = c + vert(m, i);
    return c * (1.0f / std::max<size_t>(1, m.f[face].size()));
}

V3 normalOf(const PolyMesh& m, int face) {
    float n[3];
    faceNormal(m, face, n);
    return {n[0], n[1], n[2]};
}

void checkFace(const PolyMesh& m, int face) {
    if (face < 0 || face >= (int) m.f.size()) throw std::runtime_error("face index out of range");
}

/** Removes faces (indices sorted descending) keeping attribute arrays in sync. */
void eraseFaces(PolyMesh& m, std::vector<int> faces) {
    std::sort(faces.rbegin(), faces.rend());
    faces.erase(std::unique(faces.begin(), faces.end()), faces.end());
    for (int fi : faces) {
        m.f.erase(m.f.begin() + fi);
        m.color.erase(m.color.begin() + fi);
        m.group.erase(m.group.begin() + fi);
        m.uv.erase(m.uv.begin() + fi);
    }
}

std::unordered_map<uint64_t, std::vector<int>> edgeFaces(const PolyMesh& m) {
    std::unordered_map<uint64_t, std::vector<int>> map;
    for (int fi = 0; fi < (int) m.f.size(); fi++) {
        const auto& f = m.f[fi];
        for (size_t k = 0; k < f.size(); k++) map[edgeKey(f[k], f[(k + 1) % f.size()])].push_back(fi);
    }
    return map;
}

}  // namespace

void faceNormal(const PolyMesh& m, int face, float out[3]) {
    const auto& f = m.f[face];
    float nx = 0, ny = 0, nz = 0;
    for (size_t k = 0; k < f.size(); k++) {
        V3 a = vert(m, f[k]), b = vert(m, f[(k + 1) % f.size()]);
        nx += (a.y - b.y) * (a.z + b.z);
        ny += (a.z - b.z) * (a.x + b.x);
        nz += (a.x - b.x) * (a.y + b.y);
    }
    float l = std::sqrt(nx * nx + ny * ny + nz * nz);
    if (l < 1e-12f) l = 1;
    out[0] = nx / l; out[1] = ny / l; out[2] = nz / l;
}

// ------------------------------------------------------------------------------------------------ bevel
std::vector<int> bevelFaces(PolyMesh& m, const std::vector<int>& faces, float width, float depth, int segments) {
    m.normalize();
    segments = std::max(1, std::min(segments, 12));
    width = std::max(0.0f, std::min(width, 0.95f));
    std::vector<int> caps;
    std::vector<int> sorted(faces);
    std::sort(sorted.begin(), sorted.end());
    sorted.erase(std::unique(sorted.begin(), sorted.end()), sorted.end());
    for (int fi : sorted) checkFace(m, fi);
    for (int fi : sorted) {
        const std::vector<int> outer = m.f[fi];
        const int n = (int) outer.size();
        if (n < 3) continue;
        V3 c = centroid(m, fi), nrm = normalOf(m, fi);
        int col = m.color[fi], grp = m.group[fi];
        std::vector<int> prev = outer;
        for (int s = 1; s <= segments; s++) {
            float a = (kPi / 2) * s / segments;
            float inset = width * (1 - std::cos(a));   // fraction towards the centre
            float up = depth * std::sin(a);
            if (depth == 0) inset = width * s / segments;
            std::vector<int> ring(n);
            for (int k = 0; k < n; k++) {
                V3 p = vert(m, outer[k]);
                ring[k] = addV(m, p + (c - p) * inset + nrm * up);
            }
            for (int k = 0; k < n; k++) {
                int k1 = (k + 1) % n;
                m.addFace({prev[k], prev[k1], ring[k1], ring[k]}, col, grp);
            }
            prev = ring;
        }
        m.f[fi] = prev;   // the cap reuses the original face slot (keeps colour / group)
        m.uv[fi].clear();
        caps.push_back(fi);
    }
    return caps;
}

// ------------------------------------------------------------------------------------------------ loop cut
int insertEdgeLoop(PolyMesh& m, int a, int b, float t) {
    m.normalize();
    t = std::max(0.02f, std::min(0.98f, t));
    auto ef = edgeFaces(m);
    auto it = ef.find(edgeKey(a, b));
    if (it == ef.end()) throw std::runtime_error("the two vertices are not connected by an edge");
    std::unordered_map<uint64_t, int> mids;   // edge -> new vertex
    std::vector<char> done(m.f.size(), 0);
    std::vector<std::pair<int, std::vector<int>>> replace;   // face -> first half (second half appended)
    std::vector<std::vector<int>> extra;
    std::vector<int> extraCol, extraGrp;
    std::vector<std::pair<int, std::vector<int>>> ngonEdits;
    int cut = 0;

    auto mid = [&](int p, int q, float tt) {
        uint64_t k = edgeKey(p, q);
        auto f = mids.find(k);
        if (f != mids.end()) return f->second;
        V3 P = vert(m, p), Q = vert(m, q);
        int id = addV(m, P + (Q - P) * tt);
        mids[k] = id;
        return id;
    };
    auto posIn = [](const std::vector<int>& f, int p, int q) {   // index i with f[i]=p, f[i+1]=q, else -1
        for (size_t i = 0; i < f.size(); i++) if (f[i] == p && f[(i + 1) % f.size()] == q) return (int) i;
        return -1;
    };
    // pending edits of faces (applied at the end so indices stay valid)
    std::map<int, std::vector<int>> newFace;   // face -> replacement verts

    // walks from face fi entering through the directed edge (p -> q) (p,q consecutive in fi), with parameter tt from p
    std::function<void(int, int, int, float)> walk = [&](int fi, int p, int q, float tt) {
        while (fi >= 0 && !done[fi]) {
            const std::vector<int>& f = newFace.count(fi) ? newFace[fi] : m.f[fi];
            int i = posIn(f, p, q);
            if (i < 0) return;
            if (f.size() != 4) {
                // ring ends at a non-quad: just add the cut vertex into this polygon
                std::vector<int> g(f);
                g.insert(g.begin() + i + 1, mid(p, q, tt));
                newFace[fi] = g;
                done[fi] = 1;
                return;
            }
            done[fi] = 1;
            int A = f[i], B = f[(i + 1) % 4], C = f[(i + 2) % 4], D = f[(i + 3) % 4];
            int mab = mid(A, B, tt), mdc = mid(D, C, tt);
            newFace[fi] = {A, mab, mdc, D};
            extra.push_back({mab, B, C, mdc});
            extraCol.push_back(m.color[fi]);
            extraGrp.push_back(m.group[fi]);
            cut++;
            // next face across edge (D, C) (appears as C -> D there)
            auto e = ef.find(edgeKey(C, D));
            int next = -1;
            if (e != ef.end()) for (int g : e->second) if (g != fi) { next = g; break; }
            fi = next; p = D; q = C;   // keep the parameter measured from the D side
            if (fi >= 0) {
                const std::vector<int>& g = newFace.count(fi) ? newFace[fi] : m.f[fi];
                if (posIn(g, p, q) < 0) { std::swap(p, q); tt = 1 - tt; }
                if (posIn(g, p, q) < 0) return;
            }
        }
    };

    int f0 = it->second[0];
    {
        int p = a, q = b;
        float tt = t;
        if (posIn(m.f[f0], p, q) < 0) { std::swap(p, q); tt = 1 - tt; }
        walk(f0, p, q, tt);
    }
    for (int g : it->second) {
        if (done[g]) continue;
        int p = a, q = b;
        float tt = t;
        if (posIn(m.f[g], p, q) < 0) { std::swap(p, q); tt = 1 - tt; }
        walk(g, p, q, tt);
    }
    for (auto& kv : newFace) { m.f[kv.first] = kv.second; m.uv[kv.first].clear(); }
    for (size_t i = 0; i < extra.size(); i++) m.addFace(extra[i], extraCol[i], extraGrp[i]);
    // non-quad faces not on the ring that share a cut edge also need the new vertex (keeps the mesh watertight)
    for (int fi = 0; fi < (int) m.f.size(); fi++) {
        if (fi < (int) done.size() && done[fi]) continue;
        auto& f = m.f[fi];
        for (size_t k = 0; k < f.size(); k++) {
            int p = f[k], q = f[(k + 1) % f.size()];
            auto e = mids.find(edgeKey(p, q));
            if (e == mids.end()) continue;
            if (std::find(f.begin(), f.end(), e->second) != f.end()) continue;
            f.insert(f.begin() + k + 1, e->second);
            m.uv[fi].clear();
            k++;
        }
    }
    if (cut == 0) throw std::runtime_error("no quads to cut: loop cuts need quad faces");
    return cut;
}

// ------------------------------------------------------------------------------------------------ bridge
std::vector<int> bridgeFaces(PolyMesh& m, int fa, int fb, int segments) {
    m.normalize();
    checkFace(m, fa); checkFace(m, fb);
    if (fa == fb) throw std::runtime_error("select two different faces");
    std::vector<int> A = m.f[fa], B = m.f[fb];
    const int n = (int) A.size();
    if ((int) B.size() != n) throw std::runtime_error("bridge needs two faces with the same number of vertices (" +
                                                      std::to_string(A.size()) + " vs " + std::to_string(B.size()) + ")");
    for (int x : A) if (std::find(B.begin(), B.end(), x) != B.end()) throw std::runtime_error("faces share a vertex");
    segments = std::max(1, std::min(segments, 32));
    // reversed correspondence j(i) = (k - i) mod n, choose k with the shortest total distance
    int bestK = 0;
    float best = 1e30f;
    for (int k = 0; k < n; k++) {
        float d = 0;
        for (int i = 0; i < n; i++) d += (vert(m, A[i]) - vert(m, B[((k - i) % n + n) % n])).len();
        if (d < best) { best = d; bestK = k; }
    }
    auto J = [&](int i) { return B[((bestK - i) % n + n) % n]; };
    int col = m.color[fa], grp = m.group[fa];
    std::vector<int> prev(A);
    std::vector<int> created;
    for (int s = 1; s <= segments; s++) {
        std::vector<int> ring(n);
        if (s == segments) for (int i = 0; i < n; i++) ring[i] = J(i);
        else {
            float t = (float) s / segments;
            for (int i = 0; i < n; i++) { V3 p = vert(m, A[i]), q = vert(m, J(i)); ring[i] = addV(m, p + (q - p) * t); }
        }
        for (int i = 0; i < n; i++) {
            int i1 = (i + 1) % n;
            created.push_back(m.addFace({prev[i], prev[i1], ring[i1], ring[i]}, col, grp));
        }
        prev = ring;
    }
    eraseFaces(m, {fa, fb});
    int removedBefore = 0;
    for (auto& c : created) { removedBefore = (fa < c) + (fb < c); c -= removedBefore; }
    return created;
}

// ------------------------------------------------------------------------------------------------ PolyGroups
int autoPolyGroups(PolyMesh& m, float angleDeg) {
    m.normalize();
    const float cosLimit = std::cos(std::max(0.0f, std::min(180.0f, angleDeg)) * kPi / 180);
    auto ef = edgeFaces(m);
    std::vector<V3> normals(m.f.size());
    for (size_t i = 0; i < m.f.size(); i++) normals[i] = normalOf(m, (int) i);
    std::vector<int> g(m.f.size(), -1);
    int groups = 0;
    std::vector<int> stack;
    for (size_t s = 0; s < m.f.size(); s++) {
        if (g[s] >= 0) continue;
        g[s] = groups;
        stack.assign(1, (int) s);
        while (!stack.empty()) {
            int fi = stack.back();
            stack.pop_back();
            const auto& f = m.f[fi];
            for (size_t k = 0; k < f.size(); k++) {
                for (int o : ef[edgeKey(f[k], f[(k + 1) % f.size()])]) {
                    if (g[o] >= 0) continue;
                    // compare with the seed face too so gently curving surfaces still split into sensible charts
                    if (normals[o].dot(normals[fi]) >= cosLimit && normals[o].dot(normals[s]) >= cosLimit * 0.5f - 0.5f) {
                        g[o] = groups;
                        stack.push_back(o);
                    }
                }
            }
        }
        groups++;
    }
    m.group = g;
    return groups;
}

std::vector<int> selectGroups(const PolyMesh& m, const std::vector<int>& faces) {
    std::vector<char> want;
    for (int fi : faces) {
        if (fi < 0 || fi >= (int) m.group.size()) continue;
        int gid = m.group[fi];
        if (gid < 0) continue;
        if ((int) want.size() <= gid) want.resize(gid + 1, 0);
        want[gid] = 1;
    }
    std::vector<int> out;
    for (int fi = 0; fi < (int) m.f.size() && fi < (int) m.group.size(); fi++) {
        int gid = m.group[fi];
        if (gid >= 0 && gid < (int) want.size() && want[gid]) out.push_back(fi);
    }
    return out;
}

// ------------------------------------------------------------------------------------------------ UVs
void unwrap(PolyMesh& m, int mode, float scale) {
    m.normalize();
    if (scale <= 0) scale = 1;
    float mn[3] = {1e30f, 1e30f, 1e30f}, mx[3] = {-1e30f, -1e30f, -1e30f};
    for (int i = 0; i < m.vertexCount(); i++) for (int k = 0; k < 3; k++) { mn[k] = std::min(mn[k], m.v[i * 3 + k]); mx[k] = std::max(mx[k], m.v[i * 3 + k]); }
    V3 ctr((mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2);
    float ext = std::max({mx[0] - mn[0], mx[1] - mn[1], mx[2] - mn[2], 1e-6f});
    for (size_t fi = 0; fi < m.f.size(); fi++) m.uv[fi].assign(m.f[fi].size() * 2, 0.0f);

    if (mode == UV_SMART) {
        bool hasGroups = false;
        for (int gidx : m.group) if (gidx != 0) { hasGroups = true; break; }
        if (!hasGroups) autoPolyGroups(m, 40);
        struct Chart { std::vector<int> faces; float minU = 1e30f, minV = 1e30f, maxU = -1e30f, maxV = -1e30f, ox = 0, oy = 0; };
        std::map<int, Chart> charts;
        for (int fi = 0; fi < (int) m.f.size(); fi++) charts[m.group[fi]].faces.push_back(fi);
        for (auto& kv : charts) {
            Chart& c = kv.second;
            V3 n;
            for (int fi : c.faces) { float nn[3]; faceNormal(m, fi, nn); n = n + V3(nn[0], nn[1], nn[2]); }
            n = n.norm();
            if (n.len() < 0.5f) n = V3(0, 1, 0);
            V3 up = std::fabs(n.y) > 0.9f ? V3(0, 0, 1) : V3(0, 1, 0);
            V3 U = up.cross(n).norm(), W = n.cross(U).norm();
            for (int fi : c.faces) {
                auto& f = m.f[fi];
                for (size_t k = 0; k < f.size(); k++) {
                    V3 p = vert(m, f[k]);
                    float u = p.dot(U), v = p.dot(W);
                    m.uv[fi][k * 2] = u; m.uv[fi][k * 2 + 1] = v;
                    c.minU = std::min(c.minU, u); c.maxU = std::max(c.maxU, u);
                    c.minV = std::min(c.minV, v); c.maxV = std::max(c.maxV, v);
                }
            }
        }
        // shelf packing, tallest first
        std::vector<Chart*> order;
        float area = 0;
        for (auto& kv : charts) { order.push_back(&kv.second); area += (kv.second.maxU - kv.second.minU) * (kv.second.maxV - kv.second.minV); }
        std::sort(order.begin(), order.end(), [](Chart* a, Chart* b) { return (a->maxV - a->minV) > (b->maxV - b->minV); });
        float pad = ext * 0.02f;
        float side = std::sqrt(std::max(area, 1e-8f)) * 1.25f;
        for (Chart* c : order) side = std::max(side, c->maxU - c->minU + pad * 2);
        float x = pad, y = pad, shelf = 0, usedW = 0;
        for (Chart* c : order) {
            float w = c->maxU - c->minU, h = c->maxV - c->minV;
            if (x + w + pad > side) { x = pad; y += shelf + pad; shelf = 0; }
            c->ox = x - c->minU; c->oy = y - c->minV;
            x += w + pad;
            shelf = std::max(shelf, h);
            usedW = std::max(usedW, x);
        }
        float total = std::max(usedW, y + shelf + pad);
        for (Chart* c : order) for (int fi : c->faces)
            for (size_t k = 0; k < m.f[fi].size(); k++) {
                m.uv[fi][k * 2] = (m.uv[fi][k * 2] + c->ox) / total;
                m.uv[fi][k * 2 + 1] = 1 - (m.uv[fi][k * 2 + 1] + c->oy) / total;
            }
        return;
    }

    for (int fi = 0; fi < (int) m.f.size(); fi++) {
        auto& f = m.f[fi];
        float n[3];
        faceNormal(m, fi, n);
        int ax = std::fabs(n[0]) >= std::fabs(n[1]) && std::fabs(n[0]) >= std::fabs(n[2]) ? 0 : (std::fabs(n[1]) >= std::fabs(n[2]) ? 1 : 2);
        for (size_t k = 0; k < f.size(); k++) {
            V3 p = vert(m, f[k]);
            float u = 0, v = 0;
            switch (mode) {
                case UV_PLANAR: u = (p.x - mn[0]) / ext; v = 1 - (p.y - mn[1]) / ext; break;
                case UV_CYLINDER: {
                    u = std::atan2(p.z - ctr.z, p.x - ctr.x) / (2 * kPi) + 0.5f;
                    v = 1 - (p.y - mn[1]) / std::max(mx[1] - mn[1], 1e-6f);
                    break;
                }
                case UV_SPHERE: {
                    V3 d = (p - ctr).norm();
                    u = std::atan2(d.z, d.x) / (2 * kPi) + 0.5f;
                    v = std::acos(std::max(-1.0f, std::min(1.0f, d.y))) / kPi;
                    break;
                }
                default:   // box: world-space tiling (1 unit = 1 texture repeat at scale 1)
                    if (ax == 0) { u = p.z; v = -p.y; }
                    else if (ax == 1) { u = p.x; v = p.z; }
                    else { u = p.x; v = -p.y; }
                    break;
            }
            m.uv[fi][k * 2] = u * (mode == UV_BOX ? scale : 1);
            m.uv[fi][k * 2 + 1] = v * (mode == UV_BOX ? scale : 1);
        }
        if (mode == UV_CYLINDER || mode == UV_SPHERE) {   // fix the seam: keep a face on one side of u = 0/1
            float lo = 1e9f, hi = -1e9f;
            for (size_t k = 0; k < f.size(); k++) { lo = std::min(lo, m.uv[fi][k * 2]); hi = std::max(hi, m.uv[fi][k * 2]); }
            if (hi - lo > 0.5f) for (size_t k = 0; k < f.size(); k++) if (m.uv[fi][k * 2] < 0.5f) m.uv[fi][k * 2] += 1;
            if (scale != 1) for (auto& x : m.uv[fi]) x *= scale;
        }
    }
}

// ------------------------------------------------------------------------------------------------ rigging
std::vector<int> segmentRig(const PolyMesh& m, const std::vector<float>& joints, const std::vector<int>& parents) {
    const int J = (int) joints.size() / 3;
    if (J == 0) throw std::runtime_error("place at least one joint");
    std::vector<V3> P(J), E(J);
    for (int j = 0; j < J; j++) P[j] = V3(joints[j * 3], joints[j * 3 + 1], joints[j * 3 + 2]);
    for (int j = 0; j < J; j++) {
        V3 sum; int kids = 0;
        for (int c = 0; c < J; c++) if (c != j && c < (int) parents.size() && parents[c] == j) { sum = sum + P[c]; kids++; }
        if (kids > 0) E[j] = sum * (1.0f / kids);
        else {
            int par = j < (int) parents.size() ? parents[j] : -1;
            E[j] = par >= 0 && par < J ? P[j] + (P[j] - P[par]) * 0.6f : P[j];
        }
    }
    auto segDist = [](const V3& p, const V3& a, const V3& b) {
        V3 ab = b - a;
        float l2 = ab.dot(ab);
        float t = l2 > 1e-12f ? std::max(0.0f, std::min(1.0f, (p - a).dot(ab) / l2)) : 0.0f;
        return (p - (a + ab * t)).len();
    };
    std::vector<int> out(m.f.size(), 0);
    for (size_t fi = 0; fi < m.f.size(); fi++) {
        V3 c = centroid(m, (int) fi);
        float best = 1e30f;
        for (int j = 0; j < J; j++) {
            float d = segDist(c, P[j], E[j]);
            if (d < best - 1e-6f) { best = d; out[fi] = j; }
        }
    }
    // island clean-up: a face whose neighbours mostly belong to one other bone joins it
    auto ef = edgeFaces(m);
    for (int pass = 0; pass < 2; pass++) {
        std::vector<int> next(out);
        for (size_t fi = 0; fi < m.f.size(); fi++) {
            std::map<int, int> votes;
            int total = 0;
            const auto& f = m.f[fi];
            for (size_t k = 0; k < f.size(); k++)
                for (int o : ef[edgeKey(f[k], f[(k + 1) % f.size()])]) if (o != (int) fi) { votes[out[o]]++; total++; }
            if (total == 0 || votes[out[fi]] > 0) continue;
            int bestBone = out[fi], bestVotes = 0;
            for (auto& v : votes) if (v.second > bestVotes) { bestVotes = v.second; bestBone = v.first; }
            if (bestVotes * 2 > total) next[fi] = bestBone;
        }
        out.swap(next);
    }
    return out;
}

// ------------------------------------------------------------------------------------------------ auto animation
namespace {

enum Role { R_OTHER, R_HIPS, R_SPINE, R_CHEST, R_NECK, R_HEAD, R_SHOULDER, R_UPPERARM, R_FOREARM, R_HAND, R_THIGH, R_SHIN, R_FOOT };

std::string lowerAlnum(const std::string& s) {
    std::string o;
    for (char c : s) if (std::isalnum((unsigned char) c)) o += (char) std::tolower((unsigned char) c);
    return o;
}
bool has(const std::string& s, const char* k) { return s.find(k) != std::string::npos; }

Role roleOf(const std::string& name) {
    std::string n = lowerAlnum(name);
    if (has(n, "eye") || has(n, "mouth") || has(n, "hat")) return R_OTHER;
    if (has(n, "forearm") || has(n, "lowerarm") || has(n, "elbow")) return R_FOREARM;
    if (has(n, "hand") || has(n, "finger") || has(n, "wrist") || has(n, "palm")) return R_HAND;
    if (has(n, "shoulder") || has(n, "clavicle")) return R_SHOULDER;
    if (has(n, "arm")) return R_UPPERARM;
    if (has(n, "shin") || has(n, "calf") || has(n, "knee") || has(n, "lowerleg")) return R_SHIN;
    if (has(n, "foot") || has(n, "toe") || has(n, "ankle")) return R_FOOT;
    if (has(n, "thigh") || has(n, "upperleg") || has(n, "leg")) return R_THIGH;
    if (has(n, "neck")) return R_NECK;
    if (has(n, "head") || has(n, "skull")) return R_HEAD;
    if (has(n, "chest") || has(n, "torso") || has(n, "body") || has(n, "upperspine")) return R_CHEST;
    if (has(n, "spine") || has(n, "waist") || has(n, "belly")) return R_SPINE;
    if (has(n, "hip") || has(n, "pelvis") || has(n, "root")) return R_HIPS;
    return R_OTHER;
}

/** +1 = left, -1 = right, 0 = centre. Left is the -X side (S Engine character convention) when names don't say. */
int sideOf(const std::string& name, float x) {
    std::string n = lowerAlnum(name);
    if (has(n, "left")) return 1;
    if (has(n, "right")) return -1;
    size_t L = name.size();
    if (L >= 2) {
        char last = name[L - 1], before = name[L - 2];
        bool sep = before == '_' || before == '.' || before == ' ' || before == '-' || std::islower((unsigned char) before);
        if (sep && (last == 'L' || (last == 'l' && before != 'l' && (before == '_' || before == '.' || before == ' ')))) return 1;
        if (sep && (last == 'R' || (last == 'r' && (before == '_' || before == '.' || before == ' ')))) return -1;
        char first = name[0], second = name[1];
        if ((first == 'L' || first == 'l') && (second == '_' || second == '.' || second == ' ')) return 1;
        if ((first == 'R' || first == 'r') && (second == '_' || second == '.' || second == ' ')) return -1;
    }
    if (x < -1e-3f) return 1;
    if (x > 1e-3f) return -1;
    return 0;
}

struct Delta { float p[3] = {0, 0, 0}, r[3] = {0, 0, 0}, s[3] = {1, 1, 1}; };

std::string num(float v) {
    char b[32];
    snprintf(b, sizeof b, "%.4f", std::fabs(v) < 1e-5f ? 0.0f : v);
    return b;
}

float ease(float x) { x = std::max(0.0f, std::min(1.0f, x)); return x * x * (3 - 2 * x); }
/** piecewise-linear lookup through (u, value) points */
float curve(float u, std::initializer_list<std::pair<float, float>> pts) {
    const std::pair<float, float>* prev = nullptr;
    for (auto& p : pts) {
        if (u <= p.first) {
            if (!prev) return p.second;
            float t = (u - prev->first) / std::max(1e-6f, p.first - prev->first);
            return prev->second + (p.second - prev->second) * ease(t);
        }
        prev = &p;
    }
    return prev ? prev->second : 0.0f;
}

}  // namespace

const char* autoAnimationKinds() { return "Idle,Walk,Run,Jump,Wave,Punch,Dance,Death,Celebrate,Crouch,Spin,Bounce,Hover,Shake,Swing,Pulse"; }

std::string autoAnimate(const std::string& kindIn, const std::vector<std::string>& names, const std::vector<int>& parents,
                        const std::vector<float>& rest, float height, float length) {
    const int n = (int) names.size();
    if ((int) rest.size() < n * 9) throw std::runtime_error("rest pose data is incomplete");
    std::string kind = lowerAlnum(kindIn);
    if (height <= 1e-4f) height = 1;
    std::vector<Role> role(n);
    std::vector<int> side(n);
    int humanoidParts = 0;
    bool hasShin = false, hasForearm = false;
    for (int i = 0; i < n; i++) {
        role[i] = roleOf(names[i]);
        side[i] = sideOf(names[i], rest[i * 9]);
        if (role[i] == R_UPPERARM || role[i] == R_THIGH) humanoidParts++;
        if (role[i] == R_SHIN) hasShin = true;
        if (role[i] == R_FOREARM) hasForearm = true;
    }
    const bool humanoid = humanoidParts >= 2;
    // root parts carry whole-body motion; for humanoids the hips/torso root
    std::vector<char> isRoot(n, 0);
    for (int i = 0; i < n; i++) isRoot[i] = i >= (int) parents.size() || parents[i] < 0 || parents[i] >= n;

    struct Spec { float len; bool loop; int samples; };
    Spec spec{1.0f, true, 8};
    if (kind == "idle") spec = {2.0f, true, 4};
    else if (kind == "walk") spec = {1.0f, true, 8};
    else if (kind == "run") spec = {0.6f, true, 8};
    else if (kind == "jump") spec = {0.9f, false, 9};
    else if (kind == "wave") spec = {1.2f, true, 6};
    else if (kind == "punch") spec = {0.5f, false, 6};
    else if (kind == "dance") spec = {1.6f, true, 8};
    else if (kind == "death") spec = {1.2f, false, 7};
    else if (kind == "celebrate") spec = {1.0f, true, 8};
    else if (kind == "crouch") spec = {0.5f, false, 4};
    else if (kind == "spin") spec = {1.2f, true, 4};
    else if (kind == "bounce") spec = {0.8f, true, 8};
    else if (kind == "hover") spec = {2.0f, true, 8};
    else if (kind == "shake") spec = {0.4f, true, 8};
    else if (kind == "swing") spec = {1.6f, true, 8};
    else if (kind == "pulse") spec = {1.0f, true, 4};
    else throw std::runtime_error("unknown animation '" + kindIn + "' (try: " + autoAnimationKinds() + ")");
    if (length > 0.05f) spec.len = length;

    auto sample = [&](int i, float u, Delta& d) {   // u in [0, 1)
        const float ph = u * 2 * kPi;
        const Role r = role[i];
        const float sd = (float) side[i];          // +1 left, -1 right
        const float lift = sd >= 0 ? -1.0f : 1.0f; // sign of Z rotation that raises this arm sideways
        const float legPhase = sd >= 0 ? 0.0f : kPi; // legs / arms alternate
        if (kind == "idle") {
            if (humanoid) {
                if (r == R_CHEST || (r == R_SPINE && !hasShin)) { d.s[1] = 1 + 0.025f * std::sin(ph); }
                if (r == R_HEAD) { d.r[0] = 3 * std::sin(ph); d.r[1] = 4 * std::sin(ph * 0.5f); }
                if (r == R_UPPERARM) d.r[2] = lift * (4 + 2 * std::sin(ph));
                if (isRoot[i]) d.p[1] = height * 0.006f * std::sin(ph);
            } else if (isRoot[i]) { d.s[1] = 1 + 0.03f * std::sin(ph); d.s[0] = d.s[2] = 1 - 0.015f * std::sin(ph); }
        } else if (kind == "walk" || kind == "run") {
            const bool run = kind == "run";
            const float amp = run ? 50.0f : 30.0f;
            if (humanoid) {
                if (r == R_THIGH) d.r[0] = -amp * std::sin(ph + legPhase);
                if (r == R_SHIN) d.r[0] = (run ? 70.0f : 40.0f) * std::max(0.0f, std::sin(ph + legPhase + kPi * 0.5f));
                if (r == R_FOOT) d.r[0] = -15 * std::sin(ph + legPhase);
                if (r == R_UPPERARM) { d.r[0] = amp * 0.9f * std::sin(ph + legPhase); d.r[2] = lift * 5; }
                if (r == R_FOREARM) d.r[0] = -(run ? 70.0f : 20.0f) - 10 * std::sin(ph + legPhase);
                if (r == R_CHEST || r == R_SPINE) d.r[1] = (run ? 8.0f : 5.0f) * std::sin(ph);
                if (r == R_HEAD) d.r[1] = -3 * std::sin(ph);
                if (isRoot[i]) { d.p[1] = height * (run ? 0.04f : 0.02f) * std::fabs(std::sin(ph)); if (run) d.r[0] = 8; }
                if (!hasShin && r == R_THIGH && run) d.r[0] *= 1.1f;
            } else if (isRoot[i]) {   // waddle
                d.r[2] = 8 * std::sin(ph);
                d.p[1] = height * 0.05f * std::fabs(std::sin(ph));
            }
        } else if (kind == "jump" || kind == "celebrate") {
            const bool cel = kind == "celebrate";
            float up = cel ? 0.25f * std::fabs(std::sin(ph)) : curve(u, {{0, 0}, {0.22f, -0.08f}, {0.35f, 0.1f}, {0.55f, 0.35f}, {0.75f, 0.1f}, {0.88f, -0.06f}, {1, 0}});
            float crouch = cel ? 0 : curve(u, {{0, 0}, {0.22f, 1}, {0.35f, 0}, {0.55f, 0.3f}, {0.8f, 0.2f}, {0.88f, 0.8f}, {1, 0}});
            if (isRoot[i]) {
                d.p[1] = up * height;
                if (!humanoid) {   // squash & stretch
                    float st = cel ? 0 : curve(u, {{0, 0}, {0.22f, -0.25f}, {0.4f, 0.2f}, {0.6f, 0.05f}, {0.88f, -0.2f}, {1, 0}});
                    d.s[1] = 1 + st; d.s[0] = d.s[2] = 1 - st * 0.5f;
                }
            }
            if (humanoid) {
                if (r == R_THIGH) d.r[0] = -45 * crouch;
                if (r == R_SHIN) d.r[0] = 80 * crouch;
                if (r == R_FOOT) d.r[0] = -30 * crouch;
                if (r == R_UPPERARM) {
                    if (cel) { d.r[2] = lift * (150 + 15 * std::sin(ph * 2)); }
                    else d.r[0] = curve(u, {{0, 0}, {0.22f, 40}, {0.45f, -150}, {0.8f, -60}, {1, 0}});
                }
                if (r == R_FOREARM && cel) d.r[2] = lift * 20 * std::sin(ph * 2);
                if (r == R_CHEST || r == R_SPINE) d.r[0] = 15 * crouch;
            }
        } else if (kind == "wave") {
            if (humanoid) {
                if (r == R_UPPERARM && sd < 0) d.r[2] = lift * 150;
                if (r == R_FOREARM && sd < 0) d.r[2] = lift * (15 + 25 * std::sin(ph * 2));
                if (r == R_UPPERARM && sd < 0 && !hasForearm) d.r[2] = lift * (130 + 25 * std::sin(ph * 2));
                if (r == R_HAND && sd < 0) d.r[2] = 15 * std::sin(ph * 2);
                if (r == R_HEAD) d.r[2] = 5 * std::sin(ph);
            } else if (isRoot[i]) d.r[2] = 12 * std::sin(ph);
        } else if (kind == "punch") {
            float hit = curve(u, {{0, 0}, {0.25f, -0.3f}, {0.45f, 1}, {0.7f, 1}, {1, 0}});
            if (humanoid) {
                if (r == R_UPPERARM && sd < 0) d.r[0] = -90 * hit;
                if (r == R_FOREARM && sd < 0) d.r[0] = -60 * (1 - std::max(0.0f, hit));
                if (r == R_UPPERARM && sd > 0) { d.r[0] = -40; }
                if (r == R_FOREARM && sd > 0) d.r[0] = -90;
                if (r == R_CHEST || r == R_SPINE) d.r[1] = 25 * hit;
            } else if (isRoot[i]) d.p[2] = height * 0.3f * hit;
        } else if (kind == "dance") {
            if (isRoot[i]) { d.p[1] = height * 0.03f * std::fabs(std::sin(ph * 2)); d.r[2] = humanoid ? 6 * std::sin(ph) : 15 * std::sin(ph); d.r[1] = humanoid ? 0 : 30 * std::sin(ph); }
            if (humanoid) {
                if (r == R_UPPERARM) d.r[2] = lift * (90 + 60 * std::sin(ph + legPhase));
                if (r == R_FOREARM) d.r[2] = lift * 30 * std::sin(ph * 2);
                if (r == R_THIGH) d.r[0] = -20 * std::max(0.0f, std::sin(ph + legPhase));
                if (r == R_SHIN) d.r[0] = 35 * std::max(0.0f, std::sin(ph + legPhase));
                if (r == R_HEAD) d.r[2] = 10 * std::sin(ph * 2);
                if (r == R_CHEST) d.r[1] = 15 * std::sin(ph);
            }
        } else if (kind == "death") {
            float fall = curve(u, {{0, 0}, {0.2f, -0.1f}, {0.75f, 1}, {0.85f, 0.95f}, {1, 1}});
            if (isRoot[i]) { d.r[0] = -85 * fall; d.p[1] = -height * (humanoid ? 0.4f : 0.2f) * fall; d.p[2] = -height * 0.25f * fall; }
            if (humanoid) {
                if (r == R_UPPERARM) d.r[2] = lift * 70 * fall;
                if (r == R_HEAD) d.r[0] = -20 * fall;
                if (r == R_THIGH) d.r[0] = -20 * fall * (sd >= 0 ? 1.0f : 0.5f);
                if (r == R_SHIN) d.r[0] = 30 * fall;
            }
        } else if (kind == "crouch") {
            float c = ease(u * 1.3f);
            if (isRoot[i]) d.p[1] = -height * 0.2f * c;
            if (humanoid) {
                if (r == R_THIGH) d.r[0] = -70 * c;
                if (r == R_SHIN) d.r[0] = 110 * c;
                if (r == R_FOOT) d.r[0] = -40 * c;
                if (r == R_CHEST || r == R_SPINE) d.r[0] = 20 * c;
                if (r == R_UPPERARM) d.r[0] = -30 * c;
            } else if (isRoot[i]) { d.s[1] = 1 - 0.3f * c; d.s[0] = d.s[2] = 1 + 0.15f * c; }
        } else if (isRoot[i]) {   // generic object motions
            if (kind == "spin") d.r[1] = 360 * u;
            else if (kind == "bounce") {
                float h = std::fabs(std::sin(ph * 0.5f));
                d.p[1] = height * 0.4f * h;
                float sq = h < 0.2f ? (0.2f - h) * 1.2f : 0.0f;
                d.s[1] = 1 - sq + 0.08f * h; d.s[0] = d.s[2] = 1 + sq * 0.6f - 0.04f * h;
            } else if (kind == "hover") { d.p[1] = height * 0.08f * std::sin(ph); d.r[1] = 10 * std::sin(ph * 0.5f); d.r[2] = 3 * std::sin(ph); }
            else if (kind == "shake") { d.p[0] = height * 0.03f * std::sin(ph * 3); d.r[2] = 4 * std::sin(ph * 2); }
            else if (kind == "swing") d.r[2] = 25 * std::sin(ph);
            else if (kind == "pulse") { float s = 1 + 0.12f * std::sin(ph); d.s[0] = d.s[1] = d.s[2] = s; }
        }
    };

    // spin must hit 360 exactly at the loop point: sample the last key at u = 1 for monotonic motion
    const bool monotonic = kind == "spin";
    std::string js = "{\"name\":\"" + kindIn + "\",\"length\":" + num(spec.len) + ",\"loop\":" + (spec.loop ? "true" : "false") + ",\"tracks\":[";
    bool firstTrack = true;
    for (int i = 0; i < n; i++) {
        // skip parts that never move
        bool moves = false;
        std::vector<Delta> ds(spec.samples + 1);
        int count = spec.loop && !monotonic ? spec.samples : spec.samples + 1;
        for (int s = 0; s < count; s++) {
            float u = spec.loop && !monotonic ? (float) s / spec.samples : (float) s / spec.samples;
            sample(i, u, ds[s]);
            for (int k = 0; k < 3; k++)
                if (std::fabs(ds[s].p[k]) > 1e-5f || std::fabs(ds[s].r[k]) > 1e-4f || std::fabs(ds[s].s[k] - 1) > 1e-5f) moves = true;
        }
        if (!moves) continue;
        if (!firstTrack) js += ",";
        firstTrack = false;
        js += "{\"part\":\"";
        for (char c : names[i]) { if (c == '"' || c == '\\') js += '\\'; js += c; }
        js += "\",\"keys\":[";
        for (int s = 0; s < count; s++) {
            const float* rp = &rest[i * 9];
            const Delta& d = ds[s];
            float t = spec.len * s / spec.samples;
            if (s) js += ",";
            js += "{\"t\":" + num(t) + ",\"pos\":[" + num(rp[0] + d.p[0]) + "," + num(rp[1] + d.p[1]) + "," + num(rp[2] + d.p[2]) +
                  "],\"rot\":[" + num(rp[3] + d.r[0]) + "," + num(rp[4] + d.r[1]) + "," + num(rp[5] + d.r[2]) +
                  "],\"scale\":[" + num(rp[6] * d.s[0]) + "," + num(rp[7] * d.s[1]) + "," + num(rp[8] * d.s[2]) + "]}";
        }
        js += "]}";
    }
    js += "]}";
    return js;
}

}  // namespace modelkit
}  // namespace sengine
