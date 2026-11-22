extends Control

const COLUMNS := 6
const CELL := 100.0

var _column := 0
var _row := 0
var _row_height := 0

func reset() -> void:
	_column = 0
	_row = 0
	_row_height = 0
	custom_minimum_size = Vector2(COLUMNS * CELL, CELL)
	queue_redraw()

func append_tile(tile: Control, footprint: Vector2i) -> void:
	var span := Vector2i(clampi(footprint.x, 1, COLUMNS), maxi(footprint.y, 1))
	if _column + span.x > COLUMNS:
		_row += _row_height
		_column = 0
		_row_height = 0
	add_child(tile)
	tile.position = Vector2(_column, _row) * CELL + Vector2(3, 3)
	tile.size = Vector2(span) * CELL - Vector2(6, 6)
	_column += span.x
	_row_height = maxi(_row_height, span.y)
	custom_minimum_size.y = (_row + _row_height) * CELL
	queue_redraw()

func _draw() -> void:
	var line := Color(0.19, 0.65, 0.78, 0.45)
	for column in COLUMNS + 1:
		draw_line(Vector2(column * CELL, 0), Vector2(column * CELL, custom_minimum_size.y), line)
	for row in int(custom_minimum_size.y / CELL) + 1:
		draw_line(Vector2(0, row * CELL), Vector2(COLUMNS * CELL, row * CELL), line)
