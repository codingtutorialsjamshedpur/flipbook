package com.example.ui.library

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GoldFoil
import com.example.ui.theme.GoldLight
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep

// Configurable safe limits for pasted text import
const val MAX_PASTE_WORDS_LIMIT = 10000
const val MAX_PASTE_LINES_LIMIT = 1000

@Composable
fun PasteTextScreen(
    onCreateBook: (title: String, author: String, text: String) -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var optionalTitle by remember { mutableStateOf("") }
    var optionalAuthor by remember { mutableStateOf("") }
    var pastedText by remember { mutableStateOf("") }

    // Real-time counter metrics
    val wordCount by remember(pastedText) {
        derivedStateOf {
            if (pastedText.isBlank()) 0
            else pastedText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        }
    }

    val lineCount by remember(pastedText) {
        derivedStateOf {
            if (pastedText.isBlank()) 0
            else pastedText.lines().count { it.isNotBlank() }
        }
    }

    val charCount by remember(pastedText) {
        derivedStateOf { pastedText.length }
    }

    val isWordLimitExceeded by remember(wordCount) {
        derivedStateOf { wordCount > MAX_PASTE_WORDS_LIMIT }
    }

    val isLineLimitExceeded by remember(lineCount) {
        derivedStateOf { lineCount > MAX_PASTE_LINES_LIMIT }
    }

    val isOverLimit by remember(isWordLimitExceeded, isLineLimitExceeded) {
        derivedStateOf { isWordLimitExceeded || isLineLimitExceeded }
    }

    val canCreateBook by remember(pastedText, isOverLimit) {
        derivedStateOf { pastedText.isNotBlank() && !isOverLimit }
    }

    BackHandler {
        onBackToLibrary()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF070B14)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Top Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(NavyDeep, NavyDark)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBackToLibrary,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("paste_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Library",
                                tint = GoldLight
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = "Paste Text to Create a Book",
                                color = GoldFoil,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "ChatGPT, web articles, notes or emails",
                                color = Color(0xFF8FA3C7),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Clear Text Button
                    if (pastedText.isNotEmpty()) {
                        TextButton(
                            onClick = { pastedText = "" },
                            modifier = Modifier.testTag("clear_text_button")
                        ) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", color = Color(0xFFEF5350), fontSize = 11.sp)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Optional Book Title Field
                OutlinedTextField(
                    value = optionalTitle,
                    onValueChange = { optionalTitle = it },
                    label = { Text("Book Title (Optional)", color = GoldLight) },
                    placeholder = { Text("e.g., Essay on Resilience", color = Color(0xFF6B7A99)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_title_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0x22101929),
                        unfocusedContainerColor = Color(0x22101929),
                        focusedBorderColor = GoldFoil,
                        unfocusedBorderColor = Color(0x44D4AF37),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Optional Author Field (§5)
                OutlinedTextField(
                    value = optionalAuthor,
                    onValueChange = { optionalAuthor = it },
                    label = { Text("Author (optional)", color = GoldLight) },
                    placeholder = { Text("e.g., Munshi Premchand", color = Color(0xFF6B7A99)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_author_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0x22101929),
                        unfocusedContainerColor = Color(0x22101929),
                        focusedBorderColor = GoldFoil,
                        unfocusedBorderColor = Color(0x44D4AF37),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    supportingText = {
                        Text(
                            "Leave blank if unknown — the book will be saved without an invented author.",
                            color = Color(0xFF6B7A99),
                            fontSize = 10.sp
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Paste from Clipboard & Counter Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val text = clip.getItemAt(0).text?.toString() ?: ""
                                if (text.isNotBlank()) {
                                    pastedText = text
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x33D4AF37),
                            contentColor = GoldLight
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("paste_from_clipboard_button")
                    ) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste from Clipboard", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Live Metrics Badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (isWordLimitExceeded) Color(0x33EF5350) else Color(0x1AD4AF37),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(0.6.dp, if (isWordLimitExceeded) Color(0xFFEF5350) else Color(0x33D4AF37))
                        ) {
                            Text(
                                text = "$wordCount / 10,000 words",
                                color = if (isWordLimitExceeded) Color(0xFFFF8A80) else GoldLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            color = if (isLineLimitExceeded) Color(0x33EF5350) else Color(0x1AD4AF37),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "$lineCount / 1,000 lines",
                                color = if (isLineLimitExceeded) Color(0xFFFF8A80) else Color(0xFF8FA3C7),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Limit Exceeded Warning Banner
                if (isOverLimit) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0x33EF5350),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exceeds safe limits (up to 10,000 words or 1,000 lines). Please shorten your text or import it as a TXT file via File Picker.",
                                color = Color(0xFFFFCDD2),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Main Multiline Text Area
                OutlinedTextField(
                    value = pastedText,
                    onValueChange = { pastedText = it },
                    placeholder = {
                        Text(
                            "Paste long text here...\n\nSupports English, Hindi (हिन्दी), Indic scripts, paragraphs, dialogue, and articles from any application.",
                            color = Color(0xFF6B7A99),
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("paste_text_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0B1120),
                        focusedBorderColor = GoldFoil,
                        unfocusedBorderColor = Color(0x33D4AF37),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action: Create Book & Read
                Button(
                    onClick = {
                        if (canCreateBook) {
                            onCreateBook(optionalTitle, optionalAuthor, pastedText)
                        }
                    },
                    enabled = canCreateBook,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldFoil,
                        contentColor = NavyDeep,
                        disabledContainerColor = Color(0x33D4AF37),
                        disabledContentColor = Color(0x66D4AF37)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(if (canCreateBook) 8.dp else 0.dp, RoundedCornerShape(14.dp))
                        .testTag("create_book_from_paste_button")
                ) {
                    Icon(imageVector = Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (pastedText.isBlank()) "Paste or Enter Text to Continue" else "Create Book & Open Reader",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
