package com.example.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VocabularyEntry
import com.example.service.DictionaryInfoType
import com.example.service.VocabularyManager
import com.example.ui.ReaderViewModel
import com.example.ui.theme.GoldFoil
import com.example.ui.theme.GoldLight
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditVocabularyScreen(
    viewModel: ReaderViewModel,
    onBackToReader: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeDocument by viewModel.activeDocument.collectAsState()
    val doc = activeDocument ?: return

    val entries by viewModel.vocabularyEntries.collectAsState()
    val isLookingUp by viewModel.isLookingUpDictionary.collectAsState()
    val lookupStatusText by viewModel.lookupStatusText.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    // Dialog States
    var showRangeDialog by remember { mutableStateOf(false) }
    var showEditWordDialog by remember { mutableStateOf<VocabularyEntry?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showLookupDialog by remember { mutableStateOf(false) }
    var showRawTxtDialog by remember { mutableStateOf(false) }

    // Filtered entries
    val filteredEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) {
            entries
        } else {
            val q = searchQuery.trim().lowercase()
            entries.filter {
                it.word.lowercase().contains(q) ||
                        (it.meaning?.lowercase()?.contains(q) == true) ||
                        (it.hindiMeaning?.contains(q) == true)
            }
        }
    }

    BackHandler {
        onBackToReader()
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
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackToReader,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("vocab_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Reader",
                                tint = GoldLight
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column {
                            Text(
                                text = "Vocabulary Notebook",
                                color = GoldFoil,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            val filename = "${VocabularyManager.sanitizeFilename(doc.title)}_Vocabulary.txt"
                            Text(
                                text = "$filename • ${entries.size} word${if (entries.size == 1) "" else "s"}",
                                color = Color(0xFF8FA3C7),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // View Raw TXT Dialog Button
                        IconButton(
                            onClick = { showRawTxtDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("view_raw_txt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "View TXT File",
                                tint = GoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Share / Export TXT Button
                        IconButton(
                            onClick = {
                                val shareIntent = VocabularyManager.createShareIntent(context, doc.id, doc.title)
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Vocabulary TXT"))
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("export_txt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share TXT File",
                                tint = GoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search saved words, meanings or notes...",
                            color = Color(0xFF6B7A99),
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = GoldLight,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("search_vocab_input"),
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
            }

            // Selection & Actions Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22D4AF37))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Quick Selection tools
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextButton(
                            onClick = {
                                selectedIds.clear()
                                selectedIds.addAll(filteredEntries.map { it.id })
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("select_all_button")
                        ) {
                            Text("Select All", color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (selectedIds.isNotEmpty()) {
                            TextButton(
                                onClick = { selectedIds.clear() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("clear_selection_button")
                            ) {
                                Text("Clear", color = Color(0xFFEF5350), fontSize = 11.sp)
                            }
                        }

                        TextButton(
                            onClick = { showRangeDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("select_range_button")
                        ) {
                            Text("Range...", color = Color(0xFFB0BACB), fontSize = 11.sp)
                        }
                    }

                    // Right: Actions on Selected Entries
                    if (selectedIds.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Copy to Clipboard
                            IconButton(
                                onClick = {
                                    val wordsToCopy = entries.filter { selectedIds.contains(it.id) }
                                        .mapIndexed { idx, it -> "${idx + 1}. ${it.word}" }
                                        .joinToString("\n")
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Vocabulary", wordsToCopy))
                                    Toast.makeText(context, "Copied ${selectedIds.size} word(s) to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("copy_selected_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Selected",
                                    tint = GoldLight,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Cut to Clipboard
                            IconButton(
                                onClick = {
                                    val wordsToCopy = entries.filter { selectedIds.contains(it.id) }
                                        .mapIndexed { idx, it -> "${idx + 1}. ${it.word}" }
                                        .joinToString("\n")
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Vocabulary", wordsToCopy))
                                    Toast.makeText(context, "Cut ${selectedIds.size} word(s) to clipboard", Toast.LENGTH_SHORT).show()
                                    showDeleteConfirmDialog = true
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("cut_selected_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCut,
                                    contentDescription = "Cut Selected",
                                    tint = GoldLight,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // If single word selected: Edit
                            if (selectedIds.size == 1) {
                                val singleEntry = entries.find { it.id == selectedIds.first() }
                                IconButton(
                                    onClick = { showEditWordDialog = singleEntry },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("edit_word_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Word",
                                        tint = GoldLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Delete Selected
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("delete_selected_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Selected",
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Dictionary Lookup Action Button
                            Button(
                                onClick = { showLookupDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldFoil,
                                    contentColor = NavyDeep
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("dictionary_lookup_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Look Up (${selectedIds.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            text = "${entries.size} entries",
                            color = Color(0xFF6B7A99),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Looking up status indicator
            AnimatedVisibility(visible = isLookingUp) {
                Surface(
                    color = Color(0x33D4AF37),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = GoldFoil,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = lookupStatusText.ifBlank { "Looking up dictionary notes..." },
                            color = GoldLight,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Word List (Chronological 1..N)
            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0x1AD4AF37)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No words match \"$searchQuery\"" else "No vocabulary words collected yet",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty())
                                "Try searching for a different term."
                            else
                                "Triple-tap on any word while reading to automatically capture it into this notebook.",
                            color = Color(0xFF8FA3C7),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(filteredEntries, key = { _, item -> item.id }) { index, entry ->
                        val isSelected = selectedIds.contains(entry.id)
                        val sequentialIndex = entries.indexOfFirst { it.id == entry.id } + 1

                        VocabularyItemCard(
                            entry = entry,
                            index = sequentialIndex,
                            isSelected = isSelected,
                            onToggleSelection = {
                                if (isSelected) selectedIds.remove(entry.id) else selectedIds.add(entry.id)
                            },
                            onEdit = { showEditWordDialog = entry },
                            onDelete = {
                                viewModel.deleteVocabularyEntries(setOf(entry.id), retainNotes = false)
                                selectedIds.remove(entry.id)
                            },
                            onSingleLookup = {
                                selectedIds.clear()
                                selectedIds.add(entry.id)
                                showLookupDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // 1. Range Selection Dialog (e.g., 5 to 10)
    if (showRangeDialog) {
        var startStr by remember { mutableStateOf("1") }
        var endStr by remember { mutableStateOf(entries.size.coerceAtLeast(1).toString()) }

        AlertDialog(
            onDismissRequest = { showRangeDialog = false },
            containerColor = NavyDark,
            title = {
                Text(
                    text = "Select Range of Entries",
                    color = GoldFoil,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column {
                    Text("Select entries sequentially by their 1-based order:", color = Color(0xFFB0BACB), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = startStr,
                            onValueChange = { startStr = it },
                            label = { Text("From #", color = GoldLight) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("range_start_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endStr,
                            onValueChange = { endStr = it },
                            label = { Text("To #", color = GoldLight) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("range_end_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val start = (startStr.toIntOrNull() ?: 1).coerceIn(1, entries.size)
                        val end = (endStr.toIntOrNull() ?: entries.size).coerceIn(start, entries.size)
                        selectedIds.clear()
                        for (i in (start - 1) until end) {
                            entries.getOrNull(i)?.let { selectedIds.add(it.id) }
                        }
                        showRangeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldFoil, contentColor = NavyDeep),
                    modifier = Modifier.testTag("apply_range_button")
                ) {
                    Text("Select Range")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRangeDialog = false }) {
                    Text("Cancel", color = Color(0xFFB0BACB))
                }
            }
        )
    }

    // 2. Edit/Rename Word Dialog
    showEditWordDialog?.let { entryToEdit ->
        var editedWord by remember { mutableStateOf(entryToEdit.word) }

        AlertDialog(
            onDismissRequest = { showEditWordDialog = null },
            containerColor = NavyDark,
            title = {
                Text(
                    text = "Edit Vocabulary Word",
                    color = GoldFoil,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column {
                    Text("Original spelling from source: \"${entryToEdit.word}\"", color = Color(0xFFB0BACB), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editedWord,
                        onValueChange = { editedWord = it },
                        label = { Text("Word", color = GoldLight) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_word_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldFoil
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editedWord.isNotBlank()) {
                            viewModel.editVocabularyWord(entryToEdit.id, editedWord.trim())
                        }
                        showEditWordDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldFoil, contentColor = NavyDeep),
                    modifier = Modifier.testTag("save_edit_word_button")
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditWordDialog = null }) {
                    Text("Cancel", color = Color(0xFFB0BACB))
                }
            }
        )
    }

    // 3. Bulk Delete Confirmation Dialog (with Linked Notes handling choice)
    if (showDeleteConfirmDialog) {
        var retainLinkedNotes by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = NavyDark,
            title = {
                Text(
                    text = "Delete ${selectedIds.size} Word${if (selectedIds.size > 1) "s" else ""}?",
                    color = Color(0xFFEF5350),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column {
                    Text(
                        "After deletion, remaining vocabulary entries will be automatically renumbered 1..N sequentially. Surviving stable IDs and links will be preserved.",
                        color = Color(0xFFB0BACB),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { retainLinkedNotes = !retainLinkedNotes }
                            .padding(4.dp)
                    ) {
                        Checkbox(
                            checked = retainLinkedNotes,
                            onCheckedChange = { retainLinkedNotes = it },
                            colors = CheckboxDefaults.colors(checkedColor = GoldFoil)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Keep linked dictionary notes as retained reference",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVocabularyEntries(selectedIds.toSet(), retainNotes = retainLinkedNotes)
                        selectedIds.clear()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350), contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFFB0BACB))
                }
            }
        )
    }

    // 4. Dictionary Lookup Options Dialog (P4)
    if (showLookupDialog) {
        var selectedInfoType by remember { mutableStateOf(DictionaryInfoType.ALL) }
        var preferHindi by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showLookupDialog = false },
            containerColor = NavyDark,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = GoldFoil, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Dictionary / Lexical Lookup",
                        color = GoldFoil,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                        // §3.D: bounded, scrollable content so the "Prefer Hindi
                        // Explanations" control is never clipped on small screens.
                        .heightIn(max = 380.dp)
                ) {
                    Text(
                        text = "Querying lexical provider (Free Dictionary API) for ${selectedIds.size} selected word(s) separately. Results will be saved directly into ${VocabularyManager.sanitizeFilename(doc.title)}_Vocabulary.txt.",
                        color = Color(0xFFB0BACB),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("SELECT INFORMATION TO RETRIEVE:", color = GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    val options = listOf(
                        DictionaryInfoType.MEANING,
                        DictionaryInfoType.SYNONYMS,
                        DictionaryInfoType.ANTONYMS,
                        DictionaryInfoType.EXAMPLE,
                        DictionaryInfoType.SYNONYMS_AND_EXAMPLE,
                        DictionaryInfoType.ANTONYMS_AND_EXAMPLE,
                        DictionaryInfoType.MEANING_AND_EXAMPLE,
                        DictionaryInfoType.ALL
                    )

                    options.forEach { opt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { selectedInfoType = opt }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedInfoType == opt,
                                onClick = { selectedInfoType = opt },
                                colors = RadioButtonDefaults.colors(selectedColor = GoldFoil)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = opt.label, color = Color.White, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Hindi Explanation Preference Toggle
                    Surface(
                        color = Color(0x22FFFFFF),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Prefer Hindi Explanations", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Includes हिन्दी translations where supported by lexical database.", color = Color(0xFF8FA3C7), fontSize = 10.sp)
                            }
                            Switch(
                                checked = preferHindi,
                                onCheckedChange = { preferHindi = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = GoldFoil, checkedTrackColor = Color(0x66D4AF37))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToLookup = selectedIds.toSet()
                        showLookupDialog = false
                        viewModel.lookupVocabularyEntries(idsToLookup, selectedInfoType, preferHindi)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldFoil, contentColor = NavyDeep),
                    modifier = Modifier.testTag("execute_lookup_button")
                ) {
                    Text("Look Up Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLookupDialog = false }) {
                    Text("Cancel", color = Color(0xFFB0BACB))
                }
            }
        )
    }

    // 5. View Raw TXT Content Dialog
    if (showRawTxtDialog) {
        val vocabFile = VocabularyManager.getVocabularyFile(context, doc.id, doc.title)
        val fileContent = if (vocabFile.exists()) vocabFile.readText() else "No vocabulary notebook file created yet."

        AlertDialog(
            onDismissRequest = { showRawTxtDialog = false },
            containerColor = Color(0xFF0D1424),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Balance spacer so the filename stays visually centered
                    Spacer(modifier = Modifier.width(36.dp))
                    Text(
                        text = vocabFile.name,
                        color = GoldLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showRawTxtDialog = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            },
            text = {
                Surface(
                    color = Color(0xFF070B14),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33D4AF37)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = fileContent,
                            color = Color(0xFFE0E0E0),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Vocabulary TXT", fileContent))
                        Toast.makeText(context, "Full notebook text copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldFoil, contentColor = NavyDeep)
                ) {
                    Text("Copy All Text")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val shareIntent = VocabularyManager.createShareIntent(context, doc.id, doc.title)
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Vocabulary TXT"))
                    }
                ) {
                    Text("Share File", color = GoldLight)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabularyItemCard(
    entry: VocabularyEntry,
    index: Int,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSingleLookup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 0.8.dp,
                color = if (isSelected) GoldFoil else Color(0x33D4AF37),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("vocab_entry_$index"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0x22D4AF37) else Color(0xFF0F172A)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        colors = CheckboxDefaults.colors(checkedColor = GoldFoil),
                        modifier = Modifier.testTag("vocab_checkbox_$index")
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "$index. ",
                        color = GoldLight,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = entry.word,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0x1AD4AF37),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Page ${entry.pageNumber}",
                            color = GoldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onSingleLookup,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Look up",
                            tint = GoldLight,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF8FA3C7),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Enriched Information Section (P4)
            val hasEnrichment = !entry.meaning.isNullOrBlank() ||
                    entry.synonyms.isNotEmpty() ||
                    entry.antonyms.isNotEmpty() ||
                    !entry.exampleSentence.isNullOrBlank() ||
                    !entry.hindiMeaning.isNullOrBlank()

            if (hasEnrichment) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0x15000000),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0x22D4AF37)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        if (!entry.meaning.isNullOrBlank()) {
                            Text(
                                text = entry.meaning,
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        if (!entry.hindiMeaning.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "हिन्दी: ${entry.hindiMeaning}",
                                color = Color(0xFFFFCC80),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (entry.synonyms.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Synonyms: ", color = GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    entry.synonyms.take(5).forEach { syn ->
                                        Surface(
                                            color = Color(0x1AD4AF37),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(syn, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                        }

                        if (entry.antonyms.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Antonyms: ", color = Color(0xFFEF5350), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    entry.antonyms.take(4).forEach { ant ->
                                        Surface(
                                            color = Color(0x22EF5350),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(ant, color = Color(0xFFFFCDD2), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                        }

                        if (!entry.exampleSentence.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "\"${entry.exampleSentence}\"",
                                color = Color(0xFFB0BACB),
                                fontSize = 11.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }

                        if (!entry.lookupSource.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            // §6: show honest provider status — a failed lookup is never
                            // presented as a source of data.
                            val failureReason = VocabularyManager.failureReasonOf(entry.lookupSource)
                            if (failureReason != null) {
                                Text(
                                    text = "Lookup: Unavailable — $failureReason",
                                    color = Color(0xFFEF5350),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "Source: ${entry.lookupSource}",
                                    color = Color(0xFF64748B),
                                    fontSize = 9.sp
                                )
                            }
                        }

                        if (entry.lookupTimestamp != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Looked up: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(entry.lookupTimestamp))}",
                                color = Color(0xFF64748B),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
