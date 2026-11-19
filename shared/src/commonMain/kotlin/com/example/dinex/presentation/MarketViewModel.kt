package com.example.dinex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

private const val RefreshEveryMillis = 60_000L

internal data class MarketUiState(
    val snapshot: MarketSnapshot? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val updatedAt: TimeSource.Monotonic.ValueTimeMark? = null,
)

/** Precios del mercado. Se conservan mientras la app sigue abierta para no gastar consultas. */
internal class MarketViewModel(
    private val load: suspend () -> MarketSnapshot = ::loadMarketSnapshot,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MarketUiState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    /** Ignora el pedido si ya hay una actualización en curso: la API gratuita limita las consultas. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        mutableState.value = mutableState.value.copy(loading = true)
        refreshJob = viewModelScope.launch {
            mutableState.value = try {
                mutableState.value.copy(snapshot = load(), loading = false, error = null, updatedAt = TimeSource.Monotonic.markNow())
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Throwable) {
                // El plan gratuito de CoinGecko responde 429 cuando una red consulta demasiado seguido.
                val message = if (failure.message.orEmpty().contains("429")) "Hay muchas consultas seguidas. Reintentamos en un minuto."
                else "No pudimos actualizar los precios. Revisa tu conexión."
                mutableState.value.copy(loading = false, error = message)
            }
        }
    }

    /** Actualiza cada minuto mientras la pantalla está abierta; se detiene al cerrarla. */
    suspend fun keepUpdated() {
        mutableState.value.updatedAt?.let { last ->
            val wait = RefreshEveryMillis - last.elapsedNow().inWholeMilliseconds
            if (wait > 0) delay(wait)
        }
        while (true) {
            refresh()
            delay(RefreshEveryMillis)
        }
    }
}
