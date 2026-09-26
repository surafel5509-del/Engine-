// Fading blood decal.
var life = 12;
function update(dt) {
    life -= dt;
    if (life < 3) self.color = "#" + ("0" + Math.floor(Math.max(0, life / 3) * 204).toString(16)).slice(-2) + "7F0000";
    if (life <= 0) self.destroy();
}
