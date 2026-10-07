package `in`.voxagent.mobile.connections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Smoke
import `in`.voxagent.mobile.ui.theme.SmokeDark
import `in`.voxagent.mobile.ui.theme.VoidBlack
import `in`.voxagent.mobile.ui.voxFieldColors

@Composable
internal fun PlayStationTokenInput(
    npssoToken: String,
    onTokenChange: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    var npssoVisible by remember { mutableStateOf(false) }
    var showHelpAccordion by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "PlayStation NPSSO Token",
            color = Mist,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        OutlinedTextField(
            value = npssoToken,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text("Paste your 64-char account token", color = SmokeDark, fontSize = 13.sp)
            },
            visualTransformation =
                if (npssoVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = voxFieldColors(),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                Text(
                    text = if (npssoVisible) "Hide" else "Show",
                    color = Smoke,
                    fontSize = 12.sp,
                    modifier =
                        Modifier.clickable { npssoVisible = !npssoVisible }
                            .padding(horizontal = 12.dp),
                )
            },
        )

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VoidBlack)
                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
        ) {
            Column {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { showHelpAccordion = !showHelpAccordion }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "How do I get this token?", color = Mist, fontSize = 13.sp)
                    Text(
                        text = if (showHelpAccordion) "−" else "+",
                        color = SmokeDark,
                        fontSize = 16.sp,
                    )
                }

                AnimatedVisibility(
                    visible = showHelpAccordion,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text =
                                "1. Sign in to your PlayStation account at playstation.com in your browser.",
                            color = Smoke,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                        Text(
                            text =
                                "2. Open the Sony token page below. It displays a short JSON response.",
                            color = Smoke,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                        Text(
                            text =
                                "3. Copy the 64-character value after \"npsso\" and paste it above.",
                            color = Smoke,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )

                        Button(
                            onClick = {
                                onOpenExternal("https://ca.account.sony.com/api/v1/ssocookie")
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = BorderSubtle,
                                    contentColor = Mist,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open token page in browser", fontSize = 12.sp)
                        }

                        Text(
                            text =
                                "Keep this token private. It is not an official Sony feature and may need re-entry periodically.",
                            color = SmokeDark,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}
