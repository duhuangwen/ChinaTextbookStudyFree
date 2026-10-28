#!/usr/bin/env python3
"""Install only the Web data bundle used by the embedded Android app."""
import argparse
import hashlib
import json
import shutil
import tempfile
import urllib.request
import zipfile
from pathlib import Path, PurePosixPath

RELEASE_REPO = "wuwangzhang1216/ChinaTextbookStudyFree"
DEFAULT_TAG = "v1.2.0-assets"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_target(root: Path, name: str) -> Path:
    relative = PurePosixPath(name.replace("\\", "/"))
    if relative.is_absolute() or ".." in relative.parts or ":" in name:
        raise ValueError(f"Unsafe archive path: {name}")
    target = root.joinpath(*relative.parts).resolve()
    if not target.is_relative_to(root.resolve()):
        raise ValueError(f"Archive path escapes destination: {name}")
    return target


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", default=DEFAULT_TAG)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    base = f"https://github.com/{RELEASE_REPO}/releases/download/{args.tag}"

    with tempfile.TemporaryDirectory(prefix="cstf-web-data-") as tmp:
        temporary = Path(tmp)
        manifest_path = temporary / "manifest.json"
        data_path = temporary / "data.zip"
        print(f"Downloading {args.tag}/manifest.json", flush=True)
        urllib.request.urlretrieve(f"{base}/manifest.json", manifest_path)
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        if manifest.get("schema_version") != 1 or manifest.get("tag") != args.tag:
            raise ValueError("Release manifest version/tag mismatch")
        item = next((asset for asset in manifest["assets"] if asset["name"] == "data.zip"), None)
        if item is None:
            raise ValueError("Release manifest has no data.zip")

        print(f"Downloading {args.tag}/data.zip", flush=True)
        urllib.request.urlretrieve(f"{base}/data.zip", data_path)
        if data_path.stat().st_size != item["bytes"] or sha256(data_path) != item["sha256"]:
            raise ValueError("data.zip size or SHA-256 mismatch")

        destination = args.root / "apps/web/public/data"
        destination.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(data_path) as archive:
            for entry in archive.infolist():
                target = safe_target(destination, entry.filename)
                if entry.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(entry) as source, target.open("wb") as output:
                    shutil.copyfileobj(source, output)

    print(f"Installed Web data into {(args.root / 'apps/web/public/data').resolve()}")


if __name__ == "__main__":
    main()
