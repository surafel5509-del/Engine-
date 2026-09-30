// TdTower.js — acquires targets, rotates its turret head, fires. Params: range, fireRate, damage, kind
var cd = 0, level = 1;

function start() {
    // tower-type tuning comes from the template name
    if (self.name == "ArrowTower") { range = 3.2; fireRate = 1.6; damage = 8; kind = "arrow"; }
    if (self.name == "CannonTower") { range = 2.8; fireRate = 0.55; damage = 26; kind = "cannon"; }
    if (self.name == "FrostTower") { range = 2.6; fireRate = 1.1; damage = 5; kind = "frost"; }
}

function update(dt) {
    cd -= dt;
    var target = null, bestD = 1e9;
    var enemies = scene.findAll("Enemy");
    for (var i = 0; i < enemies.length; i++) {
        var d = self.distanceTo(enemies[i]);
        if (d <= range && d < bestD) { bestD = d; target = enemies[i]; }
    }
    var head = self.child("Turret");
    if (target != null) {
        var ang = Math.atan2(target.y - self.y, target.x - self.x) * 180 / Math.PI;
        if (head != null) head.rotation = ang;
        if (cd <= 0) {
            cd = 1 / (fireRate * (1 + 0.25 * (level - 1)));
            fire(target);
        }
    }
}

function fire(target) {
    var b = scene.spawn("Bullet", self.x, self.y);
    if (b == null) return;
    var dx = target.x - self.x, dy = target.y - self.y;
    var len = Math.sqrt(dx * dx + dy * dy) || 1;
    b.send("launch", { tx: target, speed: 12, damage: damage * (1 + 0.5 * (level - 1)), kind: kind, dx: dx / len, dy: dy / len });
    if (kind == "cannon") audio.play("explosion.wav", 0.25, 1.4);
    else audio.play("laser.wav", 0.3, kind == "frost" ? 1.5 : 1.1);
}

function onTap() {
    var m = scene.find("TdManager");
    if (m != null) m.send("towerTapped", self);
}

function getLevel() { return level; }
function getDamage() { return Math.round(damage * (1 + 0.5 * (level - 1))); }
function getRange() { return range + 0.3 * (level - 1); }

function upgrade() {
    level++;
    var head = self.child("Turret");
    if (head != null) head.scaleX = head.scaleY = 1 + 0.2 * (level - 1);
    scene.shake(0.05);
}
