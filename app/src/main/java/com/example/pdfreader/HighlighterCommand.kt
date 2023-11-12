package com.example.pdfreader

import android.graphics.Path

class HighlighterCommand(currPath: PathPaint?) : UndoableCommand<MutableList<PathPaint?>> {
    var path: PathPaint? = currPath

    override fun execute(paths: MutableList<PathPaint?>) : MutableList<PathPaint?> {
        paths.add(path)
        return paths
    }
    override fun undo(paths: MutableList<PathPaint?>) : MutableList<PathPaint?> {
        paths.removeLastOrNull()
        return paths
    }
}