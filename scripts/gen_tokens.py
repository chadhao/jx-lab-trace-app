#!/usr/bin/env python3
"""从 design/tokens.json 生成 android/.../ui/theme/Tokens.kt。

唯一真相：design/tokens.json。本脚本与 check_tokens.py 配套，
CI 逐字节比对生成物与仓内 Tokens.kt，差异即红。
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOKENS_PATH = ROOT / "design" / "tokens.json"
OUT_PATH = (
    ROOT
    / "android"
    / "app"
    / "src"
    / "main"
    / "java"
    / "cn"
    / "hunanjiangxi"
    / "jxlabtrace"
    / "ui"
    / "theme"
    / "Tokens.kt"
)

HEADER = """\
// GENERATED — DO NOT EDIT
// 来源（唯一真相）：design/tokens.json
// 生成脚本：scripts/gen_tokens.py；一致性校验：scripts/check_tokens.py（CI 必跑）。
// 手改本文件会被 CI 打回。
"""


def camel(key: str) -> str:
    """snake_case → camelCase；已是 camelCase 的原样返回。"""
    parts = re.split(r"[_\-.]", key)
    head = parts[0]
    tail = "".join(p[:1].upper() + p[1:] for p in parts[1:] if p)
    if head and head[0].isdigit():
        return "k" + head[:1].upper() + head[1:] + tail
    return head + tail


def cap(key: str) -> str:
    c = camel(key)
    return c[:1].upper() + c[1:]


def kotlin_color(value: str) -> str:
    """'#RRGGBB' / 'rgba(r,g,b,a)' → Color(0xAARRGGBB)。"""
    if value.startswith("#"):
        hexv = value[1:]
        if len(hexv) == 6:
            return f"Color(0xFF{hexv.upper()})"
        if len(hexv) == 8:
            return f"Color(0x{hexv.upper()})"
        raise ValueError(f"unsupported hex color: {value}")
    m = re.fullmatch(r"rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([0-9.]+)\s*\)", value)
    if not m:
        raise ValueError(f"unsupported color value: {value}")
    r, g, b = int(m.group(1)), int(m.group(2)), int(m.group(3))
    a = round(float(m.group(4)) * 255)
    return f"Color(0x{a:02X}{r:02X}{g:02X}{b:02X})"


def emit_color_object(name: str, node: dict, indent: int, lines: list[str]) -> None:
    pad = "    " * indent
    lines.append(f"{pad}object {name} {{")
    for key, value in node.items():
        if key == "label":
            continue
        if isinstance(value, str):
            lines.append(f"{pad}    val {camel(key)} = {kotlin_color(value)}")
        elif isinstance(value, dict):
            emit_color_object(cap(key), value, indent + 1, lines)
        else:
            raise ValueError(f"unexpected color node {name}.{key}: {value!r}")
    lines.append(f"{pad}}}")


def emit_type_scale(scale: dict, indent: int, lines: list[str]) -> None:
    pad = "    " * indent
    lines.append(f"{pad}object Type {{")
    for key, spec in scale.items():
        lines.append(f"{pad}    object {cap(key)} {{")
        lines.append(f"{pad}        val size = {spec['size']}.sp")
        lines.append(f"{pad}        val lineHeight = {spec['lineHeight']}.sp")
        lines.append(f"{pad}        val weight = {spec['weight']}")
        lines.append(f"{pad}    }}")
    lines.append(f"{pad}}}")


def emit_dp_object(name: str, node: dict, indent: int, lines: list[str], prefix: str = "") -> None:
    pad = "    " * indent
    lines.append(f"{pad}object {name} {{")
    for key, value in node.items():
        if not isinstance(value, (int, float)):
            raise ValueError(f"expected numeric token {name}.{key}: {value!r}")
        key_str = str(key)
        val_name = f"{prefix}{key_str}" if key_str.isdigit() else f"{prefix}{camel(key_str)}"
        lines.append(f"{pad}    val {val_name} = {value}.dp")
    lines.append(f"{pad}}}")


def generate(tokens_path: Path = TOKENS_PATH) -> str:
    data = json.loads(tokens_path.read_text(encoding="utf-8"))
    lines: list[str] = [HEADER]
    lines.append("package cn.hunanjiangxi.jxlabtrace.ui.theme")
    lines.append("")
    lines.append("import androidx.compose.ui.graphics.Color")
    lines.append("import androidx.compose.ui.unit.dp")
    lines.append("import androidx.compose.ui.unit.sp")
    lines.append("")
    lines.append("/** 设计令牌（由 design/tokens.json 生成；颜色/字号/间距/圆角/尺寸）。 */")
    lines.append("object Tokens {")
    emit_color_object("Color", data["color"], 1, lines)
    emit_type_scale(data["typography"]["scale"], 1, lines)
    emit_dp_object("Space", data["space"], 1, lines, prefix="space")
    emit_dp_object("Radius", data["radius"], 1, lines)
    emit_dp_object("Size", data["size"], 1, lines)
    lines.append("}")
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    out = OUT_PATH
    if len(argv) > 1:
        out = Path(argv[1])
    text = generate()
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8", newline="\n")
    print(f"gen_tokens: wrote {out.relative_to(ROOT)} ({len(text)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
