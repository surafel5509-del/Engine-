// OwWorld.js — the sample's director: day/night cycle, drifting clouds, hopping slimes.
// Everything uses params: cloud=true | slime=true — one script, three jobs, like real jams.
var t = 0, DAY = 40; // seconds per full day

function start() {
    audio.playMusic("Adventure.song");
}

function update(dt) {
    if (cloud) {
        self.x += drift * dt;
        if (self.x > 22) self.x = -22;
        return;
    }
    if (slime) {
        t += dt * speed;
        self.x = self.x + Math.cos(t) * 0.004;
        self.scaleY = 0.9 + Math.abs(Math.sin(t * 3)) * 0.25;
        return;
    }
    // director: sky wash
    t += dt;
    var phase = (t % DAY) / DAY;             // 0..1
    var night = Math.max(0, Math.sin(phase * Math.PI * 2)) < 0.2;
    var wash = scene.find("Night");
    if (wash != null) {
        var a = night ? 150 : 0;
        var r = 0x10, g = 0x18, b = 0x30;
        wash.setColor("#" + hex(a) + hex(r) + hex(g) + hex(b));
    }
    var label = scene.find("TimeText");
    if (label != null) label.setText(night ? "Night" : "Day");
}

function hex(v) { var s = Math.max(0, Math.min(255, Math.round(v))).toString(16); return s.length < 2 ? "0" + s : s; }
