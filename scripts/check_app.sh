#!/usr/bin/env bash
# M13-1 门禁聚合脚本：check_tokens + check_colors + check_scope + gradle lint/test/assemble。
# CI（.github/workflows/android.yml）内跑；本地亦可复跑。任一环节非零退出即失败。
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== [1/5] design tokens 一致性 =="
python3 scripts/check_tokens.py

echo "== [2/5] 裸色值扫描 =="
python3 scripts/check_colors.py

echo "== [3/5] 白名单扫描（PC 专属词零命中）=="
python3 scripts/check_scope.py

echo "== [4/5] lintDebug + 单测 + assembleDebug =="
pushd android > /dev/null
./gradlew lintDebug testDebugUnitTest assembleDebug
popd > /dev/null

echo "== [5/5] APK 产物存在 =="
APK="android/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "check_app: FAIL — APK 不存在：$APK" >&2
  exit 1
fi
ls -l "$APK"

echo "check_app: ALL GREEN"
