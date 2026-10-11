package com.sengine.engine.physics

import com.sengine.engine.math.Aabb3
import com.sengine.engine.math.Vec3
import kotlin.math.max
import kotlin.math.min

/**
 * Dynamic AABB tree (Box2D style): fattened leaf AABBs, surface-area-heuristic insertion, AVL
 * rotations on the way back up and stack-based traversal. Used as the default broadphase.
 */
class DynamicAabbTree {

    private class Node {
        var minX = 0f; var minY = 0f; var minZ = 0f
        var maxX = 0f; var maxY = 0f; var maxZ = 0f
        var parent = -1; var child1 = -1; var child2 = -1
        var height = -1
        var id = -1
        val isLeaf: Boolean get() = child1 == -1
    }

    private val pool = ArrayList<Node>()
    private val free = ArrayDeque<Int>()
    private var root = -1

    /** Number of live nodes. */
    val nodeCount: Int get() = pool.count { it.height >= 0 }

    private fun allocate(): Int {
        if (free.isNotEmpty()) {
            val i = free.removeFirst()
            val n = pool[i]
            n.parent = -1; n.child1 = -1; n.child2 = -1; n.height = 0; n.id = -1
            return i
        }
        pool.add(Node()); return pool.size - 1
    }

    private fun free(i: Int) {
        val n = pool[i]
        n.height = -1; n.id = -1; n.parent = -1; n.child1 = -1; n.child2 = -1
        free.addLast(i)
    }

    private fun area(n: Int): Float {
        val nd = pool[n]
        val dx = nd.maxX - nd.minX; val dy = nd.maxY - nd.minY; val dz = nd.maxZ - nd.minZ
        return 2f * (dx * dy + dy * dz + dz * dx)
    }

    private fun combinedArea(a: Int, b: Int): Float {
        val na = pool[a]; val nb = pool[b]
        val dx = max(na.maxX, nb.maxX) - min(na.minX, nb.minX)
        val dy = max(na.maxY, nb.maxY) - min(na.minY, nb.minY)
        val dz = max(na.maxZ, nb.maxZ) - min(na.minZ, nb.minZ)
        return 2f * (dx * dy + dy * dz + dz * dx)
    }

    private fun combineInto(i: Int, a: Int, b: Int) {
        val n = pool[i]; val na = pool[a]; val nb = pool[b]
        n.minX = min(na.minX, nb.minX); n.minY = min(na.minY, nb.minY); n.minZ = min(na.minZ, nb.minZ)
        n.maxX = max(na.maxX, nb.maxX); n.maxY = max(na.maxY, nb.maxY); n.maxZ = max(na.maxZ, nb.maxZ)
    }

    /** Allocates a leaf node carrying [id] with bounds [aabb] (not inserted yet). */
    fun createLeaf(id: Int, aabb: Aabb3): Int {
        val i = allocate()
        val n = pool[i]
        n.minX = aabb.min.x; n.minY = aabb.min.y; n.minZ = aabb.min.z
        n.maxX = aabb.max.x; n.maxY = aabb.max.y; n.maxZ = aabb.max.z
        n.id = id; n.height = 0
        return i
    }

    fun setAabb(node: Int, aabb: Aabb3) {
        val n = pool[node]
        n.minX = aabb.min.x; n.minY = aabb.min.y; n.minZ = aabb.min.z
        n.maxX = aabb.max.x; n.maxY = aabb.max.y; n.maxZ = aabb.max.z
    }

    fun destroyLeaf(node: Int) { free(node) }

    fun insertLeaf(leaf: Int) {
        if (root == -1) { root = leaf; pool[leaf].parent = -1; return }
        var sibling = root
        while (!pool[sibling].isLeaf) {
            val ar = area(sibling)
            val combined = combinedArea(leaf, sibling)
            val cost = 2f * combined
            val inheritance = 2f * (combined - ar)
            val c1 = pool[sibling].child1; val c2 = pool[sibling].child2
            val cost1 = if (pool[c1].isLeaf) combinedArea(leaf, c1) + inheritance
                        else (combinedArea(leaf, c1) - area(c1)) + inheritance
            val cost2 = if (pool[c2].isLeaf) combinedArea(leaf, c2) + inheritance
                        else (combinedArea(leaf, c2) - area(c2)) + inheritance
            if (cost < cost1 && cost < cost2) break
            sibling = if (cost1 < cost2) c1 else c2
        }
        val oldParent = pool[sibling].parent
        val newParent = allocate()
        pool[newParent].parent = oldParent
        combineInto(newParent, leaf, sibling)
        pool[newParent].height = pool[sibling].height + 1
        if (oldParent != -1) {
            if (pool[oldParent].child1 == sibling) pool[oldParent].child1 = newParent else pool[oldParent].child2 = newParent
        } else {
            root = newParent
        }
        pool[newParent].child1 = sibling; pool[newParent].child2 = leaf
        pool[sibling].parent = newParent; pool[leaf].parent = newParent
        var index = pool[leaf].parent
        while (index != -1) {
            index = balance(index)
            val c1 = pool[index].child1; val c2 = pool[index].child2
            pool[index].height = 1 + max(pool[c1].height, pool[c2].height)
            combineInto(index, c1, c2)
            index = pool[index].parent
        }
    }

    fun removeLeaf(leaf: Int) {
        if (leaf == root) { root = -1; return }
        val parent = pool[leaf].parent
        val grandParent = pool[parent].parent
        val sibling = if (pool[parent].child1 == leaf) pool[parent].child2 else pool[parent].child1
        if (grandParent != -1) {
            if (pool[grandParent].child1 == parent) pool[grandParent].child1 = sibling else pool[grandParent].child2 = sibling
            pool[sibling].parent = grandParent
            free(parent)
            var index = grandParent
            while (index != -1) {
                index = balance(index)
                val c1 = pool[index].child1; val c2 = pool[index].child2
                combineInto(index, c1, c2)
                pool[index].height = 1 + max(pool[c1].height, pool[c2].height)
                index = pool[index].parent
            }
        } else {
            root = sibling
            pool[sibling].parent = -1
            free(parent)
        }
    }

    /** Moves a leaf to a new (tight) AABB: cheap when it stays inside the current fat bounds. */
    fun updateLeaf(node: Int, aabb: Aabb3) {
        val n = pool[node]
        if (aabb.min.x >= n.minX && aabb.min.y >= n.minY && aabb.min.z >= n.minZ &&
            aabb.max.x <= n.maxX && aabb.max.y <= n.maxY && aabb.max.z <= n.maxZ) return
        removeLeaf(node)
        setAabb(node, aabb)
        insertLeaf(node)
    }

    private fun balance(iA: Int): Int {
        val a = pool[iA]
        if (a.isLeaf || a.height < 2) return iA
        val iB = a.child1; val iC = a.child2
        val b = pool[iB]; val c = pool[iC]
        val diff = c.height - b.height
        if (diff > 1) {
            val iF = c.child1; val iG = c.child2
            val f = pool[iF]; val g = pool[iG]
            c.child1 = iA; c.parent = a.parent; a.parent = iC
            if (c.parent != -1) {
                if (pool[c.parent].child1 == iA) pool[c.parent].child1 = iC else pool[c.parent].child2 = iC
            } else root = iC
            if (f.height > g.height) {
                c.child2 = iF; a.child2 = iG; g.parent = iA
                combineInto(iA, iB, iG)
                combineInto(iC, iA, iF)
            } else {
                c.child2 = iG; a.child2 = iF; f.parent = iA
                combineInto(iA, iB, iF)
                combineInto(iC, iA, iG)
            }
            a.height = 1 + max(b.height, pool[a.child2].height)
            c.height = 1 + max(a.height, pool[c.child2].height)
            return iC
        }
        if (diff < -1) {
            val iF = b.child1; val iG = b.child2
            val f = pool[iF]; val g = pool[iG]
            b.child1 = iA; b.parent = a.parent; a.parent = iB
            if (b.parent != -1) {
                if (pool[b.parent].child1 == iA) pool[b.parent].child1 = iB else pool[b.parent].child2 = iB
            } else root = iB
            if (f.height > g.height) {
                b.child2 = iF; a.child1 = iG; g.parent = iA
                combineInto(iA, iG, iC)
                combineInto(iB, iF, iA)
            } else {
                b.child2 = iG; a.child1 = iF; f.parent = iA
                combineInto(iA, iF, iC)
                combineInto(iB, iG, iA)
            }
            a.height = 1 + max(c.height, pool[a.child1].height)
            b.height = 1 + max(a.height, pool[b.child2].height)
            return iB
        }
        return iA
    }


    private val stack = IntArray(256)

    /** Collects the ids of every leaf whose fat AABB overlaps [aabb]. */
    fun query(aabb: Aabb3, out: MutableList<Int>) {
        if (root == -1) return
        var sp = 0
        stack[sp++] = root
        while (sp > 0) {
            val n = pool[stack[--sp]]
            if (n.maxX < aabb.min.x || n.minX > aabb.max.x) continue
            if (n.maxY < aabb.min.y || n.minY > aabb.max.y) continue
            if (n.maxZ < aabb.min.z || n.minZ > aabb.max.z) continue
            if (n.isLeaf) { if (n.id >= 0) out.add(n.id) }
            else if (sp + 2 <= stack.size) { stack[sp++] = n.child1; stack[sp++] = n.child2 }
        }
    }

    /** Collects the ids of every leaf whose fat AABB overlaps the point [p]. */
    fun queryPoint(p: Vec3, out: MutableList<Int>) {
        if (root == -1) return
        var sp = 0
        stack[sp++] = root
        while (sp > 0) {
            val n = pool[stack[--sp]]
            if (p.x < n.minX || p.x > n.maxX || p.y < n.minY || p.y > n.maxY || p.z < n.minZ || p.z > n.maxZ) continue
            if (n.isLeaf) { if (n.id >= 0) out.add(n.id) }
            else if (sp + 2 <= stack.size) { stack[sp++] = n.child1; stack[sp++] = n.child2 }
        }
    }

    /**
     * Emits every overlapping leaf pair once as `(idA, idB)` with `idA < idB` into [out]. Order is
     * not guaranteed; callers sort for determinism.
     */
    fun pairs(out: MutableList<Long>) {
        if (root == -1) return
        for (i in pool.indices) {
            val n = pool[i]
            if (!n.isLeaf || n.id < 0) continue
            collectPairs(n, out)
        }
    }

    private fun collectPairs(leaf: Node, out: MutableList<Long>) {
        var sp = 0
        stack[sp++] = root
        while (sp > 0) {
            val n = pool[stack[--sp]]
            if (n.maxX < leaf.minX || n.minX > leaf.maxX) continue
            if (n.maxY < leaf.minY || n.minY > leaf.maxY) continue
            if (n.maxZ < leaf.minZ || n.minZ > leaf.maxZ) continue
            if (n.isLeaf) {
                if (n.id < 0 || n === leaf) continue
                val a = min(leaf.id, n.id); val b = max(leaf.id, n.id)
                if (a != b) out.add((a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL))
            } else if (sp + 2 <= stack.size) { stack[sp++] = n.child1; stack[sp++] = n.child2 }
        }
    }

    /** Height of the tree (0 for a single leaf, -1 when empty). */
    fun height(): Int = if (root == -1) -1 else pool[root].height

    fun clear() { pool.clear(); free.clear(); root = -1 }
}

/**
 * Sweep-and-prune broadphase variant: proxies kept sorted along the X axis. Cheap for mostly
 * static scenes; the facade keeps the same interface as [DynamicAabbTree]'s users.
 */
class SweepAndPrune3D {

    private class Proxy(val id: Int, val minX: Float, val maxX: Float, val minY: Float, val maxY: Float, val minZ: Float, val maxZ: Float)

    private val proxies = ArrayList<Proxy>()
    private var sorted = false

    fun clear() { proxies.clear(); sorted = true }

    fun add(id: Int, aabb: Aabb3) {
        proxies.add(Proxy(id, aabb.min.x, aabb.max.x, aabb.min.y, aabb.max.y, aabb.min.z, aabb.max.z))
        sorted = false
    }

    fun remove(id: Int) { proxies.removeAll { it.id == id } }

    fun update(id: Int, aabb: Aabb3) {
        for (i in proxies.indices) if (proxies[i].id == id) {
            proxies[i] = Proxy(id, aabb.min.x, aabb.max.x, aabb.min.y, aabb.max.y, aabb.min.z, aabb.max.z)
            sorted = false
            return
        }
        add(id, aabb)
    }

    /** Sorts on the X axis (stable by id) and reports every overlapping pair once. */
    fun pairs(out: MutableList<Long>) {
        if (!sorted) {
            proxies.sortWith(compareBy({ it.minX }, { it.id }))
            sorted = true
        }
        for (i in proxies.indices) {
            val a = proxies[i]
            var j = i + 1
            while (j < proxies.size && proxies[j].minX <= a.maxX) {
                val b = proxies[j]
                if (a.maxY >= b.minY && a.minY <= b.maxY && a.maxZ >= b.minZ && a.minZ <= b.maxZ) {
                    val lo = min(a.id, b.id); val hi = max(a.id, b.id)
                    out.add((lo.toLong() shl 32) or (hi.toLong() and 0xFFFFFFFFL))
                }
                j++
            }
        }
    }

    fun query(aabb: Aabb3, out: MutableList<Int>) {
        for (p in proxies) {
            if (p.maxX >= aabb.min.x && p.minX <= aabb.max.x && p.maxY >= aabb.min.y &&
                p.minY <= aabb.max.y && p.maxZ >= aabb.min.z && p.minZ <= aabb.max.z) out.add(p.id)
        }
    }

    val size: Int get() = proxies.size
}


/** One broadphase pair (`aId < bId`), with a cached "new this frame" flag for contact events. */
class BroadphasePair(val aId: Int, val bId: Int) {
    /** Stable key = `(aId << 32) | bId`. */
    val key: Long = (aId.toLong() shl 32) or (bId.toLong() and 0xFFFFFFFFL)
    /** True when the pair did not exist on the previous [Broadphase3D.computePairs] call. */
    var isNew = false
    var isTrigger = false
    /** Free slot for the owner (the world stores its persistent manifold here). */
    var userData: Any? = null
    override fun toString(): String = "Pair($aId,$bId)"
}

/**
 * Broadphase facade: fattened AABBs, collision-filter rejection, pair caching and a choice of
 * [DynamicAabbTree] (default) or [SweepAndPrune3D]. Infinite shapes (planes) are excluded from the
 * acceleration structure and paired with every filtered body instead.
 */
class Broadphase3D {

    enum class Mode { TREE, SAP }

    var mode: Mode = Mode.TREE
    /** How far the fat AABB grows beyond the tight bounds before a proxy is re-inserted. */
    var fatMargin = 0.05f

    private class Entry(val id: Int, val filter: CollisionFilter, val isInfinite: Boolean) {
        val tight = Aabb3()
        val fat = Aabb3()
        var treeNode = -1
    }

    private val entries = LinkedHashMap<Int, Entry>()
    private val tree = DynamicAabbTree()
    private val sap = SweepAndPrune3D()
    private val rawPairs = ArrayList<Long>()
    private var previousKeys = HashSet<Long>()

    /** Candidate pairs produced by the last [computePairs] call (before narrowphase culling). */
    var candidatePairs = 0; private set
    /** Pairs that survived filtering on the last call. */
    var filteredPairs = 0; private set

    val size: Int get() = entries.size

    fun clear() {
        entries.clear(); tree.clear(); sap.clear(); rawPairs.clear(); previousKeys.clear()
        candidatePairs = 0; filteredPairs = 0
    }

    /** Registers a proxy. [isInfinite] marks planes that must be paired with everything. */
    fun add(id: Int, aabb: Aabb3, filter: CollisionFilter, isInfinite: Boolean) {
        val e = Entry(id, filter.copy(), isInfinite)
        e.tight.set(aabb)
        e.fat.set(aabb).grow(fatMargin)
        entries[id] = e
        if (!isInfinite) {
            if (mode == Mode.TREE) {
                e.treeNode = tree.createLeaf(id, e.fat)
                tree.insertLeaf(e.treeNode)
            } else sap.add(id, e.fat)
        }
    }

    fun remove(id: Int) {
        val e = entries.remove(id) ?: return
        if (!e.isInfinite) {
            if (mode == Mode.TREE) { if (e.treeNode >= 0) { tree.removeLeaf(e.treeNode); tree.destroyLeaf(e.treeNode) } }
            else sap.remove(id)
        }
    }

    /** Refreshes a proxy with new tight bounds; re-inserts only when it leaves the fat AABB. */
    fun update(id: Int, aabb: Aabb3) {
        val e = entries[id] ?: return
        e.tight.set(aabb)
        if (e.isInfinite) return
        e.fat.set(aabb).grow(fatMargin)
        if (mode == Mode.TREE) {
            if (e.treeNode < 0) { e.treeNode = tree.createLeaf(id, e.fat); tree.insertLeaf(e.treeNode) }
            else tree.updateLeaf(e.treeNode, e.fat)
        } else sap.update(id, e.fat)
    }

    fun move(id: Int, aabb: Aabb3) = update(id, aabb)

    fun contains(id: Int): Boolean = entries.containsKey(id)
    fun filterOf(id: Int): CollisionFilter? = entries[id]?.filter
    fun tightAabb(id: Int): Aabb3? = entries[id]?.tight
    fun fatAabb(id: Int): Aabb3? = entries[id]?.fat


    /**
     * Computes every filtered overlapping pair and appends it to [out] in ascending key order.
     * The `isNew` flag is set from the pair cache so the contact layer can emit began/ended events.
     */
    fun computePairs(out: MutableList<BroadphasePair>): Int {
        out.clear()
        rawPairs.clear()
        if (mode == Mode.TREE) tree.pairs(rawPairs) else sap.pairs(rawPairs)
        candidatePairs = rawPairs.size
        val arr = rawPairs.toLongArray()
        java.util.Arrays.sort(arr)
        val newKeys = HashSet<Long>(arr.size * 2)
        var prev = Long.MIN_VALUE
        for (k in arr) {
            if (k == prev) continue
            prev = k
            val aId = (k ushr 32).toInt(); val bId = (k and 0xFFFFFFFFL).toInt()
            val a = entries[aId] ?: continue
            val b = entries[bId] ?: continue
            if (!a.filter.collidesWith(b.filter)) continue
            if (!a.tight.intersects(b.tight)) continue
            addPair(out, newKeys, a, b)
        }
        // infinite proxies pair with everything (subject to the filter)
        for (a in entries.values) {
            if (!a.isInfinite) continue
            for (b in entries.values) {
                if (a.id == b.id) continue
                if (!a.filter.collidesWith(b.filter)) continue
                val lo = min(a.id, b.id); val hi = max(a.id, b.id)
                addPair(out, newKeys, entries[lo]!!, entries[hi]!!)
            }
        }
        filteredPairs = out.size
        previousKeys = newKeys
        return out.size
    }

    private fun addPair(out: MutableList<BroadphasePair>, newKeys: HashSet<Long>, a: Entry, b: Entry) {
        val lo = if (a.id < b.id) a else b
        val hi = if (a.id < b.id) b else a
        val key = (lo.id.toLong() shl 32) or (hi.id.toLong() and 0xFFFFFFFFL)
        if (!newKeys.add(key)) return
        val p = BroadphasePair(lo.id, hi.id)
        p.isNew = !previousKeys.contains(key)
        p.isTrigger = false
        out.add(p)
    }

    /** Collects the ids of proxies whose tight AABB overlaps [aabb] and that pass [filter]. */
    fun query(aabb: Aabb3, filter: CollisionFilter?, out: MutableList<Int>) {
        val cand = ArrayList<Int>()
        if (mode == Mode.TREE) tree.query(aabb, cand) else sap.query(aabb, cand)
        for (e in entries.values) if (e.isInfinite) cand.add(e.id)
        collectFiltered(cand, aabb, filter, out)
    }

    /** Collects the ids of proxies whose tight AABB contains [p] and that pass [filter]. */
    fun queryPoint(p: Vec3, filter: CollisionFilter?, out: MutableList<Int>) {
        val cand = ArrayList<Int>()
        if (mode == Mode.TREE) tree.queryPoint(p, cand) else {
            val a = Aabb3(p.x, p.y, p.z, p.x, p.y, p.z)
            sap.query(a, cand)
        }
        for (e in entries.values) if (e.isInfinite) cand.add(e.id)
        collectFiltered(cand, Aabb3(p.x, p.y, p.z, p.x, p.y, p.z), filter, out)
    }

    private fun collectFiltered(cand: MutableList<Int>, aabb: Aabb3, filter: CollisionFilter?, out: MutableList<Int>) {
        out.clear()
        val seen = HashSet<Int>()
        for (id in cand) {
            val e = entries[id] ?: continue
            if (!seen.add(id)) continue
            if (filter != null && !filter.collidesWith(e.filter)) continue
            if (!e.isInfinite && !e.tight.intersects(aabb)) continue
            out.add(id)
        }
        out.sort()
    }

    /** Number of live proxies and tree height (diagnostics). */
    fun stats(): String = "Broadphase3D(mode=$mode, proxies=${entries.size}, treeHeight=${if (mode == Mode.TREE) tree.height() else -1}, pairs=$filteredPairs)"
}

