// S Engine — native 3D Model & Animation Studio kernel.
// Polygon modeling operators (bevel, insert edge loop, bridge, PolyGroups, UV unwrapping), humanoid rig
// segmentation (rigid skinning) and procedural auto-animation. Used by the Model Editor through JNI.
#pragma once

#include <string>
#include <vector>

namespace sengine {

/** Editable n-gon mesh (same layout as the Kotlin SPart). */
struct PolyMesh {
    std::vector<float> v;                    // xyz per vertex
    std::vector<std::vector<int>> f;         // faces (CCW, outward)
    std::vector<int> color;                  // per face (0 = part colour)
    std::vector<int> group;                  // per face PolyGroup id
    std::vector<std::vector<float>> uv;      // per face: 2 floats per corner (empty = none)

    int vertexCount() const { return (int) (v.size() / 3); }
    int addVertex(float x, float y, float z) { v.push_back(x); v.push_back(y); v.push_back(z); return vertexCount() - 1; }
    int addFace(const std::vector<int>& face, int col, int grp) {
        f.push_back(face); color.push_back(col); group.push_back(grp); uv.emplace_back();
        return (int) f.size() - 1;
    }
    void normalize();   // makes color/group/uv the same length as f
};

namespace modelkit {

/** Rounded bevel of each selected face (inset + raise, [segments] rings on a quarter-circle profile). Returns the new cap faces. */
std::vector<int> bevelFaces(PolyMesh& m, const std::vector<int>& faces, float width, float depth, int segments);

/** Loop cut across the edge (a, b): walks the quad ring in both directions and splits every quad at [t]. Returns faces cut. */
int insertEdgeLoop(PolyMesh& m, int a, int b, float t);

/** Connects two faces with the same vertex count by a tube of [segments] rings; both faces are removed. Returns new faces. */
std::vector<int> bridgeFaces(PolyMesh& m, int fa, int fb, int segments);

/** Flood-fills PolyGroups across edges whose dihedral angle is below [angleDeg]. Returns the number of groups. */
int autoPolyGroups(PolyMesh& m, float angleDeg);

/** Grows a face selection to every face of the same PolyGroup(s). */
std::vector<int> selectGroups(const PolyMesh& m, const std::vector<int>& faces);

enum UnwrapMode { UV_BOX = 0, UV_PLANAR = 1, UV_CYLINDER = 2, UV_SPHERE = 3, UV_SMART = 4 };
/** Writes per-corner UVs. Smart mode unwraps each PolyGroup as a planar chart and shelf-packs the charts into 0..1. */
void unwrap(PolyMesh& m, int mode, float scale);

/** Area-weighted face normal (Newell). */
void faceNormal(const PolyMesh& m, int face, float out[3]);

/**
 * Rigid skinning for point-and-click rigging: assigns every face to the nearest bone segment
 * (joint -> child joint), then removes small islands with a neighbourhood vote. Returns one joint index per face.
 */
std::vector<int> segmentRig(const PolyMesh& m, const std::vector<float>& joints, const std::vector<int>& parents);

/**
 * Procedural animation clip for a part hierarchy (humanoid roles are detected from part names; other models get
 * whole-object motion). rest = 9 floats per part (pos, rot, scale). Returns the clip as .smodel clip JSON.
 */
std::string autoAnimate(const std::string& kind, const std::vector<std::string>& names, const std::vector<int>& parents,
                        const std::vector<float>& rest, float height, float length);

/** The auto-animation kinds, comma separated. */
const char* autoAnimationKinds();

}  // namespace modelkit
}  // namespace sengine
