// Strike Force — main menu with a slowly orbiting 3D camera, mission select, settings and records.
var t = 0, cam;
function start() {
    input.setControls("none");
    cam = scene.camera3D;
    if (storage.get("sf_music", true)) audio.playMusic("MenuTheme.song", 0.5);
    refresh();
}
function update(dt) {
    t += dt * 0.12;
    if (cam) {
        cam.setPosition(Math.sin(t) * 9, 3.2, Math.cos(t) * 9);
        cam.rotY = t * 180 / Math.PI;
        cam.rotX = -12;
    }
}
function refresh() {
    var unlocked = storage.getNumber("sf_unlocked", 1);
    ui.setText("Mission1Btn", "OPERATION DUST   best " + storage.getNumber("sf_best_1", 0));
    var b2 = scene.find("Mission2Btn");
    if (b2) { b2.label = unlocked >= 2 ? "NIGHT RAID   best " + storage.getNumber("sf_best_2", 0) : "NIGHT RAID   LOCKED"; b2.interactable = unlocked >= 2; }
    ui.setText("MusicBtn", "Music: " + (storage.get("sf_music", true) ? "ON" : "OFF"));
    ui.setText("DiffBtn", "Difficulty: " + ["Recruit", "Regular", "Veteran"][storage.getNumber("sf_difficulty", 1)]);
    ui.setText("SensBtn", "Look sensitivity: " + Math.round(storage.getNumber("sf_sens", 0.45) * 100));
}
function toggleMusic() {
    var m = !storage.get("sf_music", true);
    storage.set("sf_music", m);
    if (m) audio.playMusic("MenuTheme.song", 0.5); else audio.stopMusic();
    refresh();
}
function cycleDifficulty() { storage.set("sf_difficulty", (storage.getNumber("sf_difficulty", 1) + 1) % 3); refresh(); }
function cycleSens() {
    var s = storage.getNumber("sf_sens", 0.45) + 0.15;
    if (s > 1.01) s = 0.15;
    storage.set("sf_sens", Math.round(s * 100) / 100);
    refresh();
}
