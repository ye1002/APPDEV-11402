package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar

/**
 * 夕陽預報主 ViewModel
 * 負責維護當前選定位置、日期、計算結果，以及與 Android 定位服務的交互狀態。
 */
class SunsetViewModel : ViewModel() {

    // 定義預設的熱門觀測點 / 城市，供使用者快速切換
    val presetCities = listOf(
        PresetCity("當前定位/自訂", 25.0330, 121.5654),
        PresetCity("台北市 (信義區)", 25.0330, 121.5654),
        PresetCity("台中市 (高美濕地)", 24.3120, 120.5501),
        PresetCity("高雄市 (西子灣)", 22.6256, 120.2647),
        PresetCity("花蓮市 (七星潭)", 24.0270, 121.6295),
        PresetCity("恆春 (關山日落)", 21.9961, 120.7145),
        PresetCity("澎湖縣 (觀音亭)", 23.5701, 119.5601),
        PresetCity("東京都 (富士山)", 35.3606, 138.7274),
        PresetCity("巴黎 (艾菲爾鐵塔)", 48.8584, 2.2945),
        PresetCity("紐約市 (曼哈頓)", 40.7128, -74.0060)
    )

    data class PresetCity(val name: String, val lat: Double, val lng: Double)

    // UI 狀態定義
    data class UiState(
        val latitude: Double = 25.0330, // 預設台北 101 座標
        val longitude: Double = 121.5654,
        val cityName: String = "台北市 (預設)",
        val selectedDate: Calendar = Calendar.getInstance(),
        val sunsetResult: SunsetCalculator.SunsetResult = SunsetCalculator.calculate(
            25.0330, 121.5654, Calendar.getInstance()
        ),
        val statusMessage: String = "預置完畢，正在顯示與預測夕陽",
        val isLocating: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 手動修改經緯度或選擇城市時觸發
     */
    fun updateCoordinates(lat: Double, lng: Double, cityName: String) {
        _uiState.update { state ->
            val updatedResult = SunsetCalculator.calculate(lat, lng, state.selectedDate)
            state.copy(
                latitude = lat,
                longitude = lng,
                cityName = cityName,
                sunsetResult = updatedResult,
                statusMessage = "成功切換地區至 $cityName"
            )
        }
    }

    /**
     * 手動修改經度 (從文字輸入框修改)
     */
    fun updateLongitudeText(lngStr: String) {
        val lat = _uiState.value.latitude
        val lng = lngStr.toDoubleOrNull() ?: _uiState.value.longitude
        updateCoordinates(lat, lng, "自訂座標")
    }

    /**
     * 手動修改緯度 (從文字輸入框修改)
     */
    fun updateLatitudeText(latStr: String) {
        val lat = latStr.toDoubleOrNull() ?: _uiState.value.latitude
        val lng = _uiState.value.longitude
        updateCoordinates(lat, lng, "自訂座標")
    }

    /**
     * 使用者選擇不同日期時觸發
     */
    fun updateDate(year: Int, monthIndex: Int, dayOfMonth: Int) {
        _uiState.update { state ->
            val newCalendar = (state.selectedDate.clone() as Calendar).apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, monthIndex)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            val updatedResult = SunsetCalculator.calculate(state.latitude, state.longitude, newCalendar)
            state.copy(
                selectedDate = newCalendar,
                sunsetResult = updatedResult,
                statusMessage = "日期切換至：${year}/${monthIndex + 1}/${dayOfMonth}"
            )
        }
    }

    /**
     * 啟動 Android Fused Location Client 獲取最新的 GPS 位置
     */
    @SuppressLint("MissingPermission")
    fun requestGpsLocation(fusedLocationClient: FusedLocationProviderClient) {
        _uiState.update { it.copy(isLocating = true, statusMessage = "正在搜尋最新衛星信號，獲取 GPS 定位...") }

        // 使用較新的 getCurrentLocation 常規方法
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                applyNewLocation(location, "當前 GPS 定位")
            } else {
                // 如果 getCurrentLocation 返回空，降級使用 lastLocation (上一次被系統快取的定位)
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                    if (lastLoc != null) {
                        applyNewLocation(lastLoc, "歷史緩存 GPS 定位")
                    } else {
                        _uiState.update { state ->
                            state.copy(
                                isLocating = false,
                                statusMessage = "GPS 訊號回傳為空，請確認定位服務/網路是否正常開啟。"
                            )
                        }
                    }
                }.addOnFailureListener { exception ->
                    _uiState.update { state ->
                        state.copy(
                            isLocating = false,
                            statusMessage = "快取定位獲取失敗: ${exception.localizedMessage}"
                        )
                    }
                }
            }
        }.addOnFailureListener { exception ->
            // 如果高精度 getCurrentLocation 發生致命錯誤，嘗試直接降級拉 lastLocation
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                if (lastLoc != null) {
                    applyNewLocation(lastLoc, "歷史緩存 GPS 定位")
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isLocating = false,
                            statusMessage = "GPS 模塊回應超時，請重讀或手動經緯輸入。"
                        )
                    }
                }
            }.addOnFailureListener { err ->
                _uiState.update { state ->
                    state.copy(
                        isLocating = false,
                        statusMessage = "定位異常: ${err.localizedMessage}，請確認權限或點擊自訂地點。"
                    )
                }
            }
        }
    }

    private fun applyNewLocation(location: Location, sourceName: String) {
        _uiState.update { state ->
            // 精簡經緯度至小數 4 位以優化 UI 呈現
            val formattedLat = Math.round(location.latitude * 10000.0) / 10000.0
            val formattedLng = Math.round(location.longitude * 10000.0) / 10000.0
            val updatedResult = SunsetCalculator.calculate(formattedLat, formattedLng, state.selectedDate)
            state.copy(
                latitude = formattedLat,
                longitude = formattedLng,
                cityName = sourceName,
                sunsetResult = updatedResult,
                isLocating = false,
                statusMessage = "GPS 定位刷新成功！來源：$sourceName"
            )
        }
    }
}
