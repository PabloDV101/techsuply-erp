#!/usr/bin/env python3
"""Validate and render all PlantUML sources. Requires Java, Graphviz and a local PlantUML JAR."""
import argparse
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--salida', type=Path, default=Path('/tmp/techsupply-diagramas'))
    parser.add_argument('--formato', choices=['svg', 'png'], default='svg')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    files = sorted(root.rglob('*.puml'))
    base = ['java', '-Djava.awt.headless=true', '-DPLANTUML_LIMIT_SIZE=16384', '-jar', str(args.jar.resolve()), '-charset', 'UTF-8']
    # Fail before rendering if any description is invalid. Local includes remain relative to the source.
    subprocess.run(base + ['-checkonly'] + [str(f) for f in files], check=True)
    output = args.salida.resolve()
    for category in sorted({f.parent for f in files}):
        target = output / category.relative_to(root)
        target.mkdir(parents=True, exist_ok=True)
        sources = [str(f) for f in files if f.parent == category]
        subprocess.run(base + ['-t'+args.formato, '-o', str(target)] + sources, check=True)
    artifacts = list(output.rglob('*.'+args.formato))
    if len(artifacts) != len(files):
        raise RuntimeError(f'Expected {len(files)} artifacts, found {len(artifacts)}; use an empty output directory')
    print(f'PlantUML OK: {len(files)} diagrams validated and rendered to {output}')


if __name__ == '__main__':
    main()
