// Iron Tanks — mission director: enemy waves from the spawn gates, lives, score, stars, win/lose.
var LEVELS = [
    { count: 10, alive: 3, heavy: 0.08, fast: 0.2 },
    { count: 14, alive: 4, heavy: 0.2, fast: 0.3 },
    { count: 18, alive: 5, heavy: 0.3, fast: 0.35 }
];
var level = 1, L = null, toSpawn = 0, spawnT = 0, maxAlive = 3, over = false, score = 0, kills = 0, lives = 3;
var spawns = [], toastT = 0, clock = 0;

function start() {
    level = clamp(storage.getNumber("it_level", 1), 1, 3);
    L = LEVELS[level - 1];
    for (var m = 1; m <= 3; m++) { if (m == level) ui.show("Map" + m); else ui.hide("Map" + m); }
    var starts = scene.findAll("PlayerStart");
    var pl = scene.find("Player");
    if (pl && starts.length > 0) { pl.x = starts[0].x; pl.y = starts[0].y; pl.send("setSpawn"); }
    if (storage.get("it_music", true)) audio.playMusic("Battle.song", 0.45);
    var pts = scene.findAll("EnemySpawn");
    for (var i = 0; i < pts.length; i++) spawns.push([pts[i].x, pts[i].y]);
    toSpawn = L.count;
    maxAlive = L.alive + storage.getNumber("it_difficulty", 1) - 1;
    spawnT = 1.2;
    ui.setText("LevelText", "MISSION " + level);
    banner("MISSION " + level, 2);
    hud();
}

function update(dt) {
    if (toastT > 0) { toastT -= dt; if (toastT <= 0) ui.hide("ToastText"); }
    if (over) return;
    clock += dt;
    if (toSpawn > 0) {
        spawnT -= dt;
        if (spawnT <= 0 && scene.count("Enemy") < maxAlive) { spawnT = 2.0; spawnEnemy(); }
    } else if (scene.count("Enemy") == 0) win();
}

function spawnEnemy() {
    var sp = pick(spawns);
    var r = Math.random();
    var kind = r < L.heavy ? "HeavyTank" : (r < L.heavy + L.fast ? "FastTank" : "EnemyTank");
    var fx = scene.spawn("SpawnFX", sp[0], sp[1]);
    if (fx) { fx.burst(24); after(0.8, function () { fx.destroy(); }); }
    scene.spawn(kind, sp[0], sp[1]);
    toSpawn--;
    hud();
}

function enemyKilled(p) { kills++; score += p; hud(); }
function extraLife() { lives++; hud(); }

function playerDied() {
    if (over) return;
    lives--;
    hud();
    if (lives <= 0) { lose("YOUR TANK WAS DESTROYED"); return; }
    banner("LIVES LEFT: " + lives, 1.4);
    after(1.6, function () { if (!over) scene.find("Player").send("respawn"); });
}

function baseDestroyed() { lose("THE HQ HAS FALLEN"); }

function win() {
    over = true;
    var stars = lives >= 3 ? 3 : (lives == 2 ? 2 : 1);
    var bonus = lives * 500 + Math.max(0, Math.round(300 - clock)) * 5;
    score += bonus;
    storage.set("it_stars_" + level, Math.max(stars, storage.getNumber("it_stars_" + level, 0)));
    storage.set("it_unlocked", Math.max(storage.getNumber("it_unlocked", 1), Math.min(3, level + 1)));
    if (score > storage.getNumber("it_best", 0)) storage.set("it_best", score);
    var s = "";
    for (var k = 0; k < 3; k++) s += k < stars ? "★" : "☆";
    ui.setText("WinStars", s);
    ui.setText("WinStats", "Tanks destroyed: " + kills + "\nBonus: " + bonus + "\nScore: " + score + "   Best: " + storage.getNumber("it_best", 0));
    ui.setText("NextBtn", level < 3 ? "Next Mission" : "Play Again");
    ui.show("WinPanel");
    audio.stopMusic();
    audio.play("win.wav", 1);
    hud();
}

function lose(msg) {
    over = true;
    if (score > storage.getNumber("it_best", 0)) storage.set("it_best", score);
    ui.setText("LoseReason", msg);
    ui.setText("LoseStats", "Tanks destroyed: " + kills + "   Score: " + score);
    ui.show("GameOverPanel");
    audio.stopMusic();
    audio.play("lose.wav", 1);
}

function nextLevel() {
    storage.set("it_level", level < 3 ? level + 1 : 1);
    scene.reload();
}

function toast(t) { ui.setText("ToastText", t); ui.show("ToastText"); toastT = 1.6; }

function banner(t, sec) {
    ui.setText("BannerText", t); ui.show("BannerText");
    after(sec, function () { ui.hide("BannerText"); });
}

function hud() {
    ui.setText("ScoreText", "SCORE " + score);
    ui.setText("LivesText", "LIVES " + lives);
    ui.setText("EnemiesText", "ENEMIES " + (toSpawn + scene.count("Enemy")));
    ui.setText("KillsText", "KILLS " + kills);
}
