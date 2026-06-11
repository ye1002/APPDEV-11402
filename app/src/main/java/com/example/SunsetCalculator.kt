package com.example

import java.util.Calendar
import kotlin.math.*

/**
 * 夕陽計算工具：基於 NOAA (美國國家海洋暨大氣總署) 簡化演算法
 * 提供高精度的日落時間、日落方位角，以及白晝長度計算。
 */
object SunsetCalculator {

    /**
     * 計算結果資料載體
     * @param sunsetTimeFormatted 格式化後的本地日落時間 (例如 "18:43")
     * @param sunsetHour 本地日落小時 (24小時制)
     * @param sunsetMinute 本地日落分鐘
     * @param sunsetSecond 本地日落秒數
     * @param azimuth 太陽落下方位角 (角度：0 到 360 度，以正北為 0°，順時針計算)
     * @param isPolarDay 是否為極晝 (太陽整天未落)
     * @param isPolarNight 是否為極夜 (太陽整天未升)
     * @param rAsDegrees 未經緯度修正的半經 (僅供內部的地球學調試)
     */
    data class SunsetResult(
        val sunsetTimeFormatted: String,
        val sunsetHour: Int,
        val sunsetMinute: Int,
        val sunsetSecond: Int,
        val azimuth: Double,
        val isPolarDay: Boolean = false,
        val isPolarNight: Boolean = false,
        val daylightDurationMinutes: Double = 0.0
    )

    /**
     * 預測給定日期、經緯度下的日落資訊。
     * @param latitude 緯度 (正數表示北緯，負數表示南緯)
     * @param longitude 經度 (正數表示東經，負數表示西經)
     * @param calendar 日期與本地時區資訊
     * @param customTimezoneOffset 指定時區調整值 (若為 null 則依據經度自動估計幾何時區)
     */
    fun calculate(
        latitude: Double,
        longitude: Double,
        calendar: Calendar,
        customTimezoneOffset: Double? = null
    ): SunsetResult {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        // 1. 本地時區偏移量 (單位：小時)
        // 為了避免裝置/雲端伺服器與目標觀測點不同產生的幾小時時間差偏誤，
        // 預設依據當前經度估計幾何時區，或者採用使用者手動微調的 customTimezoneOffset
        val timezoneOffsetHours = customTimezoneOffset ?: Math.round(longitude / 15.0).toDouble()

        // 估計日落發生的概略時段 (假設為下午 6 點，轉換為分數：18 * 60 = 1080)
        // 估計日落的 fractional year (gamma) 角度在一天中的變動弧度
        val gamma = 2.0 * PI / 365.0 * (dayOfYear - 1 + (18.0 - 12.0) / 24.0)

        // 2. 計算均時差 (Equation of Time, 單位：分鐘)
        val eqt = 229.18 * (0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma))

        // 3. 計算太陽赤緯角 (Solar Declination angle, 單位：弧度)
        val delta = 0.006918 -
                0.399912 * cos(gamma) +
                0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) +
                0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) +
                0.001480 * sin(3 * gamma)

        // 4. 計算太陽高度角，當太陽邊緣與地平線重合時的修正高度角 h0 為 -0.833 度 (考慮大氣折射和太陽半徑)
        val h0 = Math.toRadians(-0.833)
        val phi = Math.toRadians(latitude)

        // 5. 計算日落時的時角 (Hour Angle, omega)
        val cosOmega = (sin(h0) - sin(phi) * sin(delta)) / (cos(phi) * cos(delta))

        if (cosOmega < -1.0) {
            // 極晝情況：太陽整天都在地平線以上，不落下
            return SunsetResult(
                sunsetTimeFormatted = "極晝 (太陽不落)",
                sunsetHour = -1,
                sunsetMinute = -1,
                sunsetSecond = -1,
                azimuth = 0.0,
                isPolarDay = true
            )
        }
        if (cosOmega > 1.0) {
            // 極夜情況：太陽整天都在地平線以下，不升起
            return SunsetResult(
                sunsetTimeFormatted = "極夜 (無日落)",
                sunsetHour = -1,
                sunsetMinute = -1,
                sunsetSecond = -1,
                azimuth = 0.0,
                isPolarNight = true
            )
        }

        // 弧度轉換為角度 (omega 是正值，對應日落)
        val omegaRad = acos(cosOmega)
        val omegaDeg = Math.toDegrees(omegaRad)

        // 白晝長度 (Daylight duration) 分鐘 = 2 * omegaDeg * 4 (每度 4 分鐘)
        val daylightMinutes = 8.0 * omegaDeg

        // 6. 計算日落的 UTC 時間 (從午夜算起的總分鐘數)
        // 經度使用東經為正，公式中減去經度修正 (每度隔 4 分鐘)
        // Sunset UTC = 720 - 4 * longitude - eqt + 4 * omegaDeg
        val sunsetUTCMinutes = 720.0 - (4.0 * longitude) - eqt + (4.0 * omegaDeg)

        // 7. 加上時區偏移量以獲得本地時間
        var localSunsetMinutes = sunsetUTCMinutes + (timezoneOffsetHours * 60.0)

        // 將時間限制在一日之內 (0 到 1440 分鐘之間)
        localSunsetMinutes = (localSunsetMinutes % 1440.0 + 1440.0) % 1440.0

        val totalSeconds = (localSunsetMinutes * 60.0).roundToInt()
        val hour = (totalSeconds / 3600) % 24
        val minute = (totalSeconds % 3600) / 60
        val second = totalSeconds % 60

        val formattedTime = String.format("%02d:%02d:%02d", hour, minute, second)

        // 8. 計算日落方位角 (Solar Azimuth, 大氣折射修正)
        // 方位角公式: cos(Azimuth) = (sin(delta) - sin(h0)*sin(phi)) / (cos(h0)*cos(phi))
        val cosAz = (sin(delta) - sin(h0) * sin(phi)) / (cos(h0) * cos(phi))
        val clampedCosAz = cosAz.coerceIn(-1.0, 1.0)
        val azimuthRad = acos(clampedCosAz)
        val azimuthDeg = Math.toDegrees(azimuthRad)

        // 因為日落在西方，所以方位角在 180° - 360° 之間，
        // 依據球形三角學，日落方位角為 360° - 方位角
        val sunsetAzimuth = 360.0 - azimuthDeg

        return SunsetResult(
            sunsetTimeFormatted = formattedTime,
            sunsetHour = hour,
            sunsetMinute = minute,
            sunsetSecond = second,
            azimuth = sunsetAzimuth,
            isPolarDay = false,
            isPolarNight = false,
            daylightDurationMinutes = daylightMinutes
        )
    }

    /**
     * 獲取方位角對應的文字方向 (羅盤縮寫)
     */
    fun getCardinalDirection(azimuth: Double): String {
        val directions = arrayOf(
            "正北 (N)", "北東北 (NNE)", "東北 (NE)", "東東北 (ENE)",
            "正東 (E)", "東東南 (ESE)", "東南 (SE)", "南東南 (SSE)",
            "正南 (S)", "南西南 (SSW)", "西南 (SW)", "西西南 (WSW)",
            "正西 (W)", "西西北 (WNW)", "西北 (NW)", "北西北 (NNW)"
        )
        val index = ((azimuth + 11.25) % 360 / 22.5).toInt()
        return directions[index % directions.size]
    }
}
