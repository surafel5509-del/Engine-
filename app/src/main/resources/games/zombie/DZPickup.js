// Medkit / ammo pickup that bobs and expires. Param kind.
var kind = "medkit", life = 20, t = 0, baseScale = 1;
function start() { baseScale = self.scaleX; }
function update(dt) {
    t += dt; life -= dt;
    self.scaleX = self.scaleY = baseScale * (1 + Math.sin(t * 5) * 0.08);
    if (life < 4) self.visible = Math.floor(t * 8) % 2 == 0;
    if (life <= 0) self.destroy();
}
function onTrigger(other) {
    if (other.tag == "Player") { other.send("pickup", kind); self.destroy(); }
}
