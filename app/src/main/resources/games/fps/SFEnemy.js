// Strike Force — hostile soldier: patrols, spots the player (view distance + line of sight),
// turns and fires bursts with distance-based accuracy, reacts to gunfire, dies with a fall.
// Params: px, pz = patrol offset; guard = true to hold position.
var px = 0, pz = 0, guard = false, hp = 100, state = "patrol", dead = false;
var homeX = 0, homeZ = 0, leg = 0, burst = 0, burstT = 0, fireT = 1.5, seeT = 0, lostT = 0, walkSpeed = 1.5;
var shotsFired = 0, hitsLanded = 0, sawPlayer = 0;
var view = 24, accuracy = 1, dmg = 7, deathT = 0, strafe = 1, strafeT = 0;

function start() {
    homeX = self.x; homeZ = self.z;
    var diff = storage.getNumber("sf_difficulty", 1);
    accuracy = [0.6, 1, 1.35][diff];
    dmg = [5, 7, 10][diff];
    if (scene.name == "NightRaid") view = 17;
    playAnim(guard ? "Idle" : "Walk");
}

function playAnim(n) {
    var b = self.child("Body");
    if (b && b.modelAnim != n) b.playModelAnim(n);
}

function update(dt) {
    if (dead) {
        deathT += dt;
        self.rotX = Math.max(-90, -deathT * 300);
        self.setVelocity(0, self.vy, 0);
        return;
    }
    var p = scene.find("Player");
    if (!p) return;
    var dx = p.x - self.x, dz = p.z - self.z;
    var dist = Math.sqrt(dx * dx + dz * dz);
    seeT -= dt;
    if (seeT <= 0) {
        seeT = 0.25;
        var sees = dist < view && canSee(p, dist);
        if (sees) { state = "combat"; lostT = 4; sawPlayer++; }
        else if (state == "combat") { lostT -= 0.25; if (lostT <= 0) state = "hunt"; }
    }
    if (state == "patrol") patrol(dt);
    else if (state == "hunt") {
        face(dx, dz);
        if (dist > 3) move(dx / dist, dz / dist, 2.2); else stop();
        playAnim("Walk");
    } else combat(dt, p, dx, dz, dist);
}

function canSee(p, dist) {
    var ox = self.x, oy = self.y + 0.6, oz = self.z;
    var h = scene.raycastHit(ox, oy, oz, p.x - ox, p.y + 0.5 - oy, p.z - oz, dist + 1, self);
    return h && h.object && h.object.tag == "Player";
}

function patrol(dt) {
    if (guard || (px == 0 && pz == 0)) { stop(); playAnim("Idle"); return; }
    var tx = leg == 0 ? homeX + px : homeX, tz = leg == 0 ? homeZ + pz : homeZ;
    var dx = tx - self.x, dz = tz - self.z, d = Math.sqrt(dx * dx + dz * dz);
    if (d < 0.5) { leg = 1 - leg; return; }
    face(dx, dz);
    move(dx / d, dz / d, walkSpeed);
    playAnim("Walk");
}

function combat(dt, p, dx, dz, dist) {
    face(dx, dz);
    // strafe sideways while shooting
    strafeT -= dt;
    if (strafeT <= 0) { strafeT = random(0.8, 2); strafe = pick([-1, 0, 1]); }
    if (strafe != 0 && dist > 4) { move(-dz / dist * strafe, dx / dist * strafe, 1.6); playAnim("Walk"); }
    else if (dist > 16) { move(dx / dist, dz / dist, 2); playAnim("Walk"); }
    else { stop(); playAnim("Idle"); }
    fireT -= dt;
    if (burst > 0) {
        burstT -= dt;
        if (burstT <= 0) { burstT = 0.12; burst--; shoot(p, dist); }
    } else if (fireT <= 0) { fireT = random(1.1, 2.0); burst = 3; burstT = 0; }
}

function shoot(p, dist) {
    var fl = self.child("MuzzleFlash");
    if (fl) { fl.setComponentEnabled("MeshRenderer", true); after(0.05, function () { fl.setComponentEnabled("MeshRenderer", false); }); }
    audio.play("pistol.wav", clamp(0.5 - dist * 0.012, 0.08, 0.5), 0.8);
    shotsFired++;
    var moving = Math.abs(p.vx) + Math.abs(p.vz) > 1;
    var chance0 = clamp(0.62 - dist * 0.018, 0.1, 0.55) * accuracy * (moving ? 0.65 : 1);
    if (Math.random() < chance0 && canSee(p, dist)) { hitsLanded++; p.send("damage", dmg); }
}

function face(dx, dz) { self.rotY = Math.atan2(dx, dz) * 180 / Math.PI; }
function move(nx, nz, s) { self.vx = nx * s; self.vz = nz * s; }
function stop() { self.vx = 0; self.vz = 0; }

function debugState() { return state + " hp=" + Math.round(hp) + " saw=" + sawPlayer + " shots=" + shotsFired + " hits=" + hitsLanded + " pos=" + Math.round(self.x) + "," + Math.round(self.z); }

function alert() { if (!dead && state == "patrol") state = "hunt"; }

function hit(d) {
    if (dead) return;
    hp -= d;
    state = "combat"; lostT = 4;
    if (hp <= 0) die();
}

function die() {
    dead = true;
    stop();
    playAnim("Idle");
    self.tag = "Dead";
    self.setComponentEnabled("Collider3D", false);
    self.setComponentEnabled("Rigidbody3D", false);
    audio.play("hit.wav", 0.5, 0.6);
    if (chance(0.45)) scene.spawn("AmmoCrate", self.x, 0.35, self.z);
    scene.find("Game").send("enemyKilled", 100);
    after(6, function () { self.destroy(); });
}
