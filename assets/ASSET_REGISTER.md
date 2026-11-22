# Godot 资产来源登记

2026-10-22 起按用户确认临时复用 Android 美术。同步文件的逐项路径和哈希见 `assets/android/manifest.json`，后续替换状态保留在此登记和迁移文档中。

| path | source | license | author | public_build_allowed | replacement_status |
|---|---|---|---|---|---|
| scenes/MainMenu.tscn | Godot Control nodes created in source | Godot Engine MIT; source scene authored for project | Infinite Strife team | yes | final for phase A placeholder menu |
| scripts/app.gd | project source | project source code | Infinite Strife team | yes | active |
| scripts/battle/BattlePresentation.gd | project source; procedural placeholder records only | project source code | Infinite Strife team | yes | replace `*_placeholder` IDs with original licensed images during T3 art pass |
| scripts/battle/SkillRuntime.gd | project source; original skill records only | project source code | Infinite Strife team | yes | active |
| scripts/battle/BattleEffectCatalog.gd | project source; effect-ID mapping only | project source code | Infinite Strife team | yes | active; raster effect assets may replace mapped IDs later |
| scripts/prepare/*.gd | project source; Android 0.1.5 behavior reimplemented in GDScript | project source code | Infinite Strife team | yes | active; no Android runtime code copied |
| scenes/Preparation.tscn | Godot Control scene authored for project | Godot Engine MIT; project scene source | Infinite Strife team | yes | active; visual resource audit remains pending |
| assets/battle/azure_ruins_arena_v1.png | generated for this project with OpenAI image generation | project-generated asset; provenance recorded in project history | Infinite Strife team | yes | T3 battle background v1 |
| assets/battle/hero_cinder_idle_v1.png | generated for this project with OpenAI image generation | project-generated asset; provenance recorded in project history | Infinite Strife team | yes | T3 Cinder idle battle art v1 |
| assets/battle/hero_gale_idle_v1.png | generated for this project with OpenAI image generation | project-generated asset; provenance recorded in project history | Infinite Strife team | yes | T3 Gale idle battle art v1 |
| assets/android/** | Android app/src/main/assets; per-file SHA256 in assets/android/manifest.json | inherited Android source provenance; temporary migration reuse | original Android asset authors as recorded upstream | development reuse approved by user | temporary; replacement deferred |
