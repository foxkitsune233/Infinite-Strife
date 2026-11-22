# 剑圣与 PA 普攻动作 v2 审阅稿

日期：2026-10-18。使用内置 imagegen 编辑，透明 PNG；未接入战斗播放。

## 素材

- `juggernaut-attack-sequence-v2-review.png`：1254×1254，双剑长度修订与四个动作关键帧。
- `phantom-assassin-attack-sequence-v2-review.png`：1226×1283，双手交替挥砍，明确手掌—握柄—护手—刀身关系。
- 顺序均为左上待机、右上蓄力中间帧、左下命中、右下收势中间帧，再回到待机。

## 接入前限制

这两张是动作审阅整图，不是可直接按等宽等高切分的生产图集。部分武器进入中央留白，人物落脚点与形体仍有帧间偏差。需要逐帧分离、统一画布和脚底锚点，并检查武器刚性尺寸后才可接入。不可直接将此稿接入旧 `hero-animation-manifest.json`。

动画只表现已有战斗事件；两把武器的视觉挥动不额外生成攻击、伤害或卡牌触发。保留原版资源作为对照。

## 最终生成提示词

### 剑圣

```text
Use case: precise-object-edit. Edit the supplied 2x2 four-frame animation atlas, preserve the exact masked orange-scarf hero design, four poses, inked angular cutout style.
This is a TECHNICAL SPRITE CORRECTION, not a new illustration.
1. Give ALL FOUR sprites much more padding: in EACH quadrant keep EVERY pixel at least 12% of that cell width/height away from ALL FOUR cell edges, including the center dividers. SHRINK each entire sprite to 70% of its current size, do not crop any sword or scarf. Keep each sprite on the same relative foot baseline and body scale in each equal quadrant.
2. Normalize both long saber blades to ONE identical rigid shape and length in EVERY frame. The blade from guard to tip must equal 60% of the hero's head-to-sole height, not counting hair. Make the ready-pose swords longer. Shorten the excessive overhead sword in upper right to the SAME length as the ready-pose blades. All eight blades represent the same two equal-length long curved sabers, same guard, same tapered ivory shape, same consistent curve; no short machete in one frame and long katana in another.
3. Refine motion continuity: row-major order is READY, WIND-UP, RIGHTWARD CONTACT, RECOVERY. Same feet planted, same 3/4 camera and fixed anatomical hands. Wind-up raises swords behind/above the shoulder; contact swings them together diagonally to screen right; recovery follows through down-right before return to ready. No spinning or mirroring.
4. Exactly equal 2x2 cells. Every entire sprite fits its own cell with ample transparent padding; no overlaps. Background is genuinely alpha transparent. No text, labels, frame lines, trails, shadows or VFX. Output square sprite atlas.
```

### PA（包含右手及双手动作修正）

```text
Use case: precise-object-edit. Create a corrected four-frame 2x2 animation atlas of the exact original hooded assassin in the reference. Preserve the navy hood, silver hair, violet-black armor, silver plates, teal crescent daggers, angular inked paper-cut artwork.
The last attempt fused the RIGHT hand to its dagger and froze the rear hand. Correct BOTH issues.
ANATOMY: exactly TWO arms and TWO hands. Both hands clearly visible in every frame. Each hand is a recognizable dark gloved fist with separate thumb and wrapped fingers visibly holding a short BLACK GRIP. The SILVER GUARD is beyond the fist. The TEAL BLADE begins beyond the guard, NEVER from the wrist or forearm. At least a small visible black hilt segment and silver guard separate hand from teal blade. No blade through knuckles, no teal forearm, no fused weapon-arm, no hidden malformed hand.
WEAPONS: the exact same pair of short teal crescent daggers with fixed length and shape in EVERY frame. Both in conventional forward grip; no flipping or switching hands. Each blade approximately forearm length. Clear negative space around BOTH fists and blade silhouettes.
MOTION: ONE continuous coordinated TWO-HANDED alternating double-slash toward screen right. Anatomical right arm is on screen LEFT; anatomical left arm is screen RIGHT. BOTH arms move through the sequence.
Frame 1 TOP LEFT READY: bent knees, both hands low beside waist, dagger tips angled outward/down, palms and grips visibly distinct.
Frame 2 TOP RIGHT FIRST INTERMEDIATE/WIND-UP: both elbows bend, screen-left hand draws up toward left shoulder with blade up-left, screen-right hand rises forward with blade up-right, both weapons preparing the swing.
Frame 3 BOTTOM LEFT CONTACT: screen-right arm extends down-right in first slash, screen-left arm has ALSO begun its slash, moving forward across upper chest with dagger pointing right, both complete fists/grips clearly exposed and NOT crossing or overlapping.
Frame 4 BOTTOM RIGHT SECOND INTERMEDIATE/FOLLOW-THROUGH: screen-left arm finishes second slash down-right; screen-right arm retracts toward waist. Both arms then naturally return to frame 1. Clearly different arm positions from frames 1 and 2.
Lock identical feet positions, foot baseline, root anchor, body/head scale, 3/4 right-facing camera throughout. Small torso rotation only. Entire full-body sprites, two hands and both dagger tips fit in each equal quadrant with at least 15% blank transparent margin. Shrink sprites to fit. No overlaps across cell borders. Transparent background, no background glow, vignette, floor, shadows, text, numbers, labels, frame lines, extra limbs, or motion trails.
```
