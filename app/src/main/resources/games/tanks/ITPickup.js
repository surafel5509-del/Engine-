// Iron Tanks — power-up crate: repair, star (firepower), shield or extra life. Blinks before vanishing.
var kind = "repair", life = 12, t = 0;
function update(dt) {
    life -= dt; t += dt;
    self.scaleX = self.scaleY = 0.8 + Math.sin(t * 5) * 0.06;
    if (life < 3) self.visible = Math.floor(t * 8) % 2 == 0;
    if (life <= 0) self.destroy();
}
function onTrigger(other) {
    if (other.tag == "Player") { other.send("pickup", kind); self.destroy(); }
}
