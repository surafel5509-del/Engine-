// DqHero.js — the adventurer: move, sword arc, dash, XP levels, potions. Params: speed, hp, damage
var hp = 100, maxHp = 100, damage = 25, xp = 0, level = 1, gold = 0;
var swingCd = 0, swingT = 0, dashCd = 0, dashT = 0, invuln = 0, dead = false;

function start() {
    updateHud();
}

function update(dt) {
    if (dead || scene.getScale() == 0) return;
    var mx = input.axisX, my = input.axisY;

    // dash overrides movement
    dashT -= dt; dashCd -= dt; swingCd -= dt; swingT -= dt; invuln -= dt;
    var dashSpd = 0;
    if (input.bDown && dashCd <= 0 && (mx != 0 || my != 0)) {
        dashCd = 1.4; dashT = 0.22; invuln = Math.max(invuln, 0.3);
        audio.play("jump.wav", 0.6, 1.4);
    }
    if (dashT > 0) dashSpd = 22;

    var len = Math.sqrt(mx * mx + my * my);
    if (len > 1) { mx /= len; my /= len; }
    var sp = dashSpd > 0 ? dashSpd : speed;
    self.x += mx * sp * dt;
    self.z += -my * sp * dt;

    // face movement; camera looks from +Z so down on stick = toward camera
    if (len > 0.05 || dashT > 0) {
        self.rotation = 0;
        self.rotate(0, Math.atan2(mx, my) * 180 / Math.PI, 0);
        self.playModelAnim("Walk");
    } else if (swingT <= 0) self.playModelAnim("Idle");

    // sword swing
    if (input.aDown && swingCd <= 0) {
        swingCd = 0.45; swingT = 0.25;
        audio.play("click.wav", 0.6, 0.7);
        // damage enemies in front arc
        var es = scene.findAll("Enemy");
        var fx = Math.sin(self.rotY * Math.PI / 180), fz = Math.cos(self.rotY * Math.PI / 180);
        for (var i = 0; i < es.length; i++) {
            var e = es[i];
            var dx = e.x - self.x, dz = e.z - self.z;
            var d = Math.sqrt(dx * dx + dz * dz);
            if (d > 2.2) continue;
            var dot = (dx * fx + dz * fz) / (d || 1);
            if (dot > 0.35) {
                e.send("hurt", damage * (1 + 0.15 * (level - 1)));
                var f = scene.find("HitFx");
                if (f != null) { f.x = e.x; f.y = 1; f.z = e.z; f.burst(10); }
            }
        }
    }

    // walls clamp (soft bounds of the crypt)
    self.y = 0;
}

function gainXp(n) {
    xp += n;
    var need = level * 60;
    if (xp >= need) {
        xp -= need;
        level++;
        maxHp += 20;
        hp = maxHp;
        audio.play("powerup.wav");
        scene.find("Sparkle").burst(30);
    }
    updateHud();
}

function hurt(n) {
    if (invuln > 0 || dead) return;
    invuln = 0.5;
    hp -= n;
    scene.shake(0.2);
    audio.play("hit.wav", 0.8);
    if (hp <= 0) {
        dead = true;
        var d = scene.find("DqDirector");
        if (d != null) d.send("heroDown");
    }
    updateHud();
}

function heal(n) {
    hp = Math.min(maxHp, hp + n);
    audio.play("powerup.wav", 0.7);
    updateHud();
}

function addGold(n) { gold += n; audio.play("coin.wav", 0.6); updateHud(); }

function getLvl() { return level; }
function getGold() { return gold; }

function heroDown() {
    scene.setScale(0);
    var d = scene.find("DqDirector");
    if (d != null) d.send("gameOverNow");
}

function updateHud() {
    var bar = scene.find("HpBar");
    if (bar != null) bar.value = Math.max(0, hp) / maxHp;
    scene.find("HpText").setText("HP " + Math.max(0, Math.round(hp)));
    var xb = scene.find("XpBar");
    if (xb != null) xb.value = xp / (level * 60);
    scene.find("LvlText").setText("Lv." + level);
    scene.find("GoldText").setText(gold + "g");
}
