// Iron Tanks — main menu: mission select with stars and locks, settings, records.
function start() {
    input.setControls("none");
    if (storage.get("it_music", true)) audio.playMusic("MenuTheme.song", 0.5);
    refresh();
}

function refresh() {
    ui.setText("BestText", "BEST SCORE  " + storage.getNumber("it_best", 0));
    ui.setText("MusicBtn", "Music: " + (storage.get("it_music", true) ? "ON" : "OFF"));
    ui.setText("DiffBtn", "Difficulty: " + ["Recruit", "Veteran", "Elite"][storage.getNumber("it_difficulty", 1)]);
    ui.setText("SfxBtn", "Sound FX: " + Math.round(audio.sfxVolume * 100) + "%");
    var unlocked = storage.getNumber("it_unlocked", 1);
    var names = ["BORDER OUTPOST", "RIVER CROSSING", "STEEL FORTRESS"];
    for (var l = 1; l <= 3; l++) {
        var b = scene.find("Mission" + l + "Btn");
        if (!b) continue;
        var stars = storage.getNumber("it_stars_" + l, 0), s = "";
        for (var k = 0; k < 3; k++) s += k < stars ? "★" : "☆";
        b.label = l <= unlocked ? l + ". " + names[l - 1] + "  " + s : l + ". LOCKED";
        b.interactable = l <= unlocked;
    }
}

function go(l) { storage.set("it_level", l); scene.load("Battle"); }
function mission1() { go(1); }
function mission2() { go(2); }
function mission3() { go(3); }

function toggleMusic() {
    var m = !storage.get("it_music", true);
    storage.set("it_music", m);
    if (m) audio.playMusic("MenuTheme.song", 0.5); else audio.stopMusic();
    refresh();
}
function cycleDifficulty() { storage.set("it_difficulty", (storage.getNumber("it_difficulty", 1) + 1) % 3); refresh(); }
function cycleSfx() {
    var v = audio.sfxVolume + 0.25;
    if (v > 1.01) v = 0;
    audio.sfxVolume = v;
    refresh();
}
