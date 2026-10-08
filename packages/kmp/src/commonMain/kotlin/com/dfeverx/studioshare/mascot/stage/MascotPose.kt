package com.dfeverx.studioshare.mascot.stage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.dfeverx.studioshare.mascot.LocalMascot
import com.dfeverx.studioshare.mascot.MascotMomentMoods
import com.dfeverx.studioshare.mascot.face.StudioFace

/** True when the mascot is on, i.e. when [MascotPose] would draw something. */
@Composable
fun rememberMascotShown(): Boolean {
    val mascot = LocalMascot.current ?: return false
    val enabled by mascot.settings.enabled.collectAsState()
    val ready by mascot.settings.ready.collectAsState()
    return enabled && ready
}

/**
 * The mascot in [moment]'s mood, drawn in place — for surfaces the floating mascot cannot reach,
 * such as a dialog (it sits above the app, and its scrim covers the stage). Square; size it with
 * [modifier]. Decorative: hidden from screen readers. Draws nothing while the mascot is off; callers
 * should check [rememberMascotShown] first so their layout is unchanged then.
 */
@Composable
fun MascotPose(
    moment: String,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    if (LocalMascot.current == null) return
    StudioFace(
        mood = MascotMomentMoods.moodFor(moment, firstAlreadySeen = true),
        animate = !reducedMotion,
        hands = MascotMomentMoods.handsFor(moment, firstAlreadySeen = true),
        modifier = modifier.clearAndSetSemantics { },
    )
}
