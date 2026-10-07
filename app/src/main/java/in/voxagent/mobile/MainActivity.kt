package `in`.voxagent.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import `in`.voxagent.mobile.auth.AuthManager
import `in`.voxagent.mobile.ui.theme.VoxTheme

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF040506.toInt()))
        window.decorView.setBackgroundColor(0xFF040506.toInt())
        `in`.voxagent.mobile.logging.RemoteLog.init(applicationContext)
        authManager = AuthManager(applicationContext)

        setContent {
            VoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(authManager = authManager, activity = this)
                }
            }
        }
    }
}
