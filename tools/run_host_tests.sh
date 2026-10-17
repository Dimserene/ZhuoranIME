#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
: "${KOTLIN_GRADLE_LIB:?Set KOTLIN_GRADLE_LIB to a Gradle lib directory containing the Kotlin compiler}"
cd "$repo_root"
mkdir -p .cache
mapfile -t engine_files < <(find app/src/main/java/tw/zhuoran/ime/engine -name '*.kt' 2>/dev/null | sort)
mapfile -t input_files < <(find app/src/main/java/tw/zhuoran/ime/input -name '*.kt' ! -name 'PhysicalKeyMapper.kt' 2>/dev/null | sort)
mapfile -t test_files < <(find app/src/test/java -name '*.kt' | sort)
java -cp "$KOTLIN_GRADLE_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$KOTLIN_GRADLE_LIB/kotlin-stdlib-1.9.23.jar:$KOTLIN_GRADLE_LIB/junit-4.13.2.jar" -d .cache/tests.jar \
 app/src/main/java/tw/zhuoran/ime/{Composer,Zhuyin,Candidates,CandidateSelection,PhysicalKeys,TouchLayout}.kt "${engine_files[@]}" "${input_files[@]}" "${test_files[@]}"
mapfile -t classes < <(python3 - <<'PY'
from pathlib import Path
import re
for p in sorted(Path('app/src/test/java').rglob('*.kt')):
 s=p.read_text(); pkg=re.search(r'^package\s+([\w.]+)',s,re.M)
 for name in re.findall(r'^class\s+(\w+Test)\b',s,re.M):
  print((pkg.group(1)+'.' if pkg else '')+name)
PY
)
cd app
java -Djava.library.path="$repo_root/vendor/host" -Dzhuoran.test.dictionary="$repo_root/vendor/dictionary" -cp "$repo_root/.cache/tests.jar:$KOTLIN_GRADLE_LIB/*" org.junit.runner.JUnitCore "${classes[@]}"
