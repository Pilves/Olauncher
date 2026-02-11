package app.olauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import app.olauncher.R
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Transparent overlay view for detecting letter-shaped gestures on the home screen.
 *
 * When [isGestureLettersEnabled] is true, this view intercepts touch events to collect
 * path points. On ACTION_UP the collected path is analyzed to detect a letter shape.
 * A faint trail is drawn during the gesture.
 *
 * When disabled or when no significant drag is detected, touch events pass through
 * to views below.
 */
class GestureLetterOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Callback invoked when a letter is recognized. */
    var onLetterDetected: ((Char) -> Unit)? = null

    /** When false, all touch events pass through to views below. */
    var isGestureLettersEnabled: Boolean = false

    private val points = mutableListOf<PointF>()
    private val drawPath = Path()
    private var isTracking = false
    private var hasExceededThreshold = false
    private var startX = 0f
    private var startY = 0f

    private val dragThreshold = 30.dpToPx().toFloat()

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColorFromAttr(R.attr.primaryColor)
        alpha = 102 // ~40% of 255
        style = Paint.Style.STROKE
        strokeWidth = 3.dpToPx().toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Called externally (e.g. from the swipe listener) to forward touch events
     * for gesture tracking without consuming them in the view hierarchy.
     */
    fun forwardTouchEvent(event: MotionEvent) {
        if (!isGestureLettersEnabled) return
        onTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isGestureLettersEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                points.clear()
                points.add(PointF(event.x, event.y))
                drawPath.reset()
                drawPath.moveTo(event.x, event.y)
                isTracking = true
                hasExceededThreshold = false
                // Do not consume ACTION_DOWN yet; let parent also see it.
                // We will consume subsequent events once the drag threshold is met.
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isTracking) return false

                points.add(PointF(event.x, event.y))
                drawPath.lineTo(event.x, event.y)

                if (!hasExceededThreshold) {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    if (hypot(dx, dy) >= dragThreshold) {
                        hasExceededThreshold = true
                        // Request that parent not intercept further events
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                if (hasExceededThreshold) {
                    invalidate()
                    return true
                }
                return false
            }

            MotionEvent.ACTION_UP -> {
                if (!isTracking) return false
                isTracking = false
                val wasExceeded = hasExceededThreshold

                if (wasExceeded) {
                    points.add(PointF(event.x, event.y))
                    val letter = analyzeGesture(points)
                    if (letter != null) {
                        onLetterDetected?.invoke(letter)
                    }
                }

                points.clear()
                drawPath.reset()
                hasExceededThreshold = false
                invalidate()
                return wasExceeded
            }

            MotionEvent.ACTION_CANCEL -> {
                isTracking = false
                hasExceededThreshold = false
                points.clear()
                drawPath.reset()
                invalidate()
                return false
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isTracking && hasExceededThreshold && !drawPath.isEmpty) {
            canvas.drawPath(drawPath, trailPaint)
        }
    }

    // --- Gesture analysis ---

    /** Directions used for classifying gesture segments. */
    private enum class Direction {
        RIGHT, UP_RIGHT, UP, UP_LEFT, LEFT, DOWN_LEFT, DOWN, DOWN_RIGHT
    }

    /**
     * Analyzes collected points to detect a letter shape.
     * Returns the recognized letter character, or null if no match.
     */
    private fun analyzeGesture(pts: List<PointF>): Char? {
        if (pts.size < 3) return null

        // Check for circular "O" first
        if (isCircular(pts)) return 'O'

        val directions = extractDirections(pts)
        if (directions.isEmpty()) return null

        return matchDirectionPattern(directions, pts)
    }

    /**
     * Detects circular motion: start and end points are close together and the
     * path covers a meaningful area.
     */
    private fun isCircular(pts: List<PointF>): Boolean {
        if (pts.size < 8) return false

        val first = pts.first()
        val last = pts.last()
        val closeDist = hypot(
            (last.x - first.x).toDouble(),
            (last.y - first.y).toDouble()
        )

        // Bounding box of the path
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for (p in pts) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }

        val bboxWidth = maxX - minX
        val bboxHeight = maxY - minY
        val bboxDiag = hypot(bboxWidth.toDouble(), bboxHeight.toDouble())

        // Start/end must be within 30% of bounding box diagonal
        if (closeDist > bboxDiag * 0.35) return false

        // Must have reasonable area (not a thin line)
        val minDimension = 20.dpToPx().toFloat()
        return bboxWidth > minDimension && bboxHeight > minDimension
    }

    /**
     * Simplifies the point list into segments and classifies each segment's direction.
     * Uses an angle-based approach to classify into 8 directions.
     */
    private fun extractDirections(pts: List<PointF>): List<Direction> {
        // Resample to roughly evenly-spaced segments
        val segmentLength = 30.dpToPx().toFloat()
        val resampled = resamplePoints(pts, segmentLength)
        if (resampled.size < 2) return emptyList()

        val rawDirections = mutableListOf<Direction>()
        for (i in 0 until resampled.size - 1) {
            val dx = resampled[i + 1].x - resampled[i].x
            val dy = resampled[i + 1].y - resampled[i].y
            if (hypot(dx, dy) < 1f) continue
            rawDirections.add(classifyAngle(dx, dy))
        }

        // Collapse consecutive identical directions
        if (rawDirections.isEmpty()) return emptyList()
        val collapsed = mutableListOf(rawDirections[0])
        for (i in 1 until rawDirections.size) {
            if (rawDirections[i] != collapsed.last()) {
                collapsed.add(rawDirections[i])
            }
        }

        return collapsed
    }

    /**
     * Resamples a point list so that consecutive points are approximately
     * [segmentLength] apart.
     */
    private fun resamplePoints(pts: List<PointF>, segmentLength: Float): List<PointF> {
        if (pts.size < 2) return pts.toList()

        val result = mutableListOf(pts[0])
        var accumulated = 0f

        for (i in 1 until pts.size) {
            val dist = hypot(
                (pts[i].x - pts[i - 1].x),
                (pts[i].y - pts[i - 1].y)
            )
            accumulated += dist
            if (accumulated >= segmentLength) {
                result.add(pts[i])
                accumulated = 0f
            }
        }

        // Always include the last point
        if (result.size > 1 && result.last() != pts.last()) {
            result.add(pts.last())
        }

        return result
    }

    /**
     * Classifies a vector (dx, dy) into one of 8 directions.
     * Note: screen coordinate system has Y increasing downward.
     */
    private fun classifyAngle(dx: Float, dy: Float): Direction {
        // atan2 with inverted Y for screen coords
        val angle = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
        // Normalize to 0..360
        val normalized = ((angle % 360) + 360) % 360

        return when {
            normalized < 22.5 || normalized >= 337.5 -> Direction.RIGHT
            normalized < 67.5 -> Direction.UP_RIGHT
            normalized < 112.5 -> Direction.UP
            normalized < 157.5 -> Direction.UP_LEFT
            normalized < 202.5 -> Direction.LEFT
            normalized < 247.5 -> Direction.DOWN_LEFT
            normalized < 292.5 -> Direction.DOWN
            else -> Direction.DOWN_RIGHT
        }
    }

    /**
     * Matches a direction sequence against known letter patterns.
     */
    private fun matchDirectionPattern(directions: List<Direction>, pts: List<PointF>): Char? {
        val d = directions

        // V: DOWN_RIGHT, UP_RIGHT (or DOWN, UP_RIGHT or DOWN_RIGHT, UP)
        if (d.size == 2 && isDownish(d[0]) && isUpish(d[1]) &&
            hasRightComponent(d[0]) != false && hasRightComponent(d[1]) != false
        ) return 'V'

        // Also accept V as 3 segments with middle being bottom transition
        if (d.size == 3 && isDownish(d[0]) && isRightish(d[1]) && isUpish(d[2])) return 'V'

        // L: DOWN, RIGHT
        if (d.size == 2 && isDownish(d[0]) && isRightish(d[1])) return 'L'

        // Z: RIGHT, DOWN_LEFT, RIGHT
        if (d.size == 3 && isRightish(d[0]) && d[1] == Direction.DOWN_LEFT && isRightish(d[2])) return 'Z'
        if (d.size == 3 && isRightish(d[0]) && isDownLeftish(d[1]) && isRightish(d[2])) return 'Z'

        // S: LEFT, DOWN_RIGHT, LEFT (or RIGHT, DOWN_LEFT, RIGHT reversed)
        if (d.size == 3 && isLeftish(d[0]) && isDownRightish(d[1]) && isLeftish(d[2])) return 'S'
        if (d.size == 3 && isRightish(d[0]) && isDownRightish(d[1]) && isRightish(d[2])) return 'S'

        // N: UP, DOWN_RIGHT, UP (zigzag up)
        if (d.size == 3 && isUpish(d[0]) && isDownRightish(d[1]) && isUpish(d[2])) return 'N'
        if (d.size == 3 && isUpish(d[0]) && isDownish(d[1]) && isUpish(d[2])) return 'N'

        // C: LEFT, DOWN, RIGHT (or DOWN_LEFT, DOWN_RIGHT)
        if (d.size == 3 && isLeftish(d[0]) && isDownish(d[1]) && isRightish(d[2])) return 'C'
        if (d.size == 2 && isDownLeftish(d[0]) && isDownRightish(d[1])) return 'C'
        if (d.size == 2 && isLeftish(d[0]) && isDownRightish(d[1])) return 'C'
        if (d.size == 2 && isDownLeftish(d[0]) && isRightish(d[1])) return 'C'

        // M: UP, DOWN_RIGHT, UP_RIGHT, DOWN (or UP, DOWN, UP, DOWN)
        if (d.size == 4 && isUpish(d[0]) && isDownRightish(d[1]) &&
            isUpRightish(d[2]) && isDownish(d[3])
        ) return 'M'
        if (d.size == 4 && isUpish(d[0]) && isDownish(d[1]) &&
            isUpish(d[2]) && isDownish(d[3])
        ) return 'M'

        // W: DOWN, UP_RIGHT, DOWN, UP_RIGHT (or DOWN, UP, DOWN, UP)
        if (d.size == 4 && isDownish(d[0]) && isUpRightish(d[1]) &&
            isDownish(d[2]) && isUpRightish(d[3])
        ) return 'W'
        if (d.size == 4 && isDownish(d[0]) && isUpish(d[1]) &&
            isDownish(d[2]) && isUpish(d[3])
        ) return 'W'

        // A: UP_RIGHT, DOWN_RIGHT (simplified inverted V)
        if (d.size == 2 && isUpish(d[0]) && isDownish(d[1]) &&
            hasRightComponent(d[0]) != false && hasRightComponent(d[1]) != false
        ) return 'A'
        if (d.size == 2 && d[0] == Direction.UP_RIGHT && d[1] == Direction.DOWN_RIGHT) return 'A'

        return null
    }

    // --- Direction classification helpers ---

    private fun isUpish(d: Direction): Boolean =
        d == Direction.UP || d == Direction.UP_LEFT || d == Direction.UP_RIGHT

    private fun isDownish(d: Direction): Boolean =
        d == Direction.DOWN || d == Direction.DOWN_LEFT || d == Direction.DOWN_RIGHT

    private fun isRightish(d: Direction): Boolean =
        d == Direction.RIGHT || d == Direction.UP_RIGHT || d == Direction.DOWN_RIGHT

    private fun isLeftish(d: Direction): Boolean =
        d == Direction.LEFT || d == Direction.UP_LEFT || d == Direction.DOWN_LEFT

    private fun isDownRightish(d: Direction): Boolean =
        d == Direction.DOWN_RIGHT || d == Direction.DOWN || d == Direction.RIGHT

    private fun isDownLeftish(d: Direction): Boolean =
        d == Direction.DOWN_LEFT || d == Direction.DOWN || d == Direction.LEFT

    private fun isUpRightish(d: Direction): Boolean =
        d == Direction.UP_RIGHT || d == Direction.UP || d == Direction.RIGHT

    /**
     * Returns true if the direction has a right component, false if it has a left component,
     * or null if purely vertical.
     */
    private fun hasRightComponent(d: Direction): Boolean? = when (d) {
        Direction.RIGHT, Direction.UP_RIGHT, Direction.DOWN_RIGHT -> true
        Direction.LEFT, Direction.UP_LEFT, Direction.DOWN_LEFT -> false
        Direction.UP, Direction.DOWN -> null
    }
}
