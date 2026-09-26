// Parallax background star.
var speedY = 1;
function start() { speedY = random(0.6, 4); self.scaleX = self.scaleY = 0.04 + speedY * 0.02; }
function update(dt) {
    transform.y -= speedY * dt;
    if (transform.y < -10) { transform.y = 10; transform.x = random(-16, 16); }
}
