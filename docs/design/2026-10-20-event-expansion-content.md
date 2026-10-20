# 0.1.5 事件扩充：内容与结局美术契约

## 目标

将途中节点事件由 8 类扩至 16 类。每个新增事件固定 3 个可读结局，所有结局均有稳定的 `artKey`，以便结果页、存档回放和未来本地化共用同一张横向插画。

本稿只定义内容、标识符和结算语义；具体金币、掉落表与数值平衡由外围实现读取当前难度表后填入，不能在 UI 层硬编码。

## 新增事件与结局

| EventKind | 标题 | optionId | 结算语义 | artKey |
|---|---|---|---|---|
| `FROZEN_SHRINE` | 冰封祭坛 | `attune` / `pray` / `leave` | 生命代价换寒霜奖励／安全小奖励／离开 | `frozen_shrine.attune` / `.pray` / `.leave` |
| `WAR_BANNER_RUINS` | 战旗遗址 | `claim` / `blood_rite` / `leave` | 金币换怒火奖励／生命代价换高价值奖励／离开 | `war_banner.claim` / `.blood_rite` / `.leave` |
| `ROVING_CARAVAN` | 流动商队 | `buy` / `sell` / `depart` | 局内金币购买／明确卖出一件物品／离开 | `roving_caravan.buy` / `.sell` / `.depart` |
| `ROPEWAY_BRIDGE` | 断桥索道 | `cross` / `repair` / `leave` | 生命代价换多件小掉落／金币换中型掉落／离开 | `ropeway_bridge.cross` / `.repair` / `.leave` |
| `PACT_ALTAR` | 秘契祭坛 | `offer_medicine` / `offer_blood` / `refuse` | 交药换卡／生命代价换稀有奖励／拒绝 | `pact_altar.offer_medicine` / `.offer_blood` / `.refuse` |
| `ALCHEMY_LAB` | 失控炼金室 | `distill` / `break_seal` / `leave` | 交药换治疗型掉落／中毒或受伤换炼金组件／离开 | `alchemy_lab.distill` / `.break_seal` / `.leave` |
| `HUNTER_CAMP` | 猎人营火 | `barter` / `share_rations` / `leave` | 明确交物换战利品／交药换治疗与情报型奖励／离开 | `hunter_camp.barter` / `.share_rations` / `.leave` |
| `MOONWELL_ECHO` | 月井回响 | `resonate` / `draw_echo` / `leave` | 达标羁绊换对应奖励／随机低档奖励／离开 | `moonwell_echo.resonate` / `.draw_echo` / `.leave` |

## 结果页约束

1. `outcomeId` 与 `artKey` 必须在节点生成时固定，重进、转屏和读档不重抽。
2. 失败事务不写 `resolvedOptionId`，不消耗物品、金币或生命。
3. 交物必须走现有 `EventItemSelection`；安全箱物品默认不能选中。
4. 奖励先判定真实连续空间；不能放入时进入整理，不得自动塞进安全箱。
5. 每个结局图均为 16:9 无文字横图；标题、数字和按钮由原生 UI 叠加。

## 统一美术规范

- 高饱和度 2D 卡通奇幻、硬边纸雕感、粗深蓝描边、青绿遗迹、金黄色高光。
- 中景叙事，主体居左，右侧预留暗色结果文案区。
- 禁止真实摄影质感、卡牌边框、文字、标志、水印和 UI 边框。
- 先以冰封祭坛三张样图验画风，通过后再生成其余 21 张。
