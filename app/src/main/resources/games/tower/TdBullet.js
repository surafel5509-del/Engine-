// TdBullet.js — homing projectile. Receives {tx, speed, damage, kind, dx, dy} via launch.
var target = null, spd = 12, dmg = 8, kind = "arrow", life = 2;

function launch(cfg) {
    target = cfg.tx; spd = cfg.speed; dmg = cfg.damage; kind = cfg.kind;
    var dx = cfg.dx, dy = cfg.dy;
    self.vx = dx * spd; self.vy = dy * spd;
    self.rotation = Math.atan2(dy, dx) * 180 / Math.PI;
}

function update(dt) {
    life -= dt;
    if (life <= 0) return self.destroy();
    if (target != null && target.active) {
        var dx = target.x - self.x, dy = target.y - self.y;
        var d = Math.sqrt(dx * dx + dy * dy) || 1;
        self.vx = dx / d * spd; self.vy = dy / d * spd;
        if (d < 0.3) {
            target.send("hit", dmg, kind);
            impact();
        }
    } else if (target != null) impact(); // target died mid-flight
}

function impact() {
    var fx = scene.find("Boom");
    if (fx != null && kind == "cannon") {
        // splash: damage every enemy near the impact
        fx.x = self.x; fx.y = self.y; fx.burst(24);
        var near = scene.findInRadius("Enemy", self.x, self.y, 1.4);
        for (var i = 0; i < near.length; i++) near[i].send("hit", dmg * 0.5, kind);
        scene.shake(0.12);
        audio.play("explosion.wav", 0.5);
    }
    self.destroy();
}
