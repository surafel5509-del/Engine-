// Dead Zone survivor: twin-stick movement + aiming, 3 weapons, reload, pickups, upgrades.
var speed = 5.5, maxHp = 100, hp = 100, dead = false, invuln = 0;
var weapons = [
    { name: "PISTOL", damage: 1, rate: 0.26, mag: 12, clip: 12, reserve: -1, reload: 1.0, pellets: 1, spread: 0.04, sound: "pistol.wav" },
    { name: "SHOTGUN", damage: 1, rate: 0.75, mag: 6, clip: 6, reserve: 12, reload: 1.6, pellets: 6, spread: 0.32, sound: "shotgun.wav" },
    { name: "RIFLE", damage: 1, rate: 0.09, mag: 30, clip: 30, reserve: 60, reload: 1.4, pellets: 1, spread: 0.07, sound: "pistol.wav" }
];
var current = 0, cooldown = 0, reloading = 0, damageMul = 1, rateMul = 1, facing = 0, stepT = 0;

function start() {
    input.setControls("twin");
    input.showControls(true);
    hud();
}

function update(dt) {
    if (dead) return;
    var mx = input.axisX, my = input.axisY;
    self.vx = mx * speed;
    self.vy = my * speed;
    if (Math.abs(mx) + Math.abs(my) > 0.2) {
        stepT -= dt;
        if (stepT <= 0) { stepT = 0.38; audio.play("step.wav", 0.25); }
    }
    var ax = input.axis2X, ay = input.axis2Y;
    var aiming = ax * ax + ay * ay > 0.09;
    if (aiming) facing = Math.atan2(ay, ax);
    else if (Math.abs(mx) + Math.abs(my) > 0.1) facing = Math.atan2(my, mx);
    else {
        // auto-aim at the nearest zombie when idle
        var z = scene.nearest("Zombie", self.x, self.y);
        if (z && self.distanceTo(z) < 7) facing = Math.atan2(z.y - self.y, z.x - self.x);
    }
    transform.rotation = facing * 180 / Math.PI;
    cooldown -= dt; invuln -= dt;
    var w = weapons[current];
    if (reloading > 0) {
        reloading -= dt;
        ui.setProgress("ReloadBar", 1 - reloading / w.reload);
        if (reloading <= 0) finishReload();
    }
    var wantFire = aiming || input.button("Fire") || input.a;
    if (wantFire && cooldown <= 0 && reloading <= 0) fire();
    if (input.buttonDown("Reload")) startReload();
    if (input.buttonDown("Special") || input.bDown) switchWeapon();
}

function fire() {
    var w = weapons[current];
    if (w.clip <= 0) { audio.play("empty.wav", 0.6); startReload(); cooldown = 0.3; return; }
    w.clip--;
    cooldown = w.rate * rateMul;
    var gx = self.x + Math.cos(facing) * 0.7, gy = self.y + Math.sin(facing) * 0.7;
    for (var i = 0; i < w.pellets; i++) {
        var a = facing + random(-w.spread, w.spread);
        var b = scene.spawn("Bullet", gx, gy);
        if (b) {
            b.vx = Math.cos(a) * 22; b.vy = Math.sin(a) * 22;
            b.rotation = a * 180 / Math.PI;
            b.send("setDamage", w.damage * damageMul);
        }
    }
    var fx = scene.spawn("Muzzle", gx, gy);
    if (fx) { fx.rotation = facing * 180 / Math.PI; after(0.05, function () { fx.destroy(); }); }
    audio.play(w.sound, current == 1 ? 0.9 : 0.5);
    if (current == 1) scene.shake(0.15);
    if (w.clip == 0) startReload();
    hud();
}

function startReload() {
    var w = weapons[current];
    if (reloading > 0 || w.clip == w.mag || w.reserve == 0) return;
    reloading = w.reload;
    audio.play("reload.wav", 0.7);
    ui.show("ReloadBar");
    ui.setText("ReloadText", "RELOADING...");
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
    for (var i = 1; i <= weapons.length; i++) {
        var n = (current + i) % weapons.length;
        if (weapons[n].reserve != 0 || weapons[n].clip > 0) { current = n; break; }
    }
    reloading = 0;
    ui.hide("ReloadBar");
    ui.setText("ReloadText", "");
    audio.play("reload.wav", 0.4);
    hud();
}

function damage(n) {
    if (dead || invuln > 0) return;
    hp -= n;
    invuln = 0.35;
    scene.shake(0.3);
    platform.vibrate(35);
    audio.play("hit.wav", 0.8);
    var fx = scene.spawn("Blood", self.x, self.y);
    if (fx) { fx.burst(14); after(0.8, function () { fx.destroy(); }); }
    if (hp <= 0) { hp = 0; die(); }
    hud();
}

function pickup(kind) {
    if (kind == "medkit") { hp = Math.min(maxHp, hp + 40); audio.play("powerup.wav"); }
    else { weapons[1].reserve += 6; weapons[2].reserve += 30; audio.play("reload.wav"); }
    scene.find("Game").send("toast", kind == "medkit" ? "+40 HEALTH" : "+AMMO");
    hud();
}

function upgrade(kind) {
    if (kind == "damage") damageMul += 0.35;
    else if (kind == "rate") rateMul *= 0.8;
    else if (kind == "health") { maxHp += 25; hp = maxHp; }
    else if (kind == "speed") speed += 0.8;
    else if (kind == "ammo") { weapons[1].reserve += 18; weapons[2].reserve += 90; }
    hud();
}

function die() {
    dead = true;
    self.vx = 0; self.vy = 0;
    self.color = "#7F1D1D";
    audio.play("lose.wav");
    scene.find("Game").send("playerDied");
}

function getHp() { return hp; }

function hud() {
    var w = weapons[current];
    ui.setProgress("HealthBar", hp / maxHp);
    ui.setText("HpText", Math.ceil(hp) + " / " + maxHp);
    ui.setText("WeaponText", w.name);
    ui.setText("AmmoText", w.clip + " / " + (w.reserve < 0 ? "INF" : w.reserve));
}
