package com.letscode.geopincam

import android.app.Application
import com.letscode.geopincam.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Owns the dependency container for the life of the process. */
class GeoPinCamApp : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Temporary capture files can survive a crash; clear the old ones on start.
        applicationScope.launch { container.photoRepository.clearStaleCache() }
    }
}
