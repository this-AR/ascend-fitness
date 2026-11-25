"""Builds the app's exercise library (exercises.json + img/*.webp) from:
  curated.psv (hand-tuned lifts, authoritative), free-exercise-db (Unlicense), wger (CC-BY-SA / CC0).
- Duplicates are merged on a word-set key (synonyms, plurals, word order, filler words, equipment words).
- Rank standards for non-curated exercises are anchored to the most similar hand-tuned lift,
  converted for equipment and adjusted for the variation; simple rules only as a fallback.
Usage: python build_catalog.py <data dir> <out json> [<img out dir>]  (images are downloaded when img dir is given)"""
import collections
import hashlib
import html
import json
import os
import re
import sys

data, out = sys.argv[1], sys.argv[2]
img_dir = sys.argv[3] if len(sys.argv) > 3 else None

# ---------- names and keys ----------
SYN = {'pullup': 'pull up', 'pullups': 'pull up', 'pushup': 'push up', 'pushups': 'push up', 'chinup': 'chin up',
       'situp': 'sit up', 'db': 'dumbbell', 'bb': 'barbell', 'flye': 'fly', 'flyes': 'fly', 'flies': 'fly',
       'bicep': 'biceps', 'tricep': 'triceps', 'alternate': 'alternating', 'alt': 'alternating',
       'sz': 'ez', 'pulldown': 'pull down', 'pushdown': 'push down', 'crossovers': 'crossover', 'rdl': 'romanian deadlift',
       'ohp': 'overhead press', 'kb': 'kettlebell', 'dumbell': 'dumbbell', 'rows': 'row', 'raises': 'raise'}
STOP = {'with', 'the', 'a', 'an', 'on', 'using', 'and', 'of', 'to', 'for', 'in', 'exercise', 'movement', 'version',
        'standing', 'basic', 'simple', 'classic', 'regular', 'normal', 'variation', 'style'}
EQ_WORDS = {'Dumbbell': {'dumbbell'}, 'Barbell': {'barbell'}, 'Cable': {'cable', 'pulley'}, 'Machine': {'machine', 'lever'},
            'EZ bar': {'ez', 'bar'}, 'Kettlebell': {'kettlebell'}, 'Band': {'band', 'resistance'},
            'Bodyweight': {'bodyweight'}}
KEEP_PLURAL = {'biceps', 'triceps', 'abs', 'glutes', 'lats', 'press', 'calves', 'jacks'}


def words(n):
    n = n.lower().replace('&', ' and ').replace('-', ' ')
    n = re.sub(r'[^a-z0-9 ]+', ' ', n)
    res = []
    for w in n.split():
        for part in SYN.get(w, w).split():
            if len(part) > 3 and part.endswith('s') and not part.endswith('ss') and part not in KEEP_PLURAL:
                part = part[:-1]
            res.append(part)
    return res


def core_words(name, eq):
    return set(w for w in words(name) if w not in STOP) - EQ_WORDS.get(eq, set())


def key(name, eq):
    return eq + '|' + ' '.join(sorted(core_words(name, eq)))


KEEP_UPPER = {'EZ', 'SZ', 'TRX', 'V', 'T', 'RDL', 'II', 'III', 'IT', 'W', 'Y', 'L', 'BOSU', 'GHR', 'BW', 'JM', 'ATG', 'HIIT'}


def sentence(n):
    toks = ' '.join(n.replace('_', ' ').split()).split(' ')
    res = []
    for i, t in enumerate(toks):
        core = re.sub(r'[^A-Za-z]', '', t)
        if core and core.isupper() and core in KEEP_UPPER:
            res.append(t)
        elif i == 0:
            res.append(t[:1].upper() + t[1:].lower())
        else:
            res.append(t.lower())
    return ' '.join(res)


# ---------- muscles / equipment ----------
MUSC = {'abdominals': 'abs', 'abductors': 'glutes', 'adductors': 'adductors', 'biceps': 'biceps', 'calves': 'calves',
        'chest': 'chest', 'forearms': 'forearms', 'glutes': 'glutes', 'hamstrings': 'hamstrings', 'lats': 'lats',
        'lower back': 'lowerback', 'middle back': 'upperback', 'neck': 'traps', 'quadriceps': 'quads',
        'shoulders': 'shoulders', 'traps': 'traps', 'triceps': 'triceps',
        'Glutes': 'glutes', 'Lats': 'lats', 'Shoulders': 'shoulders', 'Quads': 'quads', 'Chest': 'chest', 'Abs': 'abs',
        'Biceps': 'biceps', 'Triceps': 'triceps', 'Trapezius': 'traps', 'Hamstrings': 'hamstrings',
        'Obliquus externus abdominis': 'obliques', 'Calves': 'calves', 'Serratus anterior': 'obliques',
        'Brachialis': 'biceps', 'Soleus': 'calves'}
WCAT = {'Legs': 'quads', 'Back': 'upperback', 'Arms': 'biceps', 'Shoulders': 'shoulders', 'Abs': 'abs', 'Chest': 'chest',
        'Calves': 'calves', 'Cardio': 'cardio'}
LOADED_WORDS = ('press', 'row', 'curl', 'deadlift', 'extension', 'fly', 'raise', 'shrug', 'pulldown', 'pushdown')


def equipment_from_name(name, eq):
    n = name.lower()
    if 'smith' in n or 'machine' in n or 'leg press' in n or 'lever' in n:
        return 'Machine'
    for word, e in (('dumbbell', 'Dumbbell'), ('barbell', 'Barbell'), ('kettlebell', 'Kettlebell'), ('cable', 'Cable'),
                    ('ez bar', 'EZ bar'), ('ez-bar', 'EZ bar'), ('sz', 'EZ bar'), ('band', 'Band')):
        if word == 'band' and 'it band' in n:
            continue
        if re.search(r'\b' + re.escape(word), n):
            return e
    if eq == 'Bodyweight' and any(w in n for w in LOADED_WORDS) and not any(w in n for w in ('push', 'pike', 'dip', 'bodyweight')):
        return 'Other'
    return eq


def per_hand(name, eq):
    return eq in ('Dumbbell', 'Kettlebell') and not any(w in name.lower() for w in ('goblet', 'pullover', 'two hand', 'two-hand'))


# ---------- rank standards ----------
ISO_WORDS = ('curl', 'raise', 'fly', 'extension', 'kickback', 'pushdown', 'push down', 'crossover', 'pullover',
             'pec deck', 'wrist', 'abduction', 'adduction', 'crunch', 'twist')
B = {'quads': 1.1, 'glutes': 1.2, 'hamstrings': 1.1, 'lowerback': 1.0, 'chest': 0.8, 'upperback': 0.75, 'lats': 0.7,
     'shoulders': 0.5, 'traps': 1.0, 'triceps': 0.6, 'biceps': 0.4, 'calves': 1.2, 'forearms': 0.45, 'abs': 0.4,
     'obliques': 0.3, 'adductors': 0.8}
ISO = {'biceps': 1.0, 'forearms': 0.8, 'calves': 1.0, 'triceps': 0.6, 'traps': 1.0}
# Strength with one implement relative to the barbell version of the same lift (dumbbell/kettlebell are per hand).
EQ = {'Barbell': 1.0, 'EZ bar': 0.95, 'Machine': 1.0, 'Cable': 0.75, 'Dumbbell': 0.4, 'Kettlebell': 0.35}
REP_RULES = [('plank', 0), ('hold', 0), ('stretch', 0), ('one arm push', 3), ('archer', 8), ('clap', 12), ('plyo', 12),
             ('diamond', 20), ('decline push', 25), ('push up', 30), ('muscle up', 3), ('chin up', 10), ('pull up', 9),
             ('handstand', 5), ('pike', 15), ('dip', 15), ('pistol', 5), ('nordic', 5), ('inverted row', 15),
             ('burpee', 20), ('lunge', 30), ('squat', 40), ('sit up', 35), ('crunch', 40), ('leg raise', 12),
             ('knee raise', 15), ('bridge', 30), ('hip thrust', 30), ('back extension', 20), ('hyperextension', 20),
             ('mountain climber', 0), ('jumping jack', 0), ('run', 0), ('jog', 0), ('walk', 0), ('jump rope', 0)]
MOVES = {'press', 'curl', 'row', 'squat', 'deadlift', 'raise', 'fly', 'extension', 'pull', 'push', 'dip', 'lunge',
         'thrust', 'bridge', 'shrug', 'crunch', 'pullover', 'kickback', 'clean', 'snatch', 'swing', 'calf', 'morning'}
MODS = [(('single', 'arm'), 0.55), (('one', 'arm'), 0.55), (('single', 'leg'), 0.55), (('one', 'leg'), 0.55),
        (('pause',), 0.92), (('paused',), 0.92), (('deficit',), 0.92), (('tempo',), 0.85), (('close', 'grip'), 0.92),
        (('wide', 'grip'), 0.95), (('behind', 'neck'), 0.85), (('partial',), 1.15), (('rack',), 1.12), (('pin',), 1.1),
        (('board',), 1.1), (('floor',), 0.92), (('incline',), 0.85), (('decline',), 1.05), (('reverse', 'grip'), 0.8),
        (('zottman',), 0.85), (('spider',), 0.8), (('preacher',), 0.9), (('concentration',), 0.85),
        (('front',), 0.85), (('jump',), 0.5)]


# For bodyweight moves the factor scales clean reps: harder variants mean fewer reps.
REP_MODS = [(('decline',), 0.8), (('incline',), 1.4), (('diamond',), 0.65), (('close', 'grip'), 0.8), (('wide', 'grip'), 0.9),
            (('one', 'arm'), 0.1), (('single', 'arm'), 0.1), (('archer',), 0.3), (('clap',), 0.4), (('plyometric',), 0.4),
            (('weighted',), 0.5), (('kneeling',), 1.6), (('knee',), 1.5), (('assisted',), 1.5), (('negative',), 1.3),
            (('pike',), 0.5), (('typewriter',), 0.3), (('l', 'sit'), 0.5), (('jump',), 0.8), (('pause',), 0.8)]


def rep_mods_factor(new_ws, anchor_ws):
    val = 1.0
    for ws, m in REP_MODS:
        if all(w in new_ws for w in ws) and not all(w in anchor_ws for w in ws):
            val *= m
    return val


def mods_factor(new_ws, anchor_ws, eq):
    val = 1.0
    for ws, m in MODS:
        if all(w in new_ws for w in ws) and not all(w in anchor_ws for w in ws):
            if ws[-1] == 'arm' and eq in ('Dumbbell', 'Kettlebell'):
                continue  # dumbbell standards are already per hand
            val *= m
    return val


curated_rows = []


def anchored(name, eq, metric):
    """Gold standard taken from the most similar hand-tuned lift, or None."""
    ws = core_words(name, eq)
    best, best_j = None, 0.0
    for c in curated_rows:
        if c['gold'] <= 0 or (c['metric'] == 'R') != (metric == 'R'):
            continue
        cws = core_words(c['name'], c['equipment'])
        if not (ws & cws & MOVES):
            continue
        j = len(ws & cws) / len(ws | cws) + (0.15 if c['equipment'] == eq else 0)
        if j > best_j:
            best, best_j = c, j
    if best is None or best_j < 0.45:
        return None
    new_ws, anchor_ws = set(words(name)), set(words(best['name']))
    if metric == 'R':
        return round(max(1.0, best['gold'] * rep_mods_factor(new_ws, anchor_ws))), best['name']
    if eq not in EQ or best['equipment'] not in EQ:
        return None
    g = best['gold'] / EQ[best['equipment']] * EQ[eq] * mods_factor(new_ws, anchor_ws, eq)
    return round(g, 3), best['name']


def rule_estimate(name, primary, equipment, compound, category, level):
    n = ' '.join(words(name))
    if equipment == 'Bodyweight':
        g = next((v for k, v in REP_RULES if k in n), 0)
        if level == 'expert' and g:
            g = max(1, round(g * 0.6))
        return 'R', float(g)
    if equipment not in EQ:
        return 'R', 0.0
    if 'leg press' in n:
        return 'W', 2.5 if equipment != 'Dumbbell' else 0.0
    base = B.get(primary, 0.5)
    if not compound:
        base *= ISO.get(primary, 0.55)
    g = base * EQ[equipment]
    if category == 'olympic weightlifting':
        g = 0.75 * EQ[equipment]
    if any(k in n for k in ('single leg', 'one leg', 'bulgarian', 'split squat', 'step up', 'lunge')):
        g *= 0.55
    return 'W', round(g, 3)


def estimate(name, primary, equipment, compound, category, level):
    """Returns (metric, gold, anchor). Stretching, cardio, strongman and unknown kit stay unranked."""
    metric, g = rule_estimate(name, primary, equipment, compound, category, level)
    if category in ('stretching', 'cardio', 'strongman') or primary == 'cardio' or g == 0:
        return metric, 0.0, None
    a = anchored(name, equipment, metric)
    return (metric, float(a[0]), a[1]) if a else (metric, g, None)


# ---------- collect ----------
rows, by_key = [], {}


def add(r):
    if not r['name']:
        return False
    k = key(r['name'], r['equipment'])
    if k in by_key:
        e = by_key[k]  # merge into the kept entry: fill its gaps
        e['instructions'] = e['instructions'] or r['instructions']
        e['images_src'] = e['images_src'] or r['images_src']
        e['level'] = e['level'] or r['level']
        return False
    by_key[k] = r
    rows.append(r)
    return True


for line in open(f'{data}/curated.psv', encoding='utf-8'):
    p = line.rstrip('\n').split('|')
    if len(p) < 7:
        continue
    r = dict(name=p[0], primary=p[1], secondary=p[2], equipment=p[3], metric=p[4], gold=float(p[5]), perHand=p[6] == '1',
             category='strength', level='', instructions='', estimated=False, anchor=None, source='curated', images_src=[])
    add(r)
    curated_rows.append(r)

FEQ = {'barbell': 'Barbell', 'dumbbell': 'Dumbbell', 'cable': 'Cable', 'machine': 'Machine', 'body only': 'Bodyweight',
       'kettlebells': 'Kettlebell', 'bands': 'Band', 'medicine ball': 'Medicine ball', 'exercise ball': 'Exercise ball',
       'foam roll': 'Foam roll', 'e-z curl bar': 'EZ bar', 'other': 'Other', None: 'Bodyweight'}
FDB_IMG = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/'
for x in json.load(open(f'{data}/free-exercise-db.json', encoding='utf-8')):
    name = sentence(x['name'])
    eq = equipment_from_name(name, FEQ.get(x.get('equipment'), 'Other'))
    prim = MUSC.get((x.get('primaryMuscles') or ['abdominals'])[0], 'abs')
    sec = sorted({MUSC[m] for m in x.get('secondaryMuscles', []) if m in MUSC} - {prim})
    compound = x['mechanic'] == 'compound' if x.get('mechanic') else not any(w in name.lower() for w in ISO_WORDS)
    steps = '\n'.join(s.strip() for s in x.get('instructions', []) if s.strip())
    metric, gold, anchor = estimate(name, prim, eq, compound, x.get('category'), x.get('level'))
    add(dict(name=name, primary=prim, secondary=','.join(sec), equipment=eq, metric=metric, gold=gold,
             perHand=per_hand(name, eq), category=x.get('category') or 'strength', level=x.get('level') or '',
             instructions=steps, estimated=True, anchor=anchor, source='free-exercise-db',
             images_src=[FDB_IMG + p for p in x.get('images', [])[:2]]))

WEQ = [('Barbell', 'Barbell'), ('SZ-Bar', 'EZ bar'), ('Dumbbell', 'Dumbbell'), ('Kettlebell', 'Kettlebell'),
       ('Cable machine', 'Cable'), ('Resistance band', 'Band'), ('Swiss Ball', 'Exercise ball')]


def strip_html(s):
    s = re.sub(r'</(p|li)>|<br ?/?>', '\n', s or '')
    s = html.unescape(re.sub(r'<[^>]+>', '', s))
    return '\n'.join(line.strip() for line in s.splitlines() if line.strip())


for x in json.load(open(f'{data}/wger.json', encoding='utf-8')):
    tr = [t for t in x['translations'] if t['language'] == 2]
    if not tr:
        continue
    name = sentence(re.sub(r'\s*\((custom|new|test)\)\s*', ' ', tr[0]['name'], flags=re.I).strip())
    if not 3 <= len(name) <= 70:
        continue
    eqs = [e['name'] for e in x['equipment']]
    eq = equipment_from_name(name, next((v for k, v in WEQ if k in eqs), 'Bodyweight'))
    ms = [m for m in (MUSC.get(m.get('name_en') or m['name']) for m in x['muscles']) if m]
    prim = ms[0] if ms else WCAT.get(x['category']['name'], 'abs')
    sec = sorted({MUSC.get(m.get('name_en') or m['name']) for m in x['muscles_secondary']} - {None, prim})
    cat = 'cardio' if x['category']['name'] == 'Cardio' else 'strength'
    compound = not any(w in name.lower() for w in ISO_WORDS)
    metric, gold, anchor = estimate(name, prim, eq, compound, cat, '')
    imgs = sorted(x.get('images', []), key=lambda i: not i.get('is_main'))
    add(dict(name=name, primary=prim, secondary=','.join(sec), equipment=eq, metric=metric, gold=gold,
             perHand=per_hand(name, eq), category=cat, level='', instructions=strip_html(tr[0].get('description')),
             estimated=True, anchor=anchor, source='wger (' + x['license']['short_name'] + ')',
             images_src=[i['image'] for i in imgs[:2]]))

# Hand-tuned lifts with no photo or instructions borrow them from the closest library entry that has them.
ALIAS = {'Skull crusher': ['Lying triceps press', 'EZ-bar skullcrusher'], 'Barbell row': ['Bent over barbell row'],
         'Pendlay row': ['Bent over barbell row'], 'Lat pulldown': ['Wide-grip lat pulldown', 'Close-grip front lat pulldown'],
         'Rear delt fly': ['Seated bent-over rear delt raise', 'Reverse flyes'], 'Pec deck': ['Butterfly'],
         'Hip adduction': ['Thigh adductor'], 'Ab wheel rollout': ['Ab roller'], 'Back extension': ['Hyperextensions (back extensions)'],
         'Hanging leg raise': ['Hanging leg raise'], 'Inverted row': ['Inverted row'], 'Muscle-up': ['Muscle up'],
         'Nordic curl': ['Natural glute ham raise'], 'Dumbbell pullover': ['Bent-arm dumbbell pullover', 'Straight-arm dumbbell pullover'],
         'Dumbbell step-up': ['Dumbbell step ups'], 'Suspension row': ['Suspended row'], 'Suspension push-up': ['Suspended push-up']}
by_lower = {}
for r in rows:
    by_lower.setdefault(r['name'].lower(), r)


def borrow(c, need):
    for alias in ALIAS.get(c['name'], []):
        r = by_lower.get(alias.lower())
        if r and r is not c and r[need]:
            return r
    cw = core_words(c['name'], c['equipment'])
    best, best_j = None, 0.0
    for r in rows:
        if r is c or r['source'] == 'curated' or not r[need]:
            continue
        rw = core_words(r['name'], r['equipment'])
        if not (cw & rw & MOVES):
            continue
        j = len(cw & rw) / len(cw | rw) + (0.1 if r['equipment'] == c['equipment'] else 0)
        if j > best_j:
            best, best_j = r, j
    return best if best_j >= 0.45 else None


for c in curated_rows:
    for need in ('images_src', 'instructions'):
        if not c[need]:
            b = borrow(c, need)
            if b:
                c[need] = b[need]

# Exercises still without a photo borrow one from a near-identical exercise (same equipment, same movement,
# strong word overlap). The app labels these as "a similar exercise" so nobody mistakes them for the exact move.
with_photos = [r for r in rows if r['images_src']]
for r in rows:
    r.setdefault('imagesFrom', '')
    if r['images_src']:
        continue
    rw = core_words(r['name'], r['equipment'])
    best, best_j = None, 0.0
    for c in with_photos:
        if c['equipment'] != r['equipment']:
            continue
        cw = core_words(c['name'], c['equipment'])
        if not (rw & cw & MOVES):
            continue
        j = len(rw & cw) / len(rw | cw)
        if j > best_j:
            best, best_j = c, j
    if best is not None and best_j >= 0.6:
        r['images_src'] = best['images_src']
        r['imagesFrom'] = best['name']

final, names = [], set()
for r in rows:
    if r['name'].lower() not in names:
        names.add(r['name'].lower())
        final.append(r)
final.sort(key=lambda r: r['name'].lower())

# ---------- images ----------
if img_dir:
    import concurrent.futures
    import io
    import urllib.request
    from PIL import Image
    os.makedirs(img_dir, exist_ok=True)

    def fetch(url):
        fname = hashlib.sha1(url.encode()).hexdigest()[:12] + '.webp'
        path = os.path.join(img_dir, fname)
        if not os.path.exists(path):
            try:
                raw = urllib.request.urlopen(url, timeout=60).read()
                im = Image.open(io.BytesIO(raw)).convert('RGB')
                im.thumbnail((320, 320))
                im.save(path, 'WEBP', quality=50, method=6)
            except Exception as e:  # a missing photo is skipped, not fatal
                print('img fail', url, e)
                return url, None
        return url, 'img/' + fname

    urls = sorted({u for r in final for u in r['images_src']})
    with concurrent.futures.ThreadPoolExecutor(16) as ex:
        got = dict(ex.map(fetch, urls))
    for r in final:
        r['images'] = [got[u] for u in r['images_src'] if got.get(u)]
for r in final:
    r.setdefault('images', [])
    r.pop('images_src', None)

json.dump({'version': 3,
           'attribution': 'Exercise data and photos: free-exercise-db (Unlicense, github.com/yuhonas/free-exercise-db) '
                          'and wger (wger.de, CC-BY-SA 4.0 / 3.0 / CC0), adapted.',
           'exercises': final}, open(out, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))

c = collections.Counter(r['source'].split(' ')[0] for r in final)
print('total', len(final), dict(c))
print('borrowed photos', sum(1 for r in final if r.get('imagesFrom')), 'still none', sum(1 for r in final if not r['images']))
print('ranked', sum(1 for r in final if r['gold'] > 0), 'anchored', sum(1 for r in final if r.get('anchor')),
      'with instructions', sum(1 for r in final if r['instructions']), 'with images', sum(1 for r in final if r['images']))
for n in ['Dumbbell bench press', 'Incline dumbbell press', 'Close-grip barbell bench press', 'Single-arm cable row',
          'Barbell curl', 'Preacher curl', 'Hammer curls', 'Dumbbell alternate bicep curl', 'Decline push-up', 'Front squat',
          'Alternating bicep curls', 'Alternating biceps curls with dumbbell']:
    r = next((r for r in final if r['name'].lower() == n.lower()), None)
    print(' ', n, '->', r and (r['equipment'], r['metric'], r['gold'], r.get('anchor'), len(r['images'])))
