#!/bin/bash
# Clone the latest toolchain-cache-* branch and prepare /tmp/tc (run once after a sandbox reset).
set -e
R=/home/user/Engine-
cd "$R"
git fetch origin 2>/dev/null || true
BR=$(git ls-remote --heads origin 'toolchain-cache-*' | awk '{print $2}' | sed 's|refs/heads/||' | sort -V | tail -1)
echo "using cache branch: $BR"
rm -rf /tmp/tc
mkdir -p /tmp/tc/toolchain
git clone -q --depth 1 --branch "$BR" https://github.com/surafel5509-del/Engine-.git /tmp/tc/cache-clone
cp -r /tmp/tc/cache-clone/out/* /tmp/tc/toolchain/
rm -rf /tmp/tc/cache-clone
cat > /tmp/tc/toolchain-java <<'W'
#!/bin/bash
exec /tmp/tc/toolchain/jre/bin/java -Xmx2g -cp "/tmp/tc/toolchain/kotlinc/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@"
W
chmod +x /tmp/tc/toolchain-java
/tmp/tc/toolchain-java -version 2>&1 | head -1 || true
echo OK
