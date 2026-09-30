// CaveMenu.js — Crystal Caverns main menu: music, records and level unlocks.
var musicOn = true;

function start() {
    audio.playMusic("Adventure.song");
    var saved = storage.get("caverns_stars");
    if (saved != null) scene.find("BestText").text = "STARS  " + saved + " / 9";
    var unlocked = storage.get("caverns_unlocked") != null ? storage.get("caverns_unlocked") : 1;
    for (var i = 1; i <= 3; i++) {
        var btn = scene.find("Level" + i + "Btn");
        if (btn == null) continue;
        if (i > unlocked) {
            btn.active = false;
        } else {
            btn.active = true;
            btn.color = i == 1 ? "#FF22C55E" : "#FF3A4566";
        }
    }
}

// Settings button ("Music: ON/OFF")
function music(name) {
    musicOn = !musicOn;
    scene.find("MusicBtn").text = musicOn ? "Music: ON" : "Music: OFF";
    if (musicOn) audio.playMusic("Adventure.song"); else audio.stopMusic();
    audio.play("click.wav");
}

function onUIClick(name) {
    if (name == "Level1Btn" || name == "Level2Btn" || name == "Level3Btn") audio.play("click.wav");
}
