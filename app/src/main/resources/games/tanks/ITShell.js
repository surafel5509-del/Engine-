// Iron Tanks — tank shell. Bricks crumble, steel stops it, HE shells blast everything around them.
var damage = 1, owner = "player", life = 2.2, he = false, done = false;
function setOwner(o) { owner = o; }
function setDamage(d) { damage = d; }
function setHE(v) { he = v; }
function start() { he = self.name.indexOf("HE") == 0; }
function update(dt) {
    life -= dt;
    if (life <= 0) boom(false);
}
function onTrigger(other) {
    if (done) return;
    var t = other.tag;
    if (t == "Brick") { other.send("hit", damage); boom(true); }
    else if (t == "Steel" || t == "Wall") boom(true);
    else if (t == "Enemy" && owner == "player") { other.send("hit", damage); boom(true); }
    else if (t == "Player" && owner == "enemy") { other.send("damage", damage); boom(true); }
    else if (t == "HQ" && owner == "enemy") { other.send("hit", 1); boom(true); }
    else if (t == "Shell" && other.name.indexOf("Enemy") != self.name.indexOf("Enemy")) { other.destroy(); boom(true); }
}
function boom(impact) {
    if (done) return;
    done = true;
    if (he) {
        var bricks = scene.findInRadius("Brick", self.x, self.y, 1.7);
        for (var i = 0; i < bricks.length; i++) bricks[i].send("hit", 9);
        var foes = scene.findInRadius("Enemy", self.x, self.y, 1.9);
        for (var j = 0; j < foes.length; j++) foes[j].send("hit", 2);
        scene.shake(0.3);
    }
    var fx = scene.spawn(he ? "BigBoom" : "Boom", self.x, self.y);
    if (fx) { fx.burst(he ? 45 : 10); after(0.8, function () { fx.destroy(); }); }
    if (impact) audio.play(he ? "explosion.wav" : "hit.wav", he ? 0.8 : 0.3, he ? 1 : 1.4);
    self.destroy();
}
