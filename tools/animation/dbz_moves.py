"""Golpes no estilo Dragon Ball para o boneco do DMZ (root > waist > head/body/bracos; pernas no root).

Cada golpe e uma lista de poses-chave (tempo, pose, curva ate a proxima). O script reamostra a 40 quadros/s
com a curva escolhida, para o GeckoLib so interpolar entre quadros proximos (fica liso a 60 fps).
Eixos do DMZ: braco x -90 = para a frente; perna x -90 = chute para a frente; waist/root x+ = inclina para a
frente; waist y+ = gira o ombro direito para a frente; root position z- = avanca.
"""
import json, math, sys

FPS = 40
BONES = ["root", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg"]

def ease(kind, u):
    if kind == "lin": return u
    if kind == "in": return u * u * u                      # acelera (golpe chegando)
    if kind == "out": return 1 - (1 - u) ** 3               # desacelera (recuo, pouso)
    if kind == "snap": return 1 - (1 - u) ** 5              # estalo quase instantaneo
    if kind == "hold": return 0.0
    return u * u * (3 - 2 * u)                              # io

def P(**bones):
    """pose: bone=dict(r=[..], p=[..], s=[..])"""
    return bones

def val(pose, bone, ch):
    d = {"r": [0, 0, 0], "p": [0, 0, 0], "s": [1, 1, 1]}[ch]
    return pose.get(bone, {}).get(ch, d)

def build(keys, length):
    """keys: [(t, pose, curve_to_next)]"""
    out = {}
    n = int(round(length * FPS))
    for b in BONES:
        for ch, name in (("r", "rotation"), ("p", "position"), ("s", "scale")):
            used = any(ch in k[1].get(b, {}) for k in keys)
            if not used: continue
            frames = {}
            for f in range(n + 1):
                t = f / FPS
                i = 0
                while i + 1 < len(keys) and keys[i + 1][0] <= t + 1e-9: i += 1
                if i + 1 >= len(keys):
                    v = val(keys[i][1], b, ch)
                else:
                    t0, p0, cv = keys[i]
                    t1, p1, _ = keys[i + 1]
                    u = ease(cv, (t - t0) / (t1 - t0))
                    a, c = val(p0, b, ch), val(p1, b, ch)
                    v = [a[j] + (c[j] - a[j]) * u for j in range(3)]
                frames["%.4f" % t] = {"vector": [round(x, 3) for x in v]}
            # tira quadros repetidos
            ks = sorted(frames, key=float)
            keep = {}
            for j, k in enumerate(ks):
                if 0 < j < len(ks) - 1 and frames[k] == frames[ks[j - 1]] == frames[ks[j + 1]]: continue
                keep[k] = frames[k]
            out.setdefault(b, {})[name] = keep
    return {"animation_length": length, "bones": out}

# ---------------------------------------------------------------- poses base
GUARD = P(root=dict(r=[6, 0, 0], p=[0, -0.6, 0]), waist=dict(r=[6, 12, 0]),
          right_arm=dict(r=[-55, 10, 12]), left_arm=dict(r=[-70, -18, -8]),
          right_leg=dict(r=[14, 0, 3]), left_leg=dict(r=[-16, 0, -3]))

MOVES = {}

# Arrancada + soco direto (Goku chegando voando no Cell).
# waist y- = ombro direito vai para a frente (y+ = recua o ombro direito, preparacao).
# Braco "horizontal para a frente" com o corpo inclinado L graus: x = -90 - L.
MOVES["dbz_dash_punch"] = (0.6, [
    # voando deitado, punho esquerdo na frente, direito armado na cintura
    (0.00, P(root=dict(r=[62, 0, 0], p=[0, 6, 13]), head=dict(r=[-52, 0, 0]), waist=dict(r=[0, 18, 0]),
             right_arm=dict(r=[18, 6, 14]), left_arm=dict(r=[-150, -6, -4]),
             right_leg=dict(r=[14, 0, 4]), left_leg=dict(r=[6, 0, -4])), "in"),
    (0.10, P(root=dict(r=[44, 0, 0], p=[0, 4, 8]), head=dict(r=[-36, 0, 0]), waist=dict(r=[4, 38, 0]),
             right_arm=dict(r=[30, 10, 20]), left_arm=dict(r=[-132, -6, -4]),
             right_leg=dict(r=[30, 0, 4]), left_leg=dict(r=[-4, 0, -4])), "in"),
    # estica o braco no quadro mais rapido (some no borrao) e encosta em 0,16
    (0.14, P(root=dict(r=[16, 0, 0], p=[0, 0.5, -1]), head=dict(r=[-18, 0, 0]), waist=dict(r=[10, -22, 0]),
             right_arm=dict(r=[-112, 2, 6], s=[1, 1.3, 1]), left_arm=dict(r=[24, -8, -18]),
             right_leg=dict(r=[32, 0, 4]), left_leg=dict(r=[-22, 0, -4])), "snap"),
    (0.16, P(root=dict(r=[14, 0, 0], p=[0, 0, -3]), head=dict(r=[-18, 0, 0]), waist=dict(r=[12, -36, 0]),
             right_arm=dict(r=[-116, 2, 4]), left_arm=dict(r=[36, -10, -24]),
             right_leg=dict(r=[34, 0, 4]), left_leg=dict(r=[-24, 0, -4])), "lin"),
    # segura o punho enterrado (o congelamento do impacto cai aqui)
    (0.30, P(root=dict(r=[15, 0, 0], p=[0, 0, -3.6]), head=dict(r=[-19, 0, 0]), waist=dict(r=[13, -38, 0]),
             right_arm=dict(r=[-117, 2, 4]), left_arm=dict(r=[38, -10, -24]),
             right_leg=dict(r=[35, 0, 4]), left_leg=dict(r=[-25, 0, -4])), "out"),
    (0.60, GUARD, "io"),
])

# Soco no estomago com o corpo todo (Gohan SSJ2 no Cell): base baixa, ombro e peso para a frente
MOVES["dbz_gut_punch"] = (0.75, [
    (0.00, GUARD, "out"),
    (0.09, P(root=dict(r=[6, 0, 0], p=[0, -2.5, 2]), head=dict(r=[-6, 0, 0]), waist=dict(r=[12, 46, 0]),
             right_arm=dict(r=[40, 12, 16]), left_arm=dict(r=[-96, -20, -8]),
             right_leg=dict(r=[20, 0, 4]), left_leg=dict(r=[-22, 0, -4])), "in"),
    (0.16, P(root=dict(r=[10, 0, 0], p=[0, -3, -4]), head=dict(r=[-26, 0, 0]), waist=dict(r=[22, -18, 0]),
             right_arm=dict(r=[-98, 2, 6], s=[1, 1.28, 1]), left_arm=dict(r=[30, -12, -22]),
             right_leg=dict(r=[38, 0, 4]), left_leg=dict(r=[-28, 0, -6])), "snap"),
    (0.19, P(root=dict(r=[12, 0, 0], p=[0, -3.2, -6.5]), head=dict(r=[-30, 0, 0]), waist=dict(r=[24, -34, 0]),
             right_arm=dict(r=[-105, 0, 4]), left_arm=dict(r=[40, -12, -24]),
             right_leg=dict(r=[42, 0, 4]), left_leg=dict(r=[-30, 0, -6])), "lin"),
    (0.46, P(root=dict(r=[13, 0, 0], p=[0, -3.3, -7]), head=dict(r=[-31, 0, 0]), waist=dict(r=[25, -35, 0]),
             right_arm=dict(r=[-106, 0, 4]), left_arm=dict(r=[41, -12, -24]),
             right_leg=dict(r=[43, 0, 4]), left_leg=dict(r=[-31, 0, -6])), "out"),
    (0.75, GUARD, "io"),
])

# Chute voador (Goku atravessando de lado): corpo deitado para tras, perna da frente reta no estomago.
# Perna horizontal com o corpo inclinado para tras B graus: x = -90 + B.
MOVES["dbz_flying_kick"] = (0.65, [
    (0.00, GUARD, "out"),
    (0.08, P(root=dict(r=[-10, 0, 0], p=[0, 2, 2]), waist=dict(r=[-4, 18, 0]), head=dict(r=[8, 0, 0]),
             right_arm=dict(r=[24, 0, 30]), left_arm=dict(r=[-70, 0, -30]),
             right_leg=dict(r=[-60, 0, 6]), left_leg=dict(r=[-70, 0, -4])), "in"),
    (0.15, P(root=dict(r=[-36, 0, 0], p=[0, 4, -13]), waist=dict(r=[-6, 8, 0]), head=dict(r=[30, 0, 0]),
             right_arm=dict(r=[40, 10, 44]), left_arm=dict(r=[30, -10, -44]),
             right_leg=dict(r=[-48, 0, 4], s=[1, 1.25, 1]), left_leg=dict(r=[-4, 0, -4])), "snap"),
    (0.18, P(root=dict(r=[-40, 0, 0], p=[0, 4, -16]), waist=dict(r=[-8, 6, 0]), head=dict(r=[34, 0, 0]),
             right_arm=dict(r=[46, 10, 50]), left_arm=dict(r=[36, -10, -50]),
             right_leg=dict(r=[-46, 0, 4]), left_leg=dict(r=[2, 0, -4])), "lin"),
    (0.36, P(root=dict(r=[-41, 0, 0], p=[0, 4, -16.5]), waist=dict(r=[-9, 6, 0]), head=dict(r=[35, 0, 0]),
             right_arm=dict(r=[47, 10, 51]), left_arm=dict(r=[37, -10, -51]),
             right_leg=dict(r=[-45, 0, 4]), left_leg=dict(r=[3, 0, -4])), "out"),
    (0.65, GUARD, "io"),
])

# Gancho que lanca para cima: agacha fundo e explode para cima com o braco reto no queixo
MOVES["dbz_rising_uppercut"] = (0.8, [
    (0.00, GUARD, "out"),
    (0.11, P(root=dict(r=[12, 0, 0], p=[0, -4, 1]), waist=dict(r=[22, 36, 0]), head=dict(r=[-18, 0, 0]),
             right_arm=dict(r=[24, 10, 14]), left_arm=dict(r=[-80, -20, -10]),
             right_leg=dict(r=[26, 0, 4]), left_leg=dict(r=[-30, 0, -4])), "in"),
    (0.19, P(root=dict(r=[2, 0, 0], p=[0, 4, -4]), waist=dict(r=[-6, -28, -8]), head=dict(r=[-24, 0, 0]),
             right_arm=dict(r=[-136, 8, 2], s=[1, 1.3, 1]), left_arm=dict(r=[30, -10, -20]),
             right_leg=dict(r=[14, 0, 4]), left_leg=dict(r=[-24, 0, -4])), "snap"),
    (0.22, P(root=dict(r=[0, 0, 0], p=[0, 6, -4.5]), waist=dict(r=[-10, -34, -10]), head=dict(r=[-30, 0, 0]),
             right_arm=dict(r=[-150, 6, 0]), left_arm=dict(r=[36, -10, -24]),
             right_leg=dict(r=[18, 0, 4]), left_leg=dict(r=[-20, 0, -4])), "lin"),
    (0.45, P(root=dict(r=[-1, 0, 0], p=[0, 6.5, -4.6]), waist=dict(r=[-11, -35, -10]), head=dict(r=[-31, 0, 0]),
             right_arm=dict(r=[-168, 6, 0]), left_arm=dict(r=[37, -10, -24]),
             right_leg=dict(r=[19, 0, 4]), left_leg=dict(r=[-19, 0, -4])), "out"),
    (0.80, GUARD, "io"),
])

# Martelo duplo (maos juntas acima da cabeca, desce com o corpo todo na cabeca do chefe)
MOVES["dbz_hammer"] = (0.75, [
    (0.00, GUARD, "out"),
    (0.13, P(root=dict(r=[-12, 0, 0], p=[0, 1, 1]), head=dict(r=[-20, 0, 0]), waist=dict(r=[-18, 0, 0]),
             right_arm=dict(r=[-200, 0, -16]), left_arm=dict(r=[-200, 0, 16]),
             right_leg=dict(r=[-30, 0, 4]), left_leg=dict(r=[10, 0, -4])), "in"),
    (0.19, P(root=dict(r=[18, 0, 0], p=[0, 0, -2]), head=dict(r=[-26, 0, 0]), waist=dict(r=[26, 0, 0]),
             right_arm=dict(r=[-92, 0, -14], s=[1, 1.3, 1]), left_arm=dict(r=[-92, 0, 14], s=[1, 1.3, 1]),
             right_leg=dict(r=[-10, 0, 4]), left_leg=dict(r=[24, 0, -4])), "snap"),
    (0.22, P(root=dict(r=[22, 0, 0], p=[0, 0, -3]), head=dict(r=[-30, 0, 0]), waist=dict(r=[30, 0, 0]),
             right_arm=dict(r=[-72, 0, -14]), left_arm=dict(r=[-72, 0, 14]),
             right_leg=dict(r=[-8, 0, 4]), left_leg=dict(r=[28, 0, -4])), "lin"),
    (0.46, P(root=dict(r=[23, 0, 0], p=[0, 0, -3.2]), head=dict(r=[-31, 0, 0]), waist=dict(r=[31, 0, 0]),
             right_arm=dict(r=[-70, 0, -14]), left_arm=dict(r=[-70, 0, 14]),
             right_leg=dict(r=[-7, 0, 4]), left_leg=dict(r=[29, 0, -4])), "out"),
    (0.75, GUARD, "io"),
])

# Defesa de bracos cruzados (Goku segurando o pisao do Cell) - pose mantida
BLOCK = P(root=dict(r=[4, 0, 0], p=[0, -2.4, 0]), head=dict(r=[16, 0, 0]), waist=dict(r=[10, 0, 0]),
          right_arm=dict(r=[-128, -30, -12]), left_arm=dict(r=[-128, 30, 12]),
          right_leg=dict(r=[-22, 0, 8]), left_leg=dict(r=[22, 0, -8]))
MOVES["dbz_cross_block"] = (0.4, [
    (0.00, GUARD, "snap"),
    (0.08, P(**{**BLOCK, "root": dict(r=[6, 0, 0], p=[0, -3.4, 0.8])}), "out"),
    (0.40, BLOCK, "hold"),
])

# Guarda de luta (loop leve, respirando)
def guard_loop():
    keys = []
    for i, t in enumerate([0, 0.5, 1.0]):
        k = 0.6 if i == 1 else 0
        g = json.loads(json.dumps(GUARD))
        g["root"]["p"] = [0, -0.6 - k, 0]
        g["waist"]["r"] = [6 + k * 2, 12, 0]
        keys.append((t, g, "io"))
    return keys
MOVES["dbz_guard"] = (1.0, guard_loop())

def export():
    anims = {}
    for name, (length, keys) in MOVES.items():
        a = build(keys, length)
        if name in ("dbz_guard",): a["loop"] = True
        if name in ("dbz_cross_block",): a["loop"] = "hold_on_last_frame"
        anims["dmzkicontrol.cs." + name] = a
    return {"format_version": "1.8.0", "animations": anims}

if __name__ == "__main__":
    json.dump(export(), open(sys.argv[1], "w"), indent=1)
    print("ok", len(MOVES))

def merge_into(path):
    """Junta (ou atualiza) os golpes no cutscene.animation.json do mod."""
    d = json.load(open(path))
    d["animations"].update(export()["animations"])
    json.dump(d, open(path, "w"), separators=(",", ":"))
