// SliceDirector.js — spawns waves, tracks score / combos / lives / mode.
var score = 0, combo = 0, comboTimer = 0, lives = 3, t = 0, spawnT = 1, frenzy = false, frenzyLeft = 0, over = false, wave = 0;

function start() {
    frenzy = storage.get("slice_mode") == "frenzy";
    lives = frenzy ? 99 : 3;
    frenzyLeft = 60;
    scene.find("ModeText").text = frenzy ? "FRENZY 60s" : "CLASSIC";
    if (frenzy) scene.find("LivesText").text = "∞";
    audio.playMusic("LoFi.song");
}

function update(dt) {
    if (over) return;
    t += dt;
    if (frenzy) {
        frenzyLeft -= dt;
        scene.find("ModeText").text = "FRENZY " + Math.ceil(frenzyLeft) + "s";
        if (frenzyLeft <= 0) return gameOver(false);
        spawnT -= dt * 2.2;
    } else {
        spawnT -= dt;
    }
    if (spawnT <= 0) {
        wave++;
        spawnT = Math.max(0.45, 1.5 - wave * 0.03) * (0.7 + Math.random() * 0.6);
        var burst = 1 + (Math.random() < 0.25 ? 1 : 0) + (frenzy ? 1 : 0);
        for (var i = 0; i < burst; i++) after(i * 0.12, spawnOne);
    }
    comboTimer -= dt;
    if (comboTimer <= 0 && combo > 0) { combo = 0; scene.find("ComboText").text = ""; }
}

function spawnOne() {
    if (over) return;
    var bomb = Math.random() < (frenzy ? 0.13 : Math.min(0.2, 0.06 + wave * 0.006));
    var tpl = bomb ? scene.find("Bomb") : scene.find("Fruit" + Math.floor(Math.random() * 6));
    if (tpl == null) return;
    var f = scene.spawn(tpl.name, -3.5 + Math.random() * 7, 6.5);
    if (f == null) return;
    f.active = true;
    f.vx = (3.5 - (f.x + 3.5)) * 0.4 + (Math.random() - 0.5) * 1.5;
    f.vy = 9.5 + Math.random() * 2.5;
}

// called by fruit/bomb when sliced or missed
function sliced(isBomb, x, y) {
    if (over) return;
    if (isBomb) return gameOver(true);
    combo++;
    comboTimer = 1.1;
    var points = 10 * combo;
    score += points;
    scene.find("ScoreText").text = "" + score;
    if (combo > 1) scene.find("ComboText").text = "COMBO x" + combo + "  +" + points;
    if (combo >= 4) { scene.shake(0.15); audio.play("powerup.wav", 0.5); }
}

function missed() {
    if (over || frenzy) return;
    lives--;
    drawLives();
    scene.shake(0.2);
    audio.play("hit.wav", 0.7);
    if (lives <= 0) gameOver(false);
}

function gameOver(byBomb) {
    if (over) return;
    over = true;
    scene.setScale(0);
    var best = storage.get("slice_best") != null ? storage.get("slice_best") : 0;
    if (score > best) { best = score; storage.set("slice_best", score); }
    scene.find("OverTitle").text = byBomb ? "BOOM!" : "RUN OVER";
    scene.find("OverScore").text = "SCORE " + score;
    scene.find("OverBest").text = "BEST " + best;
    scene.find("OverPanel").active = true;
    audio.play("lose.wav");
}

function drawLives() {
    var s = "";
    for (var i = 0; i < Math.max(0, lives); i++) s += "♥";
    scene.find("LivesText").text = s;
}
