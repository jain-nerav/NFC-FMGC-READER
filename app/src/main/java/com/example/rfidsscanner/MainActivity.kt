package com.example.rfidsscanner

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TapAndPlay
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rfidsscanner.ui.theme.RFIDSSCANNERTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class AppState {
    object WaitingForUser : AppState()
    object ScanningEnabled : AppState()
    data class ProductView(val product: Product) : AppState()
    data class ScanFailed(val message: String) : AppState()
}

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback, TextToSpeech.OnInitListener {

    private var nfcAdapter: NfcAdapter? = null
    private var tts: TextToSpeech? = null
    private var appState by mutableStateOf<AppState>(AppState.WaitingForUser)
    
    private val TAG_APP = "AccessibilityScanner"
    private val TAG_NFC = "NFCScanner"
    private val TAG_TTS = "TTS"
    private val TAG_HAPTICS = "Haptics"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG_APP, "App entered startup state")
        
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        tts = TextToSpeech(this, this)
        
        enableEdgeToEdge()
        setContent {
            RFIDSSCANNERTheme {
                AppContent(
                    state = appState,
                    onTap = { handleGlobalTap() },
                    onSwipeUp = { handleSwipeUp() }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            Log.d(TAG_TTS, "TTS initialized successfully")
            if (appState is AppState.WaitingForUser) {
                speak("Tap anywhere to enable scanning.")
            }
        } else {
            Log.e(TAG_TTS, "TTS initialization failed")
        }
    }

    private fun speak(text: String) {
        Log.d(TAG_TTS, "TTS prompt started: \"$text\"")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prompt")
    }

    private fun handleGlobalTap() {
        when (appState) {
            is AppState.WaitingForUser -> {
                Log.d(TAG_APP, "App entered scanning state")
                activateScanning()
            }
            is AppState.ProductView, is AppState.ScanFailed -> {
                Log.d(TAG_APP, "Returned to scanning state")
                activateScanning()
            }
            else -> {} // Already scanning
        }
    }

    private fun handleSwipeUp() {
        val currentState = appState
        if (currentState is AppState.ProductView) {
            Log.d(TAG_APP, "Additional details revealed via scroll")
            announceAdditionalDetails(currentState.product)
        }
    }

    private fun activateScanning() {
        tts?.stop()
        appState = AppState.ScanningEnabled
        Log.d(TAG_APP, "Scanning enabled")
        triggerActivationHaptic()
        speak("Scanning now. Hold a product near your device.")
        enableNfc()
    }

    private fun triggerActivationHaptic() {
        Log.d(TAG_HAPTICS, "Activation vibration triggered")
        vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun triggerSuccessHaptic() {
        Log.d(TAG_HAPTICS, "Success vibration triggered")
        val pattern = longArrayOf(0, 100, 50, 100)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
    }

    private fun triggerErrorHaptic() {
        Log.d(TAG_HAPTICS, "Error vibration triggered")
        val pattern = longArrayOf(0, 50, 50, 50, 50, 50)
        val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
        vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
    }

    private fun vibrate(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(effect)
        }
    }

    private fun enableNfc() {
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )
    }

    private fun disableNfc() {
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        Log.d(TAG_NFC, "Tag discovered")
        val startTime = System.currentTimeMillis()
        var isNdef = false
        val content = try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                isNdef = true
                ndef.connect()
                val msg = ndef.ndefMessage
                ndef.close()
                val payload = msg?.records?.firstOrNull()?.payload
                if (payload != null && payload.isNotEmpty() && payload[0] < 32) {
                    String(payload.sliceArray(3 until payload.size))
                } else if (payload != null) {
                    String(payload)
                } else null
            } else null
        } catch (e: Exception) { 
            Log.e(TAG_NFC, "Error reading tag: ${e.message}")
            null 
        }
        processPayload(content, tag != null, isNdef, startTime)
    }

    private fun processPayload(content: String?, tagDetected: Boolean, isNdef: Boolean, startTime: Long) {
        runOnUiThread {
            if (content != null) {
                val product = PayloadDecoder.decodePayload(content)
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG_NFC, "Total scan workflow completed in ${duration}ms")
                if (product != null) {
                    appState = AppState.ProductView(product)
                    triggerSuccessHaptic()
                    announceProductSummary(product)
                } else {
                    appState = AppState.ScanFailed("Invalid product data")
                    triggerErrorHaptic()
                    speak("Unable to read tag.")
                }
            } else if (tagDetected) {
                // TAG DETECTED BUT NO CONTENT
                val errorMsg = if (!isNdef) "Tag is not formatted for data" else "Tag is empty"
                Log.d(TAG_NFC, errorMsg)
                appState = AppState.ScanFailed(errorMsg)
                triggerErrorHaptic()
                speak(errorMsg)
            }
        }
    }

    private fun announceProductSummary(product: Product) {
        val summary = """
            Product found. ${product.brand}. ${product.product}.
            Category. ${product.category ?: "Not specified"}.
            ${if (product.allergens.isNotEmpty()) "Allergens: ${product.allergens.joinToString(", ")}. " else "No allergens. "}
            ${if (product.dietaryAttributes.isNotEmpty()) "Suitable for: ${product.dietaryAttributes.joinToString(", ")}. " else ""}
            ${when (product.expiryStatus) {
                ExpiryStatus.EXPIRED -> "Warning. This product is expired."
                ExpiryStatus.WARNING -> "Warning. This product is nearing expiry. It expires in ${product.daysRemaining} days."
                ExpiryStatus.SAFE -> "Product is safe. It expires in ${product.daysRemaining} days."
            }}
        """.trimIndent()
        speak(summary)
    }

    private fun announceAdditionalDetails(product: Product) {
        val nutritionLabels = listOf("Calories", "Protein", "Total Fat", "Carbohydrate", "Sugar", "Sodium")
        val units = listOf("k-cal", "grams", "grams", "grams", "grams", "milli-grams")
        val nutritionText = product.nutrition.mapIndexedNotNull { index, value ->
            if (index < nutritionLabels.size) "${nutritionLabels[index]}. $value ${units[index]}" else null
        }.joinToString(". ")
        val details = "Additional details. Nutrition facts. $nutritionText. Ingredients list. ${product.ingredients.joinToString(", ")}."
        speak(details)
    }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
    override fun onPause() { super.onPause(); disableNfc() }
    override fun onResume() { super.onResume(); if (appState is AppState.ScanningEnabled) enableNfc() }
}

@Composable
fun AppContent(state: AppState, onTap: () -> Unit, onSwipeUp: () -> Unit) {
    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).pointerInput(state) { detectTapGestures(onTap = { onTap() }) }) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
                label = "State"
            ) { targetState ->
                when (targetState) {
                    is AppState.WaitingForUser -> WaitingScreen()
                    is AppState.ScanningEnabled -> ScanningScreen()
                    is AppState.ProductView -> ProductScreen(targetState.product, onSwipeUpTriggered = onSwipeUp)
                    is AppState.ScanFailed -> ErrorScreen(targetState.message)
                }
            }
        }
    }
}

@Composable
fun WaitingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)).padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.TapAndPlay, null, Modifier.size(120.dp), MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.height(48.dp))
        Text("Tap anywhere to enable scanning.", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onErrorContainer, textAlign = TextAlign.Center)
    }
}

@Composable
fun ScanningScreen() {
    val infiniteTransition = rememberInfiniteTransition("Pulse")
    val pulseScale by infiniteTransition.animateFloat(0.8f, 1.2f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "Scale")
    val pulseAlpha by infiniteTransition.animateFloat(0.3f, 0.1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), "Alpha")
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)).padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(280.dp)) { drawCircle(Color(0xFF2E7D32), radius = size.minDimension / 2 * pulseScale, alpha = pulseAlpha, style = Stroke(8.dp.toPx())) }
            Surface(shape = CircleShape, color = Color(0xFF2E7D32), modifier = Modifier.size(140.dp), shadowElevation = 8.dp) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Nfc, null, Modifier.size(64.dp), Color.White) }
            }
        }
        Spacer(Modifier.height(64.dp))
        Text("Scanning now.", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF2E7D32))
        Text("Hold product near device", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun ProductScreen(product: Product, onSwipeUpTriggered: () -> Unit) {
    val scrollState = rememberScrollState()
    var hasAnnouncedExtra by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState.value) {
        if (scrollState.value > 250 && !hasAnnouncedExtra) {
            onSwipeUpTriggered()
            hasAnnouncedExtra = true
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp).verticalScroll(scrollState)
    ) {
        Spacer(Modifier.height(40.dp))
        HeaderSection(product)
        Spacer(Modifier.height(24.dp))
        SafetySection(product)
        Spacer(Modifier.height(24.dp))
        DietarySection(product.dietaryAttributes)
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(Modifier.padding(bottom = 24.dp), 1.dp, MaterialTheme.colorScheme.outlineVariant)
        Text("Additional Details", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        NutritionSection(product.nutrition)
        Spacer(Modifier.height(24.dp))
        IngredientsSection(product.ingredients)
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
fun HeaderSection(product: Product) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(24.dp)) {
            Text(product.brand.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(product.product, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            if (product.category != null) {
                Spacer(Modifier.height(12.dp))
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                    Text(product.category, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SafetySection(product: Product) {
    val expiryColor = when (product.expiryStatus) { ExpiryStatus.SAFE -> Color(0xFF2E7D32); ExpiryStatus.WARNING -> Color(0xFFF57F17); ExpiryStatus.EXPIRED -> MaterialTheme.colorScheme.error }
    val expiryContainerColor = when (product.expiryStatus) { ExpiryStatus.SAFE -> Color(0xFFE8F5E9); ExpiryStatus.WARNING -> Color(0xFFFFF9C4); ExpiryStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = expiryContainerColor)) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Event, null, Modifier.size(32.dp), expiryColor)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(if (product.expiryStatus == ExpiryStatus.EXPIRED) "EXPIRED" else "EXPIRY DATE", style = MaterialTheme.typography.labelSmall, color = expiryColor.copy(0.8f), fontWeight = FontWeight.Bold)
                    Text(if (product.expiryStatus == ExpiryStatus.EXPIRED) "Expired ${kotlin.math.abs(product.daysRemaining)} days ago" else product.expiryDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")), style = MaterialTheme.typography.titleLarge, color = expiryColor, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (product.allergens.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("ALLERGENS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(12.dp))
                    Text(product.allergens.joinToString(", "), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DietarySection(attributes: List<String>) {
    if (attributes.isEmpty()) return
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Text("DIETARY ATTRIBUTES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(0.7f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                attributes.forEach { attribute ->
                    Surface(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(0.1f), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(4.dp))
                            Text(attribute, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NutritionSection(nutrition: List<Int>) {
    val labels = listOf("Calories", "Protein", "Fat", "Carbs", "Sugar", "Sodium")
    val units = listOf("kcal", "g", "g", "g", "g", "mg")
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth()) { labels.take(3).forEachIndexed { i, label -> NutritionItem(label, nutrition.getOrNull(i) ?: 0, units[i], Modifier.weight(1f)) } }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) { labels.drop(3).forEachIndexed { i, label -> NutritionItem(label, nutrition.getOrNull(i+3) ?: 0, units[i+3], Modifier.weight(1f)) } }
        }
    }
}

@Composable
fun NutritionItem(label: String, value: Int, unit: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Text(label, style = MaterialTheme.typography.labelSmall); Text("$value$unit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }
}

@Composable
fun IngredientsSection(ingredients: List<String>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))) {
        Column(Modifier.padding(20.dp)) {
            Text("INGREDIENTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(ingredients.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ErrorScreen(message: String) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer.copy(0.9f)).padding(32.dp), Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(32.dp), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(64.dp), MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(24.dp))
                Text("SCAN ERROR", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }
    }
}


