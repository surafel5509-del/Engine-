// Turbo Rally race manager: arcade car physics, AI drivers, laps, positions, HUD, minimap, results.
// Params: trackFile, trackName
var trackFile = "Track.json", trackName = "Track";
var pts, N, W, segAng = [], turnAhead = [], cars = [], player, laps = 3, difficulty = 1;
var state = "intro", countdown = 4.2, raceTime = 0, lastCount = 5, finishOrder = [];
var nitro = 1, engineStream = -1, camMode = 0, cam, camX = 0, camY = 0, camZ = 0, lapStart = 0, bestLap = 0;
var mapCx = 0, mapCz = 0, mapScale = 0.01, resultsShown = false, wrongWayT = 0, skidT = 0;

function start() {
    var data = loadJSON(trackFile);
    pts = data.points; N = pts.length; W = data.width;
    for (var i = 0; i < N; i++) {
        var a = pts[i], b = pts[(i + 1) % N];
        segAng.push(Math.atan2(b[0] - a[0], b[1] - a[1]));
    }
    for (var j = 0; j < N; j++) {
        var t = 0;
        for (var k = 0; k < 10; k++) t += Math.abs(wrap(segAng[(j + k + 1) % N] - segAng[(j + k) % N]));
        turnAhead.push(t);
    }
    var mm = data.minimap; mapCx = mm[0]; mapCz = mm[1]; mapScale = mm[2];
    laps = storage.getNumber("rally_laps", 3);
    difficulty = storage.getNumber("rally_difficulty", 1);
    input.setControls("racing");
    input.showControls(true);
    cam = scene.camera3D;
    if (cam) {
        cam.setProp("Camera3D", "Quality", storage.getNumber("rally_quality", 2));
        cam.setProp("Camera3D", "Follow Target", "");
    }
    var names = ["Player", "AI1", "AI2", "AI3", "AI4", "AI5"];
    var drivers = ["YOU", "Blaze", "Nova", "Viper", "Rex", "Luna"];
    for (var n = 0; n < names.length; n++) {
        var o = scene.find(names[n]);
        if (!o) continue;
        var c = {
            obj: o, name: drivers[n], ai: n > 0, x: o.x, z: o.z, h: o.rotY * Math.PI / 180, vx: 0, vz: 0, speed: 0,
            idx: 0, lap: -1, lat: 0, progress: 0, finished: false, time: 0, lane: 0, laneT: 0, top: 50, accel: 15, grip: 1,
            dot: scene.find("Dot" + n), drift: false, wrongT: 0
        };
        c.idx = nearest(c.x, c.z, 0, N);
        if (c.ai) {
            var base = [40, 46, 51][difficulty];
            c.top = base + random(-2.5, 2.5) - n * 0.6;
            c.accel = 13 + difficulty * 1.5 + random(-1, 1);
            c.grip = [9.5, 12, 14.5][difficulty] + random(-0.8, 0.8);
            c.lane = random(-W * 0.25, W * 0.25);
        } else {
            player = c;
            c.top = 52; c.accel = 15.5;
        }
        o.playModelAnim("Drive");
        o.setModelAnimSpeed(0);
        cars.push(c);
    }
    var best = storage.getNumber("rally_best_" + trackName, 0);
    ui.setText("BestText", best > 0 ? "BEST " + formatTime(best) : "BEST --:--");
    ui.setText("LapText", "LAP 1/" + laps);
    ui.setText("TrackText", trackName);
    if (storage.get("rally_music", true)) audio.playMusic("RacingRush.song", 0.45);
    engineStream = audio.loop("engine.wav", 0.35);
    placeCamera(1);
    ui.show("CountText");
    ui.setText("CountText", "");
}

function wrap(a) { while (a > Math.PI) a -= Math.PI * 2; while (a < -Math.PI) a += Math.PI * 2; return a; }

function nearest(x, z, from, count) {
    var best = from, bd = 1e18;
    for (var i = 0; i < count; i++) {
        var k = ((from + i) % N + N) % N, p = pts[k];
        var d = (p[0] - x) * (p[0] - x) + (p[1] - z) * (p[1] - z);
        if (d < bd) { bd = d; best = k; }
    }
    return best;
}

function track(c) {
    var old = c.idx;
    c.idx = nearest(c.x, c.z, old - 3, 12);
    if (c.idx < N * 0.25 && old > N * 0.75) lapCross(c);
    else if (c.idx > N * 0.75 && old < N * 0.25) c.lap--;
    var a = pts[c.idx], b = pts[(c.idx + 1) % N];
    var dx = b[0] - a[0], dz = b[1] - a[1], L = Math.sqrt(dx * dx + dz * dz) || 1;
    var px = c.x - a[0], pz = c.z - a[1];
    var t = clamp((px * dx + pz * dz) / (L * L), 0, 1);
    c.lat = (px * dz - pz * dx) / L;
    c.progress = c.lap * N + c.idx + t;
    // wall (tyre barrier) collision
    var limit = W / 2 + 4.2;
    if (Math.abs(c.lat) > limit) {
        var over = Math.abs(c.lat) - limit, sgn = c.lat > 0 ? 1 : -1;
        var nx = dz / L * sgn, nz = -dx / L * sgn;
        c.x -= nx * over; c.z -= nz * over;
        var vn = c.vx * nx + c.vz * nz;
        if (vn > 0) { c.vx -= nx * vn * 1.5; c.vz -= nz * vn * 1.5; c.vx *= 0.8; c.vz *= 0.8; }
        if (!c.ai && vn > 4) { scene.shake(Math.min(0.6, vn * 0.04)); platform.vibrate(30); audio.play("hit.wav", 0.6); }
    }
}

function lapCross(c) {
    c.lap++;
    if (c.lap >= 1 && !c.ai) {
        var lapTime = raceTime - lapStart;
        lapStart = raceTime;
        if (bestLap == 0 || lapTime < bestLap) bestLap = lapTime;
        ui.setText("LastLapText", "LAP " + formatTime(lapTime));
        if (c.lap < laps) banner(c.lap == laps - 1 ? "FINAL LAP!" : "LAP " + (c.lap + 1), 1.2);
    }
    if (c.lap >= laps && !c.finished) {
        c.finished = true;
        c.time = raceTime;
        finishOrder.push(c);
        if (!c.ai) playerFinished();
    }
}

function drive(c, thr, steer, boost, brakeHard, dt) {
    var off = Math.abs(c.lat) > W / 2 + 0.6;
    var fx = Math.sin(c.h), fz = Math.cos(c.h);
    var vf0 = c.vx * fx + c.vz * fz;
    var sp = Math.abs(vf0);
    var steerRate = steer * 1.75 * Math.min(1, sp / 5) / (1 + sp / 32);
    c.h += steerRate * dt * (vf0 >= 0 ? 1 : -1);
    fx = Math.sin(c.h); fz = Math.cos(c.h);
    var rx = Math.cos(c.h), rz = -Math.sin(c.h);
    var vf = c.vx * fx + c.vz * fz, vl = c.vx * rx + c.vz * rz;
    var top = c.top * (off ? 0.5 : 1) * (boost ? 1.3 : 1);
    var acc = c.accel * (boost ? 1.8 : 1);
    if (thr > 0) { if (vf < top) vf += thr * acc * (1 - Math.max(0, vf) / top * 0.8) * dt; }
    else if (thr < 0) { if (vf > 0.5) vf += thr * 34 * dt; else if (vf > -11) vf += thr * 9 * dt; }
    else vf -= Math.min(Math.abs(vf), 2.2 * dt) * (vf > 0 ? 1 : -1);
    vf -= vf * (0.012 + (off ? 1.1 : 0)) * dt;
    if (vf > top) vf -= (vf - top) * 1.8 * dt;
    c.drift = brakeHard && sp > 18 && Math.abs(steer) > 0.5;
    var grip = (c.drift ? 2.5 : c.ai ? c.grip : 11) * (off ? 0.55 : 1);
    vl *= Math.exp(-grip * dt);
    c.vx = fx * vf + rx * vl; c.vz = fz * vf + rz * vl;
    c.x += c.vx * dt; c.z += c.vz * dt;
    c.speed = vf;
    c.slip = Math.abs(vl);
}

function aiControl(c, dt) {
    var look = 2 + Math.floor(Math.max(0, c.speed) * 0.12);
    var ti = (c.idx + look) % N, tp = pts[ti], nx = pts[(ti + 1) % N];
    var dx = nx[0] - tp[0], dz = nx[1] - tp[1], L = Math.sqrt(dx * dx + dz * dz) || 1;
    // lane changes to overtake a car just ahead
    c.laneT -= dt;
    for (var i = 0; i < cars.length; i++) {
        var o = cars[i];
        if (o === c) continue;
        var ahead = o.progress - c.progress;
        if (ahead > 0 && ahead < 2.2 && Math.abs(o.lat - c.lat) < 2.6 && c.laneT <= 0) {
            c.lane = o.lat > 0 ? o.lat - 4 : o.lat + 4;
            c.lane = clamp(c.lane, -W / 2 + 1.8, W / 2 - 1.8);
            c.laneT = 1.5;
        }
    }
    var tx = tp[0] + dz / L * c.lane, tz = tp[1] - dx / L * c.lane;
    var err = wrap(Math.atan2(tx - c.x, tz - c.z) - c.h);
    var steer = clamp(err * 2.4, -1, 1);
    var turn = Math.max(turnAhead[c.idx], turnAhead[(c.idx + 2) % N]);
    var aLat = [9, 12, 15][difficulty];
    var safe = clamp(Math.sqrt(aLat * 80 / Math.max(turn, 0.05)), 13, 99);
    var top = c.top;
    if (player && !player.finished) {
        var gap = c.progress - player.progress;
        if (gap < -12) top *= 1.07 + (2 - difficulty) * 0.02;
        else if (gap > 14) top *= 0.93 - (2 - difficulty) * 0.03;
    }
    var target = Math.min(safe, top);
    var thr = c.speed < target - 1 ? 1 : (c.speed > target + 2.5 ? -1 : 0.25);
    var saved = c.top;
    c.top = top;
    drive(c, thr, steer, false, false, dt);
    c.top = saved;
}

function carCollisions() {
    for (var i = 0; i < cars.length; i++) for (var j = i + 1; j < cars.length; j++) {
        var a = cars[i], b = cars[j];
        var dx = b.x - a.x, dz = b.z - a.z, d2 = dx * dx + dz * dz;
        if (d2 > 7.3 || d2 < 1e-6) continue;
        var d = Math.sqrt(d2), nx = dx / d, nz = dz / d, push = (2.7 - d) / 2;
        a.x -= nx * push; a.z -= nz * push; b.x += nx * push; b.z += nz * push;
        var rel = (b.vx - a.vx) * nx + (b.vz - a.vz) * nz;
        if (rel < 0) {
            var imp = -rel * 0.8 / 2;
            a.vx -= nx * imp; a.vz -= nz * imp; b.vx += nx * imp; b.vz += nz * imp;
            if (a === player || b === player) { audio.play("hit.wav", 0.5); if (imp > 3) scene.shake(0.25); }
        }
    }
}

function update(dt) {
    if (dt <= 0) return;
    if (state == "intro" || state == "countdown") {
        state = "countdown";
        countdown -= dt;
        var n = Math.ceil(countdown - 1);
        if (n != lastCount) {
            lastCount = n;
            if (n >= 1 && n <= 3) { ui.setText("CountText", "" + n); audio.play("beep.wav"); }
            else if (n <= 0) { ui.setText("CountText", "GO!"); audio.play("go.wav"); state = "race"; after(1, function () { ui.hide("CountText"); }); }
        }
    }
    var racing = state == "race" || state == "finished";
    if (racing) raceTime += dt;
    for (var i = 0; i < cars.length; i++) {
        var c = cars[i];
        if (!racing) { c.vx = 0; c.vz = 0; c.speed = 0; }
        else if (c.ai || c.finished) {
            if (c.finished) { var sv = c.top; c.top = 22; aiControl(c, dt); c.top = sv; } else aiControl(c, dt);
        } else playerControl(c, dt);
        track(c);
    }
    if (racing) carCollisions();
    for (var k = 0; k < cars.length; k++) {
        var cc = cars[k];
        cc.obj.setPosition(cc.x, 0.05, cc.z);
        cc.obj.rotY = cc.h * 180 / Math.PI;
        cc.obj.rotZ = clamp(-cc.slip * 0.6, -6, 6);
        cc.obj.setModelAnimSpeed(Math.abs(cc.speed) / 12);
        if (cc.dot) { cc.dot.x = (cc.x - mapCx) * mapScale; cc.dot.y = -(cc.z - mapCz) * mapScale; }
    }
    updateCamera(dt);
    hud(dt);
}

function playerControl(c, dt) {
    var gas = input.button("Gas") || input.a || input.axisY > 0.5;
    var brake = input.button("Brake") || input.b || input.axisY < -0.5;
    var steer = clamp(input.axisX + input.stickX("steer"), -1, 1);
    var boost = input.button("Nitro") && nitro > 0.02 && gas;
    if (boost) nitro = Math.max(0, nitro - dt * 0.33); else nitro = Math.min(1, nitro + dt * 0.05);
    var thr = gas ? 1 : (brake ? -1 : 0);
    drive(c, thr, steer, boost, brake && gas, dt);
    if (c.drift || (c.slip > 7 && Math.abs(c.speed) > 15)) {
        skidT -= dt;
        if (skidT <= 0) { skidT = 0.45; audio.play("screech.wav", 0.35); }
    }
    if (boost && !c.boosting) audio.play("whoosh.wav", 0.6);
    c.boosting = boost;
    var fx = scene.find("NitroFx");
    if (fx) fx.setEmitting(boost);
    var trackDir = segAng[c.idx];
    var wrong = Math.abs(wrap(trackDir - c.h)) > 2.1 && c.speed > 3;
    wrongWayT = wrong ? wrongWayT + dt : 0;
    ui.setText("WrongText", wrongWayT > 1 ? "WRONG WAY!" : "");
    if (engineStream >= 0) audio.setPitch(engineStream, 0.6 + Math.min(1.3, Math.abs(c.speed) / 38) + (boost ? 0.15 : 0));
}

function placeCamera(snap) {
    var c = player; if (!c || !cam) return;
    var fx = Math.sin(c.h), fz = Math.cos(c.h);
    var dist = [9, 15, 0.2][camMode], height = [3.6, 7, 1.35][camMode];
    var tx = c.x - fx * dist, ty = height, tz = c.z - fz * dist;
    var k = snap;
    camX += (tx - camX) * k; camY += (ty - camY) * k; camZ += (tz - camZ) * k;
    cam.setPosition(camX, camY, camZ);
    var ahead = camMode == 2 ? 20 : 5;
    cam.lookAt(c.x + fx * ahead, camMode == 2 ? 1.2 : 1.2, c.z + fz * ahead);
}

function updateCamera(dt) { placeCamera(camMode == 2 ? 1 : 1 - Math.exp(-7 * dt)); }

function toggleCam() { camMode = (camMode + 1) % 3; placeCamera(1); }

function hud(dt) {
    if (!player) return;
    var kmh = Math.round(Math.abs(player.speed) * 3.6);
    ui.setText("SpeedText", "" + kmh);
    ui.setProgress("SpeedBar", Math.abs(player.speed) / 60);
    ui.setText("GearText", player.speed < -0.5 ? "R" : "" + Math.max(1, Math.min(6, 1 + Math.floor(Math.abs(player.speed) / 9.5))));
    ui.setProgress("NitroBar", nitro);
    ui.setText("TimeText", formatTime(raceTime));
    ui.setText("LapText", "LAP " + Math.min(laps, Math.max(1, player.lap + 1)) + "/" + laps);
    var pos = position(player);
    ui.setText("PosText", pos + "/" + cars.length);
}

function standings() {
    var list = cars.slice();
    list.sort(function (a, b) {
        if (a.finished && b.finished) return a.time - b.time;
        if (a.finished) return -1;
        if (b.finished) return 1;
        return b.progress - a.progress;
    });
    return list;
}

function position(c) { var s = standings(); for (var i = 0; i < s.length; i++) if (s[i] === c) return i + 1; return s.length; }

function banner(msg, sec) {
    ui.setText("BannerText", msg);
    ui.show("BannerText");
    after(sec, function () { ui.hide("BannerText"); });
}

function playerFinished() {
    state = "finished";
    var pos = position(player);
    banner(pos == 1 ? "YOU WIN!" : "FINISHED P" + pos, 2);
    audio.play(pos == 1 ? "win.wav" : "powerup.wav");
    var best = storage.getNumber("rally_best_" + trackName, 0);
    var record = best == 0 || player.time < best;
    if (record) storage.set("rally_best_" + trackName, player.time);
    var trophies = storage.getNumber("rally_trophies", 0);
    if (pos == 1) storage.set("rally_trophies", trophies + 1);
    after(2.5, function () { showResults(record); });
}

function showResults(record) {
    if (resultsShown) return;
    resultsShown = true;
    var s = standings(), text = "";
    for (var i = 0; i < s.length; i++) {
        var c = s[i];
        var t = c.finished ? formatTime(c.time) : "+" + Math.max(1, Math.round((player.progress - c.progress) / 3)) + "s est.";
        text += (i + 1) + ".  " + c.name + "    " + t + "\n";
    }
    text += "\nBest lap " + formatTime(bestLap) + (record ? "   NEW RECORD!" : "");
    ui.setText("ResultText", text);
    ui.setText("ResultTitle", position(player) == 1 ? "VICTORY!" : "RACE RESULTS");
    ui.show("ResultPanel");
    input.showControls(false);
}

function onStop() { audio.stopMusic(); }
