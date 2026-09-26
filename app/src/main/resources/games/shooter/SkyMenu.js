// Sky Strike menus: settings, level select, stars and high score.
function start() {
    input.setControls("none");
    if (storage.get("sky_music", true)) audio.playMusic("MenuTheme.song", 0.5);
    for (var i = 0; i < 70; i++) scene.spawn("Star", random(-16, 16), random(-10, 10));
    refresh();
}

function refresh() {
    ui.setText("BestText", "HIGH SCORE  " + storage.getNumber("sky_best", 0));
    var music = storage.get("sky_music", true);
    var diff = storage.getNumber("sky_difficulty", 1);
    ui.setText("MusicBtn", "Music: " + (music ? "ON" : "OFF"));
    ui.setText("DiffBtn", "Difficulty: " + ["Easy", "Normal", "Hard"][diff]);
    ui.setText("SfxBtn", "Sound FX: " + Math.round(audio.sfxVolume * 100) + "%");
    var unlocked = storage.getNumber("sky_unlocked", 1);
    for (var l = 1; l <= 3; l++) {
        var b = scene.find("Level" + l + "Btn");
        if (!b) continue;
        var stars = storage.getNumber("sky_stars_" + l, 0), s = "";
        for (var k = 0; k < 3; k++) s += k < stars ? "★" : "☆";
        b.label = l <= unlocked ? "LEVEL " + l + "   " + s : "LEVEL " + l + "   LOCKED";
        b.interactable = l <= unlocked;
    }
}

function toggleMusic() {
    var m = !storage.get("sky_music", true);
    storage.set("sky_music", m);
    if (m) audio.playMusic("MenuTheme.song", 0.5); else audio.stopMusic();
    refresh();
}

function cycleDifficulty() {
    storage.set("sky_difficulty", (storage.getNumber("sky_difficulty", 1) + 1) % 3);
    refresh();
}

function cycleSfx() {
    var v = audio.sfxVolume + 0.25;
    if (v > 1.01) v = 0;
    audio.sfxVolume = v;
    storage.set("sfx_volume", v);
    refresh();
}

function resetProgress() {
    storage.set("sky_unlocked", 1);
    for (var l = 1; l <= 3; l++) storage.set("sky_stars_" + l, 0);
    storage.set("sky_best", 0);
    refresh();
}
