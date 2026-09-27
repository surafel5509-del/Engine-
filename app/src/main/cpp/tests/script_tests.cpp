// Host-side tests for the S Engine C++ script VM (run on CI: g++ -std=c++17 … && ./script_tests)
#include "../script/SScript.h"
#include "../engine/ModelKit.h"
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

static PolyMesh cube(float ox = 0) {
    PolyMesh m;
    for (int z = 0; z <= 1; z++) for (int y = 0; y <= 1; y++) for (int x = 0; x <= 1; x++) m.addVertex(x - 0.5f + ox, y - 0.5f, z - 0.5f);
    int F[6][4] = {{4, 5, 7, 6}, {1, 0, 2, 3}, {5, 1, 3, 7}, {0, 4, 6, 2}, {2, 6, 7, 3}, {0, 1, 5, 4}};
    for (auto& f : F) m.addFace({f[0], f[1], f[2], f[3]}, 0, 0);
    return m;
}

/** every directed edge must have exactly one opposite partner (closed, consistently wound 2-manifold) */
static bool watertight(const PolyMesh& m) {
    std::map<std::pair<int, int>, int> e;
    for (auto& f : m.f) for (size_t k = 0; k < f.size(); k++) e[{f[k], f[(k + 1) % f.size()]}]++;
    for (auto& kv : e) {
        if (kv.second != 1) return false;
        auto o = e.find({kv.first.second, kv.first.first});
        if (o == e.end() || o->second != 1) return false;
    }
    return true;
}

static void testModelKit() {
    using namespace modelkit;
    PolyMesh c = cube();
    CHECK(watertight(c), "cube watertight");
    // bevel all faces, 2 segments
    auto caps = bevelFaces(c, {0, 1, 2, 3, 4, 5}, 0.3f, 0.1f, 2);
    CHECK(caps.size() == 6 && c.f.size() == 6u + 6u * 4u * 2u, ("bevel faces " + std::to_string(c.f.size())).c_str());
    CHECK(watertight(c), "bevel keeps the mesh closed");
    // loop cut across edge (0,1): the ring through 4 quads
    PolyMesh l = cube();
    int cut = insertEdgeLoop(l, 0, 1, 0.5f);
    CHECK(cut == 4 && l.f.size() == 10u && l.vertexCount() == 12, ("edge loop cut=" + std::to_string(cut) + " faces=" + std::to_string(l.f.size())).c_str());
    CHECK(watertight(l), "edge loop keeps the mesh closed");
    bool midOk = false;
    for (int i = 8; i < l.vertexCount(); i++) if (fabs(l.v[i * 3]) < 1e-5f) midOk = true;
    CHECK(midOk, "loop vertices at x=0");
    // loop cut on a beveled cube stops at n-gons / continues around quad rings
    int cut2 = insertEdgeLoop(c, c.f[6][0], c.f[6][1], 0.3f);
    CHECK(cut2 >= 1 && watertight(c), ("loop on beveled cube cut=" + std::to_string(cut2)).c_str());
    // bridge two cubes: +X face of A (index 2) with -X face of B (index 3)
    PolyMesh b = cube(0);
    PolyMesh b2 = cube(3);
    int off = b.vertexCount();
    for (size_t i = 0; i < b2.v.size(); i++) b.v.push_back(b2.v[i]);
    for (auto f : b2.f) { for (auto& x : f) x += off; b.addFace(f, 0, 1); }
    auto made = bridgeFaces(b, 2, 6 + 3, 3);
    CHECK(made.size() == 12 && b.f.size() == 12u - 2u + 12u, ("bridge faces " + std::to_string(b.f.size())).c_str());
    CHECK(watertight(b), "bridge makes one closed mesh");
    bool thrown = false;
    try { PolyMesh t = cube(); t.addFace({0, 1, 2}, 0, 0); bridgeFaces(t, 0, 6, 1); } catch (std::exception&) { thrown = true; }
    CHECK(thrown, "bridge rejects faces with different vertex counts");
    // PolyGroups
    PolyMesh g = cube();
    CHECK(autoPolyGroups(g, 30) == 6, "cube has 6 PolyGroups");
    CHECK(selectGroups(g, {2}).size() == 1u, "select group");
    CHECK(autoPolyGroups(g, 180) == 1, "180 degrees merges everything");
    // UVs
    PolyMesh u = cube();
    unwrap(u, UV_SMART, 1);
    bool inRange = true;
    for (auto& fu : u.uv) { if (fu.size() != 8) inRange = false; for (float x : fu) if (x < -1e-4f || x > 1.0001f) inRange = false; }
    CHECK(inRange, "smart UV charts packed inside 0..1");
    unwrap(u, UV_CYLINDER, 1);
    CHECK(u.uv[0].size() == 8, "cylindrical UVs");
    // rigging: a 3-box tower, joints at y = 0, 1, 2
    PolyMesh t;
    for (int k = 0; k < 3; k++) {
        PolyMesh cb = cube();
        int o = t.vertexCount();
        for (int i = 0; i < cb.vertexCount(); i++) t.addVertex(cb.v[i * 3] * 0.4f, cb.v[i * 3 + 1] + 0.5f + k, cb.v[i * 3 + 2] * 0.4f);
        for (auto f : cb.f) { for (auto& x : f) x += o; t.addFace(f, 0, 0); }
    }
    auto seg = segmentRig(t, {0, 0, 0, 0, 1, 0, 0, 2, 0}, {-1, 0, 1});
    CHECK(seg.size() == 18u && seg[2] == 0 && seg[8] == 1 && seg[14] == 2, "rig segmentation follows the bones");
    // auto animation
    std::vector<std::string> names = {"Torso", "Head", "ArmL", "ArmR", "LegL", "LegR"};
    std::vector<int> parents = {-1, 0, 0, 0, -1, -1};
    std::vector<float> rest;
    float xs[6] = {0, 0, -0.5f, 0.5f, -0.18f, 0.18f};
    for (int i = 0; i < 6; i++) { float r[9] = {xs[i], 1, 0, 0, 0, 0, 1, 1, 1}; rest.insert(rest.end(), r, r + 9); }
    std::string walk = autoAnimate("Walk", names, parents, rest, 1.8f, 0);
    CHECK(walk.find("\"ArmL\"") != std::string::npos && walk.find("\"LegR\"") != std::string::npos && walk.find("\"loop\":true") != std::string::npos, walk.substr(0, 120).c_str());
    std::string wave = autoAnimate("Wave", names, parents, rest, 1.8f, 0);
    CHECK(wave.find("\"ArmR\"") != std::string::npos && wave.find("\"ArmL\"") == std::string::npos && wave.find(",130.0000]") != std::string::npos, "wave raises the right arm");
    std::string spin = autoAnimate("Spin", {"Box"}, {-1}, {0, 0, 0, 0, 0, 0, 1, 1, 1}, 1, 0);
    CHECK(spin.find("360.0") != std::string::npos, "spin reaches 360");
    thrown = false;
    try { autoAnimate("Fly", names, parents, rest, 1, 0); } catch (std::exception&) { thrown = true; }
    CHECK(thrown, "unknown animation kind");
    printf("SIM modelkit bevel=%zu faces, loop=%d cuts, bridge=%zu faces, walk json=%zu chars\n", c.f.size(), cut, b.f.size(), walk.size());
}

int main() {
    testBasics();
    testBehaviour();
    testUnrealStyle();
    testPerformance();
    testTerrain();
    testModelKit();
    printf("SIM native tests: %d passed, %d failed\n", passes, failures);
    return failures == 0 ? 0 : 1;
}
