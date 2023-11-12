package com.example.pdfreader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import androidx.core.graphics.scale
import kotlin.math.pow

@SuppressLint("AppCompatCustomView")
class PDFimage  // constructor
    (context: Context?) : ImageView(context) {
    val LOGNAME = "pdf_image"

    // drawing path
    var path: PathPaint? = null
    var paths = mutableListOf<PathPaint?>()

    // image to display
    var bitmap: Bitmap? = null
    var paint: Paint = Paint(Color.BLACK).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    var type: CommandType = CommandType.PEN

    // the undo- and redo-stacks for the executed commands
    var undoCommands = mutableListOf<Any>()
    var redoCommands = mutableListOf<Any>()

    fun undo() {
        undoCommands.removeLastOrNull()?.apply {
            redoCommands.add(this)
            paths = (this as UndoableCommand<MutableList<PathPaint?>>).undo(paths)
        }
    }

    fun redo() {
        redoCommands.removeLastOrNull()?.apply {
            undoCommands.add(this)
            paths = (this as UndoableCommand<MutableList<PathPaint?>>).execute(paths)
        }
    }

    // we save a lot of points because they need to be processed
    // during touch events e.g. ACTION_MOVE
    var x1 = 0f
    var x2 = 0f
    var y1 = 0f
    var y2 = 0f
    var old_x1 = 0f
    var old_y1 = 0f
    var old_x2 = 0f
    var old_y2 = 0f
    var mid_x = -1f
    var mid_y = -1f
    var old_mid_x = -1f
    var old_mid_y = -1f
    var p1_id = 0
    var p1_index = 0
    var p2_id = 0
    var p2_index = 0

    var panX = 0f
    var panY = 0f
    var old_panX = 0f
    var old_panY = 0f

    // store cumulative transformations
    // the inverse matrix is used to align points with the transformations - see below
    var currentMatrix = Matrix()
    var inverse = Matrix()

    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var inverted = floatArrayOf()
        when (event.pointerCount) {
            1 -> {
                p1_id = event.getPointerId(0)
                p1_index = event.findPointerIndex(p1_id)

                // invert using the current matrix to account for pan/scale
                // inverts in-place and returns boolean
                inverse = Matrix()
                currentMatrix.invert(inverse)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                inverse.mapPoints(inverted)
                x1 = inverted[0]
                y1 = inverted[1]

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d(LOGNAME, "Action down")
                        if (type == CommandType.MOVE) {
                            old_panX = event.x
                            old_panY = event.y
                            panX = translationX
                            panY = translationY
                        } else {
                            path = PathPaint(paint, type)
                            paths.add(path)
                            path!!.moveTo(x1, y1)
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        Log.d(LOGNAME, "Action move")
                        if (type == CommandType.MOVE) {
                            val dx = event.x - old_panX
                            val dy = event.y - old_panY
                            translationX = panX + dx
                            translationY = panY + dy
                        } else {
                            path!!.lineTo(x1, y1)
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        Log.d(LOGNAME, "Action up")
                        when (type) {
                            CommandType.PEN -> {
                                paths.removeLastOrNull()
                                PenCommand(path).apply {
                                    undoCommands.add(this)
                                    paths = execute(paths)
                                }
                                redoCommands.clear()
                            }

                            CommandType.HIGHLIGHTER -> {
                                paths.removeLastOrNull()
                                HighlighterCommand(path).apply {
                                    undoCommands.add(this)
                                    paths = execute(paths)
                                }
                                redoCommands.clear()
                            }

                            CommandType.ERASER -> {
                                paths.removeLastOrNull()
                                EraserCommand(path).apply {
                                    undoCommands.add(this)
                                    paths = execute(paths)
                                }
                                redoCommands.clear()
                            }

                            else -> {

                            }
                        }
                    }
                }
            }
            2 -> {
                // point 1
                p1_id = event.getPointerId(0)
                p1_index = event.findPointerIndex(p1_id)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                inverse.mapPoints(inverted)

                // first pass, initialize the old == current value
                if (old_x1 < 0 || old_y1 < 0) {
                    x1 = inverted.get(0)
                    old_x1 = x1
                    y1 = inverted.get(1)
                    old_y1 = y1
                } else {
                    old_x1 = x1
                    old_y1 = y1
                    x1 = inverted.get(0)
                    y1 = inverted.get(1)
                }

                // point 2
                p2_id = event.getPointerId(1)
                p2_index = event.findPointerIndex(p2_id)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p2_index), event.getY(p2_index))
                inverse.mapPoints(inverted)

                // first pass, initialize the old == current value
                if (old_x2 < 0 || old_y2 < 0) {
                    x2 = inverted.get(0)
                    old_x2 = x2
                    y2 = inverted.get(1)
                    old_y2 = y2
                } else {
                    old_x2 = x2
                    old_y2 = y2
                    x2 = inverted.get(0)
                    y2 = inverted.get(1)
                }

                // midpoint
                mid_x = (x1 + x2) / 2
                mid_y = (y1 + y2) / 2
                old_mid_x = (old_x1 + old_x2) / 2
                old_mid_y = (old_y1 + old_y2) / 2

                // distance
                val d_old =
                    Math.sqrt(Math.pow((old_x1 - old_x2).toDouble(), 2.0) + Math.pow((old_y1 - old_y2).toDouble(), 2.0))
                        .toFloat()
                val d = Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0))
                    .toFloat()

                // pan and zoom during MOVE event
                if (event.action == MotionEvent.ACTION_MOVE) {
                    Log.d(LOGNAME, "Multitouch move")
                    // pan == translate of midpoint
                    val dx = mid_x - old_mid_x
                    val dy = mid_y - old_mid_y
                    currentMatrix.preTranslate(dx, dy)
                    Log.d(LOGNAME, "translate: $dx,$dy")

                    // zoom == change of spread between p1 and p2
                    var scale = d / d_old
                    scale = Math.max(0f, scale)

                    val values = FloatArray(9)
                    currentMatrix.getValues(values)
                    val currentScale = values[Matrix.MSCALE_X]

                    // min and max zooming
                    val minScale = 1f  // Minimum zoom (normal size)
                    val maxScale = 4f  // Maximum zoom (quadruple the normal size)

                    if (currentScale * scale < minScale) {
                        scale = minScale / currentScale
                    } else if (currentScale * scale > maxScale) {
                        scale = maxScale / currentScale
                    }

                    currentMatrix.preScale(scale, scale, mid_x, mid_y)
                    this.scaleX *= scale
                    this.scaleY *= scale
                    Log.d(LOGNAME, "scale: $scale")

                // reset on up
                } else if (event.action == MotionEvent.ACTION_UP) {
                    old_x1 = -1f
                    old_y1 = -1f
                    old_x2 = -1f
                    old_y2 = -1f
                    old_mid_x = -1f
                    old_mid_y = -1f
                }
            }
            else -> {}
        }
        return true
    }

    // set image as background
    fun setImage(bitmap: Bitmap?) {
        this.bitmap = bitmap
    }

    // set brush characteristics
    // e.g. color, thickness, alpha
    fun setBrush(paint: Paint) {
        this.paint = paint
    }

    // set type of Path drawn
    fun setPathType(type: CommandType) {
        this.type = type
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()

        // apply transformations from the event handler above
        canvas.concat(currentMatrix)

        // draw background
        if (bitmap != null) {
            setImageBitmap(bitmap)
        }

        // draw lines over it
        for (path in paths) {
            path?.let { canvas.drawPath(it, path.paint) }
        }

        canvas.restore()
    }
}