// Turbo Rally menu: map + road selection, settings and records.
var maps = [["Green", "GREEN VALLEY"], ["Desert", "DESERT CANYON"]];
var roads = ["OVAL SPEEDWAY", "TWISTY CIRCUIT", "GRAND PRIX"];
var map = 0, road = 0;

function start() {
    input.setControls("none");
    map = storage.getNumber("rally_map", 0);
    road = storage.getNumber("rally_road", 0);
    if (storage.get("rally_music", true)) audio.playMusic("RacingRush.song", 0.4);
    refresh();
}

function trackName(m, r) { return maps[m][1] + " - " + roads[r]; }

function refresh() {
    for (var m = 0; m < 2; m++) { var b = scene.find("Map" + m); if (b) b.setProp("UIButton", "Color", m == map ? "#FF22C55E" : "#FF334155"); }
    for (var r = 0; r < 3; r++) { var rb = scene.find("Road" + r); if (rb) rb.setProp("UIButton", "Color", r == road ? "#FF22C55E" : "#FF334155"); }
    var best = storage.getNumber("rally_best_" + trackName(map, road), 0);
    ui.setText("TrackInfo", trackName(map, road) + "\nBest time: " + (best > 0 ? formatTime(best) : "--:--"));
    ui.setText("TrophyText", "TROPHIES " + storage.getNumber("rally_trophies", 0));
    ui.setText("LapsBtn", "Laps: " + storage.getNumber("rally_laps", 3));
    ui.setText("DiffBtn", "AI: " + ["Easy", "Normal", "Hard"][storage.getNumber("rally_difficulty", 1)]);
    ui.setText("QualityBtn", "Graphics: " + ["Low", "Medium", "High", "Ultra"][storage.getNumber("rally_quality", 2)]);
    ui.setText("MusicBtn", "Music: " + (storage.get("rally_music", true) ? "ON" : "OFF"));
    var cam = scene.camera3D;
    if (cam) cam.setProp("Camera3D", "Quality", storage.getNumber("rally_quality", 2));
}

function selectMap(btn) { map = parseInt(String(btn).replace("Map", "")); storage.set("rally_map", map); refresh(); }
function selectRoad(btn) { road = parseInt(String(btn).replace("Road", "")); storage.set("rally_road", road); refresh(); }
function startRace() { scene.load("Race_" + maps[map][0] + "_" + road); }
function cycleLaps() { var l = storage.getNumber("rally_laps", 3); l = l == 1 ? 3 : l == 3 ? 5 : 1; storage.set("rally_laps", l); refresh(); }
function cycleDifficulty() { storage.set("rally_difficulty", (storage.getNumber("rally_difficulty", 1) + 1) % 3); refresh(); }
function cycleQuality() { storage.set("rally_quality", (storage.getNumber("rally_quality", 2) + 1) % 4); refresh(); }
function toggleMusic() {
    var m = !storage.get("rally_music", true);
    storage.set("rally_music", m);
    if (m) audio.playMusic("RacingRush.song", 0.4); else audio.stopMusic();
    refresh();
}
