// PRICE OF FREEDOM — ambient prisoner behaviour and conversation bridge.
var baseY = 0;
var phase = 0;
function start() { baseY = self.y; phase = random(0, 6.28); }
function update(dt) {
    self.y = baseY + Math.sin(time.time * 1.15 + phase) * 0.045;
    self.rotation = Math.sin(time.time * 0.55 + phase) * 4;
}
function talk() { scene.find("Game").send("talkPrisoner", id); }
