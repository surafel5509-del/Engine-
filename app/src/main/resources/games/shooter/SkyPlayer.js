// Sky Strike — player ship. Joystick moves, auto-fire, B = bomb.
var speed = 11, fireRate = 0.15, hp = 100, maxHp = 100, bombs = 2;
var cooldown = 0, triple = 0, shield = 0, invuln = 0, dead = false;

function start() {
    input.setControls("classic");
    input.showControls(true);
    hud();
}

function update(dt) {
    if (dead) return;
    self.vx = input.axisX * speed;
    self.vy = input.axisY * speed;
    transform.x = clamp(transform.x, -12, 12);
    transform.y = clamp(transform.y, -7, 6.5);
    transform.rotation = -input.axisX * 14;
    cooldown -= dt; invuln -= dt;
    if (triple > 0) triple -= dt;
    if (shield > 0) { shield -= dt; if (shield <= 0) setShield(false); }
    if (cooldown <= 0) { cooldown = fireRate; fire(); }
    if ((input.bDown || input.buttonDown("Special")) && bombs > 0) bomb();
    self.visible = invuln <= 0 || Math.floor(time.time * 20) % 2 == 0;
}

function fire() {
    shot(0);
    if (triple > 0) { shot(-4); shot(4); }
    audio.play("laser.wav", 0.25);
}

function shot(vx) {
    var b = scene.spawn("Bullet", self.x + vx * 0.05, self.y + 0.8);
    if (b) { b.vx = vx; b.rotation = -vx * 4; }
}

function bomb() {
    bombs--;
    var list = scene.findAll("Enemy");
    for (var i = 0; i < list.length; i++) list[i].send("hit", 6);
    var bosses = scene.findAll("Boss");
    for (var j = 0; j < bosses.length; j++) bosses[j].send("hit", 10);
    var shots = scene.findAll("EnemyBullet");
    for (var k = 0; k < shots.length; k++) shots[k].destroy();
    var fx = scene.spawn("BigExplosion", self.x, self.y + 3);
    if (fx) { fx.burst(160); after(1.5, function () { fx.destroy(); }); }
    scene.shake(0.9);
    platform.vibrate(80);
    audio.play("explosion.wav");
    hud();
}

function damage(n) {
    if (dead || invuln > 0) return;
    if (shield > 0) { audio.play("hit.wav", 0.4); return; }
    hp -= n;
    invuln = 0.9;
    scene.shake(0.35);
    platform.vibrate(40);
    audio.play("hit.wav");
    if (hp <= 0) { hp = 0; die(); }
    hud();
}

function powerUp(kind) {
    audio.play("powerup.wav");
    if (kind == "heal") hp = Math.min(maxHp, hp + 35);
    else if (kind == "triple") triple = 10;
    else if (kind == "shield") { shield = 8; setShield(true); }
    else if (kind == "bomb") bombs = Math.min(5, bombs + 1);
    scene.find("Game").send("toast", kind == "heal" ? "+ HEALTH" : kind == "triple" ? "TRIPLE SHOT!" : kind == "shield" ? "SHIELD!" : "+1 BOMB");
    hud();
}

function setShield(on) {
    var s = self.child("ShieldFx");
    if (s) s.active = on;
}

function die() {
    dead = true;
    self.vx = 0; self.vy = 0;
    var fx = scene.spawn("BigExplosion", self.x, self.y);
    if (fx) fx.burst(120);
    audio.play("explosion.wav");
    self.active = false;
    scene.find("Game").send("playerDied");
}

function onTrigger(other) {
    if (other.tag == "Enemy") { damage(25); other.send("hit", 4); }
    else if (other.tag == "Boss") damage(35);
}

function getHealthPercent() { return hp / maxHp; }

function hud() {
    ui.setProgress("HealthBar", hp / maxHp);
    ui.setText("BombText", "BOMBS x" + bombs);
}
