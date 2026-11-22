class_name BattleAudioBus
extends Node
## Procedural placeholder SFX. Replace cue names with licensed AudioStream assets later.
## Godot migration ships no licensed audio files, so `sound` gates all procedural cues;
## `jingles` and `music` stay persisted but have no playable source here (see parity ledger).

const AppSettings = preload("res://scripts/app/AppSettings.gd")

var _player: AudioStreamPlayer
var _playback: AudioStreamGeneratorPlayback
var _frames_left := 0
var _frequency := 440.0
var _phase := 0.0
var _sound_enabled := true

static func cue_for(event: Dictionary) -> String:
	match str(event.get("kind", "")):
		"physical_attack": return "critical" if bool(event.get("critical", false)) else "hit"
		"skill_cast": return "skill"
		"active_used": return "active"
		"dot_tick": return "dot"
	return ""

func _ready() -> void:
	_sound_enabled = AppSettings.get_flag("sound")
	_player = AudioStreamPlayer.new()
	var stream := AudioStreamGenerator.new()
	stream.mix_rate = 22050.0
	stream.buffer_length = 0.18
	_player.stream = stream
	_player.volume_db = -18.0
	add_child(_player)
	_player.play()
	_playback = _player.get_stream_playback()

func play_event(event: Dictionary) -> void:
	play_cue(cue_for(event))

## 重新读取音效开关，供设置页保存后即时生效（无需重开战斗）。
func refresh_settings() -> void:
	_sound_enabled = AppSettings.get_flag("sound")
	if not _sound_enabled:
		_frames_left = 0

func play_cue(cue: String) -> void:
	_sound_enabled = AppSettings.get_flag("sound")
	if not _sound_enabled:
		_frames_left = 0
		return
	if cue.is_empty(): return
	var spec: Array = {"hit": [220.0, 0.055], "critical": [110.0, 0.11], "skill": [660.0, 0.14], "active": [520.0, 0.09], "dot": [180.0, 0.045]}.get(cue, [])
	if spec.is_empty(): return
	_frequency = float(spec[0])
	_frames_left = roundi(float(spec[1]) * 22050.0)
	_phase = 0.0

func _process(_delta: float) -> void:
	if _playback == null: return
	var frames := _playback.get_frames_available()
	for _index in frames:
		var sample := 0.0
		if _frames_left > 0:
			var envelope := minf(1.0, float(_frames_left) / 220.0)
			sample = sin(_phase) * 0.26 * envelope
			_phase += TAU * _frequency / 22050.0
			_frames_left -= 1
		_playback.push_frame(Vector2(sample, sample))
