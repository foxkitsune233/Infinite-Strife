# 无尽纷争 Godot 0.1.0-preview.1

Windows与Android首个Godot迁移预览版，与原生Android1.0并行；存档不互通。此次不是原生1.0的更新，也不表示全部迁移和真机验收完成。

- Windows：x64，ZIP内独立EXE，内嵌游戏数据，无需另放PCK。未做Windows代码签名。
- Android：Android7.0/API24以上，arm64-v8a与x86_64，包名org.infinitestrife.godot，versionCode101；使用独立长期签名，不覆盖原生Android游戏。
- 附件SHA256SUMS.txt用于校验下载文件。请保留Godot新存档备份后再测试。

包含两位初始英雄与八位任务英雄、旧档漏发修复、六生效/三备用装备、魔晶与神杖、主要按键适配及成长页显示修补。美术沿用Android导出素材，战斗音效暂为程序合成。

验证：Godot4.7.1，100项源码回归通过；Windows发布EXE独立目录启动和实际OpenGL渲染退出0；APK发布签名校验通过，包名/版本/最低SDK/架构已检查。APK不可调试。

限制：本机模拟器缺少硬件加速驱动，没有完成Android安装启动、真机手势安全区/密度适配和完整设备流程验收。此前间歇退出问题不能宣布根治。预览版可能仍有兼容性和平衡问题。

构建：tools/build_release.ps1。签名密钥及signing.json仅存本机%APPDATA%/Godot/keystores/infinite-strife-godot-release，不进入仓库或发布附件；维护者须另行安全备份，后续更新必须沿用同一签名。
