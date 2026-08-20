package com.ar13x.jarvis.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ar13x.jarvis.designsystem.component.BrandBackdrop
import com.ar13x.jarvis.designsystem.component.softShadow
import com.ar13x.jarvis.designsystem.theme.Corner
import com.ar13x.jarvis.designsystem.theme.JarvisTheme
import com.ar13x.jarvis.designsystem.theme.Space

/**
 * First run (plan §5.5).
 *
 * The token is entered once and never shown again. It is masked because this
 * screen is the one place it exists in plain sight, and a shared bearer token
 * on screen in a coffee shop is a worse trade than the occasional typo — which
 * the probe catches anyway, with a message that says which half went wrong.
 */
@Composable
fun TokenScreen(
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TokenViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = JarvisTheme.colors

    LaunchedEffect(state.saved) { if (state.saved) onConnected() }

    BrandBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Space.Gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(120.dp))

            Text(
                text = "Connect\nto Jarvis",
                style = JarvisTheme.typography.displayMedium,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.x3))
            Text(
                text = "Jarvis lives on your tailnet. Paste the gateway token to pair this phone.",
                style = JarvisTheme.typography.bodyMedium,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Space.x8))

            Column(
                Modifier
                    .fillMaxWidth()
                    .softShadow(Corner.Lg, tight = 2.dp, wide = 20.dp, tint = colors.shadowTint)
                    .clip(Corner.Lg)
                    .background(colors.surface, Corner.Lg)
                    .then(
                        if (colors.isDark) Modifier.border(1.dp, colors.hairline, Corner.Lg) else Modifier,
                    )
                    .padding(Space.x4),
                verticalArrangement = Arrangement.spacedBy(Space.x4),
            ) {
                Field(
                    label = "Gateway",
                    value = state.url,
                    onChange = viewModel::onUrlChange,
                    masked = false,
                    imeAction = ImeAction.Next,
                    onSubmit = {},
                )
                Field(
                    label = "Token",
                    value = state.token,
                    onChange = viewModel::onTokenChange,
                    masked = true,
                    imeAction = ImeAction.Go,
                    onSubmit = viewModel::connect,
                )
            }

            if (state.diagnosis != null) {
                Spacer(Modifier.height(Space.x4))
                Text(
                    text = state.diagnosis!!,
                    style = JarvisTheme.typography.bodyMedium,
                    color = colors.brandCore,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(Space.x6))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(Corner.Cta)
                    .background(
                        if (state.canSubmit) colors.brandCore else colors.surfaceSunk,
                        Corner.Cta,
                    )
                    .clickable(enabled = state.canSubmit, onClick = viewModel::connect),
                contentAlignment = Alignment.Center,
            ) {
                if (state.probing) {
                    CircularProgressIndicator(
                        color = colors.onBrand,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = "Connect",
                        style = JarvisTheme.typography.labelLarge,
                        color = if (state.canSubmit) colors.onBrand else colors.inkMuted,
                    )
                }
            }

            Spacer(Modifier.height(Space.x12))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    masked: Boolean,
    imeAction: ImeAction,
    onSubmit: () -> Unit,
) {
    val colors = JarvisTheme.colors
    Column {
        Text(
            text = label.uppercase(),
            style = JarvisTheme.typography.labelSmall,
            color = colors.inkMuted,
        )
        Spacer(Modifier.height(Space.x2))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(Corner.Sm)
                .background(colors.surfaceSunk, Corner.Sm)
                .padding(horizontal = Space.x3, vertical = Space.x3),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = JarvisTheme.typography.bodyLarge.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.brandCore),
                visualTransformation = if (masked) {
                    PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (masked) KeyboardType.Password else KeyboardType.Uri,
                    imeAction = imeAction,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
