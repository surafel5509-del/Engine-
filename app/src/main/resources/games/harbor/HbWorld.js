// HbWorld.js — mission director: cargo jobs, cash, sunset progression, game over.
var cash = 0, deliveries = 0, target = "", loaded = false, sunset = 0, over = false;
var PADS = ["Pad1", "Pad2", "Pad3", "Pad4", "Pad5"];

function start() {
    newJob();
    updateHud();
    audio.playMusic("Victory.song");
}

function update(dt) {
    if (over) return;
    // sky slides toward sunset as deliveries stack up
    sunset = Math.min(1, deliveries / 8);
    var cam = scene.find("Main Camera");
    if (cam != null) {
        cam.setProp("Camera3D", "Sky Top", mix(0x3E8ED8, 0x2A2154));
        cam.setProp("Camera3D", "Sky Horizon", mix(0xFFE3B0, 0xFF8A5A));
    }
}

function mix(a, b) {
    var ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
    var br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
    var r = Math.round(ar + (br - ar) * sunset), g = Math.round(ag + (bg - ag) * sunset), bl = Math.round(ab + (bb - ab) * sunset);
    return "#FF" + hex(r) + hex(g) + hex(bl);
}
function hex(v) { var s = Math.max(0, Math.min(255, v)).toString(16); return s.length < 2 ? "0" + s : s; }

function newJob() {
    loaded = false;
    target = PADS[Math.floor(Math.random() * PADS.length)];
    // light the target beacon green, dim the others
    for (var i = 0; i < PADS.length; i++) {
        var b = scene.find(PADS[i] + "Beacon");
        if (b != null) b.setColor(PADS[i] == target ? "#FF57D16A" : "#FF6B6B6B");
        var deck = scene.find(PADS[i]);
        if (deck != null) deck.setColor(PADS[i] == target ? "#FF3FE07F" : "#FF37474F");
    }
    ui.setText("JobText", loaded ? "Deliver to " + target : "Fly to the golden HARBOR pad to load cargo");
}

function loadedAtHarbor() {
    loaded = true;
    ui.setText("JobText", "Deliver to " + target + "!  (+=" + (60 + deliveries * 10) + "g)");
    audio.play("jump.wav", 0.7);
}

function isTarget(name) { return name == target && loaded; }

function delivered(name) {
    var pay = 60 + deliveries * 10;
    cash += pay;
    deliveries++;
    audio.play("win.wav", 0.6);
    ui.setText("JobText", "Delivered!  +" + pay + "g");
    newJob();
    updateHud();
}

function cash(amount) { cash += amount; updateHud(); }

function tick(dt) { }

function gameOver() {
    if (over) return;
    over = true;
    scene.setScale(0);
    var best = storage.getNumber("hb_best", 0);
    if (cash > best) storage.set("hb_best", cash);
    scene.find("OverStats").setText(deliveries + " deliveries  •  " + cash + " gold earned" +
        (cash > best && cash > 0 ? "  •  NEW RECORD!" : ""));
    scene.find("OverPanel").active = true;
    audio.play("lose.wav");
}

function updateHud() {
    ui.setText("JobText", "Deliveries " + deliveries + "  •  Cash " + cash);
}
