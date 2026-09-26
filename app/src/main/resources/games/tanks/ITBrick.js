// Iron Tanks — destructible brick wall block.
var hp = 2;
function hit(d) {
    hp -= d;
    self.color = hp <= 1 ? "#9E6E5E" : "#FFFFFF";
    var fx = scene.spawn("Debris", self.x, self.y);
    if (fx) { fx.burst(hp <= 0 ? 14 : 5); after(0.7, function () { fx.destroy(); }); }
    if (hp <= 0) { audio.play("break.wav", 0.5); self.destroy(); }
}
