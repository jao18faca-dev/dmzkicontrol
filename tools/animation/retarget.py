"""Converte captura de movimento (BVH) para o boneco do DMZ (GeckoLib).

Usa posicoes das juntas (robusto a pose de repouso diferente): tronco pelo quadril->pescoco e linha dos ombros,
bracos pelo ombro->mao, pernas pelo quadril->tornozelo, giro do quadril no root, cabeca pelas rotacoes locais.

Uso como modulo:
    clip = load("arquivo.bvh", "cmu")
    anim = to_anim(clip, t0, t1, fps=60, mirror=False)
"""
import math
import re

import numpy as np
from scipy.ndimage import gaussian_filter1d
from scipy.spatial.transform import Rotation as Rot

MAPS = {
    "cmu": dict(hips="Hips", lhip="LeftUpLeg", rhip="RightUpLeg", lknee="LeftLeg", rknee="RightLeg", lfoot="LeftFoot",
                rfoot="RightFoot", neck="Neck", head=["Neck", "Neck1", "Head"], lsho="LeftArm", rsho="RightArm",
                lhand="LeftHand", rhand="RightHand"),
    "bandai": dict(hips="Hips", lhip="UpperLeg_L", rhip="UpperLeg_R", lknee="LowerLeg_L", rknee="LowerLeg_R", lfoot="Foot_L",
                   rfoot="Foot_R", neck="Neck", head=["Neck", "Head"], lsho="UpperArm_L", rsho="UpperArm_R",
                   lhand="Hand_L", rhand="Hand_R"),
}


# ------------------------------------------------------------------ BVH

class Joint:
    def __init__(self, name, parent):
        self.name, self.parent = name, parent
        self.offset = np.zeros(3)
        self.channels = []
        self.children = []


def parse(path):
    txt = open(path).read()
    head, motion = txt.split("MOTION")
    tokens = re.findall(r"[^\s{}]+|[{}]", head)
    joints, stack, order = {}, [], []
    i = 0
    cur = None
    while i < len(tokens):
        t = tokens[i]
        if t in ("ROOT", "JOINT"):
            cur = Joint(tokens[i + 1], stack[-1] if stack else None)
            if cur.parent:
                cur.parent.children.append(cur)
            joints[cur.name] = cur
            order.append(cur)
            i += 2
        elif t == "End":
            cur = Joint(stack[-1].name + "_end", stack[-1])
            stack[-1].children.append(cur)
            i += 2
        elif t == "{":
            stack.append(cur)
            i += 1
        elif t == "}":
            stack.pop()
            i += 1
        elif t == "OFFSET":
            cur.offset = np.array([float(x) for x in tokens[i + 1:i + 4]])
            i += 4
        elif t == "CHANNELS":
            n = int(tokens[i + 1])
            cur.channels = tokens[i + 2:i + 2 + n]
            i += 2 + n
        else:
            i += 1
    lines = motion.strip().splitlines()
    nframes = int(lines[0].split(":")[1])
    dt = float(lines[1].split(":")[1])
    data = np.array([[float(x) for x in l.split()] for l in lines[2:2 + nframes]])
    return joints, order, data, dt


def fk(joints, order, data):
    """Posicoes globais e rotacoes locais de cada junta por quadro."""
    n = data.shape[0]
    pos, glob, loc = {}, {}, {}
    col = 0
    for j in order:
        trans = np.tile(j.offset, (n, 1))
        R = np.tile(np.eye(3), (n, 1, 1))
        for ch in j.channels:
            v = data[:, col]
            col += 1
            if ch.endswith("position"):
                trans[:, "XYZ".index(ch[0])] = v + (0 if j.parent else 0)
            else:
                axis = ch[0].lower()
                R = R @ Rot.from_euler(axis, v[:, None], degrees=True).as_matrix()
        if j.parent is None and not any(c.endswith("position") for c in j.channels):
            trans = np.tile(j.offset, (n, 1))
        loc[j.name] = R
        if j.parent is None:
            glob[j.name] = R
            pos[j.name] = trans
        else:
            glob[j.name] = glob[j.parent.name] @ R
            pos[j.name] = pos[j.parent.name] + np.einsum("nij,nj->ni", glob[j.parent.name], trans)
    return pos, glob, loc


class Clip:
    def __init__(self, path, kind):
        self.kind = kind
        joints, order, data, dt = parse(path)
        self.dt = dt
        self.pos, self.glob, self.loc = fk(joints, order, data)
        self.m = MAPS[kind]
        self.n = data.shape[0]
        m = self.m
        self.leg = np.linalg.norm(joints[m["lknee"]].offset) + np.linalg.norm(joints[m["lfoot"]].offset)

    def P(self, key):
        return self.pos[self.m[key]]


def load(path, kind):
    return Clip(path, kind)


# ------------------------------------------------------------------ retarget

def _unit(v):
    return v / np.maximum(np.linalg.norm(v, axis=-1, keepdims=True), 1e-9)


def _limb(d):
    """Direcao (espaco do modelo three) -> angulos (a, c) com y = 0, escolhendo o menor giro lateral."""
    dx, dy, dz = d
    a1 = math.asin(max(-1, min(1, -dz)))
    ca = math.cos(a1)
    c1 = math.atan2(dx / max(ca, 1e-6), -dy / max(ca, 1e-6))
    a2 = math.pi - a1
    c2 = math.atan2(dx / -max(ca, 1e-6), -dy / -max(ca, 1e-6))
    return (a1, c1) if abs(c1) <= abs(c2) else (a2, c2)


def _unwrap(seq):
    out = np.degrees(np.unwrap(np.radians(np.array(seq)), axis=0))
    return out


def to_anim(clip, t0, t1, fps=60, mirror=False, smooth=1.2, root_pos=True, yaw_limit=50, step_scale=1.0, exag=1.0, speed=1.0, yaw_scale=1.0):
    i0 = max(0, int(t0 / clip.dt))
    i1 = min(clip.n - 1, int(t1 / clip.dt))
    idx = np.arange(i0, i1 + 1)
    sig = smooth * (1 / 120) / clip.dt

    def S(key):
        p = clip.P(key)[idx]
        return gaussian_filter1d(p, sig, axis=0) if sig > 0 else p

    L, R = ("l", "r") if not mirror else ("r", "l")
    hips, lhip, rhip = S("hips"), S(L + "hip"), S(R + "hip")
    lsho, rsho, lhand, rhand = S(L + "sho"), S(R + "sho"), S(L + "hand"), S(R + "hand")
    lfoot, rfoot, neck = S(L + "foot"), S(R + "foot"), S("neck")
    up = np.array([0, 1.0, 0])

    def horiz(v):
        v = v.copy()
        v[..., 1] = 0
        return _unit(v)

    # referencia: para onde o lutador esta virado na media do trecho
    hr = horiz(rhip - lhip)
    sr = horiz(rsho - lsho)
    r0 = _unit(np.mean(hr + sr, axis=0))
    r0[1] = 0
    r0 = _unit(r0)
    f0 = np.cross(up, r0)
    sx = -1 if mirror else 1  # espelhar: troca lados (L/R ja trocados) e inverte x

    def model(v):
        x = v @ r0
        y = v[..., 1]
        z = -(v @ f0)
        return np.stack([x * sx, y, z], axis=-1)

    out = {b: [] for b in ("root_rot", "root_pos", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg")}
    times = np.arange(0, (i1 - i0) * clip.dt + 1e-9, speed / fps)
    base_h = model(hips[0:1])[0]
    s_px = 12.0 / clip.leg
    head_local = None
    for t in times:
        k = min(len(idx) - 1, int(round(t / clip.dt)))
        # root: so o giro (yaw) do quadril
        h = model((rhip[k] - lhip[k])[None])[0]
        h[1] = 0
        yaw = math.atan2(-h[2], h[0])
        yaw = max(-math.radians(yaw_limit), min(math.radians(yaw_limit), yaw))
        Rr = Rot.from_euler("y", yaw).as_matrix()
        out["root_rot"].append([0, -math.degrees(yaw), 0])
        if root_pos:
            d = (model(hips[k:k + 1])[0] - base_h) * s_px
            out["root_pos"].append([-d[0] * step_scale, d[1], d[2] * step_scale])
        # tronco
        up_t = _unit(model((neck[k] - hips[k])[None])[0])
        rt = model((rsho[k] - lsho[k])[None])[0]
        rt = _unit(rt - up_t * (rt @ up_t))
        back = np.cross(rt, up_t)
        Mt = np.stack([rt, up_t, back], axis=1)
        Rw = Rr.T @ Mt
        z, y, x = Rot.from_matrix(Rw).as_euler("ZYX")
        out["waist"].append([-math.degrees(x), -math.degrees(y), math.degrees(z)])
        Rup = Rr @ Rw
        # bracos (ombro -> mao) no espaco do tronco
        for side, sh, hd in (("right_arm", rsho, rhand), ("left_arm", lsho, lhand)):
            d = Rup.T @ _unit(model((hd[k] - sh[k])[None])[0])
            a, c = _limb(d)
            out[side].append([-math.degrees(a), 0, math.degrees(c)])
        # pernas (quadril -> tornozelo) no espaco do root
        for side, hp, ft in (("right_leg", rhip, rfoot), ("left_leg", lhip, lfoot)):
            d = Rr.T @ _unit(model((ft[k] - hp[k])[None])[0])
            a, c = _limb(d)
            out[side].append([-math.degrees(a), 0, math.degrees(c)])
        # cabeca: rotacoes locais do pescoco+cabeca (repouso de pe, olhando +Z no BVH)
        Rh = np.eye(3)
        for name in clip.m["head"]:
            if name in clip.loc:
                Rh = Rh @ clip.loc[name][idx[k]]
        P = np.diag([-1.0, 1.0, -1.0])
        Mh = P @ Rh @ P.T
        if mirror:
            Mx = np.diag([-1.0, 1, 1])
            Mh = Mx @ Mh @ Mx
        z, y, x = Rot.from_matrix(Mh).as_euler("ZYX")
        out["head"].append([max(-45, min(45, -math.degrees(x))), max(-60, min(60, -math.degrees(y))), max(-30, min(30, math.degrees(z)))])
    for kname in out:
        if out[kname]:
            arr = _unwrap(out[kname])
            if kname == "root_rot":
                arr = arr * yaw_scale
            elif exag != 1.0:
                arr = arr * exag
            out[kname] = arr.tolist()
    return times / speed, out


def anim_json(times, out, loop=False, hold_end=0.0):
    def key(t):
        s = ("%.4f" % t).rstrip("0").rstrip(".")
        return s if "." in s else s + ".0"

    length = float(times[-1]) + hold_end
    bones = {}
    chans = {"root": [("rotation", "root_rot"), ("position", "root_pos")], "waist": [("rotation", "waist")], "head": [("rotation", "head")],
             "right_arm": [("rotation", "right_arm")], "left_arm": [("rotation", "left_arm")],
             "right_leg": [("rotation", "right_leg")], "left_leg": [("rotation", "left_leg")]}
    for bone, lst in chans.items():
        b = {}
        for ch, src in lst:
            if not out.get(src):
                continue
            b[ch] = {key(t): {"vector": [round(v, 2) for v in out[src][i]]} for i, t in enumerate(times)}
        if b:
            bones[bone] = b
    a = {"animation_length": round(length, 4), "bones": bones}
    if loop:
        a["loop"] = True
    return a
