// Wandering animal that follows the voxel terrain.
var t = 0, dir = 0, walk = 0, speed = 1.2, ready = false;
function update(dt) {
    if (!voxel.ready) return;
    t -= dt;
    if (t <= 0) { t = random(1.5, 4); dir = random(0, Math.PI * 2); walk = chance(0.65) ? 1 : 0; }
    var nx = self.x + Math.sin(dir) * speed * walk * dt, nz = self.z + Math.cos(dir) * speed * walk * dt;
    var sx = voxel.sizeX, sz = voxel.sizeZ;
    if (nx < 1 || nz < 1 || nx > sx - 1 || nz > sz - 1) { dir += Math.PI; return; }
    var gy = voxel.surfaceY(nx, nz);
    var cy = voxel.surfaceY(self.x, self.z);
    if (ready && Math.abs(gy - cy) > 1.1) { dir += Math.PI / 2; return; }
    if (voxel.getBlock(nx, gy - 1, nz) == Block.WATER) { dir += Math.PI; return; }
    self.setPosition(nx, gy + 0.45, nz);
    self.rotY = dir * 180 / Math.PI;
    ready = true;
}
