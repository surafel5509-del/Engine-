// OwNpc.js — Ada the builder. Shows a different line depending on your wood.
var lines = [
    "Welcome to the meadow! Chop trees with A.",
    "Collect 5 wood and you can build a campfire.",
    "The pond is pretty at night, isn't it?",
    "Try moving my marker or adding new lines to OwNpc.js!"
];
var line = 0, talkT = 0;

function update(dt) {
    // bob the "!" marker
    talkT += dt;
    var m = self.child("TalkMark");
    if (m != null) m.y = 0.8 + Math.sin(talkT * 4) * 0.08;
}

function talk(playerWood) {
    var t = scene.find("ToastText");
    if (t == null) return;
    if (playerWood >= 5) t.setText("Ada: You've got wood! Press A away from things to build.");
    else {
        t.setText("Ada: " + lines[line]);
        line = (line + 1) % lines.length;
    }
    audio.play("click.wav", 0.8);
}
