package com.barometer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.barometer.data.DayData
import com.barometer.data.GraphProvider
import com.barometer.work.WorkScheduler
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = GraphProvider.get(application)
    private val pressureRepository = graph.pressureRepository
    private val settingsRepository = graph.settingsRepository

    private val selectedDay = MutableStateFlow(LocalDate.now())
    private val refreshTick = MutableStateFlow(0)
    private val messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val refreshing = MutableStateFlow(false)

    val snackMessages = messages.asSharedFlow()
    val isRefreshing = refreshing.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val useFgs: StateFlow<Boolean> = settingsRepository.useFgsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    val dayPages: StateFlow<List<LocalDate>> = pressureRepository.getDistinctDays(limit = 60)
        .map { days ->
            (listOf(LocalDate.now()) + days)
                .distinct()
                .sortedDescending()
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            listOf(LocalDate.now()),
        )

    val dayData: StateFlow<DayData> = combine(selectedDay, refreshTick) { day, _ -> day }
        .flatMapLatest { day ->
            combine(
                pressureRepository.getSamplesForDay(day),
                pressureRepository.getEventsForDay(day),
            ) { samples, events ->
                DayData(
                    date = day,
                    samples = samples,
                    events = events,
                    kpi = pressureRepository.buildKpi(samples, events),
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DayData(
                date = LocalDate.now(),
                samples = emptyList(),
                events = emptyList(),
                kpi = pressureRepository.buildKpi(emptyList(), emptyList()),
            ),
        )

    init {
        viewModelScope.launch {
            val current = settingsRepository.useFgsFlow.first()
            WorkScheduler.schedule(getApplication(), current)
        }
    }

    fun onDaySelected(day: LocalDate) {
        if (selectedDay.value != day) {
            selectedDay.value = day
        }
    }

    fun refreshCurrentDay() {
        viewModelScope.launch {
            refreshing.value = true
            refreshTick.value += 1
            delay(350)
            refreshing.value = false
        }
    }

    fun onUseFgsChanged(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseFgs(value)
            WorkScheduler.cancel(getApplication())
            WorkScheduler.schedule(getApplication(), value)
            messages.tryEmit("Rescheduled")
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(application) as T
                }
            }
        }
    }
}
