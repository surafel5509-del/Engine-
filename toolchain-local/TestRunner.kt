import org.junit.Assume
import org.junit.Test
import java.io.File
import kotlin.reflect.KFunction

/**
 * Reflective test runner: instantiates each class, runs @Test methods, prints PASS/FAIL/SKIP and a
 * SUMMARY line. Exits 1 when any test failed. Usage: java TestRunner Class1 Class2 ...
 */
object TestRunner {
    @JvmStatic fun main(args: Array<String>) {
        var passed = 0; var failed = 0; var skipped = 0
        val failures = ArrayList<String>()
        for (className in args) {
            val klass = try { Class.forName(className) } catch (e: Throwable) { println("LOAD FAIL $className: $e"); failed++; failures.add(className); continue }
            for (method in klass.declaredMethods.filter { it.isAnnotationPresent(Test::class.java) }) {
                val name = "${klass.simpleName}.${method.name}"
                try {
                    method.isAccessible = true
                    val instance = if (java.lang.reflect.Modifier.isStatic(method.modifiers)) null else klass.getDeclaredConstructor().newInstance()
                    method.invoke(instance)
                    passed++
                    println("PASS $name")
                } catch (t: Throwable) {
                    val cause = if (t is java.lang.reflect.InvocationTargetException) t.cause ?: t else t
                    if (cause is Assume.SkipException) { skipped++; println("SKIP $name (${cause.message})") }
                    else {
                        failed++
                        failures.add(name)
                        println("FAILED: $name -> ${cause.javaClass.simpleName}: ${cause.message}")
                        cause.stackTrace.take(4).forEach { println("    at $it") }
                    }
                }
            }
        }
        println("==== SUMMARY: $passed passed, $failed failed, $skipped skipped ====")
        if (failures.isNotEmpty()) {
            println("Failed tests:")
            failures.forEach { println("  $it") }
            File("/tmp/tc/FAILED").writeText(failures.joinToString("\n"))
        }
        if (failed > 0) kotlin.system.exitProcess(1)
    }
}
