package com.example.pdfreader

import android.graphics.Path

class EraserCommand(currPath: PathPaint?) : UndoableCommand<MutableList<PathPaint?>> {
    var path: PathPaint? = currPath
    var erasedPaths: MutableList<PathPaint?> = mutableListOf<PathPaint?>()

    override fun execute(paths: MutableList<PathPaint?>) : MutableList<PathPaint?> {
        paths.forEach {currPath ->
            if (currPath!!.type != CommandType.ERASER) {
                val intersect = Path()
                if (intersect.op(currPath!!, path!!, Path.Op.INTERSECT)) {
                    if (!intersect.isEmpty) {
                        erasedPaths.add(currPath)
                    }
                }
            }
        }
        erasedPaths.forEach {currPath ->
            paths.remove(currPath)
        }
        return paths
    }
    override fun undo(paths: MutableList<PathPaint?>) : MutableList<PathPaint?> {
        erasedPaths.forEach {
            paths.add(it)
        }
        return paths
    }
}