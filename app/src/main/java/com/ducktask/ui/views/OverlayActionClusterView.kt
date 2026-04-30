package com.ducktask.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Cap
import android.graphics.Paint.Style
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Standalone animated ring button view for strong reminders.
 * Extracted from StrongReminderOverlayService for reuse in FullScreenAlarmActivity.
 */
class OverlayActionClusterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress: Float = 0f
    private var accentColor: Int = DEFAULT_ACCENT_COLOR
    private var holdState: HoldState = HoldState.IDLE
    private var orbitRotation: Float = 0f
    private var ambientPulse: Float = 0f
    private var chargedPulse: Float = 0f
    private var completionProgress: Float = 0f

    private val ringRect = RectF()
    private val sliceRect = RectF()
    private val innerArcRect = RectF()
    private val outerArcRect = RectF()

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Style.FILL }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Style.STROKE
        strokeCap = Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Style.STROKE
        strokeCap = Cap.ROUND
    }
    private val trailingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Style.STROKE
        strokeCap = Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Style.STROKE
        strokeCap = Cap.ROUND
    }
    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Style.STROKE
        strokeCap = Cap.ROUND
    }
    private val spokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Style.STROKE
        strokeCap = Cap.ROUND
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Style.FILL }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Style.FILL }
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Style.STROKE
        strokeCap = Cap.ROUND
    }
    private val accentStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Style.STROKE
        strokeCap = Cap.ROUND
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Style.FILL }
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Style.STROKE
        strokeCap = Cap.ROUND
    }
    private val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Style.STROKE
        strokeCap = Cap.ROUND
    }

    override fun hasOverlappingRendering(): Boolean = false

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    fun setAccentColor(color: Int) {
        accentColor = color
        invalidate()
    }

    fun setHoldState(value: HoldState) {
        holdState = value
        invalidate()
    }

    fun setOrbitRotation(value: Float) {
        orbitRotation = value
        invalidate()
    }

    fun setAmbientPulse(value: Float) {
        ambientPulse = value.coerceIn(0f, 1f)
        invalidate()
    }

    fun setChargedPulse(value: Float) {
        chargedPulse = value.coerceIn(0f, 1f)
        invalidate()
    }

    fun setCompletionProgress(value: Float) {
        completionProgress = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height * 0.42f
        val ringRadius = minOf(width, height) * 0.315f
        val statePulse = when (holdState) {
            HoldState.IDLE -> ambientPulse
            HoldState.CHARGING -> progress
            HoldState.CHARGED_WAITING_RELEASE -> chargedPulse
            HoldState.COMPLETING -> 1f - completionProgress * 0.22f
        }

        configurePaints()
        drawHalo(canvas, centerX, centerY, ringRadius, statePulse)
        drawEnergySlices(canvas, centerX, centerY, ringRadius)
        drawRing(canvas, centerX, centerY, ringRadius)
        drawTickMarks(canvas, centerX, centerY, ringRadius)
        drawEnergySpokes(canvas, centerX, centerY, ringRadius)
        drawOrbitSparks(canvas, centerX, centerY, ringRadius)
        drawCoreGlyph(canvas, centerX, centerY, ringRadius)
        drawCrossFlare(canvas, centerX, centerY, ringRadius)
        drawBurst(canvas, centerX, centerY, ringRadius)
    }

    private fun configurePaints() {
        backgroundPaint.strokeWidth = dp(10f)
        backgroundPaint.color = Color.argb(46, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        progressPaint.strokeWidth = dp(12f)
        progressPaint.color = accentColor
        trailingPaint.strokeWidth = dp(7f)
        trailingPaint.color = Color.argb(190, 255, 255, 255)
        tickPaint.strokeWidth = dp(2f)
        tickPaint.color = Color.WHITE
        slicePaint.strokeWidth = dp(10f)
        slicePaint.color = Color.argb(150, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        spokePaint.strokeWidth = dp(3f)
        spokePaint.color = Color.argb(170, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        corePaint.color = Color.argb(180, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        shellPaint.strokeWidth = dp(4f)
        shellPaint.color = Color.argb(210, 255, 255, 255)
        accentStrokePaint.strokeWidth = dp(5f)
        accentStrokePaint.color = accentColor
        burstPaint.strokeWidth = dp(4f)
        burstPaint.color = Color.WHITE
        flarePaint.strokeWidth = dp(2.6f)
        flarePaint.color = Color.WHITE
    }

    private fun drawHalo(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, statePulse: Float) {
        val haloAlpha = when (holdState) {
            HoldState.IDLE -> (54 + ambientPulse * 26).toInt()
            HoldState.CHARGING -> (72 + progress * 56).toInt()
            HoldState.CHARGED_WAITING_RELEASE -> (120 + chargedPulse * 72).toInt()
            HoldState.COMPLETING -> (220 - completionProgress * 130).toInt()
        }.coerceIn(44, 220)
        val haloRadius = radius + dp(32f) + statePulse * dp(18f)
        glowPaint.color = Color.argb(
            haloAlpha,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor)
        )
        canvas.drawCircle(centerX, centerY, haloRadius, glowPaint)
    }

    private fun drawEnergySlices(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val sliceCount = when (holdState) {
            HoldState.IDLE -> 0
            HoldState.CHARGING -> 3
            HoldState.CHARGED_WAITING_RELEASE -> 6
            HoldState.COMPLETING -> 8
        }
        if (sliceCount == 0) return
        val sliceAlpha = when (holdState) {
            HoldState.CHARGING -> (70 + progress * 110).toInt()
            HoldState.CHARGED_WAITING_RELEASE -> (150 + chargedPulse * 90).toInt()
            HoldState.COMPLETING -> (230 - completionProgress * 150).toInt()
            HoldState.IDLE -> 0
        }.coerceIn(0, 240)
        slicePaint.color = Color.argb(
            sliceAlpha,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor)
        )
        slicePaint.strokeWidth = when (holdState) {
            HoldState.CHARGING -> dp(8f + progress * 3f)
            HoldState.CHARGED_WAITING_RELEASE -> dp(12f + chargedPulse * 5f)
            HoldState.COMPLETING -> dp(16f - completionProgress * 5f)
            HoldState.IDLE -> dp(0f)
        }
        sliceRect.set(
            centerX - radius - dp(18f),
            centerY - radius - dp(18f),
            centerX + radius + dp(18f),
            centerY + radius + dp(18f)
        )
        val sweep = when (holdState) {
            HoldState.CHARGING -> 20f + progress * 18f
            HoldState.CHARGED_WAITING_RELEASE -> 28f + chargedPulse * 18f
            HoldState.COMPLETING -> 34f - completionProgress * 10f
            HoldState.IDLE -> 0f
        }
        for (i in 0 until sliceCount) {
            val start = orbitRotation * 0.9f + i * (360f / sliceCount) - 90f
            canvas.drawArc(sliceRect, start, sweep, false, slicePaint)
        }
    }

    private fun drawRing(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        ringRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        canvas.drawCircle(centerX, centerY, radius + dp(10f), backgroundPaint)
        if (progress > 0f) {
            canvas.drawArc(ringRect, -90f, 360f * progress, false, progressPaint)
            val sweep = 44f + progress * 42f
            trailingPaint.alpha = (86 + progress * 110).toInt().coerceAtMost(220)
            canvas.drawArc(ringRect, -90f + (360f * progress) - sweep, sweep, false, trailingPaint)
        }
        if (holdState == HoldState.CHARGED_WAITING_RELEASE || holdState == HoldState.COMPLETING) {
            trailingPaint.alpha = when (holdState) {
                HoldState.CHARGED_WAITING_RELEASE -> (130 + chargedPulse * 90).toInt()
                HoldState.COMPLETING -> (200 - completionProgress * 120).toInt()
                else -> 0
            }.coerceIn(0, 230)
            canvas.drawArc(ringRect, orbitRotation - 114f, 84f, false, trailingPaint)
            canvas.drawArc(ringRect, -orbitRotation - 44f, 48f, false, trailingPaint)
        }
    }

    private fun drawTickMarks(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val totalMarks = 48
        for (i in 0 until totalMarks) {
            val markProgress = i / totalMarks.toFloat()
            val isActive = progress >= markProgress
            val alpha = when {
                holdState == HoldState.CHARGED_WAITING_RELEASE -> (120 + chargedPulse * 110).toInt()
                holdState == HoldState.COMPLETING -> (200 - completionProgress * 120).toInt()
                isActive -> 170
                else -> 40
            }.coerceIn(24, 220)
            tickPaint.color = Color.argb(alpha, 255, 255, 255)
            val angle = Math.toRadians((i * (360.0 / totalMarks) - 90.0) + orbitRotation * 0.04)
            val inner = radius + if (i % 4 == 0) dp(6f) else dp(10f)
            val outer = radius + if (i % 4 == 0) dp(18f) else dp(14f)
            val startX = centerX + cos(angle).toFloat() * inner
            val startY = centerY + sin(angle).toFloat() * inner
            val endX = centerX + cos(angle).toFloat() * outer
            val endY = centerY + sin(angle).toFloat() * outer
            canvas.drawLine(startX, startY, endX, endY, tickPaint)
        }
    }

    private fun drawEnergySpokes(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val spokeCount = when (holdState) {
            HoldState.IDLE -> 0
            HoldState.CHARGING -> 6
            HoldState.CHARGED_WAITING_RELEASE -> 10
            HoldState.COMPLETING -> 12
        }
        if (spokeCount == 0) return
        val lineAlpha = when (holdState) {
            HoldState.CHARGING -> (55 + progress * 100).toInt()
            HoldState.CHARGED_WAITING_RELEASE -> (120 + chargedPulse * 90).toInt()
            HoldState.COMPLETING -> (210 - completionProgress * 130).toInt()
            HoldState.IDLE -> 0
        }.coerceIn(0, 230)
        spokePaint.color = Color.argb(
            lineAlpha,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor)
        )
        spokePaint.strokeWidth = when (holdState) {
            HoldState.CHARGING -> dp(2.2f + progress * 1.2f)
            HoldState.CHARGED_WAITING_RELEASE -> dp(3.2f + chargedPulse * 1.4f)
            HoldState.COMPLETING -> dp(4.1f - completionProgress * 1.6f)
            HoldState.IDLE -> dp(0f)
        }
        for (i in 0 until spokeCount) {
            val angle = Math.toRadians((orbitRotation * 0.75f + i * (360.0 / spokeCount)) - 90.0)
            val inner = radius * (0.34f + chargedPulse * 0.04f)
            val outer = radius + dp(
                when (holdState) {
                    HoldState.CHARGING -> 12f + progress * 10f
                    HoldState.CHARGED_WAITING_RELEASE -> 18f + chargedPulse * 16f
                    HoldState.COMPLETING -> 22f + completionProgress * 22f
                    HoldState.IDLE -> 0f
                }
            )
            val startX = centerX + cos(angle).toFloat() * inner
            val startY = centerY + sin(angle).toFloat() * inner
            val endX = centerX + cos(angle).toFloat() * outer
            val endY = centerY + sin(angle).toFloat() * outer
            canvas.drawLine(startX, startY, endX, endY, spokePaint)
        }
    }

    private fun drawOrbitSparks(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val sparkCount = when (holdState) {
            HoldState.CHARGED_WAITING_RELEASE -> 8
            HoldState.COMPLETING -> 10
            else -> 5
        }
        for (i in 0 until sparkCount) {
            val angleDegrees = orbitRotation + i * (360f / sparkCount)
            val angle = Math.toRadians(angleDegrees.toDouble())
            val sparkRadius = radius + if (i % 2 == 0) dp(10f) else dp(18f)
            val x = centerX + cos(angle).toFloat() * sparkRadius
            val y = centerY + sin(angle).toFloat() * sparkRadius
            val size = when (holdState) {
                HoldState.CHARGED_WAITING_RELEASE -> dp(4f + chargedPulse * 3f)
                HoldState.COMPLETING -> dp(4.5f + (1f - completionProgress) * 3.5f)
                else -> dp(3f + progress * 2f)
            }
            val alpha = when (holdState) {
                HoldState.CHARGING -> (90 + progress * 120).toInt()
                HoldState.CHARGED_WAITING_RELEASE -> (150 + chargedPulse * 90).toInt()
                HoldState.COMPLETING -> (230 - completionProgress * 140).toInt()
                HoldState.IDLE -> (70 + ambientPulse * 60).toInt()
            }.coerceIn(50, 235)
            sparkPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(x, y, size, sparkPaint)
        }
    }

    private fun drawCoreGlyph(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val glyphRadius = radius * 0.46f
        val pulseScale = when (holdState) {
            HoldState.IDLE -> 0.9f + ambientPulse * 0.08f
            HoldState.CHARGING -> 0.92f + progress * 0.15f + ambientPulse * 0.04f
            HoldState.CHARGED_WAITING_RELEASE -> 1f + chargedPulse * 0.12f
            HoldState.COMPLETING -> 1.05f + (1f - completionProgress) * 0.16f
        }
        corePaint.alpha = when (holdState) {
            HoldState.IDLE -> 114
            HoldState.CHARGING -> (136 + progress * 74).toInt()
            HoldState.CHARGED_WAITING_RELEASE -> (176 + chargedPulse * 60).toInt()
            HoldState.COMPLETING -> (255 - completionProgress * 90).toInt()
        }.coerceIn(96, 255)
        canvas.drawCircle(centerX, centerY, glyphRadius * pulseScale, corePaint)

        val outerRadius = glyphRadius * (1.04f + when (holdState) {
            HoldState.IDLE -> ambientPulse * 0.06f
            HoldState.CHARGING -> progress * 0.08f
            HoldState.CHARGED_WAITING_RELEASE -> chargedPulse * 0.12f
            HoldState.COMPLETING -> (1f - completionProgress) * 0.16f
        })
        canvas.drawCircle(centerX, centerY, outerRadius, accentStrokePaint)

        shellPaint.color = Color.argb(
            when (holdState) {
                HoldState.IDLE -> 138
                HoldState.CHARGING -> (160 + progress * 55).toInt()
                HoldState.CHARGED_WAITING_RELEASE -> (210 + chargedPulse * 30).toInt()
                HoldState.COMPLETING -> (255 - completionProgress * 70).toInt()
            }.coerceIn(120, 255),
            255,
            255,
            255
        )
        innerArcRect.set(
            centerX - glyphRadius * 0.88f,
            centerY - glyphRadius * 0.88f,
            centerX + glyphRadius * 0.88f,
            centerY + glyphRadius * 0.88f
        )
        outerArcRect.set(
            centerX - glyphRadius * 1.26f,
            centerY - glyphRadius * 1.26f,
            centerX + glyphRadius * 1.26f,
            centerY + glyphRadius * 1.26f
        )
        canvas.drawArc(innerArcRect, 214f, 112f, false, shellPaint)
        canvas.drawArc(outerArcRect, 218f, 104f, false, shellPaint)
        canvas.drawArc(innerArcRect, -146f, 112f, false, shellPaint)
        canvas.drawArc(outerArcRect, -142f, 104f, false, shellPaint)

        detailPaint.color = Color.argb(
            if (holdState == HoldState.COMPLETING) 255 else 230,
            255,
            255,
            255
        )
        canvas.drawLine(centerX, centerY - glyphRadius * 0.68f, centerX, centerY + glyphRadius * 0.16f, accentStrokePaint)
        canvas.drawCircle(centerX, centerY + glyphRadius * 0.48f, glyphRadius * 0.16f, detailPaint)

        if (holdState != HoldState.IDLE) {
            val rayLength = glyphRadius * when (holdState) {
                HoldState.CHARGING -> 1.5f + progress * 0.2f
                HoldState.CHARGED_WAITING_RELEASE -> 1.7f + chargedPulse * 0.24f
                HoldState.COMPLETING -> 1.85f + (1f - completionProgress) * 0.22f
                HoldState.IDLE -> 0f
            }
            shellPaint.color = Color.argb(
                when (holdState) {
                    HoldState.CHARGING -> (92 + progress * 82).toInt()
                    HoldState.CHARGED_WAITING_RELEASE -> (165 + chargedPulse * 72).toInt()
                    HoldState.COMPLETING -> (245 - completionProgress * 130).toInt()
                    HoldState.IDLE -> 0
                }.coerceIn(0, 255),
                255,
                255,
                255
            )
            canvas.drawLine(centerX - rayLength, centerY, centerX + rayLength, centerY, shellPaint)
            canvas.drawLine(centerX, centerY - rayLength, centerX, centerY + rayLength, shellPaint)
        }
    }

    private fun drawCrossFlare(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        if (holdState == HoldState.IDLE) return
        flarePaint.alpha = when (holdState) {
            HoldState.CHARGING -> (75 + progress * 90).toInt()
            HoldState.CHARGED_WAITING_RELEASE -> (165 + chargedPulse * 60).toInt()
            HoldState.COMPLETING -> (255 - completionProgress * 150).toInt()
            HoldState.IDLE -> 0
        }.coerceIn(0, 255)
        flarePaint.strokeWidth = when (holdState) {
            HoldState.CHARGING -> dp(1.8f + progress * 0.8f)
            HoldState.CHARGED_WAITING_RELEASE -> dp(2.8f + chargedPulse * 1.2f)
            HoldState.COMPLETING -> dp(4f - completionProgress * 1.5f)
            HoldState.IDLE -> dp(0f)
        }
        val crossRadius = radius * when (holdState) {
            HoldState.CHARGING -> 0.44f + progress * 0.08f
            HoldState.CHARGED_WAITING_RELEASE -> 0.5f + chargedPulse * 0.08f
            HoldState.COMPLETING -> 0.58f + (1f - completionProgress) * 0.1f
            HoldState.IDLE -> 0f
        }
        canvas.drawLine(centerX - crossRadius, centerY, centerX + crossRadius, centerY, flarePaint)
        canvas.drawLine(centerX, centerY - crossRadius, centerX, centerY + crossRadius, flarePaint)
        if (holdState != HoldState.CHARGING) {
            val diagAngle = Math.toRadians((orbitRotation * 0.5f).toDouble())
            val dx = cos(diagAngle).toFloat() * crossRadius * 0.82f
            val dy = sin(diagAngle).toFloat() * crossRadius * 0.82f
            canvas.drawLine(centerX - dx, centerY - dy, centerX + dx, centerY + dy, flarePaint)
            canvas.drawLine(centerX - dy, centerY + dx, centerX + dy, centerY - dx, flarePaint)
        }
    }

    private fun drawBurst(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        if (holdState != HoldState.COMPLETING && completionProgress <= 0f) return
        val flashProgress = if (completionProgress < 0.24f) completionProgress / 0.24f else 1f - ((completionProgress - 0.24f) / 0.76f)
        val flashAlpha = (flashProgress.coerceIn(0f, 1f) * 180).toInt()
        sparkPaint.color = Color.argb(flashAlpha, 255, 255, 255)
        canvas.drawCircle(centerX, centerY, radius * (0.36f + flashProgress * 0.2f), sparkPaint)

        burstPaint.alpha = (225 - completionProgress * 180).toInt().coerceAtLeast(0)
        val firstRadius = radius + dp(12f) + completionProgress * dp(96f)
        val secondRadius = radius + dp(4f) + completionProgress * dp(154f)
        canvas.drawCircle(centerX, centerY, firstRadius, burstPaint)
        burstPaint.alpha = (165 - completionProgress * 140).toInt().coerceAtLeast(0)
        canvas.drawCircle(centerX, centerY, secondRadius, burstPaint)

        flarePaint.alpha = (220 - completionProgress * 180).toInt().coerceAtLeast(0)
        flarePaint.strokeWidth = dp(3.4f)
        val shardCount = 12
        val inner = radius * 0.76f
        val outer = radius + dp(28f) + completionProgress * dp(118f)
        for (i in 0 until shardCount) {
            val angle = Math.toRadians((orbitRotation * 0.35f + i * (360.0 / shardCount) - 90.0))
            val startX = centerX + cos(angle).toFloat() * inner
            val startY = centerY + sin(angle).toFloat() * inner
            val endX = centerX + cos(angle).toFloat() * outer
            val endY = centerY + sin(angle).toFloat() * outer
            canvas.drawLine(startX, startY, endX, endY, flarePaint)
        }
    }

    private fun dp(value: Float): Float {
        return value * context.resources.displayMetrics.density
    }

    companion object {
        const val DEFAULT_ACCENT_COLOR = 0xFFFF6B35.toInt()
    }
}

/**
 * Hold state for the action cluster view.
 */
enum class HoldState {
    IDLE,
    CHARGING,
    CHARGED_WAITING_RELEASE,
    COMPLETING
}