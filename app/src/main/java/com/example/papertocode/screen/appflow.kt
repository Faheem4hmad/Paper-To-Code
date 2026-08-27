package com.example.papertocode.screen

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.papertocode.data.local.AppDatabase
import com.example.papertocode.data.local.CodeHistoryEntity
import com.example.papertocode.data.remote.AiCodeEngine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppScreen {
    HOME,
    CAMERA,
    CODE_PREVIEW
}

@Composable
fun AppFlow(
    database: Any? = null,
    aiEngine: AiCodeEngine = remember { AiCodeEngine() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val historyList by db.codeHistoryDao().getAllHistory().collectAsState(initial = emptyList())

    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var isDarkMode by remember { mutableStateOf(true) }
    var loadingMessage by remember { mutableStateOf<String?>(null) }
    var runOutputDialogText by remember { mutableStateOf<String?>(null) }
    var leetcodeDialogText by remember { mutableStateOf<String?>(null) }
    var scannedCode by remember { mutableStateOf("") }
    var detectedLanguage by remember { mutableStateOf("CODE") }

    var showCustomInputDialog by remember { mutableStateOf(false) }
    var customInputText by remember { mutableStateOf("") }

    var dryRunDialogText by remember { mutableStateOf<String?>(null) }
    var complexityDialogText by remember { mutableStateOf<String?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    val processExtractedImage: (Bitmap) -> Unit = { bitmap ->
        loadingMessage = "Extracting Code from Image..."
        coroutineScope.launch {
            val result = aiEngine.extractCodeOnly(bitmap)
            loadingMessage = null
            result.onSuccess { pair ->
                scannedCode = pair.second
                detectedLanguage = pair.first

                val formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
                db.codeHistoryDao().insertCode(
                    CodeHistoryEntity(
                        language = pair.first,
                        code = pair.second,
                        formattedDateTime = formattedDate
                    )
                )

                currentScreen = AppScreen.CODE_PREVIEW
            }.onFailure { error ->
                Toast.makeText(context, "Error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                currentScreen = AppScreen.HOME
            }
        }
    }

    val runDryRun: (String) -> Unit = { input ->
        if (scannedCode.isBlank()) {
            Toast.makeText(context, "Please scan or select a code first", Toast.LENGTH_SHORT).show()
        } else {
            loadingMessage = "Tracing execution with test case..."
            coroutineScope.launch {
                val res = aiEngine.generateDryRun(scannedCode, detectedLanguage, input)
                loadingMessage = null
                res.onSuccess { trace ->
                    dryRunDialogText = trace
                }.onFailure {
                    Toast.makeText(context, "Dry run generation failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (loadingMessage != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkMode) Color(0xFF0D1322) else Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = Color(0xFF4F46E5),
                    modifier = Modifier.size(50.dp),
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = loadingMessage.orEmpty(),
                    color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    // 1. Custom Test Case Input Dialog
    if (showCustomInputDialog) {
        AlertDialog(
            onDismissRequest = { showCustomInputDialog = false },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            containerColor = Color(0xFF131B2E),
            titleContentColor = Color(0xFF818CF8),
            title = {
                Text(
                    text = "Custom Test Case Inputs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter input values for code execution (e.g. array, target, k, string).",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Array & Target: arr=[2, 7, 11], target=9\n• Array & Window: nums=[2, 1, 5], k=3\n• String: s=\"racecar\"\n• Blank = Auto-generate clean test case",
                        color = Color(0xFF64748B),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = customInputText,
                        onValueChange = { customInputText = it },
                        placeholder = {
                            Text("e.g. arr = [2, 1, 5, 1, 3], k = 3", color = Color(0xFF475569))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4F46E5),
                            unfocusedBorderColor = Color(0xFF334155),
                            cursorColor = Color(0xFF818CF8)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(95.dp),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = customInputText
                        showCustomInputDialog = false
                        runDryRun(input)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Run Trace", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomInputDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // 2. Large Dry Run Execution Trace Dialog (550dp)
    dryRunDialogText?.let { text ->
        AlertDialog(
            onDismissRequest = { dryRunDialogText = null },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            containerColor = Color(0xFF111827),
            titleContentColor = Color(0xFF818CF8),
            textContentColor = Color(0xFFE2E8F0),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Dry Run Execution Trace",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF4F46E5).copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = detectedLanguage.uppercase(),
                            color = Color(0xFFA5B4FC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            text = {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF030712)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(550.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFF93C5FD),
                            lineHeight = 22.sp,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { dryRunDialogText = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Close Trace", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        )
    }

    // 3. Complexity Dialog (550dp)
    complexityDialogText?.let { text ->
        AlertDialog(
            onDismissRequest = { complexityDialogText = null },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            containerColor = Color(0xFF111827),
            titleContentColor = Color(0xFF38BDF8),
            textContentColor = Color(0xFFE2E8F0),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Complexity Calculation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0284C7).copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = detectedLanguage.uppercase(),
                            color = Color(0xFF7DD3FC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            text = {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF030712)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(550.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFF7DD3FC),
                            lineHeight = 22.sp,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { complexityDialogText = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Close Analysis", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        )
    }

    // 4. Live Code Execution / Console Output Dialog
    runOutputDialogText?.let { output ->
        AlertDialog(
            onDismissRequest = { runOutputDialogText = null },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            containerColor = Color(0xFF111827),
            titleContentColor = Color(0xFF10B981),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Execution Terminal Output",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF10B981)
                    )
                }
            },
            text = {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF030712)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = output.ifBlank { "Program executed successfully with no return value." },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.5.sp,
                            color = Color(0xFF6EE7B7),
                            lineHeight = 20.sp,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { runOutputDialogText = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close Terminal", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // 5. Related LeetCode Problems Dialog (550dp)
    leetcodeDialogText?.let { text ->
        AlertDialog(
            onDismissRequest = { leetcodeDialogText = null },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            containerColor = Color(0xFF111827),
            titleContentColor = Color(0xFFF59E0B),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Related LeetCode Practice",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFFF59E0B)
                    )
                }
            },
            text = {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF030712)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(550.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFFFDE68A),
                            lineHeight = 22.sp,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { leetcodeDialogText = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Close Problems", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Black)
                }
            }
        )
    }

    // 6. Saved History Dialog (550dp)
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            containerColor = Color(0xFF111827),
            titleContentColor = Color(0xFF818CF8),
            textContentColor = Color(0xFFE2E8F0),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Saved Scan History", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    if (historyList.isNotEmpty()) {
                        TextButton(onClick = { coroutineScope.launch { db.codeHistoryDao().clearAllHistory() } }) {
                            Text("Clear All", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }
                }
            },
            text = {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF030712)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(550.dp)
                ) {
                    if (historyList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No scanned codes saved yet.", color = Color(0xFF64748B), fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(historyList) { item ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161F36)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scannedCode = item.code
                                            detectedLanguage = item.language
                                            showHistoryDialog = false
                                            currentScreen = AppScreen.CODE_PREVIEW
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = item.language.uppercase(),
                                                color = Color(0xFF818CF8),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = item.formattedDateTime,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = item.code.take(90) + if (item.code.length > 90) "..." else "",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFFE2E8F0),
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHistoryDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Close History", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        )
    }

    when (currentScreen) {
        AppScreen.HOME -> {
            HomeScreen(
                isDarkMode = isDarkMode,
                onToggleTheme = { isDarkMode = !isDarkMode },
                onScanClick = { currentScreen = AppScreen.CAMERA },
                onGalleryImageSelected = { bitmap -> processExtractedImage(bitmap) },
                onDryRunClick = {
                    showCustomInputDialog = true
                },
                onComplexityClick = {
                    if (scannedCode.isBlank()) {
                        Toast.makeText(context, "Please scan a note first", Toast.LENGTH_SHORT).show()
                    } else {
                        loadingMessage = "Calculating Time & Space..."
                        coroutineScope.launch {
                            val res = aiEngine.analyzeComplexity(scannedCode, detectedLanguage)
                            loadingMessage = null
                            res.onSuccess { breakdown ->
                                complexityDialogText = breakdown
                            }.onFailure {
                                Toast.makeText(context, "Complexity calculation failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onExportClick = {
                    if (scannedCode.isNotBlank()) {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, scannedCode)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share $detectedLanguage Code"))
                    } else {
                        Toast.makeText(context, "No scanned code available to export", Toast.LENGTH_SHORT).show()
                    }
                },
                onHistoryClick = {
                    showHistoryDialog = true
                },
                onRescanClick = {
                    scannedCode = ""
                    currentScreen = AppScreen.CAMERA
                }
            )
        }

        AppScreen.CAMERA -> {
            CameraScreen(
                onImageConfirmed = { bitmap -> processExtractedImage(bitmap) },
                onBack = { currentScreen = AppScreen.HOME }
            )
        }

        AppScreen.CODE_PREVIEW -> {
            ScannedCodeScreen(
                code = scannedCode,
                language = detectedLanguage,
                onBack = { currentScreen = AppScreen.HOME },
                onDryRun = {
                    showCustomInputDialog = true
                },
                onComplexity = {
                    loadingMessage = "Calculating Time & Space..."
                    coroutineScope.launch {
                        val res = aiEngine.analyzeComplexity(scannedCode, detectedLanguage)
                        loadingMessage = null
                        res.onSuccess { breakdown ->
                            complexityDialogText = breakdown
                        }.onFailure {
                            Toast.makeText(context, "Complexity calculation failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onRunCode = {
                    loadingMessage = "Executing Code on Sandbox..."
                    coroutineScope.launch {
                        val res = aiEngine.executeCode(scannedCode, detectedLanguage, customInputText)
                        loadingMessage = null
                        res.onSuccess { output ->
                            runOutputDialogText = output
                        }.onFailure {
                            Toast.makeText(context, "Execution failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onLeetCodeProblems = {
                    loadingMessage = "Finding matching LeetCode problems..."
                    coroutineScope.launch {
                        val res = aiEngine.getRelatedLeetCodeProblems(scannedCode, detectedLanguage)
                        loadingMessage = null
                        res.onSuccess { problems ->
                            leetcodeDialogText = problems
                        }.onFailure {
                            Toast.makeText(context, "Failed to fetch problems", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}