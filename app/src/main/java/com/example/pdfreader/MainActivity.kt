package com.example.pdfreader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not at all obvious from documentation, so we should expect people to need this.
// We may wish to provide this code.
class MainActivity : AppCompatActivity() {
    val LOGNAME = "pdf_viewer"
    val FILENAME = "shannon1948.pdf"
    val FILERESID = R.raw.shannon1948

    // manage the pages of the PDF, see below
    lateinit var pdfRenderer: PdfRenderer
    lateinit var parcelFileDescriptor: ParcelFileDescriptor
    var currentPage: PdfRenderer.Page? = null
    var pageNum: Int = 0

    // custom ImageView class that captures strokes and draws them over the image
    lateinit var pageImage: PDFimage

    val pages: MutableList<MutableList<PathPaint?>> = mutableListOf<MutableList<PathPaint?>>()
    val undos: MutableList<MutableList<Any>> = mutableListOf<MutableList<Any>>()
    val redos: MutableList<MutableList<Any>> = mutableListOf<MutableList<Any>>()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val layout = findViewById<LinearLayout>(R.id.pdfLayout)
        layout.isEnabled = true

        pageImage = PDFimage(this)
        layout.addView(pageImage)
        pageImage.minimumWidth = 1000
        pageImage.minimumHeight = 2000

        for (i in 1..55) {
            var newPage: MutableList<PathPaint?> = mutableListOf<PathPaint?>()
            pages.add(newPage)
            var pageUndo: MutableList<Any> = mutableListOf<Any>()
            undos.add(pageUndo)
            var pageRedo: MutableList<Any> = mutableListOf<Any>()
            redos.add(pageRedo)
        }

        // ribbon tab
        val ribbonTab = findViewById<LinearLayout>(R.id.ribbonTab)
        val titleBar = findViewById<TextView>(R.id.titleBar).apply {
            text = FILENAME
        }
        val moveButton = findViewById<ImageButton>(R.id.moveButton).apply {
            setOnClickListener {
                pageImage.setPathType(CommandType.MOVE)
//                println("move")
            }
        }
        val penButton = findViewById<ImageButton>(R.id.penButton).apply {
            setOnClickListener {
                var paint = Paint(Color.BLACK).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                }
                pageImage.setBrush(paint)
                pageImage.setPathType(CommandType.PEN)
//                println("pen")
            }
        }
        val highlighterButton = findViewById<ImageButton>(R.id.highlighterButton).apply {
            setOnClickListener {
                var paint = Paint().apply {
                    color = Color.YELLOW
                    style = Paint.Style.STROKE
                    strokeWidth = 30f
                    alpha = 100
                }
                pageImage.setBrush(paint)
                pageImage.setPathType(CommandType.HIGHLIGHTER)
//                println("highlighter")
            }
        }
        val eraserButton = findViewById<ImageButton>(R.id.eraserButton).apply {
            setOnClickListener {
                var paint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 60f
                    alpha = 200
                }
                pageImage.setBrush(paint)
                pageImage.setPathType(CommandType.ERASER)
//                println("eraser")
            }
        }

        // undo redo tab
        val undoRedoTab = findViewById<LinearLayout>(R.id.undoRedoTab)
        val undoButton = findViewById<ImageButton>(R.id.undoButton).apply {
            setOnClickListener {
                pageImage.undo()
//                println("undo")
            }
        }
        val redoButton = findViewById<ImageButton>(R.id.redoButton).apply {
            setOnClickListener {
                pageImage.redo()
//                println("redo")
            }
        }

        // navigate page tab
        val navPageTab = findViewById<LinearLayout>(R.id.navPageTab)
        val pageStatus = findViewById<TextView>(R.id.pageStatus)
        val prevPageButton = findViewById<ImageButton>(R.id.prevPageButton).apply {
            setOnClickListener {
                if (pageNum > 0) {
                    pages[pageNum] = pageImage.paths
                    undos[pageNum] = pageImage.undoCommands
                    redos[pageNum] = pageImage.redoCommands
                    pageNum -= 1
                    pageImage.paths = pages[pageNum]
                    pageImage.undoCommands = undos[pageNum]
                    pageImage.redoCommands = redos[pageNum]
                    pageImage.scaleX = 1f
                    pageImage.scaleY = 1f
                    pageImage.translationX = 0f
                    pageImage.translationY = 0f
                    showPage(pageNum)
                    pageStatus.apply {
                        text = "${pageNum + 1} / ${pdfRenderer.pageCount}"
                    }
                } else {
                    /* DO NOTHING */
                }
//                println("prevPage")
            }
        }
        val nextPageButton = findViewById<ImageButton>(R.id.nextPageButton).apply {
            setOnClickListener {
                if (pageNum < pdfRenderer.pageCount-1) {
                    pages[pageNum] = pageImage.paths
                    undos[pageNum] = pageImage.undoCommands
                    redos[pageNum] = pageImage.redoCommands
                    pageNum += 1
                    pageImage.paths = pages[pageNum]
                    pageImage.undoCommands = undos[pageNum]
                    pageImage.redoCommands = redos[pageNum]
                    pageImage.scaleX = 1f
                    pageImage.scaleY = 1f
                    pageImage.translationX = 0f
                    pageImage.translationY = 0f
                    showPage(pageNum)
                    pageStatus.apply {
                        text = "${pageNum + 1} / ${pdfRenderer.pageCount}"
                    }
                } else {
                    /* DO NOTHING */
                }
//                println("nextPage")
            }
        }

        // open page 0 of the PDF
        // it will be displayed as an image in the pageImage (above)
        try {
            openRenderer(this)
            showPage(pageNum)
            // update pageStatus
            pageStatus.apply {
                text = "${pageNum + 1} / ${pdfRenderer.pageCount}"
            }
//            closeRenderer()
        } catch (exception: IOException) {
            Log.d(LOGNAME, "Error opening PDF")
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            closeRenderer()
        } catch (ex: IOException) {
            Log.d(LOGNAME, "Unable to close PDF renderer")
        }
    }

    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        // In this sample, we read a PDF from the assets directory.
        val file = File(context.cacheDir, FILENAME)
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            val asset = this.resources.openRawResource(FILERESID)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                output.write(buffer, 0, size)
            }
            asset.close()
            output.close()
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        // capture PDF data
        // all this just to get a handle to the actual PDF representation
        pdfRenderer = PdfRenderer(parcelFileDescriptor)
    }

    // do this before you quit!
    @Throws(IOException::class)
    private fun closeRenderer() {
        currentPage?.close()
        pdfRenderer.close()
        parcelFileDescriptor.close()
    }

    private fun showPage(index: Int) {
        if (pdfRenderer.pageCount <= index) {
            return
        }
        // Close the current page before opening another one.
        currentPage?.close()

        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(index)

        if (currentPage != null) {
            // Important: the destination bitmap must be ARGB (not RGB).
            val bitmap = Bitmap.createBitmap(currentPage!!.getWidth(), currentPage!!.getHeight(), Bitmap.Config.ARGB_8888)

            // Here, we render the page onto the Bitmap.
            // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
            // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
            currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Display the page
            pageImage.setImage(bitmap)
        }
    }
}