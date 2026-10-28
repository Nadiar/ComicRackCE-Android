package com.comicrack.settings.composables

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * AnimatedToggleButton â€” M3E Animated Toggle Button
 *
 * A circular toggle button with animated checkmark/radio icon.
 * Features identical animation to hardware button toggles:
 * - Scale: 0.8f â†’ 1.1f (bounce to 1.0f) with MediumBouncy spring
 * - Rotation: 0Â° â†’ 360Â° full spin on toggle
 * - Semantics: Role.Switch for accessibility
 *
 * Used consistently across all settings screens for visual uniformity.
 *
 * @param isEnabled Whether the toggle is in enabled state
 * @param onToggle Callback when toggled (inverts state)
 * @param modifier Layout modifier
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@Composable
fun AnimatedToggleButton(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Animation trigger: incremented on each toggle
    var animationTrigger by remember { mutableIntStateOf(0) }

    // Scale animation: bounce from 0.8f to 1.1f (lands at 1.0f)
    val toggleScale by animateFloatAsState(
        targetValue = if (animationTrigger % 2 == 1) 1.1f else 0.8f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "toggleScale",
    )

    // Rotation animation: 360Â° full spin on toggle
    val toggleRotation by animateFloatAsState(
        targetValue = if (animationTrigger % 2 == 1) 360f else 0f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "toggleRotation",
    )

    Surface(
        onClick = {
            onToggle(!isEnabled)
            animationTrigger++ // Trigger animation on toggle
        },
        enabled = true,
        color =
            if (isEnabled) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        contentColor =
            if (isEnabled) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier =
            modifier
                .size(48.dp)
                .semantics { role = Role.Switch },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isEnabled) "Enabled" else "Disabled",
                modifier =
                    Modifier
                        .size(24.dp)
                        .scale(toggleScale)
                        .rotate(toggleRotation),
            )
        }
    }
}
