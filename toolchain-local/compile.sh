#!/bin/bash
# Compile main engine/project/agent/export sources with the real android.jar → /tmp/tc/out-main
set -e
TC=/tmp/tc/toolchain
R=/home/user/Engine-
rm -rf /tmp/tc/out-main
"$TC/jre/bin/java" -Xmx2g -cp "$TC/kotlinc/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn -jvm-target 17 \
  -cp "$TC/kotlinc/kotlin-stdlib.jar:$TC/android-34.jar:$TC/org-json-20231013.jar:$TC/rhino-1.7.13.jar" \
  -d /tmp/tc/out-main \
  $(find $R/app/src/main/java/com/sengine/engine $R/app/src/main/java/com/sengine/project $R/app/src/main/java/com/sengine/agent $R/app/src/main/java/com/sengine/export -name '*.kt') 2>&1 | grep -E "^e: " | head -60
rc=${PIPESTATUS[0]}
echo "compile exit=$rc"
exit $rc
