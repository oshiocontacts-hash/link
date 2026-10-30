"""TaCZ（Timeless and Classics Zero）の Forge 1.20.1 版の最新リリースを Modrinth から mod/libs に落とす。

GitHub Actions 用。GITHUB_OUTPUT に name / version を書き、Gradle には
-Ptacz_local_name=<name> -Ptacz_local_version=<version> で渡す（mod/libs/<name>-<version>.jar）。
"""
import json
import os
import re
import sys
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2"
HEADERS = {"User-Agent": "neonclash-ci (github actions)"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=60) as r:
        return json.load(r)


def find_project():
    for slug in ("tacz", "timeless-and-classics-zero", "timeless-and-classics-guns-zero"):
        try:
            p = get(f"{API}/project/{slug}")
            print(f"project {slug}: {p['title']} ({p['id']})")
            if "timeless" in p["title"].lower() or "tacz" in p["title"].lower():
                return p
        except Exception as e:  # noqa: BLE001
            print(f"no project {slug}: {e}")
    q = urllib.parse.quote("timeless and classics zero")
    facets = urllib.parse.quote('[["project_type:mod"],["categories:forge"]]')
    hits = get(f"{API}/search?query={q}&facets={facets}")["hits"]
    for h in hits:
        print(f"search hit: {h['slug']} - {h['title']}")
    for h in hits:
        if "timeless" in h["title"].lower():
            return get(f"{API}/project/{h['slug']}")
    sys.exit("TaCZ was not found on Modrinth")


def main():
    project = find_project()
    loaders = urllib.parse.quote('["forge"]')
    games = urllib.parse.quote('["1.20.1"]')
    versions = get(f"{API}/project/{project['id']}/version?loaders={loaders}&game_versions={games}")
    if not versions:
        sys.exit("no Forge 1.20.1 version of TaCZ on Modrinth")
    releases = [v for v in versions if v["version_type"] == "release"] or versions
    v = releases[0]
    f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
    print(f"TaCZ {v['version_number']} ({v['version_type']}, {v['date_published']}): {f['filename']}")
    version = re.sub(r"[^A-Za-z0-9._-]", "_", v["version_number"])
    os.makedirs("mod/libs", exist_ok=True)
    dest = f"mod/libs/tacz-{version}.jar"
    with urllib.request.urlopen(urllib.request.Request(f["url"], headers=HEADERS), timeout=300) as r, open(dest, "wb") as out:
        out.write(r.read())
    print(f"saved {dest} ({os.path.getsize(dest)} bytes)")
    with open(os.environ.get("GITHUB_OUTPUT", os.devnull), "a") as out:
        out.write(f"name=tacz\nversion={version}\nlabel={v['version_number']}\n")


if __name__ == "__main__":
    main()
