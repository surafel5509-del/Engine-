// CaveGem.js — bobbing collectible with a sparkle burst.
var baseY;

function start() { baseY = self.y; }

function update(dt) {
    self.y = baseY + Math.sin(time.time * 3 + self.x) * 0.12;
}

function onTrigger(other) {
    if (other.tag != "Player") return;
    audio.play("coin.wav");
    var fx = scene.spawn("Dust", self.x, self.y);
    if (fx != null) { fx.burst(14); after(0.8, function () { fx.destroy(); }); }
    other.send("addGem");
    var lm = scene.find("LevelManager");
    if (lm != null) lm.send("gemCollected");
    self.destroy();
}
