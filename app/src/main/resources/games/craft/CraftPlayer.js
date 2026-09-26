// MiniCraft first-person player: look, walk, jump, swim, fly, break & place blocks, hotbar,
// day/night cycle, autosave. Params: pitch (start pitch in degrees)
var pitch = -15;
var speed = 4.6, jumpV = 7.4, yaw = 0, reach = 6.5, sens = 0.5;
var hotbar = [1, 2, 3, 4, 6, 8, 9, 10, 11, 7, 12, 14];
var slot = 0, fly = false, cam, sun, frames = 0, ready = false, actionT = 0, placeT = 0, mined = 0, placed = 0;
var dayT = 0.3, dayLen = 300, skyT = 0, saveT = 60, stepT = 0, spawnX = 48, spawnZ = 48;

function start() {
    input.setControls("first");
    input.showControls(true);
    cam = scene.camera3D;
    sun = scene.find("Sun");
    if (cam) cam.setProp("Camera3D", "Follow Target", "");
    var world = scene.find("World");
    var size = storage.getNumber("mc_size", 6);
    world.setProp("VoxelWorld", "Chunks X", size);
    world.setProp("VoxelWorld", "Chunks Z", size);
    world.setProp("VoxelWorld", "Seed", storage.getNumber("mc_seed", 1337));
    spawnX = size * 8 + 0.5; spawnZ = size * 8 + 0.5;
    dayT = storage.getNumber("mc_time", 0.3);
    if (storage.get("mc_music", true)) audio.playMusic("BlockWorld.song", 0.4);
    buildHotbar();
}

function update(dt) {
    frames++;
    if (!ready) {
        // the world regenerates at the end of the first frame (new seed / size)
        if (frames >= 3 && voxel.ready) {
            ready = true;
            if (storage.get("mc_continue", false) && voxel.load("world1")) {
                self.setPosition(storage.getNumber("mc_px", spawnX), storage.getNumber("mc_py", 40) + 0.2, storage.getNumber("mc_pz", spawnZ));
                yaw = storage.getNumber("mc_yaw", 0);
                toast("World loaded");
            } else respawn();
        }
        placeCamera();
        return;
    }
    // look
    yaw -= input.lookX * sens;
    pitch = clamp(pitch - input.lookY * sens, -89, 89);
    // move relative to the view direction
    var ry = yaw * Math.PI / 180;
    var fx = -Math.sin(ry), fz = -Math.cos(ry), rx = Math.cos(ry), rz = -Math.sin(ry);
    var mx = input.axisX, my = input.axisY;
    var head = voxel.getBlock(self.x, self.y + 0.6, self.z), feet = voxel.getBlock(self.x, self.y - 0.5, self.z);
    var inWater = head == Block.WATER || feet == Block.WATER;
    var sp = speed * (inWater ? 0.55 : 1) * (fly ? 2.2 : 1);
    self.vx = (fx * my + rx * mx) * sp;
    self.vz = (fz * my + rz * mx) * sp;
    var jump = input.a || input.button("A");
    if (fly) self.vy = jump ? 6 : (input.b ? -6 : 0);
    else if (inWater) { if (jump) self.vy = 3.2; else if (self.vy < -2.5) self.vy = -2.5; }
    else if (jump && self.grounded) { self.vy = jumpV; }
    // auto-jump one-block steps
    if (!fly && self.grounded && (Math.abs(mx) + Math.abs(my)) > 0.3) {
        var dx = (fx * my + rx * mx), dz = (fz * my + rz * mx), l = Math.sqrt(dx * dx + dz * dz) || 1;
        var ax = self.x + dx / l * 0.75, az = self.z + dz / l * 0.75;
        if (solid(voxel.getBlock(ax, self.y - 0.6, az)) && !solid(voxel.getBlock(ax, self.y + 0.5, az)) && !solid(voxel.getBlock(self.x, self.y + 1.2, self.z))) self.vy = 6.2;
        stepT -= dt;
        if (stepT <= 0) { stepT = 0.42; audio.play("step.wav", 0.2); }
    }
    if (self.y < -12) { respawn(); toast("You fell out of the world!"); }
    placeCamera();
    // break / place
    actionT -= dt; placeT -= dt;
    if (input.button("Break") && actionT <= 0) { actionT = 0.22; breakBlock(); }
    if (input.button("Place") && placeT <= 0) { placeT = 0.25; placeBlock(); }
    if (input.tapped && !input.button("look")) { /* taps are handled by UI buttons */ }
    highlight();
    dayNight(dt);
    saveT -= dt;
    if (saveT <= 0) { saveT = 60; save(); }
}

function solid(b) { return b != Block.AIR && b != Block.WATER; }

function placeCamera() {
    if (!cam) return;
    cam.setPosition(self.x, self.y + 0.65, self.z);
    cam.rotY = yaw;
    cam.rotX = pitch;
}

function ray() {
    var ry = yaw * Math.PI / 180, rp = pitch * Math.PI / 180;
    var dx = -Math.sin(ry) * Math.cos(rp), dy = Math.sin(rp), dz = -Math.cos(ry) * Math.cos(rp);
    return voxel.raycast(self.x, self.y + 0.65, self.z, dx, dy, dz, reach);
}

function breakBlock() {
    var h = ray();
    if (!h || h.block == Block.BEDROCK) return;
    voxel.setBlock(h.x, h.y, h.z, Block.AIR);
    mined++;
    var fx = scene.spawn("BreakFx", h.x + 0.5, h.y + 0.5, h.z + 0.5);
    if (fx) { fx.burst(18); after(0.8, function () { fx.destroy(); }); }
    audio.play("break.wav", 0.6, random(0.9, 1.1));
    platform.vibrate(12);
}

function placeBlock() {
    var h = ray();
    if (!h) return;
    var x = Math.floor(h.x + h.nx), y = Math.floor(h.y + h.ny), z = Math.floor(h.z + h.nz);
    // don't place inside the player
    if (Math.abs(x + 0.5 - self.x) < 0.8 && Math.abs(z + 0.5 - self.z) < 0.8 && y + 1 > self.y - 0.9 && y < self.y + 0.9) return;
    if (solid(voxel.getBlock(x, y, z))) return;
    voxel.setBlock(x, y, z, hotbar[slot]);
    placed++;
    audio.play("place.wav", 0.6);
}

function highlight() {
    var sel = scene.find("Selection");
    if (!sel) return;
    var h = ray();
    if (h) { sel.active = true; sel.setPosition(h.x + 0.5, h.y + 0.5, h.z + 0.5); }
    else sel.active = false;
}

function respawn() {
    var y = voxel.surfaceY(spawnX, spawnZ);
    self.setPosition(spawnX, y + 1.2, spawnZ);
    self.setVelocity(0, 0, 0);
}

function buildHotbar() {
    for (var i = 0; i < 12; i++) {
        var b = scene.find("Slot" + i);
        if (b) b.label = voxel.blockName(hotbar[i]);
    }
    selectSlot("Slot0");
}

function selectSlot(btn) {
    slot = parseInt(String(btn).replace("Slot", ""));
    for (var i = 0; i < 12; i++) {
        var b = scene.find("Slot" + i);
        if (b) b.setProp("UIButton", "Color", i == slot ? "#FF22C55E" : "#CC1F2937");
    }
    ui.setText("BlockText", voxel.blockName(hotbar[slot]));
}

function toggleFly() {
    fly = !fly;
    toast(fly ? "Fly mode ON (A = up, B = down)" : "Fly mode OFF");
    ui.setText("FlyBtn", fly ? "WALK" : "FLY");
}

function dayNight(dt) {
    dayT = (dayT + dt / dayLen) % 1;
    skyT -= dt;
    if (skyT > 0) return;
    skyT = 0.5;
    var sunH = Math.sin(dayT * Math.PI * 2);            // -1..1
    var light = clamp(sunH * 1.4 + 0.25, 0.12, 1.1);
    if (sun) { sun.setProp("Light", "Intensity", light); sun.rotX = -10 - Math.max(0, sunH) * 70; }
    var t = clamp(sunH * 1.5 + 0.4, 0, 1);
    if (cam) {
        cam.setProp("Camera3D", "Sky Top", mix([10, 14, 40], [59, 123, 212], t));
        cam.setProp("Camera3D", "Sky Horizon", mix([30, 30, 60], [191, 216, 240], t));
    }
    ui.setText("ClockText", (sunH > 0 ? "DAY " : "NIGHT ") + Math.floor(dayT * 24) + ":00");
}

function mix(a, b, t) {
    var s = "#FF";
    for (var i = 0; i < 3; i++) { var v = Math.round(a[i] + (b[i] - a[i]) * t); s += (v < 16 ? "0" : "") + v.toString(16); }
    return s;
}

function toast(msg) {
    ui.setText("ToastText", msg);
    ui.show("ToastText");
    after(2, function () { ui.hide("ToastText"); });
}

function save() {
    if (!ready) return;
    voxel.save("world1");
    storage.set("mc_px", self.x); storage.set("mc_py", self.y); storage.set("mc_pz", self.z);
    storage.set("mc_yaw", yaw); storage.set("mc_time", dayT);
    storage.set("mc_continue", true);
}

function saveGame() { save(); toast("World saved"); }
function saveAndQuit() { save(); scene.load("CraftMenu"); }
function getMined() { return mined; }
function onStop() { audio.stopMusic(); }
