// Dead Zone wave director: spawns zombies, tracks score, offers upgrades between waves.
var wave = 0, toSpawn = 0, spawnT = 0, alive = true, between = false, kills = 0, score = 0, toastT = 0;
var spawnPoints = [];
var upgrades = [
    ["damage", "DAMAGE +35%"], ["rate", "FIRE RATE +25%"], ["health", "MAX HEALTH +25"],
    ["speed", "MOVE SPEED +"], ["ammo", "BIG AMMO CRATE"]
];
var offered = [];

function start() {
    if (storage.get("dz_music", true)) audio.playMusic("SpookyNight.song", 0.55);
    var pts = scene.findAll("SpawnPoint");
    for (var i = 0; i < pts.length; i++) spawnPoints.push([pts[i].x, pts[i].y]);
    banner("SURVIVE THE NIGHT", 2);
    after(2.2, nextWave);
}

function getWave() { return Math.max(1, wave); }

function nextWave() {
    if (!alive) return;
    wave++;
    between = false;
    toSpawn = 6 + wave * 3;
    spawnT = 0.5;
    ui.setText("WaveText", "WAVE " + wave);
    banner("WAVE " + wave, 1.6);
    audio.play("roar.wav", 0.5);
}

function update(dt) {
    if (toastT > 0) { toastT -= dt; if (toastT <= 0) ui.hide("ToastText"); }
    if (!alive || between) return;
    if (toSpawn > 0) {
        spawnT -= dt;
        if (spawnT <= 0 && scene.count("Zombie") < 22 + wave) {
            spawnT = Math.max(0.25, 0.9 - wave * 0.05);
            spawnOne();
        }
    } else if (scene.count("Zombie") == 0) {
        waveCleared();
    }
    ui.setText("ZombiesText", "ZOMBIES " + (toSpawn + scene.count("Zombie")));
}

function spawnOne() {
    var p = scene.find("Player");
    var best = null;
    for (var tries = 0; tries < 6; tries++) {
        var sp = pick(spawnPoints);
        if (!p || distance(sp[0], sp[1], p.x, p.y) > 9) { best = sp; break; }
        best = sp;
    }
    var kind = "Walker";
    var r = Math.random();
    if (wave >= 5 && r < 0.08 + wave * 0.01) kind = "Brute";
    else if (wave >= 3 && r < 0.45) kind = "Runner";
    scene.spawn(kind, best[0] + random(-0.8, 0.8), best[1] + random(-0.8, 0.8));
    toSpawn--;
}

function waveCleared() {
    between = true;
    score += wave * 100;
    ui.setText("ScoreText", "SCORE " + score);
    banner("WAVE " + wave + " CLEARED!", 1.5);
    audio.play("win.wav", 0.6);
    after(1.6, offerUpgrades);
}

function offerUpgrades() {
    if (!alive) return;
    var pool = upgrades.slice();
    offered = [];
    for (var i = 0; i < 3; i++) {
        var k = Math.floor(Math.random() * pool.length);
        offered.push(pool[k]);
        pool.splice(k, 1);
        ui.setText("Upgrade" + (i + 1), offered[i][1]);
    }
    ui.show("UpgradePanel");
}

function pickUpgrade(buttonName) {
    var i = parseInt(String(buttonName).replace("Upgrade", "")) - 1;
    if (!(i >= 0 && i < offered.length)) return;
    scene.find("Player").send("upgrade", offered[i][0]);
    ui.hide("UpgradePanel");
    toast(offered[i][1]);
    after(1.2, nextWave);
}

function zombieKilled(points) {
    kills++;
    score += points;
    ui.setText("ScoreText", "SCORE " + score);
    ui.setText("KillsText", "KILLS " + kills);
}

function toast(msg) {
    ui.setText("ToastText", msg);
    ui.show("ToastText");
    toastT = 1.3;
}

function banner(msg, sec) {
    ui.setText("BannerText", msg);
    ui.show("BannerText");
    after(sec, function () { ui.hide("BannerText"); });
}

function playerDied() {
    if (!alive) return;
    alive = false;
    var bestWave = storage.getNumber("dz_best_wave", 0), bestKills = storage.getNumber("dz_best_kills", 0);
    var record = wave > bestWave || kills > bestKills;
    if (wave > bestWave) storage.set("dz_best_wave", wave);
    if (kills > bestKills) storage.set("dz_best_kills", kills);
    ui.setText("DeadStats", "You survived " + wave + " waves\nKills " + kills + "   Score " + score + (record ? "\nNEW RECORD!" : ""));
    after(1.5, function () { ui.show("GameOverPanel"); });
}

function onStop() { audio.stopMusic(); }
