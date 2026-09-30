// ZgCar.js — arcade truck physics, roadkill, turret fire. Params: accel, maxSpeed, grip, hp
var speed = 0, heading = 0, hp = 100, armor = 0, ammo = 120, cash = 0, turretLvl = 1, fireCd = 0, invuln = 0, dead = false;
var wheels = [];

function start() {
    hp = 100;
    wheels = scene.findAll("Wheel");
    updateHud();
}

function update(dt) {
    if (dead || scene.getScale() == 0) return;
    var mx = input.axisX;
    var gas = input.button("Gas"), brake = input.button("Brake"), nitro = input.button("Nitro");
    var fire = input.button("Fire");

    var top = maxSpeed * (nitro ? 1.35 : 1);
    if (gas) speed += accel * dt;
    else if (brake) speed -= accel * 1.2 * dt;
    else speed -= speed * 1.2 * dt;
    speed = Math.max(-top * 0.5, Math.min(top, speed));

    heading -= mx * 80 * dt * Math.min(1, Math.abs(speed) / 8) * (speed < 0 ? -1 : 1);
    self.rotation = 0;
    self.rotate(0, heading, 0);

    var rx = Math.sin(heading * Math.PI / 180), rz = -Math.cos(heading * Math.PI / 180);
    self.x += rx * speed * dt;
    self.z += rz * speed * dt;
    self.y = 0.8;

    // walls
    if (self.x < -35) { self.x = -35; speed *= 0.5; }
    if (self.x > 35) { self.x = 35; speed *= 0.5; }
    if (self.z < -35) { self.z = -35; speed *= 0.5; }
    if (self.z > 35) { self.z = 35; speed *= 0.5; }

    // wheels
    for (var i = 0; i < wheels.length; i++) wheels[i].send("drive", speed / maxSpeed * 2, -mx * 25);

    // turret
    fireCd -= dt;
    if (fire && ammo > 0 && fireCd <= 0) shoot();

    // roadkill: fast contact handled by zombie trigger (see ZgZombie.onTrigger → roadkill)
    invuln -= dt;
    updateHud();
}

function shoot() {
    var rate = 0.16 - 0.02 * turretLvl;
    fireCd = rate;
    ammo--;
    var t = nearestZombie(26);
    var tur = self.child("Turret");
    if (tur != null) {
        if (t != null) {
            var dx = t.x - self.x, dz = t.z - self.z;
            tur.rotY = Math.atan2(dx, -dz) * 180 / Math.PI - heading;
        }
        var mz = tur.child("Muzzle");
        if (mz != null) { mz.active = true; after(0.05, function () { mz.active = false; }); }
    }
    var b = scene.spawn("Bullet", self.x, 1.6, self.z);
    if (b == null) return;
    var tx = t != null ? t.x : self.x + Math.sin(heading * Math.PI / 180) * 20;
    var tz = t != null ? t.z : self.z - Math.cos(heading * Math.PI / 180) * 20;
    var dx = tx - self.x, dz = tz - self.z;
    var len = Math.sqrt(dx * dx + dz * dz) || 1;
    b.send("launch", { dx: dx / len, dz: dz / len, speed: 34, dmg: 12 + 6 * turretLvl });
    audio.play("pistol.wav", 0.5, 1.2);
}

function nearestZombie(range) {
    var best = null, bestD = range;
    var zs = scene.findAll("Zombie");
    for (var i = 0; i < zs.length; i++) {
        var d = self.distanceTo(zs[i]);
        if (d < bestD) { bestD = d; best = zs[i]; }
    }
    return best;
}

// zombie calls when it touches the truck
function hitZombieContact(z) {
    var closing = Math.abs(speed);
    if (closing > 9) {
        // roadkill!
        z.send("splat");
        cash += 10;
        audio.play("hit.wav", 0.8, 0.8);
        scene.shake(0.1);
        var goo = scene.find("Goo");
        if (goo != null) { goo.x = z.x; goo.y = 0.8; goo.z = z.z; goo.burst(20); }
    } else {
        damage(6);
    }
}

function damage(n) {
    if (invuln > 0 || dead) return;
    var mitigated = Math.max(1, n - armor);
    hp -= mitigated;
    invuln = 0.35;
    scene.shake(0.25);
    audio.play("hit.wav", 0.7);
    if (hp <= 0) {
        dead = true;
        var blast = scene.find("Blast");
        if (blast != null) { blast.x = self.x; blast.y = 1; blast.z = self.z; blast.burst(60); }
        audio.play("explosion.wav");
        var d = scene.find("ZgDirector");
        if (d != null) d.send("truckDown");
    }
    updateHud();
}

// pickups
function giveAmmo() { ammo += 60; audio.play("click.wav", 0.9); updateHud(); }
function giveCash(n) { cash += n; audio.play("coin.wav", 0.8); updateHud(); }
function repair(n) { hp = Math.min(hpMax(), hp + n); updateHud(); }

// garage shop
function buyRepair(name) { if (cash >= 40) { cash -= 40; repair(50); audio.play("powerup.wav"); } else deny(); }
function buyArmor(name) { if (cash >= 60) { cash -= 60; armor += 2; audio.play("powerup.wav"); } else deny(); }
function buyAmmo(name) { if (cash >= 30) { cash -= 30; ammo += 90; audio.play("click.wav"); } else deny(); }
function buyTurret(name) { if (cash >= 90 && turretLvl < 4) { cash -= 90; turretLvl++; audio.play("powerup.wav"); } else deny(); }

function deny() { audio.play("hit.wav", 0.4); scene.shake(0.05); }

function hpMax() { return 100; }
function updateHud() {
    var bar = scene.find("HpBar");
    if (bar != null) bar.value = Math.max(0, hp) / 100;
    scene.find("HpText").setText("TRUCK " + Math.max(0, Math.round(hp)) + "%");
    scene.find("AmmoText").setText("AMMO " + ammo);
    scene.find("CashText").setText(cash + "g");
}
