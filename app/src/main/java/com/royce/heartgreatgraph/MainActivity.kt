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
import androidx.compose.material3.Checkbox
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
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
        HealthPermission.getReadPermission(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class)
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
                    var distanceData by remember { mutableStateOf<List<DistanceRecord>>(emptyList()) }
                    var showCalories by remember { mutableStateOf(false) }
                    var showDistance by remember { mutableStateOf(false) }
                    var showStats by remember { mutableStateOf(false) }
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
                                distanceData = readDistanceData(healthConnectClient)
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
                                distanceData = readDistanceData(healthConnectClient)
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
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = showCalories,
                                                    onCheckedChange = { showCalories = it },
                                                )
                                                Text(text = "Show Calories", fontSize = 12.sp)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = showDistance,
                                                    onCheckedChange = { showDistance = it },
                                                )
                                                Text(text = "Show Distance", fontSize = 12.sp)
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = showStats,
                                                onCheckedChange = { showStats = it },
                                            )
                                            Text(text = "HR Min/Avg/Max", fontSize = 12.sp)
                                        }
                                    }
                                }
                                HeartRateGraph(
                                    heartRateData = heartRateData,
                                    caloriesData = caloriesData,
                                    distanceData = distanceData,
                                    showCalories = showCalories,
                                    showDistance = showDistance,
                                    showStats = showStats,
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

    private suspend fun readDistanceData(client: HealthConnectClient): List<DistanceRecord> {
        val zoneId = ZoneId.systemDefault()
        val nowZoned = ZonedDateTime.now(zoneId)
        val startTimeZoned = nowZoned.minusHours(24)
        
        val endTime = nowZoned.toInstant()
        val startTime = startTimeZoned.toInstant()

        // Use Health Connect's built-in Aggregation API
        // Health Connect will AUTOMATICALLY deduplicate data based on the user's priority settings!
        // This solves the issue of Google Fit + Samsung Health both recording the same walk.
        
        return try {
            val response = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    timeRangeSlicer = Duration.ofMinutes(15L) // Pre-aggregate into 15 min buckets
                )
            )
            
            val mappedRecords = mutableListOf<DistanceRecord>()
            
            // Map the aggregated buckets back into mock DistanceRecords so we don't have to rewrite the rendering logic
            for (durationResult in response) {
                val distance = durationResult.result[DistanceRecord.DISTANCE_TOTAL]
                if (distance != null && distance.inMeters > 0.0) {
                     val start = durationResult.startTime
                     val end = durationResult.endTime
                     
                     // We create a dummy DistanceRecord holding the total distance for this chunk
                     mappedRecords.add(
                         DistanceRecord(
                             startTime = start,
                             startZoneOffset = null,
                             endTime = end,
                             endZoneOffset = null,
                             distance = distance,
                             metadata = androidx.health.connect.client.records.metadata.Metadata.unknownRecordingMethod()
                         )
                     )
                }
            }
            
            mappedRecords
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
    distanceData: List<DistanceRecord>,
    showCalories: Boolean,
    showDistance: Boolean,
    showStats: Boolean,
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
    

    // Dynamic Y-axis logic - THIS is what was causing the disconnect.
    // The Y-axis (and yMin/yMax) currently changes dynamically based on what is visible.
    // For smooth drawing, base min/max on ALL thinned points that would be rendered, not just the original raw sample max/min.
    // Because thinning averages out spikes, the actual highest point *drawn* might be slightly lower than raw activeMaxBpm.
    // For the max line to be exactly on the peak, it needs to match the highest *drawn* peak.
    
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

    val activeMinBpm = if (visibleThinnedSamples.isNotEmpty()) visibleThinnedSamples.minOf { it.second } else globalMinBpm
    val activeMaxBpm = if (visibleThinnedSamples.isNotEmpty()) visibleThinnedSamples.maxOf { it.second } else globalMaxBpm
    
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
            
            // Draw max calorie label with a background for better visibility
            // (Label drawing moved to the end so it stays on top of everything)
        }
        
        // --- END METRICS LOGIC ---

        val bpmRange = yMax - yMin
        
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
                    width = 2.dp.toPx(), // Slightly thicker line
                    cap = StrokeCap.Round, // Round off the ends of broken segments
                    join = StrokeJoin.Round // Smooth out the jagged spikes where lines meet
                )
            )

            // --- DISTANCE BUBBLES DRAWN ON TOP OF RED LINE ---
            if (showDistance && distanceData.isNotEmpty()) {
                val bucketWidthMs = 15 * 60 * 1000L // 15 minute buckets for finer granularity
                
                val globalBuckets = mutableMapOf<Long, Double>()
                for (record in distanceData) {
                    // Only process records that overlap with our overall 24 hour view + a buffer
                    val windowStart = visibleStartTime - (2 * 60 * 60 * 1000L)
                    val windowEnd = visibleEndTime + (2 * 60 * 60 * 1000L)
                    
                    val recordStartMs = record.startTime.toEpochMilli()
                    val recordEndMs = record.endTime.toEpochMilli()
                    
                    if (recordEndMs < windowStart || recordStartMs > windowEnd) continue
                    
                    val recordDuration = recordEndMs - recordStartMs
                    if (recordDuration <= 0) continue
                    
                    val distanceMeters = record.distance.inMeters
                    val distancePerMs = distanceMeters / recordDuration

                    var currentMs = recordStartMs
                    while (currentMs < recordEndMs) {
                        val bucketStart = currentMs - (currentMs % bucketWidthMs)
                        val bucketEnd = bucketStart + bucketWidthMs
                        val nextStep = minOf(recordEndMs, bucketEnd)
                        val distanceInBucket = (nextStep - currentMs) * distancePerMs
                        globalBuckets[bucketStart] = (globalBuckets[bucketStart] ?: 0.0) + distanceInBucket
                        currentMs = nextStep
                    }
                }
                
                val globalMaxDistance = globalBuckets.values.maxOrNull() ?: 1.0
                
                // Track distinct packages to correct for multiple overlapping distance sources
                val dataOrigins = mutableSetOf<String>()
                for (record in distanceData) {
                    val recordStartMs = record.startTime.toEpochMilli()
                    val recordEndMs = record.endTime.toEpochMilli()
                    if (recordEndMs > visibleStartTime && recordStartMs < visibleEndTime) {
                        dataOrigins.add(record.metadata.dataOrigin.packageName)
                    }
                }
                
                var maxDistanceToUse = globalMaxDistance.coerceAtLeast(1.0)
                if (dataOrigins.size > 1) {
                    maxDistanceToUse /= dataOrigins.size.toDouble()
                }
                
                val visibleBuckets = globalBuckets.filterKeys { 
                    val bucketEnd = it + bucketWidthMs
                    bucketEnd > visibleStartTime && it < visibleEndTime 
                }

                if (visibleBuckets.isNotEmpty()) {
                    val baselineY = height - (height * 0.15f) // Position the baseline slightly above the very bottom.
                    
                    val maxRadius = 10.dp.toPx()
                    val minRadius = 3.dp.toPx()
                    
                    for ((bucketTime, distance) in visibleBuckets) {
                        // Apply the same heuristic fix for the drawn bubbles so they don't over-scale
                        var scaledDistance = distance
                        if (dataOrigins.size > 1) {
                             scaledDistance /= dataOrigins.size.toDouble()
                        }
                        
                        if (scaledDistance <= 0.1) continue
                        
                        val fraction = (scaledDistance.toFloat() / maxDistanceToUse.toFloat()).coerceIn(0f, 1f)
                        
                        // We draw a circle at the center of the time bucket.
                        val bucketCenterTime = bucketTime + (bucketWidthMs / 2)
                        val cx = ((bucketCenterTime - visibleStartTime).toFloat() / visibleTimeRange.toFloat()) * width
                        
                        val steppedFraction = when {
                            fraction < 0.1f -> 0.1f
                            fraction < 0.3f -> 0.3f
                            fraction < 0.5f -> 0.5f
                            fraction < 0.7f -> 0.7f
                            fraction < 0.9f -> 0.9f
                            else -> 1.0f
                        }
                        
                        // Modify transparency based on zoom.
                        val baseAlpha = 0.25f + (0.35f * steppedFraction) 
                        val zoomFactor = (24 * 60 * 60 * 1000L).toFloat() / visibleTimeRange.toFloat()
                        val zoomAlphaModifier = 1f + ((zoomFactor - 1f) / 11f) 
                        val dynamicAlpha = (baseAlpha * zoomAlphaModifier).coerceAtMost(0.9f)
                        
                        val zoomMultiplier = Math.sqrt(zoomFactor.toDouble()).toFloat()
                        val dynamicRadius = minRadius * (1f + (zoomMultiplier * 0.05f)) + (steppedFraction * (maxRadius - minRadius) * zoomMultiplier).coerceAtMost(maxRadius * 2.5f)

                        val color = Color(0xFF81C784).copy(alpha = dynamicAlpha) // Green bubbles
                        
                        drawCircle(
                            color = color,
                            radius = dynamicRadius,
                            center = Offset(cx, baselineY)
                        )
                    }
                    
                    // Draw a faint reference line for the baseline
                    drawLine(
                        color = Color(0xFF81C784).copy(alpha = 0.3f),
                        start = Offset(0f, baselineY),
                        end = Offset(width, baselineY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
            }


            if (showStats) {
                // Min, Avg, Max Lines
                val minLineY = height - (((activeMinBpm - yMin) / bpmRange) * height)
                val maxLineY = height - (((activeMaxBpm - yMin) / bpmRange) * height)

                // Calculate active average
                var activeAvgBpm = 0f
                if (visibleThinnedSamples.isNotEmpty()) {
                    activeAvgBpm = visibleThinnedSamples.map { it.second }.average().toFloat()
                } else if (thinnedSamples.isNotEmpty()) {
                    activeAvgBpm = thinnedSamples.map { it.second }.average().toFloat()
                }

                if (activeAvgBpm > 0f) {
                     val avgLineY = height - (((activeAvgBpm - yMin) / bpmRange) * height)

                     drawLine(
                         color = Color(0xFF64B5F6), // Lighter Blue
                         start = Offset(0f, minLineY),
                         end = Offset(width, minLineY),
                         strokeWidth = 2.dp.toPx(),
                         pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                     )

                     drawLine(
                         color = Color.Gray,
                         start = Offset(0f, avgLineY),
                         end = Offset(width, avgLineY),
                         strokeWidth = 2.dp.toPx(),
                         pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                     )

                     drawLine(
                         color = Color.Unspecified, // fallback
                         start = Offset(0f, maxLineY),
                         end = Offset(width, maxLineY),
                         strokeWidth = 2.dp.toPx(),
                         pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                     )
                     
                     // Draw actual orange for Max Line
                     drawLine(
                         color = Color(0xFFFFA500), // Orange
                         start = Offset(0f, maxLineY),
                         end = Offset(width, maxLineY),
                         strokeWidth = 2.dp.toPx(),
                         pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                     )
                }
            }
            
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

            // --- DRAW METRICS BOXES LAST SO THEY STAY ON TOP OF EVERYTHING ---
            var currentLabelY = 10f
            
            if (showCalories && caloriesData.isNotEmpty()) {
                val bucketWidthMs = 30 * 60 * 1000L 
                
                val globalBuckets = mutableMapOf<Long, Double>()
                for (record in caloriesData) {
                    val recordStartMs = record.startTime.toEpochMilli()
                    val recordEndMs = record.endTime.toEpochMilli()
                    val recordDuration = recordEndMs - recordStartMs
                    if (recordDuration <= 0) continue
                    
                    val calsKcal = record.energy.inKilocalories
                    val calsPerMs = calsKcal / recordDuration
    
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
                
                val totalCalsInView = visibleBuckets.values.sum()
                
                val labelText = "Max burned (global): ${String.format(java.util.Locale.getDefault(), "%.1f", maxCalsToUse)} kcal / ${bucketWidthMs / 60000}m\n" +
                                "Total visible: ${String.format(java.util.Locale.getDefault(), "%.1f", totalCalsInView)} kcal"
                val textLayoutResult = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(color = Color.White, fontSize = 14.sp)
                )
                val padding = 6.dp.toPx()
                
                drawRoundRect(
                    color = Color(0x66000000), // Semi-transparent black (was 0xBB000000)
                    topLeft = Offset(10f, currentLabelY),
                    size = Size(textLayoutResult.size.width + padding * 2, textLayoutResult.size.height + padding * 2),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(10f + padding, currentLabelY + padding)
                )
                
                currentLabelY += textLayoutResult.size.height + padding * 2 + 10f
            }
            
            if (showDistance && distanceData.isNotEmpty()) {
                val bucketWidthMs = 15 * 60 * 1000L 
                
                val globalBuckets = mutableMapOf<Long, Double>()
                for (record in distanceData) {
                    // Only process records that overlap with our overall 24 hour view + a buffer
                    val windowStart = visibleStartTime - (2 * 60 * 60 * 1000L)
                    val windowEnd = visibleEndTime + (2 * 60 * 60 * 1000L)
                    
                    val recordStartMs = record.startTime.toEpochMilli()
                    val recordEndMs = record.endTime.toEpochMilli()
                    
                    if (recordEndMs < windowStart || recordStartMs > windowEnd) continue
                    
                    val recordDuration = recordEndMs - recordStartMs
                    if (recordDuration <= 0) continue
                    
                    val distanceMeters = record.distance.inMeters
                    val distancePerMs = distanceMeters / recordDuration
    
                    var currentMs = recordStartMs
                    while (currentMs < recordEndMs) {
                        val bucketStart = currentMs - (currentMs % bucketWidthMs)
                        val bucketEnd = bucketStart + bucketWidthMs
                        val nextStep = minOf(recordEndMs, bucketEnd)
                        val distanceInBucket = (nextStep - currentMs) * distancePerMs
                        
                        // We only want to accumulate data for valid buckets where the user was actually moving
                        // The bug was appending to the bucket multiple times, let's just make sure we only do it once per record
                        
                        // BUT - the distance algorithm in Health Connect will return multiple records for the same time period 
                        // if the user has multiple apps writing to the store (ex: phone tracked steps, watch tracked steps).
                        // Therefore we need to be careful to not double count.  For now, we'll just sum all of them.
                        globalBuckets[bucketStart] = (globalBuckets[bucketStart] ?: 0.0) + distanceInBucket
                        currentMs = nextStep
                    }
                }
                
                val globalMaxDistance = globalBuckets.values.maxOrNull() ?: 1.0
                val maxDistanceToUse = globalMaxDistance.coerceAtLeast(1.0)
                
                val visibleBuckets = globalBuckets.filterKeys { 
                    val bucketEnd = it + bucketWidthMs
                    bucketEnd > visibleStartTime && it < visibleEndTime 
                }
                
                // IMPORTANT: Calculate the sum based strictly on the bounds of the visible screen!
                // The previous code summed the "visibleBuckets", but a bucket could start before the screen 
                // and extend into it, leading to slight over-counting, and it could contain overlapping duplicate records.
                // However, the massive 2x overcounting is likely due to duplicate data sources (e.g. phone + watch).
                // A true fix requires checking the `dataOrigin` of each record and de-duplicating.
                // For a quick fix without complex de-duping, we'll sum up the distance from all records that fall within
                // the visible bounds, proportional to how much of the record is visible.
                
                var totalDistanceInViewMeters = 0.0
                
                for (record in distanceData) {
                    val recordStartMs = record.startTime.toEpochMilli()
                    val recordEndMs = record.endTime.toEpochMilli()
                    
                    // Does this record overlap the visible screen at all?
                    if (recordEndMs > visibleStartTime && recordStartMs < visibleEndTime) {
                        val overlapStart = maxOf(recordStartMs, visibleStartTime)
                        val overlapEnd = minOf(recordEndMs, visibleEndTime)
                        val overlapDuration = overlapEnd - overlapStart
                        val recordDuration = recordEndMs - recordStartMs
                        
                        if (recordDuration > 0 && overlapDuration > 0) {
                             val proportion = overlapDuration.toDouble() / recordDuration.toDouble()
                             totalDistanceInViewMeters += (record.distance.inMeters * proportion)
                        }
                    }
                }
                
                // Because we are using the Aggregation API upstream, it has already handled deduplication.
                // We no longer need to manually attempt heuristic corrections based on data origins!
                
                val metersToMiles = { meters: Double -> meters * 0.000621371 }
                
                val maxMiles = metersToMiles(maxDistanceToUse)
                val totalMilesInView = metersToMiles(totalDistanceInViewMeters)
                
                val labelText = "Max distance (global): ${String.format(java.util.Locale.getDefault(), "%.2f", maxMiles)} mi / ${bucketWidthMs / 60000}m\n" +
                                "Total visible: ${String.format(java.util.Locale.getDefault(), "%.2f", totalMilesInView)} mi"
                val textLayoutResult = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(color = Color.White, fontSize = 14.sp)
                )
                val padding = 6.dp.toPx()
                
                drawRoundRect(
                    color = Color(0x66000000), // Semi-transparent black (was 0xBB000000)
                    topLeft = Offset(10f, currentLabelY),
                    size = Size(textLayoutResult.size.width + padding * 2, textLayoutResult.size.height + padding * 2),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(10f + padding, currentLabelY + padding)
                )
                
                currentLabelY += textLayoutResult.size.height + padding * 2 + 10f
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
                
                drawRoundRect(
                    color = Color(0x66000000), // Semi-transparent black (was 0xBB000000)
                    topLeft = Offset(10f, currentLabelY),
                    size = Size(hrTextLayoutResult.size.width + padding * 2, hrTextLayoutResult.size.height + padding * 2),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                
                drawText(
                    textLayoutResult = hrTextLayoutResult,
                    topLeft = Offset(10f + padding, currentLabelY + padding)
                )
            }
        }
    }
}
