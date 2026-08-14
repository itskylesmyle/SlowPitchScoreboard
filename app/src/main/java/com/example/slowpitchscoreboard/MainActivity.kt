package com.example.slowpitchscoreboard

import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Constants (single source of truth — was previously repeated as literals)
// ---------------------------------------------------------------------------
private const val PREFS_NAME = "ScoreboardPrefs"
private const val PREF_SOUND = "sound_enabled"
private const val PREF_VIBRATE = "vibrate_enabled"
private const val DEFAULT_TIMER_SECONDS = 50 * 60
private const val MAX_OUTS = 3 // 3rd out ends the half-inning; 2 dots are shown
private val TIMER_PRESET_MINUTES = listOf(50, 55, 60)

private val OutYellow = Color(0xFFFFD400)
private val DimDot = Color(0xFF3A3A3A)
private val DividerGray = Color(0xFF555555)
private val LabelGray = Color(0xFFAAAAAA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    ScoreboardScreen()
                }
            }
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}

/**
 * Combines a tap gesture with a "swipe left to undo" gesture. Two separate
 * pointerInput blocks are used deliberately: detectTapGestures and
 * detectHorizontalDragGestures each own their own gesture-detection loop,
 * and mixing them into a single block risks one silently starving the other.
 */
private fun Modifier.tapAndSwipeLeft(
    onTap: () -> Unit,
    onSwipeLeft: () -> Unit,
    swipeThreshold: Float = 90f,
    onRegistered: () -> Unit = {}
): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(onTap = {
            onRegistered()
            onTap()
        })
    }
    .pointerInput(Unit) {
        var dragTotal = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragTotal = 0f },
            onDragEnd = {
                if (dragTotal <= -swipeThreshold) {
                    onRegistered()
                    onSwipeLeft()
                }
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                dragTotal += dragAmount
            }
        )
    }

// ---------------------------------------------------------------------------
// Small reusable composables
//
// These were previously duplicated verbatim between the portrait and
// landscape layouts (and, for the timer dialog, duplicated a second time
// between its portrait/landscape variants). Pulling them out means each
// piece of UI and its behavior is defined exactly once.
// ---------------------------------------------------------------------------

/** The row of "outs" dots. Identical in portrait/landscape apart from sizing. */
@Composable
private fun OutsDots(outs: Int, dotSize: Dp, spacing: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
        repeat(MAX_OUTS - 1) { i ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(if (i < outs) OutYellow else DimDot)
            )
        }
    }
}

/** The overflow (⋮) menu button and its dropdown. Was duplicated in full per orientation. */
@Composable
private fun OptionsMenuButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onResetClick: () -> Unit,
    soundEnabled: Boolean,
    onToggleSound: () -> Unit,
    vibrateEnabled: Boolean,
    onToggleVibrate: () -> Unit
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text("Reset All") },
                onClick = {
                    onExpandedChange(false)
                    onResetClick()
                }
            )
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = soundEnabled, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sound")
                    }
                },
                onClick = onToggleSound
            )
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = vibrateEnabled, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vibrate")
                    }
                },
                onClick = onToggleVibrate
            )
        }
    }
}

/** The big score number. Was duplicated 4x (away/home × portrait/landscape). */
@Composable
private fun BigScoreText(score: Int, fontSize: TextUnit, modifier: Modifier = Modifier) {
    Text(
        text = score.toString(),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        style = LocalTextStyle.current.copy(fontSize = fontSize),
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}

/** The "-1 min" / "+1 min" button pair in the timer dialog. */
@Composable
private fun AdjustMinutesRow(
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    buttonContentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    onMinusMinute: () -> Unit,
    onPlusMinute: () -> Unit
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        Button(onClick = onMinusMinute, modifier = buttonModifier, contentPadding = buttonContentPadding) {
            Text("-1 min", maxLines = 1, softWrap = false)
        }
        Button(onClick = onPlusMinute, modifier = buttonModifier, contentPadding = buttonContentPadding) {
            Text("+1 min", maxLines = 1, softWrap = false)
        }
    }
}

/** The Start/Pause button in the timer dialog. */
@Composable
private fun StartPauseButton(timerRunning: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (timerRunning) Color(0xFFFBC02D) else Color(0xFF2E7D32)
        )
    ) {
        Text(if (timerRunning) "Pause" else "Start")
    }
}

/** The row of quick-start preset buttons (50/55/60 min) in the timer dialog. */
@Composable
private fun PresetButtonsRow(
    spacing: Dp,
    buttonHeight: Dp,
    buttonContentPadding: PaddingValues,
    onPresetSelected: (minutes: Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
        TIMER_PRESET_MINUTES.forEach { minutes ->
            Button(
                onClick = { onPresetSelected(minutes) },
                modifier = Modifier.weight(1f).height(buttonHeight),
                contentPadding = buttonContentPadding
            ) {
                Text("$minutes min", maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The "Reset to 50:00" text button in the timer dialog. */
@Composable
private fun ResetTimerButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text("Reset to ${formatTime(DEFAULT_TIMER_SECONDS)}", maxLines = 1, softWrap = false)
    }
}

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

@Composable
fun ScoreboardScreen() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var soundEnabled by remember { mutableStateOf(sharedPrefs.getBoolean(PREF_SOUND, true)) }
    var vibrateEnabled by remember { mutableStateOf(sharedPrefs.getBoolean(PREF_VIBRATE, true)) }

    fun persist(key: String, value: Boolean) {
        sharedPrefs.edit().putBoolean(key, value).apply()
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val playClick: () -> Unit = {
        if (soundEnabled) audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK)
    }

    val haptics = LocalHapticFeedback.current
    val vibrate: () -> Unit = {
        if (vibrateEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    var awayScore by rememberSaveable { mutableStateOf(0) }
    var homeScore by rememberSaveable { mutableStateOf(0) }
    var awayHR by rememberSaveable { mutableStateOf(0) }
    var homeHR by rememberSaveable { mutableStateOf(0) }
    var outs by rememberSaveable { mutableStateOf(0) }
    var inningNumber by rememberSaveable { mutableStateOf(1) }
    var isTop by rememberSaveable { mutableStateOf(true) }

    var totalSeconds by rememberSaveable { mutableStateOf(0) }
    var timerRunning by rememberSaveable { mutableStateOf(false) }

    var showTimerDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(timerRunning) {
        while (timerRunning && totalSeconds > 0) {
            delay(1000)
            totalSeconds -= 1
        }
        if (totalSeconds <= 0) {
            timerRunning = false
        }
    }

    LaunchedEffect(showTimerDialog) {
        if (showTimerDialog && totalSeconds == 0) {
            totalSeconds = DEFAULT_TIMER_SECONDS
        }
    }

    fun advanceHalfInning() {
        if (isTop) {
            isTop = false
        } else {
            isTop = true
            inningNumber += 1
        }
    }

    fun regressHalfInning(): Boolean {
        return if (isTop) {
            if (inningNumber > 1) {
                inningNumber -= 1
                isTop = false
                true
            } else {
                false
            }
        } else {
            isTop = true
            true
        }
    }

    fun incrementOuts() {
        if (outs < MAX_OUTS - 1) {
            outs += 1
        } else {
            outs = 0
            advanceHalfInning()
        }
    }

    fun decrementOuts() {
        if (outs > 0) {
            outs -= 1
        } else if (regressHalfInning()) {
            outs = MAX_OUTS - 1
        }
    }

    fun resetAll() {
        awayScore = 0
        homeScore = 0
        awayHR = 0
        homeHR = 0
        outs = 0
        inningNumber = 1
        isTop = true
        totalSeconds = DEFAULT_TIMER_SECONDS
        timerRunning = false
    }

    // Behavior shared identically between the portrait and landscape layouts,
    // hoisted once instead of being re-written in both branches.
    val onToggleSound: () -> Unit = {
        soundEnabled = !soundEnabled
        persist(PREF_SOUND, soundEnabled)
    }
    val onToggleVibrate: () -> Unit = {
        vibrateEnabled = !vibrateEnabled
        persist(PREF_VIBRATE, vibrateEnabled)
    }
    val onResetClick: () -> Unit = { showResetConfirm = true }
    val onClockTap: () -> Unit = { playClick(); showTimerDialog = true }
    val onInningTap: () -> Unit = { advanceHalfInning() }
    val onInningSwipe: () -> Unit = { regressHalfInning() }
    val onAwayScoreTap: () -> Unit = { awayScore += 1; vibrate() }
    val onAwayScoreSwipe: () -> Unit = { awayScore = (awayScore - 1).coerceAtLeast(0) }
    val onHomeScoreTap: () -> Unit = { homeScore += 1; vibrate() }
    val onHomeScoreSwipe: () -> Unit = { homeScore = (homeScore - 1).coerceAtLeast(0) }
    val onAwayHRTap: () -> Unit = { awayHR += 1; vibrate() }
    val onAwayHRSwipe: () -> Unit = { awayHR = (awayHR - 1).coerceAtLeast(0) }
    val onHomeHRTap: () -> Unit = { homeHR += 1; vibrate() }
    val onHomeHRSwipe: () -> Unit = { homeHR = (homeHR - 1).coerceAtLeast(0) }
    val onOutsTap: () -> Unit = { incrementOuts(); vibrate() }
    val onOutsSwipe: () -> Unit = { decrementOuts() }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    if (isPortrait) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoxWithConstraints {
                    val clockFontSize = configuration.screenWidthDp.sp * 0.12f
                    Text(
                        text = formatTime(totalSeconds),
                        color = Color.White,
                        fontSize = clockFontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onTap = { onClockTap() })
                        }
                    )
                }

                BoxWithConstraints(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val inningFontSize = (maxWidth.value * 0.22f).sp
                    Text(
                        text = "${if (isTop) "Top" else "Bot"} $inningNumber",
                        color = Color.White,
                        fontSize = inningFontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.tapAndSwipeLeft(
                            onTap = onInningTap,
                            onSwipeLeft = onInningSwipe,
                            onRegistered = playClick
                        )
                    )
                }

                OptionsMenuButton(
                    expanded = showMenu,
                    onExpandedChange = { showMenu = it },
                    onResetClick = onResetClick,
                    soundEnabled = soundEnabled,
                    onToggleSound = onToggleSound,
                    vibrateEnabled = vibrateEnabled,
                    onToggleVibrate = onToggleVibrate
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Away",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(y = 30.dp)
                    )

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .tapAndSwipeLeft(onTap = onAwayScoreTap, onSwipeLeft = onAwayScoreSwipe, onRegistered = playClick),
                            contentAlignment = Alignment.Center
                        ) {
                            val scoreFontSize = minOf(maxHeight.value * 0.55f, maxWidth.value * 0.8f).sp
                            BigScoreText(score = awayScore, fontSize = scoreFontSize)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .tapAndSwipeLeft(onTap = onAwayHRTap, onSwipeLeft = onAwayHRSwipe, onRegistered = playClick)
                                .padding(12.dp)
                        ) {
                            Text("HR", color = LabelGray, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(awayHR.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .tapAndSwipeLeft(onTap = onHomeScoreTap, onSwipeLeft = onHomeScoreSwipe, onRegistered = playClick),
                            contentAlignment = Alignment.Center
                        ) {
                            val scoreFontSize = minOf(maxHeight.value * 0.55f, maxWidth.value * 0.8f).sp
                            BigScoreText(score = homeScore, fontSize = scoreFontSize)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .tapAndSwipeLeft(onTap = onHomeHRTap, onSwipeLeft = onHomeHRSwipe, onRegistered = playClick)
                                .padding(12.dp)
                        ) {
                            Text("HR", color = LabelGray, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(homeHR.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Home",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(y = (-25).dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .offset(x = (-50).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .tapAndSwipeLeft(onTap = onOutsTap, onSwipeLeft = onOutsSwipe, onRegistered = playClick)
                            .padding(16.dp)
                    ) {
                        Text("Outs", color = LabelGray, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(18.dp))
                        OutsDots(outs = outs, dotSize = 35.dp, spacing = 24.dp)
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = 16.dp, end = 16.dp, top = 0.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val clockFontSize = (maxWidth.value * 0.18f).sp
                    Text(
                        text = formatTime(totalSeconds),
                        color = Color.White,
                        fontSize = clockFontSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(4.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onClockTap() })
                            }
                    )
                }

                BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val inningFontSize = (maxWidth.value * 0.10f).sp
                    Text(
                        text = "${if (isTop) "Top" else "Bot"} $inningNumber",
                        color = Color.White,
                        fontSize = inningFontSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(8.dp)
                            .tapAndSwipeLeft(onTap = onInningTap, onSwipeLeft = onInningSwipe, onRegistered = playClick)
                    )
                }

                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    OptionsMenuButton(
                        expanded = showMenu,
                        onExpandedChange = { showMenu = it },
                        onResetClick = onResetClick,
                        soundEnabled = soundEnabled,
                        onToggleSound = onToggleSound,
                        vibrateEnabled = vibrateEnabled,
                        onToggleVibrate = onToggleVibrate
                    )
                }
            }

            Row(Modifier.fillMaxWidth()) {
                BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val labelFontSize = (maxWidth.value * 0.05f).sp
                    Text("Away", color = Color.White, fontSize = labelFontSize, fontWeight = FontWeight.Bold)
                }
                BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val labelFontSize = (maxWidth.value * 0.05f).sp
                    Text("Home", color = Color.White, fontSize = labelFontSize, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .tapAndSwipeLeft(onTap = onAwayScoreTap, onSwipeLeft = onAwayScoreSwipe, onRegistered = playClick),
                    contentAlignment = Alignment.Center
                ) {
                    BigScoreText(
                        score = awayScore,
                        fontSize = maxHeight.value.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight(0.65f)
                        .background(DividerGray)
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .tapAndSwipeLeft(onTap = onHomeScoreTap, onSwipeLeft = onHomeScoreSwipe, onRegistered = playClick),
                    contentAlignment = Alignment.Center
                ) {
                    BigScoreText(
                        score = homeScore,
                        fontSize = maxHeight.value.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                val hrNumberSize = (maxWidth.value * 0.03f).sp
                val hrLabelSize = (maxWidth.value * 0.03f).sp
                val outsLabelSize = (maxWidth.value * 0.03f).sp
                val outsDotSize = (maxWidth.value * 0.045f).dp
                val hrRowSpacing = (maxWidth.value * 0.05f).dp
                val outsLabelDotsGap = (maxWidth.value * 0.035f).dp
                val outsDotSpacing = (maxWidth.value * 0.025f).dp
                val outsPaddingStart = (maxWidth.value * 0.02f).dp
                val outsPaddingEnd = (maxWidth.value * 0.035f).dp
                val outsPaddingTop = (maxWidth.value * 0.025f).dp
                val outsPaddingBottom = (maxWidth.value * 0.01f).dp

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(hrRowSpacing)
                ) {
                    Text(
                        text = awayHR.toString(),
                        color = Color.White,
                        fontSize = hrNumberSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .tapAndSwipeLeft(onTap = onAwayHRTap, onSwipeLeft = onAwayHRSwipe, onRegistered = playClick)
                    )
                    Text("HR", color = LabelGray, fontSize = hrLabelSize, fontWeight = FontWeight.Bold)
                    Text(
                        text = homeHR.toString(),
                        color = Color.White,
                        fontSize = hrNumberSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .tapAndSwipeLeft(onTap = onHomeHRTap, onSwipeLeft = onHomeHRSwipe, onRegistered = playClick)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = (-20).dp)
                        .tapAndSwipeLeft(onTap = onOutsTap, onSwipeLeft = onOutsSwipe, onRegistered = playClick)
                        .padding(
                            start = outsPaddingStart,
                            end = outsPaddingEnd,
                            top = outsPaddingTop,
                            bottom = outsPaddingBottom
                        ),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Outs", color = LabelGray, fontSize = outsLabelSize, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(outsLabelDotsGap))
                        OutsDots(outs = outs, dotSize = outsDotSize, spacing = outsDotSpacing)
                    }
                }
            }
        }
    }

    if (showTimerDialog) {
        val onPresetSelected: (Int) -> Unit = { minutes ->
            playClick()
            totalSeconds = minutes * 60
            timerRunning = true
            showTimerDialog = false
        }
        val onResetTimer: () -> Unit = {
            playClick()
            totalSeconds = DEFAULT_TIMER_SECONDS
            timerRunning = false
        }
        val onStartPause: () -> Unit = {
            playClick()
            timerRunning = !timerRunning
            if (timerRunning) showTimerDialog = false
        }
        val onMinusMinute: () -> Unit = { playClick(); totalSeconds = (totalSeconds - 60).coerceAtLeast(0) }
        val onPlusMinute: () -> Unit = { playClick(); totalSeconds += 60 }

        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.7f),
            title = { Text("Adjust Timer") },
            text = {
                if (isPortrait) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = formatTime(totalSeconds), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        AdjustMinutesRow(spacing = 12.dp, onMinusMinute = onMinusMinute, onPlusMinute = onPlusMinute)
                        Spacer(Modifier.height(12.dp))
                        StartPauseButton(timerRunning = timerRunning, onClick = onStartPause)
                        Spacer(Modifier.height(16.dp))
                        Text("Presets", color = LabelGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        PresetButtonsRow(
                            spacing = 8.dp,
                            buttonHeight = 55.dp,
                            buttonContentPadding = PaddingValues(horizontal = 2.dp),
                            onPresetSelected = onPresetSelected
                        )
                        Spacer(Modifier.height(12.dp))
                        ResetTimerButton(onClick = onResetTimer)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = formatTime(totalSeconds), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            AdjustMinutesRow(
                                modifier = Modifier.fillMaxWidth(),
                                buttonModifier = Modifier.weight(1f),
                                spacing = 8.dp,
                                buttonContentPadding = PaddingValues(horizontal = 2.dp),
                                onMinusMinute = onMinusMinute,
                                onPlusMinute = onPlusMinute
                            )
                            Spacer(Modifier.height(12.dp))
                            StartPauseButton(
                                timerRunning = timerRunning,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onStartPause
                            )
                        }

                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Presets", color = LabelGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            PresetButtonsRow(
                                spacing = 4.dp,
                                buttonHeight = 48.dp,
                                buttonContentPadding = PaddingValues(horizontal = 0.dp),
                                onPresetSelected = onPresetSelected
                            )
                            Spacer(Modifier.height(12.dp))
                            ResetTimerButton(onClick = onResetTimer)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { playClick(); showTimerDialog = false }) { Text("Done") }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset scoreboard?") },
            text = { Text("This resets the score, HRs, outs, inning, and timer back to their starting values.") },
            confirmButton = {
                TextButton(onClick = {
                    playClick()
                    resetAll()
                    showResetConfirm = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = {
                    playClick()
                    showResetConfirm = false
                }) { Text("Cancel") }
            }
        )
    }
}