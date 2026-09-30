// ZgGame.js — wave director + bullets + pickups. Also hosts bullet behavior (params bullet=true).
var wave = 0, aliveCount = 0, toSpawn = 0, spawnT = 0, betweenT = 0, state = "idle", cash = 0;

function start() {
    if (bullet) return;
    after(1.5, function () { startWave(); });
}

function update(dt) {
    if (bullet) {
        self.x += bdx * bspd * dt;
        self.z += bdz * bspd * dt;
        self.y = 1.6;
        life -= dt;
        if (life <= 0) return self.destroy();
        var near = scene.findInRadius("Zombie", self.x, self.z, 1.1);
        if (near.length > 0) {
            near[0].send("shot", dmg);
            var goo = scene.find("Goo");
            if (goo != null) { goo.x = self.x; goo.y = 1.2; goo.z = self.z; goo.burst(10); }
            self.destroy();
        }
        return;
    }
    if (state != "wave") return;
    if (toSpawn > 0) {
        spawnT -= dt;
        if (spawnT <= 0) {
            spawnT = Math.max(0.25, 0.9 - wave * 0.05);
            spawnZombie();
        }
    } else if (aliveCount <= 0) {
        waveCleared();
    }
}

// ------------------------------------------------------------- bullets
var bdx = 0, bdz = 0, bspd = 30, dmg = 12, life = 1.6;
function launch(cfg) { bdx = cfg.dx; bdz = cfg.dz; bspd = cfg.speed; dmg = cfg.dmg; life = 1.6; }

// ------------------------------------------------------------- waves
function spawnZombie() {
    toSpawn--;
    aliveCount++;
    var ang = Math.random() * Math.PI * 2;
    var r = 30 + Math.random() * 5;
    var z = scene.spawn("Zombie", Math.cos(ang) * r, 0.85, Math.sin(ang) * r);
    if (z == null) { aliveCount--; return; }
    if (wave >= 3 && Math.random() < 0.15) {
        z.scaleX = z.scaleY = z.scaleZ = 1.6;   // brute!
        z.setColor("#FF8BC34A");
    }
    if (Math.random() < 0.4) audio.play("groan.wav", 0.35, 0.8 + Math.random() * 0.5);
}

function startWave() {
    if (bullet || state == "wave") return;
    state = "wave";
    wave++;
    toSpawn = 4 + wave * 3;
    aliveCount = 0;
    scene.find("ShopPanel").active = false;
    scene.find("WaveText").setText("WAVE " + wave);
    audio.play("powerup.wav", 0.8);
    updateLeft();
}

function zombieDown(z) {
    aliveCount--;
    var reward = z.scaleY > 1.4 ? 25 : 10;
    cash += reward;
    updateLeft();
}

function waveCleared() {
    if (bullet) return;
    state = "shop";
    var best = storage.getNumber("zg_best", 0);
    if (wave > best) storage.set("zg_best", wave);
    audio.play("win.wav", 0.7);
    scene.find("ShopCash").setText(cash + "g");
    scene.find("ShopPanel").active = true;
    scene.find("WaveText").setText("WAVE " + wave + " CLEARED");
}

function truckDown() {
    if (bullet || state == "over") return;
    state = "over";
    scene.setScale(0);
    var best = storage.getNumber("zg_best", 0);
    if (wave > best) storage.set("zg_best", wave);
    scene.find("OverStats").setText("Survived to wave " + wave + "  •  " + cash + "g earned");
    scene.find("OverPanel").active = true;
    audio.play("lose.wav");
}

function updateLeft() {
    scene.find("LeftText").setText(Math.max(0, aliveCount + toSpawn) + " zombies");
}

function getCash() { return cash; }
function getWave() { return wave; }

// shop buttons forward to the car's wallet
function buyRepair(name) { shop("buyRepair"); }
function buyArmor(name) { shop("buyArmor"); }
function buyAmmo(name) { shop("buyAmmo"); }
function buyTurret(name) { shop("buyTurret"); }
function shop(fn) {
    if (state != "shop") return;
    var car = scene.find("Car");
    if (car == null) return;
    car.send(fn);
    scene.find("ShopCash").setText(car.send("getCash") + "g");
}
