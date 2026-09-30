// DqEnemy.js — skeletons chase and swing; the Lich also fires orbs. Params: speed, hp, damage, boss
var hp = 40, boss = false, atkCd = 1, alive = true, orbCd = 2, wobble = 0;

function start() {
    hp = hp + levelBoost();
    if (boss) {
        self.active = false;  // sealed until the gate opens
        var d = scene.find("DqDirector");
        if (d != null) d.send("bossReady", self);
    }
}

function levelBoost() {
    var d = scene.find("DqDirector");
    return d != null ? Math.floor(d.send("getWaveish") * 0.5) : 0;
}

function update(dt) {
    if (!alive || !self.active) return;
    var hero = scene.find("Hero");
    if (hero == null || !hero.active) return;
    var dx = hero.x - self.x, dz = hero.z - self.z;
    var d = Math.sqrt(dx * dx + dz * dz) || 1;
    var stopAt = boss ? 3.4 : 1.5;
    if (d > stopAt) {
        self.x += dx / d * speed * dt;
        self.z += dz / d * speed * dt;
    }
    self.rotation = 0;
    self.rotate(0, Math.atan2(dx, dz) * 180 / Math.PI, 0);
    self.playModelAnim("Walk");

    atkCd -= dt;
    if (d <= stopAt + 0.6 && atkCd <= 0) {
        atkCd = boss ? 1.1 : 1.3;
        hero.send("hurt", damage);
        scene.shake(boss ? 0.25 : 0.1);
        audio.play("hit.wav", 0.5, boss ? 0.7 : 1.1);
    }

    if (boss) {
        // purple orbs volley
        orbCd -= dt;
        if (orbCd <= 0) {
            orbCd = 2.6;
            var eye = scene.find("BossEye");
            if (eye != null) {
                eye.active = true;
                eye.x = self.x; eye.y = self.y + 1.8; eye.z = self.z;
                eye.send("fire", { tx: hero.x, tz: hero.z, dmg: damage });
                after(1.6, function () { eye.active = false; });
            }
        }
    }
}

function hurt(n) {
    if (!alive) return;
    hp -= n;
    // flash tint
    self.setColor(boss ? "#FFE040FB" : "#FFFF8A80");
    after(0.12, function () { self.setColor(boss ? "#FFB39DDB" : "#FFE8E4D8"); });
    if (hp <= 0) {
        alive = false;
        var d = scene.find("DqDirector");
        if (d != null) d.send("enemyDown", boss ? "boss" : "skeleton");
        var f = scene.find("Sparkle");
        if (f != null) { f.x = self.x; f.y = 1; f.z = self.z; f.burst(boss ? 80 : 20); }
        audio.play(boss ? "roar.wav" : "hit.wav", 0.8, boss ? 0.8 : 1.3);
        self.destroy();
    }
}
