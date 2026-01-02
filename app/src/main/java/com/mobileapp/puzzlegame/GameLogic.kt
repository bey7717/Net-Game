package com.mobileapp.puzzlegame

import android.util.Log
import com.mobileapp.puzzlegame.EdgeGrid.EdgeState.Closed
import com.mobileapp.puzzlegame.EdgeGrid.EdgeState.Open
import com.mobileapp.puzzlegame.EdgeGrid.EdgeState.Unknown
import kotlinx.serialization.Serializable
import java.util.TreeSet
import kotlin.math.max
import kotlin.random.Random

class DisjointSetForest<T> {
    private val parents = HashMap<T, T>()

    fun getParent(x: T): T {
        return parents.getOrPut(x) { x }
    }

    fun findRoot(x: T): T {
        var x = x

        while (true) {
            val p = getParent(x)
            if (p == x) {
                // we have found the root
                return x
            }
            // we have not found the root, so keep traversing up
            // but to optimize (amortize) the DSF, we might as well update the parents
            // on our way up the tree to make future searches faster
            // this particular method is called path halving
            val gp = getParent(p) // x's parent is set to x's grandparent
            parents[x] = gp
            x = gp
        }
    }

    fun merge(x: T, y: T) {
        val x = findRoot(x)
        val y = findRoot(y)

        if (x == y) {
            // the two sets are already merged
            return
        }

        parents[y] = x
    }
}


enum class Direction {
    RIGHT, UP, LEFT, DOWN;

    operator fun not(): Direction {
        return when (this) {
            RIGHT -> LEFT
            UP -> DOWN
            LEFT -> RIGHT
            DOWN -> UP
        }
    }

    operator fun plus(n: Int): Direction {
        val n = n % 4
        return when (n) {
            1 -> clockwise()
            2 -> !this
            3 -> antiClockwise()
            else -> this
        }
    }

    fun antiClockwise(): Direction {
        return when (this) {
            RIGHT -> UP
            UP -> LEFT
            LEFT -> DOWN
            DOWN -> RIGHT
        }
    }

    fun clockwise(): Direction {
        return when (this) {
            RIGHT -> DOWN
            UP -> RIGHT
            LEFT -> UP
            DOWN -> LEFT
        }
    }
}

@Serializable
open class Links(var inner: Int) {
    constructor() : this(0)

    constructor(
        right: Boolean, up: Boolean, left: Boolean, down: Boolean
    ) : this((if (right) 1 else 0) or ((if (up) 1 else 0) shl 1) or ((if (left) 1 else 0) shl 2) or ((if (down) 1 else 0) shl 3))

    override fun toString(): String {
        val type = when (linkCount()) {
            1 -> "P"
            2 -> {
                if (inner == 10 || inner == 5) "I" else "L"
            }

            3 -> "T"
            else -> "+"
        }
        return "Links{r: ${this.isLinkedTo(Direction.RIGHT)}, u: ${this.isLinkedTo(Direction.UP)}, l: ${
            this.isLinkedTo(
                Direction.LEFT
            )
        }, d: ${this.isLinkedTo(Direction.DOWN)}, #: ${this.linkCount()}, t: $type}"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Links) return false
        return this.inner == other.inner
    }

    override fun hashCode(): Int {
        return inner.hashCode()
    }

    fun isLinkedTo(dir: Direction): Boolean {
        return inner and (1 shl dir.ordinal) > 0
    }

    fun isLinked(): Boolean {
        return inner > 0
    }

    fun linkCount(): Int {
        return inner.countOneBits()
    }

    fun clockwised(): Links {
        return Links(((inner and 0xE) shr 1) or ((inner and 0x1) shl 3))
    }

    fun antiClockwised(): Links {
        return Links(((inner and 0x7) shl 1) or ((inner and 0x08) shr 3))
    }
}

@Serializable
class MutableLinks() : Links(0) {
    operator fun plusAssign(dir: Direction) {
        inner = inner or (1 shl dir.ordinal)
    }

    operator fun minusAssign(dir: Direction) {
        inner = inner and (1 shl dir.ordinal).inv()
    }

    fun clockwise() {
        inner = ((inner and 0xE) shr 1) or ((inner and 0x1) shl 3)
    }

    fun antiClockwise() {
        inner = ((inner and 0x7) shl 1) or ((inner and 0x08) shr 3)
    }
}

interface List2D<out T> {
    operator fun get(pos: Position): T
    operator fun get(x: Int, y: Int): T
}

@Serializable
class MutableList2D<T>(private val inner: MutableList<MutableList<T>>) : List2D<T> {
    val height get() = inner.size
    val width get() = inner.getOrNull(0)?.size ?: 0

    constructor(width: Int, height: Int, init: (Int, Int) -> T) : this(
        MutableList(height) { y ->
            MutableList(width) { x ->
                init(x, y)
            }
        })

    constructor(width: Int, height: Int, init: () -> T) : this(
        MutableList(height) {
            MutableList(width) {
                init()
            }
        })

    fun <R> map(transform: (T) -> R): MutableList2D<R> {
        return MutableList2D(inner.map { row ->
            row.map { x ->
                transform(x)
            }.toMutableList()
        }.toMutableList())
    }

    override operator fun get(pos: Position): T {
        return inner[pos.y][pos.x]
    }

    override operator fun get(x: Int, y: Int): T {
        return inner[y][x]
    }

    operator fun set(pos: Position, new: T) {
        inner[pos.y][pos.x] = new
    }

    operator fun set(x: Int, y: Int, new: T) {
        inner[y][x] = new
    }

    fun clone(): MutableList2D<T> {
        val height = inner.size
        val width = inner[0].size
        return MutableList2D(width, height) { x, y ->
            this[x, y]
        }
    }
}

@Serializable
class Cell(val isLocked: Boolean, val isPowered: Boolean) : Links(0) {
    constructor() : this(Links(), false, false)
    constructor(links: Links, isLocked: Boolean, isPowered: Boolean) : this(isLocked, isPowered) {
        inner = links.inner
    }

    // misLinks is only used for rendering, to highlight things like loops or locked links to walls.
    // it is not part of data class constructor as it should not be considered for equality or copied
    var misLinks = Links()

    fun clone(): Cell {
        return Cell(Links(inner), isLocked, isPowered)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Cell) return false
        return this.inner == other.inner && this.isLocked == other.isLocked && this.isPowered == other.isPowered
    }

    fun clockwise(): Cell {
        return Cell(this.clockwised(), isLocked, isPowered)
    }

    fun antiClockwise(): Cell {
        return Cell(this.antiClockwised(), isLocked, isPowered)
    }

    fun locked(): Cell {
        return Cell(Links(inner), true, isPowered)
    }

    fun unlocked(): Cell {
        return Cell(Links(inner), false, isPowered)
    }

    fun powered(): Cell {
        return Cell(Links(inner), isLocked, true)
    }

    fun unPowered(): Cell {
        return Cell(Links(inner), isLocked, false)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + isLocked.hashCode()
        result = 31 * result + isPowered.hashCode()
        result = 31 * result + misLinks.hashCode()
        return result
    }
}

@Serializable
data class Position(val x: Int, val y: Int) : Comparable<Position> {
    operator fun plus(dir: Direction): Position {
        return when (dir) {
            Direction.RIGHT -> Position(x + 1, y)
            Direction.UP -> Position(x, y - 1)
            Direction.LEFT -> Position(x - 1, y)
            Direction.DOWN -> Position(x, y + 1)
        }
    }

    override fun compareTo(other: Position): Int {
        if (this.x < other.x) return -1
        if (this.x > other.x) return 1
        if (this.y < other.y) return -1
        if (this.y > other.y) return 1
        return 0
    }
}

data class Transform(val pos: Position, val dir: Direction) : Comparable<Transform> {
    constructor(x: Int, y: Int, dir: Direction) : this(Position(x, y), dir)

    val x get() = pos.x
    val y get() = pos.y

    operator fun not(): Transform {
        return Transform(pos + dir, !dir)
    }

    fun withDir(newDir: Direction): Transform {
        return Transform(pos, newDir)
    }

    fun edgeNormalized(): Transform {
        return when (dir) {
            Direction.RIGHT -> Transform(pos.x + 1, pos.y, Direction.LEFT)
            Direction.DOWN -> Transform(pos.x, pos.y + 1, Direction.UP)
            else -> copy()
        }
    }

    fun apply(): Position {
        return pos + dir
    }

    override fun compareTo(other: Transform): Int {
        if (this.pos < other.pos) return -1
        if (this.pos > other.pos) return 1
        if (this.dir < other.dir) return -1
        if (this.dir > other.dir) return 1
        return 0
    }
}

// stores the state of every edge
// Unknown is used by the solver, while a generated game should have all edges as either closed or open
@Serializable
class EdgeGrid(
    val width: Int,
    val height: Int,
    val isWrapping: Boolean,
    private val inner: Array<Array<Array<EdgeState>>>
) : Cloneable {
    enum class EdgeState {
        Unknown, Open, Closed
    }

    public override fun clone(): EdgeGrid {
        var y = -1
        return EdgeGrid(width, height, isWrapping, Array(inner.size) {
            var x = -1
            y += 1
            Array(inner[y].size) {
                x += 1
                Array(2) { i ->
                    inner[y][x][i]
                }
            }
        })
    }

    constructor(width: Int, height: Int, isWrapping: Boolean, default: EdgeState) : this(
        width, height, isWrapping, Array(if (isWrapping) height else height + 1) {
            Array(if (isWrapping) width else width + 1) {
                Array(2) {
                    default
                }
            }
        })

    operator fun get(trf: Transform): EdgeState {
        val trf = trf.edgeNormalized()
        val y = if (isWrapping) trf.y % height else trf.y
        val x = if (isWrapping) trf.x % width else trf.x
        return inner[y][x][if (trf.dir == Direction.LEFT) 0 else 1]
    }

    operator fun set(trf: Transform, new: EdgeState) {
        val trf = trf.edgeNormalized()
        val y = if (isWrapping) trf.y % height else trf.y
        val x = if (isWrapping) trf.x % width else trf.x
        inner[y][x][if (trf.dir == Direction.LEFT) 0 else 1] = new
    }
}

@Serializable
data class GameDescription(
    val seed: Long,
    val width: Int,
    val height: Int,
    val wallProbability: Float,
    val isWrapping: Boolean,
    val isUnique: Boolean,
)

class TodoList<T> {
    private val order = ArrayDeque<T>()
    private val stuff = HashSet<T>()

    fun removeLast(): T {
        val thing = order.removeAt(order.lastIndex)
        stuff.remove(thing)
        return thing
    }

    fun isEmpty(): Boolean {
        return order.isEmpty()
    }

    fun add(thing: T) {
        if (stuff.contains(thing)) return
        stuff.add(thing)
        order.add(thing)
    }
}

@Serializable
class GameLogic(
    val desc: GameDescription, var doNotUse: MutableList2D<Cell>, var walls: EdgeGrid
) : Cloneable {
    val width get() = desc.width
    val height get() = desc.height

    val centerPos = Position(width / 2, height / 2)

    constructor(desc: GameDescription) : this(
        desc,
        MutableList2D(desc.width, desc.height) { Cell() },
        EdgeGrid(desc.width, desc.height, desc.isWrapping, Open)
    ) {
        generate(Random(desc.seed))
        computeActive()
    }

    public override fun clone(): GameLogic {
        return GameLogic(desc, doNotUse.clone(), walls.clone()) // TODO
    }

    private fun isInBounds(pos: Position): Boolean {
        return pos.x in 0..<width && pos.y in 0..<height
    }

    private fun wrap(pos: Position): Position {
        return Position((pos.x + width) % width, (pos.y + height) % height)
    }

    private fun softWrap(pos: Position): Position? {
        // if in bounds, don't do anything and just return the same pos
        if (isInBounds(pos)) return pos
        // if out of bounds, but we're not wrapping, return null
        if (!desc.isWrapping) return null
        // if out of bounds, but we are wrapping, return it wrapped
        return wrap(pos)
    }

    private tailrec fun generate(rng: Random) {
        Log.d("generate", "begin generator with first pull as ${rng.nextInt()}")

        // this will keep track of all positions that could be expanded into.
        // it is important that the data structure used is repeatable when pulled from randomly
        // (so do not use a hashset)
        val spreadTargets = TreeSet<Transform>()

        val board = MutableList2D(width, height) { MutableLinks() }

        // initial possibilities spread in all directions from the center cell of the map, if they are within bounds
        for (dir in Direction.entries) {
            if (isInBounds(centerPos + dir)) {
                spreadTargets.add(Transform(centerPos, dir))
            }
        }

        while (!spreadTargets.isEmpty()) {
            val currTrf = spreadTargets.random(rng) // actually so cool that Kotlin has this method
            spreadTargets.remove(currTrf)
            val targetPos = wrap(currTrf.pos + currTrf.dir)

            // connect the cells
            board[currTrf.pos] += currTrf.dir
            board[targetPos] += !currTrf.dir

            // if we've made a T-piece, don't let it become an X-piece
            if (board[currTrf.pos].linkCount() >= 3) {
                for (dir in Direction.entries) {
                    if (spreadTargets.remove(currTrf.withDir(dir))) break
                }
            }

            // to avoid creating loops, remove all other possibilities that would enter
            // the cell we just moved into
            for (dir in Direction.entries) {
                val toRemove = Transform(wrap(targetPos + !dir), dir)
                spreadTargets.remove(toRemove)
            }

            // add the new possibilities for moving out of the cell we just moved into
            for (dir in Direction.entries) {
                if (dir == !currTrf.dir) continue // skip where we just came from
                val outwardPos =
                    softWrap(targetPos + dir) ?: continue // skip out-of-bounds directions
                if (board[outwardPos].isLinked()) continue // avoid creating loops

                spreadTargets.add(Transform(targetPos, dir))
            }
        }
        Log.d("generate", "end generator")

        // ensure the generated grid is uniquely solvable, if that is desired by the game description
        if (desc.isUnique) {
            var prevPerturbedCount = -1

            while (true) {
                val (isSolved, solution) = solve(board)
                if (isSolved) break

                val solvedCells = solution.map { x -> x != null }
                Log.d("ensureUniqueness", "prevN is $prevPerturbedCount")

                // the solver failed, so try to make the grid more solvable by using perturb
                var currPerturbedCount = 0
                for (y in 0..<height) {
                    for (x in 0..<width) {
                        val currIsSolved = solvedCells[x, y]

                        if (x + 1 < width) {
                            val rightIsSolved = solvedCells[x + 1, y]
                            if (currIsSolved && !rightIsSolved) {
                                currPerturbedCount += perturb(
                                    Position(x + 1, y), Direction.LEFT, board, solvedCells, rng
                                )
                            } else if (!currIsSolved && rightIsSolved) {
                                currPerturbedCount += perturb(
                                    Position(x, y), Direction.RIGHT, board, solvedCells, rng
                                )
                            }
                        }

                        if (y + 1 < height) {
                            val downIsSolved = solution[x, y + 1] != null
                            if (currIsSolved && !downIsSolved) {
                                currPerturbedCount += perturb(
                                    Position(x, y + 1), Direction.UP, board, solvedCells, rng
                                )
                            } else if (!currIsSolved && downIsSolved) {
                                currPerturbedCount += perturb(
                                    Position(x, y), Direction.DOWN, board, solvedCells, rng
                                )
                            }
                        }
                    }
                }

                // if the amount of cells perturbed has not gone down since the last time we tried
                // to fix this board, it's probably not going to work out so let's give up and start
                // over with a new board
                if (prevPerturbedCount != -1 && prevPerturbedCount <= currPerturbedCount) {
                    val newSeed = rng.nextInt()
                    Log.d(
                        "generate",
                        "perturbing did not seem to help, starting over with seed $newSeed"
                    )
                    return generate(Random(newSeed))
                }

                prevPerturbedCount = currPerturbedCount
            }
        }

        // board generation successful, set it as the game's cell grid
        doNotUse = MutableList2D(width, height) { x, y ->
            Cell(board[x, y], false, false)
        }
    }

    fun solve(): Boolean {
        val (result, solution) = solve(doNotUse)
        if (!result) return false

        doNotUse = MutableList2D(width, height) { x, y ->
            Cell(solution[x, y]!!, true, true)
        }
        return true
    }

    private fun solve(board: List2D<Links>): Pair<Boolean, MutableList2D<Links?>> {
        // this keep track of every possible rotation of each cell, without duplicates.
        // the solver primarily works by making deductions that rule out possible rotations, until
        // all cells have only one remaining possible orientation, indicating the grid is solved.
        // while building the possible orientations, the area is also counted up to be used later.

        val possibleCellOrientations = MutableList2D(width, height) { mutableListOf<Links>() }

        var ret = true
        fun getSolved(): MutableList2D<Links?> {
            return MutableList2D(width, height) { x, y ->
                if (possibleCellOrientations[x, y].size == 1) {
                    possibleCellOrientations[x, y][0]
                } else {
                    ret = false
                    null
                }
            }
        }

        var area = 0
        for (y in 0..<height) {
            for (x in 0..<width) {
                val pos = Position(x, y)
                val currCellOrientations = possibleCellOrientations[x, y]

                // the first possible orientation of each cell is its current orientation
                val firstOrientation = board[pos]
                currCellOrientations.add(firstOrientation)

                if (firstOrientation.isLinked()) area++

                // subsequent orientations are valid so long as they don't repeat
                for (i in 1..<4) {
                    val prevOrientation = currCellOrientations[i - 1]
                    val newOrientation = prevOrientation.antiClockwised()
                    if (newOrientation == firstOrientation) break
                    currCellOrientations.add(newOrientation)
                }
            }
        }
        Log.d("solve", "area is $area")

        // this keeps track of whether edges should be open or closed.
        // the solver uses this to deduce valid orientations of cells, since cells must mutually link
        // (a cell can't link to a cell that isn't linking back to it through the same edge)
        val edgeStates = EdgeGrid(width, height, desc.isWrapping, Unknown)
        // if this is a not a wrapping game, close the relevant edges immediately
        if (!desc.isWrapping) {
            for (x in 0..width) {
                // top-most edge
                edgeStates[Transform(x, 0, Direction.UP)] = Closed
                // bottom-most edge
                edgeStates[Transform(x, height, Direction.UP)] = Closed
            }
            for (y in 0..height) {
                // left-most edge
                edgeStates[Transform(0, y, Direction.LEFT)] = Closed
                // right-most edge
                edgeStates[Transform(width, y, Direction.LEFT)] = Closed
            }
        }

        // this keeps track of how much area is reachable by each edge of each cell.
        // the solver uses this to avoid dead ends, since the solution is supposed to be a connected
        // graph (all cells can reach all other cells)
        val deadEnds = Array(height) { Array(width) { Array(4) { area + 1 } } }

        // this is disjoint-set data structure that is used to track loops.
        // the solver uses this to avoid loops, since the solution is supposed to have zero of them
        val equivalence = DisjointSetForest<Position>()

        // TODO copy existing barriers here

        // solving begins here
        val todo = TodoList<Position>()
        var didSomething = true
        while (true) {
            if (todo.isEmpty()) {
                if (!didSomething) break
                didSomething = false

                for (y in 0..<height) {
                    for (x in 0..<width) {
                        todo.add(Position(x, y))
                    }
                }
                continue
            }
            val currPos = todo.removeLast()


            val ourClass = equivalence.findRoot(currPos)
            val deadEndMax = Array(4) { 0 }


            // go through all the possible orientations of the current cell
            val currOrientations = possibleCellOrientations[currPos]
            val iterator = currOrientations.listIterator()
            while (iterator.hasNext()) {
                val orientation = iterator.next()
                var isValidOrientation = true
                var totalReachableArea = 0
                val nonDeadEndDirections = mutableListOf<Int>()
                val equiv = mutableListOf(ourClass)

                for (dir in Direction.entries) {
                    val edgeState = edgeStates[Transform(currPos, dir)]
                    val isLinked = orientation.isLinkedTo(dir)
                    if ((edgeState == Closed && isLinked) || (edgeState == Open && !isLinked)) {
                        // this orientation is invalid, because it has a link that does not match
                        // with what we know of that link's edge
                        Log.d(
                            "solve",
                            "orientation $orientation at $currPos is invalid as it conflicts with edge knowledge"
                        )
                        isValidOrientation = false
                    }

                    if (isLinked) {
                        // tally up information about dead ends
                        val reachableArea = deadEnds[currPos.y][currPos.x][dir.ordinal]
                        if (reachableArea <= area) { // if this is a dead end
                            totalReachableArea += reachableArea
                        } else {
                            nonDeadEndDirections.add(dir.ordinal)
                        }

                        // check if we're creating any loops by linking to any cells through
                        // an edge that is not known to be open
                        if (edgeState == Unknown) {
                            val otherClass = equivalence.findRoot(wrap(currPos + dir))
                            if (equiv.contains(otherClass)) {
                                Log.d(
                                    "solve",
                                    "orientation $orientation at $currPos is invalid as it creates a loop"
                                )
                                isValidOrientation = false
                            } else {
                                equiv.add(otherClass)
                            }
                        }
                    }
                }

                if (nonDeadEndDirections.isEmpty()) {
                    // this orientation links together only dead-ends, so if the total area is less
                    // than the entire grid it is invalid
                    // Note: +1 to include self in the area
                    if (totalReachableArea > 0 && totalReachableArea + 1 < area) {
                        Log.d(
                            "solve",
                            "orientation $orientation at $currPos is invalid as it connects together only dead ends"
                        )
                        isValidOrientation = false
                    }
                } else if (nonDeadEndDirections.size == 1) {
                    // this orientation links together only one non-dead-end, so that non-dead-end
                    // may become a dead-end if all other orientations do the same
                    val index = nonDeadEndDirections.first()
                    totalReachableArea++ // +1 to include self
                    if (deadEndMax[index] < totalReachableArea) {
                        deadEndMax[index] = totalReachableArea
                    }
                } else {
                    // this orientation links together 2+ non-dead-ends, so they should continue
                    // to be marked as non-dead-ends
                    for (index in nonDeadEndDirections) {
                        deadEndMax[index] = area + 1
                    }
                }

                if (!isValidOrientation) {
                    // this orientation isn't possible, so remove it from possibilities
                    iterator.remove()
                    didSomething = true
                }
            }

            if (!iterator.hasPrevious()) {
                // there are no possible orientations for this cell, so this grid is unsolvable
                Log.d(
                    "solve", "unsolvable: cell at $currPos has no remaining possible orientations"
                )
                return Pair(false, getSolved())
            }

            // see if we've learned anything new about the edges for this cell
            for (dir in Direction.entries) {
                val trf = Transform(currPos, dir)

                if (edgeStates[trf] == Unknown) {
                    val targetPos = wrap(currPos + dir)

                    if (currOrientations.all { links -> links.isLinkedTo(dir) }) {
                        // this edge is open in all orientations
                        edgeStates[trf] = Open
                        equivalence.merge(currPos, targetPos)
                        Log.d("solve", "edge $currPos $dir is OPEN")
                    } else if (currOrientations.none { links -> links.isLinkedTo(dir) }) {
                        // this edge is closed in all orientations
                        edgeStates[trf] = Closed
                        Log.d("solve", "edge $currPos $dir is CLOSED")
                    } else {
                        continue
                    }
                    didSomething = true
                    todo.add(targetPos)
                }
            }

            // see if we've learned anything new about dead-ends
            for (dir in Direction.entries) {
                val targetPos = wrap(currPos + dir)
                val invDir = !dir
                val v = deadEndMax[dir.ordinal]
                if (v > 0 && deadEnds[targetPos.y][targetPos.x][invDir.ordinal] > v) {
                    Log.d("solve", "setting dead end for $targetPos $invDir to $v")
                    deadEnds[targetPos.y][targetPos.x][invDir.ordinal] = v
                    didSomething = true
                    todo.add(targetPos)
                }
            }
        }

        val solved = getSolved()
        Log.d("solve", "solve ended with $ret")
        return Pair(ret, solved) // TODO
    }

    // Tries to make an adjustment to a region of unlocked cells in order to improve the solvability
    // of the grid.
    // Locked cells represent cells the solver was able to deduce, so the idea here is to make a new
    // link somewhere around the perimeter of the unsolved region, which should allow for deductions
    // to "spread into" the unsolved region.
    private fun perturb(
        startPos: Position,
        startDir: Direction,
        board: MutableList2D<MutableLinks>,
        solvedMap: MutableList2D<Boolean>,
        rng: Random
    ): Int {
        var currPos = startPos.copy()
        var currDir = startDir
        Log.d("perturb", "perturb called at $currPos $currDir")

        // This loop builds a perimeter of the area of unlocked cells by "hugging the right wall",
        // which is a method that should be conceptually familiar if you've tried solving a maze.
        // At all times, currPos is an unlocked cell and currDir points from that position to the
        // perimeter, which is either a locked cell or the outer edge of a non-wrapping grid.
        val perimeter = mutableListOf<Transform>()
        do {
            perimeter.add(Transform(currPos, currDir))

            // For the purposes of explanation, assume we are standing in the cell at currPos and
            // facing the direction currDir. The way we are facing is hereby referred to as "forward",
            // so "left" and "right" refer to the intuitive directions relative to forward.

            // From where we are, look to our left and see if that's part of the perimeter
            val leftDir = currDir.antiClockwise()
            var targetPos = softWrap(currPos + leftDir)
            if (targetPos == null || solvedMap[targetPos]) {
                // The perimeter continues to our left, so turn to face it and continue
                currDir = leftDir
                continue
            }

            // The cell to our left is not part of the perimeter, so now we walk into it *without*
            // changing the direction we're facing, and look in front of us
            currPos = targetPos
            targetPos = softWrap(currPos + currDir)
            if (targetPos == null || solvedMap[targetPos]) {
                // We're facing the perimeter, so continue
                continue
            }

            // The cell to our front is not part of the perimeter, so now we walk into it *and*
            // turn to our right, meaning we are now facing the same cell we were facing at the
            // beginning of this loop (but standing in a different cell)
            currPos = targetPos
            currDir = currDir.clockwise()
        } while (currPos != startPos || currDir != startDir)
        // possible future improvement: this perimeter algorithm currently does not fully find an area
        // on some wrapping games, so possibly replace it with a flood-fill algorithm

        // shuffle the perimeter to avoid any bias due to the direction we generated it in
        perimeter.shuffle(rng)
        // find the first valid spot along the perimeter to create a link on
        var didSomething = false
        var subparChoice: Transform? = null
        for (trf in perimeter) {
            currPos = trf.pos
            currDir = trf.dir

            // can't make a link across the non-wrapping outer edge
            val targetPos = softWrap(currPos + currDir) ?: continue
            // can't make a link where there already is one
            if (board[currPos].isLinkedTo(currDir)) continue

            val wouldMakeCurrIntoCross = board[currPos].linkCount() >= 3
            val wouldMakeTargIntoCross = board[targetPos].linkCount() >= 3

            if (wouldMakeCurrIntoCross || wouldMakeTargIntoCross) {
                // can't make a link if it would make two crosses
                if (wouldMakeCurrIntoCross && wouldMakeTargIntoCross) continue

                // a link here would only make one cross, which is okay only if we later (during the
                // loop fixing stage of perturb) remove one of its other links (to return it to a T)
                // so for now don't make a link here, but remember the option is available so that
                // we can later use it as a last resort

                subparChoice = trf
                continue
            }

            // if we've made it down here, it's okay to make a link here, so let's do so
            Log.d("perturb", "adding a new link at $currPos $currDir")
            board[currPos] += currDir
            board[targetPos] += !currDir
            didSomething = true
            break
        }

        if (didSomething) {
            // we didn't need to use it, so forget about our subparChoice
            subparChoice = null
        } else {
            if (subparChoice == null) {
                // it wasn't possible to do anything anywhere along the perimeter
                return 0
            } else {
                // we didn't make a link yet, but we have the subparChoice we saved for later
                // available, so make a link here now
                currPos = subparChoice.pos
                currDir = subparChoice.dir
                Log.d("perturb", "resorting to subpar to add a new link at $currPos $currDir")
                board[currPos] += currDir
                board[wrap(currPos + currDir)] += !currDir
                Log.d("perturb", "subpar choice available")
            }
        }


        // Since we added a link, we've created a loop in the grid, so we need to find it and fix it
        // by randomly removing a different one of its links. To find the loop, we'll perform two
        // searches in parallel, each searching by "hugging" the left and right side respectively.
        val loopHeadTransforms = Array(2) { Transform(currPos, currDir) }
        val loops = Array(2) { mutableListOf<Transform>() }

        loopFinder@ while (true) {
            for (i in 0..<2) {
                val currTrf = loopHeadTransforms[i]
                currPos = currTrf.pos
                currDir = currTrf.dir

                // take a step along our search direction
                val targetPos = wrap(currPos + currDir)
                val prevTrf = loops[i].lastOrNull()
                if (prevTrf != null && prevTrf.pos == targetPos && prevTrf.dir == !currDir) {
                    // current segment undoes the previous segment, so get rid of the previous segment
                    loops[i].removeAt(loops[i].lastIndex)
                } else {
                    // current segment continues the loop
                    loops[i].add(currTrf)
                }

                // make turns until we're facing in a direction that has a link
                // turning around before making further turns ensures going back where we came from
                // is our last option
                currDir = !currDir
                for (_j in 0..<4) {
                    currDir = if (i == 0) currDir.antiClockwise() else currDir.clockwise()
                    if (board[targetPos].isLinkedTo(currDir)) {
                        loopHeadTransforms[i] = Transform(targetPos, currDir)
                        break
                    }
                }

                // check if we've made it back to our starting point, indicating we've finished
                // discovering a loop, so delete one of the links in that loop to remove the loop
                if (loopHeadTransforms[i].pos == loops[i][0].pos && loopHeadTransforms[i].dir == loops[i][0].dir) {
                    Log.d("perturb", "loop finished tracking ${loops[i]}")

                    // the only link we shouldn't remove is the one we made earlier as our perturb
                    loops[i].removeAt(0)

                    val currTrf = if (subparChoice != null) {
                        // if we resorted to using the subparChoice, we MUST remove a link from a
                        // cross-shaped cell, since crosses aren't allowed in this game and we only
                        // allowed creating one temporarily as that was our only option
                        loops[i].firstOrNull { v ->
                            board[v.pos].linkCount() == 4
                        } ?: loops[i][loops[i].lastIndex]
                    } else {
                        // pick a random link to remove
                        loops[i].random(rng)
                    }

                    Log.d("perturb", "fixing loop by removing a link at $currTrf")

                    // remove the targeted link
                    board[currTrf.pos] -= currTrf.dir
                    val targetPos = wrap(currTrf.apply())
                    board[targetPos] -= !currTrf.dir
                    break@loopFinder
                }
            }
        }

        // by now, perturb is done, so let's lock all of the cells in the region to mark it as done
        var count = 0
        // sorting the perimeter will give us an order that defines the area of it
        var remaining = perimeter.sorted()
        while (remaining.isNotEmpty()) {
            val x = remaining.first().x
            val pair = remaining.partition { trf -> trf.x == x }
            var column = pair.first
            remaining = pair.second

            // lock every cell in the column
            var firstPass = true
            while (column.isNotEmpty()) {
                // find the top and bottom edge of the column
                val indexTop = column.indexOfFirst { trf -> trf.dir == Direction.UP }
                val indexBottom = column.indexOfFirst { trf -> trf.dir == Direction.DOWN }

                var topPos: Position
                val bottomPos: Position
                if (indexTop == -1 || indexBottom == -1) {
                    if (!firstPass) break

                    // there is no top or bottom perimeter edge in this column, which means the
                    // entire column is part of the area
                    topPos = Position(x, 0)
                    bottomPos = Position(x, height - 1)

                    column = emptyList()
                } else {
                    topPos = Position(x, column[indexTop].y)
                    bottomPos = Position(x, column[indexBottom].y)

                    column = column.drop(max(indexTop, indexBottom) + 1)
                }

                while (true) {
                    Log.d("perturb", "perturb locking $topPos")
                    solvedMap[topPos] = true
                    count++
                    if (topPos == bottomPos) break
                    topPos = wrap(topPos + Direction.DOWN)
                }
                firstPass = false
            }
        }
        Log.d("perturb", "perturb touched $count cells")
        return count
    }

    fun rotateCW(x: Int, y: Int): Boolean {
        val currCell = doNotUse[x, y]
        if (currCell.isLocked) return false
        doNotUse[x, y] = currCell.clockwise()
        return true
    }

    fun rotateCCW(x: Int, y: Int): Boolean {
        val currCell = doNotUse[x, y]
        if (currCell.isLocked) return false
        doNotUse[x, y] = currCell.antiClockwise()
        return true
    }

    fun lock(x: Int, y: Int) {
        val currCell = doNotUse[x, y]
        if (currCell.isLocked) {
            doNotUse[x, y] = currCell.unlocked()
        } else {
            doNotUse[x, y] = currCell.locked()
        }

    }

    fun checkIfSolved(): Boolean {
        return computeActive() == width * height
    }

    fun computeActive(): Int {
        val power = MutableList(height) {
            MutableList(width) {
                false
            }
        }

        // start spreading power from the center cell
        // Note: this is arbitrary; any non-empty cell could be used as the starting point
        power[centerPos.y][centerPos.x] = true

        // queue of cells to visit
        val todo = ArrayDeque<Position>()
        todo.add(centerPos)

        while (todo.isNotEmpty()) {
            val currentPos = todo.removeFirst()
            val currentCell = doNotUse[currentPos]

            for (dir in Direction.entries) {
                val targetPos = wrap(currentPos + dir)
                val targetCell = doNotUse[targetPos]

                if (currentCell.isLinkedTo(dir) && // current links towards target
                    targetCell.isLinkedTo(!dir) && // target links towards current
                    walls[Transform(currentPos, dir)] == Open && // there isn't a wall in the way
                    !power[targetPos.y][targetPos.x] // the target isn't already powered
                ) {
                    power[targetPos.y][targetPos.x] = true // power the target
                    todo.add(targetPos) // expand from there
                }
            }
        }

        var count = 0
        // apply the calculated power to all the cells
        for (y in 0..<height) {
            for (x in 0..<width) {
                val pos = Position(x, y)
                val isPowered = power[y][x]
                doNotUse[pos] =
                    if (isPowered) doNotUse[pos].powered() else doNotUse[pos].unPowered()
                if (isPowered) count += 1
            }
        }

        return count
    }
}