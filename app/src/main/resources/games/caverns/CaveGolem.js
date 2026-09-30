// CaveGolem.js — slow, tanky walker. Stomps cause camera shake.
// Params: distance, speed
var homeX, hp = 3, dir = 1, stepT = 0;

function start() { homeX = self.x; }

function update(dt) {
    if (self.x > homeX + distance) { dir = -1; stomp(); }
    if (self.x < homeX - distance) { dir = 1; stomp(); }
    self.vx = dir * speed;
    self.flipX = dir < 0;
    stepT -= dt;
}

function stomp() { scene.shake(0.2); audio.play("hit.wav", 0.4); }

// getting stomped on from above hurts the golem instead of the player
function onTrigger(other) {
    if (other.tag == "Player") {
        var py = scene.find("Player");
        if (py != null && py.y > self.y + 0.4) {
            hp--;
            other.vy = 9; // bounce
            audio.play("hit.wav");
            self.color = "#FFB0B0";
            after(0.15, function () { self.color = "#FF78909C"; });
            scene.find("Player").send("golemHit", hp);
            if (hp <= 0) {
                var fx = scene.spawn("Dust", self.x, self.y);
                if (fx) fx.burst(30);
                self.destroy();
                audio.play("explosion.wav", 0.6);
            }
        }
    }
}
