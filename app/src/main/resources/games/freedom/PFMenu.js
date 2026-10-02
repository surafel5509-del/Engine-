// PRICE OF FREEDOM — menu, records and presentation settings.
function start() {
    input.setControls("none");
    if (storage.get("pf_music", true)) audio.playMusic("MenuTheme.song", 0.42);
    refresh();
}
function refresh() {
    var escapes = storage.getNumber("pf_escapes", 0);
    var best = storage.getNumber("pf_best_day", 0);
    ui.setText("RecordText", escapes > 0 ? "ESCAPES " + escapes + "   •   FASTEST DAY " + best + "   •   LAST ROUTE " + storage.get("pf_last_route", "unknown").toUpperCase() : "NO ESCAPE ON RECORD");
    ui.setText("MusicBtn", "Music: " + (storage.get("pf_music", true) ? "ON" : "OFF"));
    ui.setText("SfxBtn", "Sound FX: " + Math.round(audio.sfxVolume * 100) + "%");
}
function newCampaign() {
    audio.stopMusic();
    scene.load("Prison");
}
function toggleMusic() {
    var on = !storage.get("pf_music", true);
    storage.set("pf_music", on);
    if (on) audio.playMusic("MenuTheme.song", 0.42); else audio.stopMusic();
    refresh();
}
function cycleSfx() {
    var v = audio.sfxVolume + 0.25;
    if (v > 1.01) v = 0;
    audio.sfxVolume = v;
    refresh();
}
function resetRecords() {
    storage.remove("pf_escapes"); storage.remove("pf_best_day"); storage.remove("pf_last_route");
    refresh();
}
function onStop() { audio.stopMusic(); }
