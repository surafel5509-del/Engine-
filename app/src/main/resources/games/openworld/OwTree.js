// OwTree.js — a choppable tree. Shakes when chopped, respawns after a while.
var hp = 3;

function chop() {
    if (hp <= 0) return 0;
    hp--;
    self.rotation = (Math.random() - 0.5) * 14;
    after(0.15, function () { self.rotation = 0; });
    if (hp == 0) {
        self.active = false;
        after(25, function () { hp = 3; self.active = true; });
    }
    return 1;
}
