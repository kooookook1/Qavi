#!/usr/bin/env bash
# rename.sh — deterministic, re-runnable mechanical rebrand: Mika -> Qavi
# Operates on the sibling src/ tree (pristine extract of Mika v0.1.42).
# Rename ONLY: no logic changes. Excluded from all edits: files named
# LICENSE or NOTICE (any depth), the licenses/ directory, and any path
# mentioning "proot" (a different upstream project; binaries untouched).
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$HERE/src"
cd "$SRC" || { echo "FATAL: no src tree at $SRC"; exit 1; }

EXCLUDE_RE='(^|/)(LICENSE|NOTICE)$|^\./licenses/'

keep() { # stdin: path list -> stdout: paths in scope for content edits
  # PRoot upstream material in this tree = tools/sandbox/patches/ only
  # (verified: those patches contain zero Mika mentions). Do NOT filter on
  # the bare word "proot": the app's own Proot*.kt sources are in scope.
  grep -v -E "$EXCLUDE_RE" | grep -v '^\./tools/sandbox/patches/' || true
}

echo "== Step 2: content replacements (text files only, via grep -rIl) =="
# Collect per-pattern file lists from the CURRENT (pre-edit) tree state.
grep -rIl -e 'com\.wangzan101\.mika' . | keep > /tmp/qavi-list-a.txt || true
grep -rIl -e 'Mika'                  . | keep > /tmp/qavi-list-b.txt || true
grep -rIl -e 'MIKA'                  . | keep > /tmp/qavi-list-c.txt || true
grep -rIl -e 'mika'                  . | keep > /tmp/qavi-list-d.txt || true

apply() { # $1=listfile $2=sed-expr
  [ -s "$1" ] || return 0
  while IFS= read -r f; do
    [ -f "$f" ] && sed -i "$2" "$f"
  done < "$1"
}
apply /tmp/qavi-list-a.txt 's/com\.wangzan101\.mika/app.qavi/g'
apply /tmp/qavi-list-b.txt 's/Mika/Qavi/g'
apply /tmp/qavi-list-c.txt 's/MIKA/QAVI/g'
apply /tmp/qavi-list-d.txt 's/mika/qavi/g'

cat /tmp/qavi-list-a.txt /tmp/qavi-list-b.txt /tmp/qavi-list-c.txt /tmp/qavi-list-d.txt \
  | sort -u > /tmp/qavi-list-all.txt
echo "files content-edited (union): $(wc -l < /tmp/qavi-list-all.txt)"
echo "  pattern a com.wangzan101.mika: $(wc -l < /tmp/qavi-list-a.txt)"
echo "  pattern b Mika:                $(wc -l < /tmp/qavi-list-b.txt)"
echo "  pattern c MIKA:                $(wc -l < /tmp/qavi-list-c.txt)"
echo "  pattern d mika:                $(wc -l < /tmp/qavi-list-d.txt)"

echo "== Step 3: rename files/dirs whose NAME contains mika/Mika/MIKA =="
renamed=0
while IFS= read -r -d '' p; do
  base="$(basename "$p")"
  newbase="$(printf '%s' "$base" | sed -e 's/Mika/Qavi/g' -e 's/MIKA/QAVI/g' -e 's/mika/qavi/g')"
  if [ "$base" != "$newbase" ]; then
    mv "$p" "$(dirname "$p")/$newbase"
    renamed=$((renamed+1))
    echo "  renamed: $p -> $(dirname "$p")/$newbase"
  fi
done < <(find . -depth ! -path './licenses/*' \( -name '*mika*' -o -name '*Mika*' -o -name '*MIKA*' \) -print0)
echo "files/dirs renamed: $renamed"

echo "== Step 4: package dirs com/wangzan101/qavi -> app/qavi =="
moved=0
# Collect first (paths change as we move them).
mapfile -t pkgdirs < <(find . -type d -path '*/com/wangzan101/qavi')
for d in "${pkgdirs[@]}"; do
  root="${d%/com/wangzan101/qavi}"
  mkdir -p "$root/app"
  if [ -e "$root/app/qavi" ]; then
    # Merge (should not happen in this tree, but keep re-runs safe).
    cp -a "$d/." "$root/app/qavi/" && rm -rf "$d"
  else
    mv "$d" "$root/app/qavi"
  fi
  moved=$((moved+1))
  echo "  moved: $d -> $root/app/qavi"
  rmdir "$root/com/wangzan101" "$root/com" 2>/dev/null || true
done
echo "package dirs moved: $moved"
left="$(find . -path '*com/wangzan101*' | wc -l)"
echo "remaining com/wangzan101 path entries: $left"

echo "== Step 5: updater GitHub URLs -> placeholder qavi-app/qavi =="
# After steps 2-4 the old 'wangzan101/Mika' strings read 'wangzan101/Qavi'.
for f in \
  app/src/main/java/app/qavi/about/QaviLinks.kt \
  app/src/test/java/app/qavi/about/QaviLinksTest.kt ; do
  if [ -f "$f" ]; then
    sed -i 's|wangzan101/Qavi|qavi-app/qavi|g' "$f"
    echo "  placeholder applied: $f"
  fi
done
links="app/src/main/java/app/qavi/about/QaviLinks.kt"
if [ -f "$links" ] && ! grep -q 'TODO(qavi)' "$links"; then
  sed -i '/const val REPO =/i\    // TODO(qavi): point at the real release repo before release' "$links"
  echo "  TODO comment added above REPO in $links"
fi

echo "== Step 5b: remaining com/wangzan101 path segments (step-4 mandate) =="
# The step-2 patterns only covered the DOTTED package with the old name.
# Path-form (slash) and regex-escaped references to the moved package were
# left as 'com/wangzan101/qavi' by the mika->qavi pass. Fix them per-file
# (a global slash-form sed would corrupt 'github.com/wangzan101/qavi' URLs
# in strings_chat.xml / ChatCopyTest, which are NOT in this list).
pathfix_files="
app/src/main/baseline-prof.txt
app/src/test/java/app/qavi/ManifestProvidersTest.kt
core/src/test/kotlin/app/qavi/core/action/SingleEntryTest.kt
core/src/test/kotlin/app/qavi/core/capability/PackageDependencyTest.kt
core/src/testFixtures/kotlin/app/qavi/core/docs/ReadmeSections.kt
docs/dev/architecture.md
docs/dev/chatgpt-signin-check.md
openspec/changes/fix-589-pip-sound-toggle/proposal.md
tools/sandbox/README.md
"
for f in $pathfix_files; do
  [ -f "$f" ] && sed -i 's|com/wangzan101/qavi|app/qavi|g' "$f" && echo "  path segments fixed: $f"
done
# Regex-escaped package forms (raw-string literals; file bytes hold the
# single-backslash form com\.wangzan101\.qavi). Done in python: sed
# escaping for this is error-prone.
for f in \
  core/src/test/kotlin/app/qavi/core/capability/PackageDependencyTest.kt \
  core/src/test/kotlin/app/qavi/core/DocsConsistencyTest.kt ; do
  if [ -f "$f" ]; then
    python3 - "$f" <<'PYEOF'
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
s = s.replace(r'com\.wangzan101\.qavi', r'app\.qavi')
open(p, 'w', encoding='utf-8').write(s)
PYEOF
    echo "  regex package fixed: $f"
  fi
done
# Own-package guard in a device test: prefix is now app.qavi.
f="app/src/androidTest/java/app/qavi/cap/phone/a11y/SystemPickersTest.kt"
[ -f "$f" ] && sed -i 's|com\.wangzan101|app.qavi|g' "$f" && echo "  own-package prefix fixed: $f"
# Nonexistent-app fixture: keep it nonexistent, under the new namespace.
f="app/src/androidTest/java/app/qavi/cap/phone/shell/ShellParityDeviceTest.kt"
[ -f "$f" ] && sed -i 's|com\.wangzan101\.no\.such\.app|app.qavi.no.such.app|g' "$f" && echo "  fixture package fixed: $f"
# Segment-form path in the sandbox runtime builder (os.path.join parts).
f="tools/sandbox/build-runtime.py"
[ -f "$f" ] && sed -i 's|"com", "wangzan101", "qavi"|"app", "qavi"|g' "$f" && echo "  segment path fixed: $f"
# ApkIntegrityTest samples encode the updater's official repo: follow step 5.
f="app/src/test/java/app/qavi/about/ApkIntegrityTest.kt"
[ -f "$f" ] && sed -i 's|wangzan101/Qavi|qavi-app/qavi|g' "$f" && echo "  updater test samples fixed: $f"

echo "== README attribution (idempotent: drop prior line, append fresh) =="
sed -i '/based on the open-source/d' README.md
printf '\n## Attribution\n\nQavi is based on the open-source Mika project (https://github.com/wangzan101/Mika) by wangzan101, used under the Apache-2.0 license; see LICENSE and NOTICE.\n' >> README.md
echo "  attribution line ensured in README.md"

echo "== Step 6: new workflow .github/workflows/qavi-apk.yml =="
cat > .github/workflows/qavi-apk.yml <<'YML'
name: Qavi APK

on:
  workflow_dispatch:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  apk:
    name: Build Qavi debug APK
    runs-on: ubuntu-latest
    timeout-minutes: 40
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "17"
      - uses: android-actions/setup-android@v4
        with:
          packages: "platform-tools platforms;android-36 build-tools;35.0.0"
      - uses: gradle/actions/setup-gradle@v6
        with:
          cache-provider: basic
      - name: Unit tests and debug APK
        run: ./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace
      - name: Upload Qavi APK
        uses: actions/upload-artifact@v7
        with:
          name: Qavi-APK
          path: app/build/outputs/apk/debug/*.apk
          if-no-files-found: error
          retention-days: 14
YML
echo "  written: .github/workflows/qavi-apk.yml"

echo "== rename.sh done =="
