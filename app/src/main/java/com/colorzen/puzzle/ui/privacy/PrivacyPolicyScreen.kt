package com.colorzen.puzzle.ui.privacy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.colorzen.puzzle.R
import com.colorzen.puzzle.core.AppConfig
import com.colorzen.puzzle.core.AppContainer
import com.colorzen.puzzle.core.Sfx
import com.colorzen.puzzle.ui.components.ZenButton
import com.colorzen.puzzle.ui.components.ZenCard
import com.colorzen.puzzle.ui.components.ZenScreen
import com.colorzen.puzzle.ui.components.ZenTopBar
import com.colorzen.puzzle.ui.nav.ZenNavigator

/**
 * Privacy Policy screen.
 *
 * Google Play requires both a policy URL in Play Console and, for apps that
 * declare no data collection, an honest in-app statement. The full text is
 * bundled and localised (English + Arabic) so it is readable with no network
 * access - the app has no INTERNET permission at all.
 */
@Composable
fun PrivacyPolicyScreen(container: AppContainer, navigator: ZenNavigator) {
    val context = LocalContext.current
    val sections = remember {
        listOf(
            R.string.privacy_s1_title to R.string.privacy_s1_body,
            R.string.privacy_s2_title to R.string.privacy_s2_body,
            R.string.privacy_s3_title to R.string.privacy_s3_body,
            R.string.privacy_s4_title to R.string.privacy_s4_body,
            R.string.privacy_s5_title to R.string.privacy_s5_body,
            R.string.privacy_s6_title to R.string.privacy_s6_body,
            R.string.privacy_s7_title to R.string.privacy_s7_body,
            R.string.privacy_s8_title to R.string.privacy_s8_body,
        )
    }

    ZenScreen {
        ZenTopBar(
            title = stringResource(id = R.string.privacy_title),
            onBack = { navigator.back() },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ZenCard {
                    Text(
                        text = stringResource(id = R.string.privacy_updated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.privacy_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            items(sections, key = { it.first }) { (titleRes, bodyRes) ->
                ZenCard {
                    Text(
                        text = stringResource(id = titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(id = bodyRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ZenButton(
                        text = stringResource(id = R.string.privacy_open_browser),
                        onClick = {
                            container.soundManager.play(Sfx.CLICK)
                            openUrl(context, AppConfig.PRIVACY_POLICY_URL)
                        },
                        iconRes = R.drawable.ic_globe,
                        filled = false,
                        modifier = Modifier.fillMaxWidth(),
                        height = 50.dp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = AppConfig.PRIVACY_POLICY_URL,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (ignored: ActivityNotFoundException) {
        // No browser available; the full policy text is already on screen.
    }
}
