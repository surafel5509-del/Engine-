// Native World — third-person player, written in C++ for the S Engine native VM.
#include "SEngine.h"

class PlayerController : public Behaviour {
public:
    float speed = 7.0f;       // metres / second
    float jump = 7.5f;        // jump velocity
    float sprint = 1.6f;      // B held = sprint multiplier
    Vec3 spawn;
    int jumps = 0;

    void Start() override {
        // Drop the player onto the C++ landscape, whatever the terrain seed is.
        Vec3 p = gameObject.position;
        p.y = Scene::TerrainHeight(p.x, p.z) + 1.5f;
        gameObject.position = p;
        spawn = p;
        Log("C++ player ready at", spawn);
    }

    void Update(float dt) override {
        Vec3 move(Input::AxisX(), 0, -Input::AxisY());
        if (move.Length() > 1.0f) move = move.Normalized();
        float s = speed * (Input::B() ? sprint : 1.0f);
        gameObject.vx = move.x * s;
        gameObject.vz = move.z * s;
        if (move.Length() > 0.1f) gameObject.rotY = atan2(move.x, move.z) * Rad2Deg;

        if (Input::ADown() && gameObject.grounded) {
            gameObject.vy = jump;
            jumps++;
        }
        // fell off the island? respawn
        if (gameObject.y < -20.0f) {
            gameObject.position = spawn;
            gameObject.velocity = Vec3::Zero;
        }
    }
};
