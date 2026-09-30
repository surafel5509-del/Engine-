// TdGame.js — build/upgrade/sell, waves, gold, lives, win/lose.
// Runs on "TdManager" (map=Td1|Td2, road=...) AND on every build "Spot" (spot=true).
var buildMode = null, gold = 150, lives = 20, wave = 0, alive = 0;
var isSpot = false, manager = null;
var COST = { ArrowTower: 50, CannonTower: 90, FrostTower: 70 };
var selected = null;
var queue = [], qi = 0, left = 0, spawning = false, speedMode = 1;
var ROAD = null, mapKey = "";

function start() {
    if (spot) { isSpot = true; manager = scene.find("TdManager"); return; }
    manager = self;
    mapKey = map;
    ROAD = [];
    var flat = road.split("|");
    for (var i = 0; i + 1 < flat.length; i += 2) ROAD.push([parseFloat(flat[i]), parseFloat(flat[i + 1])]);
    audio.playMusic("Battle.song");
    updateHud();
}

function update(dt) {
    if (isSpot || over() || !spawning) return;
    if (alive <= 0 && left <= 0 && qi >= queue.length) waveCleared();
}

// ------------------------------------------------------------- building (manager side)
function pickArrow() { pick("ArrowTower"); }
function pickCannon() { pick("CannonTower"); }
function pickFrost() { pick("FrostTower"); }

function pick(t) {
    if (isSpot) return;
    if (over()) return;
    if (gold < COST[t]) return flashGold();
    buildMode = (buildMode == t) ? "" : t;
    audio.play("click.wav", 0.6);
    glowSpots(buildMode != "");
}

function getMode() { return buildMode; }

function tryBuild(t) {
    if (isSpot) return false;
    if (gold < COST[t]) { flashGold(); return false; }
    gold -= COST[t];
    buildMode = "";
    glowSpots(false);
    updateHud();
    return true;
}

function glowSpots(on) {
    var spots = scene.findAll("Spot");
    for (var i = 0; i < spots.length; i++) {
        var g = spots[i].child("SpotGlow");
        if (g != null) g.active = on;
    }
}

// ------------------------------------------------------------- spot taps
function onTap() {
    if (!isSpot || manager == null || self.tag != "Spot") return;
    var t = manager.send("getMode");
    if (t == null || t == "") return;
    if (!manager.send("tryBuild", t)) return;
    var tw = scene.spawn(t, self.x, self.y);
    if (tw == null) return;
    self.tag = "UsedSpot";
    audio.play("click.wav", 0.8);
}

// ------------------------------------------------------------- tower popup
function towerTapped(tw) {
    selected = tw;
    var lv = tw.send("getLevel"), dmg = tw.send("getDamage"), rng = tw.send("getRange");
    scene.find("TowerName").setText(tw.name + (lv > 1 ? "  Lv." + lv : ""));
    scene.find("TowerInfo").setText("damage " + dmg + "  •  range " + Math.round(rng * 10) / 10);
    scene.find("UpgradeBtn").setText(lv >= 3 ? "MAX LEVEL" : "Upgrade 80g");
    scene.find("TowerPanel").active = true;
}

function upgradeTower(name) {
    if (selected == null || selected.send("getLevel") >= 3) return;
    if (gold < 80) return flashGold();
    gold -= 80;
    selected.send("upgrade");
    audio.play("powerup.wav");
    scene.find("TowerPanel").active = false;
    updateHud();
}

function sellTower(name) {
    if (selected == null) return;
    gold += Math.floor(COST[selected.name] * 0.6) + 40 * (selected.send("getLevel") - 1);
    // free its spot again
    var spots = scene.findAll("UsedSpot");
    for (var i = 0; i < spots.length; i++) {
        if (Math.abs(spots[i].x - selected.x) < 0.6 && Math.abs(spots[i].y - selected.y) < 0.6) spots[i].tag = "Spot";
    }
    selected.destroy();
    selected = null;
    scene.find("TowerPanel").active = false;
    audio.play("coin.wav", 0.7);
    updateHud();
}

// ------------------------------------------------------------- waves
function startWave() {
    if (isSpot) return;
    if (spawning || over() || wave >= 10) return;
    wave++;
    spawning = true;
    scene.find("WaveBtn").active = false;
    audio.play("click.wav");
    var hp = 20 + wave * 12;
    queue = [];
    if (wave % 5 == 0) {
        queue.push({ kind: "Boss", hp: hp * 6, n: 2 + Math.floor(wave / 5), gap: 2.2 });
    } else {
        var n = 5 + wave * 2;
        queue.push({ kind: "Grunt", hp: hp, n: Math.ceil(n * 0.7), gap: 0.9 });
        queue.push({ kind: "Runner", hp: Math.floor(hp * 0.6), n: Math.floor(n * 0.3), gap: 0.55 });
    }
    qi = 0; left = queue[0].n;
    after(0.8, spawnTick);
    updateHud();
}

function spawnTick() {
    if (isSpot) return;
    if (over()) return;
    if (left <= 0) {
        qi++;
        if (qi >= queue.length) return;
        left = queue[qi].n;
    }
    var g = queue[qi];
    var e = scene.spawn(g.kind, ROAD[0][0] - 1.5, ROAD[0][1]);
    if (e != null) { e.send("init", g.hp); alive++; }
    left--;
    after(g.gap, spawnTick);
}

function waveCleared() {
    if (isSpot) return;
    spawning = false;
    gold += 60 + wave * 10;
    audio.play("win.wav", 0.5);
    if (wave >= 10) return victory();
    scene.find("WaveBtn").active = true;
    updateHud();
}

function enemyDied(reward) { alive--; gold += reward; updateHud(); }

function enemyReached() {
    alive--;
    lives--;
    scene.shake(0.3);
    audio.play("hit.wav");
    updateHud();
    if (lives <= 0) defeat();
}

// road lookup for enemies (they ask the manager)
function roadAt(i) { return ROAD[i]; }
function roadCount() { return ROAD.length; }

function victory() {
    if (isSpot) return;
    scene.setScale(0);
    var best = storage.getNumber("td_best_" + mapKey, 0);
    if (10 > best) storage.set("td_best_" + mapKey, 10);
    scene.find("WinStats").setText("All 10 waves cleared  •  " + gold + "g left  •  " + lives + " lives");
    scene.find("WinPanel").active = true;
    audio.play("win.wav");
}

function defeat() {
    if (isSpot) return;
    scene.setScale(0);
    var best = storage.getNumber("td_best_" + mapKey, 0);
    if (wave > best) storage.set("td_best_" + mapKey, wave);
    scene.find("LoseStats").setText("You reached wave " + wave + " of 10");
    scene.find("LosePanel").active = true;
    audio.play("lose.wav");
}

function over() { return scene.getScale() == 0; }

// ------------------------------------------------------------- misc
function speed(name) {
    if (isSpot) return;
    speedMode = speedMode == 1 ? 2 : 1;
    scene.setScale(speedMode);
    scene.find("SpeedBtn").setText("x" + speedMode);
    audio.play("click.wav", 0.5);
}

function updateHud() {
    if (isSpot) return;
    scene.find("GoldText").setText(gold + "g");
    scene.find("LivesText").setText("" + lives);
    scene.find("WaveText").setText("Wave " + wave + "/10");
}

function flashGold() {
    if (isSpot) return;
    var t = scene.find("GoldText");
    t.setColor("#FF5C6C");
    after(0.4, function () { t.setColor("#FFD166"); });
    audio.play("hit.wav", 0.4);
}
