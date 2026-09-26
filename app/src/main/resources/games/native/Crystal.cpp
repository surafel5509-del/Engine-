// Native World — a collectible crystal that spins, bobs and reports to the GameManager.
#include "SEngine.h"

class Crystal : public Behaviour {
public:
    float spin = 90.0f;
    float bob = 0.0f;
    float baseY = 0.0f;

    void Start() override {
        baseY = gameObject.y;
        bob = Random(0.0f, 6.28f);
    }

    void Update(float dt) override {
        bob += dt * 2.0f;
        gameObject.rotY += spin * dt;
        gameObject.y = baseY + sin(bob) * 0.25f;
    }

    void OnTrigger(GameObject other) {
        if (other.tag != "Player") return;
        Scene::Find("GameManager").SendMessage("Collect", 1);
        Audio::Play("coin.wav");
        gameObject.Destroy();
    }
};
