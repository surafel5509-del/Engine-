#!/bin/bash
# Full headless suite: android stubs + junit stubs + engine sources + tests → ONE kotlinc call → run.
set -e
TC=/tmp/tc/toolchain
R=/home/user/Engine-
k() { "$TC/jre/bin/java" -Xmx2g -cp "$TC/kotlinc/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@"; }
CPSTD="$TC/kotlinc/kotlin-stdlib.jar:$TC/org-json-20231013.jar:$TC/rhino-1.7.13.jar"
rm -rf /tmp/tc/out-android /tmp/tc/out-all /tmp/tc/FAILED
# 1) android runtime stubs
k -nowarn -jvm-target 17 -cp "$TC/kotlinc/kotlin-stdlib.jar" -d /tmp/tc/out-android $R/toolchain-local/stubs/*.kt 2>&1 | grep -E "^e: |error" | head -20; true
# 2) everything in one invocation (stubs visible to each other + engine)
SRC="$R/toolchain-local/stubs/*.kt $R/toolchain-local/JunitStubs.kt $R/toolchain-local/TestRunner.kt"
for d in engine project agent export; do SRC="$SRC $(find $R/app/src/main/java/com/sengine/$d -name '*.kt')"; done
SRC="$SRC $(find $R/app/src/test/java -name '*.kt' ! -name 'ApkExportTest.kt')"
k -nowarn -jvm-target 17 -cp "$CPSTD" -d /tmp/tc/out-all $SRC 2>&1 | grep -E "^e: |error" | head -60; true
# 3) bundled games on the runtime classpath
mkdir -p /tmp/tc/out-all/games
ln -sf $R/app/src/main/resources/games/* /tmp/tc/out-all/games/ 2>/dev/null || true
# 4) run
"$TC/jre/bin/java" -Xmx1g -cp "/tmp/tc/out-android:/tmp/tc/out-all:$TC/kotlinc/kotlin-stdlib.jar:$TC/org-json-20231013.jar:$TC/rhino-1.7.13.jar" TestRunner \
  com.sengine.EngineAgentTest com.sengine.EngineGamesTest com.sengine.EngineSimulationTest \
  com.sengine.EngineV2Test com.sengine.EngineV3Test com.sengine.EngineV3ToolsTest \
  com.sengine.EngineV4Test com.sengine.EngineV5Test com.sengine.EngineV6Test \
  com.sengine.EngineProTest com.sengine.UltimateProTest
