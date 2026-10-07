package `in`.voxagent.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.VoxAtmosphereBackground
import `in`.voxagent.mobile.ui.VoxLogo
import `in`.voxagent.mobile.ui.VoxPrimaryButton
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.VoxSpaceGroteskFontFamily

@Composable
internal fun SignInScreen(statusMessage: String, onSignIn: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        VoxAtmosphereBackground(showGlow = true, noiseOpacity = 0.22f)
        SignInContent(statusMessage = statusMessage, onSignIn = onSignIn)
    }
}

@Composable
internal fun SignInContent(statusMessage: String, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.fillMaxHeight(0.25f))
        VoxLogo(modifier = Modifier.offset(y = (-20).dp), size = 80.dp, animated = true)

        Column(
            modifier = Modifier.offset(y = (-18).dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Vox",
                    color = Mist,
                    fontFamily = VoxSpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 36.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(50))
                            .background(androidx.compose.ui.graphics.Color(0xFF3A1418))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "MOBILE",
                        color = Mist,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }
            Text(
                text =
                    "Talk to your agent, manage tasks, and work with your data — all in one place.",
                color = GraphiteDark,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            if (statusMessage.isNotEmpty()) {
                Text(text = statusMessage, color = CoralPulse, textAlign = TextAlign.Center)
            }
            VoxPrimaryButton(
                text = "Continue with Google",
                icon = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_google),
                        contentDescription = null,
                    )
                },
                onClick = onSignIn,
            )
        }
    }
}
