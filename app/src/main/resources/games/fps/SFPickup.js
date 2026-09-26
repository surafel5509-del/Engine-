// Strike Force — ammo crate dropped by enemies (touch to collect).
var t = 0;
function update(dt) {
    t += dt;
    self.rotY += dt * 90;
    var p = scene.find("Player");
    if (p && distance(p.x, p.z, self.x, self.z) < 1.3 && Math.abs(p.y - self.y) < 2) { p.send("addAmmo", 1); self.destroy(); }
    if (t > 30) self.destroy();
}
