# 装备配方核验记录（已核验组件关系）

2026-10-17。本轮29个中间/主要装备配方已按实际数据核对并接入，另保留原有护腕/系带/挂件3配方。价格、属性、尺寸、触发概率与CD采用本游戏首轮值，不声称照搬原作平衡。

## 来源和证据边界

- 一级来源：[Valve物品列表（中文）](https://www.dota2.com/datafeed/itemlist?language=schinese)、[英文](https://www.dota2.com/datafeed/itemlist?language=english)。读取`item_recipe_*`的`recipes[].items[]`，再按物品ID映射组件，使用真实配方字段，未从页脚索引猜组件。
- 本地快照：[中文原始JSON](../../.firecrawl/valve-itemlist-zh.json)、[英文原始JSON](../../.firecrawl/valve-itemlist-en.json)。快照获取日为2026-10-17；接口不提供补丁版本，不能冒称完整配方绑定7.41某个小版本。
- 卷轴是否单独消耗：辅以[SteamTracking脚本快照](https://github.com/SteamDatabase/GameTracking-Dota2/blob/3f630cc18bfa63f63b3ce259be52209d922558c3/game/dota/scripts/npc/items.txt)中相应`item_recipe_*`的`ItemCost>0`。这是固定提交的分发脚本镜像，**不是Valve官网**；镜像提交日期2026-10-07。本地原文为[items.txt](../../.firecrawl/dota-items.txt)。物价不采用镜像金额。
- Liquipedia此前仅能取得部分页面/索引，未以其作为本轮已核验的证据。原文档“全部待核验”现由本记录替代。

## 29个配方

下表均已核对组件ID。卷轴表示每次合成都消耗一份；无卷轴者不额外发明配方卷轴。

| 装备（Valve中文名） | 原作ID | 本轮核验组件 |
| --- | --- | --- |
| 先锋盾 | vanguard | 活力之球 + 治疗指环 |
| 空明杖 | oblivion_staff | 闪电指套 + 法师长袍 + 贤者面罩 |
| 振魂石 | soul_booster | 精气之球 + 活力之球 + 能量之球 |
| 魔龙枪 | dragon_lance | 欢欣之刃 + 力量腰带 + 卷轴 |
| 行家阵列 | specialists_array | 欢欣之刃 + 阔剑 + 卷轴 |
| 疯狂面具 | mask_of_madness | 阔剑 + 吸血面具 |
| 漩涡 | maelstrom | 秘银锤 + 标枪 + 加速手套 |
| 雷神之锤 | mjollnir | 漩涡 + 振奋宝石 + 卷轴 |
| 慧光 | kaya | 魔力法杖 + 法师长袍 + 卷轴 |
| 散华 | sange | 食人魔之斧 + 力量腰带 + 卷轴 |
| 夜叉 | yasha | 欢欣之刃 + 精灵布带 + 卷轴 |
| 散夜对剑 | sange_and_yasha | 散华 + 夜叉 |
| 散慧对剑 | kaya_and_sange | 慧光 + 散华 |
| 慧夜对剑 | yasha_and_kaya | 夜叉 + 慧光 |
| 玲珑心 | octarine_core | 振魂石 + 赛莉蒙妮之冠 + 卷轴 |
| 水晶剑 | lesser_crit | 大剑 + 攻击之爪 + 卷轴 |
| 代达罗斯之殇 | greater_crit | 水晶剑 + 恶魔刀锋 + 卷轴 |
| 赤红甲 | crimson_guard | 先锋盾 + 铁意头盔 + 卷轴 |
| 洞察烟斗 | pipe | 恐鳌之戒 + 抗魔斗篷 + 披巾 + 卷轴 |
| 莫尔迪基安的臂章 | armlet | 铁意头盔 + 加速手套 + 攻击之爪 + 卷轴 |
| 恐鳌之心 | heart | 掠夺者之斧 + 恐鳌之戒 + 卷轴 |
| 撒旦之邪力 | satanic | 掠夺者之斧 + 大剑 + 吸血面具 |
| 蝴蝶 | butterfly | 鹰歌弓 + 大剑 + 闪避护符 |
| 巫师之刃 | witch_blade | 空明杖 + 锁子甲 + 淬毒之珠 + 卷轴 |
| 圣斧 | devastator（Parasma） | 巫师之刃 + 神秘法杖 + 卷轴 |
| 怪蛇之息 | hydras_breath（Hydra’s Breath） | 行家阵列 + 魔龙枪 + 淬毒之珠 + 卷轴 |
| 原力法杖 | force_staff | 魔力法杖 + 毛毛帽 + 卷轴 |
| 飓风长戟 | hurricane_pike | 原力法杖 + 魔龙枪 + 卷轴 |
| 黑皇杖 | black_king_bar | 秘银锤 + 食人魔之斧 + 卷轴 |

## 已修正的内部映射与兼容别名

已有ID保留，避免物品实例、材料与存档失效；下列ID仅是本游戏历史兼容ID，不能把它们直接发送给原作接口：

| 本游戏ID | 实际对应 | 处理 |
| --- | --- | --- |
| ring_of_health | 治疗指环 | 修正原先误写为回复戒指的显示名，先锋盾改消耗此件 |
| ring_of_regen | 回复戒指 | 修正原先误写为治疗指环的显示名 |
| quarterstaff | blitz_knuckles／闪电指套 | 兼容别名，不冒充短棍 |
| voodoo_mask | ring_of_tarrasque／恐鳌之戒 | 兼容别名，不冒充法术吸血面具 |
| perseverance | tiara_of_selemene／赛莉蒙妮之冠 | 更正显示名，仍作为可获取组件，无下级配方 |
| rod_of_atos | devastator／Parasma／圣斧 | 兼容别名，与阿托斯之棍无关 |
| viper_bite | hydras_breath／怪蛇之息 | 兼容别名 |
| expert_array | specialists_array／行家阵列 | 本游戏保留用户使用的“专家阵列”显示名 |

本轮主程序将全部组件、卷轴、5中间装备及24主要装备加入实际物品目录，合成统一走现有局外事务。卷轴不堆叠，库存1份，开放门槛随对应成装；既有3种基础成装和旧存档物品ID保留。

