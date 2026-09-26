// Dead Zone main menu.
function start() {
    input.setControls("none");
    if (storage.get("dz_music", true)) audio.playMusic("SpookyNight.song", 0.5);
    refresh();
}
function refresh() {
    ui.setText("RecordText", "BEST: WAVE " + storage.getNumber("dz_best_wave", 0) + "   KILLS " + storage.getNumber("dz_best_kills", 0));
    ui.setText("MusicBtn", "Music: " + (storage.get("dz_music", true) ? "ON" : "OFF"));
    ui.setText("DiffBtn", "Difficulty: " + ["Easy", "Normal", "Nightmare"][storage.getNumber("dz_difficulty", 1)]);
    ui.setText("SfxBtn", "Sound FX: " + Math.round(audio.sfxVolume * 100) + "%");
}
function toggleMusic() {
    var m = !storage.get("dz_music", true);
    storage.set("dz_music", m);
    if (m) audio.playMusic("SpookyNight.song", 0.5); else audio.stopMusic();
    refresh();
}
function cycleDifficulty() { storage.set("dz_difficulty", (storage.getNumber("dz_difficulty", 1) + 1) % 3); refresh(); }
function cycleSfx() { var v = audio.sfxVolume + 0.25; if (v > 1.01) v = 0; audio.sfxVolume = v; refresh(); }
