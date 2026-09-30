// TdMenu.js — menu records + map unlock info.
function start() {
    audio.playMusic("MenuTheme.song");
    var b1 = storage.get("td_best_Td1") != null ? storage.get("td_best_Td1") : 0;
    var b2 = storage.get("td_best_Td2") != null ? storage.get("td_best_Td2") : 0;
    scene.find("Record").text = "BEST WAVES  —  Meadow " + b1 + "  •  Crossroads " + b2;
}

function onUIClick(name) { audio.play("click.wav"); }
