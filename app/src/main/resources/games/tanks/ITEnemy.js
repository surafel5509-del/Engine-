// Iron Tanks — enemy AI: drives along the four axes, re-plans when blocked, hunts the player or the HQ
// and fires when it has a clear lane or the player is close.
var kind = "light", hp = 1, speed = 2.6, dir = 270, turnT = 2, fireT = 1.5, dmg = 25, pts = 100, fireRate = 1.6;
var stuckT = 0, lastX = 0, lastY = 0, dead = false, turret = 270, baseColor = "#FFFFFF";
var DIRS = [0, 90, 180, 270];

function start() {
    if (kind == "fast") { hp = 1; speed = 4.1; pts = 150; fireRate = 1.4; }
    else if (kind == "heavy") { hp = 4; speed = 1.7; dmg = 35; pts = 300; fireRate = 2.0; }
    var diff = storage.getNumber("it_difficulty", 1);
    fireRate *= [1.4, 1, 0.72][diff];
    turnT = random(0.8, 2.5); fireT = random(1, 2.5);
    lastX = self.x; lastY = self.y;
    baseColor = self.color;
}

function update(dt) {
    if (dead) return;
    var p = scene.find("Player");
    var hq = scene.find("HQ");
    turnT -= dt; fireT -= dt; stuckT += dt;
    if (stuckT > 0.35) {
        if (distance(self.x, self.y, lastX, lastY) < 0.12) pickDir(p, hq, true);
        lastX = self.x; lastY = self.y; stuckT = 0;
    }
    if (turnT <= 0) pickDir(p, hq, false);
    var a = dir * Math.PI / 180;
    self.vx = Math.cos(a) * speed;
    self.vy = Math.sin(a) * speed;
    self.rotation = dir;

    var aim = dir, close = false, lane = false;
    if (p && p.x > -500) {
        var d = self.distanceTo(p);
        close = d < 6.5;
        lane = Math.abs(p.x - self.x) < 0.6 || Math.abs(p.y - self.y) < 0.6;
        if (close || lane) aim = angleTo(self.x, self.y, p.x, p.y);
    }
    if (!close && !lane && hq && (Math.abs(hq.x - self.x) < 0.6 || Math.abs(hq.y - self.y) < 0.6)) { aim = angleTo(self.x, self.y, hq.x, hq.y); lane = true; }
    turret += clamp(((aim - turret + 540) % 360) - 180, -200 * dt, 200 * dt);
    var t = self.child("Turret");
    if (t) t.rotation = turret - dir;
    if (fireT <= 0) {
        fireT = fireRate * random(0.7, 1.3);
        if (close || lane || chance(0.35)) fire();
    }
}

function pickDir(p, hq, blocked) {
    turnT = random(1.2, 3.2);
    var goal = (hq && chance(0.35)) ? hq : p;
    var nd = dir;
    if (goal && goal.x > -500 && chance(0.65)) {
        var dx = goal.x - self.x, dy = goal.y - self.y;
        if (Math.abs(dx) > Math.abs(dy)) nd = dx > 0 ? 0 : 180; else nd = dy > 0 ? 90 : 270;
        if (blocked && nd == dir) nd = Math.abs(dx) > Math.abs(dy) ? (dy > 0 ? 90 : 270) : (dx > 0 ? 0 : 180);
    } else nd = pick(DIRS);
    if (blocked && nd == dir) nd = (dir + pick([90, 180, 270])) % 360;
    dir = nd;
}

function fire() {
    var a = turret * Math.PI / 180;
    var s = scene.spawn("EnemyShell", self.x + Math.cos(a) * 0.95, self.y + Math.sin(a) * 0.95);
    if (s) {
        s.vx = Math.cos(a) * 10; s.vy = Math.sin(a) * 10;
        s.rotation = turret;
        s.send("setOwner", "enemy");
        s.send("setDamage", dmg);
    }
    audio.play("pistol.wav", 0.3, 0.55);
}

function hit(d) {
    if (dead) return;
    hp -= d;
    self.color = "#FFFFFF";
    after(0.07, function () { if (!dead) self.color = baseColor; });
    audio.play("hit.wav", 0.5, 0.8);
    if (hp <= 0) die();
}

function die() {
    dead = true;
    var b = scene.spawn("BigBoom", self.x, self.y);
    if (b) { b.burst(40); after(1, function () { b.destroy(); }); }
    var sc = scene.spawn("Scorch", self.x, self.y);
    if (sc) sc.rotation = random(0, 360);
    audio.play("explosion.wav", 0.7);
    scene.shake(0.35);
    if (chance(kind == "heavy" ? 0.6 : 0.2)) scene.spawn(pick(["Repair", "StarUp", "ShieldUp", "Repair", "LifeUp"]), self.x, self.y);
    scene.find("Game").send("enemyKilled", pts);
    self.destroy();
}
