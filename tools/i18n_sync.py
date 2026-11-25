#!/usr/bin/env python3
"""Synchronise assets/lang/*.json avec les textes tr("…") du code : retire les textes qui ne sont
plus utilisés et liste ceux qui manquent. Avec --add fichier.json ({"fr": "en"}), ajoute les
traductions anglaises données. Même lecture des textes que I18nTest."""
import json, os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIRS = ["core/src/main/kotlin", "app/src/main/kotlin", "commun/src/main/kotlin", "pilote/src/main/kotlin"]
LANG = os.path.join(ROOT, "app/src/main/assets/lang")
CALL = re.compile(r'\btr(?:Key)?\(\s*')

def literal(src, i):
    assert src[i] == '"'
    j = i + 1; out = []
    while src[j] != '"':
        if src[j] == '\\': out.append(src[j:j+2]); j += 2
        else: out.append(src[j]); j += 1
    return ''.join(out), j + 1

def unescape(s):
    return s.replace('\\"', '"').replace('\\n', '\n').replace('\\$', '$').replace("\\'", "'").replace('\\\\', '\\')

def keys():
    ks = set()
    for d in DIRS:
        for base, _, files in os.walk(os.path.join(ROOT, d)):
            for f in files:
                if not f.endswith('.kt'): continue
                src = open(os.path.join(base, f), encoding='utf-8').read()
                for m in CALL.finditer(src):
                    i = m.end()
                    if src[i] != '"': continue
                    key = ''
                    while True:
                        raw, end = literal(src, i)
                        key += unescape(raw)
                        k = end
                        while src[k].isspace(): k += 1
                        if src[k] == '+':
                            k += 1
                            while src[k].isspace(): k += 1
                            if src[k] == '"': i = k; continue
                        break
                    ks.add(key)
    return ks

ks = keys()
add = json.load(open(sys.argv[2], encoding='utf-8')) if len(sys.argv) > 2 and sys.argv[1] == '--add' else {}
for f in sorted(os.listdir(LANG)):
    p = os.path.join(LANG, f)
    d = json.load(open(p, encoding='utf-8'))
    meta = {k: v for k, v in d.items() if k.startswith('_')}
    texts = {k: v for k, v in d.items() if not k.startswith('_') and k in ks}
    if f == 'en.json': texts.update({k: v for k, v in add.items() if k in ks})
    out = dict(meta); out.update(dict(sorted(texts.items())))
    json.dump(out, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=2); open(p, 'a').write('\n')
    if f == 'en.json':
        missing = sorted(k for k in ks if k not in texts)
        print(json.dumps({k: "" for k in missing}, ensure_ascii=False, indent=1))
