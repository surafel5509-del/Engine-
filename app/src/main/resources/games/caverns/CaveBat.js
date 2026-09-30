// CaveBat.js — swoops in a sine wave around its home point.
// Params: range, speed
var homeX, homeY, t = 0;

function start() { homeX = self.x; homeY = self.y; }

function update(dt) {
    t += dt * speed;
    self.x = homeX + Math.sin(t) * range;
    self.y = homeY + Math.cos(t * 2.3) * range * 0.4;
    self.flipX = Math.cos(t) < 0;
}
