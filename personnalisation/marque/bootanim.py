"""Animation de démarrage Camybrith Cooking (cocotte et nom), dessinée par marque.py.
Usage : python3 bootanim.py [sortie.zip]"""
import math, os, sys, zipfile, io
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import marque
W, H, FPS = 1280, 800, 15
out = sys.argv[1] if len(sys.argv) > 1 else 'bootanimation.zip'
frames0, frames1 = [], []
for f in range(30):
    pot = marque.ease(f / 9)
    steam = tuple(marque.ease((f - 6 - 3 * i) / 8) for i in range(3))
    words = marque.ease((f - 14) / 10)
    slide = 24 * (1 - words)
    frames0.append(marque.frame(W, H, pot, steam, 0.0, words, slide))
for f in range(30):
    ph = 2 * math.pi * f / 30
    frames1.append(marque.frame(W, H, 1.0, (1, 1, 1), ph, 1.0, 0.0, 0.85 + 0.15 * math.cos(ph)))
with zipfile.ZipFile(out, 'w', zipfile.ZIP_STORED) as z:
    z.writestr('desc.txt', f'{W} {H} {FPS}\np 1 1 part0\np 0 1 part1\n')
    for name, frames in (('part0', frames0), ('part1', frames1)):
        for i, im in enumerate(frames):
            b = io.BytesIO(); im.save(b, 'PNG', optimize=True)
            z.writestr(f'{name}/{i:03d}.png', b.getvalue())
frames1[0].save(os.path.splitext(out)[0] + '_apercu.png')
print(out, os.path.getsize(out), 'octets')
