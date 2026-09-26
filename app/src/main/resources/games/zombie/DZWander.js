// Menu decoration: a zombie shuffling around.
var t = 0, bx = 0, by = 0;
function start() { bx = self.x; by = self.y; t = random(0, 10); }
function update(dt) {
    t += dt;
    transform.x = bx + Math.sin(t * 0.4) * 2;
    transform.y = by + Math.cos(t * 0.3) * 1;
    transform.rotation = Math.atan2(-Math.sin(t * 0.3) * 0.3, Math.cos(t * 0.4) * 0.8) * 180 / Math.PI;
}
