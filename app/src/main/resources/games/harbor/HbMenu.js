// HbMenu.js — pilot record.
function start() {
    audio.playMusic("Adventure.song");
    var cash = storage.getNumber("hb_best", 0);
    if (cash > 0) scene.find("RecordText").setText("BEST HAUL  " + cash + " gold");
}

function onUIClick(name) { audio.play("click.wav"); }
