// MiniCraft title screen: orbiting camera over a live voxel world, new / continue, world size.
var angle = 0, cam;
function start() {
    input.setControls("none");
    cam = scene.camera3D;
    if (cam) cam.setProp("Camera3D", "Follow Target", "");
    if (storage.get("mc_music", true)) audio.playMusic("BlockWorld.song", 0.4);
    refresh();
}
function update(dt) {
    angle += dt * 8;
    if (!cam) return;
    var a = angle * Math.PI / 180, cx = 16, cz = 16;
    cam.setPosition(cx + Math.sin(a) * 26, 34, cz + Math.cos(a) * 26);
    cam.lookAt(cx, 18, cz);
}
function refresh() {
    var has = voxel.hasSave("world1") && storage.get("mc_continue", false);
    var b = scene.find("ContinueBtn");
    if (b) { b.interactable = has; b.label = has ? "Continue World" : "No saved world"; }
    ui.setText("SizeBtn", "World size: " + ({ 4: "Small", 6: "Medium", 8: "Large" })[storage.getNumber("mc_size", 6)]);
    ui.setText("MusicBtn", "Music: " + (storage.get("mc_music", true) ? "ON" : "OFF"));
    ui.setText("SeedText", "Seed " + storage.getNumber("mc_seed", 1337));
}
function newWorld() {
    storage.set("mc_seed", Math.floor(random(1, 99999)));
    storage.set("mc_continue", false);
    storage.set("mc_time", 0.3);
    voxel.deleteSave("world1");
    scene.load("World");
}
function continueWorld() { storage.set("mc_continue", true); scene.load("World"); }
function cycleSize() {
    var s = storage.getNumber("mc_size", 6);
    s = s == 4 ? 6 : s == 6 ? 8 : 4;
    storage.set("mc_size", s);
    storage.set("mc_continue", false);
    refresh();
}
function toggleMusic() {
    var m = !storage.get("mc_music", true);
    storage.set("mc_music", m);
    if (m) audio.playMusic("BlockWorld.song", 0.4); else audio.stopMusic();
    refresh();
}
