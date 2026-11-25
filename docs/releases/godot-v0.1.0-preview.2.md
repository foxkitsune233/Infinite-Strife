# Godot 0.1.0-preview.2 — Android竖屏修复

修复preview.1仅设置竖屏画布、却未锁定Android系统方向的问题。旧APK清单screenOrientation=0（横屏），新APK已验证为1（竖屏）。新增项目配置回归与APK导出清单校验，方向不符时阻止打包成功。

仅更新安卓版。包名org.infinitestrife.godot不变，versionCode从101升为102，签名证书SHA256保持326da9bcc37796e9582dc84924fcc53acca636b0a323749f89ab8c0ef1f7eaac，可覆盖preview.1保留应用存档。无需卸载旧包。

验证：新APK签名、证书一致性、版本及最终清单方向通过；竖屏项目、按键适配294项检查、成长页438项检查通过。本机仍缺Android模拟器加速驱动，未进行真机方向验收。

Windows版没有此次方向问题，继续使用preview.1的Windows ZIP；本次附件仅APK与SHA256SUMS。
