#!/usr/bin/env python3
"""Recalcula agregados desde muestras anónimas producidas por medir_backend.py."""
import argparse
from collections import Counter
import json
import math
from pathlib import Path


def require(condition, route, field):
    if not condition:
        raise ValueError(f'{route}: agregado incoherente: {field}')


def verify(path):
    with path.open(encoding='utf-8') as file:
        data=json.load(file)
    for stage in data['etapas']:
        total=0
        for route, stats in stage['rutas'].items():
            samples=stats['muestras_ms_estado_valida']
            elapsed=[item[0] for item in samples]
            valid=sorted(ms for ms,status,ok in samples if status=='200' and ok)
            require(len(samples)==stats['solicitudes'], route,'solicitudes')
            require(len(valid)==stats['correctas'], route,'correctas')
            require(dict(Counter(status for _,status,_ in samples))==stats['estados'], route,'estados')
            require(sum(status=='200' and not ok for _,status,ok in samples)==stats['respuestas_200_invalidas'], route,'inválidas')
            for label,fraction in [('p50_ms_correctas',.50),('p95_ms_correctas',.95),('p99_ms_correctas',.99)]:
                expected=valid[math.ceil(fraction*len(valid))-1] if valid else None
                require(stats[label]==expected, route,label)
            require(stats['max_ms_todas']==max(elapsed), route,'máximo')
            require(stats['correctas_bajo_umbral']==sum(ms<stats['umbral_ms'] for ms in valid), route,'umbral')
            total+=len(samples)
        require(total==stage['usuarios']*stage['solicitudes_por_usuario'],stage['usuarios'],'total')
        print(f"{stage['usuarios']} usuarios: {total} muestras y agregados coherentes")


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archivo',type=Path)
    verify(parser.parse_args().archivo)
