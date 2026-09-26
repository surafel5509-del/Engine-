// Player bullet: damages enemies and bosses.
function update(dt) {
    if (transform.y > 10 || Math.abs(transform.x) > 16) self.destroy();
}
function onTrigger(other) {
    if (other.tag == "Enemy" || other.tag == "Boss") {
        other.send("hit", 1);
        var fx = scene.spawn("Spark", self.x, self.y);
        if (fx) { fx.burst(8); after(0.5, function () { fx.destroy(); }); }
        self.destroy();
    }
}
