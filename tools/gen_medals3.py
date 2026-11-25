"""Modern / techie rank badges: geometric silhouettes, neon edges (stacked strokes, no blur filters),
circuit traces, HUD rings, monospace tier tags. Renders the same in browsers and AndroidSVG.
Usage: python gen_medals3.py <canvas project dir> <android assets/medals dir>"""
import math
import os
import sys

CX, CY = 100.0, 112.0
NAMES = ['Rookie', 'Bronze', 'Silver', 'Gold', 'Platinum', 'Diamond', 'Titan', 'Spartan', 'Mythic', 'Galactic demon']


def f(v):
    return f'{v:.1f}'


def lg(i, stops, x1=0, y1=0, x2=0, y2=1):
    s = ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    return f'<linearGradient id="{i}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{s}</linearGradient>'


def rg(i, stops, cx=0.5, cy=0.45, r=0.6):
    s = ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    return f'<radialGradient id="{i}" cx="{cx}" cy="{cy}" r="{r}">{s}</radialGradient>'


def pts(points):
    return ' '.join(f'{f(x)},{f(y)}' for x, y in points)


def ngon(n, r, rot=-90, cx=CX, cy=CY):
    return [(cx + r * math.cos(math.radians(rot + i * 360 / n)), cy + r * math.sin(math.radians(rot + i * 360 / n))) for i in range(n)]


def star_pts(n, r1, r2, rot=-90, cx=CX, cy=CY):
    out = []
    for i in range(2 * n):
        r = r1 if i % 2 == 0 else r2
        a = math.radians(rot + i * 180 / n)
        out.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return out


def arc(r, a0, a1, cx=CX, cy=CY):
    x0, y0 = cx + r * math.cos(math.radians(a0)), cy + r * math.sin(math.radians(a0))
    x1, y1 = cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1))
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return f'M{f(x0)},{f(y0)} A{r},{r} 0 {large} 1 {f(x1)},{f(y1)}'


def glow(shape, color, w=2.0, layers=((10, 0.07), (6, 0.14), (3.2, 0.3))):
    """Neon edge: wide faint strokes under a crisp one. `shape` is an element missing its paint attributes."""
    out = ''.join(shape.replace('/>', f' fill="none" stroke="{color}" stroke-width="{lw}" stroke-opacity="{op}" stroke-linejoin="round" stroke-linecap="round"/>')
                  for lw, op in layers)
    return out + shape.replace('/>', f' fill="none" stroke="{color}" stroke-width="{w}" stroke-linejoin="round" stroke-linecap="round"/>')


def poly_el(points):
    return f'<polygon points="{pts(points)}"/>'


def path_el(d):
    return f'<path d="{d}"/>'


def grid(clip_id, color='#FFFFFF', step=8, op=0.05):
    lines = ''.join(f'<line x1="{x}" y1="0" x2="{x}" y2="230" stroke="{color}" stroke-width="0.5"/>' for x in range(0, 201, step))
    lines += ''.join(f'<line x1="0" y1="{y}" x2="200" y2="{y}" stroke="{color}" stroke-width="0.5"/>' for y in range(0, 231, step))
    return f'<g clip-path="url(#{clip_id})" opacity="{op}">{lines}</g>'


def traces(color, items, op=0.55):
    """Circuit traces: polylines that end in a pad."""
    out = []
    for p in items:
        out.append(f'<polyline points="{pts(p)}" fill="none" stroke="{color}" stroke-width="1.2" stroke-opacity="{op}" stroke-linejoin="round"/>')
        x, y = p[-1]
        out.append(f'<circle cx="{f(x)}" cy="{f(y)}" r="2" fill="none" stroke="{color}" stroke-width="1.1" stroke-opacity="{op}"/>')
    return ''.join(out)


def ticks(r1, r2, n, color, op=0.6, w=1.0, skip=None):
    out = []
    for i in range(n):
        if skip and skip(i):
            continue
        a = math.radians(i * 360 / n - 90)
        out.append(f'<line x1="{f(CX + r1 * math.cos(a))}" y1="{f(CY + r1 * math.sin(a))}" x2="{f(CX + r2 * math.cos(a))}" '
                   f'y2="{f(CY + r2 * math.sin(a))}" stroke="{color}" stroke-width="{w}" stroke-opacity="{op}"/>')
    return ''.join(out)


def tag(n, color, bg='#0B0D10'):
    t = f'T-{n:02d}'
    return (f'<rect x="72" y="200" width="56" height="20" rx="10" fill="{bg}" stroke="{color}" stroke-width="1.4"/>'
            f'<text x="100" y="214.2" font-family="monospace" font-size="11" font-weight="bold" fill="{color}" '
            f'text-anchor="middle" letter-spacing="1">{t}</text>')


def chevrons(k, color_grad, y0=22, gap=17, w=28, h=12, thick=7, yoff=0.0):
    out = []
    for j in range(k):
        y = CY + y0 - j * gap - (k - 1) * gap / 2 + gap * (k - 1) / 2
        y = CY + 14 - j * gap + (k - 1) * gap / 2 - 6 + yoff
        p = [(CX - w, y + h), (CX, y - h), (CX + w, y + h), (CX + w, y + h + thick), (CX, y - h + thick), (CX - w, y + h + thick)]
        out.append(f'<polygon points="{pts(p)}" fill="url(#{color_grad})"/>')
    return ''.join(out)


def plate(outline_pts_or_d, rim, panel, edge, clip_id, inset=None, is_path=False):
    """Dark tech plate: gradient rim + inset dark panel with a faint grid."""
    el = (lambda extra: f'<path d="{outline_pts_or_d}" {extra}/>') if is_path else (lambda extra: f'<polygon points="{pts(outline_pts_or_d)}" {extra}/>')
    s = el('fill="#000" opacity="0.45" transform="translate(0 4)"')
    s += el(f'fill="url(#{rim})"')
    if inset is not None:
        ins = (lambda extra: f'<path d="{inset}" {extra}/>') if is_path else (lambda extra: f'<polygon points="{pts(inset)}" {extra}/>')
        s += f'<clipPath id="{clip_id}">' + ins('') + '</clipPath>'
        s += ins(f'fill="url(#{panel})"')
        s += grid(clip_id)
        s += ins(f'fill="none" stroke="{edge}" stroke-width="1" stroke-opacity="0.6"')
    return s


M = {}

# 01 Rookie: gunmetal hexagon, single chevron, faint traces
hexo, hexi = ngon(6, 80), ngon(6, 66)
M[1] = (lg('r1_rim', [(0, '#B8C2CF'), (0.5, '#5B636E'), (1, '#262B31')]) + rg('r1_pan', [(0, '#1E232A'), (1, '#0A0C0F')]) +
        lg('r1_em', [(0, '#E3E9F0'), (1, '#8A93A0')]),
        plate(hexo, 'r1_rim', 'r1_pan', '#8A93A0', 'c1', hexi) +
        traces('#8A93A0', [[(54, 96), (62, 96), (68, 90)], [(146, 132), (138, 132), (132, 138)]]) +
        chevrons(1, 'r1_em') + glow(poly_el(hexi), '#8A93A0', 1.0, ((4, 0.08),)) + tag(1, '#B8C2CF'))

# 02 Bronze: microchip with pins, two chevrons, copper traces
chip = [(46, 58), (154, 58), (154, 166), (46, 166)]
chip_in = [(56, 68), (144, 68), (144, 156), (56, 156)]
pins = ''.join(f'<rect x="{x}" y="46" width="6" height="12" rx="1.5" fill="url(#b2_pin)"/><rect x="{x}" y="166" width="6" height="12" rx="1.5" fill="url(#b2_pin)"/>'
               for x in range(60, 141, 14))
pins += ''.join(f'<rect x="34" y="{y}" width="12" height="6" rx="1.5" fill="url(#b2_pin)"/><rect x="154" y="{y}" width="12" height="6" rx="1.5" fill="url(#b2_pin)"/>'
                for y in range(72, 153, 14))
M[2] = (lg('b2_rim', [(0, '#FFB27A'), (0.5, '#C0652B'), (1, '#5A260C')]) + lg('b2_pin', [(0, '#FFD2AE'), (1, '#9A4A1A')], 0, 0, 1, 0) +
        rg('b2_pan', [(0, '#24170F'), (1, '#0B0705')]) + lg('b2_em', [(0, '#FFD9B8'), (1, '#E07A38')]),
        pins + plate(chip, 'b2_rim', 'b2_pan', '#E08A4A', 'c2', chip_in) +
        '<circle cx="64" cy="76" r="3" fill="#E08A4A" opacity="0.8"/>' +
        traces('#E08A4A', [[(60, 140), (74, 140), (82, 132)], [(140, 84), (126, 84), (118, 92)], [(140, 140), (128, 140)]]) +
        chevrons(2, 'b2_em') + glow(poly_el(chip_in), '#E08A4A', 1.1, ((5, 0.1),)) + tag(2, '#FFB27A'))

# 03 Silver: angular cut-corner shield, three chevrons, scanlines
sh = [(100, 30), (160, 50), (160, 120), (100, 190), (40, 120), (40, 50)]
sh_in = [(100, 42), (148, 58), (148, 116), (100, 174), (52, 116), (52, 58)]
scan = ''.join(f'<line x1="40" y1="{y}" x2="160" y2="{y}" stroke="#FFFFFF" stroke-width="0.6" opacity="0.08"/>' for y in range(44, 176, 4))
M[3] = (lg('s3_rim', [(0, '#FFFFFF'), (0.45, '#AEB8C4'), (1, '#3E4652')], 0, 0, 1, 1) + rg('s3_pan', [(0, '#202733'), (1, '#0A0D12')]) +
        lg('s3_em', [(0, '#FFFFFF'), (1, '#9AA6B4')]),
        plate(sh, 's3_rim', 's3_pan', '#DCE3EA', 'c3', sh_in) + f'<g clip-path="url(#c3)">{scan}</g>' +
        chevrons(3, 's3_em', gap=15, w=24, h=10, thick=6, yoff=-14) + glow(poly_el(sh_in), '#E6ECF2', 1.2, ((6, 0.08), (3, 0.18))) + tag(3, '#E6ECF2'))

# 04 Gold: HUD octagon with segmented ring and a star
octo, octi = ngon(8, 84, -67.5), ngon(8, 70, -67.5)
segs = ''.join(glow(path_el(arc(62, a + 4, a + 38)), '#FFD447', 2.2, ((6, 0.1),)) for a in range(-90, 270, 45))
M[4] = (lg('g4_rim', [(0, '#FFF1A6'), (0.5, '#D9A21C'), (1, '#6A4708')], 0, 0, 1, 1) + rg('g4_pan', [(0, '#2A220C'), (1, '#0C0904')]) +
        lg('g4_em', [(0, '#FFF6C8'), (1, '#F0B21E')]),
        plate(octo, 'g4_rim', 'g4_pan', '#FFD447', 'c4', octi) + segs +
        f'<polygon points="{pts(star_pts(5, 34, 14))}" fill="url(#g4_em)"/>' +
        glow(poly_el(star_pts(5, 34, 14)), '#FFD447', 1.2, ((7, 0.12), (4, 0.22))) + tag(4, '#FFD447'))

# 05 Platinum: winged hex chip with a delta mark
wing_l = [(58, 86), (8, 64), (26, 92), (2, 102), (30, 116), (14, 132), (58, 128)]
wing_r = [(200 - x, y) for x, y in wing_l]
hx, hxi = ngon(6, 58, -90), ngon(6, 46, -90)
delta = [(100, 82), (126, 132), (74, 132)]
delta_in = [(100, 100), (112, 124), (88, 124)]
M[5] = (lg('p5_rim', [(0, '#F2FFFC'), (0.5, '#8FD3C9'), (1, '#2E5F58')], 0, 0, 1, 1) + rg('p5_pan', [(0, '#132826'), (1, '#060D0C')]) +
        lg('p5_wing', [(0, '#E9FFFB'), (1, '#4FB3A6')], 0, 0, 1, 0) + lg('p5_em', [(0, '#FFFFFF'), (1, '#7FD8CB')]),
        f'<polygon points="{pts(wing_l)}" fill="url(#p5_wing)" opacity="0.9"/><polygon points="{pts(wing_r)}" fill="url(#p5_wing)" opacity="0.9"/>' +
        ''.join(f'<line x1="{a}" y1="{b}" x2="{c}" y2="{d}" stroke="#0A1514" stroke-width="1.2" opacity="0.6"/>'
                for a, b, c, d in [(58, 96, 18, 84), (58, 108, 14, 104), (58, 120, 24, 124), (142, 96, 182, 84), (142, 108, 186, 104), (142, 120, 176, 124)]) +
        plate(hx, 'p5_rim', 'p5_pan', '#C8F5EE', 'c5', hxi) +
        f'<polygon points="{pts(delta)}" fill="url(#p5_em)"/><polygon points="{pts(delta_in)}" fill="#0B1716"/>' +
        glow(poly_el(delta), '#C8F5EE', 1.2, ((7, 0.1), (4, 0.2))) + tag(5, '#C8F5EE'))

# 06 Diamond: crystal inside a scanner ring
gem = ('<polygon points="100,70 132,94 100,154 68,94" fill="#1C6FD1"/>'
       '<polygon points="68,94 84,70 116,70 132,94" fill="#7FE2FF"/>'
       '<polygon points="84,70 100,94 116,70" fill="#D8F7FF"/>'
       '<polygon points="68,94 84,70 100,94" fill="#4CC0F0"/>'
       '<polygon points="132,94 116,70 100,94" fill="#A8EBFF"/>'
       '<polygon points="68,94 100,94 100,154" fill="#2A8EE6"/>'
       '<polygon points="132,94 100,94 100,154" fill="#1557A8"/>')
M[6] = (rg('d6_pan', [(0, '#0E2236'), (1, '#04090F')]) + lg('d6_ring', [(0, '#C9F4FF'), (1, '#1C6FD1')], 0, 0, 1, 1),
        f'<circle cx="{CX}" cy="{CY + 4}" r="84" fill="#000" opacity="0.45"/>'
        f'<circle cx="{CX}" cy="{CY}" r="84" fill="url(#d6_pan)" stroke="url(#d6_ring)" stroke-width="3"/>' +
        ticks(78, 72, 60, '#5BD8FF', 0.5, 1, skip=lambda i: i % 5 == 0) + ticks(80, 68, 12, '#C9F4FF', 0.9, 2) +
        glow(path_el(arc(62, -60, 30)), '#5BD8FF', 2, ((7, 0.1), (4, 0.2))) + glow(path_el(arc(62, 120, 210)), '#5BD8FF', 2, ((7, 0.1), (4, 0.2))) +
        glow(poly_el([(100, 70), (132, 94), (100, 154), (68, 94)]), '#5BD8FF', 1, ((10, 0.08), (6, 0.16))) + gem +
        '<polygon points="100,70 132,94 100,154 68,94 84,70 116,70" fill="none" stroke="#E6FAFF" stroke-width="1.2"/>' +
        '<path d="M146,62 l3,-8 l3,8 l8,3 l-8,3 l-3,8 l-3,-8 l-8,-3 Z" fill="#FFFFFF"/>' + tag(6, '#5BD8FF'))

# 07 Titan: arc reactor with coils and a lightning core
coils = ''.join(
    f'<polygon points="{pts([(CX + 58 * math.cos(math.radians(a - 12)), CY + 58 * math.sin(math.radians(a - 12))), (CX + 78 * math.cos(math.radians(a - 9)), CY + 78 * math.sin(math.radians(a - 9))), (CX + 78 * math.cos(math.radians(a + 9)), CY + 78 * math.sin(math.radians(a + 9))), (CX + 58 * math.cos(math.radians(a + 12)), CY + 58 * math.sin(math.radians(a + 12)))])}" '
    f'fill="url(#t7_coil)" stroke="#2A1004" stroke-width="1"/>' for a in range(-90, 270, 36))
bolt = [(106, 74), (84, 116), (98, 116), (90, 152), (118, 104), (104, 104)]
M[7] = (rg('t7_pan', [(0, '#3A1A08'), (1, '#0B0502')]) + lg('t7_coil', [(0, '#FFD08A'), (1, '#C2370A')]) + rg('t7_core', [(0, '#FFF3D6'), (0.5, '#FFB04A'), (1, '#C2370A')]),
        f'<circle cx="{CX}" cy="{CY + 4}" r="88" fill="#000" opacity="0.45"/>'
        f'<circle cx="{CX}" cy="{CY}" r="88" fill="url(#t7_pan)" stroke="#FF8A2B" stroke-width="2"/>' +
        ticks(86, 82, 72, '#FF8A2B', 0.5) + coils +
        glow(f'<circle cx="{CX}" cy="{CY}" r="50"/>', '#FF8A2B', 2, ((10, 0.08), (6, 0.16), (3, 0.3))) +
        f'<circle cx="{CX}" cy="{CY}" r="40" fill="url(#t7_core)" opacity="0.35"/>' +
        f'<polygon points="{pts(bolt)}" fill="#FFF3D6"/>' + glow(poly_el(bolt), '#FFB04A', 1.4, ((8, 0.12), (4, 0.25))) + tag(7, '#FF8A2B'))

# 08 Spartan: visor shield with HUD brackets and a helmet
vs = [(60, 26), (140, 26), (164, 50), (164, 120), (100, 192), (36, 120), (36, 50)]
vs_in = [(64, 38), (136, 38), (152, 54), (152, 116), (100, 176), (48, 116), (48, 54)]
HELMET = ('M0,-34 C22,-34 34,-18 34,4 V28 C34,34 28,38 22,36 L12,32 L10,16 H6 V34 H-6 V16 H-10 L-12,32 L-22,36 '
          'C-28,38 -34,34 -34,28 V4 C-34,-18 -22,-34 0,-34 Z M-26,4 C-18,-2 -8,-1 -4,4 V12 H4 V4 C8,-1 18,-2 26,4 L25,10 '
          'C18,6 10,7 8,11 V30 H-8 V11 C-10,7 -18,6 -25,10 Z')
CREST = 'M-44,-26 C-38,-66 38,-66 44,-26 C32,-46 -32,-46 -44,-26 Z'
brackets = ''.join(f'<polyline points="{p}" fill="none" stroke="#FF3B47" stroke-width="2" stroke-opacity="0.8"/>'
                   for p in ['58,64 58,56 66,56', '142,64 142,56 134,56', '58,150 58,158 66,158', '142,150 142,158 134,158'])
M[8] = (lg('s8_rim', [(0, '#FFB0B5'), (0.5, '#C51F2C'), (1, '#4A060C')], 0, 0, 1, 1) + rg('s8_pan', [(0, '#2A0A0E'), (1, '#0A0204')]) +
        lg('s8_em', [(0, '#F4F6F9'), (0.55, '#B8C0CA'), (1, '#5A6270')]) + lg('s8_crest', [(0, '#FF6A74'), (1, '#8A0F1C')]),
        plate(vs, 's8_rim', 's8_pan', '#FF3B47', 'c8', vs_in) + brackets +
        f'<g transform="translate({CX} {CY + 12}) scale(0.95)"><path d="{CREST}" fill="url(#s8_crest)"/>'
        + ''.join(f'<line x1="{x}" y1="-44" x2="{x * 1.15:.1f}" y2="-56" stroke="#4A060C" stroke-width="1.2" opacity="0.6"/>' for x in range(-30, 31, 6)) +
        f'<path d="{HELMET}" fill="url(#s8_em)" fill-rule="evenodd"/></g>' +
        f'<g transform="translate({CX} {CY + 12}) scale(0.95)">' + glow(f'<path d="{HELMET}"/>', '#FF3B47', 1.0, ((6, 0.1), (3, 0.22))) +
        glow(f'<path d="{CREST}"/>', '#FF3B47', 1.0, ((5, 0.12),)) + '</g>' + tag(8, '#FF5A64'))

# 09 Mythic: orbit rings and a low-poly phoenix
wingL = [[(100, 96), (60, 70), (70, 100)], [(100, 96), (40, 86), (66, 112)], [(100, 102), (50, 116), (76, 122)]]
wingR = [[(200 - x, y) for x, y in t] for t in wingL]
body = [(100, 74), (108, 96), (100, 140), (92, 96)]
tail = [[(100, 136), (88, 160), (100, 150)], [(100, 136), (112, 160), (100, 150)], [(100, 140), (100, 168), (96, 150)]]
tri = ''.join(f'<polygon points="{pts(t)}" fill="url(#m9_wing)" stroke="#0B2A20" stroke-width="0.8"/>' for t in wingL + wingR)
tri += f'<polygon points="{pts(body)}" fill="url(#m9_gold)"/>' + ''.join(f'<polygon points="{pts(t)}" fill="url(#m9_gold)" opacity="0.9"/>' for t in tail)
M[9] = (rg('m9_pan', [(0, '#0C2A21'), (1, '#030A08')]) + lg('m9_wing', [(0, '#B8FFE2'), (1, '#0E8F6A')], 0, 0, 1, 1) +
        lg('m9_gold', [(0, '#FFF6C8'), (1, '#F0B21E')]),
        f'<circle cx="{CX}" cy="{CY + 4}" r="84" fill="#000" opacity="0.45"/>'
        f'<circle cx="{CX}" cy="{CY}" r="84" fill="url(#m9_pan)" stroke="#3DF0A6" stroke-width="2"/>' +
        glow(f'<ellipse cx="{CX}" cy="{CY}" rx="76" ry="26" transform="rotate(-24 {CX} {CY})"/>', '#3DF0A6', 1.4, ((5, 0.1),)) +
        glow(f'<ellipse cx="{CX}" cy="{CY}" rx="76" ry="26" transform="rotate(24 {CX} {CY})"/>', '#FFD447', 1.4, ((5, 0.1),)) +
        f'<circle cx="{f(CX + 76 * math.cos(math.radians(-24)) * 0.7)}" cy="{f(CY - 30)}" r="3" fill="#FFD447"/>'
        f'<circle cx="52" cy="140" r="2.6" fill="#3DF0A6"/>' +
        tri + glow(poly_el(body), '#FFD447', 0.8, ((6, 0.12),)) + tag(9, '#3DF0A6'))

# 10 Galactic demon: black hole with accretion disk, neon horns, spiked ring, glitch bars
spikes = star_pts(16, 96, 82)
hornL = [(78, 70), (60, 46), (54, 18), (70, 40), (88, 62)]
hornR = [(200 - x, y) for x, y in hornL]
M[10] = (rg('x_pan', [(0, '#1A0610'), (1, '#030102')]) + lg('x_disk', [(0, '#FFE27A'), (0.4, '#FF9A2E'), (0.75, '#FF2D55'), (1, '#5A0018')], 0, 0, 1, 0) +
         lg('x_rim', [(0, '#FF9A2E'), (1, '#FF2D55')], 0, 0, 1, 1) + lg('x_horn', [(0, '#FFE27A'), (1, '#FF2D55')], 0, 1, 0, 0),
         f'<polygon points="{pts(spikes)}" fill="#000" opacity="0.5" transform="translate(0 4)"/>'
         f'<polygon points="{pts(spikes)}" fill="url(#x_rim)" stroke="#2A0008" stroke-width="1"/>'
         f'<circle cx="{CX}" cy="{CY}" r="80" fill="url(#x_pan)" stroke="#FF2D55" stroke-width="1.4"/>' +
         ''.join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#FFFFFF" opacity="{o}"/>' for x, y, r, o in
                 [(52, 86, 1.2, 0.8), (150, 80, 1.6, 0.9), (60, 150, 1, 0.6), (146, 148, 1.3, 0.8), (130, 60, 0.9, 0.6), (70, 64, 0.9, 0.5), (160, 120, 0.8, 0.5)]) +
         f'<ellipse cx="{CX}" cy="{CY + 6}" rx="74" ry="18" fill="none" stroke="url(#x_disk)" stroke-width="9" opacity="0.35" transform="rotate(-12 {CX} {CY + 6})"/>'
         f'<ellipse cx="{CX}" cy="{CY + 6}" rx="74" ry="18" fill="none" stroke="url(#x_disk)" stroke-width="3" transform="rotate(-12 {CX} {CY + 6})"/>' +
         glow(f'<circle cx="{CX}" cy="{CY}" r="30"/>', '#FF9A2E', 2, ((12, 0.1), (7, 0.2), (4, 0.35))) +
         f'<circle cx="{CX}" cy="{CY}" r="28" fill="#000000"/>' +
         # front half of the disk passes in front of the hole
         f'<path d="M{f(CX - 72)},{f(CY + 21)} A74,18 -12 0 0 {f(CX + 72)},{f(CY - 9)}" fill="none" stroke="url(#x_disk)" stroke-width="3" transform="rotate(0)"/>' +
         f'<polygon points="{pts(hornL)}" fill="url(#x_horn)"/><polygon points="{pts(hornR)}" fill="url(#x_horn)"/>' +
         glow(poly_el(hornL), '#FF2D55', 1, ((6, 0.12), (3, 0.25))) + glow(poly_el(hornR), '#FF2D55', 1, ((6, 0.12), (3, 0.25))) +
         '<polygon points="90,108 98,112 90,114" fill="#FF2D55"/><polygon points="110,108 102,112 110,114" fill="#FF2D55"/>' +
         '<rect x="38" y="160" width="34" height="3" fill="#FF2D55" opacity="0.8"/><rect x="44" y="165" width="18" height="2" fill="#FF9A2E" opacity="0.7"/>'
         '<rect x="132" y="70" width="26" height="3" fill="#FF9A2E" opacity="0.8"/><rect x="140" y="75" width="14" height="2" fill="#FF2D55" opacity="0.7"/>' +
         tag(10, '#FF2D55'))


def svg_doc(n, standalone=True):
    defs, body = M[n]
    ns = ' xmlns="http://www.w3.org/2000/svg"' if standalone else ''
    return f'<svg{ns} viewBox="0 0 200 230" width="200" height="230"><defs>{defs}</defs>{body}</svg>'


def write_all(canvas_dir, assets_dir):
    os.makedirs(assets_dir, exist_ok=True)
    for n in range(1, 11):
        open(os.path.join(assets_dir, f'tier{n}.svg'), 'w', encoding='utf-8').write(svg_doc(n))
    blocks = []
    for n in range(1, 11):
        defs, body = M[n]
        blocks.append(f'<sc-if value="{{{{is{n}}}}}" hint-placeholder-val="{{{{ {str(n == 1).lower()} }}}}">\n'
                      f'<svg width="{{{{w}}}}" height="{{{{h}}}}" viewBox="0 0 200 230" role="img" aria-label="{{{{label}}}}">'
                      f'<defs>{defs}</defs>{body}</svg>\n</sc-if>')
    names_js = '[' + ', '.join(f'"{n}"' for n in NAMES) + ']'
    html = ('<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n<title>Rank medal</title>\n'
            '<script src="./support.js"></script>\n</head>\n<body>\n<x-dc>\n<helmet>\n<style>\nbody{margin:0}\n</style>\n</helmet>\n'
            '<div style="display: inline-flex; line-height: 0">\n' + '\n'.join(blocks) + '\n</div>\n</x-dc>\n'
            '<script type="text/x-dc" data-dc-script data-props=\'{"tier":{"editor":"int","min":1,"max":10,"default":10},'
            '"size":{"editor":"int","min":24,"max":400,"default":200},"$preview":{"width":200,"height":230}}\'>\n'
            'class Component extends DCLogic {\n  renderVals() {\n'
            f'    const NAMES = {names_js};\n'
            '    const t = Math.min(10, Math.max(1, parseInt(this.props.tier ?? 10, 10) || 1));\n'
            '    const w = parseInt(this.props.size ?? 200, 10) || 200;\n'
            '    const v = { w, h: Math.round(w * 1.15), label: NAMES[t - 1] + " badge, tier " + t + " of 10" };\n'
            '    for (let i = 1; i <= 10; i++) v["is" + i] = i === t;\n    return v;\n  }\n}\n</script>\n</body>\n</html>\n')
    open(os.path.join(canvas_dir, 'Medal.dc.html'), 'w', encoding='utf-8').write(html)
    sheet = ''.join(f'<g transform="translate({(i % 5) * 210} {(i // 5) * 240})">{svg_doc(i + 1, False)}</g>' for i in range(10))
    open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'medals_preview.svg'), 'w', encoding='utf-8').write(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1050 480" width="1050" height="480"><rect width="1050" height="480" fill="#0B0B0C"/>{sheet}</svg>')
    print('badges written')


def apply_choices():
    """Tiers the user picked from the option boards (2026-11-25: Platinum B, Diamond B, Titan G, Spartan E, Galactic demon H)."""
    import gen_variants
    M[5] = gen_variants.V['plat_B']
    M[6] = gen_variants.V['dia_B']
    import gen_variants2  # final picks: Titan G, Spartan E, Galactic demon H
    M[7] = gen_variants2.V['tit_G']
    M[8] = gen_variants2.V['spa_E']
    M[10] = gen_variants2.V['dem_H']


if __name__ == '__main__':
    apply_choices()
    write_all(sys.argv[1], sys.argv[2])
