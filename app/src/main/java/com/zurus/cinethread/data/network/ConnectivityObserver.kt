package com.zurus.cinethread.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Наблюдатель сетевой доступности (docs/05_roadmap.md, Этап 5, компонент OfflineBanner).
 *
 * Пока устройство офлайн, репозитории возвращают устаревший Room-кэш
 * (docs/03_data_layer.md, «Порядок чтения»); [online] == false — сигнал UI
 * показать OfflineBanner («Нет сети — показываются сохранённые данные»).
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _online = MutableStateFlow(isOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _online.value = isOnline()
        }

        override fun onLost(network: Network) {
            _online.value = isOnline()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _online.value = isOnline()
        }
    }

    init {
        // runCatching: Robolectric/тестовые среды без живой ConnectivityManager.
        runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }
    }

    private fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
