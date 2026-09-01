package com.royce.heartgreatgraph

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.royce.heartgreatgraph.ui.theme.HeartGreatGraphTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {

    private lateinit var healthConnectClient: HealthConnectClient

    private val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val availabilityStatus = HealthConnectClient.getSdkStatus(this, "com.google.android.apps.healthdata")
        if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE) {
            healthConnectClient = HealthConnectClient.getOrCreate(this)
        }

        setContent {
            HeartGreatGraphTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var hasPermissions by remember { mutableStateOf(false) }
                    var refreshTrigger by remember { mutableIntStateOf(0) }
                    var heartRateData by remember { mutableStateOf<List<HeartRateRecord>>(emptyList()) }
                    var caloriesData by remember { mutableStateOf<List<androidx.health.connect.client.records.TotalCaloriesBurnedRecord>>(emptyList()) }
                    var showCalories by remember { mutableStateOf(false) }
                    var sdkAvailable by remember { mutableStateOf(availabilityStatus == HealthConnectClient.SDK_AVAILABLE) }
                    var sdkError by remember { mutableStateOf<String?>(null) }
                    val coroutineScope = rememberCoroutineScope()

                    if (availabilityStatus != HealthConnectClient.SDK_AVAILABLE) {
                        sdkError = "Health Connect SDK is not available. Status: $availabilityStatus"
                    }

                    // ActivityResultContracts must be registered unconditionally in the Composable
                    // before it reaches the STARTED state, and definitely not conditionally.
                    val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()
                    val requestPermissions = androidx.activity.compose.rememberLauncherForActivityResult(requestPermissionActivityContract) { granted ->
                        if (granted.containsAll(permissions)) {
                            hasPermissions = true
                            coroutineScope.launch {
                                heartRateData = readHeartRateData(healthConnectClient)
                                caloriesData = readCaloriesData(healthConnectClient)
                            }
                        }
                    }

                    LaunchedEffect(refreshTrigger, hasPermissions) {
                        if (sdkAvailable && !hasPermissions) {
                            val granted = healthConnectClient.permissionController.getGrantedPermissions()
                            if (granted.containsAll(permissions)) {
                                hasPermissions = true
                            }
                        }
                        if (hasPermissions) {
                            while(true) {
                                heartRateData = readHeartRateData(healthConnectClient)
                                caloriesData = readCaloriesData(healthConnectClient)
                                delay(5 * 60 * 1000L) // Wait 5 minutes
                            }
                        }
                    }

                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (sdkError != null) {
                            Text(text = sdkError!!)
                        } else if (!hasPermissions) {
                            Button(onClick = { requestPermissions.launch(permissions) }) {
                                Text("Grant Health Connect Permissions")
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Dynamically fetch versionName from the package manager
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val packageInfo = remember {
                                    try {
                                        context.packageManager.getPackageInfo(context.packageName, 0)
                                    } catch (e: PackageManager.NameNotFoundException) {
                                        null
                                    }
                                }
                                val versionName = packageInfo?.versionName ?: "Unknown"

                                Text(
                                    text = "v$versionName",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                                    color = Color.Gray
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Heart Rate (Last 24 Hours)",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.Checkbox(
                                                checked = showCalories,
                                                onCheckedChange = { showCalories = it },
                                            )
                                            Text(text = "Show Calories", fontSize = 12.sp)
                                        }
                                    }
                                    Button(
                                        onClick = { 
                                            refreshTrigger++
                                        },
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Refresh", fontSize = 12.sp)
                                    }
                                }
                                HeartRateGraph(
                                    heartRateData = heartRateData,
                                    caloriesData = caloriesData,
                                    showCalories = showCalories,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f) // Takes up remaining space instead of fixed 300.dp
                                        .padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun readHeartRateData(client: HealthConnectClient): List<HeartRateRecord> {
        val zoneId = ZoneId.systemDefault()
        // Get current time in local timezone
        val nowZoned = ZonedDateTime.now(zoneId)
        
        val startTimeZoned = nowZoned.minusHours(24)
        
        // Convert to absolute Instant for querying Health Connect
        val endTime = nowZoned.toInstant()
        val startTime = startTimeZoned.toInstant()

        return try {
            val allRecords = mutableListOf<HeartRateRecord>()
            var pageToken: String? = null
            
            do {
                val readResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                        pageToken = pageToken
                    )
                )
                allRecords.addAll(readResponse.records)
                pageToken = readResponse.pageToken
            } while (pageToken != null)
            
            val allSamples = allRecords.flatMap { it.samples }
            if (allSamples.isNotEmpty()) {
                val latestSampleTime = allSamples.maxOf { it.time }
                val zoneIdLocal = ZoneId.systemDefault()
                val latestLocal = ZonedDateTime.ofInstant(latestSampleTime, zoneIdLocal)
                val currentLocal = ZonedDateTime.ofInstant(endTime, zoneIdLocal)
                Log.d("HeartRateData", "Read ${allSamples.size} samples via pagination. Latest sample time (local): $latestLocal, Current time (local): $currentLocal")
            } else {
                Log.d("HeartRateData", "No samples found between $startTime and $endTime")
            }
            allRecords
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun readCaloriesData(client: HealthConnectClient): List<androidx.health.connect.client.records.TotalCaloriesBurnedRecord> {
        val zoneId = ZoneId.systemDefault()
        // Get current time in local timezone
        val nowZoned = ZonedDateTime.now(zoneId)
        val startTimeZoned = nowZoned.minusHours(24)
        
        val endTime = nowZoned.toInstant()
        val startTime = startTimeZoned.toInstant()

        return try {
            val allRecords = mutableListOf<androidx.health.connect.client.records.TotalCaloriesBurnedRecord>()
            var pageToken: String? = null
            
            do {
                val readResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                        pageToken = pageToken
                    )
                )
                allRecords.addAll(readResponse.records)
                pageToken = readResponse.pageToken
            } while (pageToken != null)
            
            allRecords
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

@Composable
fun HeartRateGraph(
    heartRateData: List<HeartRateRecord>,
    caloriesData: List<androidx.health.connect.client.records.TotalCaloriesBurnedRecord>,
    showCalories: Boolean,
    modifier: Modifier = Modifier
) {
    if (heartRateData.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No heart rate data available for the last 24 hours.")
        }
        return
    }

    // Flatten all samples into a single list
    val allSamples = heartRateData.flatMap { it.samples }
    if (allSamples.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
             Text("No heart rate data available for the last 24 hours.")
        }
        return
    }
    
    // Fix: Keep it dead simple. The bounds are the EXACT timestamps from HealthConnect bounds.
    val absoluteEndTime = Instant.now().toEpochMilli()
    val absoluteStartTime = absoluteEndTime - (24 * 60 * 60 * 1000L)

    var customWindow by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    
    // Default the graph window to the rolling 24 hour view
    val visibleStartTime = customWindow?.first ?: absoluteStartTime
    val visibleEndTime = customWindow?.second ?: absoluteEndTime
    // Calculate visible time range
    val visibleTimeRange = visibleEndTime - visibleStartTime

    val visibleStartTimeState = rememberUpdatedState(visibleStartTime)
    val visibleEndTimeState = rememberUpdatedState(visibleEndTime)
    val absoluteEndTimeState = rememberUpdatedState(absoluteEndTime)
    val absoluteStartTimeState = rememberUpdatedState(absoluteStartTime)
    
    // Find the GLOBAL min and max BPM for the entire 24-hour period (not just visible)
    val globalMinBpm = if (allSamples.isNotEmpty()) allSamples.minOf { it.beatsPerMinute }.toFloat() else 40f
    val globalMaxBpm = if (allSamples.isNotEmpty()) allSamples.maxOf { it.beatsPerMinute }.toFloat() else 150f
    
    // Filter the samples down to ONLY what is currently visible on the screen
    // so we can mathematically find the lowest and highest heart rate *in view*.
    val currentlyVisibleSamples = allSamples.filter {
        it.time.toEpochMilli() in (visibleStartTime)..(visibleEndTime)
    }
    
    // Dynamic Y-axis logic - THIS is what was causing the disconnect.
    // The Y-axis (and yMin/yMax) currently changes dynamically based on what is visible.
    val activeMinBpm = if (currentlyVisibleSamples.isNotEmpty()) currentlyVisibleSamples.minOf { it.beatsPerMinute }.toFloat() else globalMinBpm
    val activeMaxBpm = if (currentlyVisibleSamples.isNotEmpty()) currentlyVisibleSamples.maxOf { it.beatsPerMinute }.toFloat() else globalMaxBpm
    
    val yMin = (activeMinBpm - 5).coerceAtLeast(0f)
    val yMax = activeMaxBpm + 5

    // Scrubbing state
    var scrubbingPositionX by remember { mutableStateOf<Float?>(null) }
    var scrubbedSample by remember { mutableStateOf<Pair<Long, Float>?>(null) }

    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // Dismiss scrubber on normal tap
                        scrubbingPositionX = null
                        scrubbedSample = null
                    },
                    onLongPress = { offset ->
                        // Activate scrubber
                        scrubbingPositionX = offset.x
                    }
                )
            }
            .pointerInput(scrubbingPositionX != null) {
                if (scrubbingPositionX != null) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // If scrubber is active AND the drag starts reasonably close to it, start dragging the scrubber
                            val distance = Math.abs(scrubbingPositionX!! - offset.x)
                            if (distance < size.width * 0.15f) { // Allow grabbing within 15% of screen width
                                scrubbingPositionX = offset.x
                            } else {
                                // If they dragged far away from the active scrubber, cancel the scrubber
                                // so the transform gesture can take over panning
                                scrubbingPositionX = null
                                scrubbedSample = null
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (scrubbingPositionX != null) {
                                change.consume() // Consume the gesture so it doesn't trigger pan
                                
                                val newX = (scrubbingPositionX!! + dragAmount.x).coerceIn(0f, size.width.toFloat())
                                scrubbingPositionX = newX
                                
                                // Auto-panning logic!
                                val edgeThreshold = size.width * 0.05f // 5% of screen width
                                
                                val currentVStart = visibleStartTimeState.value
                                val currentVEnd = visibleEndTimeState.value
                                val currentVRange = currentVEnd - currentVStart
                                val absStart = absoluteStartTimeState.value
                                val absEnd = absoluteEndTimeState.value
                                
                                if (newX < edgeThreshold) {
                                    // Pan left (go back in time)
                                    val panSpeedMs = currentVRange * 0.05f // 5% of visible time per frame
                                    val newVStart = (currentVStart - panSpeedMs).toLong().coerceAtLeast(absStart)
                                    val newVEnd = newVStart + currentVRange
                                    customWindow = Pair(newVStart, newVEnd)
                                    // Keep scrubber visually snapped to edge
                                    scrubbingPositionX = edgeThreshold
                                } else if (newX > size.width - edgeThreshold) {
                                    // Pan right (go forward in time)
                                    val panSpeedMs = currentVRange * 0.05f // 5% of visible time per frame
                                    val newVEnd = (currentVEnd + panSpeedMs).toLong().coerceAtMost(absEnd)
                                    val newVStart = newVEnd - currentVRange
                                    customWindow = Pair(newVStart, newVEnd)
                                    // Keep scrubber visually snapped to edge
                                    scrubbingPositionX = size.width - edgeThreshold
                                }
                            }
                        },
                        onDragEnd = {
                            // Keep scrubber active when letting go, until a clean tap clears it
                        },
                        onDragCancel = {
                        }
                    )
                } else {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val currentVStart = visibleStartTimeState.value
                        val currentVEnd = visibleEndTimeState.value
                        val absStart = absoluteStartTimeState.value
                        val absEnd = absoluteEndTimeState.value
                        
                        val oldRange = currentVEnd - currentVStart
                        val newRange = (oldRange / zoom).toLong().coerceIn(60 * 60 * 1000L, 24 * 60 * 60 * 1000L)
                        
                        val fraction = centroid.x / size.width
                        val centroidTime = currentVStart + (oldRange * fraction).toLong()
                        
                        val panTime = (pan.x / size.width) * newRange
                        
                        var newStartTime = centroidTime - (newRange * fraction).toLong() - panTime.toLong()
                        var newEndTime = newStartTime + newRange
                        
                        if (newEndTime > absEnd) {
                            newEndTime = absEnd
                            newStartTime = newEndTime - newRange
                        }
                        if (newStartTime < absStart) {
                            newStartTime = absStart
                            newEndTime = newStartTime + newRange
                        }
                        
                        if (newRange >= 24 * 60 * 60 * 1000L - 1000L && newEndTime >= absEnd - 1000L) {
                            customWindow = null
                        } else {
                            customWindow = Pair(newStartTime, newEndTime)
                        }
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height

        // Calculate grid intervals dynamically based on visible range (needed for both calories and grid)
        val gridIntervalMs = when {
            visibleTimeRange <= 2 * 60 * 60 * 1000L -> 15 * 60 * 1000L // <= 2 hours: 15 min grid
            visibleTimeRange <= 6 * 60 * 60 * 1000L -> 30 * 60 * 1000L // <= 6 hours: 30 min grid
            visibleTimeRange <= 12 * 60 * 60 * 1000L -> 60 * 60 * 1000L // <= 12 hours: 1 hour grid
            else -> 2 * 60 * 60 * 1000L // > 12 hours: 2 hour grid
        }
        
        // --- CALORIES BUCKETING & DRAWING LOGIC ---
        if (showCalories && caloriesData.isNotEmpty()) {
            // FIXED bucket width: 30 minutes (30 * 60 * 1000L). 
            // If we tie bucket width to zoom level, the buckets get smaller as we zoom in, 
            // which means less calories per bucket, causing the bars to dynamically change their meaning
            // and the "Max burned" calculation to drastically change as you zoom!
            val bucketWidthMs = 30 * 60 * 1000L 
            
            // Find what buckets actually intersect our visible window
            // We don't need firstVisibleBucketStart anymore since we filter more accurately below
            
            // We need the global max cals from the FULL 24-HOUR DATASET, not just the buckets we calculated for the current "relevantCalories"
            // The issue is that relevantCalories only looks at the currently visible window (+2 hrs padding).
            // So if you zoom in, "buckets" only contains local data, meaning globalMaxCals isn't actually global!
            
            // Let's recalculate the TRUE global max by bucketing ALL caloriesData for the last 24 hours.
            val globalBuckets = mutableMapOf<Long, Double>()
            for (record in caloriesData) {
                val recordStartMs = record.startTime.toEpochMilli()
                val recordEndMs = record.endTime.toEpochMilli()
                val recordDuration = recordEndMs - recordStartMs
                if (recordDuration <= 0) continue
                
                val cals = record.energy.inKilocalories
                val calsPerMs = cals / recordDuration

                var currentMs = recordStartMs
                while (currentMs < recordEndMs) {
                    val bucketStart = currentMs - (currentMs % bucketWidthMs)
                    val bucketEnd = bucketStart + bucketWidthMs
                    val nextStep = minOf(recordEndMs, bucketEnd)
                    val calsInBucket = (nextStep - currentMs) * calsPerMs
                    globalBuckets[bucketStart] = (globalBuckets[bucketStart] ?: 0.0) + calsInBucket
                    currentMs = nextStep
                }
            }
            
            val globalMaxCals = globalBuckets.values.maxOrNull() ?: 1.0
            val maxCalsToUse = globalMaxCals.coerceAtLeast(1.0)
            

            val visibleBuckets = globalBuckets.filterKeys { 
                val bucketEnd = it + bucketWidthMs
                bucketEnd > visibleStartTime && it < visibleEndTime 
            }

            clipRect(left = 0f, top = 0f, right = width, bottom = height) {
                val barWidth = (bucketWidthMs.toFloat() / visibleTimeRange.toFloat()) * width
                
                for ((bucketTime, cals) in visibleBuckets) {
                    if (cals <= 0.1) continue // Skip essentially empty buckets
                    
                    val fraction = (cals / maxCalsToUse).coerceIn(0.0, 1.0)
                    
                    // Soft blue (low) to Orange/Red (high)
                    val color = Color(
                        red = (0.3f + (0.7f * fraction.toFloat())),
                        green = (0.5f + (0.5f * (1f - fraction.toFloat()))),
                        blue = (1.0f - fraction.toFloat()).coerceAtLeast(0.1f),
                        alpha = 0.15f + (0.15f * fraction.toFloat())
                    )
                    
                    // x is calculated from the bucketTime relative to visibleStartTime.
                    val x = ((bucketTime - visibleStartTime).toFloat() / visibleTimeRange.toFloat()) * width
                    
                    // Map the calorie fraction to the GLOBAL BPM bounds.
                    // This means 0 calories = global minimum BPM
                    // Max calories = global maximum BPM
                    val calorieBaseBpm = (globalMinBpm - 5f).coerceAtLeast(0f)
                    val calorieTopBpm = globalMaxBpm + 5f
                    val equivalentBpm = (calorieBaseBpm + (fraction * (calorieTopBpm - calorieBaseBpm))).toFloat()
                    
                    // Calculate the Y position using the EXACT SAME dynamically changing yMin/yMax
                    // that the heart rate line uses. This perfectly locks the visual relationship!
                    val activeBpmRange = yMax - yMin
                    val barTop = height - (((equivalentBpm - yMin) / activeBpmRange) * height)
                    
                    val barBottom = height
                    val actualBarHeight = (barBottom - barTop).coerceAtLeast(0f)
                    
                    drawRect(
                        color = color,
                        topLeft = Offset(x, barTop),
                        size = Size(barWidth - 1f, actualBarHeight)
                    )
                }
            }
            
            // Calculate total calories burned in the currently visible window
            val totalCalsInView = visibleBuckets.values.sum()
            
            // Draw max calorie label with a background for better visibility
            val labelText = "Max burned (global): ${String.format(java.util.Locale.getDefault(), "%.1f", maxCalsToUse)} kcal / ${bucketWidthMs / 60000}m\n" +
                            "Total visible: ${String.format(java.util.Locale.getDefault(), "%.1f", totalCalsInView)} kcal"
            val textLayoutResult = textMeasurer.measure(
                text = labelText,
                style = TextStyle(color = Color.White, fontSize = 14.sp)
            )
            val padding = 6.dp.toPx()
            
            drawRoundRect(
                color = Color(0xBB000000), // Semi-transparent black background
                topLeft = Offset(10f, 10f),
                size = Size(textLayoutResult.size.width + padding * 2, textLayoutResult.size.height + padding * 2),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(10f + padding, 10f + padding)
            )
        }
        
        // --- DRAW HEART RATE METRICS ---
        // Instead of looking at raw currentlyVisibleSamples (which contains dense data points), 
        // we should look at the "thinnedSamples" which are the actual data points being rendered
        // on the graph, so the text matches the visual lines!
        // We calculate thinnedSamples below, so we'll just hoist the calculation up here:
        
        // Ensure samples are strictly sorted by time
        val sortedSamples = allSamples.sortedBy { it.time }

        // Filter the samples: we will group them into buckets dynamically based on zoom level.
        val bucketSizeMs = when {
            visibleTimeRange > 12 * 60 * 60 * 1000L -> 10 * 60 * 1000L // >12hr view: 10 min average
            visibleTimeRange > 6 * 60 * 60 * 1000L -> 5 * 60 * 1000L // 6-12hr view: 5 min average
            else -> 3 * 60 * 1000L // <6hr view: 3 min average
        }
        
        val thinnedSamples = run {
            val bucketedSamples = sortedSamples.groupBy { 
                it.time.toEpochMilli() / bucketSizeMs 
            }
            bucketedSamples.map { (bucketIndex, samplesInBucket) ->
                val bucketTime = bucketIndex * bucketSizeMs
                val avgBpm = samplesInBucket.map { it.beatsPerMinute }.average().toFloat()
                Pair(bucketTime, avgBpm)
            }
        }
        
        val visibleThinnedSamples = thinnedSamples.filter {
            it.first in visibleStartTime..visibleEndTime
        }
        
        if (visibleThinnedSamples.isNotEmpty()) {
            val avgBpm = visibleThinnedSamples.map { it.second }.average()
            val maxBpm = visibleThinnedSamples.maxOf { it.second }
            val minBpm = visibleThinnedSamples.minOf { it.second }
            
            val hrLabelText = "Avg BPM: ${avgBpm.toInt()}\n" +
                              "Min/Max: ${minBpm.toInt()} / ${maxBpm.toInt()}"
            val hrTextLayoutResult = textMeasurer.measure(
                text = hrLabelText,
                style = TextStyle(color = Color.White, fontSize = 14.sp)
            )
            val padding = 6.dp.toPx()
            
            // If calories text is showing, we need to shift this text over to the right
            val xOffset = if (showCalories && caloriesData.isNotEmpty()) {
                val calLabelText = "Max burned: 99.9 kcal / 99m\nTotal visible: 999.9 kcal" // Estimate width to avoid recalculating
                val estCalTextLayout = textMeasurer.measure(text = calLabelText, style = TextStyle(fontSize = 14.sp))
                10f + estCalTextLayout.size.width + (padding * 4) // X padding + cal width + extra padding
            } else {
                10f
            }
            
            drawRoundRect(
                color = Color(0xBB000000), // Semi-transparent black background
                topLeft = Offset(xOffset, 10f),
                size = Size(hrTextLayoutResult.size.width + padding * 2, hrTextLayoutResult.size.height + padding * 2),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            
            drawText(
                textLayoutResult = hrTextLayoutResult,
                topLeft = Offset(xOffset + padding, 10f + padding)
            )
        }
        
        // --- END CALORIES LOGIC ---


        val bpmRange = yMax - yMin
        
        // Avoid division by zero
        if (visibleTimeRange <= 0 || bpmRange <= 0) return@Canvas
        
        // Draw grid lines BEFORE drawing the red data line so the red line sits on top!
        // Dynamic Y-axis grid lines (every 10 bpm, or every 5 bpm if zoom is tight)
        val yInterval = if (bpmRange <= 50f) 5 else 10 // If screen shows 50 bpm or less, use 5 bpm gridlines
        val firstYLabel = (yMin / yInterval).toInt() * yInterval
        var currentYLabel = firstYLabel
        
        while (currentYLabel <= yMax) {
            if (currentYLabel >= yMin) {
                val y = height - (((currentYLabel.toFloat() - yMin) / bpmRange) * height)
                if (y in 0f..height) {
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = currentYLabel.toString(),
                        topLeft = Offset(0f, y - 14.dp.toPx()),
                        style = TextStyle(color = Color.Gray, fontSize = 10.sp)
                    )
                }
            }
            currentYLabel += yInterval
        }
        
        // Draw X-axis grid lines based on zoom level
        // (gridIntervalMs was already calculated above)

        val zoneId = ZoneId.systemDefault()
        val visibleStartZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(visibleStartTime), zoneId)
        
        var currentTimeLine = visibleStartZoned.truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli()
        while (currentTimeLine < visibleStartTime) {
            currentTimeLine += gridIntervalMs
        }
        
        while (currentTimeLine <= visibleEndTime) {
            val x = ((currentTimeLine - visibleStartTime).toFloat() / visibleTimeRange.toFloat()) * width
            
            val zoneIdInstance = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTimeLine), zoneId)
            
            // Check if this timeline falls exactly on the hour (minute == 0)
            val isTopOfHour = zoneIdInstance.minute == 0
            
            // All lines are thin, but sub-hour lines are dashed!
            // First float is line length, second float is blank space length
            val pathEffect = if (!isTopOfHour) PathEffect.dashPathEffect(floatArrayOf(10f, 20f), 0f) else null
            
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = pathEffect
            )
            
            val formatter = if (gridIntervalMs >= 60 * 60 * 1000L) DateTimeFormatter.ofPattern("HH") else DateTimeFormatter.ofPattern("HH:mm")
            
            // Only draw the label if we are at a 2hr, 1hr, or 15min zoom level.
            // When we are at the 30min zoom level, the text is too crowded, so we only 
            // draw the label for the top of the hour (HH:00) and skip the half-hours (HH:30).
            val timeString = formatter.format(zoneIdInstance)
            
            val shouldDrawLabel = gridIntervalMs != 30 * 60 * 1000L || isTopOfHour
            
            if (shouldDrawLabel) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = timeString,
                    topLeft = Offset(x + 2.dp.toPx(), height - 20.dp.toPx()),
                    style = TextStyle(color = Color.Gray, fontSize = 14.sp)
                )
            }
            
            currentTimeLine += gridIntervalMs
        }

        val path = Path()
        
        var previousTime: Long? = null
        var isFirstPointInSegment = true
        var isDataMissing = false
        val missingDataPath = Path()

        thinnedSamples.forEachIndexed { index, thinnedSample ->
            val correctedTime = thinnedSample.first
            val avgBpm = thinnedSample.second
            
            // Only draw lines for data points that are actually on or near the screen bounds
            if (correctedTime in (visibleStartTime - (2 * 60 * 60 * 1000L))..(visibleEndTime + (2 * 60 * 60 * 1000L))) {
                
                val timeFraction = (correctedTime - visibleStartTime).toDouble() / visibleTimeRange.toDouble()
                val x = (timeFraction * width).toFloat()
                
                val y = height - (((avgBpm - yMin) / bpmRange) * height)
    
                if (previousTime != null && (correctedTime - previousTime!!) > 30 * 60 * 1000L) {
                    // Gap is larger than 30 minutes! Break the line by flagging the next point as the start of a new segment.
                    isFirstPointInSegment = true
                    isDataMissing = true
                    
                    // Draw a dashed straight line from the last known point to this newly discovered point!
                    val prevFraction = (previousTime!! - visibleStartTime).toDouble() / visibleTimeRange.toDouble()
                    val prevX = (prevFraction * width).toFloat()
                    val prevSample = thinnedSamples[index - 1]
                    val prevY = height - (((prevSample.second - yMin) / bpmRange) * height)
                    
                    missingDataPath.moveTo(prevX, prevY)
                    missingDataPath.lineTo(x, y)
                }
                
                if (isFirstPointInSegment) { 
                    path.moveTo(x, y)
                    isFirstPointInSegment = false
                } else {
                    // Optional: Smooth the line by using a quadratic bezier curve instead of a hard lineTo
                    // For simplicity and performance, we'll keep lineTo but use rounded corners/caps on the stroke.
                    path.lineTo(x, y)
                }
                
                previousTime = correctedTime
            }
        }

        clipRect {
            if (isDataMissing) {
                drawPath(
                    path = missingDataPath,
                    color = Color.Red.copy(alpha = 0.5f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    )
                )
            }
            
            drawPath(
                path = path,
                color = Color.Red,
                style = Stroke(
                    width = 3.dp.toPx(), // Slightly thicker line
                    cap = StrokeCap.Round, // Round off the ends of broken segments
                    join = StrokeJoin.Round // Smooth out the jagged spikes where lines meet
                )
            )
            
            // --- DRAW SCRUBBER ---
            if (scrubbingPositionX != null) {
                val scrubberX = scrubbingPositionX!!
                
                // 1. Calculate what timestamp the user's finger is pointing at
                val fraction = scrubberX / width
                val targetTime = visibleStartTime + (visibleTimeRange * fraction).toLong()
                
                // 2. Find the closest thinnedSample to that timestamp
                val closestSample = thinnedSamples.minByOrNull { Math.abs(it.first - targetTime) }
                
                if (closestSample != null) {
                    val sampleTime = closestSample.first
                    val sampleBpm = closestSample.second
                    
                    // Update state to render the text box (this is safe as it's just state mutation)
                    scrubbedSample = closestSample
                    
                    // 3. Calculate exact X and Y of the closest sample
                    val sampleX = ((sampleTime - visibleStartTime).toDouble() / visibleTimeRange.toDouble() * width).toFloat()
                    val sampleY = height - (((sampleBpm - yMin) / bpmRange) * height)
                    
                    // 4. Draw Dashed Vertical Line
                    drawLine(
                        color = Color.White,
                        start = Offset(sampleX, 0f),
                        end = Offset(sampleX, height),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    
                    // 5. Draw Solid Dot on the Data Line
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = Offset(sampleX, sampleY)
                    )
                    drawCircle(
                        color = Color.Red,
                        radius = 4.dp.toPx(),
                        center = Offset(sampleX, sampleY)
                    )
                    
                    // 6. Draw Tooltip Box
                    val formatter = DateTimeFormatter.ofPattern("HH:mm")
                    val timeString = formatter.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(sampleTime), zoneId))
                    val bpmString = "${sampleBpm.toInt()} bpm"
                    
                    val textLayoutResult = textMeasurer.measure(
                        text = "$timeString\n$bpmString",
                        style = TextStyle(color = Color.White, fontSize = 12.sp)
                    )
                    
                    val padding = 8.dp.toPx()
                    val boxWidth = textLayoutResult.size.width + (padding * 2)
                    val boxHeight = textLayoutResult.size.height + (padding * 2)
                    
                    // Keep tooltip on screen
                    var boxX = sampleX + 16.dp.toPx()
                    if (boxX + boxWidth > width) {
                        boxX = sampleX - boxWidth - 16.dp.toPx()
                    }
                    
                    // Draw Box Background
                    drawRoundRect(
                        color = Color(0xBB000000), // Semi-transparent black
                        topLeft = Offset(boxX, 16.dp.toPx()),
                        size = Size(boxWidth, boxHeight),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                    
                    // Draw Text
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(boxX + padding, 16.dp.toPx() + padding)
                    )
                }
            }
        }
    }
}
