// PRICE OF FREEDOM — patrol / perception AI.
// Guards keep moving through their routes, build sight over time and call the central prison director.
var routePoints = [];
var routeIndex = 0;
var seenFor = 0;
var turn = 0;

function start() {
    routePoints = routeFor(route);
    if (route == "camera") { self.rotation = 180; return; }
    routeIndex = Math.floor(Math.random() * routePoints.length);
}

function routeFor(r) {
    if (r == "yard") return [[-4, 4.2], [4.5, 4.2], [4.5, -2.0], [-4, -2.0]];
    if (r == "kitchen") return [[-15, 2.5], [-7.2, 2.5], [-7.2, -0.5], [-15, -0.5]];
    if (r == "library") return [[7.8, -3.0], [15.5, -3.0], [15.5, -6.8], [7.8, -6.8]];
    if (r == "quarters") return [[-7.8, -9.5], [-1.8, -9.5], [-1.8, -10.8], [-7.8, -10.8]];
    if (r == "gate") return [[-2.0, -10.4], [2.0, -10.4], [2.0, -11.3], [-2.0, -11.3]];
    return [[self.x, self.y]];
}

function update(dt) {
    var game = scene.find("Game");
    if (!game || game.send("isModal")) { self.vx = 0; self.vy = 0; return; }
    if (route == "camera") {
        self.rotation += Math.sin(time.time * 0.9 + self.x) * 11 * dt;
    } else {
        var p = routePoints[routeIndex];
        var dx = p[0] - self.x, dy = p[1] - self.y;
        var d = Math.sqrt(dx * dx + dy * dy);
        if (d < 0.18) { routeIndex = (routeIndex + 1) % routePoints.length; }
        else {
            var speed = id == "GuardSolomon" ? 0.72 : 1.18;
            self.vx = dx / d * speed; self.vy = dy / d * speed;
            self.rotation = Math.atan2(dy, dx) * 180 / Math.PI;
        }
    }
    var player = scene.find("Player");
    if (!player || player.send("isHidden")) { seenFor = Math.max(0, seenFor - dt * 2); return; }
    var ox = player.x - self.x, oy = player.y - self.y;
    var dist = Math.sqrt(ox * ox + oy * oy);
    if (dist < vision) {
        seenFor += dt;
        if (seenFor > 0.5) game.send("raiseAlarm", route == "camera" ? 1.3 : 0.75);
        if (seenFor > 2.2) { player.send("damage", route == "camera" ? 8 : 14); game.send("capture", id); seenFor = 0; }
    } else seenFor = Math.max(0, seenFor - dt * 1.5);
}

function talk() { scene.find("Game").send("talkGuard", id); }
