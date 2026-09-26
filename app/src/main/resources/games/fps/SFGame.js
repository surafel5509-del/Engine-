// Strike Force — mission director: objectives (eliminate hostiles, then extract), score, timer, results.
var total = 0, killed = 0, over = false, clock = 0, phase = 1, toastT = 0, score = 0, mission = 1;

function start() {
    mission = scene.name == "NightRaid" ? 2 : 1;
    if (storage.get("sf_music", true)) audio.playMusic("Battle.song", 0.35);
    total = scene.count("Enemy");
    ui.hide("ExtractBeacon");
    objective();
    banner(mission == 1 ? "OPERATION DUST" : "OPERATION NIGHT RAID", 2.5);
}

function update(dt) {
    if (toastT > 0) { toastT -= dt; if (toastT <= 0) ui.hide("ToastText"); }
    if (over) return;
    clock += dt;
    ui.setText("TimerText", formatTime(clock));
    if (phase == 2) {
        var p = scene.find("Player"), z = scene.find("Extraction");
        if (p && z && distance(p.x, p.z, z.x, z.z) < 3) win();
    }
}

function enemyKilled(pts) {
    killed++;
    score += pts;
    toast("HOSTILE DOWN  +" + pts);
    objective();
    if (killed >= total && phase == 1) {
        phase = 2;
        ui.show("ExtractBeacon");
        banner("AREA CLEAR - GET TO EXTRACTION", 2.5);
        audio.play("powerup.wav", 1);
        objective();
    }
}

function objective() {
    if (phase == 1) ui.setText("ObjectiveText", "OBJECTIVE: Eliminate all hostiles  " + killed + "/" + total);
    else ui.setText("ObjectiveText", "OBJECTIVE: Reach the green extraction flare");
    ui.setText("ScoreText", "SCORE " + score);
}

function playerDied() {
    if (over) return;
    over = true;
    ui.setText("LoseStats", "Hostiles eliminated: " + killed + "/" + total + "\nTime: " + formatTime(clock));
    ui.show("GameOverPanel");
    audio.stopMusic();
    audio.play("lose.wav", 1);
}

function win() {
    over = true;
    var acc = scene.find("Player").send("getAccuracy");
    var bonus = Math.max(0, Math.round(600 - clock)) * 5;
    score += bonus;
    var key = "sf_best_" + mission;
    if (score > storage.getNumber(key, 0)) storage.set(key, score);
    storage.set("sf_unlocked", Math.max(storage.getNumber("sf_unlocked", 1), 2));
    ui.setText("WinStats", "Time: " + formatTime(clock) + "\nAccuracy: " + acc + "%\nTime bonus: " + bonus + "\nScore: " + score + "   Best: " + storage.getNumber(key, 0));
    ui.show("WinPanel");
    audio.stopMusic();
    audio.play("win.wav", 1);
}

function toast(t) { ui.setText("ToastText", t); ui.show("ToastText"); toastT = 1.5; }
function banner(t, sec) { ui.setText("BannerText", t); ui.show("BannerText"); after(sec, function () { ui.hide("BannerText"); }); }
