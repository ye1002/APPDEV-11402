package com.example

import android.Manifest
import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val viewModel: SunsetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 啟用全螢幕 Edge-to-Edge 沉浸式體驗
        enableEdgeToEdge()
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MyApplicationTheme {
                SunsetPredictorScreen(viewModel = viewModel, fusedLocationClient = fusedLocationClient)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SunsetPredictorScreen(
    viewModel: SunsetViewModel,
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 電子儀表板和日出日落的呼吸動畫
    val infiniteTransition = rememberInfiniteTransition(label = "SunGlow")
    val sunGlowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowScale"
    )

    // 處理 Android 經緯度定位運行時權限 (使用 Accompanist Permissions 庫)
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // 當權限第一次被允許時，自動執行定位
    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted) {
            viewModel.requestGpsLocation(fusedLocationClient)
        }
    }

    // 主背景天際線漸層色 (模擬傍晚日落至暮色黃昏的氛圍)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0B1E), // 深邃星夜紫
                        Color(0xFF1F112D), // 黃昏暗夜紫
                        Color(0xFF381530), // 夕陽晚霞紅
                        Color(0xFF4C2025)  // 地平線餘暉橘
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent, // 保持背景漸層透出
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing // 確保不被前相機挖孔與系統底條裁剪
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 應用程式美學標題
                HeaderSection()

                // 2. 夕陽黃金英雄看板 (Hero Display)
                SunsetGoldenCard(uiState = uiState, glowScale = sunGlowScale)

                // 3. 方位角太陽羅盤 (Sunset Compass Disc)
                SunsetCompassCard(azimuth = uiState.sunsetResult.azimuth, glowScale = sunGlowScale)

                // 4. 便捷設定與定位中樞 (Dashboard Control)
                DashboardControlSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    permissionsState = locationPermissionsState,
                    onRequestGps = {
                        if (locationPermissionsState.allPermissionsGranted) {
                            viewModel.requestGpsLocation(fusedLocationClient)
                        } else {
                            locationPermissionsState.launchMultiplePermissionRequest()
                        }
                    }
                )

                // 5. 天文科普小卡與底部宣告
                FooterSection()
            }
        }
    }
}

@Composable
fun HeaderSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.WbTwilight,
                contentDescription = "夕陽圖誌",
                tint = Color(0xFFFFB74D),
                modifier = Modifier
                    .size(28.dp)
                    .testTag("app_logo_icon")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "夕 陽 預 報",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
        Text(
            text = "基於天文經緯星曆與 NOAA 演算法日落時間方位角預測",
            fontSize = 11.sp,
            color = Color(0xFFB0BEC5),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun SunsetGoldenCard(
    uiState: SunsetViewModel.UiState,
    glowScale: Float
) {
    val result = uiState.sunsetResult
    val dateFormat = SimpleDateFormat("yyyy 年 MM 月 dd 日 (EEEE)", Locale.CHINESE)
    val dateString = dateFormat.format(uiState.selectedDate.time)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
        border = BorderStroke(1.dp, Color(0x44FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sunset_hero_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 地區標籤
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color(0x22FFFFFF))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "觀測選點",
                    tint = Color(0xFFFF8A65),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = uiState.cityName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 主時間顯示
            if (result.isPolarDay) {
                Text(
                    text = "極晝 (太陽不落)",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFD54F),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else if (result.isPolarNight) {
                Text(
                    text = "極夜 (無日落)",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF90A4AE),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "預測日落時刻",
                        fontSize = 12.sp,
                        color = Color(0xFFFFB74D),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        // 霓虹背光呼吸圓球
                        Canvas(
                            modifier = Modifier
                                .size(140.dp)
                                .graphicsLayer(rotationZ = glowScale * 5f)
                        ) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0x1FEE9A49), Color.Transparent)
                                ),
                                radius = size.minDimension * glowScale * 0.7f
                            )
                        }

                        Text(
                            text = result.sunsetTimeFormatted.substringBeforeLast(":"),
                            fontSize = 58.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontFamily = FontFamily.Serif,
                            modifier = Modifier.testTag("sunset_time_text")
                        )
                    }
                    Text(
                        text = "精確至秒: ${result.sunsetTimeFormatted}",
                        fontSize = 11.sp,
                        color = Color(0xAAFFFFFF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 分割線
            HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // 天文多維數據 (方位角 + 白晝總長)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "夕陽落下方位角", fontSize = 11.sp, color = Color(0xFFB0BEC5))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format("%.2f°", result.azimuth),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF8A65)
                    )
                    Text(
                        text = SunsetCalculator.getCardinalDirection(result.azimuth),
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(35.dp)
                        .background(Color(0x22FFFFFF))
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "預測白晝總長", fontSize = 11.sp, color = Color(0xFFB0BEC5))
                    Spacer(modifier = Modifier.height(4.dp))
                    if (result.isPolarDay) {
                        Text(text = "24 小時", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                    } else if (result.isPolarNight) {
                        Text(text = "0 小時", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF90A4AE))
                    } else {
                        val hours = (result.daylightDurationMinutes / 60.0).toInt()
                        val minutes = (result.daylightDurationMinutes % 60.0).toInt()
                        Text(
                            text = "${hours}小時 ${minutes}分鐘",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F)
                        )
                    }
                    Text(text = "地心緯向投影計", fontSize = 12.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0x11FFFFFF), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // 底部日曆提示
            Text(
                text = dateString,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFECEFF1)
            )
        }
    }
}

@Composable
fun SunsetCompassCard(
    azimuth: Double,
    glowScale: Float
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sunset_compass_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "落下方位角虛擬羅盤儀",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Text(
                text = "紅色指針代表正北 0°，暖金太陽代表該經緯度的日落地點",
                fontSize = 10.sp,
                color = Color(0xFFCFD8DC),
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 自訂 Canvas 繪製專利夕陽羅盤
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(220.dp)
                    .background(Color(0x11000000), shape = CircleShape)
                    .border(2.dp, Color(0x1EFFFFFF), CircleShape)
            ) {
                Canvas(
                    modifier = Modifier.size(190.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val center = Offset(width / 2f, height / 2f)
                    val radius = width / 2f

                    // A. 繪製羅盤刻度線 (每 15 度一格，共 24 格)
                    for (angle in 0 until 360 step 15) {
                        val isMajor = angle % 90 == 0
                        val tickLength = if (isMajor) 16f else 8f
                        val strokeW = if (isMajor) 3f else 1.5f
                        val rad = Math.toRadians(angle.toDouble())

                        val startX = center.x + (radius - tickLength) * cos(rad).toFloat()
                        val startY = center.y + (radius - tickLength) * sin(rad).toFloat()
                        val endX = center.x + radius * cos(rad).toFloat()
                        val endY = center.y + radius * sin(rad).toFloat()

                        drawLine(
                            color = if (isMajor) Color(0x99FFFFFF) else Color(0x44FFFFFF),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = strokeW
                        )
                    }

                    // B. 繪製羅盤內同心圓
                    drawCircle(
                        color = Color(0x11FFFFFF),
                        radius = radius * 0.7f,
                        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    )

                    // C. 繪製指北針 (紅色三角形指向 12 點鐘方向 0°)
                    val northRad = Math.toRadians(-90.0)
                    val triangleLeftRad = Math.toRadians(-105.0)
                    val triangleRightRad = Math.toRadians(-75.0)

                    val path = Path().apply {
                        moveTo(center.x + (radius - 10f) * cos(northRad).toFloat(), center.y + (radius - 10f) * sin(northRad).toFloat())
                        lineTo(center.x + 12f * cos(triangleLeftRad).toFloat(), center.y + 12f * sin(triangleLeftRad).toFloat())
                        lineTo(center.x + 12f * cos(triangleRightRad).toFloat(), center.y + 12f * sin(triangleRightRad).toFloat())
                        close()
                    }
                    drawPath(path = path, color = Color(0xFFEF5350)) // 正北鮮紅三角

                    // D. 繪製四個正字方位 (N, S, E, W) 文字，使用 nativeCanvas 以相容無 SDK 版本
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 28f
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }

                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    // 北 N
                    nativeCanvas.drawText("N", center.x, center.y - radius + 45f, textPaint)
                    // 南 S
                    nativeCanvas.drawText("S", center.x, center.y + radius - 25f, textPaint)

                    textPaint.color = android.graphics.Color.parseColor("#CCCCCC")
                    textPaint.textSize = 25f
                    // 東 E
                    nativeCanvas.drawText("E", center.x + radius - 30f, center.y + 9f, textPaint)
                    // 西 W
                    nativeCanvas.drawText("W", center.x - radius + 30f, center.y + 9f, textPaint)

                    // E. 繪製夕陽落山雷射金線與發光太陽 (方位角順時針偏移，正北 0° 對應 -90° 弧度)
                    val adjustedAzimuth = azimuth - 90.0
                    val azimuthRad = Math.toRadians(adjustedAzimuth)

                    // 雷射金色餘暉虛線
                    drawLine(
                        color = Color(0xFFFFB300),
                        start = center,
                        end = Offset(
                            x = center.x + (radius - 40f) * cos(azimuthRad).toFloat(),
                            y = center.y + (radius - 40f) * sin(azimuthRad).toFloat()
                        ),
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 5f), 0f)
                    )

                    // 發光圓心
                    drawCircle(
                        color = Color(0xFFFFB300),
                        radius = 8f
                    )

                    // 金黃夕陽在圓環外緣
                    val sunX = center.x + (radius - 12f) * cos(azimuthRad).toFloat()
                    val sunY = center.y + (radius - 12f) * sin(azimuthRad).toFloat()

                    // 夕陽輻射光暈
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFEE58), Color(0xFFFF7043), Color.Transparent),
                            center = Offset(sunX, sunY),
                            radius = 28f * glowScale
                        ),
                        center = Offset(sunX, sunY),
                        radius = 28f * glowScale
                    )

                    // 太陽實體
                    drawCircle(
                        color = Color(0xFFFF7043), // 晚霞暖橘
                        center = Offset(sunX, sunY),
                        radius = 12f
                    )
                    drawCircle(
                        color = Color(0xFFFFD54F), // 太陽核心亮黃
                        center = Offset(sunX, sunY),
                        radius = 6f
                    )
                }

                // 中心羅盤圓形飾蓋，顯示當前方位角度字樣
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(Color(0xE01F112D), CircleShape)
                        .border(1.dp, Color(0x44FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "角度",
                            fontSize = 9.sp,
                            color = Color(0xFFB0BEC5),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format("%.1f°", azimuth),
                            fontSize = 13.sp,
                            color = Color(0xFFFFCA28),
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = SunsetCalculator.getCardinalDirection(azimuth).substringBefore(" "),
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFFF7043), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val cardDir = SunsetCalculator.getCardinalDirection(azimuth)
                Text(
                    text = "今日夕陽將在正北偏順時針 ${String.format("%.1f", azimuth)}° 處落山 (約為 $cardDir 側)",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DashboardControlSection(
    uiState: SunsetViewModel.UiState,
    viewModel: SunsetViewModel,
    permissionsState: MultiplePermissionsState,
    onRequestGps: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: 智慧定位與切換, 1: 進階手動輸入
    var showPresetMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_controls")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // A. 控制台頁籤
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFFFFB74D)
                    )
                },
                divider = { HorizontalDivider(color = Color(0x11FFFFFF)) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("選點與定位", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("進階微調經緯", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            // 頁面內容 0: 定位與快速切換
            if (selectedTab == 0) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 自動 GPS 定位發射大按鈕
                    Button(
                        onClick = onRequestGps,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isLocating) Color(0xFFE0F2F1) else Color(0xFFFF8A65),
                            contentColor = if (uiState.isLocating) Color.Gray else Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("gps_request_button")
                    ) {
                        if (uiState.isLocating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF00796B),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("尋找衛星中...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = if (permissionsState.allPermissionsGranted) Icons.Default.MyLocation else Icons.Default.LocationDisabled,
                                contentDescription = "GPS 定位"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (permissionsState.allPermissionsGranted) "自動定位：更新當前經緯度" else "啟用 GPS 自動衛星定位",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 定位狀態日誌列
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x15000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "定位反饋",
                            tint = Color(0xFFCFD8DC),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.statusMessage,
                            fontSize = 11.sp,
                            color = Color(0xFFECEFF1)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "或快速選擇全球熱門夕陽觀測點：",
                        fontSize = 11.sp,
                        color = Color(0xFFB0BEC5),
                        fontWeight = FontWeight.Medium
                    )

                    // 橫向滑動推薦地區列表 (讓 App 在模擬器內零阻塞地玩耍)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.presetCities.filter { it.name != "當前定位/自訂" }.forEach { city ->
                            val isSelected = uiState.cityName.contains(city.name.substringBefore(" "))
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.updateCoordinates(city.lat, city.lng, city.name)
                                },
                                label = { Text(city.name, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFB74D),
                                    selectedLabelColor = Color(0xFF1F112D),
                                    containerColor = Color(0x11FFFFFF),
                                    labelColor = Color.White
                                ),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFB74D) else Color(0x33FFFFFF)),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            } else {
                // 頁面內容 1: 進階自訂手動座標輸入
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "請輸入目地地理經緯度點位：",
                        fontSize = 12.sp,
                        color = Color(0xFFCFD8DC),
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 經度
                        OutlinedTextField(
                            value = uiState.longitude.toString(),
                            onValueChange = { viewModel.updateLongitudeText(it) },
                            label = { Text("經度 (E 120 ~ 122 為台灣)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFB74D),
                                unfocusedBorderColor = Color(0x44FFFFFF)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("longitude_input_field")
                        )

                        // 緯度
                        OutlinedTextField(
                            value = uiState.latitude.toString(),
                            onValueChange = { viewModel.updateLatitudeText(it) },
                            label = { Text("緯度 (N 22 ~ 25 為台灣)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFB74D),
                                unfocusedBorderColor = Color(0x44FFFFFF)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("latitude_input_field")
                        )
                    }

                    // 地理區限與半球備註
                    Text(
                        text = "特別體貼：北緯與東經以正值表示（如台北 N25, E121）；南半球緯度或西半球經度請輸入負號。",
                        fontSize = 9.sp,
                        color = Color(0xFFB0BEC5),
                        style = androidx.compose.ui.text.TextStyle(lineHeight = 13.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))

            // B. 預測日期切換器 (極致美觀)
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "預期預測日期：",
                    fontSize = 11.sp,
                    color = Color(0xFFB0BEC5),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))

                val calendar = uiState.selectedDate
                // 顯示日期與按鈕
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                viewModel.updateDate(year, month, day)
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("date_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "設定日期",
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(
                            "觀測日期: %04d / %02d / %02d (點擊更換)",
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH) + 1,
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FooterSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 科普卡紙
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "💡 天文科普知識",
                    fontSize = 12.sp,
                    color = Color(0xFFFFB74D),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "1. 方位角 (Azimuth)：以「正北」為 0°，順時針計算。正東為 90°、正南為 180°、正西為 270°。北半球夏季夕陽偏向西北方關照，冬季則朝向西南方落幕。\n" +
                            "2. 大氣折射修正 (Atmospheric Refraction)：當我們在地平線看見日落一瞬時，由於空氣折射原理，太陽幾何學實體其實早就完全隱沒入水平線下了！NOAA 公式已體貼整合此 -0.833° 的光學差額補償。",
                    fontSize = 10.sp,
                    color = Color(0xFFCFD8DC),
                    style = androidx.compose.ui.text.TextStyle(lineHeight = 15.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "由 繁體中文天文計算引擎 · 100% 離線安全計算",
            fontSize = 9.sp,
            color = Color(0x66FFFFFF),
            textAlign = TextAlign.Center
        )
    }
}

