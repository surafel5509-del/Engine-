// Strike Force — first-person soldier: look, sprint-walk, jump, aim down sights, 3 weapons with
// magazines and recoil, hitscan with headshots, grenades, regenerating health (take cover!).
var speed = 5.2, jumpV = 6.5, yaw = 180, pitch = 0, sens = 0.45;
var hp = 100, maxHp = 100, regenT = 0, dead = false, hurt = 0;
var weapons = [
    { name: "M4 CARBINE", model: "Rifle", dmg: 34, rate: 0.1, mag: 30, clip: 30, reserve: 150, reload: 1.7, pellets: 1, spread: 0.018, ads: 0.004, kick: 1.1, auto: true, sound: "pistol.wav", pitch: 0.9 },
    { name: "SPAS-12", model: "Shotgun", dmg: 16, rate: 0.85, mag: 6, clip: 6, reserve: 30, reload: 2.2, pellets: 8, spread: 0.07, ads: 0.05, kick: 4.5, auto: false, sound: "shotgun.wav", pitch: 1 },
    { name: "M1911", model: "Pistol", dmg: 45, rate: 0.22, mag: 8, clip: 8, reserve: -1, reload: 1.2, pellets: 1, spread: 0.02, ads: 0.008, kick: 2.2, auto: false, sound: "pistol.wav", pitch: 1.2 }
];
var current = 0, cooldown = 0, reloading = 0, grenades = 3, grenadeCd = 0, aiming = false, fov = 72;
var cam, gunKick = 0, bob = 0, stepT = 0, kills = 0, shots = 0, hits = 0, firedOnce = false;

function start() {
    input.setControls("fps");
    input.showControls(true);
    cam = scene.camera3D;
    sens = storage.getNumber("sf_sens", 0.45);
    yaw = self.rotY;
    showWeapon();
    hud();
}

function update(dt) {
    if (dead) return;
    // look (with a little ADS slow-down)
    var s = sens * (aiming ? 0.55 : 1);
    yaw -= input.lookX * s;
    pitch = clamp(pitch - input.lookY * s, -80, 80);
    // move relative to view
    var ry = yaw * Math.PI / 180;
    var fx = -Math.sin(ry), fz = -Math.cos(ry), rx = Math.cos(ry), rz = -Math.sin(ry);
    var mx = input.axisX, my = input.axisY;
    var sp = speed * (aiming ? 0.55 : 1);
    self.vx = (fx * my + rx * mx) * sp;
    self.vz = (fz * my + rz * mx) * sp;
    if ((input.buttonDown("A") || input.aDown) && self.grounded) self.vy = jumpV;
    var moving = Math.abs(mx) + Math.abs(my) > 0.2;
    if (moving && self.grounded) {
        bob += dt * 9;
        stepT -= dt;
        if (stepT <= 0) { stepT = 0.4; audio.play("step.wav", 0.2, random(0.9, 1.1)); }
    }
    if (self.y < -10) { self.setPosition(0, 2, 30); self.setVelocity(0, 0, 0); }

    // aim down sights (with mobile aim assist toward a visible hostile near the crosshair)
    var wasAiming = aiming;
    aiming = input.button("Aim");
    if (aiming && !wasAiming) aimAtNearest(14);
    var targetFov = aiming ? (current == 1 ? 60 : 45) : 72;
    fov = smoothDamp(fov, targetFov, 14, dt);
    if (cam) cam.setProp("Camera3D", "Field of View", fov);
    ui.setText("Crosshair", aiming ? "" : "+");

    // weapons
    var w = weapons[current];
    cooldown -= dt; grenadeCd -= dt;
    if (reloading > 0) {
        reloading -= dt;
        ui.setProgress("ReloadBar", 1 - reloading / w.reload);
        if (reloading <= 0) finishReload();
    }
    var trigger = input.button("Fire");
    if (trigger && (w.auto || !firedOnce) && cooldown <= 0 && reloading <= 0) fire();
    firedOnce = trigger;
    if (input.buttonDown("Reload")) startReload();
    if (input.buttonDown("Switch")) switchWeapon();
    if (input.buttonDown("Grenade") && grenades > 0 && grenadeCd <= 0) throwGrenade();

    // health regeneration after 4 s without damage
    regenT -= dt;
    if (regenT <= 0 && hp < maxHp) { hp = Math.min(maxHp, hp + 22 * dt); hud(); }
    if (hurt > 0) { hurt -= dt; if (hurt <= 0) ui.hide("HurtFX"); }
    gunKick = Math.max(0, gunKick - dt * 6);
    placeCamera();
}

/** Turns the view toward the nearest visible hostile within maxAngle degrees (any angle when omitted). */
function aimAtNearest(maxAngle) {
    var list = scene.findInRadius3("Enemy", self.x, self.y, self.z, 60);
    var ox = self.x, oy = self.y + 0.7, oz = self.z;
    for (var i = 0; i < list.length; i++) {
        var e = list[i];
        var dx = e.x - ox, dy = e.y + 0.3 - oy, dz = e.z - oz;
        var h = scene.raycastHit(ox, oy, oz, dx, dy, dz, 80, self);
        if (!h || !h.object || h.object.tag != "Enemy") continue;
        var ty = Math.atan2(-dx, -dz) * 180 / Math.PI;
        var tp = Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI;
        var dyaw = ((ty - yaw + 540) % 360) - 180;
        if (maxAngle && (Math.abs(dyaw) > maxAngle || Math.abs(tp - pitch) > maxAngle)) continue;
        yaw = yaw + dyaw; pitch = tp;
        return true;
    }
    return false;
}

function placeCamera() {
    if (!cam) return;
    cam.setPosition(self.x, self.y + 0.7 + Math.sin(bob) * 0.04, self.z);
    cam.rotY = yaw;
    cam.rotX = pitch + gunKick * 2;
    var g = cam.child(weapons[current].model);
    if (g) {
        if (aiming) g.setPosition(0, -0.16, -0.42 + gunKick * 0.08);
        else g.setPosition(0.26, -0.24 + Math.sin(bob * 0.5) * 0.012, -0.55 + gunKick * 0.12);
    }
}

function dir() {
    var ry = yaw * Math.PI / 180, rp = (pitch + gunKick * 2) * Math.PI / 180;
    return [-Math.sin(ry) * Math.cos(rp), Math.sin(rp), -Math.cos(ry) * Math.cos(rp)];
}

function fire() {
    var w = weapons[current];
    if (w.clip <= 0) { audio.play("empty.wav", 0.6); startReload(); cooldown = 0.3; return; }
    w.clip--;
    shots++;
    cooldown = w.rate;
    var d = dir();
    var ox = self.x, oy = self.y + 0.7, oz = self.z;
    var spread = aiming ? w.ads : w.spread;
    var hitSomething = false, head = false;
    for (var i = 0; i < w.pellets; i++) {
        var dx = d[0] + random(-spread, spread), dy = d[1] + random(-spread, spread), dz = d[2] + random(-spread, spread);
        var h = scene.raycastHit(ox, oy, oz, dx, dy, dz, 120, self);
        if (!h) continue;
        var o = h.object;
        var fxName = "Impact";
        if (o && o.tag == "Enemy") {
            var isHead = h.y > o.y + 0.55;
            var falloff = w.pellets > 1 ? clamp(1.4 - h.distance / 14, 0.2, 1) : 1;
            o.send("hit", w.dmg * falloff * (isHead ? 2.2 : 1));
            hitSomething = true; if (isHead) head = true;
            fxName = "BloodHit";
        } else if (o && o.tag == "Barrel") {
            o.send("hit", w.dmg);
        }
        var fx = scene.spawn(fxName, h.x + h.nx * 0.05, h.y + h.ny * 0.05, h.z + h.nz * 0.05);
        if (fx) { fx.burst(fxName == "Impact" ? 6 : 10); after(0.5, function () { fx.destroy(); }); }
    }
    if (hitSomething) {
        hits++;
        ui.setText("HitMarker", head ? "HEADSHOT" : "x");
        ui.show("HitMarker");
        after(0.12, function () { ui.hide("HitMarker"); });
        audio.play("hit.wav", 0.35, head ? 1.6 : 1.3);
    }
    // alert enemies within earshot
    var near = scene.findInRadius3("Enemy", self.x, self.y, self.z, 35);
    for (var k = 0; k < near.length; k++) near[k].send("alert");
    // recoil & flash
    gunKick = Math.min(1.6, gunKick + w.kick * 0.25);
    pitch = clamp(pitch + w.kick * 0.35, -80, 80);
    yaw += random(-w.kick, w.kick) * 0.15;
    var g = cam ? cam.child(w.model) : null;
    var fl = g ? g.child("Flash") : null;
    if (fl) { fl.setComponentEnabled("MeshRenderer", true); after(0.04, function () { fl.setComponentEnabled("MeshRenderer", false); }); }
    audio.play(w.sound, current == 1 ? 0.9 : 0.5, w.pitch * random(0.95, 1.05));
    if (current == 1) scene.shake(0.12);
    if (w.clip == 0) startReload();
    hud();
}

function startReload() {
    var w = weapons[current];
    if (reloading > 0 || w.clip == w.mag || w.reserve == 0) return;
    reloading = w.reload;
    audio.play("reload.wav", 0.7);
    ui.show("ReloadBar");
    ui.setText("ReloadText", "RELOADING");
}

function finishReload() {
    var w = weapons[current];
    var need = w.mag - w.clip;
    if (w.reserve < 0) w.clip = w.mag;
    else { var take = Math.min(need, w.reserve); w.clip += take; w.reserve -= take; }
    reloading = 0;
    ui.hide("ReloadBar");
    ui.setText("ReloadText", "");
    hud();
}

function switchWeapon() {
    current = (current + 1) % weapons.length;
    reloading = 0;
    ui.hide("ReloadBar"); ui.setText("ReloadText", "");
    audio.play("reload.wav", 0.4, 1.3);
    showWeapon();
    hud();
}

function showWeapon() {
    if (!cam) return;
    for (var i = 0; i < weapons.length; i++) {
        var g = cam.child(weapons[i].model);
        if (g) g.active = i == current;
    }
}

function throwGrenade() {
    grenades--;
    grenadeCd = 1;
    var d = dir();
    var g = scene.spawn("Grenade", self.x + d[0] * 0.8, self.y + 0.9, self.z + d[2] * 0.8);
    if (g) g.setVelocity(d[0] * 13, d[1] * 13 + 4, d[2] * 13);
    audio.play("whoosh.wav", 0.6);
    hud();
}

function damage(n) {
    if (dead) return;
    hp -= n;
    regenT = 4;
    hurt = 0.35;
    ui.show("HurtFX");
    platform.vibrate(25);
    audio.play("hit.wav", 0.7, 0.7);
    scene.shake(0.15);
    hud();
    if (hp <= 0) {
        dead = true;
        self.setVelocity(0, 0, 0);
        if (cam) cam.rotX = 60;
        scene.find("Game").send("playerDied");
    }
}

function addAmmo(n) {
    weapons[0].reserve += 60; weapons[1].reserve += 8; grenades = Math.min(5, grenades + 1);
    audio.play("powerup.wav", 0.7);
    scene.find("Game").send("toast", "AMMO +");
    hud();
}

function heal(n) { hp = Math.min(maxHp, hp + n); hud(); }
function getHp() { return Math.round(hp); }
function getAccuracy() { return shots == 0 ? 0 : Math.round(hits * 100 / shots); }

function hud() {
    var w = weapons[current];
    ui.setText("WeaponText", w.name);
    ui.setText("AmmoText", w.clip + " / " + (w.reserve < 0 ? "INF" : w.reserve));
    ui.setText("GrenadeText", "GRENADES " + grenades);
    ui.setProgress("HealthBar", Math.max(0, hp) / maxHp);
}
