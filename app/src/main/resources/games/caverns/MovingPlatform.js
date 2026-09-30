// MovingPlatform.js — glides back and forth. Params: range, speed
var startX;

function start() { startX = self.x; }

function update(dt) {
    self.vx = Math.cos(time.time * speed) * range * speed;
}
