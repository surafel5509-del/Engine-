// Strike Force — explosive barrel: a few hits (or a blast) make it explode; it keeps burning after.
var hp = 40, exploded = false;
function hit(d) {
    if (exploded) return;
    hp -= d;
    if (hp <= 0) explode();
}
function explode() {
    exploded = true;
    var x = self.x, y = self.y, z = self.z;
    var fx = scene.spawn("Explosion", x, y + 0.5, z);
    if (fx) { fx.burst(70); after(1.2, function () { fx.destroy(); }); }
    var fire = scene.spawn("Fire", x, y, z);
    if (fire) after(9, function () { fire.destroy(); });
    audio.play("explosion.wav", 1);
    scene.shake(0.7);
    var foes = scene.findInRadius3("Enemy", x, y, z, 5);
    for (var i = 0; i < foes.length; i++) foes[i].send("hit", 150);
    var others = scene.findInRadius3("Barrel", x, y, z, 4.5);
    for (var j = 0; j < others.length; j++) { var o = others[j]; after(0.15, function () { o.send("hit", 100); }); }
    var p = scene.find("Player");
    if (p) { var d = distance(p.x, p.z, x, z); if (d < 5) p.send("damage", Math.round(60 * (1 - d / 5))); }
    self.destroy();
}
