package com.example.ui.storyboard

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CameraMovement
import com.example.data.model.DocumentEntity
import com.example.data.model.ShotType
import com.example.data.model.StoryboardScene
import com.example.data.model.StoryboardShot
import com.example.service.PdfExporter
import com.example.ui.reader.PageCurlState
import com.example.ui.reader.PageFlipContainer
import com.example.ui.theme.CameraBadgeBg
import com.example.ui.theme.CameraBadgeText
import com.example.ui.theme.GoldFoil
import com.example.ui.theme.GoldLight
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PaperBackground
import com.example.ui.theme.PaperText
import com.example.ui.theme.PaperTextSecondary
import java.util.UUID

@Composable
fun StoryboardStudioView(
    document: DocumentEntity,
    scenes: List<StoryboardScene>,
    isGenerating: Boolean,
    onRegenerate: () -> Unit,
    onAddShot: (sceneNumber: Int, shot: StoryboardShot) -> Unit,
    onBackToReader: () -> Unit,
    pageCurlState: PageCurlState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Director Deck, 1: Flipbook View
    var showAddDialog by remember { mutableStateOf(false) }
    var currentFlipIndex by remember { mutableIntStateOf(0) }

    // Flatten shots for flipbook view
    val allShots = remember(scenes) {
        scenes.flatMap { sc -> sc.shots.map { Pair(sc, it) } }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090E1A))
    ) {
        // Studio Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavyDeep)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackToReader,
                    modifier = Modifier.size(36.dp).testTag("storyboard_back_button")
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
                        text = "STORYBOARD STUDIO",
                        color = GoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = document.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Export PDF button
                Button(
                    onClick = {
                        val result = PdfExporter.exportStoryboardDeck(context, document.title, scenes)
                        result.onSuccess { path ->
                            Toast.makeText(context, "Exported PDF deck successfully!", Toast.LENGTH_LONG).show()
                        }.onFailure {
                            Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldFoil,
                        contentColor = NavyDeep
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(34.dp).testTag("export_pdf_button")
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Regenerate AI Breakdown
                IconButton(
                    onClick = onRegenerate,
                    modifier = Modifier.size(36.dp).testTag("regenerate_storyboard_button"),
                    enabled = !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = GoldFoil, strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Regenerate AI Breakdown", tint = GoldLight)
                    }
                }
            }
        }

        // View Mode Tabs: Director Deck vs Storyboard Flipbook
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = NavyDark,
            contentColor = GoldLight,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = GoldFoil,
                    height = 2.5.dp
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Director Deck (${allShots.size} Shots)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Storyboard Flipbook", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (isGenerating) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = GoldFoil)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("AI Cinematographer Analyzing Script...", color = GoldLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Extracting scene headings, shot framing & concept prompts", color = Color(0xFFA0AAC0), fontSize = 12.sp)
                }
            }
        } else if (selectedTab == 0) {
            // DIRECTOR SHOT DECK (SCENE LIST)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(scenes) { scene ->
                        SceneHeaderCard(scene = scene)
                        Spacer(modifier = Modifier.height(8.dp))
                        scene.shots.forEach { shot ->
                            StoryboardShotCard(shot = shot, sceneHeading = scene.heading)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                // Add Shot Floating Action Button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                ) {
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldFoil,
                            contentColor = NavyDeep
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.shadow(8.dp, RoundedCornerShape(16.dp)).testTag("add_custom_shot_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Shot", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // STORYBOARD FLIPBOOK VIEW (Flip through shots with 3D curl physics)
            if (allShots.isNotEmpty()) {
                val currentPair = allShots.getOrNull(currentFlipIndex)
                val canPrev = currentFlipIndex > 0
                val canNext = currentFlipIndex < allShots.size - 1

                PageFlipContainer(
                    state = pageCurlState,
                    isLandscape = false,
                    canTurnForward = canNext,
                    canTurnBackward = canPrev,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        currentPair?.let { (scene, shot) ->
                            StoryboardFlipbookSheet(
                                scene = scene,
                                shot = shot,
                                index = currentFlipIndex + 1,
                                total = allShots.size
                            )
                        }
                    }
                }

                // Flipbook Navigation Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyDark)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (currentFlipIndex > 0) currentFlipIndex-- },
                        enabled = currentFlipIndex > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x33D4AF37),
                            contentColor = GoldLight
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Previous Panel")
                    }

                    Text(
                        text = "Panel ${currentFlipIndex + 1} of ${allShots.size}",
                        color = GoldLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = { if (currentFlipIndex < allShots.size - 1) currentFlipIndex++ },
                        enabled = currentFlipIndex < allShots.size - 1,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldFoil,
                            contentColor = NavyDeep
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Next Panel", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No storyboard shots generated yet.", color = Color.White)
                }
            }
        }
    }

    // Add Custom Shot Dialog
    if (showAddDialog) {
        AddShotDialog(
            scenes = scenes,
            onDismiss = { showAddDialog = false },
            onConfirm = { sceneNum, newShot ->
                onAddShot(sceneNum, newShot)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun SceneHeaderCard(scene: StoryboardScene) {
    Surface(
        color = NavyDark,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33D4AF37)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SCENE ${scene.sceneNumber}",
                    color = GoldFoil,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = scene.heading,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${scene.location} • ${scene.timeOfDay}",
                    color = Color(0xFF9EAABF),
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x26D4AF37))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${scene.shots.size} SHOTS",
                    color = GoldLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun StoryboardShotCard(shot: StoryboardShot, sceneHeading: String) {
    Surface(
        color = Color(0xFF131A2B),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
        modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Card Top Row: Shot Badge & Camera Movement
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CameraBadgeBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "SHOT ${shot.shotNumber}",
                            color = CameraBadgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x33D4AF37))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = shot.shotType.label,
                            color = GoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF8FA3C7), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(shot.cameraMovement.label, color = Color(0xFF8FA3C7), fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cinematic Sketch Frame (Film Sprocket Canvas)
            CinematicSketchFrame(
                shot = shot,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Description
            Text("ACTION:", color = GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(shot.actionNotes, color = Color(0xFFE2E6F0), fontSize = 12.sp, lineHeight = 16.sp)

            // Dialogue if present
            if (!shot.dialogue.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("DIALOGUE:", color = Color(0xFF8FA3C7), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(
                    text = shot.dialogue,
                    color = GoldLight,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * High-fidelity sketch frame with filmstrip border, chiaroscuro shading, and framing lines.
 */
@Composable
fun CinematicSketchFrame(shot: StoryboardShot, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E2129))
            .border(1.dp, Color(0xFF384050), RoundedCornerShape(8.dp))
            .drawBehind {
                // Top and bottom sprocket notches
                val notchCount = 14
                val notchWidth = size.width / (notchCount * 2f)
                val notchHeight = 10.dp.toPx()

                for (i in 0 until notchCount) {
                    val x = i * notchWidth * 2f + notchWidth * 0.5f
                    // Top notch
                    drawRect(Color(0xFF101216), Offset(x, 0f), androidx.compose.ui.geometry.Size(notchWidth, notchHeight))
                    // Bottom notch
                    drawRect(Color(0xFF101216), Offset(x, size.height - notchHeight), androidx.compose.ui.geometry.Size(notchWidth, notchHeight))
                }

                // Rule-of-thirds cinematic grid guides
                val thirdX1 = size.width / 3f
                val thirdX2 = size.width * 2f / 3f
                val thirdY1 = size.height / 3f
                val thirdY2 = size.height * 2f / 3f

                drawLine(Color(0x22FFFFFF), Offset(thirdX1, 0f), Offset(thirdX1, size.height), 1f)
                drawLine(Color(0x22FFFFFF), Offset(thirdX2, 0f), Offset(thirdX2, size.height), 1f)
                drawLine(Color(0x22FFFFFF), Offset(0f, thirdY1), Offset(size.width, thirdY1), 1f)
                drawLine(Color(0x22FFFFFF), Offset(0f, thirdY2), Offset(size.width, thirdY2), 1f)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x44000000))
                    .border(1.dp, Color(0x33D4AF37), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${shot.shotType.label} • ${shot.cameraMovement.label}".uppercase(),
                    color = GoldLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = shot.visualPrompt,
                color = Color(0xFFC0CAD8),
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Animator's physical sketchbook sheet for Flipbook View.
 */
@Composable
fun StoryboardFlipbookSheet(
    scene: StoryboardScene,
    shot: StoryboardShot,
    index: Int,
    total: Int
) {
    Surface(
        color = PaperBackground,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 18.dp,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .aspectRatio(3f / 4f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SCENE ${scene.sceneNumber}: ${scene.heading.take(30)}",
                    color = PaperTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "FRAME $index / $total",
                    color = GoldFoil,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Cinematic Sketch Frame
            CinematicSketchFrame(
                shot = shot,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9.5f)
            )

            // Shot Details
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SHOT ${shot.shotNumber}: ${shot.shotType.label}",
                        color = PaperText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = shot.cameraMovement.label,
                        color = PaperTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = shot.actionNotes,
                    color = PaperText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                if (!shot.dialogue.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = shot.dialogue,
                        color = Color(0xFF6B5824),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Bottom Flip Hint
            Text(
                text = "— Drag right edge to flip forward • Drag left edge to flip back —",
                color = PaperTextSecondary.copy(alpha = 0.6f),
                fontSize = 10.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShotDialog(
    scenes: List<StoryboardScene>,
    onDismiss: () -> Unit,
    onConfirm: (sceneNumber: Int, shot: StoryboardShot) -> Unit
) {
    var selectedSceneNumber by remember { mutableIntStateOf(scenes.firstOrNull()?.sceneNumber ?: 1) }
    var shotType by remember { mutableStateOf(ShotType.MEDIUM_SHOT) }
    var cameraMovement by remember { mutableStateOf(CameraMovement.STATIC) }
    var actionNotes by remember { mutableStateOf("") }
    var dialogue by remember { mutableStateOf("") }
    var visualPrompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Storyboard Shot", color = GoldLight) },
        containerColor = NavyDark,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = actionNotes,
                    onValueChange = { actionNotes = it },
                    label = { Text("Action Notes") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldFoil,
                        unfocusedBorderColor = Color(0x44FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dialogue,
                    onValueChange = { dialogue = it },
                    label = { Text("Dialogue (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldFoil,
                        unfocusedBorderColor = Color(0x44FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = visualPrompt,
                    onValueChange = { visualPrompt = it },
                    label = { Text("Visual Prompt for Concept Sketch") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldFoil,
                        unfocusedBorderColor = Color(0x44FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newShot = StoryboardShot(
                        id = UUID.randomUUID().toString(),
                        sceneNumber = selectedSceneNumber,
                        shotNumber = (scenes.find { it.sceneNumber == selectedSceneNumber }?.shots?.size ?: 0) + 1,
                        shotType = shotType,
                        cameraMovement = cameraMovement,
                        visualDescription = actionNotes.ifBlank { "Custom Framed Shot" },
                        actionNotes = actionNotes.ifBlank { "Action occurs in frame." },
                        dialogue = dialogue.ifBlank { null },
                        visualPrompt = visualPrompt.ifBlank { "Cinematic storyboard sketch of scene $selectedSceneNumber." }
                    )
                    onConfirm(selectedSceneNumber, newShot)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldFoil, contentColor = NavyDeep)
            ) {
                Text("Add Shot", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFC0CAD8))
            }
        }
    )
}
