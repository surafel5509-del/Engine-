// PRICE OF FREEDOM — player controller.
// Keyboard: WASD move, Shift sprint, E interact, F search, R use, C hide,
// Tab inventory, M map, J journal, T time. The matching mobile controls are saved with the project.
var health = 100;
var maxHealth = 100;
var energy = 100;
var maxEnergy = 100;
var hidden = false;
var stepTimer = 0;
var moveTimer = 0;

function start() {
    input.setControls("project");
    input.showControls(true);
}

function update(dt) {
    var game = scene.find("Game");
    if (!game || game.send("isModal")) { self.vx = 0; self.vy = 0; return; }
    if (input.buttonDown("Interact") || input.buttonDown("PickUp") || input.aDown) game.send("interact");
    if (input.buttonDown("Search") || input.bDown) game.send("search");
    if (input.buttonDown("Hide")) game.send("toggleHide");
    if (input.buttonDown("Inventory")) game.send("toggleInventory");
    if (input.buttonDown("Map")) game.send("toggleMap");
    if (input.buttonDown("Journal")) game.send("toggleJournal");
    if (input.buttonDown("Time")) game.send("showTime");
    if (input.buttonDown("Use")) game.send("useItem");

    if (hidden) { self.vx = 0; self.vy = 0; return; }
    var x = input.axisX, y = input.axisY;
    var magnitude = Math.sqrt(x * x + y * y);
    var sprint = input.button("Run") && energy > 1 && magnitude > 0.15;
    var crouch = input.button("Crouch");
    var speed = sprint ? 4.25 : (crouch ? 1.35 : 2.65);
    if (energy <= 0) speed = 0.7;
    if (magnitude > 1) { x /= magnitude; y /= magnitude; }
    self.vx = x * speed;
    self.vy = y * speed;
    if (magnitude > 0.1) {
        self.rotation = Math.atan2(y, x) * 180 / Math.PI;
        self.flipX = x < -0.1;
        self.play(sprint ? "PrisonerRun.anim" : "PrisonerWalk.anim");
        if (sprint) energy = Math.max(0, energy - 6.0 * dt);
        else energy = Math.min(maxEnergy, energy + 0.65 * dt);
        stepTimer -= dt;
        if (stepTimer <= 0) { stepTimer = sprint ? 0.23 : 0.38; audio.play("step.wav", sprint ? 0.18 : 0.11, sprint ? 1.15 : 0.88); }
    } else {
        self.vx = 0; self.vy = 0;
        self.stopAnimation();
        energy = Math.min(maxEnergy, energy + 2.1 * dt);
    }
}

function isHidden() { return hidden; }
function setHidden(v) { hidden = v; self.visible = !v; if (v) self.stopAnimation(); }
function healthRatio() { return Math.max(0, health) / maxHealth; }
function energyRatio() { return Math.max(0, energy) / maxEnergy; }
function rest() { energy = Math.min(maxEnergy, energy + 22); health = Math.min(maxHealth, health + 8); }
function restoreEnergy(n) { energy = Math.min(maxEnergy, energy + n); }
function heal(n) { health = Math.min(maxHealth, health + n); }
function damage(n, source) {
    if (hidden || health <= 0) return;
    health -= n;
    audio.play("hit.wav", 0.6);
    scene.shake(0.22);
    platform.vibrate(30);
    if (health <= 0) { health = 0; scene.find("Game").send("hospitalized"); }
}
function captured() {
    hidden = false; self.visible = true;
    self.x = -13.2; self.y = 7.0;
    health = Math.max(25, health - 20);
    energy = Math.max(20, energy - 35);
}
function hospitalRecover() {
    hidden = false; self.visible = true;
    self.x = -1.0; self.y = 0.5;
    health = 70; energy = 65;
}
