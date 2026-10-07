package com.dfeverx.studioshare.mascot.stage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.dfeverx.studioshare.mascot.LocalMascot
import com.dfeverx.studioshare.mascot.render.MascotSprite
import com.dfeverx.studioshare.mascot.rig.MascotFigure
import com.dfeverx.studioshare.mascot.rig.RigColors

/** True when the mascot is on and has a pack, i.e. when [MascotPose] would draw something. */
@Composable
fun rememberMascotShown(): Boolean {
    val mascot = LocalMascot.current ?: return false
    val enabled by mascot.settings.enabled.collectAsState()
    val ready by mascot.settings.ready.collectAsState()
    val pack by mascot.packs.pack.collectAsState()
    LaunchedEffect(mascot, enabled) { if (enabled) mascot.packs.load() }
    return enabled && ready && pack != null
}

/**
 * The mascot in [moment]'s pose, drawn in place — for surfaces the floating mascot cannot reach,
 * such as a dialog (it sits above the app, and its scrim covers the stage). Size it with [modifier]
 * at the rig's 100 × 140 aspect. Decorative: hidden from screen readers. Draws nothing while the
 * mascot is off; callers should check [rememberMascotShown] first so their layout is unchanged then.
 */
@Composable
fun MascotPose(
    moment: String,
    dark: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val mascot = LocalMascot.current ?: return
    val loaded by mascot.packs.pack.collectAsState()
    val pack = loaded ?: return
    val resolved = remember(pack, moment) { pack.pack.resolve(moment, firstAlreadySeen = true) }
    val m = modifier.clearAndSetSemantics { }
    if (resolved.art != null) {
        MascotSprite(
            resolved = resolved,
            loaded = pack,
            dark = dark,
            cache = mascot.atlases,
            loop = true,
            animate = !reducedMotion,
            modifier = m,
        )
    } else {
        MascotFigure(
            resolved = resolved,
            colors = if (dark) RigColors.Light else RigColors.Dark,
            oneShot = false,
            walking = false,
            mirrored = false,
            animate = !reducedMotion,
            modifier = m,
        )
    }
}
