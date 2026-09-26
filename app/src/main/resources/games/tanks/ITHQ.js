// Iron Tanks — headquarters. Three enemy hits and the mission is lost.
var hp = 3, lost = false;
function hit(d) {
    if (lost) return;
    hp -= 1;
    scene.shake(0.4);
    self.color = hp == 2 ? "#FFD180" : "#FF8A65";
    ui.setText("HQText", "HQ " + Math.max(0, hp) + "/3");
    audio.play("roar.wav", 0.4, 1.6);
    if (hp <= 0) {
        lost = true;
        var b = scene.spawn("BigBoom", self.x, self.y);
        if (b) b.burst(60);
        scene.find("Game").send("baseDestroyed");
    }
}
