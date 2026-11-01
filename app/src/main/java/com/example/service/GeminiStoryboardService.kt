package com.example.service

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.CameraMovement
import com.example.data.model.ShotType
import com.example.data.model.StoryboardScene
import com.example.data.model.StoryboardShot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object GeminiStoryboardService {

    private const val TAG = "GeminiStoryboardService"
    private const val MODEL_NAME = "gemini-3.5-flash"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Breaks down script text into a sequence of cinematic storyboard scenes and shots.
     */
    suspend fun generateStoryboardFromScript(scriptText: String): List<StoryboardScene> =
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey()
            if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
                Log.d(TAG, "No user Gemini API key provided, generating local heuristic storyboard breakdown.")
                return@withContext generateLocalScriptBreakdown(scriptText)
            }

            try {
                val prompt = """
                    You are a veteran Hollywood cinematographer and storyboard director.
                    Analyze the following screenplay/script excerpt and break it down into sequential storyboard scenes and visual shots.
                    
                    For each scene, identify:
                    - sceneNumber (integer)
                    - heading (e.g., EXT. RAIN-SLICKED ALLEY - NIGHT)
                    - location
                    - timeOfDay
                    - summary (one sentence)
                    
                    For each shot within a scene, provide:
                    - shotNumber (integer)
                    - shotType (must be one of: EXTREME_WIDE, WIDE_SHOT, MEDIUM_SHOT, CLOSE_UP, EXTREME_CLOSE_UP, OVER_SHOULDER, POV, DUTCH_ANGLE)
                    - cameraMovement (must be one of: STATIC, PAN_LEFT, PAN_RIGHT, TILT_UP, TILT_DOWN, DOLLY_IN, TRACKING, CRANE)
                    - visualDescription (cinematographic framing and subject positioning)
                    - actionNotes (what happens in the frame)
                    - dialogue (if any character speaks during this shot)
                    - visualPrompt (a vivid, detailed prompt for generating a cinematic concept sketch)
                    
                    Output strictly valid JSON matching this format:
                    {
                      "scenes": [
                        {
                          "sceneNumber": 1,
                          "heading": "...",
                          "location": "...",
                          "timeOfDay": "...",
                          "summary": "...",
                          "shots": [
                            {
                              "shotNumber": 1,
                              "shotType": "WIDE_SHOT",
                              "cameraMovement": "TRACKING",
                              "visualDescription": "...",
                              "actionNotes": "...",
                              "dialogue": "...",
                              "visualPrompt": "..."
                            }
                          ]
                        }
                      ]
                    }
                    
                    Screenplay excerpt:
                    $scriptText
                """.trimIndent()

                val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"
                val requestJson = JSONObject().apply {
                    val contents = JSONArray().apply {
                        val part = JSONObject().apply {
                            val parts = JSONArray().apply {
                                put(JSONObject().put("text", prompt))
                            }
                            put("parts", parts)
                        }
                        put(part)
                    }
                    put("contents", contents)
                }

                val body = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(body).build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    val scenes = parseGeminiResponse(responseBody)
                    if (scenes.isNotEmpty()) {
                        return@withContext scenes
                    }
                } else {
                    Log.w(TAG, "Gemini API error code: ${response.code}, message: $responseBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing Gemini API call: ${e.message}", e)
            }

            // Fallback to local heuristic extraction if API call fails or returns empty
            return@withContext generateLocalScriptBreakdown(scriptText)
        }

    private fun getApiKey(): String? {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isNullOrBlank() || key == "MY_GEMINI_API_KEY") null else key
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGeminiResponse(jsonString: String): List<StoryboardScene> {
        return try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates") ?: return emptyList()
            val firstCandidate = candidates.optJSONObject(0) ?: return emptyList()
            val content = firstCandidate.optJSONObject("content") ?: return emptyList()
            val parts = content.optJSONArray("parts") ?: return emptyList()
            val textPart = parts.optJSONObject(0)?.optString("text") ?: return emptyList()

            // Extract JSON from markdown fences if present
            val cleanedJson = textPart.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val scenesJson = JSONObject(cleanedJson).getJSONArray("scenes")
            val result = mutableListOf<StoryboardScene>()

            for (i in 0 until scenesJson.length()) {
                val scObj = scenesJson.getJSONObject(i)
                val sceneNum = scObj.optInt("sceneNumber", i + 1)
                val heading = scObj.optString("heading", "SCENE $sceneNum")
                val location = scObj.optString("location", "Studio Stage")
                val timeOfDay = scObj.optString("timeOfDay", "DAY")
                val summary = scObj.optString("summary", "Scene $sceneNum narrative sequence.")

                val shotsJson = scObj.optJSONArray("shots") ?: JSONArray()
                val shotsList = mutableListOf<StoryboardShot>()

                for (j in 0 until shotsJson.length()) {
                    val shObj = shotsJson.getJSONObject(j)
                    val shotNum = shObj.optInt("shotNumber", j + 1)
                    val shotTypeStr = shObj.optString("shotType", "MEDIUM_SHOT")
                    val movementStr = shObj.optString("cameraMovement", "STATIC")

                    val shotType = try {
                        ShotType.valueOf(shotTypeStr.uppercase())
                    } catch (e: Exception) {
                        ShotType.MEDIUM_SHOT
                    }

                    val cameraMovement = try {
                        CameraMovement.valueOf(movementStr.uppercase())
                    } catch (e: Exception) {
                        CameraMovement.STATIC
                    }

                    shotsList.add(
                        StoryboardShot(
                            id = UUID.randomUUID().toString(),
                            sceneNumber = sceneNum,
                            shotNumber = shotNum,
                            shotType = shotType,
                            cameraMovement = cameraMovement,
                            visualDescription = shObj.optString("visualDescription", "Framed composition"),
                            actionNotes = shObj.optString("actionNotes", "Action unfolds in frame"),
                            dialogue = shObj.optString("dialogue").ifBlank { null },
                            visualPrompt = shObj.optString("visualPrompt", "Cinematic sketch of $heading"),
                            sketchSeed = (sceneNum * 10 + shotNum)
                        )
                    )
                }

                result.add(
                    StoryboardScene(
                        sceneNumber = sceneNum,
                        heading = heading,
                        location = location,
                        timeOfDay = timeOfDay,
                        summary = summary,
                        shots = shotsList
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response JSON: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Reliable heuristic script parser that converts any screenplay or book text
     * into dramatic, director-grade storyboard shots with camera framing.
     */
    fun generateLocalScriptBreakdown(scriptText: String): List<StoryboardScene> {
        val lines = scriptText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val scenes = mutableListOf<StoryboardScene>()

        var currentSceneNumber = 1
        var currentHeading = "SCENE 1: EXT. RAIN-SLICKED ALLEY - NIGHT"
        var currentLocation = "City Sector 4"
        var currentTime = "NIGHT"
        var currentSummary = "Detective confronts shadowy contact in the rain."
        var currentShots = mutableListOf<StoryboardShot>()
        var shotCounter = 1

        val shotTypes = listOf(
            ShotType.EXTREME_WIDE,
            ShotType.WIDE_SHOT,
            ShotType.MEDIUM_SHOT,
            ShotType.OVER_SHOULDER,
            ShotType.CLOSE_UP,
            ShotType.DUTCH_ANGLE
        )
        val movements = listOf(
            CameraMovement.DOLLY_IN,
            CameraMovement.TRACKING,
            CameraMovement.STATIC,
            CameraMovement.PAN_RIGHT,
            CameraMovement.CRANE
        )

        for (line in lines) {
            if (line.startsWith("SCENE", ignoreCase = true) ||
                line.startsWith("EXT.", ignoreCase = true) ||
                line.startsWith("INT.", ignoreCase = true) ||
                line.startsWith("CHAPTER", ignoreCase = true)
            ) {
                if (currentShots.isNotEmpty()) {
                    scenes.add(
                        StoryboardScene(
                            sceneNumber = currentSceneNumber,
                            heading = currentHeading,
                            location = currentLocation,
                            timeOfDay = currentTime,
                            summary = currentSummary,
                            shots = currentShots
                        )
                    )
                    currentSceneNumber++
                    currentShots = mutableListOf()
                    shotCounter = 1
                }
                currentHeading = line
                currentLocation = if (line.contains("INT.")) "Interior Location" else "Exterior Alley"
                currentTime = if (line.contains("NIGHT", ignoreCase = true)) "NIGHT" else "DAY"
                currentSummary = "Key narrative beat in $currentHeading"
            } else if (line.length > 25 && shotCounter <= 4) {
                val sType = shotTypes[(shotCounter - 1) % shotTypes.size]
                val sMove = movements[(shotCounter - 1) % movements.size]

                currentShots.add(
                    StoryboardShot(
                        id = UUID.randomUUID().toString(),
                        sceneNumber = currentSceneNumber,
                        shotNumber = shotCounter,
                        shotType = sType,
                        cameraMovement = sMove,
                        visualDescription = "${sType.label} capturing the intense atmosphere of $currentHeading.",
                        actionNotes = line,
                        dialogue = if (line.contains(":") || line.all { it.isUpperCase() }) line else null,
                        visualPrompt = "Cinematic film concept sketch, $sType, high-contrast chiaroscuro lighting, charcoal texture, cinematic atmosphere of $currentHeading.",
                        sketchSeed = currentSceneNumber * 10 + shotCounter
                    )
                )
                shotCounter++
            }
        }

        if (currentShots.isNotEmpty()) {
            scenes.add(
                StoryboardScene(
                    sceneNumber = currentSceneNumber,
                    heading = currentHeading,
                    location = currentLocation,
                    timeOfDay = currentTime,
                    summary = currentSummary,
                    shots = currentShots
                )
            )
        }

        // Default fallback if no clear scenes detected
        if (scenes.isEmpty()) {
            scenes.add(
                StoryboardScene(
                    sceneNumber = 1,
                    heading = "SCENE 1: OPENING SEQUENCE",
                    location = "Main Stage",
                    timeOfDay = "DUSK",
                    summary = "Atmospheric opening sequence establishing mood and main character.",
                    shots = listOf(
                        StoryboardShot(
                            id = UUID.randomUUID().toString(),
                            sceneNumber = 1,
                            shotNumber = 1,
                            shotType = ShotType.WIDE_SHOT,
                            cameraMovement = CameraMovement.TRACKING,
                            visualDescription = "Wide establishing shot of the sprawling landscape with dramatic sky.",
                            actionNotes = "The protagonist steps into the frame against the stormy horizon.",
                            dialogue = null,
                            visualPrompt = "Cinematic widescreen storyboard sketch, wide angle establishing shot, rough charcoal and sepia wash style.",
                            sketchSeed = 11
                        ),
                        StoryboardShot(
                            id = UUID.randomUUID().toString(),
                            sceneNumber = 1,
                            shotNumber = 2,
                            shotType = ShotType.CLOSE_UP,
                            cameraMovement = CameraMovement.DOLLY_IN,
                            visualDescription = "Close-up on character's focused expression as they glance back.",
                            actionNotes = "Wind stirs their hair as they reach for their equipment.",
                            dialogue = "RAY: 'Time to finish what we started.'",
                            visualPrompt = "Cinematic director storyboard sketch, dramatic close-up, expressive character linework, atmospheric rim light.",
                            sketchSeed = 12
                        ),
                        StoryboardShot(
                            id = UUID.randomUUID().toString(),
                            sceneNumber = 1,
                            shotNumber = 3,
                            shotType = ShotType.DUTCH_ANGLE,
                            cameraMovement = CameraMovement.STATIC,
                            visualDescription = "Canted angle emphasizing sudden tension as an obstacle appears.",
                            actionNotes = "Sparks shower from the damaged power conduit.",
                            dialogue = null,
                            visualPrompt = "Dynamic tilted angle storyboard frame, high tension, cinematic chiaroscuro sketching.",
                            sketchSeed = 13
                        )
                    )
                )
            )
        }

        return scenes
    }
}
