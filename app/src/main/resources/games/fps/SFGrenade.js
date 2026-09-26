// Strike Force — frag grenade: bounces, then explodes after the fuse.
var fuse = 2.2, done = false;
function update(dt) {
    fuse -= dt;
    if (fuse <= 0 && !done) boom();
}
function boom() {
    done = true;
    var x = self.x, y = self.y, z = self.z;
    var fx = scene.spawn("Explosion", x, y + 0.3, z);
    if (fx) { fx.burst(60); after(1.2, function () { fx.destroy(); }); }
    audio.play("explosion.wav", 1, 1.1);
    scene.shake(0.6);
    var foes = scene.findInRadius3("Enemy", x, y, z, 5.5);
    for (var i = 0; i < foes.length; i++) foes[i].send("hit", 160 * (1 - Math.min(1, distance(foes[i].x, foes[i].z, x, z) / 6.5)));
    var barrels = scene.findInRadius3("Barrel", x, y, z, 4);
    for (var j = 0; j < barrels.length; j++) barrels[j].send("hit", 100);
    var p = scene.find("Player");
    if (p) { var d = distance(p.x, p.z, x, z); if (d < 5) p.send("damage", Math.round(70 * (1 - d / 5))); }
    self.destroy();
}
