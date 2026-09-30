// DqMenu.js
function start() {
    audio.playMusic("MenuTheme.song");
    var wins = storage.getNumber("dq_wins", 0);
    if (wins > 0) scene.find("RecordText").setText("CRYPTS CLEARED  " + wins);
}

function onUIClick(name) { audio.play("click.wav"); }
