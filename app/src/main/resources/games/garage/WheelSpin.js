// WheelSpin.js — spins a wheel by the parent car's speed (ZgCar broadcasts "drive").
var spin = 0, steer = 0;

function drive(fwd, steerAngle) {
    spin = fwd;
    steer = steerAngle || 0;
}

function update(dt) {
    self.rotation += spin * 320 * dt;
    // front wheels (negative local Z) also steer
    if (self.z < 0) self.rotY = steer;
}
