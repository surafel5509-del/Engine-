// Host-side tests for the S Engine C++ script VM (run on CI: g++ -std=c++17 … && ./script_tests)
#include "../script/SScript.h"
#include "../engine/Terrain.h"

#include <chrono>
#include <cmath>
#include <cstdio>
#include <map>

using namespace sengine;

static int failures = 0, passes = 0;
#define CHECK(cond, msg)                                                   \
    do {                                                                   \
        if (cond) passes++;                                                \
        else { failures++; printf("FAIL %s:%d %s\n", __FILE__, __LINE__, msg); } \
    } while (0)

struct MockHost : Host {
    std::map<int64_t, std::map<std::string, Value>> objects;
    std::vector<std::string> logs;
    std::vector<std::string> calls;
    Value call(int64_t target, const std::string& fn, std::vector<Value>& args) override {
        calls.push_back(std::to_string(target) + ":" + fn);
        if (target != 0) {
            auto& o = objects[target];
            if (fn.rfind("get:", 0) == 0) {
                std::string p = fn.substr(4);
                if (p == "position") return Value::vec(o["x"].num(), o["y"].num(), o["z"].num());
                return o[p];
            }
            if (fn.rfind("set:", 0) == 0) {
                std::string p = fn.substr(4);
                if (p == "position") { o["x"] = Value::number(args[0].v.x); o["y"] = Value::number(args[0].v.y); o["z"] = Value::number(args[0].v.z); }
                else o[p] = args[0];
                return Value();
            }
            if (fn == "Destroy" || fn == "destroy") { o["destroyed"] = Value::boolean(true); return Value(); }
            throw std::runtime_error("GameObject has no method " + fn);
        }
        if (fn == "get:Time::deltaTime") return Value::number(0.016);
        if (fn == "Input::AxisX" || fn == "Input::GetAxisX") return Value::number(1.0);
        if (fn == "Scene::Find" || fn == "Find") return args[0].toString() == "Enemy" ? Value::obj(2) : Value();
        if (fn == "Scene::FindAll") { Value l = Value::newList(); l.list->push_back(Value::obj(2)); l.list->push_back(Value::obj(3)); return l; }
        throw std::runtime_error("unknown engine function " + fn);
    }
    void log(int level, const std::string& msg) override { logs.push_back((level ? "[" + std::to_string(level) + "] " : "") + msg); }
};

static Value run(const char* src, const char* fn, MockHost& host, std::string* err = nullptr) {
    try {
        auto prog = compile("test.cpp", src);
        Interpreter vm(prog.get(), &host);
        std::vector<Value> none;
        Value r = vm.callFunction(fn, none);
        return r;
    } catch (ScriptError& e) {
        if (err) *err = "line " + std::to_string(e.line) + ": " + e.what();
        else printf("  error line %d: %s\n", e.line, e.what());
        return Value::str("<error>");
    }
}

static void testBasics() {
    MockHost h;
    Value r = run(R"(
        #include "SEngine.h"
        using namespace std;
        int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }
        int test() {
            int total = 0;
            for (int i = 0; i < 10; i++) { if (i % 2 == 0) continue; total += i; }   // 1+3+5+7+9 = 25
            int k = 0;
            while (true) { k++; if (k >= 5) break; }
            do { k--; } while (k > 2);
            switch (k) { case 1: total += 100; break; case 2: total += 1000; break; default: total -= 1; }
            return total + fib(10) + 7 / 2;   // 25 + 1000 + 55 + 3
        }
    )", "test", h);
    CHECK(r.t == T::Int && r.i == 1083, ("basics: " + r.toString()).c_str());

    r = run(R"(
        float test() { float f = 7 / 2; float g = 7.0f / 2; int t = (int)3.9; double d = static_cast<double>(t) * 1.5; return f + g + d; }
    )", "test", h);
    CHECK(r.t == T::Float && fabs(r.f - (3 + 3.5 + 4.5)) < 1e-9, ("int/float semantics: " + r.toString()).c_str());

    r = run(R"(
        std::string test() {
            std::string s = "Score: " + std::to_string(42);
            s += "!";
            std::vector<int> v = {5, 3, 9};
            v.push_back(1);
            v.sort();
            int sum = 0;
            for (auto x : v) sum += x;
            std::map<std::string, int> m;
            m["a"] = 2; m["b"] = 3;
            return s + " " + std::to_string(sum) + " " + std::to_string(v[0]) + std::to_string(v.size()) + " " + std::to_string(m["a"] * m["b"]) + " " + s.substr(0, 5);
        }
    )", "test", h);
    CHECK(r.t == T::Str && *r.s == "Score: 42! 18 14 6 Score", ("strings/vectors: " + r.toString()).c_str());

    r = run(R"(
        Vec3 test() {
            Vec3 a(1, 2, 3);
            Vec3 b = {4, 5, 6};
            Vec3 c = a + b * 2.0f;      // (9, 12, 15)
            c.y = 0;
            c.x += 1;                   // (10, 0, 15)
            float len = Vec3(3, 4, 0).Length();   // 5
            return c + Vec3::Up * len + FVector(0, 0, a.Dot(b));   // (10, 5, 15+32)
        }
    )", "test", h);
    CHECK(r.t == T::Vec && r.v.x == 10 && r.v.y == 5 && r.v.z == 47, ("vectors: " + r.toString()).c_str());

    std::string err;
    run("int test() { int x = 1; return y; }", "test", h, &err);
    CHECK(err.find("'y' was not declared") != std::string::npos && err.find("line 1") != std::string::npos, ("undeclared error: " + err).c_str());
    err.clear();
    run("int test() {\n int x = 1\n return x; }", "test", h, &err);
    CHECK(err.find("expected ';'") != std::string::npos, ("syntax error: " + err).c_str());
    err.clear();
    run("int test() { while (true) {} return 0; }", "test", h, &err);
    CHECK(err.find("too long") != std::string::npos, ("runaway guard: " + err).c_str());
    err.clear();
    run("int test() { std::vector<int> v; return v[3]; }", "test", h, &err);
    CHECK(err.find("out of range") != std::string::npos, ("bounds: " + err).c_str());

    MockHost h2;
    run(R"(
        void test() {
            std::cout << "hello " << 3 << " " << 2.5f << std::endl;
            printf("hp=%d speed=%.1f name=%s\n", 90, 4.25, "Bob");
            UE_LOG(LogTemp, Warning, TEXT("UE style %d"), 7);
            Log("done", true);
        }
    )", "test", h2);
    CHECK(h2.logs.size() == 4, ("logs count " + std::to_string(h2.logs.size())).c_str());
    if (h2.logs.size() == 4) {
        CHECK(h2.logs[0] == "hello 3 2.5", h2.logs[0].c_str());
        CHECK(h2.logs[1] == "hp=90 speed=4.2 name=Bob" || h2.logs[1] == "hp=90 speed=4.3 name=Bob", h2.logs[1].c_str());
        CHECK(h2.logs[2] == "[1] UE style 7", h2.logs[2].c_str());
        CHECK(h2.logs[3] == "done true", h2.logs[3].c_str());
    }
}

static void testBehaviour() {
    MockHost h;
    h.objects[1]["x"] = Value::number(0);
    h.objects[1]["name"] = Value::str("Player");
    h.objects[2]["x"] = Value::number(10);
    const char* src = R"(
        #include "SEngine.h"
        enum class State { Idle, Run = 5, Jump };
        class Weapon {
        public:
            int ammo;
            Weapon(int a) : ammo(a) {}
            bool Fire() { if (ammo <= 0) return false; ammo--; return true; }
        };
        class Player : public Behaviour {
        public:
            float speed = 4.0f;
            int hp = 100;
            int frames = 0;
            State state = State::Idle;
            Weapon gun = Weapon(2);
            static int instances;
            std::vector<GameObject> hits;

            void Start() override { instances++; Log("start", gameObject.name); }
            void Update(float dt) override {
                frames++;
                gameObject.x += Input::AxisX() * speed * dt;
                if (auto e = Scene::Find("Enemy")) {
                    if (distance(gameObject.x, 0, e.x, 0) < 100) state = State::Run;
                }
                Vec3 p = gameObject.position;
                p.z = 2;
                gameObject.position = p;
            }
            void OnCollision(GameObject other) { hits.push_back(other); hp -= 10; if (gun.Fire()) Log("bang"); }
            int Report() { return hp * 1000 + frames * 10 + (int)state + gun.ammo * 100000; }
        };
        int Player::instances = 0;
    )";
    try {
        auto prog = compile("Player.cpp", src);
        Interpreter vm(prog.get(), &h);
        ClassDef* cls = prog->behaviourClass();
        CHECK(cls && cls->name == "Player", "behaviour class");
        auto inst = vm.instantiate(cls, 1);
        CHECK(vm.setField(inst.get(), "speed", "10"), "param");
        std::vector<Value> none, dt = {Value::number(0.5)};
        bool found = false;
        vm.callMethod(inst, "start", none, &found);
        CHECK(found, "start found");
        for (int k = 0; k < 4; k++) vm.callMethod(inst, "update", dt);
        std::vector<Value> other = {Value::obj(2)};
        vm.callMethod(inst, "onCollision", other);
        vm.callMethod(inst, "onCollision", other);
        vm.callMethod(inst, "onCollision", other);
        Value r = vm.callMethod(inst, "Report", none);
        // hp 70, frames 4, state Run(5), ammo 0 -> 70000 + 40 + 5
        CHECK(r.t == T::Int && r.i == 70045, ("report " + r.toString()).c_str());
        CHECK(fabs(h.objects[1]["x"].num() - 20.0) < 1e-9, ("moved x=" + h.objects[1]["x"].toString()).c_str());
        CHECK(h.objects[1]["z"].num() == 2, "position write-back");
        CHECK(vm.getField(inst.get(), "hits").list->size() == 3, "hits vector");
        CHECK(h.logs.size() >= 3 && h.logs[0] == "start Player", h.logs.empty() ? "no logs" : h.logs[0].c_str());
        Value inst2 = vm.getField(vm.instantiate(cls, 5).get(), "instances");
        CHECK(inst2.toInt() == 1, "static field shared");
        found = true;
        vm.callMethod(inst, "onTrigger", none, &found);
        CHECK(!found, "missing method reported");
    } catch (ScriptError& e) {
        failures++;
        printf("FAIL behaviour: line %d %s\n", e.line, e.what());
    }
}

static void testUnrealStyle() {
    MockHost h;
    h.objects[7]["rotY"] = Value::number(0);
    const char* src = R"(
        class ASpinner : public AActor {
            GENERATED_BODY()
        public:
            UPROPERTY(EditAnywhere)
            float RotationRate = 90.f;
            int32 Ticks = 0;
            FString Label = TEXT("spin");
            virtual void BeginPlay() override { Super::BeginPlay(); }
            virtual void Tick(float DeltaTime) override {
                Super::Tick(DeltaTime);
                Ticks++;
                gameObject.rotY = FMath::Fmod(gameObject.rotY + RotationRate * DeltaTime, 360.0f);
            }
        };
    )";
    try {
        auto prog = compile("Spinner.cpp", src);
        Interpreter vm(prog.get(), &h);
        auto inst = vm.instantiate(prog->behaviourClass(), 7);
        std::vector<Value> none, dt = {Value::number(1.0)};
        vm.callMethod(inst, "start", none);
        for (int k = 0; k < 5; k++) vm.callMethod(inst, "update", dt);
        CHECK(fabs(h.objects[7]["rotY"].num() - 90.0) < 1e-9, ("UE spinner rotY=" + h.objects[7]["rotY"].toString()).c_str());
        CHECK(vm.getField(inst.get(), "Ticks").toInt() == 5, "UE ticks");
    } catch (ScriptError& e) {
        failures++;
        printf("FAIL unreal: line %d %s\n", e.line, e.what());
    }
}

static void testPerformance() {
    MockHost h;
    const char* src = R"(
        int test() {
            int count = 0;
            for (int i = 2; i < 30000; i++) {
                bool prime = true;
                for (int d = 2; d * d <= i; d++) if (i % d == 0) { prime = false; break; }
                if (prime) count++;
            }
            return count;
        }
    )";
    auto t0 = std::chrono::steady_clock::now();
    Value r = run(src, "test", h);
    double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
    CHECK(r.t == T::Int && r.i == 3245, ("primes " + r.toString()).c_str());
    printf("SIM cpp perf primes<30000=%s in %.1f ms\n", r.toString().c_str(), ms);
}

static void testTerrain() {
    TerrainParams p;
    p.resolution = 65;
    p.size = 64;
    p.height = 12;
    p.seed = 7;
    p.octaves = 5;
    p.erosion = 20000;
    std::vector<float> heights;
    auto t0 = std::chrono::steady_clock::now();
    generateHeightmap(p, heights);
    double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
    CHECK(heights.size() == 65u * 65u, "terrain size");
    float lo = 1e9f, hi = -1e9f;
    for (float v : heights) { lo = std::min(lo, v); hi = std::max(hi, v); }
    CHECK(hi > lo && hi <= p.height + 0.001f && lo >= -0.001f, "terrain range");
    std::vector<float> mesh;
    std::vector<int> idx;
    buildTerrainMesh(p, heights, mesh, idx);
    CHECK(mesh.size() == 65u * 65u * 8u && idx.size() == 64u * 64u * 6u, "terrain mesh");
    std::vector<float> h2;
    generateHeightmap(p, h2);
    CHECK(h2 == heights, "terrain deterministic");
    CHECK(fabs(sampleHeight(p, heights, 0, 0) - heights[32 * 65 + 32]) < 1e-4, "terrain sample centre");
    printf("SIM terrain 65x65 min=%.2f max=%.2f in %.1f ms\n", lo, hi, ms);
}

int main() {
    testBasics();
    testBehaviour();
    testUnrealStyle();
    testPerformance();
    testTerrain();
    printf("SIM native tests: %d passed, %d failed\n", passes, failures);
    return failures == 0 ? 0 : 1;
}
