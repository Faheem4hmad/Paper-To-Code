package com.example.papertocode.data.remote

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class AiCodeEngine(
    private val apiKey: String = ApiKeyProvider.GEMINI_API_KEY
) {

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val modelName = "gemini-3.1-flash-lite"


    suspend fun extractCodeOnly(bitmap: Bitmap): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val maxDimension = 1200
            val scale = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            } else {
                1.0f
            }
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )

            val stream = ByteArrayOutputStream()
            // 85% prevents edge artifacting around handwritten strokes
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

            val prompt = """
                You are an expert OCR and Polyglot DSA Compiler.
                Carefully read the handwritten code/algorithm from this image.
                Pay close attention to index boundaries, variable names, and loop conditions.
                
                RULES:
                1. Detect the programming language (Java, C++, Python, JavaScript, C).
                2. If the language is Java, strictly DO NOT use the ternary operator anywhere (always use standard if-else).
                3. Do NOT include any markdown code blocks (no ``` or ```java).
                
                RESPONSE FORMAT:
                LANG: <Detected Language Name>
                CODE:
                <Clean Raw Code Here>
            """.trimIndent()

            val root = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.0)
                    put("maxOutputTokens", 1500)
                })
            }

            val text = executeGeminiRequest(root)
            var detectedLang = "Code"
            val codeBuilder = StringBuilder()
            var isReadingCode = false

            for (line in text.lines()) {
                if (line.startsWith("LANG:")) {
                    detectedLang = line.removePrefix("LANG:").trim()
                } else if (line.startsWith("CODE:")) {
                    isReadingCode = true
                } else if (isReadingCode) {
                    if (!line.startsWith("```")) {
                        codeBuilder.append(line).append("\n")
                    }
                }
            }

            val finalCode = codeBuilder.toString().trim().ifEmpty {
                text.replace(Regex("```[a-zA-Z]*"), "").replace("```", "").trim()
            }

            Result.success(Pair(detectedLang, finalCode))
        } catch (e: Throwable) {
            Result.failure(mapToUserFriendlyException(e))
        }
    }

    // 2. Step-by-Step Multi-Language Dry Run Trace
    suspend fun generateDryRun(
        code: String,
        language: String = "Code",
        customInput: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputInstruction = if (customInput.isNotBlank()) {
                "User custom input: \"$customInput\". Parse parameters accurately."
            } else {
                "Select a clean, concise test case with all parameters defined."
            }

            val prompt = """
                Perform a step-by-step Dry Run trace for this $language code.
                $inputInstruction

                STRICT FORMATTING RULES:
                1. DO NOT use markdown characters: No *, #, `, _, ~.
                2. Put an EMPTY BLANK LINE between sections and steps.

                OUTPUT FORMAT:
                ===========================
                DSA PATTERN: [Pattern Name]
                ===========================

                INITIAL SETUP:
                - Function Arguments: [e.g. arr = [1, 2, 3], k = 2]
                - Expected Output: [Value]
                - Variables State Before Loop: [State]

                STEP BY STEP ITERATION TRACE:

                Step 1:
                - Current Indices / Pointers: ...
                - Condition Evaluated: ...
                - Action Executed: ...
                - UPDATED STATE: ...
                - PARTIAL OUTPUT: ...

                ===========================
                FINAL EXECUTION RESULT:
                ===========================
                - Loop Exit Reason: ...
                - FINAL RETURNED OUTPUT: [Result]

                Code to Trace:
                $code
            """.trimIndent()

            val root = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 1200)
                })
            }

            val rawText = executeGeminiRequest(root)
            val cleanedText = rawText.replace(Regex("[#*`_~]"), "").trim()
            Result.success(cleanedText)
        } catch (e: Throwable) {
            Result.failure(mapToUserFriendlyException(e))
        }
    }

    // 3. Mathematical Complexity Proof
    suspend fun analyzeComplexity(code: String, language: String = "Code"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Provide direct mathematical complexity calculation for this $language code. No markdown (*, #, `, _).

                OUTPUT FORMAT:
                ===========================
                TIME COMPLEXITY: [Value]
                ===========================
                - Loop / Recursion: [Details]
                - Mathematical Proof: [Proof]
                - Worst Case: [Value]
                - Best Case: [Value]

                ===========================
                SPACE COMPLEXITY: [Value]
                ===========================
                - Auxiliary Structures: [Structures]
                - Call Stack Memory: [Depth]
                - In-Place Status: [Yes/No]
                - Final Auxiliary Space: [Value]

                Code:
                $code
            """.trimIndent()

            val root = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.0)
                    put("maxOutputTokens", 500)
                })
            }

            val rawText = executeGeminiRequest(root)
            val cleanedText = rawText.replace(Regex("[#*`_~]"), "").trim()
            Result.success(cleanedText)
        } catch (e: Throwable) {
            Result.failure(mapToUserFriendlyException(e))
        }
    }

    // 4. Live Code Execution & Sandbox Output
    suspend fun executeCode(code: String, language: String = "Code", customInput: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputPrompt = if (customInput.isNotBlank()) "Input Data: $customInput" else "Use default sample test inputs."

            val prompt = """
                Execute this $language code ($inputPrompt) and output ONLY the raw terminal output.
                No markdown blocks, no conversational explanations.
                $code
            """.trimIndent()

            val root = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.0)
                    put("maxOutputTokens", 300)
                })
            }

            val rawText = executeGeminiRequest(root)
            val cleanedText = rawText.replace(Regex("```[a-zA-Z]*"), "").replace("```", "").trim()
            Result.success(cleanedText)
        } catch (e: Throwable) {
            Result.failure(mapToUserFriendlyException(e))
        }
    }

    // 5. Related LeetCode Problems Recommendation
    suspend fun getRelatedLeetCodeProblems(code: String, language: String = "Code"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Suggest exactly 3 high-frequency LeetCode problems matching the algorithmic pattern of this $language code.
                No markdown characters (*, #, `, _).

                OUTPUT FORMAT:
                ===========================
                IDENTIFIED PATTERN: [Pattern]
                ===========================

                RECOMMENDED LEETCODE PROBLEMS:
                1. [Number]. [Title] - [Difficulty]
                   - Why: [1 line]
                2. [Number]. [Title] - [Difficulty]
                   - Why: [1 line]
                3. [Number]. [Title] - [Difficulty]
                   - Why: [1 line]

                Code:
                $code
            """.trimIndent()

            val root = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 450)
                })
            }

            val rawText = executeGeminiRequest(root)
            val cleanedText = rawText.replace(Regex("[#*`_~]"), "").trim()
            Result.success(cleanedText)
        } catch (e: Throwable) {
            Result.failure(mapToUserFriendlyException(e))
        }
    }

    private fun executeGeminiRequest(root: JSONObject): String {
        val trimmedKey = ApiKeyProvider.GEMINI_API_KEY.trim()


        val httpUrl = HttpUrl.Builder()
            .scheme("https")
            .host("generativelanguage.googleapis.com")
            .addPathSegment("v1beta")
            .addPathSegment("models")
            .addPathSegment("$modelName:generateContent")
            .build()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = root.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(httpUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-goog-api-key", trimmedKey)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseText = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorMsg = try {
                JSONObject(responseText).getJSONObject("error").getString("message")
            } catch (e: Exception) {
                responseText
            }
            throw Exception("API Error (${response.code}): $errorMsg")
        }

        val json = JSONObject(responseText)
        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    private fun mapToUserFriendlyException(e: Throwable): Exception {
        return when (e) {
            is UnknownHostException -> Exception("No Internet Connection. Please check your network.")
            is SocketTimeoutException -> Exception("Request timed out. Please try again.")
            is IOException -> Exception("Network error occurred. Please try again.")
            else -> Exception(e.localizedMessage ?: "An unexpected error occurred.")
        }
    }
}