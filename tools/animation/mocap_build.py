import sys, json
sys.path.insert(0, '/tmp/claude-0/-home-claude/c75d1bbe-d433-52dd-b5fe-9aaa2a816762/scratchpad/scripts')
from retarget import load, to_anim, anim_json
C='/home/claude/una-dinosauria/cmu-mocap/data/'
B='/home/claude/bandainamcoresearchinc/bandai-namco-research-motiondataset/dataset/Bandai-Namco-Research-Motiondataset-1/data/'
# nome: (arquivo, tipo, inicio, fim, espelhar)
CLIPS = {
 "mc_cross_cmu143": (C+'143/143_23.bvh','cmu',0.85,1.65,False),
 "mc_lpunch_cmu143": (C+'143/143_23.bvh','cmu',1.55,2.35,False),
 "mc_punch_bandai": (B+'dataset-1_punch_normal_002.bvh','bandai',1.4,2.3,False),
 "mc_box_right": (C+'014/14_01.bvh','cmu',1.05,1.8,False),
 "mc_box_left": (C+'014/14_01.bvh','cmu',2.05,2.8,False),
 "mc_oiduki": (C+'135/135_09.bvh','cmu',1.8,3.1,False),
 "mc_front_kick": (C+'135/135_04.bvh','cmu',2.4,3.9,False),
 "mc_roundhouse": (C+'135/135_07.bvh','cmu',0.3,1.8,False),
 "mc_side_kick": (C+'135/135_11.bvh','cmu',2.2,3.7,False),
 "mc_kick_bandai": (B+'dataset-1_kick_normal_001.bvh','bandai',1.3,2.8,False),
 "mc_duck": (C+'077/77_09.bvh','cmu',3.6,5.0,False),
 "mc_block": (C+'144/144_26.bvh','cmu',3.1,4.1,False),
 # golpes do combo (recortados em volta do impacto)
 "combo_jab": (C+'143/143_23.bvh','cmu',0.92,1.45,False, dict(exag=1.25, speed=1.15, yaw_scale=0.6)),
 "combo_hook": (C+'014/14_01.bvh','cmu',2.1,2.65,False, dict(exag=1.25, speed=1.15, yaw_scale=0.6)),
 "combo_kick": (C+'135/135_07.bvh','cmu',0.5,1.45,False, dict(exag=1.1, speed=1.2, yaw_scale=0.5)),
 "deflect": (C+'144/144_26.bvh','cmu',3.28,3.9,False),
}
sel = sys.argv[2:] or list(CLIPS)
out = {"format_version": "1.8.0", "animations": {}}
for name in sel:
    f, k, t0, t1, mir, *opt = CLIPS[name]
    c = load(f, k)
    times, o = to_anim(c, t0, t1, fps=60, mirror=mir, **(opt[0] if opt else {}))
    if name.startswith("combo_"):
        o["head"] = [[max(-25, min(25, v[0])), v[1], v[2]] for v in o["head"]]
    out["animations"]["dmzkicontrol.cs." + name] = anim_json(times, o)
    print(name, len(times), 'quadros')
json.dump(out, open(sys.argv[1], 'w'))
