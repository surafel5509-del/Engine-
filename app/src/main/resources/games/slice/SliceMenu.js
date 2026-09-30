// SliceMenu.js — best score + mode entry.
function start() {
    audio.playMusic("MenuTheme.song");
    var best = storage.get("slice_best");
    scene.find("BestText").text = "BEST " + (best != null ? best : 0);
}

// FrenzyBtn action: remember the mode, then enter the game scene
function startFrenzy(name) {
    storage.set("slice_mode", "frenzy");
    audio.play("click.wav");
    scene.load("Arcade");
}

function onUIClick(name) {
    if (name == "ClassicBtn") storage.set("slice_mode", "classic");
}
