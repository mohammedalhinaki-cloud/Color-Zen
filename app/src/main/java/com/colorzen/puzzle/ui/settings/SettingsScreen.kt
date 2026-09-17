package com.colorzen.puzzle.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.colorzen.puzzle.R
import com.colorzen.puzzle.core.AppConfig
import com.colorzen.puzzle.core.AppContainer
import com.colorzen.puzzle.core.Sfx
import com.colorzen.puzzle.ui.components.SwitchRow
import com.colorzen.puzzle.ui.components.ZenCard
import com.colorzen.puzzle.ui.components.ZenRow
import com.colorzen.puzzle.ui.components.ZenScreen
import com.colorzen.puzzle.ui.components.ZenTopBar
import com.colorzen.puzzle.ui.nav.ZenNavigator

/**
 * Settings.
 *
 * The only preference the game has is the sound-effects toggle - there is no
 * music to turn off because the app contains no music at all. Everything else
 * here is required disclosure (privacy policy, purchases) plus app info.
 */
@Composable
fun SettingsScreen(container: AppContainer, navigator: ZenNavigator) {
    val store = container.playerStore
    val sound = container.soundManager
    val soundEffects by store.soundEffects.collectAsState()
    val context = LocalContext.current

    ZenScreen {
        ZenTopBar(
            title = stringResource(id = R.string.settings_title),
            onBack = { navigator.back() },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ZenCard {
                SwitchRow(
                    title = stringResource(id = R.string.settings_sound_title),
                    subtitle = stringResource(id = R.string.settings_sound_subtitle),
                    iconRes = if (soundEffects) R.drawable.ic_sound_on else R.drawable.ic_sound_off,
                    checked = soundEffects,
                    onCheckedChange = { enabled ->
                        store.setSoundEffects(enabled)
                        if (enabled) {
                            // Immediate confirmation that effects are back on.
                            sound.play(Sfx.CLICK)
                        }
                    },
                )
            }

            ZenCard {
                Text(
                    text = stringResource(id = R.string.settings_purchases_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(10.dp))
                ZenRow(
                    title = stringResource(id = R.string.shop_restore),
                    subtitle = stringResource(id = R.string.settings_restore_subtitle),
                    iconRes = R.drawable.ic_restart,
                    onClick = {
                        sound.play(Sfx.CLICK)
                        container.billingManager.restorePurchases()
                        navigator.toShop()
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                ZenRow(
                    title = stringResource(id = R.string.menu_shop),
                    subtitle = stringResource(id = R.string.menu_shop_subtitle),
                    iconRes = R.drawable.ic_shop,
                    onClick = { navigator.toShop() },
                )
            }

            ZenCard {
                Text(
                    text = stringResource(id = R.string.settings_about_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(10.dp))
                ZenRow(
                    title = stringResource(id = R.string.menu_privacy),
                    subtitle = stringResource(id = R.string.menu_privacy_subtitle),
                    iconRes = R.drawable.ic_shield,
                    onClick = { navigator.toPrivacy() },
                )
                Spacer(modifier = Modifier.height(8.dp))
                ZenRow(
                    title = stringResource(id = R.string.settings_support_title),
                    subtitle = AppConfig.SUPPORT_EMAIL,
                    iconRes = R.drawable.ic_mail,
                    onClick = {
                        sound.play(Sfx.CLICK)
                        openSupportEmail(context)
                    },
                )
            }

            ZenCard {
                InfoLine(
                    label = stringResource(id = R.string.settings_app_name),
                    value = stringResource(id = R.string.app_name),
                )
                InfoLine(
                    label = stringResource(id = R.string.settings_version),
                    value = stringResource(id = R.string.settings_version_value, AppConfig.VERSION_NAME, AppConfig.VERSION_CODE),
                )
                InfoLine(
                    label = stringResource(id = R.string.settings_package),
                    value = AppConfig.APPLICATION_ID,
                )
                InfoLine(
                    label = stringResource(id = R.string.settings_languages),
                    value = stringResource(id = R.string.settings_languages_value),
                )
                InfoLine(
                    label = stringResource(id = R.string.settings_content_rating),
                    value = stringResource(id = R.string.settings_content_rating_value),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(id = R.string.settings_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.menu_no_ads),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun openSupportEmail(context: android.content.Context) {
    val intent = Intent(
        Intent.ACTION_SENDTO,
        Uri.parse("mailto:${AppConfig.SUPPORT_EMAIL}?subject=Color%20Zen"),
    )
    try {
        context.startActivity(intent)
    } catch (ignored: ActivityNotFoundException) {
        // No mail client installed; the address is visible on screen anyway.
    }
}
