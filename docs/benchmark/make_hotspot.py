"""Generate the deterministic 80/20 JMeter input from 100 isolated paths."""

import argparse
import csv
from pathlib import Path
import random
from urllib.parse import urlsplit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture", type=Path, help="100 unique local paths, one per line")
    parser.add_argument("output", type=Path)
    parser.add_argument("--seed", type=int, default=802022)
    args = parser.parse_args()
    paths = [line.strip() for line in args.fixture.read_text(encoding="utf-8-sig").splitlines()
             if line.strip()]
    if len(paths) != 100 or len(set(paths)) != 100:
        parser.error("fixture must contain exactly 100 distinct short-link paths")
    if any(not path.startswith("/") or urlsplit(path).netloc or urlsplit(path).path == "/"
           for path in paths):
        parser.error("only relative short-link paths are allowed")
    weights = [40] * 20 + [3] * 40 + [2] * 40
    rows = [(path, "https://example.com/") for path, weight in zip(paths, weights, strict=True)
            for _ in range(weight)]
    random.Random(args.seed).shuffle(rows)
    with args.output.open("w", encoding="utf-8", newline="") as stream:
        csv.writer(stream, lineterminator="\n").writerows(rows)


if __name__ == "__main__":
    main()
