// Native World — score, timer and HUD, written in C++.
#include "SEngine.h"

class GameManager : public Behaviour {
public:
    int total = 0;
    int collected = 0;
    float timer = 0.0f;
    bool won = false;

    void SetTotal(int n) {
        total = n;
        Refresh();
    }

    void Collect(int n) {
        collected += n;
        if (!won && total > 0 && collected >= total) {
            won = true;
            Scene::Find("HUD").text = Format("All %d crystals found in %.0f s!", total, timer);
            Platform::Vibrate(80);
            UE_LOG(LogTemp, Log, TEXT("Player won in %.1f seconds"), timer);
            return;
        }
        Refresh();
    }

    void Update(float dt) override {
        if (won) return;
        timer += dt;
        Refresh();
    }

    void Refresh() {
        if (won) return;
        Scene::Find("HUD").text = Format("Crystals %d / %d     %.0f s", collected, total, timer);
    }
};
