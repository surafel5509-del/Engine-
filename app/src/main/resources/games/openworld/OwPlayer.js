// OwPlayer.js — walk, interact with what's in front of you, build campfires.
// This is the "read me first" script of the sample: copy it, remix it, break it.
var wood = 0, faceX = 0, faceY = 1, walkT = 0;

function start() {
    updateHud();
}

function update(dt) {
    var mx = input.axisX, my = input.axisY;
    var len = Math.sqrt(mx * mx + my * my);
    if (len > 1) { mx /= len; my /= len; }

    self.x += mx * speed * dt;
    self.y += my * speed * dt;

    if (len > 0.1) {
        faceX = mx; faceY = my;
        walkT += dt;
        self.flipX = mx < 0;
        // little hop while walking (sprite bob)
        self.scaleY = 1.1 + Math.sin(walkT * 14) * 0.06;
    } else {
        self.scaleY = 1.1;
    }

    // A: interact with the nearest interesting thing
    if (input.aDown) interact();
}

function interact() {
    var target = nearest(["Tree", "Npc", "Campfire"], 1.6);
    if (target == null) { toast(""); return; }
    if (target.tag == "Tree") {
        var got = target.send("chop");
        if (got > 0) {
            wood += got;
            toast("+" + got + " wood");
            audio.play("hit.wav", 0.6, 1.2);
        }
        if (wood >= 5) toast("Enough wood! Stand in the open and press A to build.");
    } else if (target.tag == "Npc") {
        target.send("talk", wood);
    } else if (target.tag == "Campfire") {
        toast("The fire crackles. Cozy.");
    }
    // BUILD: with enough wood and nothing else nearby, place a campfire
    if (wood >= 5 && target == null) {
        wood -= 5;
        var f = scene.spawn("Campfire", self.x + faceX * 1.4, self.y + faceY * 1.4);
        if (f != null) { f.active = true; f.tag = "Campfire"; }
        toast("Campfire built!");
        audio.play("powerup.wav");
    }
    updateHud();
}

function nearest(tags, range) {
    var best = null, bestD = range;
    for (var t = 0; t < tags.length; t++) {
        var list = scene.findAll(tags[t]);
        for (var i = 0; i < list.length; i++) {
            var d = self.distanceTo(list[i]);
            if (d < bestD) { bestD = d; best = list[i]; }
        }
    }
    return best;
}

function toast(msg) { var t = scene.find("ToastText"); if (t != null) t.setText(msg); }

function updateHud() {
    var t = scene.find("WoodText");
    if (t != null) t.setText("Wood " + wood);
}
