#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""B5 门禁（M13-1）：APP 源码白名单扫描 —— PC 专属权限点/API 在 android/app/src/main 零命中。

判据来源：docs/SCOPE-APP.md §八 + MIMO-NEXT-BATCH-05 §1。
模式设计口径（与 B2–B4 手工 grep 验收一致）：
- 权限点/路径形（insp. / notice.create / md. / void / destroy / export / retain …）
  用词边界与上下文约束，**不误伤 Android 平台 API**：
  · `webView.destroy()`（WebView 平台方法）≠ `retain.destroy` / `destroy.init` 权限点；
  · `android:exported`（Manifest 属性）≠ `rpt/export` 导出接口；
  · `ALREADY_VOID` / `voided` / `Voided`（作废状态词，B2 已验收）≠ `ship.void` 权限点。
- 机器码 ALREADY_VOID / voided / Voided 为 APP 既有领域词（作废状态五分类），
  非 PC 撤销入口；PC 撤销权限形为 ship.void.init / void.approve。
命中即失败（退出码 1），并打印 文件:行号:内容。
"""
from __future__ import annotations

import re
import subprocess
import sys

SCAN_ROOT = "android/app/src/main"

# (说明, 编译后的正则) —— 命中任一即失败
PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    # 检测线权限/路径：insp.*（录值/任务/结论/签署…全 PC）
    ("insp.*（检测线）", re.compile(r"insp\.")),
    # 车次预报新建：notice.create
    ("notice.create（预报新建）", re.compile(r"notice\.create")),
    # 主数据：md.*（md.customer / /api/md/ …）；负向后视排除 cmd. 等
    ("md.*（主数据）", re.compile(r"(?<![A-Za-z])md\.")),
    # 出货撤销：ship.void 权限点/路径形（不匹配 ALREADY_VOID / voided 状态词）
    ("ship.void（出货撤销）", re.compile(r"\bvoid\b|ship\.void")),
    # 留样销毁：retain.destroy 权限点 / destroy.init|approve（不匹配平台方法 webView.destroy()）
    ("destroy（留样销毁权限点）", re.compile(r"retain\.destroy|destroy\.(init|approve)")),
    # 让步接收
    ("concession（让步接收）", re.compile(r"\bconcession\b", re.IGNORECASE)),
    # 紧急放行
    ("urgent（紧急放行）", re.compile(r"\burgent\b", re.IGNORECASE)),
    # 检测结论签署
    ("conclusion（结论签署）", re.compile(r"\bconclusion\b", re.IGNORECASE)),
    # 返工
    ("rework（返工）", re.compile(r"\brework\b", re.IGNORECASE)),
    # 报表导出：rpt/export 或独立 export 词（不匹配 android:exported 属性）
    ("export（报表导出）", re.compile(r"\bexport\b|rpt/export")),
    # 留样入库/借还
    ("retain（留样入库/借还）", re.compile(r"\bretain\b")),
]


def main() -> int:
    # --untracked：扫描工作区未跟踪的新文件（git grep 默认只看已跟踪文件，
    # 会让「先跑门禁后 git add」的流程漏检新源码——B5 实测踩坑）。
    r = subprocess.run(
        ["git", "grep", "-n", "-I", "--untracked", "-e", "", "--", SCAN_ROOT],
        capture_output=True,
        text=True,
    )
    if r.returncode not in (0, 1):
        print(f"check_scope: git grep 失败：{r.stderr}", file=sys.stderr)
        return 2

    hits: list[tuple[str, str, str, str]] = []  # (pattern_label, file, line, content)
    for line in r.stdout.splitlines():
        # git grep 输出格式：path:lineno:content
        try:
            path, lineno, content = line.split(":", 2)
        except ValueError:
            continue
        for label, pattern in PATTERNS:
            if pattern.search(content):
                hits.append((label, path, lineno, content.strip()))

    if hits:
        print(f"check_scope: FAIL —— PC 专属词命中 {len(hits)} 处：", file=sys.stderr)
        for label, path, lineno, content in hits:
            print(f"  [{label}] {path}:{lineno}: {content}", file=sys.stderr)
        print(
            "\nSCOPE-APP §八：APP 源码不得出现 PC 专属权限点/API（18 项白名单外零入口）。",
            file=sys.stderr,
        )
        return 1

    print(f"check_scope: OK — {SCAN_ROOT} 下 PC 专属词零命中（{len(PATTERNS)} 组模式）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
