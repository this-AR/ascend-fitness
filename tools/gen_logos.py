"""20 app-icon concepts (2 per name) in the app's style. viewBox 0 0 100 100, rounded-square icon.
Usage: python gen_logos.py <canvas project dir>  -> Logos.dc.html + logos_preview.svg"""
import math
import os
import sys

A, B = '#FF4D1F', '#FFA03D'
INK = '#0B0B0C'


def defs(i):
    return (f'<linearGradient id="g{i}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{A}"/><stop offset="1" stop-color="{B}"/></linearGradient>'
            f'<linearGradient id="bg{i}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1A1A1E"/><stop offset="1" stop-color="#0B0B0C"/></linearGradient>')


def icon(i, body, bg=None, light=False):
    back = f'url(#g{i})' if light else (bg or f'url(#bg{i})')
    return (f'<svg viewBox="0 0 100 100" width="{{W}}" height="{{W}}"><defs>{defs(i)}</defs>'
            f'<rect width="100" height="100" rx="23" fill="{back}"/>{body.replace("GRAD", f"url(#g{i})")}</svg>')


def pts(p):
    return ' '.join(f'{x:.1f},{y:.1f}' for x, y in p)


L = []
# 1 Ascend: stacked double chevron
L.append(('Ascend', 'Double chevron', icon(1, '<polygon points="22,62 50,36 78,62 78,74 50,48 22,74" fill="GRAD"/>'
                                                '<polygon points="30,44 50,26 70,44 70,53 50,35 30,53" fill="#F4F3F0"/>')))
# 2 Ascend: rising stairs to a dot
L.append(('Ascend', 'Rising steps', icon(2, '<rect x="20" y="62" width="14" height="16" rx="3" fill="GRAD" opacity="0.55"/>'
                                            '<rect x="38" y="48" width="14" height="30" rx="3" fill="GRAD" opacity="0.75"/>'
                                            '<rect x="56" y="34" width="14" height="44" rx="3" fill="GRAD"/>'
                                            '<circle cx="78" cy="24" r="6" fill="#F4F3F0"/>')))
# 3 IronRank: hex badge with three rank chevrons
hexp = [(50 + 34 * math.cos(math.radians(-90 + k * 60)), 50 + 34 * math.sin(math.radians(-90 + k * 60))) for k in range(6)]
L.append(('IronRank', 'Rank hex', icon(3, f'<polygon points="{pts(hexp)}" fill="none" stroke="GRAD" stroke-width="6" stroke-linejoin="round"/>' +
                                       ''.join(f'<polygon points="34,{y + 8} 50,{y} 66,{y + 8} 66,{y + 14} 50,{y + 6} 34,{y + 14}" fill="{c}"/>'
                                               for y, c in [(30, '#F4F3F0'), (42, 'GRAD'), (54, 'GRAD')]))))
# 4 IronRank: dumbbell whose plates form a rising bar chart
L.append(('IronRank', 'Dumbbell chart', icon(4, '<rect x="18" y="47" width="64" height="6" rx="3" fill="#F4F3F0"/>'
                                                '<rect x="20" y="40" width="9" height="20" rx="3" fill="GRAD" opacity="0.6"/>'
                                                '<rect x="31" y="34" width="9" height="32" rx="3" fill="GRAD" opacity="0.8"/>'
                                                '<rect x="60" y="28" width="9" height="44" rx="3" fill="GRAD"/>'
                                                '<rect x="71" y="22" width="9" height="56" rx="3" fill="GRAD"/>')))
# 5 Apex: A-peak with a summit dot
L.append(('Apex', 'Peak A', icon(5, '<polygon points="50,20 82,80 66,80 50,48 34,80 18,80" fill="GRAD"/>'
                                    '<rect x="38" y="62" width="24" height="7" rx="2" fill="#F4F3F0"/><circle cx="50" cy="14" r="4" fill="#F4F3F0"/>', light=False)))
# 6 Apex: light icon, dark summit star over a ridge
L.append(('Apex', 'Summit star', icon(6, '<polygon points="12,82 38,46 50,60 64,38 88,82" fill="#0B0B0C"/>'
                                         '<polygon points="64,14 67,24 77,24 69,30 72,40 64,34 56,40 59,30 51,24 61,24" fill="#0B0B0C"/>', light=True)))
# 7 Overload: barbell with growing plates and an up arrow
L.append(('Overload', 'Growing plates', icon(7, '<rect x="14" y="56" width="72" height="5" rx="2.5" fill="#F4F3F0"/>' +
                                              ''.join(f'<rect x="{x}" y="{58.5 - h / 2}" width="7" height="{h}" rx="2.5" fill="GRAD" opacity="{o}"/>'
                                                      for x, h, o in [(22, 14, 0.5), (32, 22, 0.7), (61, 30, 0.85), (71, 38, 1)]) +
                                              '<polygon points="50,18 62,32 54,32 54,44 46,44 46,32 38,32" fill="GRAD"/>')))
# 8 Overload: gauge needle into the red
arcd = 'M22,66 A30,30 0 1 1 78,66'
L.append(('Overload', 'Max gauge', icon(8, f'<path d="{arcd}" fill="none" stroke="#2A2A2E" stroke-width="9" stroke-linecap="round"/>'
                                           f'<path d="M22,66 A30,30 0 1 1 74,36" fill="none" stroke="GRAD" stroke-width="9" stroke-linecap="round"/>'
                                           '<line x1="50" y1="56" x2="72" y2="34" stroke="#F4F3F0" stroke-width="5" stroke-linecap="round"/>'
                                           '<circle cx="50" cy="56" r="6" fill="#F4F3F0"/>')))
# 9 Forge: anvil with a spark
L.append(('Forge', 'Anvil spark', icon(9, '<path d="M18,40 H64 C72,40 78,36 84,30 V42 C78,48 70,50 64,50 H60 V58 L68,66 V74 H32 V66 L40,58 V50 H26 C21,50 18,46 18,40 Z" fill="GRAD"/>'
                                          '<polygon points="46,14 49,24 59,22 51,29 56,37 47,32 40,38 42,28 34,23 44,23" fill="#F4F3F0"/>')))
# 10 Forge: hammer over a dumbbell with flame
L.append(('Forge', 'Hammer and flame', icon(10, '<path d="M50,16 C60,28 66,36 62,48 C60,56 54,60 50,60 C42,60 36,54 37,46 C38,38 45,36 46,28 C50,34 50,38 49,42 C55,36 54,26 50,16 Z" fill="GRAD"/>'
                                                '<rect x="20" y="66" width="60" height="6" rx="3" fill="#F4F3F0"/><rect x="22" y="60" width="8" height="18" rx="2.5" fill="#F4F3F0"/>'
                                                '<rect x="70" y="60" width="8" height="18" rx="2.5" fill="#F4F3F0"/>')))
# 11 Summit: flag on a mountain
L.append(('Summit', 'Flag peak', icon(11, '<polygon points="10,84 42,38 56,56 66,44 90,84" fill="GRAD"/>'
                                         '<polygon points="42,38 48,46 36,46" fill="#F4F3F0" opacity="0.8"/>'
                                         '<line x1="66" y1="44" x2="66" y2="16" stroke="#F4F3F0" stroke-width="3"/><polygon points="66,16 82,22 66,28" fill="#F4F3F0"/>')))
# 12 Summit: low-poly mountain with a sun
L.append(('Summit', 'Sunrise ridge', icon(12, '<circle cx="64" cy="34" r="14" fill="GRAD"/>'
                                             '<polygon points="10,82 36,44 50,60 50,82" fill="#3A3A40"/><polygon points="36,44 50,60 30,82 10,82" fill="#2A2A2E"/>'
                                             '<polygon points="40,82 66,50 90,82" fill="#F4F3F0"/><polygon points="66,50 90,82 66,82" fill="#C9C9CF"/>')))
# 13 Kilo: weight plate with KG
L.append(('Kilo', 'KG plate', icon(13, '<circle cx="50" cy="50" r="32" fill="GRAD"/><circle cx="50" cy="50" r="22" fill="none" stroke="#0B0B0C" stroke-width="2" opacity="0.4"/>'
                                      '<circle cx="50" cy="50" r="7" fill="#0B0B0C"/>'
                                      '<text x="50" y="35" font-family="Arial, sans-serif" font-size="10" font-weight="bold" fill="#0B0B0C" text-anchor="middle">KG</text>')))
# 14 Kilo: kettlebell with a K cut-out
L.append(('Kilo', 'K kettlebell', icon(14, '<path d="M50,36 C66,36 76,46 76,60 C76,74 66,80 50,80 C34,80 24,74 24,60 C24,46 34,36 50,36 Z M34,38 C30,14 70,14 66,38 L58,36 C60,24 40,24 42,36 Z" fill="GRAD"/>'
                                          '<path d="M42,48 V70 M42,60 L56,48 M46,57 L58,70" stroke="#0B0B0C" stroke-width="5" stroke-linecap="round" fill="none"/>')))
# 15 Tierlift: stacked tiers
L.append(('Tierlift', 'Stacked tiers', icon(15, '<rect x="18" y="64" width="64" height="12" rx="5" fill="#3A3A40"/>'
                                               '<rect x="26" y="48" width="48" height="12" rx="5" fill="#C9C9CF"/>'
                                               '<rect x="34" y="32" width="32" height="12" rx="5" fill="GRAD"/>'
                                               '<polygon points="50,14 54,22 46,22" fill="GRAD"/>')))
# 16 Tierlift: podium with a star
L.append(('Tierlift', 'Podium', icon(16, '<rect x="16" y="58" width="22" height="22" rx="3" fill="#3A3A40"/><rect x="62" y="64" width="22" height="16" rx="3" fill="#2A2A2E"/>'
                                        '<rect x="39" y="46" width="22" height="34" rx="3" fill="GRAD"/>'
                                        '<polygon points="50,18 53,27 62,27 55,32 58,41 50,36 42,41 45,32 38,27 47,27" fill="#F4F3F0"/>')))
# 17 Vertex: geometric V of two facets
L.append(('Vertex', 'Faceted V', icon(17, '<polygon points="18,22 40,22 50,58 50,82" fill="GRAD"/><polygon points="82,22 60,22 50,58 50,82" fill="#F4F3F0"/>'
                                         '<polygon points="40,22 50,58 60,22" fill="#0B0B0C" opacity="0.0"/>')))
# 18 Vertex: wireframe crystal
oc = {'t': (50, 16), 'b': (50, 84), 'l': (22, 50), 'r': (78, 50), 'f': (58, 58), 'k': (42, 42)}
ed = [('t', 'l'), ('t', 'r'), ('t', 'f'), ('t', 'k'), ('b', 'l'), ('b', 'r'), ('b', 'f'), ('b', 'k'), ('l', 'f'), ('f', 'r'), ('r', 'k'), ('k', 'l')]
L.append(('Vertex', 'Wire crystal', icon(18, ''.join(f'<line x1="{oc[a][0]}" y1="{oc[a][1]}" x2="{oc[b][0]}" y2="{oc[b][1]}" stroke="{"GRAD" if "k" not in (a, b) else "#5A5A62"}" stroke-width="3" stroke-linecap="round"/>' for a, b in ed) +
                                             ''.join(f'<circle cx="{x}" cy="{y}" r="3.5" fill="#F4F3F0"/>' for x, y in oc.values()))))
# 19 Ironclimb: dumbbell with climbing chevrons
L.append(('Ironclimb', 'Climb bar', icon(19, '<rect x="44" y="16" width="12" height="68" rx="4" fill="#3A3A40"/>'
                                            '<rect x="30" y="14" width="40" height="9" rx="4" fill="#F4F3F0"/><rect x="30" y="77" width="40" height="9" rx="4" fill="#F4F3F0"/>' +
                                            ''.join(f'<polygon points="36,{y + 8} 50,{y} 64,{y + 8} 64,{y + 13} 50,{y + 5} 36,{y + 13}" fill="GRAD" opacity="{o}"/>'
                                                    for y, o in [(56, 0.55), (44, 0.8), (32, 1)]))))
# 20 Ironclimb: plate with an up arrow cut-out
L.append(('Ironclimb', 'Plate arrow', icon(20, '<circle cx="50" cy="50" r="34" fill="GRAD"/><circle cx="50" cy="50" r="27" fill="none" stroke="#0B0B0C" stroke-width="2" opacity="0.35"/>'
                                           '<polygon points="50,26 66,46 56,46 56,72 44,72 44,46 34,46" fill="#0B0B0C"/>')))

NAMES = [
    ('Ascend', 'Climb the ten ranks; the current working name.'),
    ('IronRank', 'Says exactly what is unique: a rank for every lift.'),
    ('Apex', 'The top of the ladder. Short and punchy.'),
    ('Overload', 'Named after progressive overload, the core of how the app suggests weights.'),
    ('Forge', 'You forge strength, set by set.'),
    ('Summit', 'Calm, aspirational, mountain-themed.'),
    ('Kilo', 'Every kilogram counts; a one-word, numbers-first name.'),
    ('Tierlift', 'Tiers plus lifting: the rank system in one word.'),
    ('Vertex', 'The highest point; techie and geometric like the badges.'),
    ('Ironclimb', 'Lifting iron to climb the ranks.'),
]

if __name__ == '__main__':
    out = sys.argv[1]
    cells = ''.join(f'<g transform="translate({(k % 5) * 120 + 10} {(k // 5) * 120 + 10})">{svg.replace("{W}", "100")}</g>' for k, (_, _, svg) in enumerate(L))
    open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'logos_preview.svg'), 'w', encoding='utf-8').write(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 480" width="600" height="480"><rect width="600" height="480" fill="#202024"/>{cells}</svg>')
    names_html = ''.join(
        f'<div style="background: #131315; border: 1px solid #1F1F23; border-radius: 18px; padding: 14px 16px; display: flex; flex-direction: column; gap: 4px">'
        f'<span style="font-family: \'Bricolage Grotesque\', sans-serif; font-weight: 500; font-size: 24px; letter-spacing: -0.02em">{i + 1}. {n}</span>'
        f'<span style="font-size: 14px; color: #A3A3AA; line-height: 1.5">{d}</span></div>' for i, (n, d) in enumerate(NAMES))
    icons_html = ''.join(
        f'<div style="background: #131315; border: 1px solid #1F1F23; border-radius: 22px; padding: 18px 12px; display: flex; flex-direction: column; align-items: center; gap: 10px">'
        f'{svg.replace("{W}", "120")}'
        f'<span style="font-size: 14px; font-weight: 500">L{k + 1} · {name}</span><span style="font-size: 13px; color: #A3A3AA">{label}</span></div>'
        for k, (name, label, svg) in enumerate(L))
    html = ('<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n<title>App names and icons</title>\n<script src="./support.js"></script>\n</head>\n<body>\n<x-dc>\n'
            '<helmet>\n<link rel="preconnect" href="https://fonts.googleapis.com">\n'
            '<link href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,400..600&amp;family=Geist:wght@400;500&amp;display=swap" rel="stylesheet">\n'
            '<style>\nbody{margin:0}\n</style>\n</helmet>\n'
            '<div style="width: 1360px; min-height: 2100px; box-sizing: border-box; padding: 56px 64px; background: #0B0B0C; color: #F4F3F0; font-family: \'Geist\', sans-serif; display: flex; flex-direction: column; gap: 36px">\n'
            '<div style="display: flex; flex-direction: column; gap: 10px"><h1 style="margin: 0; font-family: \'Bricolage Grotesque\', sans-serif; font-weight: 500; font-size: 48px; letter-spacing: -0.03em">App names and icons</h1>'
            '<p style="margin: 0; font-size: 16px; line-height: 1.6; color: #A3A3AA; max-width: 860px">Ten name ideas, and two icon concepts for each (L1–L20) in the Ember gradient. Any icon can be redrawn in Volt or Tide. Pick a name and an icon number.</p></div>\n'
            f'<div style="display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 14px">{names_html}</div>\n'
            f'<div style="display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 16px">{icons_html}</div>\n'
            '</div>\n</x-dc>\n<script type="text/x-dc" data-dc-script data-props=\'{"$preview":{"width":1360,"height":2100}}\'>\n'
            'class Component extends DCLogic {\n  renderVals() { return {}; }\n}\n</script>\n</body>\n</html>\n')
    open(os.path.join(out, 'Logos.dc.html'), 'w', encoding='utf-8').write(html)
    print('logos written')
