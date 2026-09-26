// Enemy AI. Param kind = drone | fighter | heavy
var kind = "drone";
var hp = 2, score = 10, speed = 2.5, t = 0, baseX = 0, fireT = 1, dead = false, color = "#FFFFFF";

function start() {
    var diff = storage.getNumber("sky_difficulty", 1);
    baseX = self.x;
    t = random(0, 6);
    if (kind == "fighter") { hp = 1; score = 15; speed = 6.5; }
    else if (kind == "heavy") { hp = 9; score = 50; speed = 1.4; }
    else { hp = 2; score = 10; speed = 2.6; }
    hp = Math.ceil(hp * (0.75 + diff * 0.25));
    fireT = random(1.0, 2.5) / (0.6 + diff * 0.4);
    color = self.color;
}

function update(dt) {
    t += dt;
    if (kind == "drone") {
        transform.y -= speed * dt;
        transform.x = baseX + Math.sin(t * 2) * 2.5;
    } else if (kind == "fighter") {
        var p = scene.find("Player");
        if (p && p.active && transform.y > p.y + 2) transform.x += clamp(p.x - transform.x, -1, 1) * 3.5 * dt;
        transform.y -= speed * dt;
        transform.rotation = 180 + clamp((p ? p.x - transform.x : 0) * 8, -25, 25);
    } else {
        transform.y -= speed * dt;
        if (transform.y < 5) transform.y += speed * dt * 0.6;
    }
    fireT -= dt;
    if (fireT <= 0 && transform.y < 8 && transform.y > -3) {
        fireT = kind == "heavy" ? 1.4 : random(1.6, 3.2);
        shoot();
    }
    if (transform.y < -10) self.destroy();
}

function shoot() {
    var p = scene.find("Player");
    if (!p || !p.active) return;
    if (kind == "heavy") {
        for (var i = -2; i <= 2; i++) bullet(-Math.PI / 2 + i * 0.3, 6);
    } else {
        bullet(Math.atan2(p.y - self.y, p.x - self.x), kind == "fighter" ? 9 : 7);
    }
}

function bullet(a, v) {
    var b = scene.spawn("EnemyBullet", self.x, self.y - 0.6);
    if (b) { b.vx = Math.cos(a) * v; b.vy = Math.sin(a) * v; }
}

function hit(d) {
    if (dead) return;
    hp -= d;
    self.color = "#FFFFFF";
    after(0.06, function () { if (!dead) self.color = color; });
    if (hp <= 0) die();
}

function die() {
    dead = true;
    var fx = scene.spawn("Explosion", self.x, self.y);
    if (fx) { fx.burst(kind == "heavy" ? 70 : 35); after(1, function () { fx.destroy(); }); }
    audio.play("explosion.wav", 0.5);
    scene.find("Game").send("enemyKilled", score);
    if (chance(kind == "heavy" ? 0.6 : 0.1)) {
        var drop = scene.spawn(pick(["PowerHeal", "PowerTriple", "PowerShield", "PowerBomb"]), self.x, self.y);
    }
    self.destroy();
}
