package com.mobileapp.puzzlegame

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.rotationMatrix
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.mobileapp.puzzlegame.databinding.FragmentGameBinding
import java.util.concurrent.TimeUnit
import kotlin.math.max

class TileCurveProvider(curveGap: Float) {
    var backgroundPaths: Array<Path> = arrayOf()
    var foregroundPaths: Array<Path> = arrayOf()

    var curveGap = 0f
        set(v) {
            field = v

            backgroundPaths = makePaths(0.01f)
            foregroundPaths = makePaths(0.02f)
        }

    fun makePaths(overdraw: Float): Array<Path> {
        // six unique shapes: X, T, L, |, P, and (empty)
        val beg = 0f - overdraw
        val mid = .5f
        val end = 1f + overdraw

        // shapes I, P, and (empty) are each special
        val shapeI = Path().apply {
            moveTo(end, mid)
            lineTo(beg, mid)
        }

        val shapeP = Path().apply {
            moveTo(end, mid)
            lineTo(mid, mid)
        }

        val shapeEmpty = Path()

        // shapes L, T, and X share prefixes, so they are initialized off each other here
        val shapeL = Path().apply {
            moveTo(end, mid)
            lineTo(mid + curveGap, mid)
            quadTo(mid, mid, mid, mid - curveGap)
            lineTo(mid, beg)
        }

        val shapeT = Path(shapeL).apply {
            moveTo(mid, mid - curveGap)
            quadTo(mid, mid, mid - curveGap, mid)
            lineTo(beg, mid)
        }

        val shapeX = Path(shapeT).apply {
            moveTo(mid - curveGap, mid)
            quadTo(mid, mid, mid, mid + curveGap)
            lineTo(mid, end)
            moveTo(mid, mid + curveGap)
            quadTo(mid, mid, mid + curveGap, mid)
        }

        // shapeT was not given its final line segment earlier, so it could be copied to
        // make shapeX, but now that shapeX has been made we can give it that line segment
        shapeT.apply {
            lineTo(end, mid)
        }

        val rotateCCW = rotationMatrix(-90f, mid, mid)
        val rotateRev = rotationMatrix(180f, mid, mid)
        val rotateCW = rotationMatrix(90f, mid, mid)

        return arrayOf(
            // DLUR (down, left, up, right)
            // 0000 - empty
            shapeEmpty,
            // 0001 - P right
            shapeP,
            // 0010 - P up
            Path(shapeP).apply { transform(rotateCCW) },
            // 0011 - L ru
            shapeL,
            // 0100 - P left
            Path(shapeP).apply { transform(rotateRev) },
            // 0101 - I horizontal
            shapeI,
            // 0110 - L ul
            Path(shapeL).apply { transform(rotateCCW) },
            // 0111 - T nd
            shapeT,
            // 1000 - P down
            Path(shapeP).apply { transform(rotateCW) },
            // 1001 - L rd
            Path(shapeL).apply { transform(rotateCW) },
            // 1010 - I vertical
            Path(shapeI).apply { transform(rotateCW) },
            // 1011 - T nl
            Path(shapeT).apply { transform(rotateCW) },
            // 1100 - L ld
            Path(shapeL).apply { transform(rotateRev) },
            // 1101 - T nu
            Path(shapeT).apply { transform(rotateRev) },
            // 1110 - T nr
            Path(shapeT).apply { transform(rotateCCW) },
            // 1111 - X
            shapeX,
        )
    }

    init {
        this.curveGap = curveGap
    }
}

class GameBoardDrawable(
    val width: Int,
    val height: Int,
    val board: MutableList2D<Cell>,
    val cellRotations: HashMap<Position, Float>,
    val tileCurveProvider: TileCurveProvider,
    private val outlinePaint: Paint,
    private val fillPaint: Paint,
    private val errorPaint: Paint,
) : Drawable() {
    private fun drawCell(canvas: Canvas, cell: Cell) {
        val isEndpoint = cell.linkCount() == 1

        if (cell.isLocked) {
            canvas.withClip(0f, 0f, 1f, 1f) {
                drawRGB(128, 128, 128)
            }
        }

        // first, draw the outline
        canvas.drawPath(tileCurveProvider.backgroundPaths[cell.inner], outlinePaint)

        if (isEndpoint) {
            canvas.drawCircle(
                0.5f,
                0.5f,
                outlinePaint.strokeWidth,
                Paint(outlinePaint).apply { style = Paint.Style.FILL })
        }

        // then, draw the active lines
        if (cell.isPowered) {
            canvas.drawPath(tileCurveProvider.foregroundPaths[cell.inner], fillPaint)

            if (isEndpoint) {
                canvas.drawCircle(
                    0.5f,
                    0.5f,
                    outlinePaint.strokeWidth + (fillPaint.strokeWidth - outlinePaint.strokeWidth) / 2,
                    Paint(fillPaint).apply { style = Paint.Style.FILL })
                // TODO: create paint provider to not be making paint objects inside draw()
            }
        }

        // finally, draw the error lines
        canvas.drawPath(tileCurveProvider.foregroundPaths[cell.misLinks.inner], errorPaint)
    }

    override fun draw(canvas: Canvas) {
        Log.d("drawable", "full redraw $bounds")

        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()

        val stepX = bounds.width() / width.toFloat()
        val stepY = bounds.height() / height.toFloat()

        var drawY = top
        for (y in 0..<height) {
            var drawX = left
            for (x in 0..<width) {

                canvas.withSave {
                    translate(drawX, drawY)
                    scale(stepX, stepY)

                    val rotation = cellRotations[Position(x, y)]
                    if (rotation != null) rotate(rotation, 0.5f, 0.5f)

                    drawCell(canvas, board[x, y])
                }

                drawX += stepX
            }
            drawY += stepY
        }
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}
}

class GameFragment : Fragment() {
    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: GameViewModel
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var startTime: Long = 0L
    private var victoryRecorded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment, with bindings
        _binding = FragmentGameBinding.inflate(inflater, container, false)

        val arguments = GameFragmentArgs.fromBundle(requireArguments())
        // handle the view model
        val viewModelFactory = GameViewModelFactory(arguments.forceNewGame, requireContext())
        viewModel = ViewModelProvider(this, viewModelFactory)[GameViewModel::class.java]

        setupGameGrid()

        val modeButtons = mapOf(
            InteractMode.RotateLeft to binding.modeLeftButton,
            InteractMode.RotateRight to binding.modeRightButton,
            InteractMode.Lock to binding.modeLockButton
        )

        viewModel.mode.observe(viewLifecycleOwner) { newMode ->
            for ((mode, button) in modeButtons)
                button.isChecked = newMode == mode
        }
        for ((mode, button) in modeButtons) {
            button.setOnClickListener {
                viewModel.mode.value = mode
            }
        }

        victoryRecorded = false

        setupToolbar()
        setupMenu()
        setupHowToPlay()
        setupVictoryPopup()
        startTimer()

        // Observe move count
        viewModel.moveCount.observe(viewLifecycleOwner) { count ->
            binding.movesText.text = getString(R.string.moves_format, count)
        }

        // Observe victory - only record if not from solve
        viewModel.isVictory.observe(viewLifecycleOwner) { victory ->
            if (victory && !victoryRecorded && !viewModel.wasSolverUsed) {
                victoryRecorded = true
                recordVictory()
                showVictoryPopup()
            }
        }

        return binding.root
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveGame(requireContext())
    }

    private fun setupGameGrid() {
        val context = requireContext()

        val tileCurveProvider = TileCurveProvider(SettingsManager.getCurveGap(context))

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        outlinePaint.style = Paint.Style.STROKE
        fillPaint.style = Paint.Style.STROKE

        outlinePaint.color = "#000000".toColorInt()
        fillPaint.color = SettingsManager.getColor(context)
        errorPaint.color = "#C00000".toColorInt()

        outlinePaint.strokeWidth = SettingsManager.getThick(context)
        fillPaint.strokeWidth = outlinePaint.strokeWidth * SettingsManager.getThickFill(context)
        errorPaint.strokeWidth = fillPaint.strokeWidth


        viewModel.currentBoard.observe(viewLifecycleOwner) { board ->
            val width = board.width
            val height = board.height

            binding.gameBoardView.background = GameBoardDrawable(
                width,
                height,
                board,
                viewModel.cellRotations,
                tileCurveProvider,
                outlinePaint,
                fillPaint,
                errorPaint
            )
            binding.zoomLayout.setMaxZoom(max(width, height).toFloat())
        }

        var lastTouch = Pair(0f, 0f)
        binding.gameBoardView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                lastTouch = Pair(event.x, event.y)
            }
            false
        }

        binding.gameBoardView.setOnClickListener { v ->
            val gridX = (lastTouch.first / (v.width.toFloat() / viewModel.desc.width)).toInt()
            val gridY = (lastTouch.second / (v.height.toFloat() / viewModel.desc.height)).toInt()
            viewModel.click(gridX, gridY)
        }

        binding.gameBoardView.setOnLongClickListener { v ->
            val gridX = (lastTouch.first / (v.width.toFloat() / viewModel.desc.width)).toInt()
            val gridY = (lastTouch.second / (v.height.toFloat() / viewModel.desc.height)).toInt()
            viewModel.longClick(gridX, gridY)
            true
        }
    }

    private fun recordVictory() {
        val moves = viewModel.moveCount.value ?: 0
        val elapsedMs = System.currentTimeMillis() - startTime
        val elapsedSeconds = elapsedMs / 1000
        val size = "${viewModel.desc.width}x${viewModel.desc.height}"

        val historyItem = GameHistoryItem(size, moves, elapsedSeconds)
        GameHistoryManager.saveGame(requireContext(), historyItem)
    }

    private fun showVictoryPopup() {
        val moves = viewModel.moveCount.value ?: 0
        val elapsedMs = System.currentTimeMillis() - startTime
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % 60

        binding.victoryMovesText.text = getString(R.string.victory_moves, moves)
        binding.victoryTimeText.text = getString(R.string.victory_time, minutes, seconds)
        binding.victoryOverlay.visibility = View.VISIBLE
    }

    private fun setupVictoryPopup() {
        binding.victoryOkButton.setOnClickListener {
            binding.victoryOverlay.visibility = View.GONE
        }

        binding.victoryOverlay.setOnClickListener {
            binding.victoryOverlay.visibility = View.GONE
        }

        binding.victoryCard.setOnClickListener {
            // Prevent clicks on card from closing overlay
        }
    }

    private fun setupToolbar() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.menuButton.setOnClickListener {
            binding.menuOverlay.visibility = View.VISIBLE
        }
    }

    private fun setupMenu() {
        binding.menuOverlay.setOnClickListener {
            binding.menuOverlay.visibility = View.GONE
        }

        binding.menuCard.setOnClickListener {
            // Prevent clicks on card from closing overlay
        }

        binding.menuUndo.setOnClickListener {
            viewModel.undo()
            binding.menuOverlay.visibility = View.GONE
        }

        binding.menuRestart.setOnClickListener {
            victoryRecorded = false
            startTime = System.currentTimeMillis()
            viewModel.restart()
            timerRunnable?.let { timerHandler.removeCallbacks(it) }
            startTimer()
            binding.menuOverlay.visibility = View.GONE
        }

        binding.menuSolve.setOnClickListener {
            viewModel.solve()
            binding.menuOverlay.visibility = View.GONE
        }

        binding.menuNewGame.setOnClickListener {
            victoryRecorded = false
            startTime = System.currentTimeMillis()
            viewModel.newGame()
            timerRunnable?.let { timerHandler.removeCallbacks(it) }
            startTimer()
            binding.menuOverlay.visibility = View.GONE
        }

        binding.menuCellSize.setOnClickListener {
            findNavController().navigate(R.id.action_gameFragment_to_cellSizeSettingsFragment)
            binding.menuOverlay.visibility = View.GONE
        }

        binding.menuHowToPlay.setOnClickListener {
            binding.menuOverlay.visibility = View.GONE
            binding.howToPlayOverlay.visibility = View.VISIBLE
        }
    }

    private fun setupHowToPlay() {
        binding.closeHowToPlayButton.setOnClickListener {
            binding.howToPlayOverlay.visibility = View.GONE
        }

        binding.howToPlayOverlay.setOnClickListener {
            binding.howToPlayOverlay.visibility = View.GONE
        }
    }

    private fun startTimer() {
        startTime = System.currentTimeMillis()
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
                binding.timerText.text = getString(R.string.time_format, minutes, seconds)
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        _binding = null
    }
}