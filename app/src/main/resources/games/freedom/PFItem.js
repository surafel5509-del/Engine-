// PRICE OF FREEDOM — collectible contraband / evidence pickup.
var baseY = 0;
function start() { baseY = self.y; }
function update(dt) {
    self.y = baseY + Math.sin(time.time * 2.2 + self.x * 0.8) * 0.08;
    self.rotation += 35 * dt;
    self.setShaderParam(0.7 + 0.3 * Math.sin(time.time * 3));
}
function getKind() { return kind; }
function take() {
    scene.find("Game").send("collect", kind);
    audio.play("coin.wav", 0.22, 0.78);
    self.destroy();
}
