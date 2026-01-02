package com.mobileapp.puzzlegame

import android.animation.ValueAnimator
import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.animation.doOnEnd
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.random.Random

const val DEFAULT_HEIGHT: Int = 5
const val DEFAULT_WIDTH: Int = 5
const val HISTORY_LIMIT: Int = 50

enum class InteractMode() {
    RotateLeft, RotateRight, Lock
}

class GameViewModel(forceNewGame: Boolean, context: Context) : ViewModel() {
    var walls = MutableLiveData<EdgeGrid>()

    private var gameState: GameLogic? = null
    private val undoHistory = ArrayDeque<GameLogic>()
    var wasSolverUsed = false // Track if solve() was used
        private set

    val currentBoard = MutableLiveData<MutableList2D<Cell>>()
    val cellRotations = HashMap<Position, Float>()

    val mode = MutableLiveData(InteractMode.RotateLeft)
    val moveCount = MutableLiveData(0)
    val elapsedTime = MutableLiveData(0L)
    val isVictory = MutableLiveData(false)
    val canUndo = MutableLiveData(false)

    val currentGridSize = MutableLiveData<Pair<Int, Int>>()

    var desc: GameDescription

    init {
        var gameString: String? = null
        if (!forceNewGame) {
            gameString = SettingsManager.getSavedGameString(context)
        }
        if (gameString != null) {
            Log.d("GameViewModel", "loaded the game")

            // load existing game state
            val gameState: GameLogic = Json.decodeFromString(gameString)
            this.gameState = gameState
            desc = gameState.desc

            setupGrid()

            viewModelScope.launch {
                withContext(Dispatchers.Default) {
                    refreshCells()
                }
            }
        } else {
            // Generate a random seed for each new game
            val randomSeed = System.currentTimeMillis()
            desc = GameDescription(
                randomSeed,
                SettingsManager.getGameWidth(context),
                SettingsManager.getGameHeight(context),
                0f,
                isWrapping = SettingsManager.isWrapping(context),
                isUnique = SettingsManager.isUnique(context)
            )
            newGame()
        }
    }

    private fun setupGrid() {
        currentGridSize.value = Pair(desc.width, desc.height)
    }

    fun saveGame(context: Context) {
        Log.d("GameViewModel", "saved the game")
        if (gameState != null) {
            val gameString = Json.encodeToString(gameState!!)
            SettingsManager.setSavedGameString(context, gameString)
        }
    }

    fun newGame() {
        moveCount.value = 0
        elapsedTime.value = 0L
        isVictory.value = false
        canUndo.value = false
        wasSolverUsed = false
        undoHistory.clear()

        setupGrid()

        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                gameState = GameLogic(desc)
                // Randomize rotations so game doesn't start solved
                randomizeRotations()
                refreshCells()
            }
        }
    }

    fun restart() {
        newGame()
    }

    private fun randomizeRotations() {
        if (gameState == null) return
        val rng = Random(System.currentTimeMillis())
        // Randomly rotate each cell 0-3 times
        for (y in 0..<desc.height) {
            for (x in 0..<desc.width) {
                val rotations = rng.nextInt(4)
                for (i in 0 until rotations) {
                    gameState!!.rotateCW(x, y)
                }
            }
        }
        // Recompute active state after randomization
        gameState!!.computeActive()
    }

    fun undo() {
        if (undoHistory.isEmpty()) return

        gameState = undoHistory.removeAt(undoHistory.lastIndex)
        canUndo.postValue(undoHistory.isNotEmpty())
        refreshCells()
        moveCount.value = moveCount.value!! - 1
    }

    fun solve() {
        if (gameState == null) return

        wasSolverUsed = true


        // TODO
//        wasSolverUsed = true // Mark that solve was used
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val result = gameState!!.solve()
                if (result) {
                    // Solution found - cells are now locked in correct positions
                    gameState!!.computeActive()
                    refreshCells()
                    // Don't set victory flag - solve shouldn't trigger victory popup or history
                }
            }
        }
    }

    @WorkerThread
    fun refreshCells() {
        val gameState = gameState!!
        currentBoard.postValue(gameState.doNotUse)
        walls.postValue(gameState.walls)

    }

    fun click(x: Int, y: Int) {
        // ignore click if there is no game, if we've already won, or if the solver was used
        if (gameState == null || isVictory.value == true || wasSolverUsed) return

        // grab non-null values out of LiveData's to use here
        val mode = mode.value!!
        val gameState = gameState!!

        // save state before move for undo
        undoHistory.add(gameState.clone())
        while (undoHistory.size > HISTORY_LIMIT) undoHistory.removeFirst()

        when (mode) {
            InteractMode.RotateLeft -> {
                if (!gameState.rotateCCW(x, y)) return
            }

            InteractMode.RotateRight -> {
                if (!gameState.rotateCW(x, y)) return
            }

            InteractMode.Lock -> {
                // no animation for locking, so we can return from here
                gameState.lock(x, y)
                refreshCells()
                return
            }
        }

        // Increment move count
        moveCount.value = (moveCount.value ?: 0) + 1

        val pos = Position(x, y)
        val rot = if (mode == InteractMode.RotateRight) -90f else 90f

        // create an animation for rotating the cell
        ValueAnimator.ofFloat(cellRotations.getOrDefault(pos, 0f) + rot, 0f).apply {
            duration = 280
            addUpdateListener { animation ->
                cellRotations[pos] = animation.animatedValue as Float
                currentBoard.postValue(gameState.doNotUse)
            }
            start()
        }.doOnEnd {
            cellRotations.remove(pos)

            viewModelScope.launch {
                withContext(Dispatchers.Default) {
                    val activeCount = gameState.computeActive()
                    refreshCells()
                    // Check for victory after computing active state
                    if (activeCount == desc.width * desc.height) {
                        isVictory.postValue(true)
                    }
                }
            }
        }
    }

    fun longClick(x: Int, y: Int) {
        if (gameState == null || isVictory.value == true || wasSolverUsed) return

        val mode = mode.value!!
        val gameState = gameState!!

        when (mode) {
            InteractMode.RotateLeft -> gameState.lock(x, y)
            InteractMode.RotateRight -> gameState.lock(x, y)
            InteractMode.Lock -> return
        }
        refreshCells()
    }
}

class GameViewModelFactory(private val forceNewGame: Boolean, private val context: Context) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return GameViewModel(forceNewGame, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}