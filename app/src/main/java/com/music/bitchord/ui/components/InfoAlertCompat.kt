package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import dev.chrisbanes.haze.HazeState

/**
 * Upstream's one-button alert, in this repo's frosted shell.
 *
 * Upstream keeps it beside `ConfirmationAlert` in `AccountAlerts.kt`. It is here
 * instead so the ported callers can be dropped in without that file being
 * rewritten underneath them: [AlertScaffold] — the shell, its scrim and its
 * rule — is the same one this repo already draws every alert on, so what is
 * missing is only the shape of the body, which is a title, a paragraph and one
 * way out.
 */
@Composable
fun InfoAlert(
    hazeState: HazeState,
    title: String,
    description: String,
    onDismiss: () -> Unit,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        AlertRule()
        AlertAction(label = stringResource(R.string.got_it), emphasised = true, onClick = onDismiss)
    }
}
