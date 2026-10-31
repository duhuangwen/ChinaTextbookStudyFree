#!/usr/bin/env python3
"""Apply scripts/content-errata.json to the prebuilt Web data in apps/web/public/data.

The Android build installs course JSON from a fixed Release bundle, so fixes made in
output/ never reach it. Each erratum replaces one question field when it still holds
the expected wrong text, and drops that field's narration, which reads the old text.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def apply(data_root: Path, errata: list[dict]) -> int:
    applied = 0
    for erratum in errata:
        path = data_root / erratum["file"]
        lesson = json.loads(path.read_text(encoding="utf-8"))
        question = next((q for q in lesson["questions"] if q["id"] == erratum["questionId"]), None)
        if question is None:
            raise ValueError(f"{erratum['file']}: question {erratum['questionId']} not found")
        field = erratum["field"]
        if question.get(field) == erratum["to"]:
            continue
        if question.get(field) != erratum["from"]:
            raise ValueError(f"{erratum['file']}#{erratum['questionId']}.{field} no longer matches the erratum")
        question[field] = erratum["to"]
        question.get("audio", {}).pop(field, None)
        path.write_text(json.dumps(lesson, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        applied += 1
    return applied


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=ROOT / "apps/web/public/data")
    parser.add_argument("--errata", type=Path, default=ROOT / "scripts/content-errata.json")
    args = parser.parse_args()
    errata = json.loads(args.errata.read_text(encoding="utf-8"))
    print(f"Applied {apply(args.data, errata)} of {len(errata)} content errata")


if __name__ == "__main__":
    main()
