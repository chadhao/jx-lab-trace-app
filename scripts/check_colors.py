# -*- coding: utf-8 -*-
"""J4 门禁：android 源码禁裸色值（Tokens.kt 除外）。非空即失败。"""
import subprocess, sys

r = subprocess.run(
    ["git", "grep", "-n", "-E", "#[0-9A-Fa-f]{6}", "--", "android/app/src", "*.kt"],
    capture_output=True, text=True,
)
# 口径：J4 管「Kotlin 源码零裸色值」；XML 品牌资产（logo vector 等）豁免，Tokens.kt 为生成物豁免。
hits = [
    l for l in r.stdout.splitlines()
    if "ui/theme/Tokens.kt" not in l
    and not l.split(":", 1)[0].endswith(".xml")
]
if hits:
    print("裸色值命中 %d 处：" % len(hits))
    print("\n".join(hits[:40]))
    sys.exit(1)
print("check_colors: OK — 无裸色值")
