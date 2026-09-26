// Sky Strike level manager: waves, boss, score, stars, win / lose. Param level.
var level = 1;
var wave = 0, waves = [], score = 0, kills = 0, alive = true, done = false;
var waveActive = false, toSpawn = 0, toastT = 0;

function start() {
    if (storage.get("sky_music", true)) audio.playMusic(level == 3 ? "Battle.song" : "Adventure.song", 0.5);
    for (var i = 0; i < 60; i++) scene.spawn("Star", random(-16, 16), random(-10, 10));
    waves = buildWaves(level);
    ui.setText("LevelText", "LEVEL " + level);
    ui.setText("ScoreText", "0");
    banner("LEVEL " + level + "\nGET READY!", 2.2);
    after(2.4, nextWave);
}

function buildWaves(l) {
    var w = [
        [["Drone", 5, 0.7]],
        [["Drone", 4, 0.6], ["Fighter", 3, 0.9]],
        [["Fighter", 6, 0.5]]
    ];
    if (l >= 2) { w.push([["Heavy", 2, 2.2], ["Drone", 6, 0.5]]); w.push([["Fighter", 8, 0.35], ["Heavy", 1, 1]]); }
    else w.push([["Drone", 8, 0.45], ["Fighter", 3, 1]]);
    if (l >= 3) { w.push([["Heavy", 3, 1.6], ["Fighter", 8, 0.3]]); w.push([["Drone", 12, 0.25], ["Heavy", 2, 1.4]]); }
    return w;
}

function nextWave() {
    if (!alive || done) return;
    if (wave >= waves.length) { startBoss(); return; }
    var def = waves[wave];
    wave++;
    ui.setText("WaveText", "WAVE " + wave + "/" + waves.length);
    banner("WAVE " + wave, 1.2);
    toSpawn = 0;
    for (var g = 0; g < def.length; g++) {
        for (var i = 0; i < def[g][1]; i++) { toSpawn++; schedule(def[g][0], 1 + g * 0.4 + i * def[g][2]); }
    }
    waveActive = true;
}

function schedule(name, delay) {
    after(delay, function () {
        if (alive && !done) scene.spawn(name, random(-11, 11), 10.5);
        toSpawn--;
    });
}

function startBoss() {
    ui.setText("WaveText", "BOSS");
    banner("WARNING!\nBOSS APPROACHING", 2.2);
    after(2.4, function () { if (alive) scene.spawn("Boss", 0, 12); });
}

function update(dt) {
    if (toastT > 0) { toastT -= dt; if (toastT <= 0) ui.hide("ToastText"); }
    if (!alive || done) return;
    if (waveActive && toSpawn <= 0 && scene.count("Enemy") == 0) {
        waveActive = false;
        after(1.2, nextWave);
    }
}

function enemyKilled(points) {
    kills++;
    score += points;
    ui.setText("ScoreText", "" + score);
}

function toast(msg) {
    ui.setText("ToastText", msg);
    ui.show("ToastText");
    toastT = 1.4;
}

function banner(msg, sec) {
    ui.setText("BannerText", msg);
    ui.show("BannerText");
    after(sec, function () { ui.hide("BannerText"); });
}

function bossKilled() {
    if (done) return;
    done = true;
    score += 500 * level;
    ui.setText("ScoreText", "" + score);
    var p = scene.find("Player");
    var hpPct = p ? p.send("getHealthPercent") : 0;
    var stars = hpPct >= 0.7 ? 3 : hpPct >= 0.35 ? 2 : 1;
    var key = "sky_stars_" + level;
    if (stars > storage.getNumber(key, 0)) storage.set(key, stars);
    if (storage.getNumber("sky_unlocked", 1) < level + 1) storage.set("sky_unlocked", Math.min(3, level + 1));
    var best = storage.getNumber("sky_best", 0);
    if (score > best) storage.set("sky_best", score);
    var starText = "";
    for (var i = 0; i < 3; i++) starText += i < stars ? "★" : "☆";
    ui.setText("WinStars", starText);
    ui.setText("WinScore", "Score " + score + "   Kills " + kills + (score > best ? "\nNEW HIGH SCORE!" : ""));
    audio.play("powerup.wav");
    after(1.5, function () { ui.show("VictoryPanel"); });
}

function playerDied() {
    if (!alive) return;
    alive = false;
    ui.setText("LoseScore", "Score " + score + "   Wave " + wave);
    after(1.6, function () { ui.show("GameOverPanel"); });
}

function onStop() { audio.stopMusic(); }
