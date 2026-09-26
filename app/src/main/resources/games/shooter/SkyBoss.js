// Boss with phases: aimed bursts, radial spread and spirals. Param level.
var level = 1;
var hp = 60, maxHp = 60, t = 0, pattern = 0, patternT = 0, fireT = 0, spiral = 0, dead = false, entering = true, color = "#FFFFFF";

function start() {
    var diff = storage.getNumber("sky_difficulty", 1);
    maxHp = Math.round([0, 70, 120, 180][level] * (0.75 + diff * 0.25));
    hp = maxHp;
    color = self.color;
    ui.show("BossBar");
    ui.setProgress("BossBar", 1);
    audio.play("roar.wav");
}

function update(dt) {
    if (dead) return;
    t += dt;
    if (entering) {
        transform.y -= 2.5 * dt;
        if (transform.y <= 5) entering = false;
        return;
    }
    transform.x = Math.sin(t * 0.7) * 8;
    transform.y = 5 + Math.sin(t * 1.3) * 0.6;
    patternT += dt;
    if (patternT > 6) { patternT = 0; pattern = (pattern + 1) % (level + 1); if (level >= 2 && pattern == 0) minions(); }
    fireT -= dt;
    if (fireT > 0) return;
    var p = scene.find("Player");
    if (pattern == 0) {
        fireT = 0.9;
        if (p && p.active) { var a = Math.atan2(p.y - self.y, p.x - self.x); for (var i = -1; i <= 1; i++) bullet(a + i * 0.18, 8); }
    } else if (pattern == 1) {
        fireT = 1.3;
        var n = 10 + level * 2;
        for (var k = 0; k < n; k++) bullet(k / n * Math.PI * 2 + t, 5.5);
    } else {
        fireT = 0.07;
        spiral += 0.35;
        bullet(spiral, 6); bullet(spiral + Math.PI, 6);
    }
}

function minions() {
    for (var i = 0; i < 4; i++) scene.spawn("Drone", -9 + i * 6, 10);
}

function bullet(a, v) {
    var b = scene.spawn("EnemyBullet", self.x, self.y - 1.2);
    if (b) { b.vx = Math.cos(a) * v; b.vy = Math.sin(a) * v; }
}

function hit(d) {
    if (dead || entering) return;
    hp -= d;
    ui.setProgress("BossBar", Math.max(0, hp) / maxHp);
    self.color = "#FFB0B0";
    after(0.05, function () { if (!dead) self.color = color; });
    if (hp <= 0) die();
}

function die() {
    dead = true;
    ui.hide("BossBar");
    var shots = scene.findAll("EnemyBullet");
    for (var k = 0; k < shots.length; k++) shots[k].destroy();
    for (var i = 0; i < 5; i++) boom(i * 0.25, random(-2, 2), random(-1.5, 1.5));
    scene.shake(1.2);
    audio.play("explosion.wav");
    after(1.4, function () { scene.find("Game").send("bossKilled"); self.destroy(); });
}

function boom(delay, ox, oy) {
    after(delay, function () {
        var fx = scene.spawn("BigExplosion", self.x + ox, self.y + oy);
        if (fx) { fx.burst(90); after(1.5, function () { fx.destroy(); }); }
        audio.play("explosion.wav", 0.6);
    });
}
