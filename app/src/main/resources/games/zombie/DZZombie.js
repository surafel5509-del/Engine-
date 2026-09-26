// Zombie AI: chase with flanking, attack in range, stagger on hit, drops loot. Param kind.
var kind = "walker";
var hp = 3, speed = 1.6, dmg = 12, reach = 0.95, attackT = 0, stun = 0, dead = false, groanT = 0, wobble = 0, color = "#FFFFFF", score = 10;

function start() {
    var wave = scene.find("Game").send("getWave") || 1;
    var diff = storage.getNumber("dz_difficulty", 1);
    var mul = 1 + (wave - 1) * 0.04 + (diff - 1) * 0.15;
    if (kind == "runner") { hp = 2; speed = 3.3; dmg = 8; score = 15; }
    else if (kind == "brute") { hp = 14; speed = 1.15; dmg = 28; reach = 1.4; score = 50; }
    else { hp = 3; speed = 1.7; dmg = 12; score = 10; }
    hp = Math.ceil(hp * (1 + (wave - 1) * 0.08));
    speed = speed * Math.min(1.6, mul);
    wobble = random(0, 6);
    groanT = random(2, 8);
    color = self.color;
}

function update(dt) {
    if (dead) return;
    var p = scene.find("Player");
    if (!p) return;
    attackT -= dt;
    if (stun > 0) { stun -= dt; self.vx *= 0.85; self.vy *= 0.85; return; }
    var dx = p.x - self.x, dy = p.y - self.y;
    var d = Math.sqrt(dx * dx + dy * dy) || 1;
    var a = Math.atan2(dy, dx) + Math.sin(time.time * 1.7 + wobble) * (d > 4 ? 0.5 : 0.1);
    self.vx = Math.cos(a) * speed;
    self.vy = Math.sin(a) * speed;
    transform.rotation = Math.atan2(dy, dx) * 180 / Math.PI;
    if (d < reach && attackT <= 0) {
        attackT = 0.9;
        p.send("damage", dmg);
    }
    groanT -= dt;
    if (groanT <= 0) { groanT = random(5, 12); if (d < 10) audio.play("groan.wav", 0.35, random(0.8, 1.2)); }
}

function hit(d) {
    if (dead) return;
    hp -= d;
    stun = kind == "brute" ? 0.04 : 0.12;
    var p = scene.find("Player");
    if (p) { var a = Math.atan2(self.y - p.y, self.x - p.x); self.vx = Math.cos(a) * 4; self.vy = Math.sin(a) * 4; }
    self.color = "#FFFFFF";
    after(0.06, function () { if (!dead) self.color = color; });
    var fx = scene.spawn("Blood", self.x, self.y);
    if (fx) { fx.burst(8); after(0.6, function () { fx.destroy(); }); }
    if (hp <= 0) die();
}

function die() {
    dead = true;
    var s = scene.spawn("Splat", self.x, self.y);
    if (s) s.rotation = random(0, 360);
    if (chance(0.08)) scene.spawn("Medkit", self.x, self.y);
    else if (chance(kind == "brute" ? 0.7 : 0.12)) scene.spawn("AmmoBox", self.x, self.y);
    scene.find("Game").send("zombieKilled", score);
    self.destroy();
}
