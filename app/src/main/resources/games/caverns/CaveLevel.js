// CaveLevel.js — level flow: timer, gems, win/lose screens and star ratings.
// On the goal gate object + a hidden "LevelManager" object runs this too.
// Params: level, totalGems
var t = 0;
var running = true;
var gems = 0;

function start() {
    t = 0;
    running = self.name == "LevelManager";
    var saved = storage.get("caverns_stars");
    if (saved != null && scene.find("BestText") != null) scene.find("BestText").text = "STARS  " + saved + " / 9";
}

function gemCollected() { gems++; }

function update(dt) {
    if (!running) return;
    t += dt;
    var label = scene.find("TimerText");
    if (label != null) label.text = (Math.round(t * 10) / 10) + "s";
}

function win() {
    if (!running) return;
    running = false;
    var player = scene.find("Player");
    var allGems = gems >= totalGems;
    var stars = 1 + (gems > 0 ? 1 : 0) + (allGems && t < 60 ? 1 : 0);
    if (player != null) { player.active = false; player.send("celebrate"); }
    audio.play("win.wav");
    var panel = scene.find("WinPanel");
    panel.active = true;
    scene.setScale(0);
    var starsText = "";
    for (var i = 0; i < 3; i++) starsText += i < stars ? "★" : "☆";
    scene.find("StarsText").text = starsText;
    scene.find("WinStats").text = "Gems " + gems + "/" + totalGems + "   •   Time " + (Math.round(t * 10) / 10) + "s";
    // save stars + unlock the next cave
    var key = "caverns_stars_" + level;
    var best = storage.get(key) != null ? storage.get(key) : 0;
    if (stars > best) storage.set(key, stars);
    var total = 0;
    for (var lv = 1; lv <= 3; lv++) { var v = storage.get("caverns_stars_" + lv); total += v != null ? v : 0; }
    storage.set("caverns_stars", total);
    if (level < 3) {
        var unlocked = storage.get("caverns_unlocked") != null ? storage.get("caverns_unlocked") : 1;
        if (level + 1 > unlocked) storage.set("caverns_unlocked", level + 1);
        scene.find("WinNextBtn").active = true;
    } else {
        scene.find("WinNextBtn").active = false;
    }
}

function lose() {
    if (!running) return;
    running = false;
    scene.setScale(0);
    scene.find("GameOverPanel").active = true;
    scene.find("OverStats").text = "Gems " + gems + "/" + totalGems + "   •   Time " + (Math.round(t * 10) / 10) + "s";
    audio.play("lose.wav");
}

function nextLevel(name) {
    var next = level + 1;
    if (next > 3) next = 1;
    scene.setScale(1);
    scene.load("Cave" + next);
}
