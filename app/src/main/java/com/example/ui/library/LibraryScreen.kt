package com.example.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentFormat
import com.example.ui.theme.BurgundyRibbon
import com.example.ui.theme.GoldFoil
import com.example.ui.theme.GoldLight
import com.example.ui.theme.NavyDark
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.UploadFile
import com.example.ui.theme.NavyDeep

@Composable
fun LibraryScreen(
    documents: List<DocumentEntity>,
    onSelectDocument: (DocumentEntity) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onFileSelected: (Uri) -> Unit,
    onLoadSampleDocument: (sampleIndex: Int) -> Unit,
    onOpenPasteText: () -> Unit = {},
    isParsing: Boolean = false,
    parsingStage: String = "",
    parsingFilename: String = "",
    onCancelParsing: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmDialog by remember { mutableStateOf<DocumentEntity?>(null) }
    var showCopyUploadDialog by remember { mutableStateOf(false) }

    // Android System File Picker for PDF, TXT, EPUB, DOCX
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onFileSelected(uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF070B14),
        floatingActionButton = {
            Button(
                onClick = { showCopyUploadDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldFoil,
                    contentColor = NavyDeep
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .testTag("copy_and_upload_button")
                    .testTag("import_document_fab")
            ) {
                Icon(imageVector = Icons.Default.FileOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Or Upload", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    ) { innerPadding ->
        // Copy Or Upload Choice Dialog (§2, §3.A: title centered)
        if (showCopyUploadDialog) {
            AlertDialog(
                onDismissRequest = { showCopyUploadDialog = false },
                containerColor = NavyDark,
                title = {
                    Text(
                        text = "Copy Or Upload",
                        color = GoldFoil,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Choose how you want to import your content into StoryFlip:",
                            color = Color(0xFFB0BACB),
                            fontSize = 12.sp
                        )

                        // Option 1: Upload File
                        Surface(
                            onClick = {
                                showCopyUploadDialog = false
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "text/plain",
                                        "application/epub+zip",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "application/msword",
                                        "*/*"
                                    )
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x22FFFFFF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33D4AF37)),
                            modifier = Modifier.fillMaxWidth().testTag("choice_upload_file")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33D4AF37)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Upload File", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("PDF, TXT, EPUB or DOCX via file picker", color = Color(0xFF8FA3C7), fontSize = 11.sp)
                                }
                            }
                        }

                        // Option 2: Paste Text
                        Surface(
                            onClick = {
                                showCopyUploadDialog = false
                                onOpenPasteText()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x22FFFFFF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33D4AF37)),
                            modifier = Modifier.fillMaxWidth().testTag("choice_paste_text")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33D4AF37)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Paste Text", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Paste from ChatGPT, web articles, notes or email", color = Color(0xFF8FA3C7), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showCopyUploadDialog = false }) {
                        Text("Cancel", color = Color(0xFFB0BACB))
                    }
                }
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Header & Branding
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(NavyDeep, NavyDark)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x33D4AF37))
                                    .border(1.dp, GoldFoil, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = GoldLight,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "STORYFLIP",
                                    color = GoldFoil,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    text = "Automatic Document Reader & Storyboards",
                                    color = Color(0xFFB0BACB),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Supported Formats Pills (Hindi is a language, not a format — §1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("PDF", "TXT", "EPUB", "DOCX").forEach { fmt ->
                            Surface(
                                color = Color(0x1AD4AF37),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0x33D4AF37))
                            ) {
                                Text(
                                    text = fmt,
                                    color = GoldLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Demo Samples — 8 intentional fixtures (4 English, 4 Hindi) across
                    // the four supported formats. Opening one is a session-only action (§1.B/C).
                    Text("QUICK DEMO SAMPLES", color = Color(0xFF8FA3C7), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("sample_chips_row")
                    ) {
                        itemsIndexed(com.example.service.DemoCatalog.descriptors) { index, descriptor ->
                            Button(
                                onClick = { onLoadSampleDocument(index) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x22FFFFFF),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("sample_chip_${descriptor.id}")
                            ) {
                                Text(descriptor.chipLabel, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Documents List or Empty State
                if (documents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1AD4AF37)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = GoldFoil,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "No documents in your Library yet",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Use \"Copy Or Upload\" to import a PDF, TXT, EPUB, or DOCX file, paste text, or open a Quick Demo Sample above. Demo samples never appear here.",
                                color = Color(0xFF8FA3C7),
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIBRARY DOCUMENTS (${documents.size})",
                                    color = GoldLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        items(documents, key = { it.id }) { doc ->
                            DocumentLibraryCard(
                                document = doc,
                                onClick = { onSelectDocument(doc) },
                                onDelete = { showDeleteConfirmDialog = doc }
                            )
                        }
                    }
                }
            }

            // AUTOMATIC FILE LOADED & PARSING MODAL OVERLAY
            if (isParsing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .shadow(24.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyDark)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = GoldFoil, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(18.dp))

                            // Dialog title: centered (§3.A/C)
                            Text(
                                text = "File Loading",
                                color = GoldLight,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Selected-document block: label + real filename left-aligned
                            // inside a clearly defined content block (§3.C).
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33D4AF37)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "SELECTED DOCUMENT",
                                        color = Color(0xFF8FA3C7),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = parsingFilename.ifBlank { "Selected Document" },
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = parsingStage.ifBlank { "Parsing content automatically..." },
                                        color = GoldLight,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            TextButton(onClick = onCancelParsing) {
                                Text("Cancel", color = Color(0xFFC0CAD8))
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog (§3.A: centered title)
    showDeleteConfirmDialog?.let { doc ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = {
                Text(
                    text = "Remove from Library?",
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = { Text("Remove \"${doc.title}\" and its saved reading progress from the local library?", color = Color(0xFFC0CAD8)) },
            containerColor = NavyDark,
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDocument(doc.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun DocumentLibraryCard(
    document: DocumentEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("document_card_${document.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131A2B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Book Spine & Miniature Cover
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(68.dp)
                    .shadow(8.dp, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF090E1A), NavyDeep, Color(0xFF1B2438))
                        )
                    )
                    .border(1.dp, Color(0x44D4AF37), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (document.format == DocumentFormat.SCRIPT) Icons.Default.Movie else Icons.Default.Description,
                    contentDescription = null,
                    tint = GoldFoil,
                    modifier = Modifier.size(22.dp)
                )

                if (document.hasProgress && document.bookmarkPage != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 4.dp)
                            .width(8.dp)
                            .height(24.dp)
                            .background(BurgundyRibbon, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Metadata & Progress
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0x26D4AF37),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = document.format.name,
                            color = GoldLight,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (document.hasProgress && document.bookmarkPage != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = BurgundyRibbon,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Bookmark: Page ${document.bookmarkPage}",
                                color = GoldLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = document.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "By ${document.author} • ${document.totalPages} Pages",
                    color = Color(0xFF9EAABF),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                val progress = (document.lastReadPage.toFloat() / document.totalPages.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = GoldFoil,
                    trackColor = Color(0x22FFFFFF)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove Document",
                    tint = Color(0xFF7A869C)
                )
            }
        }
    }
}
