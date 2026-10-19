#!/usr/bin/env python3
"""校验仓内 Tokens.kt 与 design/tokens.json 逐字节一致（CI 必跑，差异退出码 1）。"""
from __future__ import annotations

import difflib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from gen_tokens import OUT_PATH, TOKENS_PATH, generate  # noqa: E402


def main() -> int:
    expected = generate(TOKENS_PATH)
    if not OUT_PATH.exists():
        print(f"check_tokens: FAIL — 生成物不存在：{OUT_PATH}", file=sys.stderr)
        return 1
    actual = OUT_PATH.read_text(encoding="utf-8")
    if expected == actual:
        print(f"check_tokens: OK — {OUT_PATH.name} 与 tokens.json 一致（{len(expected)} bytes）")
        return 0

    print("check_tokens: FAIL — Tokens.kt 与 tokens.json 生成结果不一致：", file=sys.stderr)
    diff = difflib.unified_diff(
        actual.splitlines(keepends=True),
        expected.splitlines(keepends=True),
        fromfile="android/.../Tokens.kt (仓内)",
        tofile="gen_tokens.py 重新生成",
    )
    sys.stderr.writelines(list(diff)[:80])
    print("\n请运行：python scripts/gen_tokens.py 后重新提交。", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
