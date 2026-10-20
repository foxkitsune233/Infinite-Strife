# 主宰头像启动图标 v1

按用户要求基于现有主宰头部形象重新绘制，使用内置image_gen工具生成；不是裁切全身立绘，也未修改原角色素材。保留白色面具、橙色纹样/围巾、黑色束发和现有纸片卡通风格。

- 参考：`app/src/main/assets/art/heroes/juggernaut-idle-v1.png`
- 生成原图：`app/src/main/assets/art/branding/juggernaut-launcher-portrait-v1.png`
- Android资源：`res/drawable-nodpi/ic_launcher_portrait.png`、5档mipmap普通/圆形PNG、v26自适应图标前景/背景。
- 图标预览：`docs/design/launcher-icon-preview-v1.png`；为蒙版模拟，不是设备截图。
- 可重复打包：用有Pillow的Python运行`tools/export_launcher_icon.py`，只做尺寸导出和启动器蒙版适配，不重绘角色。

## 生成提示词

Create one finished square Android game launcher icon portrait, a new redraw guided by the attached character reference. Reference role: character identity and existing visual style, not a pose to retain. Show ONLY a close-up head and upper shoulders of this masked sword warrior. Preserve distinctive ivory angular mask with orange-red cheek/forehead markings and narrow black eye slits, black swept hair tied into a high ponytail with orange binding, bright burnt-orange scarf, dark charcoal armor. Strong readable silhouette, slightly three-quarter facing viewer, mask centered. Existing game style: saturated flat 2D cartoon fantasy, bold angular cut-paper painted shapes, restrained painterly paper texture, thick crisp dark outlines, attractive polished mobile game icon, expressive and formidable without excessive realism. Ivory mask luminous against deep teal navy background, warm orange scarf as clear secondary mass. Mask and eyes entirely within central safe zone; keep vital hair/mask/scarf detail within central 60% width and height so circular launcher masks will not cut important features. Full-bleed square deep teal backdrop with subtle angular color shapes, no border, no frame, no circle, no lettering, no logo, no watermark, no weapons, no hands, no additional characters. Produce a single square illustration, not a sheet or phone mockup. Output is a final game asset.

## 外部检查

覆盖安装新包后检查桌面、应用抽屉及应用信息页：显示主宰头像，圆形/圆角裁切不挡面具。Android24/25走密度PNG，26+走自适应图标。不删除应用数据；部分桌面缓存旧图标时可重新打开桌面/刷新快捷方式。

仅资源构建与静态蒙版预览检查，本机未运行模拟器或单测。
