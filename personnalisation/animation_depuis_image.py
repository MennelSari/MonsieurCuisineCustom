#!/usr/bin/env python3
"""Fabrique une animation de démarrage à partir de TON image (logo, photo...).

    python3 personnalisation/animation_depuis_image.py mon_logo.png [couleur_de_fond]

L'image apparaît en fondu sur le fond, puis reste affichée jusqu'à la fin du démarrage.
Couleur de fond en hexadécimal, par défaut 1b1b1f (presque noir), par exemple ffffff pour du blanc.
Résultat : personnalisation/bootanimation.zip (1280 x 800, l'écran du Smart), à transformer en
module avec  bash personnalisation/fabriquer_module_animation.sh
Il faut Python 3 et Pillow (pip install pillow)."""
import io, os, sys, zipfile
from PIL import Image

W, H, FPS = 1280, 800, 15

def main():
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(1)
    src = sys.argv[1]
    bg = sys.argv[2] if len(sys.argv) > 2 else '1b1b1f'
    bg = tuple(int(bg[i:i + 2], 16) for i in (0, 2, 4))
    logo = Image.open(src).convert('RGBA')
    # L'image tient dans 80 % de la largeur et 70 % de la hauteur, proportions gardées.
    k = min(W * 0.8 / logo.width, H * 0.7 / logo.height, 1.0 if max(logo.size) < 200 else 10)
    logo = logo.resize((max(1, int(logo.width * k)), max(1, int(logo.height * k))), Image.LANCZOS)
    pos = ((W - logo.width) // 2, (H - logo.height) // 2)

    def frame(alpha):
        im = Image.new('RGB', (W, H), bg)
        l = logo.copy()
        l.putalpha(l.getchannel('A').point(lambda v: int(v * alpha)))
        im.paste(l, pos, l)
        return im

    intro = [frame(min(1.0, i / 12)) for i in range(15)]
    fixe = frame(1.0)
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'bootanimation.zip')
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_STORED) as z:
        # part0 joué une fois (fondu), part1 répété jusqu'à la fin du démarrage (image fixe).
        z.writestr('desc.txt', f'{W} {H} {FPS}\np 1 0 part0\np 0 0 part1\n')
        for i, im in enumerate(intro):
            b = io.BytesIO(); im.save(b, 'PNG', optimize=True); z.writestr(f'part0/{i:03d}.png', b.getvalue())
        b = io.BytesIO(); fixe.save(b, 'PNG', optimize=True); z.writestr('part1/000.png', b.getvalue())
    fixe.save(os.path.splitext(out)[0] + '_apercu.png')
    print('Animation prête :', out)
    print('Aperçu :', os.path.splitext(out)[0] + '_apercu.png')
    print('Étape suivante : bash personnalisation/fabriquer_module_animation.sh')

if __name__ == '__main__':
    main()
