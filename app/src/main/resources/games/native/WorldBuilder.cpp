// Native World — procedural population of the landscape (trees + crystals), all in C++.
#include "SEngine.h"

class WorldBuilder : public Behaviour {
public:
    int trees = 45;
    int crystals = 10;
    int seed = 42;
    float radius = 48.0f;
    float waterY = 2.6f;

    void Start() override {
        srand(seed);
        int placedTrees = 0;
        int placedCrystals = 0;
        int tries = 0;
        while ((placedTrees < trees || placedCrystals < crystals) && tries < 5000) {
            tries++;
            float x = Random(-radius, radius);
            float z = Random(-radius, radius);
            float y = Scene::TerrainHeight(x, z, -100.0f);
            if (y < waterY + 0.8f) continue;               // keep beaches and the lake clear
            if (placedCrystals < crystals && tries % 3 == 0) {
                Scene::Spawn("Crystal", x, y + 1.2f, z);
                placedCrystals++;
            } else if (placedTrees < trees) {
                GameObject t = Scene::Spawn("Tree", x, y, z);
                float s = Random(0.8f, 1.5f);
                t.scale = Vec3(s, s, s);
                t.rotY = Random(0.0f, 360.0f);
                placedTrees++;
            }
        }
        Log("World built:", placedTrees, "trees,", placedCrystals, "crystals");
        Scene::Find("GameManager").SendMessage("SetTotal", placedCrystals);
    }
};
