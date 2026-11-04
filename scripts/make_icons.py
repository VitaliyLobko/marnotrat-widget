"""Іконки монет Марнотрату → векторні drawable Android і таблиця CoinIcons.kt.

Джерело — ті самі набори, що в застосунку: marnotrat/js/icons.js (свої) і icons-lucide.js (Lucide);
своя іконка перекриває Lucide з тим самим ім'ям, як ICONS[name] ?? LUCIDE[name] у js/ui.js.
Запуск після зміни іконок у Марнотраті:  py scripts/make_icons.py [шлях до marnotrat]
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parent / 'marnotrat'
RES = ROOT / 'app/src/main/res/drawable'
KT = ROOT / 'app/src/main/java/ua/vitaliylobko/marnotrat/widget/CoinIcons.kt'
PREFIX = 'ic_coin_'
# Товщина лінії, як на монетах головної (css: .coin-face .coin-ico svg { stroke-width: 1.6 })
STROKE = 1.6
ENTRY = re.compile(r"^\s*'?([a-z0-9-]+)'?:\s*'(.*)',?\s*$", re.M)
NUM = re.compile(r'[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?')


def icons(name):
    return dict(ENTRY.findall((SRC / 'js' / name).read_text(encoding='utf-8')))


def fmt(v):
    s = f'{v:.3f}'.rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s


def path_data(d):
    """Шлях SVG з явними розділювачами: у дузі прапорці «01» без пробілу Android може прочитати як одне число."""
    out = []
    i, cmd = 0, None
    args = {'a': 7}
    while i < len(d):
        ch = d[i]
        if ch.isalpha():
            cmd = ch
            out.append(ch)
            i += 1
            continue
        if ch in ' ,\t\n':
            i += 1
            continue
        if cmd and cmd.lower() == 'a':
            # rx ry обертання прапорець прапорець x y — прапорці по одному символу
            vals = []
            while len(vals) < 7 and i < len(d):
                while i < len(d) and d[i] in ' ,\t\n':
                    i += 1
                if len(vals) in (3, 4):
                    vals.append(d[i])
                    i += 1
                else:
                    m = NUM.match(d, i)
                    vals.append(m.group())
                    i = m.end()
            out.append(' '.join(vals))
            continue
        m = NUM.match(d, i)
        if not m:
            raise ValueError(f'не шлях: {d[i:i + 20]!r}')
        out.append(m.group())
        i = m.end()
    return ' '.join(out)


def ellipse(cx, cy, rx, ry):
    return (f'M{fmt(cx - rx)} {fmt(cy)} A{fmt(rx)} {fmt(ry)} 0 1 0 {fmt(cx + rx)} {fmt(cy)} '
            f'A{fmt(rx)} {fmt(ry)} 0 1 0 {fmt(cx - rx)} {fmt(cy)} Z')


def rect(x, y, w, h, rx, ry):
    if not rx and not ry:
        return f'M{fmt(x)} {fmt(y)} H{fmt(x + w)} V{fmt(y + h)} H{fmt(x)} Z'
    rx = min(rx or ry, w / 2)
    ry = min(ry or rx, h / 2)
    a = f'A{fmt(rx)} {fmt(ry)} 0 0 1'
    return (f'M{fmt(x + rx)} {fmt(y)} H{fmt(x + w - rx)} {a} {fmt(x + w)} {fmt(y + ry)} V{fmt(y + h - ry)} '
            f'{a} {fmt(x + w - rx)} {fmt(y + h)} H{fmt(x + rx)} {a} {fmt(x)} {fmt(y + h - ry)} V{fmt(y + ry)} '
            f'{a} {fmt(x + rx)} {fmt(y)} Z')


def element(el):
    f = lambda k, dflt=0.0: float(el.get(k, dflt))
    tag = el.tag
    if tag == 'path':
        return path_data(el.get('d'))
    if tag == 'circle':
        return ellipse(f('cx'), f('cy'), f('r'), f('r'))
    if tag == 'ellipse':
        return ellipse(f('cx'), f('cy'), f('rx'), f('ry'))
    if tag == 'rect':
        rx, ry = el.get('rx'), el.get('ry')
        return rect(f('x'), f('y'), f('width'), f('height'), float(rx or ry or 0), float(ry or rx or 0))
    if tag == 'line':
        return f"M{fmt(f('x1'))} {fmt(f('y1'))} L{fmt(f('x2'))} {fmt(f('y2'))}"
    if tag in ('polyline', 'polygon'):
        pts = [float(x) for x in NUM.findall(el.get('points'))]
        pairs = [f'{fmt(pts[k])} {fmt(pts[k + 1])}' for k in range(0, len(pts), 2)]
        return 'M' + ' L'.join(pairs) + (' Z' if tag == 'polygon' else '')
    raise ValueError(f'невідомий елемент {tag}')


def paths(markup):
    """Елементи іконки пласким списком; атрибути <g> (товщина) переходять до дітей."""
    root = ET.fromstring(f'<svg>{markup}</svg>')
    out = []

    def walk(node, inherited):
        for el in node:
            attrs = {**inherited, **{k: v for k, v in el.attrib.items() if k in ('stroke-width', 'fill')}}
            if el.tag == 'g':
                walk(el, attrs)
            else:
                out.append((element(el), attrs))
    walk(root, {})
    return out


def vector(markup):
    rows = []
    for data, attrs in paths(markup):
        width = float(attrs.get('stroke-width', STROKE))
        fill = '#FFFFFFFF' if attrs.get('fill') == 'currentColor' else '#00000000'
        rows.append(
            f'    <path\n        android:fillColor="{fill}"\n        android:pathData="{data}"\n'
            f'        android:strokeColor="#FFFFFFFF"\n        android:strokeLineCap="round"\n'
            f'        android:strokeLineJoin="round"\n        android:strokeWidth="{fmt(width)}" />')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Згенеровано scripts/make_icons.py з іконок Марнотрату — руками не правити -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="24"\n    android:viewportHeight="24">\n'
            + '\n'.join(rows) + '\n</vector>\n')


def main():
    merged = {**icons('icons-lucide.js'), **icons('icons.js')}
    for old in RES.glob(f'{PREFIX}*.xml'):
        old.unlink()
    names = {}
    for name, markup in sorted(merged.items()):
        res = PREFIX + name.replace('-', '_')
        (RES / f'{res}.xml').write_text(vector(markup), encoding='utf-8', newline='\n')
        names[name] = res
    rows = ',\n'.join(f'        "{n}" to R.drawable.{r}' for n, r in names.items())
    KT.write_text(
        'package ua.vitaliylobko.marnotrat.widget\n\n'
        '// Згенеровано scripts/make_icons.py з іконок Марнотрату — руками не правити.\n'
        '// Посилання прямі (R.drawable…), а не getIdentifier: інакше shrinkResources викинув би іконки зі збірки.\n'
        'object CoinIcons {\n'
        '    private val MAP = mapOf(\n' + rows + ',\n    )\n\n'
        '    /** Іконка монети за назвою з Марнотрату; невідома — монетки, як у застосунку. */\n'
        '    fun of(name: String): Int = MAP[name] ?: MAP.getValue("coins")\n'
        '}\n', encoding='utf-8', newline='\n')
    print(f'{len(names)} іконок → {RES.relative_to(ROOT)}, {KT.name}')


if __name__ == '__main__':
    main()
