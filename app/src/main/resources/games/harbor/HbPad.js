// HbPad.js — behaviors for harbor pad, delivery pads, rings, buoys, storms (flag params).
var glowT = 0;

function start() { }

function update(dt) {
    glowT += dt;
    if (buoy) self.y = 0.35 + Math.sin(glowT * 2 + self.x) * 0.12;
    if (ring) {
        self.rotation += 40 * dt;
        self.scaleX = self.scaleY = self.scaleZ = 2.6 + Math.sin(glowT * 3) * 0.15;
    }
}

function onTrigger(other) {
    if (!other.tag || other.tag != "Player") return;
    var plane = other;
    if (ring) {
        plane.send("ringPickup");
        var sp = scene.find("Sparkle");
        if (sp != null) { sp.x = self.x; sp.y = self.y; sp.z = self.z; sp.burst(18); }
        self.active = false;
        after(30, function () { self.active = true; });
    } else if (buoy) {
        plane.send("buoyPickup");
        self.active = false;
        after(20, function () { self.active = true; });
    } else if (storm) {
        plane.send("bump", 18);
    } else if (home) {
        if (plane.send("loadCargo")) {
            var w = scene.find("HbWorld");
            if (w != null) w.send("loadedAtHarbor");
            audio.play("click.wav", 0.8);
        }
    } else {
        // delivery pad — only the lit one accepts
        var w = scene.find("HbWorld");
        if (w != null && w.send("isTarget", self.name)) {
            if (plane.send("deliverCargo")) w.send("delivered", self.name);
        }
    }
}
