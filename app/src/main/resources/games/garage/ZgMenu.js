// ZgMenu.js
function start() {
    audio.playMusic("MenuTheme.song");
    var best = storage.getNumber("zg_best", 0);
    if (best > 0) scene.find("RecordText").setText("BEST  WAVE " + best);
}

function onUIClick(name) { audio.play("click.wav"); }
