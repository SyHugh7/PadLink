package com.padlink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padlink.app.PadLinkController
import com.padlink.app.layout.DPadStyle
import com.padlink.app.layout.PadElement
import com.padlink.app.layout.PadElementKind
import com.padlink.app.layout.PadLayout
import com.padlink.app.layout.SubKey
import com.padlink.app.layout.TriggerStyle
import com.padlink.app.ui.theme.LocalPadPalette
import com.padlink.core.DPad
import com.padlink.core.GamepadButtons
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 画布根手势命中的输入类型。 */
private sealed interface RootHit {
    data class Button(val bit: Int) : RootHit
    data class Stick(val left: Boolean) : RootHit
    data class Trigger(val left: Boolean) : RootHit
    object DPad : RootHit
}

/** 编辑模式下选中的东西：整个控件，或某个控件里的一个子键。 */
sealed interface Selection {
    data class Element(val kind: PadElementKind) : Selection
    data class Sub(val kind: PadElementKind, val index: Int) : Selection
}

/**
 * 拖动判定阈值。
 *
 * 取 24dp 是有教训的：之前写 12px，在 524dpi 的屏幕上只有 ~2dp，
 * 手指按下去想"选中"控件，稍微抖一下就被当成拖动，**布局会被误拖乱**。
 */
private val DRAG_SLOP = 24.dp

/**
 * 按 [PadLayout] 摆放所有控件。
 *
 * [editing] 为 true 时进入布局编辑：控件只画轮廓、不响应游戏输入。
 * 点一下选中，拖一下挪位置；带子键的控件（ABXY / 三角十字）连里面的每个键都能单独选。
 */
@Composable
fun GamepadCanvas(
    controller: PadLinkController,
    layout: PadLayout,
    editing: Boolean,
    selection: Selection?,
    onSelectionChange: (Selection?) -> Unit,
    onLayoutChange: (PadLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rootLayout by rememberUpdatedState(layout)

    // 根手势当前命中的离散按键；控件用它显示按下态，手指滑过时也能换键。
    var rootPressed by remember { mutableStateOf<Set<Int>>(emptySet()) }

    BoxWithConstraints(
        modifier.then(
            if (editing) {
                Modifier
            } else {
                // 手指滑过哪个离散按键就触发哪个——命中测试放在画布根节点，
                // 不跟某一个按键的 pointerInput 绑定。
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        val active = mutableMapOf<PointerId, Int>()
                        val stickActive = mutableMapOf<PointerId, Boolean>()
                        val triggerActive = mutableMapOf<PointerId, Boolean>()
                        val dpadActive = mutableSetOf<PointerId>()
                        val input = controller.input

                        fun bitAt(position: Offset): Int? {
                            val current = rootLayout
                            val canvasWidth = size.width.toFloat()
                            val canvasHeight = size.height.toFloat()

                            for (element in current.elements) {
                                if (!element.enabled) continue

                                val cx = canvasWidth * element.x
                                val cy = canvasHeight * element.y
                                val w = canvasWidth * element.size
                                val h = w * current.aspectOf(element.kind)
                                val angle = Math.toRadians(-element.rotation.toDouble())
                                val cosA = cos(angle).toFloat()
                                val sinA = sin(angle).toFloat()
                                val dx = position.x - cx
                                val dy = position.y - cy
                                val lx = dx * cosA - dy * sinA
                                val ly = dx * sinA + dy * cosA

                                when (element.kind) {
                                    PadElementKind.ABXY_GROUP -> {
                                        for (key in current.subKeys[element.kind].orEmpty()) {
                                            if (!key.enabled) continue
                                            val bit = AbxyBits[key.label] ?: continue
                                            val kx = key.dx * w
                                            val ky = key.dy * h
                                            val kr = key.scale * w / 2f
                                            if (hypot(lx - kx, ly - ky) <= kr) return bit
                                        }
                                    }

                                    PadElementKind.SHOULDER_LEFT ->
                                        if (abs(lx) <= w / 2f && abs(ly) <= h / 2f) {
                                            return GamepadButtons.LEFT_SHOULDER
                                        }

                                    PadElementKind.SHOULDER_RIGHT ->
                                        if (abs(lx) <= w / 2f && abs(ly) <= h / 2f) {
                                            return GamepadButtons.RIGHT_SHOULDER
                                        }

                                    PadElementKind.BACK ->
                                        if (hypot(lx, ly) <= minOf(w, h) / 2f) return GamepadButtons.BACK

                                    PadElementKind.START ->
                                        if (hypot(lx, ly) <= minOf(w, h) / 2f) return GamepadButtons.START

                                    PadElementKind.GUIDE ->
                                        if (hypot(lx, ly) <= minOf(w, h) / 2f) return GamepadButtons.GUIDE

                                    PadElementKind.STICK_LEFT_BUTTON ->
                                        if (hypot(lx, ly) <= minOf(w, h) / 2f) {
                                            return GamepadButtons.LEFT_THUMB
                                        }

                                    PadElementKind.STICK_RIGHT_BUTTON ->
                                        if (hypot(lx, ly) <= minOf(w, h) / 2f) {
                                            return GamepadButtons.RIGHT_THUMB
                                        }

                                    else -> Unit
                                }
                            }

                            return null
                        }

                        fun metrics(element: PadElement): FloatArray {
                            val current = rootLayout
                            val cx = size.width.toFloat() * element.x
                            val cy = size.height.toFloat() * element.y
                            val w = size.width.toFloat() * element.size
                            val h = w * current.aspectOf(element.kind)
                            val angle = Math.toRadians(-element.rotation.toDouble())
                            return floatArrayOf(cx, cy, w, h, cos(angle).toFloat(), sin(angle).toFloat())
                        }

                        fun local(position: Offset, m: FloatArray): Pair<Float, Float> {
                            val dx = position.x - m[0]
                            val dy = position.y - m[1]
                            return (dx * m[4] - dy * m[5]) to (dx * m[5] + dy * m[4])
                        }

                        fun stickAt(position: Offset): Boolean? {
                            for (left in listOf(true, false)) {
                                val kind = if (left) PadElementKind.LEFT_STICK else PadElementKind.RIGHT_STICK
                                val element = rootLayout.element(kind) ?: continue
                                val m = metrics(element)
                                val (lx, ly) = local(position, m)
                                if (hypot(lx, ly) <= minOf(m[2], m[3]) / 2f) return left
                            }
                            return null
                        }

                        fun triggerAt(position: Offset): Boolean? {
                            if (rootLayout.triggerStyle != TriggerStyle.SLIDE) return null
                            for (left in listOf(true, false)) {
                                val kind = if (left) PadElementKind.TRIGGER_LEFT else PadElementKind.TRIGGER_RIGHT
                                val element = rootLayout.element(kind) ?: continue
                                val m = metrics(element)
                                val (lx, ly) = local(position, m)
                                if (abs(lx) <= m[2] / 2f && abs(ly) <= m[3] / 2f) return left
                            }
                            return null
                        }

                        fun dpadAt(position: Offset): Boolean {
                            if (rootLayout.dpadStyle != DPadStyle.COMBO) return false
                            val element = rootLayout.element(PadElementKind.DPAD) ?: return false
                            val m = metrics(element)
                            val (lx, ly) = local(position, m)
                            return hypot(lx, ly) <= minOf(m[2], m[3]) / 2f
                        }

                        fun applyStick(left: Boolean, position: Offset) {
                            val kind = if (left) PadElementKind.LEFT_STICK else PadElementKind.RIGHT_STICK
                            val element = rootLayout.element(kind) ?: return
                            val m = metrics(element)
                            val (lx, ly) = local(position, m)
                            val maxTravel = minOf(m[2], m[3]) / 2f * 0.62f
                            if (maxTravel <= 0f) return

                            val distance = hypot(lx, ly)
                            val scale = if (distance > maxTravel && distance > 0f) maxTravel / distance else 1f
                            val nx = (lx * scale / maxTravel).coerceIn(-1f, 1f)
                            val ny = (ly * scale / maxTravel).coerceIn(-1f, 1f)

                            if (left) {
                                input.setStickEngaged(true)
                                input.setLeftStick(nx, ny)
                            } else {
                                input.rightX = nx
                                input.rightY = ny
                            }
                            controller.push()
                        }

                        fun clearStick(left: Boolean) {
                            if (left) {
                                input.setStickEngaged(false)
                                input.setLeftStick(0f, 0f)
                            } else {
                                input.rightX = 0f
                                input.rightY = 0f
                            }
                            controller.push()
                        }

                        fun applyTrigger(left: Boolean, position: Offset) {
                            val kind = if (left) PadElementKind.TRIGGER_LEFT else PadElementKind.TRIGGER_RIGHT
                            val element = rootLayout.element(kind) ?: return
                            val m = metrics(element)
                            val (_, ly) = local(position, m)
                            val gate = rootLayout.triggerGate
                                .coerceIn(PadLayout.MIN_TRIGGER_GATE, PadLayout.MAX_TRIGGER_GATE)
                            val gateHeight = m[3] * gate
                            val travel = (m[3] - gateHeight).coerceAtLeast(1f)
                            val fromTop = ly + m[3] / 2f
                            val value = if (fromTop < gateHeight) {
                                0f
                            } else {
                                ((fromTop - gateHeight) / travel).coerceIn(0f, 1f)
                            }

                            if (left) input.leftTrigger = value else input.rightTrigger = value
                            controller.push()
                        }

                        fun clearTrigger(left: Boolean) {
                            if (left) input.leftTrigger = 0f else input.rightTrigger = 0f
                            controller.push()
                        }

                        fun applyDpad(position: Offset) {
                            val element = rootLayout.element(PadElementKind.DPAD) ?: return
                            val m = metrics(element)
                            val (lx, ly) = local(position, m)
                            val deadZone = minOf(m[2], m[3]) * 0.16f
                            val direction = if (hypot(lx, ly) < deadZone) {
                                DPad.NEUTRAL
                            } else {
                                directionFromDegrees(Math.toDegrees(atan2(-ly.toDouble(), lx.toDouble())))
                            }

                            input.dpad = direction
                            controller.push()
                        }

                        fun clearDpad() {
                            input.dpad = DPad.NEUTRAL
                            controller.push()
                        }

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    val buttonNext = if (change.pressed) bitAt(change.position) else null
                                    val buttonPrev = active[change.id]
                                    if (buttonNext != buttonPrev) {
                                        buttonPrev?.let { controller.setButton(it, false) }
                                        buttonNext?.let { controller.setButton(it, true) }
                                        if (buttonNext == null) active.remove(change.id) else active[change.id] = buttonNext
                                        rootPressed = active.values.toSet()
                                    }

                                    if (!change.pressed) {
                                        stickActive.remove(change.id)?.let { clearStick(it) }
                                        triggerActive.remove(change.id)?.let { clearTrigger(it) }
                                        if (dpadActive.remove(change.id)) clearDpad()
                                        continue
                                    }

                                    // 按在离散按键上时，连续控件让位。
                                    if (buttonNext != null) {
                                        stickActive.remove(change.id)?.let { clearStick(it) }
                                        triggerActive.remove(change.id)?.let { clearTrigger(it) }
                                        if (dpadActive.remove(change.id)) clearDpad()
                                        continue
                                    }

                                    val stick = stickAt(change.position)
                                    val trigger = if (stick == null) triggerAt(change.position) else null
                                    val onDpad = stick == null && trigger == null && dpadAt(change.position)

                                    if (stick != null) {
                                        if (stickActive[change.id] != stick) {
                                            stickActive[change.id]?.let { clearStick(it) }
                                            stickActive[change.id] = stick
                                        }
                                        triggerActive.remove(change.id)?.let { clearTrigger(it) }
                                        if (dpadActive.remove(change.id)) clearDpad()
                                        applyStick(stick, change.position)
                                    } else if (trigger != null) {
                                        stickActive.remove(change.id)?.let { clearStick(it) }
                                        if (triggerActive[change.id] != trigger) {
                                            triggerActive[change.id]?.let { clearTrigger(it) }
                                            triggerActive[change.id] = trigger
                                        }
                                        if (dpadActive.remove(change.id)) clearDpad()
                                        applyTrigger(trigger, change.position)
                                    } else if (onDpad) {
                                        stickActive.remove(change.id)?.let { clearStick(it) }
                                        triggerActive.remove(change.id)?.let { clearTrigger(it) }
                                        dpadActive.add(change.id)
                                        applyDpad(change.position)
                                    }
                                    // 滑到空白：连续控件保持最后的值，直到抬手。
                                }
                            }
                        } finally {
                            active.values.forEach { controller.setButton(it, false) }
                            stickActive.values.forEach { clearStick(it) }
                            triggerActive.values.forEach { clearTrigger(it) }
                            if (dpadActive.isNotEmpty()) clearDpad()
                            active.clear()
                            stickActive.clear()
                            triggerActive.clear()
                            dpadActive.clear()
                            rootPressed = emptySet()
                        }
                    }
                }
            },
        ),
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val screenWidthPx = with(LocalDensity.current) { screenWidth.toPx() }
        val screenHeightPx = with(LocalDensity.current) { screenHeight.toPx() }
        val dragSlopPx = with(LocalDensity.current) { DRAG_SLOP.toPx() }

        val latestLayout by rememberUpdatedState(layout)
        val latestSelect by rememberUpdatedState(onSelectionChange)
        val latestChange by rememberUpdatedState(onLayoutChange)

        layout.elements.forEach { element ->
            // key 保证每个控件的组合位置固定：布局一变不至于把状态串到别的控件上。
            key(element.kind) {
                val elementWidth = screenWidth * element.size
                val elementHeight = elementWidth * layout.aspectOf(element.kind)
                val isSelected = selection is Selection.Element && selection.kind == element.kind

                Box(
                    modifier = Modifier
                        .offset(
                            x = screenWidth * element.x - elementWidth / 2f,
                            y = screenHeight * element.y - elementHeight / 2f,
                        )
                        .size(elementWidth, elementHeight)
                        .rotate(element.rotation)
                        .then(if (isSelected) Modifier.selectionPaint() else Modifier)
                        .then(
                            if (editing) {
                                Modifier.pointerInput(element.kind) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        var moved = Offset.Zero
                                        var dragged = false
                                        latestSelect(Selection.Element(element.kind))
                                        down.consume()

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break

                                            val delta = change.positionChange()
                                            moved += delta
                                            if (moved.getDistance() > dragSlopPx) {
                                                dragged = true
                                                val base = latestLayout
                                                val self = base.element(element.kind)
                                                if (self != null) {
                                                    latestChange(
                                                        base.move(
                                                            element.kind,
                                                            self.x + delta.x / screenWidthPx,
                                                            self.y + delta.y / screenHeightPx,
                                                        )
                                                    )
                                                }
                                            }
                                            change.consume()
                                        }

                                        if (!dragged) latestSelect(Selection.Element(element.kind))
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    when {
                        editing && element.kind.hasSubKeys ->
                            SubKeyEditor(element.kind, layout, selection, onSelectionChange, onLayoutChange)

                        editing -> ElementOutline(element, isSelected)
                        element.enabled -> ElementContent(element, layout, controller, rootPressed)
                    }
                }
            }
        }
    }
}

/**
 * 编辑带子键的控件：每个小键单独点选、单独拖。
 * 拖动改的是子键相对父控件的偏移，所以整体挪位置时它们会跟着走。
 */
@Composable
private fun SubKeyEditor(
    kind: PadElementKind,
    layout: PadLayout,
    selection: Selection?,
    onSelectionChange: (Selection?) -> Unit,
    onLayoutChange: (PadLayout) -> Unit,
) {
    val keys = layout.subKeys[kind].orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        val widthPx = with(LocalDensity.current) { width.toPx() }
        val heightPx = with(LocalDensity.current) { height.toPx() }
        val dragSlopPx = with(LocalDensity.current) { DRAG_SLOP.toPx() }

        val latestLayout by rememberUpdatedState(layout)
        val latestSelect by rememberUpdatedState(onSelectionChange)
        val latestChange by rememberUpdatedState(onLayoutChange)

        keys.forEachIndexed { index, key ->
            val keySize = width * key.scale
            val selected = selection is Selection.Sub && selection.kind == kind && selection.index == index

            Box(
                Modifier
                    .offset(x = width * (0.5f + key.dx) - keySize / 2, y = height * (0.5f + key.dy) - keySize / 2)
                    .size(keySize)
                    .then(if (selected) Modifier.selectionPaint() else Modifier)
                    .pointerInput(kind, index) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var moved = Offset.Zero
                            latestSelect(Selection.Sub(kind, index))
                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break

                                val delta = change.positionChange()
                                moved += delta
                                if (moved.getDistance() > dragSlopPx) {
                                    latestChange(
                                        latestLayout.updateSubKey(kind, index) {
                                            it.copy(dx = it.dx + delta.x / widthPx, dy = it.dy + delta.y / heightPx)
                                        }
                                    )
                                }
                                change.consume()
                            }

                            if (moved.getDistance() <= dragSlopPx) latestSelect(Selection.Sub(kind, index))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                SubKeyOutline(key, selected)
            }
        }
    }
}

// ------------------------------------------------------------------ 正常模式的渲染

@Composable
private fun Modifier.selectionPaint(): Modifier {
    val colors = LocalPadPalette.current
    return drawBehind {
        val corner = CornerRadius(14f, 14f)
        drawRoundRect(color = colors.selectionFill, cornerRadius = corner)
        drawRoundRect(color = colors.selectionBorder, cornerRadius = corner, style = Stroke(width = 4f))
    }
}

/** 每个控件的宽高比见 [PadLayout.aspectOf]——放在数据模型上，测试也要用。 */
@Composable
private fun ElementContent(
    element: PadElement,
    layout: PadLayout,
    controller: PadLinkController,
    rootPressed: Set<Int>,
) {
    val input = controller.input

    when (element.kind) {
        PadElementKind.LEFT_STICK -> StickPad(
            modifier = Modifier.fillMaxSize(),
            knobOverride = Offset(input.leftX, input.leftY),
            onEngaged = {
                input.setStickEngaged(it)
                // 重力转向开着时，碰左摇杆是为了让位/微调，不震；L3 仍走 setButton。
                if (it && !controller.tiltEnabled) controller.haptic(30)
            },
        ) { x, y ->
            input.setLeftStick(x, y)
            controller.push()
        }

        PadElementKind.RIGHT_STICK -> StickPad(
            Modifier.fillMaxSize(),
            knobOverride = Offset(input.rightX, input.rightY),
            onEngaged = { if (it) controller.haptic(30) },
        ) { x, y ->
            input.rightX = x
            input.rightY = y
            controller.push()
        }

        PadElementKind.DPAD -> when (layout.dpadStyle) {
            DPadStyle.COMBO -> DPadPad(
                Modifier.fillMaxSize(),
                activeOverride = input.dpad,
                onHaptic = { controller.haptic(it) },
            ) { direction ->
                input.dpad = direction
                controller.push()
            }

            DPadStyle.TRIANGLE -> TriangleDpad(layout.subKeys[PadElementKind.DPAD].orEmpty()) { direction ->
                input.dpad = direction
                controller.push()
            }
        }

        PadElementKind.ABXY_GROUP -> AbxyCluster(
            keys = layout.subKeys[PadElementKind.ABXY_GROUP].orEmpty(),
            pressedBits = rootPressed,
        ) { bit, down ->
            controller.setButton(bit, down)
        }

        PadElementKind.TRIGGER_LEFT -> TriggerOrButton(
            layout = layout,
            label = "LT",
            modifier = Modifier.fillMaxSize(),
            valueOverride = input.leftTrigger,
            onHaptic = { controller.haptic(it) },
        ) { value ->
            input.leftTrigger = value
            controller.push()
        }

        PadElementKind.TRIGGER_RIGHT -> TriggerOrButton(
            layout = layout,
            label = "RT",
            modifier = Modifier.fillMaxSize(),
            valueOverride = input.rightTrigger,
            onHaptic = { controller.haptic(it) },
        ) { value ->
            input.rightTrigger = value
            controller.push()
        }

        PadElementKind.SHOULDER_LEFT -> PadRectButton(
            "LB",
            Modifier.fillMaxSize(),
            pressedOverride = rootPressed.contains(GamepadButtons.LEFT_SHOULDER),
        ) { down ->
            controller.setButton(GamepadButtons.LEFT_SHOULDER, down)
        }

        PadElementKind.SHOULDER_RIGHT -> PadRectButton(
            "RB",
            Modifier.fillMaxSize(),
            pressedOverride = rootPressed.contains(GamepadButtons.RIGHT_SHOULDER),
        ) { down ->
            controller.setButton(GamepadButtons.RIGHT_SHOULDER, down)
        }

        PadElementKind.BACK -> PadIconButton(
            PadIcon.VIEW,
            Modifier.fillMaxSize(),
            pressedOverride = rootPressed.contains(GamepadButtons.BACK),
        ) { down ->
            controller.setButton(GamepadButtons.BACK, down)
        }

        PadElementKind.START -> PadIconButton(
            PadIcon.MENU,
            Modifier.fillMaxSize(),
            pressedOverride = rootPressed.contains(GamepadButtons.START),
        ) { down ->
            controller.setButton(GamepadButtons.START, down)
        }

        PadElementKind.GUIDE -> PadGuideButton(
            Modifier.fillMaxSize(),
            pressedOverride = rootPressed.contains(GamepadButtons.GUIDE),
        ) { down ->
            controller.setButton(GamepadButtons.GUIDE, down)
        }

        PadElementKind.STICK_LEFT_BUTTON -> PadButton(
            "L3",
            Modifier.fillMaxSize(),
            labelSize = 13.sp,
            pressedOverride = rootPressed.contains(GamepadButtons.LEFT_THUMB),
        ) { down ->
            controller.setButton(GamepadButtons.LEFT_THUMB, down)
        }

        PadElementKind.STICK_RIGHT_BUTTON -> PadButton(
            "R3",
            Modifier.fillMaxSize(),
            labelSize = 13.sp,
            pressedOverride = rootPressed.contains(GamepadButtons.RIGHT_THUMB),
        ) { down ->
            controller.setButton(GamepadButtons.RIGHT_THUMB, down)
        }
    }
}

private val AbxyBits = mapOf(
    "A" to GamepadButtons.A,
    "B" to GamepadButtons.B,
    "X" to GamepadButtons.X,
    "Y" to GamepadButtons.Y,
)

/** ABXY：位置和大小全部来自布局里的子键，所以每个键都能单独挪。 */
@Composable
private fun AbxyCluster(
    keys: List<SubKey>,
    pressedBits: Set<Int>,
    onButton: (Int, Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight

        keys.forEach { key ->
            val bit = AbxyBits[key.label] ?: return@forEach
            if (!key.enabled) return@forEach
            val keySize = width * key.scale

            Box(
                Modifier
                    .offset(x = width * (0.5f + key.dx) - keySize / 2, y = height * (0.5f + key.dy) - keySize / 2)
                    .size(keySize),
            ) {
                PadButton(
                    label = key.label,
                    modifier = Modifier.fillMaxSize(),
                    pressedOverride = pressedBits.contains(bit),
                ) { down -> onButton(bit, down) }
            }
        }
    }
}

private val TriangleDirections = mapOf(
    "N" to DPad.NORTH,
    "S" to DPad.SOUTH,
    "W" to DPad.WEST,
    "E" to DPad.EAST,
)

/** 三角分键：同按两个能拼出斜向，所以不比 8 方向那套差。 */
@Composable
private fun TriangleDpad(keys: List<SubKey>, onDirection: (DPad) -> Unit) {
    val pressed = remember { mutableStateListOf<DPad>() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight

        keys.forEach { key ->
            val direction = TriangleDirections[key.label] ?: return@forEach
            if (!key.enabled) return@forEach
            val keySize = width * key.scale

            Box(
                Modifier
                    .offset(x = width * (0.5f + key.dx) - keySize / 2, y = height * (0.5f + key.dy) - keySize / 2)
                    .size(keySize),
            ) {
                TriangleKey(direction, Modifier.fillMaxSize()) { down ->
                    if (down) pressed.add(direction) else pressed.remove(direction)
                    onDirection(combine(pressed))
                }
            }
        }
    }
}

@Composable
private fun TriangleKey(
    direction: DPad,
    modifier: Modifier,
    onPressChange: (Boolean) -> Unit,
) {
    var down by remember { mutableStateOf(false) }
    val colors = LocalPadPalette.current

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var pressedBy: PointerId? = null

                    fun release() {
                        if (pressedBy != null) {
                            pressedBy = null
                            down = false
                            onPressChange(false)
                        }
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            // 只看当前手指是否还在圆形区域内，滑出就释放。
                            val hit = event.changes.firstOrNull { change ->
                                change.pressed &&
                                    (change.position - Offset(size.width / 2f, size.height / 2f))
                                        .getDistance() <= minOf(size.width, size.height) / 2f
                            }

                            if (hit != null) {
                                if (pressedBy == null) {
                                    down = true
                                    onPressChange(true)
                                }
                                pressedBy = hit.id
                                hit.consume()
                            } else {
                                release()
                            }
                        }
                    } finally {
                        release()
                    }
                }

            }
            .drawBehind {
                val radius = minOf(size.width, size.height) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(color = if (down) colors.facePressed else colors.face, radius = radius, center = center)
                drawTriangle(
                    direction = direction,
                    color = if (down) colors.labelPressed else colors.label,
                    center = center,
                    radius = radius * 0.46f,
                )
            },
    )
}

/** 四个方向 → 8 方向枚举。 */
internal fun combine(pressed: List<DPad>): DPad {
    val north = DPad.NORTH in pressed
    val south = DPad.SOUTH in pressed
    val west = DPad.WEST in pressed
    val east = DPad.EAST in pressed

    return when {
        north && east -> DPad.NORTH_EAST
        north && west -> DPad.NORTH_WEST
        south && east -> DPad.SOUTH_EAST
        south && west -> DPad.SOUTH_WEST
        north -> DPad.NORTH
        south -> DPad.SOUTH
        west -> DPad.WEST
        east -> DPad.EAST
        else -> DPad.NEUTRAL
    }
}

private fun DrawScope.drawTriangle(direction: DPad, color: Color, center: Offset, radius: Float) {
    val path = Path()
    val half = radius * 0.9f

    when (direction) {
        DPad.NORTH -> {
            path.moveTo(center.x, center.y - radius)
            path.lineTo(center.x - half, center.y + radius * 0.7f)
            path.lineTo(center.x + half, center.y + radius * 0.7f)
        }

        DPad.SOUTH -> {
            path.moveTo(center.x, center.y + radius)
            path.lineTo(center.x - half, center.y - radius * 0.7f)
            path.lineTo(center.x + half, center.y - radius * 0.7f)
        }

        DPad.WEST -> {
            path.moveTo(center.x - radius, center.y)
            path.lineTo(center.x + radius * 0.7f, center.y - half)
            path.lineTo(center.x + radius * 0.7f, center.y + half)
        }

        else -> {
            path.moveTo(center.x + radius, center.y)
            path.lineTo(center.x - radius * 0.7f, center.y - half)
            path.lineTo(center.x - radius * 0.7f, center.y + half)
        }
    }

    path.close()
    drawPath(path, color)
}

// ------------------------------------------------------------------ 编辑模式的轮廓

@Composable
private fun ElementOutline(element: PadElement, selected: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    val colors = LocalPadPalette.current
    val accent = if (element.enabled) colors.selectionBorder.copy(alpha = 0.40f) else colors.disabledBorder

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (element.enabled) colors.selectionFill.copy(alpha = 0.10f) else colors.disabledFill,
                shape,
            )
            .border(if (selected) 1.5.dp else 1.dp, accent, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = element.kind.label,
            color = if (element.enabled) colors.selectionText else colors.disabledText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SubKeyOutline(key: SubKey, selected: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val colors = LocalPadPalette.current
    val accent = if (key.enabled) colors.selectionBorder.copy(alpha = 0.40f) else colors.disabledBorder

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (key.enabled) colors.selectionFill.copy(alpha = 0.14f) else colors.disabledFill,
                shape,
            )
            .border(if (selected) 1.5.dp else 1.dp, accent, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.label,
            color = if (key.enabled) colors.selectionText else colors.disabledText,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

/** 扳机按布局里选的样式渲染。 */
@Composable
private fun TriggerOrButton(
    layout: PadLayout,
    label: String,
    modifier: Modifier,
    valueOverride: Float?,
    onHaptic: (Long) -> Unit,
    onChange: (Float) -> Unit,
) {
    when (layout.triggerStyle) {
        TriggerStyle.SLIDE -> TriggerSlider(modifier, label, layout.triggerGate, valueOverride, onHaptic, onChange)
        TriggerStyle.BUTTON -> PadRectButton(label, modifier) { down -> onChange(if (down) 1f else 0f) }
    }
}
