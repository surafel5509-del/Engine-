// Enemy projectile.
function update(dt) {
    if (Math.abs(transform.x) > 16 || Math.abs(transform.y) > 11) self.destroy();
}
function onTrigger(other) {
    if (other.tag == "Player") { other.send("damage", 10); self.destroy(); }
}
