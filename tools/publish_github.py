#!/usr/bin/env python3
"""把本地仓库发布到 GitHub：建仓库 → 推 main/tags → 建 Release 并上传 APK。

用法:
    export GH_TOKEN=ghp_xxx          # 需要 repo 权限（细粒度: Contents 读写 + Administration 读写）
    python3 tools/publish_github.py --repo card-clone --apk dist/nfc-tool.apk --tag v0.2

只推代码、不建 Release：
    python3 tools/publish_github.py --repo card-clone --no-release
"""
import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

API = "https://api.github.com"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def api(method, path, token, data=None, raw=None, ctype="application/json"):
    url = path if path.startswith("http") else API + path
    body = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    if body is not None:
        req.add_header("Content-Type", ctype)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            content = resp.read()
            return resp.status, (json.loads(content) if content else {})
    except urllib.error.HTTPError as e:
        content = e.read()
        try:
            return e.code, json.loads(content)
        except Exception:
            return e.code, {"message": content.decode("utf-8", "replace")}


def git(*args, **kw):
    return subprocess.run(["git", "-C", ROOT] + list(args), check=True, **kw)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--token", default=os.environ.get("GH_TOKEN", ""))
    ap.add_argument("--repo", required=True, help="仓库名，例如 card-clone")
    ap.add_argument("--owner", default="", help="默认是 token 所属账号")
    ap.add_argument("--description", default="Read a MIFARE Classic card, write a copy, verify byte by byte.")
    ap.add_argument("--private", action="store_true")
    ap.add_argument("--tag", default="v0.2")
    ap.add_argument("--apk", default="dist/nfc-tool.apk")
    ap.add_argument("--no-release", action="store_true")
    ap.add_argument("--enable-actions-write", action="store_true",
                    help="把仓库 Actions 默认权限改成读写，让 release.yml 能建 Release")
    args = ap.parse_args()

    if not args.token:
        print("缺少 token：export GH_TOKEN=... 或 --token")
        return 1

    status, me = api("GET", "/user", args.token)
    if status != 200:
        print("token 无效:", status, me.get("message"))
        return 1
    owner = args.owner or me["login"]
    print(f"账号: {owner}")

    full = f"{owner}/{args.repo}"
    status, info = api("GET", f"/repos/{full}", args.token)
    if status == 404:
        print(f"仓库 {full} 不存在，创建中…")
        status, info = api("POST", "/user/repos", args.token, {
            "name": args.repo,
            "description": args.description,
            "private": bool(args.private),
            "has_issues": True,
            "has_wiki": False,
            "auto_init": False,
        })
        if status not in (200, 201):
            print("创建失败:", status, info.get("message"))
            return 1
        print("已创建:", info["html_url"])
    elif status == 200:
        print("仓库已存在:", info["html_url"])
    else:
        print("查询失败:", status, info.get("message"))
        return 1

    if args.enable_actions_write:
        st, r = api("PUT", f"/repos/{full}/actions/permissions/workflow", args.token,
                    {"default_workflow_permissions": "write"})
        print("Actions 权限:", "已设为读写" if st in (200, 204) else f"设置失败 {st} {r.get('message')}")

    # 推送（token 只临时放在 remote URL 里，推完还原）
    auth_url = f"https://x-access-token:{args.token}@github.com/{full}.git"
    plain_url = f"https://github.com/{full}.git"
    git("remote", "remove", "origin", check=False, stderr=subprocess.DEVNULL)
    git("remote", "add", "origin", plain_url)
    git("remote", "set-url", "origin", auth_url)
    try:
        print("推送 main…")
        git("push", "-u", "origin", "main")
        print("推送 tags…")
        git("push", "origin", "--tags")
    finally:
        git("remote", "set-url", "origin", plain_url)

    if args.no_release:
        print("完成（跳过 Release）")
        return 0

    # 确保 tag 存在
    tags = subprocess.run(["git", "-C", ROOT, "tag", "-l", args.tag],
                          capture_output=True, text=True).stdout.strip()
    if not tags:
        git("tag", "-a", args.tag, "-m", f"Card Clone {args.tag}")
        git("push", "origin", args.tag)
        print("已创建并推送 tag", args.tag)

    status, rel = api("GET", f"/repos/{full}/releases/tags/{args.tag}", args.token)
    if status == 200:
        print("Release 已存在:", rel["html_url"])
    else:
        status, rel = api("POST", f"/repos/{full}/releases", args.token, {
            "tag_name": args.tag,
            "name": f"Card Clone {args.tag}",
            "body": "Side-loadable APK (debug-signed). Android 5.0+ with NFC.\n\n"
                    "MIFARE Classic 1K/4K only. Use it only on cards you are authorized to copy.",
            "draft": False,
            "prerelease": False,
        })
        if status not in (200, 201):
            print("建 Release 失败:", status, rel.get("message"))
            return 1
        print("已建 Release:", rel["html_url"])

    apk = os.path.join(ROOT, args.apk)
    if not os.path.exists(apk):
        print("找不到 APK:", apk)
        return 1
    name = f"card-clone-{args.tag}.apk"
    with open(apk, "rb") as f:
        blob = f.read()
    status, asset = api("POST",
                        rel["upload_url"].split("{")[0] + f"?name={name}",
                        args.token, raw=blob, ctype="application/vnd.android.package-archive")
    if status in (200, 201):
        print("已上传:", asset.get("browser_download_url"))
    else:
        print("上传失败:", status, asset.get("message"))
        return 1
    print("完成:", rel["html_url"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
