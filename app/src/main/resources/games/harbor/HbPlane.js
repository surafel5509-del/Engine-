// HbPlane.js — the seaplane: thrust, banking, stall, cargo, damage. Params: thrust, turn, pitchRate, maxSpeed
var vx = 0, vy = 0, vz = 0, speed = 12, pitch = 0, roll = 0, heading = 0;
var fuel = 100, hull = 100, cargo = false, streak = 0, invuln = 0, splashCd = 0, prop = null;

function start() {
    prop = self.child("Prop");
    updateBars();
}

function update(dt) {
    if (down()) return;
    var mx = input.axisX, my = input.axisY;   // steer / pitch intent
    var gas = input.button("Gas") || input.button("A");
    var brake = input.button("Brake") || input.button("B");
    var nitro = input.button("Nitro");

    // engine + fuel
    var throttle = gas ? 1 : (brake ? 0.45 : 0.7);
    if (fuel <= 0) throttle = 0.15;
    else { fuel -= dt * (gas ? 1.6 : 1.0) * (nitro ? 2.2 : 1); if (fuel < 0) fuel = 0; }
    var targetSpeed = maxSpeed * throttle * (nitro ? 1.35 : 1);
    speed += (targetSpeed - speed) * 0.8 * dt;

    // attitude
    var bank = mx * 0.55;
    roll += (bank - roll) * 6 * dt;
    pitch += ((brake ? -0.35 : gas ? 0.3 : -my * 0.35) - pitch) * 4 * dt;
    heading -= mx * turn * dt * (speed / maxSpeed);

    self.rotation = 0;
    self.rotate(pitch * 180 / Math.PI * -1, heading, roll * 180 / Math.PI);

    // move along heading
    var rx = Math.sin(heading * Math.PI / 180), rz = -Math.cos(heading * Math.PI / 180);
    vx = rx * speed; vz = rz * speed;
    var lift = (speed > maxSpeed * 0.35) ? 1 : speed / (maxSpeed * 0.35);
    vy = pitch * speed * 0.9 * lift - (1 - lift) * 6;   // stall sink
    if (self.y < 1.2 && vy < 0) vy = 0;                  // water skim
    self.x += vx * dt; self.y += vy * dt; self.z += vz * dt;

    // prop spin + world
    if (prop != null) prop.rotate(0, 0, speed * 260 * dt);
    var w = scene.find("HbWorld");
    if (w != null) w.send("tick", dt);

    // water touch → splash + small fuel scoop
    splashCd -= dt;
    if (self.y <= 1.15 && splashCd <= 0) {
        splashCd = 0.5;
        splash(12);
        fuel = Math.min(100, fuel + 6);
    }
    // storm damage checked here via proximity
    var storms = scene.findAll("Storm");
    for (var i = 0; i < storms.length; i++) {
        var s = storms[i];
        var dx = s.x - self.x, dy = (s.y - self.y) * 2, dz = s.z - self.z;
        var d2 = dx * dx + dy * dy + dz * dz;
        if (d2 < 42) {
            hull -= dt * (14 + (1 - Math.sqrt(d2) / 6.5) * 26);
            scene.shake(0.12);
            if (hull <= 0) return crash();
        }
    }
    hull = Math.min(100, hull + dt * 1.2);
    invuln -= dt;
    updateBars();
    if (self.y > 60) self.y = 60;
}

function down() { return scene.getScale() == 0; }

function splash(n) {
    var fx = scene.find("Splash");
    if (fx != null) { fx.x = self.x; fx.y = 0.4; fx.z = self.z; fx.burst(n); }
}

function updateBars() {
    var f = scene.find("FuelBar"), h = scene.find("HullBar");
    if (f != null) f.value = fuel / 100;
    if (h != null) h.value = hull / 100;
    var c = scene.find("CargoText");
    if (c != null) c.setText(cargo ? "Cargo: crate aboard" : "Cargo: none");
}

// pads call these
function loadCargo() {
    if (cargo) return false;
    cargo = true;
    audio.play("powerup.wav");
    return true;
}

function deliverCargo() {
    if (!cargo) return false;
    cargo = false;
    streak++;
    return true;
}

function bonus(amount) {
    var w = scene.find("HbWorld");
    if (w != null) w.send("cash", amount);
}

function ringPickup() {
    fuel = Math.min(100, fuel + 30);
    bonus(15);
    audio.play("coin.wav");
}

function buoyPickup() {
    fuel = 100;
    bonus(5);
    audio.play("powerup.wav");
}

function bump(dmg) {
    if (invuln > 0) return;
    invuln = 0.8;
    hull -= dmg;
    scene.shake(0.35);
    audio.play("hit.wav");
    if (hull <= 0) crash();
}

function crash() {
    if (scene.getScale() == 0) return;
    hull = 0;
    splash(60);
    scene.shake(0.8);
    audio.play("explosion.wav");
    self.active = false;
    var w = scene.find("HbWorld");
    if (w != null) w.send("gameOver");
}
