// DqGame.js — pickups, souls, traps, boss orb, run flow. Every behavior uses kind=<kind> params.
var souls = 0, kills = 0, bossAwake = false, over = false, bossRef = null;
var ox = 0, oz = 0, odmg = 10, ospd = 7, olife = 0;

function start() {
    if (kind != null && kind != "") { bob = Math.random() * 6; return; }   // pickups only bob
    audio.playMusic("Battle.song");
    // seed skeleton guards around the crypt (cloned from the inactive template)
    var spots = [[0, 27], [-8, 6], [8, 6], [0, 3], [20, 6], [-20, 6], [0, -8]];
    for (var i = 0; i < spots.length; i++) {
        var e = scene.spawn("Skeleton", spots[i][0], 0, spots[i][1]);
        if (e != null) e.active = true;
    }
}

var bob = 0;
function update(dt) {
    if (over) return;
    if (kind == "soul") {
        bob += dt;
        self.y = 1.2 + Math.sin(bob * 3) * 0.15;
        self.rotation += 90 * dt;
        var hero = scene.find("Hero");
        if (hero != null && hero.active && Math.abs(hero.x - self.x) < 1 && Math.abs(hero.z - self.z) < 1) collectSoul();
    } else if (kind == "pot") {
        var hero = scene.find("Hero");
        if (hero != null && hero.active && Math.abs(hero.x - self.x) < 0.9 && Math.abs(hero.z - self.z) < 0.9) {
            hero.send("heal", 25);
            burst(10);
            self.destroy();
        }
    } else if (kind == "chest") {
        var hero = scene.find("Hero");
        if (hero != null && hero.active && Math.abs(hero.x - self.x) < 1.1 && Math.abs(hero.z - self.z) < 1.1) openChest();
    } else if (kind == "trap") {
        // spin damage: any hero close to a blade gets cut
        var hero = scene.find("Hero");
        if (hero != null && hero.active) {
            var dx = hero.x - self.x, dz = hero.z - self.z;
            var d2 = dx * dx + dz * dz;
            if (d2 < 1.6) hero.send("hurt", 8);
        }
    } else if (kind == "bossOrb") {
        olife -= dt;
        self.y += dt * 0.4;
        if (olife <= 0) return self.destroy();
        var hero = scene.find("Hero");
        if (hero == null) return;
        var dx = hero.x - self.x, dz = hero.z - self.z;
        var d = Math.sqrt(dx * dx + dz * dz) || 1;
        self.x += dx / d * ospd * dt;
        self.z += dz / d * ospd * dt;
        if (d < 0.9) {
            hero.send("hurt", odmg);
            self.destroy();
        }
    }
}

function fire(cfg) { ox = 0; odmg = cfg.dmg; olife = 4; }

function collectSoul() {
    audio.play("powerup.wav");
    var f = scene.find("SoulFx");
    if (f != null) { f.x = self.x; f.y = 1.2; f.z = self.z; f.burst(40); }
    self.destroy();
    var d = scene.find("DqDirector");
    if (d != null) d.send("soulGot");   // counting lives on the single director instance
}

function soulGot() {
    souls++;
    ui.setText("SoulText", "SOULS " + souls + "/3");
    if (souls >= 3) openGate();
}

function openGate() {
    audio.play("roar.wav", 0.9);
    scene.shake(0.5);
    var gate = scene.find("BossGate");
    if (gate != null) gate.active = false;
    var boss = scene.find("Lich");
    if (boss != null) { boss.active = true; }
    var eye = scene.find("BossEye");
    if (eye != null) eye.active = false;
    ui.setText("SoulText", "THE GATE OPENS!");
    after(3, function () { ui.setText("SoulText", "SLAY THE LICH!"); });
}

function openChest() {
    var gold = chestKind == "big" ? 80 : 30;
    var hero = scene.find("Hero");
    if (hero != null) hero.send("addGold", gold);
    if (chestKind == "big") {
        // big chests also drop a potion next to them
        var pot = scene.spawn("Pot", self.x + 1, 0.4, self.z);
        if (pot != null) pot.active = true;
    }
    var lid = self.child("Lid");
    if (lid != null) lid.rotX = -70;
    kind = "chestOpen"; // can't reopen
    audio.play("win.wav", 0.5, 1.6);
}

function burst(n) {
    var f = scene.find("Sparkle");
    if (f != null) { f.x = self.x; f.y = 0.8; f.z = self.z; f.burst(n); }
}

// ------------------------------------------------------------- director bits
function bossReady(b) { bossRef = b; }

function enemyDown(kind2) {
    kills++;
    var hero = scene.find("Hero");
    if (hero != null) hero.send("gainXp", kind2 == "boss" ? 200 : 20);
    if (kind2 == "boss") return victory();
}

function getWaveish() { return kills / 2; }

function heroDownNow() { }

function gameOverNow() {
    if (over) return;
    over = true;
    scene.setScale(0);
    scene.find("OverStats").setText("Level " + lvl() + "  •  " + kills + " kills  •  " + goldOf() + "g  •  souls " + souls + "/3");
    scene.find("OverPanel").active = true;
    audio.play("lose.wav");
}

function victory() {
    if (over) return;
    over = true;
    scene.setScale(0);
    var wins = storage.getNumber("dq_wins", 0);
    storage.set("dq_wins", wins + 1);
    scene.find("WinStats").setText("The Lich is dust!  Level " + lvl() + "  •  " + kills + " kills  •  " + goldOf() + "g");
    scene.find("WinPanel").active = true;
    audio.play("win.wav");
}

function lvl() { var h = scene.find("Hero"); return h != null ? h.send("getLvl") : 1; }
function goldOf() { var h = scene.find("Hero"); return h != null ? h.send("getGold") : 0; }
