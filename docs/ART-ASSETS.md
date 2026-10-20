# 美术资源说明（ART-ASSETS）

## 为什么仓库里没有图

`app/src/main/assets/art/` 下的美术资源**不随源码分发**。这部分素材包含 DOTA2 相关的人物立绘、卡牌画面与场景图，版权归属 Valve，放进公开仓库存在侵权风险。

仓库提供的是**源码 + 配置 + 音频**，可以直接编译。跑起来会缺图，需要自行补齐美术资源。

## 仓库里有什么

| 路径 | 内容 | 授权 |
| --- | --- | --- |
| `app/src/main/assets/config/` | 全部游戏配置 JSON（英雄、卡牌、事件、战利品、难度等） | 自研 |
| `app/src/main/assets/audio/` | BGM 与音效（6 个 ogg + 4 个授权声明） | CC0 1.0 |
| `app/src/main/assets/licenses/` | 第三方资源授权文本 | — |

音频来自 [OpenGameArt](https://opengameart.org/content/once-upon-a-time-loop)（TAD，《Once Upon a Time》，CC0）与 [Kenney](https://kenney.nl/)（界面音效、Jingles，CC0），均为公有领域，可自由分发。

## 缺失的美术资源

总计 303 个 PNG，约 **151 MB**。

| 子目录 | 文件数 | 体积 | 用途 |
| --- | --- | --- | --- |
| `cards/` | 47 | 86.1 MB | 卡牌画面 |
| `heroes/` | 47 | 44.9 MB | 英雄立绘与攻击序列帧（含 `atlases/` 图集） |
| `items/` | 167 | 11.8 MB | 物品与装备图标 |
| `scenes/` | 14 | 3.1 MB | 场景背景 |
| `events/` | 25 | 2.3 MB | 事件插图 |
| `branding/` | 1 | 2.4 MB | 品牌图 |
| `chests/` | 2 | 0.4 MB | 宝箱图 |

## 如何补齐

1. 从项目方获取美术资源包（约 151 MB，另见项目主页提供方式）
2. 解压后把 `art/` 整个目录放回 **`app/src/main/assets/art/`**，保持上面的子目录结构不变
3. 重新构建即可

目录结构与文件名是代码里硬编码的读取路径，**不要重命名或改层级**，否则资源会加载失败。

## 注意事项

- 美术资源仅供个人学习与非商业用途使用；对外分发、商业化或二次发布前，请先自行确认 DOTA2 素材的授权状况
- 若要自建替代美术，按同名同路径替换即可，无需改动代码
