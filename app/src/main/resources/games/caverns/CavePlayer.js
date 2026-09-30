// CavePlayer.js — the hero: run, double-height jump, hearts, spikes and enemies.
// Params: speed, jump
var gems = 0;
var totalGems = 0;
var hearts = 3;
var invuln = 0;
var coyote = 0;
var startX = 0, startY = 0;

function start() {
    startX = self.x; startY = self.y;
    totalGems = scene.count("Gem");
}

function update(dt) {
    // --- run with coyote time and variable jump height
    self.vx = input.axisX * speed;
    coyote = self.grounded ? 0.1 : coyote - dt;
    if (input.aDown && coyote > 0) { self.vy = jump; coyote = 0; audio.play("jump.wav"); }
    if (!input.a && self.vy > 2) self.vy *= 0.92;
    if (input.axisX < -0.1) self.flipX = true; else if (input.axisX > 0.1) self.flipX = false;

    // run animation while grounded
    if (Math.abs(self.vx) > 0.3 && self.grounded) self.play("HeroRun.anim"); else self.stopAnimation();

    // dust while running
    var dust = scene.find("Dust");
    if (dust != null && self.grounded && Math.abs(self.vx) > 2 && Math.random() < 0.2) {
        dust.x = self.x; dust.y = self.y - 0.5; dust.burst(2);
    }

    invuln -= dt;
    // fell into the abyss
    if (self.y < -14) damage(99);
}

function golemHit(hpLeft) {
    // the golem was stomped — small score reward flash, no damage to the player
    if (hpLeft <= 0) audio.play("win.wav", 0.4, 1.4);
}

function onTrigger(other) {
    if (other.name == "Spikes" && invuln <= 0) { damage(1); knock(); }
    if (other.name == "Checkpoint" && !other.touched) {
        other.touched = true;
        other.color = "#FF57D16A";
        startX = other.x; startY = other.y;
        audio.play("powerup.wav", 0.6);
    }
    if (other.tag == "Enemy" && invuln <= 0) { damage(1); knock(); }
    if (other.name == "Goal") scene.find("LevelManager").send("win");
}

function damage(n) {
    hearts -= n;
    invuln = 1.2;
    self.shaderParam = 1;                 // red flash shader
    after(0.25, function () { self.shaderParam = 0; });
    scene.find("Hearts").value = hearts / 3;
    audio.play("hit.wav");
    scene.shake(0.25);
    if (hearts <= 0) {
        self.active = false;
        var boom = scene.spawn("Dust", self.x, self.y);
        if (boom) boom.burst(40);
        scene.find("LevelManager").send("lose");
    }
}

function knock() {
    self.vy = 6;
    self.vx = -input.axisX * 5;
}

// gem counter (called by CaveGem.js)
function addGem() {
    gems++;
    scene.find("GemText").text = gems + " / " + totalGems;
}

// victory pose (called by the level manager)
function celebrate() {
    self.vy = 7;
    audio.play("powerup.wav");
}

// respawn helper used by the level manager
function respawn() {
    self.x = startX; self.y = startY + 0.5;
    self.vx = 0; self.vy = 0;
    self.active = true;
    hearts = 3;
    scene.find("Hearts").value = 1;
    invuln = 1.5;
}
