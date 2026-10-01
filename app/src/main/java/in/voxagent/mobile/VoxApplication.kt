package `in`.voxagent.mobile

import android.app.Application
import timber.log.Timber

class VoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
