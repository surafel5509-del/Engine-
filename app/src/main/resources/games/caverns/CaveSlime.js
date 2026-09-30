// CaveSlime.js — hops back and forth between walls (patrol).
// Params: distance, speed
var homeX;
var dir = 1;

function start() { homeX = self.x; }

function update(dt) {
    if (self.x > homeX + distance) dir = -1;
    if (self.x < homeX - distance) dir = 1;
    self.vx = dir * speed;
    self.flipX = dir < 0;
    // little hops
    if (self.grounded && Math.random() < 0.01) self.vy = 4;
}
