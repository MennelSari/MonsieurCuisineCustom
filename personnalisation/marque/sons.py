#!/usr/bin/env python3
"""Sons Camybrith Cooking, synthétisés (aucun échantillon externe) : WAV 22 050 Hz, 16 bits, mono.
Usage : sons.py dossier_de_sortie"""
import sys, wave
import numpy as np

SR = 22050

def note(freq, dur, kind='marimba', vol=1.0):
    t = np.arange(int(SR * dur)) / SR
    if kind == 'marimba':      # attaque franche, partiels harmoniques amortis
        y = np.sin(2 * np.pi * freq * t) * np.exp(-t * 6) + 0.35 * np.sin(2 * np.pi * freq * 4 * t) * np.exp(-t * 18)
    elif kind == 'cloche':     # partiels inharmoniques, longue résonance
        y = sum(a * np.sin(2 * np.pi * freq * r * t) * np.exp(-t * d) for r, a, d in
                ((1, 1.0, 2.2), (2.76, 0.45, 3.5), (5.4, 0.22, 6), (8.9, 0.1, 9)))
    elif kind == 'doux':       # sinus feutré
        y = np.sin(2 * np.pi * freq * t) * np.exp(-t * 4) + 0.15 * np.sin(2 * np.pi * freq * 2 * t) * np.exp(-t * 8)
    elif kind == 'alerte':     # carré adouci
        y = np.tanh(3 * np.sin(2 * np.pi * freq * t)) * 0.6
        y *= np.minimum(1, (dur - t) * 30)
    else:
        raise ValueError(kind)
    attack = np.minimum(1, t * 400)
    return y * attack * vol

def mix(parts, total):
    out = np.zeros(int(SR * total))
    for start, sig in parts:
        i = int(SR * start)
        n = min(len(sig), len(out) - i)
        out[i:i + n] += sig[:n]
    return out

def finish(y, peak_db):
    y = y / (np.max(np.abs(y)) or 1) * 10 ** (peak_db / 20)
    fade = int(SR * 0.02)
    y[-fade:] *= np.linspace(1, 0, fade)
    return y

def write(path, y):
    data = (np.clip(y, -1, 1) * 32767).astype('<i2').tobytes()
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(data)

C5, D5, E5, F5, G5, A5, B5 = 523.25, 587.33, 659.25, 698.46, 783.99, 880.0, 987.77
C6, E6, G6 = 1046.5, 1318.5, 1568.0

def main(out):
    sons = {
        # Ouverture : arpège montant sur un accord tenu, signature Camybrith.
        'demarrage': (mix([(0.00, note(C5, 1.2)), (0.12, note(E5, 1.1)), (0.24, note(G5, 1.0)),
                           (0.36, note(C6, 1.2, 'cloche', 0.8)), (0.36, note(C5 / 2, 1.2, 'doux', 0.5))], 1.6), -3),
        # Début de programme : deux notes montantes, brèves.
        'debut': (mix([(0.00, note(G5, 0.3)), (0.10, note(C6, 0.35))], 0.45), -6),
        # Fin de programme : trois coups de cloche, deux fois.
        'fin': (mix([(0.0, note(C6, 0.9, 'cloche')), (0.22, note(E6, 0.9, 'cloche')), (0.44, note(G6, 1.2, 'cloche')),
                     (1.10, note(C6, 0.9, 'cloche', 0.7)), (1.32, note(E6, 0.9, 'cloche', 0.7)), (1.54, note(G6, 1.4, 'cloche', 0.7))], 2.9), -3),
        # Recette terminée : petite fanfare.
        'recette': (mix([(0.00, note(C5, 0.4)), (0.13, note(E5, 0.4)), (0.26, note(G5, 0.4)),
                         (0.39, note(C6, 0.9, 'cloche')), (0.39, note(E5, 0.9, 'doux', 0.6)), (0.39, note(G5, 0.9, 'doux', 0.6))], 1.5), -3),
        # Alerte : deux tons descendants, trois fois.
        'alerte': (mix([(k * 0.36, note(A5, 0.15, 'alerte')) for k in range(3)] +
                       [(k * 0.36 + 0.16, note(E5, 0.15, 'alerte')) for k in range(3)], 1.15), -4),
        # Pause (couvercle ouvert, bol retiré) : une note douce qui descend.
        'pause': (mix([(0.0, note(E5, 0.35, 'doux')), (0.12, note(C5, 0.45, 'doux'))], 0.6), -8),
        # Appui sur un bouton : tic très court et discret.
        'appui': (note(2093.0, 0.04, 'doux') * np.exp(-np.arange(int(SR * 0.04)) / SR * 90), -16),
    }
    for name, (y, db) in sons.items():
        write(f'{out}/{name}.wav', finish(np.asarray(y, dtype=float), db))
        print(name)

if __name__ == '__main__':
    main(sys.argv[1])
