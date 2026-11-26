"""Poses v2: animacoes fluidas.

- Golpes (uma vez): poses-chave com preparacao, impacto, passar do ponto e recuperacao, com curvas de
  aceleracao, amostradas a 60 quadros por segundo; cabeca e tronco acompanham com atraso (follow-through).
- Poses em loop: base + camadas de movimento organico (respiracao, balanco, peso) em senoides que fecham
  exatamente no loop de 4 s; tremor so nas poses tensas.

Uso: python3 poses_v2.py cutscene.animation.json   (reescreve o arquivo no lugar)
"""
import json
import math
import sys

FPS = 60
LOOP = 4.0
P = "dmzkicontrol.cs."


# ------------------------------------------------------------------ curvas

def ease(kind, u):
    if kind == "lin":
        return u
    if kind == "in":      # comeca devagar, termina rapido (o golpe)
        return u * u * u
    if kind == "out":     # comeca rapido, freia (recuperacao)
        return 1 - (1 - u) ** 3
    if kind == "io":
        return 4 * u * u * u if u < 0.5 else 1 - (-2 * u + 2) ** 3 / 2
    if kind == "back":    # passa um pouco do ponto e volta
        c1, c3 = 1.70158, 2.70158
        return 1 + c3 * (u - 1) ** 3 + c1 * (u - 1) ** 2
    if kind == "elastic":
        if u in (0, 1):
            return u
        return 2 ** (-10 * u) * math.sin((u * 10 - 0.75) * (2 * math.pi / 3)) + 1
    return u


def sample(keys, length):
    """keys: [(t, [x,y,z], ease)] -> {tempo: vetor} denso."""
    keys = sorted(keys, key=lambda k: k[0])
    times = set(round(i / FPS, 4) for i in range(int(length * FPS) + 1))
    times |= set(round(k[0], 4) for k in keys)
    times.add(round(length, 4))
    out = {}
    for t in sorted(times):
        if t <= keys[0][0]:
            v = keys[0][1]
        elif t >= keys[-1][0]:
            v = keys[-1][1]
        else:
            for i in range(len(keys) - 1):
                a, b = keys[i], keys[i + 1]
                if a[0] <= t <= b[0]:
                    u = (t - a[0]) / max(1e-6, b[0] - a[0])
                    k = ease(b[2], u)
                    v = [a[1][j] + (b[1][j] - a[1][j]) * k for j in range(3)]
                    break
        key = ("%.4f" % t).rstrip("0").rstrip(".")
        if "." not in key:
            key += ".0"
        out[key] = {"vector": [round(x, 2) for x in v]}
    return out


def shift(keys, dt, damp=1.0):
    """Atraso (follow-through): mesma curva, um pouco depois e mais fraca."""
    out = [(keys[0][0], keys[0][1], keys[0][2])]
    for t, v, e in keys[1:]:
        out.append((t + dt, [x * damp for x in v], e))
    return out


def one_shot(length, bones):
    anim = {"animation_length": length, "bones": {}}
    for bone, chans in bones.items():
        anim["bones"][bone] = {c: sample(k, length) for c, k in chans.items()}
    return anim


# ------------------------------------------------------------------ loops organicos

def wave(terms):
    """terms: [(amp, ciclos no loop, fase em graus)] -> expressao molang (graus/s inteiros: fecha no loop)."""
    parts = []
    for amp, cyc, ph in terms:
        if abs(amp) < 1e-3:
            continue
        w = int(round(360 * cyc / LOOP))
        if amp < 0:
            amp, ph = -amp, ph + 180
        parts.append(f"math.sin(query.anim_time*{w}+{ph % 360})*{round(amp, 3)}")
    return parts


def loop_pose(base, tense=0.0, energy=1.0):
    """base: {bone: [x,y,z]}; tense = tremor (0 calmo .. 1 tenso); energy = tamanho do balanco."""
    anim = {"loop": True, "animation_length": LOOP, "bones": {}}
    e = energy
    layers = {
        # (eixo, [(amp, ciclos, fase)])
        "waist": {0: [(1.6 * e, 2, 0), (0.6 * e, 3, 40)], 1: [(1.2 * e, 1, 90)], 2: [(0.8 * e, 1, 0)]},
        "head": {0: [(-1.2 * e, 2, 35), (0.5 * e, 5, 10)], 1: [(1.8 * e, 1, 140)], 2: [(0.6 * e, 1, 60)]},
        "right_arm": {0: [(2.2 * e, 2, 20)], 1: [(0.8 * e, 1, 0)], 2: [(1.5 * e, 2, 70)]},
        "left_arm": {0: [(2.2 * e, 2, 200)], 1: [(0.8 * e, 1, 180)], 2: [(-1.5 * e, 2, 250)]},
        "right_leg": {0: [(0.9 * e, 1, 0)], 2: [(0.5 * e, 1, 90)]},
        "left_leg": {0: [(-0.9 * e, 1, 0)], 2: [(-0.5 * e, 1, 90)]},
        "body": {0: [(0.6 * e, 2, 0)]},
    }
    for bone, vec in base.items():
        expr = []
        for ax in range(3):
            terms = list(layers.get(bone, {}).get(ax, []))
            if tense > 0 and bone != "root":
                # tremor: duas frequencias altas fora de fase (nao parece serrote)
                terms += [(1.1 * tense, 20, ax * 50), (0.6 * tense, 33, ax * 80 + 20)]
            if bone == "root":
                terms = [(1.0 * e, 1, 0)] if ax == 0 else []
            parts = wave(terms)
            if not parts:
                expr.append(round(vec[ax], 2))
            else:
                expr.append(f"{round(vec[ax], 2)} + " + " + ".join(parts))
        anim["bones"][bone] = {"rotation": {"0.0": {"vector": expr}, "4.0": {"vector": expr}}}
    return anim


# ------------------------------------------------------------------ golpes

# Eixos medidos no modelo do DMZ (visualizador):
#   waist/root x+ = inclina para FRENTE; waist/head y+ = gira para a DIREITA (ombro direito vai para tras);
#   waist z+ = inclina para a esquerda do personagem; head x- = olha para cima;
#   braco x- = levanta para frente (-90 reto, -170 acima da cabeca); braco direito y+ = abre para fora; braco esquerdo y- = abre para fora;
#   perna x- = para frente;
#   root position z- = passo para FRENTE (pixels, 16 = 1 bloco).
GUARD = {"waist": [6, 12, 0], "head": [-4, -10, 0], "right_arm": [-70, -12, 8], "left_arm": [-84, 10, -6],
         "right_leg": [16, 4, 5], "left_leg": [-12, -4, -5]}


def k(*items):
    return list(items)


def step(*items):
    """Passo do corpo inteiro (root position)."""
    return list(items)


def strikes():
    A = {}
    g = GUARD
    Z = [0, 0, 0]

    # direto de direita: arma o ombro, gira o quadril, passo a frente, braco estica e volta
    A["combo_jab"] = one_shot(0.36, {
        "root": {"position": step((0, Z, "lin"), (0.07, [0, 0, 1], "out"), (0.13, [0, -0.5, -5], "in"), (0.17, [0, -0.5, -6], "out"), (0.36, Z, "io"))},
        "waist": {"rotation": k((0, g["waist"], "lin"), (0.07, [3, 22, 0], "out"), (0.13, [12, -28, 0], "in"), (0.17, [14, -32, 0], "out"), (0.36, g["waist"], "io"))},
        "head": {"rotation": shift(k((0, g["head"], "lin"), (0.07, [-2, -20, 0], "out"), (0.13, [-10, 22, 0], "in"), (0.36, g["head"], "io")), 0.025)},
        "right_arm": {"rotation": k((0, g["right_arm"], "lin"), (0.07, [-52, -4, 16], "out"), (0.13, [-92, -8, 0], "in"),
                                    (0.17, [-95, -9, -1], "out"), (0.36, g["right_arm"], "io"))},
        "left_arm": {"rotation": k((0, g["left_arm"], "lin"), (0.13, [-68, 22, -10], "out"), (0.36, g["left_arm"], "io"))},
        "right_leg": {"rotation": k((0, g["right_leg"], "lin"), (0.13, [26, 4, 5], "out"), (0.36, g["right_leg"], "io"))},
        "left_leg": {"rotation": k((0, g["left_leg"], "lin"), (0.13, [-20, -4, -5], "out"), (0.36, g["left_leg"], "io"))},
    })

    # gancho de esquerda: abre o braco por fora e fecha cruzando, quadril gira para a direita
    A["combo_hook"] = one_shot(0.38, {
        "root": {"position": step((0, Z, "lin"), (0.08, [0, 0, 1], "out"), (0.15, [0, -0.5, -4], "in"), (0.19, [0, -0.5, -4.5], "out"), (0.38, Z, "io"))},
        "waist": {"rotation": k((0, g["waist"], "lin"), (0.08, [4, -24, -4], "out"), (0.15, [10, 34, 6], "in"), (0.19, [11, 39, 7], "out"), (0.38, g["waist"], "io"))},
        "head": {"rotation": shift(k((0, g["head"], "lin"), (0.08, [-2, 18, 0], "out"), (0.15, [-8, -26, 0], "in"), (0.38, g["head"], "io")), 0.025)},
        "left_arm": {"rotation": k((0, g["left_arm"], "lin"), (0.08, [-70, -42, -28], "out"), (0.15, [-88, 36, -8], "in"),
                                   (0.19, [-89, 44, -6], "out"), (0.38, g["left_arm"], "io"))},
        "right_arm": {"rotation": k((0, g["right_arm"], "lin"), (0.15, [-76, -20, 8], "out"), (0.38, g["right_arm"], "io"))},
        "right_leg": {"rotation": k((0, g["right_leg"], "lin"), (0.15, [20, 10, 6], "out"), (0.38, g["right_leg"], "io"))},
        "left_leg": {"rotation": k((0, g["left_leg"], "lin"), (0.15, [-16, -10, -6], "out"), (0.38, g["left_leg"], "io"))},
    })

    # chute frontal alto de direita: recolhe a perna, estica, tronco inclina para tras para equilibrar
    A["combo_kick"] = one_shot(0.52, {
        "root": {"position": step((0, Z, "lin"), (0.19, [0, 0.5, -3], "in"), (0.36, [0, 0, -2], "io"), (0.52, Z, "io"))},
        "waist": {"rotation": k((0, g["waist"], "lin"), (0.1, [-4, 0, 0], "out"), (0.19, [-16, -14, 8], "in"), (0.24, [-18, -16, 9], "out"),
                                (0.36, [-6, -4, 3], "io"), (0.52, g["waist"], "io"))},
        "head": {"rotation": shift(k((0, g["head"], "lin"), (0.19, [10, 12, -6], "in"), (0.52, g["head"], "io")), 0.035)},
        "right_leg": {"rotation": k((0, g["right_leg"], "lin"), (0.1, [-62, 0, 6], "out"), (0.19, [-102, 0, 8], "in"),
                                    (0.24, [-108, 0, 9], "out"), (0.36, [-46, 0, 6], "io"), (0.52, g["right_leg"], "io"))},
        "left_leg": {"rotation": k((0, g["left_leg"], "lin"), (0.19, [6, 0, -4], "out"), (0.52, g["left_leg"], "io"))},
        "right_arm": {"rotation": k((0, g["right_arm"], "lin"), (0.19, [-30, 10, 46], "out"), (0.36, [-50, 0, 24], "io"), (0.52, g["right_arm"], "io"))},
        "left_arm": {"rotation": k((0, g["left_arm"], "lin"), (0.19, [-50, -10, -40], "out"), (0.52, g["left_arm"], "io"))},
    })

    # martelo com as duas maos: sobe arqueando para tras, desce dobrando o corpo para frente
    A["double_axe"] = one_shot(0.64, {
        "root": {"position": step((0, Z, "lin"), (0.2, [0, 1, 1], "out"), (0.36, [0, -1.5, -5], "in"), (0.41, [0, -2, -6], "out"), (0.64, [0, 0, -2], "io"))},
        "waist": {"rotation": k((0, [4, 0, 0], "lin"), (0.2, [-10, 0, 0], "out"), (0.27, [-11, 0, 0], "lin"),
                                (0.36, [32, 0, 0], "in"), (0.41, [37, 0, 0], "out"), (0.64, [14, 0, 0], "io"))},
        "head": {"rotation": shift(k((0, [0, 0, 0], "lin"), (0.2, [-12, 0, 0], "out"), (0.36, [-8, 0, 0], "in"), (0.64, [-6, 0, 0], "io")), 0.03)},
        "right_arm": {"rotation": k((0, [-60, -6, 6], "lin"), (0.2, [-168, -12, 0], "back"), (0.27, [-172, -12, 0], "lin"),
                                    (0.36, [-40, -14, 0], "in"), (0.41, [-30, -14, 0], "out"), (0.64, [-56, -10, 4], "io"))},
        "left_arm": {"rotation": k((0, [-60, 6, -6], "lin"), (0.2, [-168, 12, 0], "back"), (0.27, [-172, 12, 0], "lin"),
                                   (0.36, [-40, 14, 0], "in"), (0.41, [-30, 14, 0], "out"), (0.64, [-56, 10, -4], "io"))},
        "right_leg": {"rotation": k((0, [0, 0, 2], "lin"), (0.2, [-12, 0, 4], "out"), (0.36, [16, 0, 2], "in"), (0.64, [6, 0, 2], "io"))},
        "left_leg": {"rotation": k((0, [0, 0, -2], "lin"), (0.2, [-6, 0, -4], "out"), (0.36, [20, 0, -2], "in"), (0.64, [4, 0, -2], "io"))},
    })

    # esquivas: corpo inclina para o lado (waist z), bracos abrem para equilibrar, volta amortecendo
    for name, s in (("dodge_left", 1), ("dodge_right", -1)):
        A[name] = one_shot(0.56, {
            "waist": {"rotation": k((0, g["waist"], "lin"), (0.09, [-6, 8 * s, 24 * s], "out"), (0.2, [-4, 6 * s, 18 * s], "lin"),
                                    (0.34, [4, -2 * s, -4 * s], "io"), (0.56, g["waist"], "out"))},
            "head": {"rotation": shift(k((0, g["head"], "lin"), (0.09, [0, -6 * s, -18 * s], "out"), (0.34, [-4, 0, 4 * s], "io"),
                                         (0.56, g["head"], "out")), 0.03)},
            "right_arm": {"rotation": k((0, g["right_arm"], "lin"), (0.09, [-34, 8, 30 if s > 0 else 52], "out"),
                                        (0.34, [-58, -6, 14], "io"), (0.56, g["right_arm"], "out"))},
            "left_arm": {"rotation": k((0, g["left_arm"], "lin"), (0.09, [-34, -8, -52 if s > 0 else -30], "out"),
                                       (0.34, [-72, 6, -12], "io"), (0.56, g["left_arm"], "out"))},
            "right_leg": {"rotation": k((0, g["right_leg"], "lin"), (0.09, [10, 0, 6 + 14 * (1 if s < 0 else 0)], "out"),
                                        (0.34, [12, 2, 5], "io"), (0.56, g["right_leg"], "out"))},
            "left_leg": {"rotation": k((0, g["left_leg"], "lin"), (0.09, [-6, 0, -6 - 14 * (1 if s > 0 else 0)], "out"),
                                       (0.34, [-10, -2, -5], "io"), (0.56, g["left_leg"], "out"))},
        })

    # rebater: mao cruza na frente do corpo e varre para fora e para cima (costas da mao)
    A["deflect"] = one_shot(0.46, {
        "waist": {"rotation": k((0, g["waist"], "lin"), (0.07, [6, -18, 0], "out"), (0.16, [2, 24, 0], "in"), (0.21, [1, 28, 0], "out"), (0.46, g["waist"], "io"))},
        "head": {"rotation": shift(k((0, g["head"], "lin"), (0.16, [-12, -16, 0], "in"), (0.46, g["head"], "io")), 0.03)},
        "right_arm": {"rotation": k((0, g["right_arm"], "lin"), (0.07, [-80, -42, 0], "out"), (0.16, [-140, 30, 32], "in"),
                                    (0.21, [-146, 36, 38], "out"), (0.46, g["right_arm"], "io"))},
        "left_arm": {"rotation": k((0, g["left_arm"], "lin"), (0.16, [-60, 6, -24], "out"), (0.46, g["left_arm"], "io"))},
        "right_leg": {"rotation": k((0, g["right_leg"], "lin"), (0.16, [20, 8, 6], "out"), (0.46, g["right_leg"], "io"))},
        "left_leg": {"rotation": k((0, g["left_leg"], "lin"), (0.16, [-16, -6, -6], "out"), (0.46, g["left_leg"], "io"))},
    })

    # atingido no estomago: empurrado para tras, dobra para frente, volta amortecendo
    A["hit_recoil"] = one_shot(0.7, {
        "root": {"position": step((0, Z, "lin"), (0.06, [0, 0, 5], "out"), (0.2, [0, 0, 6], "out"), (0.7, Z, "io"))},
        "waist": {"rotation": k((0, g["waist"], "lin"), (0.05, [30, 0, 4], "out"), (0.12, [36, 0, 6], "out"), (0.32, [20, 0, 2], "io"), (0.7, g["waist"], "out"))},
        "head": {"rotation": shift(k((0, g["head"], "lin"), (0.05, [-20, 0, -6], "out"), (0.14, [18, 0, -8], "out"), (0.7, g["head"], "out")), 0.03)},
        "right_arm": {"rotation": k((0, g["right_arm"], "lin"), (0.05, [-40, 6, 42], "out"), (0.14, [-34, 6, 48], "out"), (0.7, g["right_arm"], "out"))},
        "left_arm": {"rotation": k((0, g["left_arm"], "lin"), (0.05, [-40, -6, -42], "out"), (0.14, [-34, -6, -48], "out"), (0.7, g["left_arm"], "out"))},
        "right_leg": {"rotation": k((0, g["right_leg"], "lin"), (0.06, [-24, 0, 8], "out"), (0.7, g["right_leg"], "out"))},
        "left_leg": {"rotation": k((0, g["left_leg"], "lin"), (0.06, [-14, 0, -8], "out"), (0.7, g["left_leg"], "out"))},
    })
    return A


# ------------------------------------------------------------------ loops

TENSE = {"clench": 0.8, "scream": 1.0, "tremble": 1.4, "roar_up": 1.0, "hunch_roar": 1.0, "head_pain": 0.9, "claw": 0.6,
         "flex": 0.5, "fist_up": 0.4, "kame_charge": 0.6, "kame_fire": 1.2, "blown_back": 0.3, "crouch": 0.3}
CALM = {"calm": 0.6, "meditate": 0.4, "arms_folded": 0.6, "look_hand": 0.7, "palms": 0.8, "float_spread": 1.2, "point": 0.7}


def base_of(anim):
    """Le a base (numero antes do ' + math') de cada osso de uma pose antiga."""
    out = {}
    for bone, ch in anim.get("bones", {}).items():
        rot = ch.get("rotation")
        if not rot:
            continue
        first = rot[sorted(rot, key=float)[0]]["vector"]
        vec = []
        for x in first:
            if isinstance(x, (int, float)):
                vec.append(float(x))
            else:
                vec.append(float(str(x).split("+")[0].strip() or 0))
        out[bone] = vec
    return out


QTE_LOOPS = {
    # maos em concha no quadril direito: ombro direito para tras, mao esquerda cruza o corpo
    "kame_charge": {"waist": [4, 30, 0], "head": [-4, -26, 0], "right_arm": [-26, -10, 8], "left_arm": [-52, 46, 0],
                    "right_leg": [18, 0, 10], "left_leg": [-14, 0, -10]},
    # bracos retos para frente, maos juntas
    "kame_fire": {"waist": [8, -6, 0], "head": [-6, 6, 0], "right_arm": [-90, -14, 0], "left_arm": [-90, 14, 0],
                  "right_leg": [22, 0, 8], "left_leg": [-16, 0, -8]},
    "guard_ready": dict(GUARD),
    "rush": {"root": [62, 0, 0], "head": [-48, 0, 0], "right_arm": [42, 0, 14], "left_arm": [42, 0, -14],
             "right_leg": [14, 0, 4], "left_leg": [26, 0, -4], "waist": [4, 0, 0]},
}

if __name__ == "__main__":
    path = sys.argv[1]
    d = json.load(open(path))
    anims = d["animations"]
    one = strikes()
    names = [n[len(P):] for n in anims]
    for name in names:
        if name in one:
            continue
        base = QTE_LOOPS.get(name) or base_of(anims[P + name])
        tense = TENSE.get(name, 0.0)
        energy = CALM.get(name, 1.0) * (1.6 if name == "rush" else 1.0)
        old = anims[P + name]
        new = loop_pose(base, tense, energy)
        for bone, ch in old.get("bones", {}).items():
            pos = ch.get("position")
            if pos:
                first = pos[sorted(pos, key=float)[0]]["vector"]
                new["bones"].setdefault(bone, {})["position"] = {"0.0": {"vector": first}, "4.0": {"vector": first}}
        anims[P + name] = new
    for name, a in one.items():
        anims[P + name] = a
    json.dump(d, open(path, "w"), indent=1)
    frames = sum(len(c) for a in one.values() for b in a["bones"].values() for c in b.values())
    print(len(anims), "poses;", len(one), "golpes com", frames, "quadros-chave")
