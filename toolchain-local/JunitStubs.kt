package org.junit

/** Minimal JUnit surface used by S Engine's headless JVM tests. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Test

object Assert {
    fun fail(): Unit = throw AssertionError("fail")
    fun fail(msg: String): Unit = throw AssertionError(msg)
    fun assertTrue(v: Boolean) { if (!v) throw AssertionError("expected true") }
    fun assertTrue(msg: String, v: Boolean) { if (!v) throw AssertionError(msg) }
    fun assertFalse(v: Boolean) { if (v) throw AssertionError("expected false") }
    fun assertFalse(msg: String, v: Boolean) { if (v) throw AssertionError(msg) }
    fun assertNull(v: Any?) { if (v != null) throw AssertionError("expected null but was $v") }
    fun assertNull(msg: String, v: Any?) { if (v != null) throw AssertionError(msg) }
    fun assertNotNull(v: Any?) { if (v == null) throw AssertionError("expected non-null") }
    fun assertNotNull(msg: String, v: Any?) { if (v == null) throw AssertionError(msg) }
    fun assertEquals(expected: Int, actual: Int) { if (expected != actual) throw AssertionError("expected: <$expected> but was: <$actual>") }
    fun assertEquals(msg: String, expected: Int, actual: Int) { if (expected != actual) throw AssertionError("$msg: expected: <$expected> but was: <$actual>") }
    fun assertEquals(expected: Long, actual: Long) { if (expected != actual) throw AssertionError("expected: <$expected> but was: <$actual>") }
    fun assertEquals(msg: String, expected: Long, actual: Long) { if (expected != actual) throw AssertionError("$msg: expected: <$expected> but was: <$actual>") }
    fun assertEquals(expected: Boolean, actual: Boolean) { if (expected != actual) throw AssertionError("expected: <$expected> but was: <$actual>") }
    fun assertEquals(msg: String, expected: Boolean, actual: Boolean) { if (expected != actual) throw AssertionError("$msg: expected: <$expected> but was: <$actual>") }
    fun assertEquals(expected: Any?, actual: Any?) { if (expected != actual) throw AssertionError("expected: <$expected> but was: <$actual>") }
    fun assertEquals(msg: String, expected: Any?, actual: Any?) { if (expected != actual) throw AssertionError("$msg: expected: <$expected> but was: <$actual>") }
    fun assertEquals(expected: Double, actual: Double, delta: Double) { if (Math.abs(expected - actual) > delta) throw AssertionError("expected: <$expected> but was: <$actual>") }
    fun assertEquals(msg: String, expected: Double, actual: Double, delta: Double) { if (Math.abs(expected - actual) > delta) throw AssertionError("$msg: expected: <$expected> but was: <$actual>") }
    fun assertEquals(expected: Float, actual: Float, delta: Float) { if (Math.abs(expected - actual) > delta) throw AssertionError("expected: <$expected> but was: <$actual>") }
    fun assertEquals(msg: String, expected: Float, actual: Float, delta: Float) { if (Math.abs(expected - actual) > delta) throw AssertionError("$msg: expected: <$expected> but was: <$actual>") }
    fun assertArrayEquals(expected: IntArray, actual: IntArray) { if (!expected.contentEquals(actual)) throw AssertionError("arrays differ: ${expected.toList()} vs ${actual.toList()}") }
    fun assertArrayEquals(expected: LongArray, actual: LongArray) { if (!expected.contentEquals(actual)) throw AssertionError("arrays differ") }
    fun assertArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float) {
        if (expected.size != actual.size) throw AssertionError("array sizes differ")
        for (i in expected.indices) if (Math.abs(expected[i] - actual[i]) > delta) throw AssertionError("arrays differ at $i")
    }
    fun assertArrayEquals(expected: Array<Any?>, actual: Array<Any?>) { if (!expected.contentEquals(actual)) throw AssertionError("arrays differ") }
    fun assertNotEquals(unexpected: Any?, actual: Any?) { if (unexpected == actual) throw AssertionError("expected not equal to $unexpected") }
    fun assertNotEquals(msg: String, unexpected: Any?, actual: Any?) { if (unexpected == actual) throw AssertionError(msg) }
    fun assertThrows(klass: Class<*>, block: () -> Unit) {
        try { block() } catch (t: Throwable) { if (klass.isInstance(t)) return else throw AssertionError("threw ${t.javaClass.name}") }
        throw AssertionError("expected ${klass.name} to be thrown")
    }
}

object Assume {
    fun assumeTrue(v: Boolean) { if (!v) throw SkipException() }
    fun assumeTrue(msg: String, v: Boolean) { if (!v) throw SkipException() }
    fun assumeFalse(v: Boolean) { if (v) throw SkipException() }
    fun assumeFalse(msg: String, v: Boolean) { if (v) throw SkipException() }
    class SkipException : RuntimeException("skipped by assumption")
}
