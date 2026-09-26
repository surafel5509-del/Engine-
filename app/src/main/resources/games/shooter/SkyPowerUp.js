// Floating power-up. Param kind = heal | triple | shield | bomb
var kind = "heal";
function update(dt) {
    transform.y -= 2 * dt;
    transform.rotation += 90 * dt;
    if (transform.y < -10) self.destroy();
}
function onTrigger(other) {
    if (other.tag == "Player") { other.send("powerUp", kind); self.destroy(); }
}
