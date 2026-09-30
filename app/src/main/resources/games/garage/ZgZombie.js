// ZgZombie.js — shambles toward the truck, lurches, splats. Params: speed, hp, damage
var hp = 30, alive = true, lurch = 0, wander = 0, wanderT = 0;

function start() {
    hp = hp + waveBonus();
    self.scaleX = self.scaleY = self.scaleZ = 0.9 + Math.random() * 0.35;
    wander = (Math.random() - 0.5) * 0.6;
}

function waveBonus() {
    var d = scene.find("ZgDirector");
    return d != null ? (d.send("getWave") - 1) * 6 : 0;
}

function update(dt) {
    if (!alive) return;
    var car = scene.find("Car");
    if (car == null || !car.active) { idle(dt); return; }
    var dx = car.x - self.x, dz = car.z - self.z;
    var d = Math.sqrt(dx * dx + dz * dz) || 1;
    if (d < 2.3) {
        car.send("hitZombieContact", self);
        return;
    }
    // gentle weave so hordes don't stack
    wanderT -= dt;
    if (wanderT <= 0) { wanderT = 1 + Math.random(); wander = (Math.random() - 0.5) * 1.2; }
    var ang = Math.atan2(dx, -dz) + wander * 0.4;
    var sp = speed * (1 + 0.05 * Math.sin(time.time * 6 + self.id));
    self.x += Math.sin(ang) * sp * dt;
    self.z += -Math.cos(ang) * sp * dt;
    self.rotation = 0;
    self.rotate(0, ang * 180 / Math.PI, 0);
    // shamble arms
    lurch += dt * 6;
    var armL = self.child("ArmL"), armR = self.child("ArmR");
    if (armL != null) armL.rotation = -70 + Math.sin(lurch) * 18;
    if (armR != null) armR.rotation = -70 - Math.sin(lurch) * 18;
}

function idle(dt) {
    lurch += dt * 2;
}

function splat() {
    if (!alive) return;
    alive = false;
    var d = scene.find("ZgDirector");
    if (d != null) d.send("zombieDown", self);
    self.destroy();
}

function shot(dmg) {
    if (!alive) return;
    hp -= dmg;
    if (hp <= 0) splat();
}
