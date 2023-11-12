package com.example.pdfreader

import android.graphics.Paint
import android.graphics.Path

class PathPaint(currPaint: Paint, type: CommandType) : Path() {
    var paint: Paint = currPaint
    var type: CommandType = type
}