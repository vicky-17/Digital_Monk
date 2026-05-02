package com.example.digitalmonk.service.overlay




import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.digitalmonk.ui.theme.inclusiveSans
import com.example.digitalmonk.ui.theme.inknutAntiqua


// region BlockedPageScreen
/**
 * A screen displayed when a page is blocked by Digital Monk protection.
 *
 * This screen shows a protection message and provides a button to navigate home.
 *
 * @param onGoToHomeClick Callback invoked when the "Go to Home" button is clicked.
 * @param modifier Optional modifier for the root composable.
 */
@Composable
fun BlockedPageScreen(
    onGoToHomeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlockedPageTokens.backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BlockedPageTokens.horizontalPadding)
        ) {
            ProtectionHeader()

            Spacer(modifier = Modifier.height(BlockedPageTokens.spacingBetweenHeaderAndMessage))

            BlockedMessage()

            Spacer(modifier = Modifier.height(BlockedPageTokens.spacingBetweenMessageAndButton))

            GoToHomeButton(onClick = onGoToHomeClick)
        }
    }
}
// endregion

// region ProtectionHeader
/**
 * Displays the protection header with shield emoji and title.
 */
@Composable
private fun ProtectionHeader(
    modifier: Modifier = Modifier
) {
    Text(
        text = "🛡️  Protected by Digital Monk",
        color = BlockedPageTokens.textColor,
        style = TextStyle(
            fontSize = BlockedPageTokens.headerFontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = inknutAntiqua
        ),
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
// endregion

// region BlockedMessage
/**
 * Displays the blocked page warning message with warning emojis.
 */
@Composable
private fun BlockedMessage(
    modifier: Modifier = Modifier
) {
    val warningEmoji = "⚠️"
    val blockedText = "This page is blocked by Digital Monk"

    Text(
        text = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontFamily = inknutAntiqua)) {
                append(warningEmoji)
            }
            withStyle(style = SpanStyle(fontFamily = inclusiveSans)) {
                append(" $blockedText ")
            }
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontFamily = inknutAntiqua)) {
                append(warningEmoji)
            }
        },
        color = BlockedPageTokens.textColor,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
// endregion

// region GoToHomeButton
/**
 * A button that navigates the user to the home screen.
 *
 * @param onClick Callback invoked when the button is clicked.
 * @param modifier Optional modifier for the button.
 */
@Composable
private fun GoToHomeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = BlockedPageTokens.buttonBackgroundColor
        ),
        shape = RoundedCornerShape(BlockedPageTokens.buttonCornerRadius),
        modifier = modifier
            .padding(horizontal = BlockedPageTokens.buttonHorizontalPadding)
    ) {
        Text(
            text = "← Go to Home Screen",
            color = BlockedPageTokens.textColor,
            style = TextStyle(
                fontSize = BlockedPageTokens.buttonFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = inknutAntiqua
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                horizontal = BlockedPageTokens.buttonTextHorizontalPadding,
                vertical = BlockedPageTokens.buttonTextVerticalPadding
            )
        )
    }
}
// endregion

// region Preview
@Preview(showBackground = true)
@Composable
private fun BlockedPageScreenPreview() {
    AppTheme {
        BlockedPageScreen()
    }
}
// endregion
