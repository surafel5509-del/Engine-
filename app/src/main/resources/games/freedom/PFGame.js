// PRICE OF FREEDOM — prison director.
// This script owns the simulation: clock, schedule, relationships, inventory, searches,
// evidence, route validation, save records and every UI screen. Edit it in S Engine's Script Editor.
var clockMinutes = 405; // 06:45. One real minute advances one prison minute.
var day = 1;
var suspicion = 5;
var knowledge = 0;
var friends = 0;
var items = {};
var relations = {};
var helped = {};
var modal = true;
var currentTalk = "";
var alertTimer = 0;
var toastTimer = 0;
var captureLock = 0;
var searchTimer = 150;
var escaped = false;
var ended = false;

var NAMES = {
    rope: "Rope", gloves: "Work gloves", shoes: "Quiet shoes", flashlight: "Flashlight", mask: "Dust mask",
    evidence: "Case evidence", coffee: "Coffee", spoon: "Kitchen spoon", hook: "Grappling hook", map: "Prison map",
    shovel: "Workshop shovel", hammer: "Engineer hammer", machine: "Bypass machine", keycard: "Guard keycard",
    key: "Chief's key", code: "Security code", forgery: "Forged pass", medkit: "Medical kit"
};
var NPC = {
    DoctorYosef: ["Doctor Yosef", "Medicine is never free here. Help me keep the infirmary quiet and I will make sure you leave standing.", "medkit"],
    EngineerSamuel: ["Engineer Samuel", "Machines fail because people stop listening. Earn my trust and I can hide a shovel, hammer and bypass machine in plain sight.", "machine"],
    MapMakerMulu: ["Map Maker Mulu", "A prison only looks confusing to people without a map. I know every service route and camera blind spot.", "map"],
    ThiefBenjamin: ["Thief Benjamin", "Keys are only metal until somebody needs them. I can acquire a keycard, but I never work for free.", "keycard"],
    ConmanGirma: ["Conman Girma", "Uniforms open doors. Stories open the rest. I can forge a pass that will make a guard hesitate.", "forgery"],
    KillerTemesgen: ["Killer Temesgen", "I do not need a friend. I need a reason. Give me one and I will stand with you when the wall comes down.", "friend"]
};

function start() {
    if (storage.get("pf_music", true)) audio.playMusic("SpookyNight.song", 0.38);
    setTime();
    refreshHud();
    prompt("Read the room. Your first move is not your last.");
}

function update(dt) {
    if (ended) return;
    // The UI button uses the engine's built-in hide action, then this director opens play on the next frame.
    // Keeping the state transition here means UI Creator edits cannot strand the player in the briefing.
    if (!introDone && !scene.find("IntroPanel").active) {
        introDone = true;
        modal = false;
        banner("DAY ONE — OBSERVE BEFORE YOU ACT", 2.4);
    }
    // The campaign clock intentionally follows real time: 60 seconds of play = one prison minute.
    clockMinutes += dt / 60.0;
    if (clockMinutes >= 1440) {
        clockMinutes -= 1440;
        day++;
        suspicion = Math.max(0, suspicion - 8);
        if (day > 30) { fail("Thirty days passed. The case was buried before you could act."); return; }
        banner("DAY " + day + " — THE PRISON REMEMBERS", 2.2);
    }
    captureLock = Math.max(0, captureLock - dt);
    alertTimer = Math.max(0, alertTimer - dt);
    if (alertTimer <= 0) ui.hide("AlertText");
    if (toastTimer > 0) { toastTimer -= dt; if (toastTimer <= 0) ui.setText("PromptText", ""); }
    searchTimer -= dt;
    if (searchTimer <= 0) { searchTimer = 150 + random(0, 100); searchEvent(); }
    setTime();
    refreshHud();
    if (!modal) updatePrompt();
}

function timeOfDay() { return Math.floor(clockMinutes / 60); }
function minuteOfHour() { return Math.floor(clockMinutes % 60); }
function clockString() {
    var h = timeOfDay(), m = minuteOfHour();
    return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
}
function schedule() {
    var h = timeOfDay();
    if (h < 7) return "WAKE / ROLL CALL";
    if (h < 12) return "WORK DETAIL";
    if (h < 13) return "LUNCH";
    if (h < 18) return "OPEN DETAIL";
    if (h < 22) return "DINNER / COUNT";
    return "LIGHTS OUT";
}
function setTime() { ui.setText("TimeText", "DAY " + day + "  •  " + clockString() + "  •  " + schedule()); }

function closest(tag, radius) {
    var list = scene.findAll(tag), best = null, bestD = radius * radius;
    var p = scene.find("Player");
    if (!p) return null;
    for (var i = 0; i < list.length; i++) {
        var dx = list[i].x - p.x, dy = list[i].y - p.y, d = dx * dx + dy * dy;
        if (d < bestD) { best = list[i]; bestD = d; }
    }
    return best;
}

function updatePrompt() {
    var target = closest("Item", 1.25);
    if (target) { ui.setText("PromptText", "E / INTERACT  •  Take " + itemLabel(target.send("getKind"))); return; }
    target = closest("Prisoner", 1.35);
    if (target) { ui.setText("PromptText", "E / INTERACT  •  Talk to " + humanName(target.name)); return; }
    target = closest("Guard", 1.35);
    if (target) { ui.setText("PromptText", "E / INTERACT  •  Speak carefully to " + humanName(target.name)); return; }
    target = closest("Point", 1.35);
    if (target) { ui.setText("PromptText", pointPrompt(target.name)); return; }
    ui.setText("PromptText", "WASD / joystick to move • E interact • F search • Tab inventory");
}

function humanName(id) {
    return ({ DoctorYosef: "Doctor Yosef", EngineerSamuel: "Engineer Samuel", MapMakerMulu: "Map Maker Mulu", ThiefBenjamin: "Thief Benjamin", ConmanGirma: "Conman Girma", KillerTemesgen: "Killer Temesgen", SergeantTesfaye: "Sergeant Tesfaye", GuardDawit: "Guard Dawit", GuardSolomon: "Guard Solomon", SergeantMarta: "Sergeant Marta", ChiefAbebe: "Chief Guard Abebe" })[id] || id;
}
function itemLabel(k) { return NAMES[k] || k; }
function pointPrompt(n) {
    if (n.indexOf("Hide") == 0) return "C / HIDE  •  Take cover";
    if (n == "SewerHatch") return "E / INTERACT  •  Inspect sewer hatch";
    if (n == "TunnelSpot") return "E / INTERACT  •  Inspect tunnel ground";
    if (n == "WallClimb") return "E / INTERACT  •  Inspect outer wall";
    if (n == "Helipad") return "E / INTERACT  •  Inspect roof access";
    if (n == "MainGate") return "E / INTERACT  •  Inspect main gate";
    return "E / INTERACT  •  Search " + n.replace(/([A-Z])/g, " $1");
}

function interact() {
    if (modal || ended) return;
    var o = closest("Item", 1.35);
    if (o) { o.send("take"); return; }
    o = closest("Prisoner", 1.4);
    if (o) { o.send("talk"); return; }
    o = closest("Guard", 1.35);
    if (o) { o.send("talk"); return; }
    o = closest("Point", 1.45);
    if (o) { interactPoint(o.name); return; }
    toast("Nothing useful within reach.");
}
function search() {
    if (modal || ended) return;
    suspicion = Math.min(100, suspicion + (timeOfDay() >= 22 ? 1 : 2));
    interact();
}

function collect(k) {
    addItem(k, 1);
    if (k == "evidence") { knowledge += 30; objective("Protect the case evidence. Choose an escape route."); }
    else if (k == "spoon") knowledge += 5;
    else if (k == "hook" || k == "flashlight") knowledge += 8;
    toast("Taken: " + itemLabel(k));
    audio.play("powerup.wav", 0.35);
}
function addItem(k, amount) { items[k] = (items[k] || 0) + amount; }
function removeItem(k, amount) {
    if (!has(k, amount)) return false;
    items[k] -= amount; return true;
}
function has(k, amount) { return (items[k] || 0) >= (amount || 1); }
function inventoryCount(k) { return items[k] || 0; }

function talkPrisoner(id) {
    if (!NPC[id]) return;
    currentTalk = id; modal = true; ui.show("DialogPanel");
    var n = NPC[id];
    var relation = relations[id] || 0;
    ui.setText("DialogPanelTitle", n[0].toUpperCase());
    ui.setText("DialogText", n[1] + "\n\nRelationship: " + relation + " / 100" + (helped[id] ? "\n\nYou have already earned this person's trust." : "\n\nChoose OFFER HELP to build trust and receive their favor."));
    ui.setText("DialogHelpBtn", helped[id] ? "Already helped" : "Offer help");
}
function talkGuard(id) {
    if (id.indexOf("Camera") == 0) { raiseAlarm(7, "camera"); toast("The camera turns toward you."); return; }
    if (id == "SergeantTesfaye") {
        if (has("coffee")) { removeItem("coffee", 1); addItem("key", 1); relations[id] = (relations[id] || 0) + 25; knowledge += 10; toast("Tesfaye takes the coffee. A heavy key changes hands."); audio.play("select.wav", 0.5); }
        else toast("Tesfaye: 'Rules are rules. Unless you have something worth my time.'");
    } else if (id == "GuardDawit") {
        relations[id] = (relations[id] || 0) + 5;
        toast("Dawit scans the corridor nervously. 'The east hatch is never checked at lunch.'"); knowledge += 5;
    } else if (id == "GuardSolomon") {
        toast("Solomon yawns. He is weakest after lights out."); knowledge += 5;
    } else if (id == "ChiefAbebe") {
        raiseAlarm(4, "Abebe"); toast("Abebe studies your face. Do not give him a reason.");
    } else toast(humanName(id) + " does not want to talk.");
}
function dialogHelp() {
    if (!currentTalk || helped[currentTalk]) { toast("That favor has already been settled."); return; }
    helped[currentTalk] = true;
    relations[currentTalk] = (relations[currentTalk] || 0) + 35;
    friends++;
    var reward = NPC[currentTalk][2];
    if (currentTalk == "DoctorYosef") { addItem("medkit", 1); addItem("mask", 1); knowledge += 10; }
    if (currentTalk == "EngineerSamuel") { addItem("shovel", 1); addItem("hammer", 1); addItem("machine", 1); knowledge += 15; }
    if (currentTalk == "MapMakerMulu") { addItem("map", 1); knowledge += 20; }
    if (currentTalk == "ThiefBenjamin") { addItem("keycard", 1); knowledge += 10; }
    if (currentTalk == "ConmanGirma") { addItem("forgery", 1); knowledge += 10; }
    if (currentTalk == "KillerTemesgen") { knowledge += 5; suspicion = Math.min(100, suspicion + 8); }
    ui.hide("DialogPanel"); modal = false;
    toast(humanName(currentTalk) + " is now an ally. Favor received: " + (reward == "friend" ? "an accomplice" : itemLabel(reward)) + ".");
    audio.play("win.wav", 0.38);
    currentTalk = "";
}

function interactPoint(n) {
    if (n == "CellBed") {
        var p = scene.find("Player"); p.send("rest");
        if (timeOfDay() >= 22 || timeOfDay() < 6) { clockMinutes = 360; day++; toast("You sleep lightly. Another day begins."); } else toast("A short rest steadies your hands.");
    } else if (n == "KitchenCache") {
        if (!has("spoon")) { addItem("spoon", 1); addItem("coffee", 1); knowledge += 10; toast("Behind the food crates: a spoon and a coffee packet."); }
        else toast("The kitchen cache is empty.");
    } else if (n == "HospitalCabinet") {
        if (!has("medkit")) { addItem("medkit", 1); toast("A small medical kit disappears into your clothes."); } else toast("The cabinet has been picked clean.");
    } else if (n == "BathroomVent") {
        if (!has("mask")) { addItem("mask", 1); addItem("rope", 1); knowledge += 10; toast("The vent holds a mask and a knotted line."); } else toast("The vent is empty.");
    } else if (n == "WorkshopBench") {
        if ((relations.EngineerSamuel || 0) >= 20) { addItem("gloves", 1); toast("Samuel nods. You find work gloves under the bench."); }
        else { raiseAlarm(4, "workshop"); toast("The tools are chained. Samuel may know how to help."); }
    } else if (n == "LibraryDesk") {
        if (!has("evidence")) { addItem("evidence", 1); knowledge += 30; objective("You have proof. Build a route out before day 30."); toast("Inside an old legal volume: the evidence that clears your name."); }
        else toast("Your evidence is hidden safely in your clothes.");
    } else if (n == "SecurityTerminal") {
        if (!has("keycard")) { raiseAlarm(10, "terminal"); toast("ACCESS DENIED. You need a guard keycard."); }
        else if (!has("code")) { addItem("code", 1); knowledge += 30; toast("You copy the security override code before the terminal locks."); }
        else toast("You already know the security code.");
    } else if (n.indexOf("Hide") == 0) toggleHide();
    else if (n == "MainGate") attemptEscape("gate");
    else if (n == "TunnelSpot") attemptEscape("tunnel");
    else if (n == "WallClimb") attemptEscape("wall");
    else if (n == "Helipad") attemptEscape("helicopter");
    else if (n == "SewerHatch") attemptEscape("sewer");
}

function toggleHide() {
    var p = scene.find("Player");
    if (!p) return;
    if (p.send("isHidden")) { p.send("setHidden", false); toast("You leave cover."); return; }
    var h = closest("Point", 1.5);
    if (h && h.name.indexOf("Hide") == 0) { p.send("setHidden", true); toast("You hold still in cover. Guards cannot see a patient shadow."); }
    else toast("You need a locker, crate or pews to hide properly.");
}

function useItem() {
    var p = scene.find("Player");
    if (has("medkit")) { removeItem("medkit", 1); p.send("heal", 30); toast("You patch yourself up. +30 health."); return true; }
    if (has("coffee")) { removeItem("coffee", 1); p.send("restoreEnergy", 20); toast("Coffee buys a little focus. +20 energy."); return true; }
    toast("You have nothing you can use right now."); return false;
}

function attemptEscape(route) {
    if (!has("evidence")) { toast("Escape without proof is only running. Find the case evidence first."); return; }
    var missing = [];
    if (route == "gate") {
        need("key", 1, missing); need("keycard", 1, missing); need("code", 1, missing); need("machine", 1, missing);
    } else if (route == "tunnel") {
        need("spoon", 1, missing); need("shovel", 1, missing); need("hammer", 1, missing); need("rope", 1, missing); need("map", 1, missing);
        if (timeOfDay() < 22 && timeOfDay() > 5) missing.push("darkness");
    } else if (route == "wall") {
        need("rope", 3, missing); need("hook", 1, missing); need("gloves", 1, missing); need("shoes", 1, missing); if (friends < 1) missing.push("an accomplice");
    } else if (route == "helicopter") {
        need("map", 1, missing); need("machine", 1, missing); need("forgery", 1, missing); if (friends < 5) missing.push((5 - friends) + " more allies");
    } else if (route == "sewer") {
        need("flashlight", 1, missing); need("mask", 1, missing); need("rope", 1, missing); need("gloves", 1, missing); if (friends < 1) missing.push("an accomplice");
    }
    if (missing.length > 0) { toast("Route incomplete: " + missing.join(", ") + "."); return; }
    win(route);
}
function need(k, n, out) { if (!has(k, n)) out.push((n > 1 ? n + "× " : "") + itemLabel(k).toLowerCase()); }

function raiseAlarm(amount, source) {
    if (modal || ended) return;
    suspicion = Math.min(100, suspicion + amount);
    alertTimer = 2.5;
    ui.setText("AlertText", "SUSPICION RISING"); ui.show("AlertText");
    var sparks = scene.spawn("AlarmSparks", scene.find("Player").x, scene.find("Player").y);
    if (sparks) { sparks.burst(10); after(0.5, function() { sparks.destroy(); }); }
    if (suspicion >= 92) capture(source || "guard");
}
function capture(source) {
    if (captureLock > 0 || ended) return;
    captureLock = 8;
    suspicion = 25;
    knowledge = Math.max(0, knowledge - 10);
    day++;
    clockMinutes = 360;
    var p = scene.find("Player");
    p.send("captured");
    toast("Caught by " + source + ". Isolation costs a day and hard-earned knowledge.");
    banner("LOCKDOWN", 2.2);
    if (day > 30) fail("The final lockdown ended your chance to expose the truth.");
}
function hospitalized() {
    day += 3; clockMinutes = 360; suspicion = 12;
    var p = scene.find("Player"); p.send("hospitalRecover");
    toast("Your injuries force three days in hospital. The plan survives, barely.");
}
function searchEvent() {
    if (modal || ended || suspicion < 20) return;
    var contraband = has("spoon") || has("keycard") || has("evidence") || has("code");
    if (!contraband) return;
    suspicion = Math.min(100, suspicion + 8);
    banner("CELL SEARCH", 2.0);
    toast("Guards search your bunk and locker. Keep important items moving.");
}

function objective(t) { ui.setText("ObjectiveText", "OBJECTIVE: " + t); }
function toast(t) { ui.setText("PromptText", t); toastTimer = 3.0; }
function banner(t, sec) { ui.setText("AlertText", t); ui.show("AlertText"); alertTimer = sec || 2; }
function refreshHud() {
    var p = scene.find("Player");
    if (p) { ui.setProgress("HealthBar", p.send("healthRatio")); ui.setProgress("EnergyBar", p.send("energyRatio")); }
    ui.setProgress("SuspicionBar", suspicion / 100);
    ui.setText("KnowledgeText", "KNOWLEDGE " + Math.round(knowledge) + "  •  ALLIES " + friends + "/5");
}

function showTime() { banner("DAY " + day + " • " + clockString() + " • " + schedule(), 2.0); }
function toggleInventory() { if (modal && !scene.find("InventoryPanel").active) return; openPanel("InventoryPanel"); }
function toggleMap() { if (modal && !scene.find("MapPanel").active) return; openPanel("MapPanel"); }
function toggleJournal() { if (modal && !scene.find("JournalPanel").active) return; openPanel("JournalPanel"); }
function openPanel(panel) {
    if (scene.find(panel).active) { closeModal(); return; }
    modal = true;
    if (panel == "InventoryPanel") ui.setText("InventoryText", inventoryText());
    if (panel == "JournalPanel") ui.setText("JournalText", journalText());
    ui.show(panel);
}
function closeIntro() { ui.hide("IntroPanel"); modal = false; banner("DAY ONE — OBSERVE BEFORE YOU ACT", 2.4); }
function closeModal() {
    ui.hide("InventoryPanel"); ui.hide("MapPanel"); ui.hide("JournalPanel"); ui.hide("DialogPanel");
    modal = false; currentTalk = "";
}
function isModal() { return modal || ended; }
function inventoryText() {
    var out = "TOOLS & EVIDENCE\n\n"; var any = false;
    for (var k in NAMES) if ((items[k] || 0) > 0) { out += "• " + itemLabel(k) + (items[k] > 1 ? "  ×" + items[k] : "") + "\n"; any = true; }
    if (!any) out += "Empty. Search quietly and talk to prisoners.\n";
    out += "\nUse R / USE to consume a medical kit or coffee.\n\nEvidence: " + (has("evidence") ? "SECURED" : "MISSING") + "\nKnowledge: " + knowledge + "\nAllies: " + friends + " / 5";
    return out;
}
function journalText() {
    return "DAILY SCHEDULE\n06:00 wake  •  07:00 roll call  •  08:00 work\n12:00 lunch  •  18:00 dinner  •  22:00 lights out\n\nCURRENT LEAD\n" + (has("evidence") ? "Your case evidence is secure. Pick a route at the Gate, Tunnel, Wall, Helipad or Sewer Hatch." : "Search the Library for the case evidence. Build trust with the people who know the prison.") + "\n\nESCAPE ROUTES\nGate: key, keycard, code, machine.\nTunnel: spoon, shovel, hammer, rope, map, night.\nWall: 3 rope, hook, gloves, shoes, ally.\nHelicopter: 5 allies, map, machine, forged pass.\nSewer: flashlight, mask, rope, gloves, ally.\n\nRemember: people remember how you treat them.";
}
function fail(reason) {
    ended = true; modal = true; audio.stopMusic(); audio.play("lose.wav", 0.8);
    ui.setText("FailureText", reason + "\n\nThe prison remains. Try another plan."); ui.show("FailurePanel");
}
function win(route) {
    escaped = true; ended = true; modal = true; audio.stopMusic(); audio.playMusic("Victory.song", 0.55); audio.play("win.wav", 1);
    var names = { gate: "the Main Gate", tunnel: "the tunnel", wall: "the outer wall", helicopter: "the helicopter", sewer: "the sewer" };
    storage.set("pf_escapes", storage.getNumber("pf_escapes", 0) + 1);
    storage.set("pf_best_day", Math.min(storage.getNumber("pf_best_day", 99), day));
    storage.set("pf_last_route", route);
    ui.setText("VictoryText", "You escaped through " + names[route] + " on day " + day + ".\n\nThe evidence is out. Your name can be cleared.\n\nKnowledge gathered: " + knowledge + "\nAllies who trusted you: " + friends + "\nSuspicion at escape: " + Math.round(suspicion) + "%");
    ui.show("VictoryPanel");
}
// UI actions also broadcast onUIClick. Keep this small fallback so the first-day prompt remains
// responsive even when a project owner rewires the UI Creator button action.
function onUIClick(name) {
    if (name == "StartDayBtn") closeIntro();
}
function onStop() { audio.stopMusic(); }
