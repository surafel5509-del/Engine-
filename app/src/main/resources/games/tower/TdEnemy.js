// TdEnemy.js — walks the road waypoint list, chipped by bullets. Params: speed
var hp = 20, maxHp = 20, wp = 1, slow = 0, reward = 8, dead = false, wpPos = null;

function start() {
    if (self.name == "Runner") speed = 2.6; else if (self.name == "Boss") speed = 1.0; else speed = 1.6;
    if (self.name == "Boss") { reward = 60; } else if (self.name == "Runner") { reward = 6; }
    scaleBar();
}

function init(startHp) {
    hp = startHp; maxHp = startHp;
    if (self.name == "Boss") { self.scaleX = 1.25; self.scaleY = 1.25; }
    scaleBar();
}

function update(dt) {
    if (dead) return;
    var m = scene.find("TdManager");
    if (m == null) return;
    if (wpPos == null) { wpPos = m.send("roadAt", Math.min(1, m.send("roadCount") - 1)); self.x = m.send("roadAt", 0)[0]; self.y = m.send("roadAt", 0)[1]; }
    var spd = speed * (slow > 0 ? 0.5 : 1);
    slow -= dt;
    var dx = wpPos[0] - self.x, dy = wpPos[1] - self.y;
    var d = Math.sqrt(dx * dx + dy * dy);
    if (d < 0.15) {
        wp++;
        if (wp >= m.send("roadCount")) {
            dead = true;
            m.send("enemyReached");
            self.destroy();
            return;
        }
        wpPos = m.send("roadAt", wp);
    } else {
        self.x += dx / d * spd * dt;
        self.y += dy / d * spd * dt;
        self.flipX = dx < 0;
    }
    scaleBar();
}

function scaleBar() {
    var bar = self.child("HpBar");
    if (bar != null) bar.scaleX = Math.max(0.05, hp / maxHp);
}

function hit(dmg, kind) {
    if (dead) return;
    hp -= dmg;
    if (kind == "frost") slow = 1.4;
    scaleBar();
    if (hp <= 0) {
        dead = true;
        var fx = scene.find("Boom");
        if (fx != null) { fx.x = self.x; fx.y = self.y; fx.burst(16); }
        var m = scene.find("TdManager");
        if (m != null) m.send("enemyDied", reward);
        audio.play("explosion.wav", 0.35, self.name == "Boss" ? 0.7 : 1.2);
        self.destroy();
    }
}

function onTap() { } // enemies are not clickable; towers are
