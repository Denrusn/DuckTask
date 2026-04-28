# 悬浮窗美化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 美化 StrongReminderOverlayService 悬浮窗，添加环形填充动画和粒子爆发效果

**Architecture:** 使用 Android View 系统和 ObjectAnimator 实现动画效果，参考 StrongReminderActivity 的设计

**Tech Stack:** Android Views, ObjectAnimator, ValueAnimator, Canvas 绑定绘制

---

## 文件结构

- Modify: `app/src/main/java/com/ducktask/StrongReminderOverlayService.kt` - 重构悬浮窗 UI

---

## Task 1: 简化悬浮窗布局

**Files:**
- Modify: `app/src/main/java/com/ducktask/StrongReminderOverlayService.kt`

- [ ] **Step 1: 读取当前 showOverlay 方法**

确认 `showOverlay` 方法的完整结构。

- [ ] **Step 2: 重构 showOverlay 方法简化布局**

删除：
- "强提醒进行中" 标签
- 大数字倒计时显示
- "操作说明" 卡片
- 线性进度条

保留：
- 深色背景
- 事件名称
- 长按按钮

```kotlin
private fun showOverlay(event: String, description: String) {
    removeOverlay()

    val root = FrameLayout(this).apply {
        setBackgroundColor(Color.parseColor("#E60D0D0D")) // 深色背景
        isClickable = true
        isFocusable = true
    }

    // 发光背景
    val glowView = View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(Color.parseColor("#26FF6B35"), Color.TRANSPARENT)
        }
    }
    root.addView(glowView, FrameLayout.LayoutParams(dp(400), dp(400)).apply {
        gravity = Gravity.CENTER
    })

    // 事件名称
    val eventText = TextView(this).apply {
        text = event.ifBlank { "DuckTask 提醒" }
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        textSize = 32f
        gravity = Gravity.CENTER
    }
    root.addView(eventText, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.CENTER
        topMargin = -dp(80)
    })

    // 环形进度按钮容器（稍后添加）
    val buttonContainer = FrameLayout(this).apply {
        id = R.id.button_container
    }
    root.addView(buttonContainer, FrameLayout.LayoutParams(dp(220), dp(220)).apply {
        gravity = Gravity.CENTER
        topMargin = dp(60)
    })

    // 绑定窗口
    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.CENTER }

    windowManager.addView(root, params)
    overlayView = root
    this.eventText = eventText
    this.glowView = glowView
    this.buttonContainer = buttonContainer

    // 创建环形按钮
    createRingButton(buttonContainer)
}
```

- [ ] **Step 3: 添加成员变量**

在类开头添加：
```kotlin
private var overlayView: View? = null
private var eventText: TextView? = null
private var glowView: View? = null
private var buttonContainer: FrameLayout? = null
private var ringView: RingProgressView? = null
private var buttonText: TextView? = null
private var holdRunnable: Runnable? = null
private var dismissing = false
```

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/ducktask/StrongReminderOverlayService.kt
git commit -m "refactor(overlay): simplify overlay layout - remove redundant elements"
```

---

## Task 2: 创建自定义环形进度视图

**Files:**
- Modify: `app/src/main/java/com/ducktask/StrongReminderOverlayService.kt`

- [ ] **Step 1: 创建 RingProgressView 类**

在 `StrongReminderOverlayService` 类中添加内部类：

```kotlin
class RingProgressView(context: Context) : View(context) {
    private var progress: Float = 0f
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(10f)
        color = Color.parseColor("#33FF6B35")
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(10f)
        color = Color.parseColor("#FFFF6B35")
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#26FF6B35")
    }

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    fun setColor(color: Int) {
        progressPaint.color = color
        backgroundPaint.color = Color.argb(51, Color.red(color), Color.green(color), Color.blue(color))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (minOf(width, height) - backgroundPaint.strokeWidth) / 2f

        // 背景光晕
        canvas.drawCircle(centerX, centerY, radius + dp(20f), glowPaint)

        // 背景圆环
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

        // 进度圆弧
        val rect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        canvas.drawArc(rect, -90f, 360f * progress, false, progressPaint)
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
```

- [ ] **Step 2: 创建 createRingButton 方法**

```kotlin
private fun createRingButton(container: FrameLayout) {
    container.removeAllViews()

    // 环形进度视图
    ringView = RingProgressView(this).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }
    container.addView(ringView)

    // 中心文字容器
    val centerLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
    }

    // 图标
    val iconView = ImageView(this).apply {
        setImageResource(android.R.drawable.ic_menu_compass)
        setColorFilter(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
    }
    centerLayout.addView(iconView)

    // 文字
    buttonText = TextView(this).apply {
        text = "长按解锁"
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        textSize = 18f
        gravity = Gravity.CENTER
    }
    centerLayout.addView(buttonText)
    container.addView(centerLayout)

    // 设置触摸监听
    container.setOnTouchListener { _, event ->
        handleButtonTouch(event)
        true
    }
}
```

- [ ] **Step 3: 修改 handleHoldTouch 为 handleButtonTouch**

```kotlin
private fun handleButtonTouch(event: MotionEvent) {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> startHold()
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelHold()
    }
}
```

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/ducktask/StrongReminderOverlayService.kt
git commit -m "feat(overlay): add RingProgressView custom view for circular progress"
```

---

## Task 3: 实现环形填充动画

**Files:**
- Modify: `app/src/main/java/com/ducktask/StrongReminderOverlayService.kt`

- [ ] **Step 1: 修改 startHold 方法**

```kotlin
private fun startHold() {
    dismissing = false
    buttonText?.text = "保持"
    ringView?.setProgress(0f)
    ringView?.setColor(Color.parseColor("#FFFF6B35"))
    mainHandler.removeCallbacksAndMessages(null)

    // 按钮缩放动画
    buttonContainer?.let { container ->
        ObjectAnimator.ofFloat(container, "scaleX", 1f, 0.94f).apply {
            duration = 120
            start()
        }
        ObjectAnimator.ofFloat(container, "scaleY", 1f, 0.94f).apply {
            duration = 120
            start()
        }
    }

    val startedAt = SystemClock.elapsedRealtime()
    holdRunnable = object : Runnable {
        override fun run() {
            val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtMost(HOLD_DURATION_MS)
            val progress = elapsed / HOLD_DURATION_MS
            ringView?.setProgress(progress)

            if (elapsed >= HOLD_DURATION_MS) {
                dismissing = true
                onComplete()
            } else {
                mainHandler.postDelayed(this, 16)
            }
        }
    }
    mainHandler.post(holdRunnable!!)
}
```

- [ ] **Step 2: 添加 onComplete 方法**

```kotlin
private fun onComplete() {
    // 变绿
    ringView?.setColor(Color.parseColor("#FF4CAF50"))
    buttonText?.text = "完成"

    // 恢复按钮大小
    buttonContainer?.let { container ->
        ObjectAnimator.ofFloat(container, "scaleX", 0.94f, 1.08f).apply {
            duration = 200
            start()
        }
        ObjectAnimator.ofFloat(container, "scaleY", 0.94f, 1.08f).apply {
            duration = 200
            start()
        }
    }

    // 触发粒子效果
    triggerParticleEffect()

    // 延迟关闭
    mainHandler.postDelayed({
        dismissReminder()
    }, 600)
}
```

- [ ] **Step 3: 修改 cancelHold 方法**

```kotlin
private fun cancelHold() {
    if (dismissing) return
    mainHandler.removeCallbacksAndMessages(null)
    ringView?.setProgress(0f)
    buttonText?.text = "长按解锁"

    // 恢复按钮大小
    buttonContainer?.let { container ->
        ObjectAnimator.ofFloat(container, "scaleX", container.scaleX, 1f).apply {
            duration = 120
            start()
        }
        ObjectAnimator.ofFloat(container, "scaleY", container.scaleY, 1f).apply {
            duration = 120
            start()
        }
    }
}
```

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/ducktask/StrongReminderOverlayService.kt
git commit -m "feat(overlay): implement ring fill animation and completion state"
```

---

## Task 4: 实现粒子爆发效果

**Files:**
- Modify: `app/src/main/java/com/ducktask/StrongReminderOverlayService.kt`

- [ ] **Step 1: 添加粒子相关变量**

```kotlin
private var particleViews = mutableListOf<View>()
```

- [ ] **Step 2: 添加 triggerParticleEffect 方法**

```kotlin
private fun triggerParticleEffect() {
    overlayView?.let { root ->
        val centerX = root.width / 2f
        val centerY = root.height / 2f
        val particleCount = 16

        for (i in 0 until particleCount) {
            val angle = (i * 22.5f - 90f) * Math.PI / 180f
            val distance = (100 + Math.random() * 80).toFloat()
            val size = (12 + Math.random() * 16).toFloat()

            val particle = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#FF4CAF50"))
                }
            }
            particle.layoutParams = FrameLayout.LayoutParams(size.toInt(), size.toInt())
            particle.x = centerX - size / 2
            particle.y = centerY - size / 2
            root.addView(particle)
            particleViews.add(particle)

            // 动画
            val targetX = centerX + (distance * Math.cos(angle)).toFloat() - size / 2
            val targetY = centerY + (distance * Math.sin(angle)).toFloat() - size / 2

            particle.animate()
                .x(targetX)
                .y(targetY)
                .alpha(0f)
                .scaleX(0.3f)
                .scaleY(0.3f)
                .setDuration(700)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    root.removeView(particle)
                    particleViews.remove(particle)
                }
                .start()
        }

        // 触发波纹效果
        triggerRippleEffect()
    }
}
```

- [ ] **Step 3: 添加 triggerRippleEffect 方法**

```kotlin
private fun triggerRippleEffect() {
    overlayView?.let { root ->
        val ripple = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(3f), Color.parseColor("#80FF6B35"))
            }
        }
        val size = dp(220f)
        ripple.layoutParams = FrameLayout.LayoutParams(size, size)
        ripple.x = root.width / 2f - size / 2
        ripple.y = root.height / 2f - size / 2
        root.addView(ripple)
        particleViews.add(ripple)

        ripple.animate()
            .scaleX(3f)
            .scaleY(3f)
            .alpha(0f)
            .setDuration(1000)
            .setInterpolator(LinearInterpolator())
            .withEndAction {
                root.removeView(ripple)
                particleViews.remove(ripple)
            }
            .start()
    }
}
```

- [ ] **Step 4: 修改 removeOverlay 方法清理粒子**

```kotlin
private fun removeOverlay() {
    overlayView?.let { view ->
        runCatching { windowManager.removeView(view) }
    }
    overlayView = null
    particleViews.forEach { view ->
        runCatching { (view.parent as? ViewGroup)?.removeView(view) }
    }
    particleViews.clear()
    // ... 其他清理
}
```

- [ ] **Step 5: 提交代码**

```bash
git add app/src/main/java/com/ducktask/StrongReminderOverlayService.kt
git commit -m "feat(overlay): add particle burst and ripple effects on completion"
```

---

## Task 5: 验证并推送

- [ ] **Step 1: 推送代码**

```bash
git push origin HEAD
```

- [ ] **Step 2: 等待构建**

```bash
gh run list --limit 3
```

- [ ] **Step 3: 获取 APK 链接**

---

## Self-Review Checklist

1. **Spec coverage:**
   - [x] 简化悬浮窗布局 - Task 1
   - [x] 环形进度视图 - Task 2
   - [x] 环形填充动画 - Task 3
   - [x] 粒子爆发效果 - Task 4

2. **Placeholder scan:** 无占位符

3. **功能验证:**
   - [ ] 应用在后台时悬浮窗正常弹出
   - [ ] 长按时环形进度从 0 填充到 100%
   - [ ] 完成时粒子向四周爆发
   - [ ] 波纹从按钮中心向外扩散
