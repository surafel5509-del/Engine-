// Iron Tanks — player tank: hull steers toward the left stick, turret aims with the right stick
// (auto-aims at the nearest enemy when idle). A/Fire shoots, B/Special fires a high-explosive shell.
var speed = 4.2, turnRate = 260, hp = 100, maxHp = 100, dead = false, invuln = 0;
var hull = 90, turret = 90, cooldown = 0, heCd = 0, rateMul = 1, dmgMul = 1, shield = 0;
var spawnX = 0, spawnY = 0, trackT = 0;

function start() {
    input.setControls("twin");
    input.showControls(true);
    spawnX = self.x; spawnY = self.y;
    hull = self.rotation; turret = hull;
    hud();
}

function setSpawn() { spawnX = self.x; spawnY = self.y; }

function angleDiff(a, b) {
    var d = (a - b) % 360;
    if (d > 180) d -= 360;
    if (d < -180) d += 360;
    return d;
}

function update(dt) {
    if (dead) { self.vx = 0; self.vy = 0; return; }
    cooldown -= dt; heCd -= dt; invuln -= dt;
    var fx = self.child("ShieldFX");
    if (shield > 0) shield -= dt;
    if (fx) fx.visible = shield > 0 || invuln > 0;

    var mx = input.axisX, my = input.axisY;
    var mag = Math.sqrt(mx * mx + my * my);
    if (mag > 0.2) {
        var target = Math.atan2(my, mx) * 180 / Math.PI;
        var diff = angleDiff(target, hull);
        var step = turnRate * dt;
        hull += clamp(diff, -step, step);
        var align = Math.cos(diff * Math.PI / 180);
        var sp = speed * Math.min(1, mag) * Math.max(0, align);
        self.vx = Math.cos(hull * Math.PI / 180) * sp;
        self.vy = Math.sin(hull * Math.PI / 180) * sp;
        trackT -= dt;
        if (trackT <= 0) {
            trackT = 0.12;
            var d = scene.spawn("TrackDust", self.x - Math.cos(hull * Math.PI / 180) * 0.5, self.y - Math.sin(hull * Math.PI / 180) * 0.5);
            if (d) { d.burst(2); after(0.5, function () { d.destroy(); }); }
        }
    } else {
        self.vx *= 0.8; self.vy *= 0.8;
    }
    self.rotation = hull;

    var ax = input.axis2X, ay = input.axis2Y;
    var aiming = ax * ax + ay * ay > 0.09;
    var desired = hull;
    if (aiming) desired = Math.atan2(ay, ax) * 180 / Math.PI;
    else {
        var e = scene.nearest("Enemy", self.x, self.y);
        if (e && self.distanceTo(e) < 9) desired = angleTo(self.x, self.y, e.x, e.y);
    }
    turret += clamp(angleDiff(desired, turret), -320 * dt, 320 * dt);
    var t = self.child("Turret");
    if (t) t.rotation = turret - hull;

    var locked = Math.abs(angleDiff(desired, turret)) < 8;
    if ((aiming || input.button("Fire") || input.a || (!aiming && locked && desired != hull)) && cooldown <= 0) fire(false);
    if ((input.buttonDown("Special") || input.bDown) && heCd <= 0) fire(true);
    ui.setProgress("HEBar", 1 - Math.max(0, heCd) / 4);
}

function fire(he) {
    var a = turret * Math.PI / 180;
    var gx = self.x + Math.cos(a) * 0.95, gy = self.y + Math.sin(a) * 0.95;
    var s = scene.spawn(he ? "HEShell" : "Shell", gx, gy);
    if (s) {
        var v = he ? 11 : 15;
        s.vx = Math.cos(a) * v; s.vy = Math.sin(a) * v;
        s.rotation = turret;
        s.send("setOwner", "player");
        s.send("setDamage", (he ? 3 : 1) * dmgMul);
    }
    var m = scene.spawn("Flash", gx, gy);
    if (m) { m.rotation = turret; after(0.06, function () { m.destroy(); }); }
    audio.play(he ? "shotgun.wav" : "pistol.wav", he ? 0.8 : 0.45, he ? 0.6 : 0.75);
    if (he) { heCd = 4; scene.shake(0.25); } else cooldown = 0.42 * rateMul;
}

function damage(n) {
    if (dead || invuln > 0 || shield > 0) return;
    hp -= n;
    invuln = 0.25;
    scene.shake(0.3);
    platform.vibrate(40);
    audio.play("hit.wav", 0.8);
    hud();
    if (hp <= 0) die();
}

function die() {
    dead = true;
    var b = scene.spawn("BigBoom", self.x, self.y);
    if (b) { b.burst(50); after(1, function () { b.destroy(); }); }
    var sc = scene.spawn("Scorch", self.x, self.y);
    audio.play("explosion.wav", 1);
    scene.shake(0.8);
    self.x = -1000; self.y = -1000;
    scene.find("Game").send("playerDied");
}

function respawn() {
    self.x = spawnX; self.y = spawnY;
    hull = 90; turret = 90;
    hp = maxHp; dead = false; shield = 3;
    hud();
}

function pickup(kind) {
    if (kind == "repair") { hp = Math.min(maxHp, hp + 50); toast("REPAIRED +50"); }
    else if (kind == "star") { rateMul = Math.max(0.45, rateMul * 0.8); dmgMul = Math.min(3, dmgMul + 0.5); toast("FIREPOWER UP"); }
    else if (kind == "shield") { shield = 8; toast("SHIELD 8s"); }
    else if (kind == "life") { scene.find("Game").send("extraLife"); toast("EXTRA LIFE"); }
    audio.play("powerup.wav", 0.8);
    hud();
}

function toast(t) { scene.find("Game").send("toast", t); }

function hud() {
    ui.setProgress("HealthBar", hp / maxHp);
    ui.setText("HpText", Math.max(0, Math.round(hp)) + " / " + maxHp);
}
