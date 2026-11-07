#!/usr/bin/env python3
"""对比两份 MIFARE dump（.mfd + .json），用于在电脑上复核复制结果。

用法:
    python3 tools/verify_dump.py 基准.mfd 复制.mfd [--json 基准.json 复制.json]

只给 .mfd 时比对 1024/4096 字节；带 .json 时额外比对 UID、密钥、访问位。
退出码 0 = 完全一致，1 = 有差异。
"""
import argparse
import json
import sys


def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def compare_bytes(a, b):
    n = min(len(a), len(b))
    diffs = [i for i in range(n) if a[i] != b[i]]
    blocks = sorted({i // 16 for i in diffs})
    return diffs, blocks


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("copy")
    ap.add_argument("--json", nargs=2, metavar=("BASE_JSON", "COPY_JSON"))
    args = ap.parse_args()

    a = open(args.base, "rb").read()
    b = open(args.copy, "rb").read()

    ok = True
    print(f"基准: {args.base}  {len(a)} 字节")
    print(f"复制: {args.copy}  {len(b)} 字节")

    if len(a) != len(b):
        print(f"✗ 容量不同: {len(a)} vs {len(b)}")
        ok = False
    else:
        diffs, blocks = compare_bytes(a, b)
        if diffs:
            ok = False
            print(f"✗ {len(diffs)} 字节不同，涉及 {len(blocks)} 个块: {blocks[:10]}")
            for blk in blocks[:5]:
                print(f"  b{blk:02d} base {a[blk*16:blk*16+16].hex(' ').upper()}")
                print(f"  b{blk:02d} copy {b[blk*16:blk*16+16].hex(' ').upper()}")
        else:
            print(f"✓ 数据一致: {len(a)//16} 块 / {len(a)} 字节，0 处不同")

    if args.json:
        ja = load_json(args.json[0])
        jb = load_json(args.json[1])
        for key in ("uid", "sak", "atqa", "sectors"):
            same = ja.get(key) == jb.get(key)
            print(f"{'✓' if same else '✗'} {key:8} {ja.get(key)} | {jb.get(key)}")
            ok = ok and same
        for key in ("keyA", "keyB", "access"):
            same = ja.get(key) == jb.get(key)
            if not same:
                diff = [(i, ja.get(key, [])[i] if i < len(ja.get(key, [])) else None,
                         jb.get(key, [])[i] if i < len(jb.get(key, [])) else None)
                        for i in range(max(len(ja.get(key, [])), len(jb.get(key, []))))
                        if (ja.get(key, [None] * (i + 1))[i:i + 1] != jb.get(key, [None] * (i + 1))[i:i + 1])]
                print(f"✗ {key} 不同: {diff[:4]}")
            else:
                print(f"✓ {key:8} 一致")
            ok = ok and same

    print("结果:", "完全一致" if ok else "存在差异")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
