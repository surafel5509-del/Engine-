// Bullet: hits zombies, stops at walls, expires after a short time.
var damage = 1, life = 1.1;
function setDamage(d) { damage = d; }
function update(dt) {
    life -= dt;
    if (life <= 0) self.destroy();
}
function onTrigger(other) {
    if (other.tag == "Zombie") {
        other.send("hit", damage);
        self.destroy();
    } else if (other.tag == "Wall") {
        var fx = scene.spawn("Dust", self.x, self.y);
        if (fx) { fx.burst(6); after(0.5, function () { fx.destroy(); }); }
        self.destroy();
    }
}
