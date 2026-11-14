package com.example.data

import android.util.Base64
import com.example.model.DownloadedArtifact
import com.example.model.GitHubArtifact
import com.example.model.GitHubJob
import com.example.model.GitHubJobStep
import com.example.model.GitHubRepo
import com.example.model.GitHubUser
import com.example.model.GitHubWorkflowRun
import com.example.model.LogCategory
import com.example.model.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class GitHubApiClient(
    private val onLog: (LogCategory, LogLevel, String, String) -> Unit
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun buildRequest(
        token: String,
        url: String,
        method: String = "GET",
        jsonBody: JSONObject? = null
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "CodeForge-Android-IDE")

        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer ${token.trim()}")
        }

        val body = jsonBody?.toString()?.toRequestBody(jsonMediaType)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: "{}".toRequestBody(jsonMediaType))
            "PUT" -> builder.put(body ?: "{}".toRequestBody(jsonMediaType))
            "PATCH" -> builder.patch(body ?: "{}".toRequestBody(jsonMediaType))
        }
        return builder.build()
    }

    private fun executeJson(
        token: String,
        url: String,
        method: String = "GET",
        jsonBody: JSONObject? = null
    ): Result<Pair<Int, String>> {
        return runCatching {
            val request = buildRequest(token, url, method, jsonBody)
            client.newCall(request).execute().use { response ->
                val code = response.code
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = runCatching { JSONObject(raw).optString("message", raw) }
                        .getOrDefault(raw)
                    val friendly = if (errMsg.contains("workflow", ignoreCase = true) && errMsg.contains("scope", ignoreCase = true)) {
                        "Tu token de GitHub necesita el permiso 'workflow' (además de 'repo') para subir archivos en .github/workflows/."
                    } else {
                        "GitHub HTTP $code: $errMsg"
                    }
                    throw IllegalStateException(friendly)
                }
                code to raw
            }
        }
    }

    suspend fun verifyToken(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        runCatching {
            val request = buildRequest(token, "https://api.github.com/user")
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = runCatching { JSONObject(raw).optString("message", "Token inválido") }
                        .getOrDefault("Token inválido")
                    throw IllegalStateException("HTTP ${response.code}: $msg")
                }
                val scopes = response.header("X-OAuth-Scopes")
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()
                val json = JSONObject(raw)
                val user = GitHubUser(
                    login = json.getString("login"),
                    name = json.optString("name").ifBlank { json.getString("login") },
                    avatarUrl = json.optString("avatar_url"),
                    htmlUrl = json.optString("html_url"),
                    scopes = scopes
                )
                onLog(
                    LogCategory.GITHUB_API,
                    LogLevel.SUCCESS,
                    "GitHub Auth",
                    "Conectado como @${user.login} (scopes: ${scopes.joinToString(", ").ifBlank { "fine-grained" }})"
                )
                user
            }
        }.onFailure {
            onLog(LogCategory.GITHUB_API, LogLevel.ERROR, "GitHub Auth", it.message ?: "Error al validar token")
        }
    }

    suspend fun listRepos(token: String): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        executeJson(
            token,
            "https://api.github.com/user/repos?sort=updated&per_page=50&affiliation=owner,collaborator"
        ).map { (_, raw) ->
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                parseRepo(arr.getJSONObject(i))
            }
        }
    }

    suspend fun getRepo(
        token: String,
        owner: String,
        repo: String
    ): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        executeJson(
            token,
            "https://api.github.com/repos/${owner.trim()}/${repo.trim()}"
        ).map { (_, raw) ->
            parseRepo(JSONObject(raw))
        }
    }

    suspend fun createRepo(
        token: String,
        name: String,
        description: String,
        isPrivate: Boolean
    ): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("name", name.trim())
            put("description", description.trim())
            put("private", isPrivate)
            put("auto_init", true)
        }
        onLog(LogCategory.GITHUB_API, LogLevel.INFO, "GitHub Repo", "Creando repositorio '$name'...")
        executeJson(token, "https://api.github.com/user/repos", "POST", body).map { (_, raw) ->
            val repo = parseRepo(JSONObject(raw))
            onLog(LogCategory.GITHUB_API, LogLevel.SUCCESS, "GitHub Repo", "Repositorio creado: ${repo.fullName}")
            repo
        }.onFailure {
            onLog(LogCategory.GITHUB_API, LogLevel.ERROR, "GitHub Repo", it.message ?: "No se pudo crear repo")
        }
    }

    suspend fun pushFilesAtomically(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        commitMessage: String,
        files: List<Pair<String, ByteArray>>,
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress("Verificando rama '$branch' en $owner/$repo...")
            val refUrl = "https://api.github.com/repos/$owner/$repo/git/ref/heads/$branch"
            var refResult = executeJson(token, refUrl)

            if (refResult.isFailure) {
                // Repository may be completely empty (409 Git Repository is empty or 404 Not Found).
                // Bootstrap initial commit via Contents API.
                onProgress("Inicializando repositorio vacío con README.md...")
                onLog(LogCategory.GITHUB_API, LogLevel.INFO, "Git Push", "Repositorio vacío detectado; creando commit inicial...")
                val initPayload = JSONObject().apply {
                    put("message", "chore: initialize repository from CodeForge")
                    put("content", Base64.encodeToString("# $repo\n".toByteArray(), Base64.NO_WRAP))
                    put("branch", branch)
                }
                executeJson(
                    token,
                    "https://api.github.com/repos/$owner/$repo/contents/README.md",
                    "PUT",
                    initPayload
                ).getOrThrow()
                refResult = executeJson(token, refUrl)
            }

            val refJson = JSONObject(refResult.getOrThrow().second)
            val headCommitSha = refJson.getJSONObject("object").getString("sha")

            val commitJson = JSONObject(
                executeJson(
                    token,
                    "https://api.github.com/repos/$owner/$repo/git/commits/$headCommitSha"
                ).getOrThrow().second
            )
            val baseTreeSha = commitJson.getJSONObject("tree").getString("sha")

            val treeArray = JSONArray()
            files.forEachIndexed { index, (path, bytes) ->
                onProgress("Subiendo archivo ${index + 1}/${files.size}: $path")
                val blobPayload = JSONObject().apply {
                    put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    put("encoding", "base64")
                }
                val blobResp = JSONObject(
                    executeJson(
                        token,
                        "https://api.github.com/repos/$owner/$repo/git/blobs",
                        "POST",
                        blobPayload
                    ).getOrThrow().second
                )
                val blobSha = blobResp.getString("sha")
                val mode = if (path.endsWith("gradlew") || path.endsWith(".sh")) "100755" else "100644"
                treeArray.put(
                    JSONObject().apply {
                        put("path", path)
                        put("mode", mode)
                        put("type", "blob")
                        put("sha", blobSha)
                    }
                )
            }

            onProgress("Creando árbol Git (${files.size} archivos)...")
            val treePayload = JSONObject().apply {
                put("base_tree", baseTreeSha)
                put("tree", treeArray)
            }
            val newTreeSha = JSONObject(
                executeJson(
                    token,
                    "https://api.github.com/repos/$owner/$repo/git/trees",
                    "POST",
                    treePayload
                ).getOrThrow().second
            ).getString("sha")

            onProgress("Creando commit...")
            val newCommitPayload = JSONObject().apply {
                put("message", commitMessage.ifBlank { "Update from CodeForge IDE" })
                put("tree", newTreeSha)
                put("parents", JSONArray().put(headCommitSha))
            }
            val newCommitSha = JSONObject(
                executeJson(
                    token,
                    "https://api.github.com/repos/$owner/$repo/git/commits",
                    "POST",
                    newCommitPayload
                ).getOrThrow().second
            ).getString("sha")

            onProgress("Actualizando referencia heads/$branch...")
            val patchRefPayload = JSONObject().apply {
                put("sha", newCommitSha)
                put("force", false)
            }
            executeJson(
                token,
                "https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch",
                "PATCH",
                patchRefPayload
            ).getOrThrow()

            onLog(
                LogCategory.GITHUB_API,
                LogLevel.SUCCESS,
                "Git Push",
                "Commit ${newCommitSha.take(7)} publicado en $owner/$repo ($branch)"
            )
            newCommitSha
        }.onFailure {
            onLog(LogCategory.GITHUB_API, LogLevel.ERROR, "Git Push", it.message ?: "Error al subir código")
        }
    }

    suspend fun triggerWorkflowDispatch(
        token: String,
        owner: String,
        repo: String,
        workflowFile: String,
        branch: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("ref", branch) }
        executeJson(
            token,
            "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFile/dispatches",
            "POST",
            body
        ).map {
            onLog(
                LogCategory.BUILD,
                LogLevel.INFO,
                "GitHub Actions",
                "Workflow '$workflowFile' disparado en rama '$branch'"
            )
        }
    }

    suspend fun listWorkflowRuns(
        token: String,
        owner: String,
        repo: String
    ): Result<List<GitHubWorkflowRun>> = withContext(Dispatchers.IO) {
        executeJson(
            token,
            "https://api.github.com/repos/$owner/$repo/actions/runs?per_page=10"
        ).map { (_, raw) ->
            val arr = JSONObject(raw).optJSONArray("workflow_runs") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                GitHubWorkflowRun(
                    id = obj.getLong("id"),
                    name = obj.optString("name", "Workflow"),
                    status = obj.optString("status", "unknown"),
                    conclusion = obj.optString("conclusion").takeIf { it.isNotBlank() && it != "null" },
                    event = obj.optString("event", "push"),
                    htmlUrl = obj.optString("html_url"),
                    createdAt = obj.optString("created_at"),
                    updatedAt = obj.optString("updated_at"),
                    runNumber = obj.optInt("run_number", 0),
                    headSha = obj.optString("head_sha")
                )
            }
        }
    }

    suspend fun listRunJobs(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<List<GitHubJob>> = withContext(Dispatchers.IO) {
        executeJson(
            token,
            "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs"
        ).map { (_, raw) ->
            val arr = JSONObject(raw).optJSONArray("jobs") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val stepsArr = obj.optJSONArray("steps") ?: JSONArray()
                val steps = (0 until stepsArr.length()).map { sIdx ->
                    val s = stepsArr.getJSONObject(sIdx)
                    GitHubJobStep(
                        number = s.optInt("number", sIdx + 1),
                        name = s.optString("name", "Step"),
                        status = s.optString("status", "queued"),
                        conclusion = s.optString("conclusion").takeIf { it.isNotBlank() && it != "null" }
                    )
                }
                GitHubJob(
                    id = obj.getLong("id"),
                    name = obj.optString("name", "Job"),
                    status = obj.optString("status", "queued"),
                    conclusion = obj.optString("conclusion").takeIf { it.isNotBlank() && it != "null" },
                    startedAt = obj.optString("started_at").takeIf { it != "null" },
                    completedAt = obj.optString("completed_at").takeIf { it != "null" },
                    steps = steps
                )
            }
        }
    }

    suspend fun listRunArtifacts(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<List<GitHubArtifact>> = withContext(Dispatchers.IO) {
        executeJson(
            token,
            "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts"
        ).map { (_, raw) ->
            val arr = JSONObject(raw).optJSONArray("artifacts") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                GitHubArtifact(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    sizeInBytes = obj.optLong("size_in_bytes", 0L),
                    archiveDownloadUrl = obj.getString("archive_download_url"),
                    expired = obj.optBoolean("expired", false),
                    createdAt = obj.optString("created_at")
                )
            }
        }
    }

    suspend fun downloadAndExtractArtifact(
        token: String,
        artifact: GitHubArtifact,
        destDir: File,
        onProgress: (Float) -> Unit
    ): Result<DownloadedArtifact> = withContext(Dispatchers.IO) {
        runCatching {
            destDir.mkdirs()
            val zipFile = File(destDir, "${artifact.name}_${artifact.id}.zip")
            onLog(LogCategory.BUILD, LogLevel.INFO, "Descarga", "Descargando ${artifact.name} desde GitHub...")

            val req = Request.Builder()
                .url(artifact.archiveDownloadUrl)
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer ${token.trim()}")
                .header("User-Agent", "CodeForge-Android-IDE")
                .get()
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Error HTTP ${response.code} al descargar artefacto")
                }
                val body = response.body ?: throw IllegalStateException("Respuesta vacía")
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: artifact.sizeInBytes.coerceAtLeast(1L)
                body.byteStream().use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            onProgress((copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.95f))
                        }
                    }
                }
            }

            // Extract APK or AAB from the downloaded GitHub Actions ZIP
            var extractedFile: File? = null
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val safeName = File(entry.name).name
                        val target = File(destDir, "${artifact.id}_$safeName")
                        FileOutputStream(target).use { out ->
                            zis.copyTo(out)
                        }
                        if (safeName.endsWith(".apk", true) || safeName.endsWith(".aab", true)) {
                            extractedFile = target
                        } else if (extractedFile == null) {
                            extractedFile = target
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            zipFile.delete()
            onProgress(1f)

            val finalFile = extractedFile ?: throw IllegalStateException("El ZIP del artefacto no contiene archivos.")
            val isApk = finalFile.name.endsWith(".apk", ignoreCase = true)
            val isAab = finalFile.name.endsWith(".aab", ignoreCase = true)

            onLog(
                LogCategory.BUILD,
                LogLevel.SUCCESS,
                "Artefacto listo",
                "Extraído ${finalFile.name} (${finalFile.length() / 1024} KB)"
            )

            DownloadedArtifact(
                artifactId = artifact.id,
                artifactName = artifact.name,
                localFilePath = finalFile.absolutePath,
                fileName = finalFile.name,
                sizeBytes = finalFile.length(),
                isApk = isApk,
                isAab = isAab
            )
        }.onFailure {
            onLog(LogCategory.BUILD, LogLevel.ERROR, "Descarga", it.message ?: "Error al descargar")
        }
    }

    suspend fun fetchRepoFiles(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        onProgress: (String) -> Unit
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress("Leyendo árbol de archivos de $owner/$repo...")
            val treeRaw = executeJson(
                token,
                "https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1"
            ).getOrThrow().second
            val treeArr = JSONObject(treeRaw).getJSONArray("tree")

            val textExtensions = setOf(
                "kt", "kts", "java", "xml", "html", "htm", "css", "js", "ts", "json",
                "yml", "yaml", "md", "txt", "properties", "toml", "gradle", "gitignore", "svg"
            )
            val blobsToFetch = mutableListOf<Pair<String, String>>()
            for (i in 0 until treeArr.length()) {
                val item = treeArr.getJSONObject(i)
                if (item.getString("type") == "blob") {
                    val path = item.getString("path")
                    val ext = path.substringAfterLast('.', "").lowercase()
                    val size = item.optLong("size", 0L)
                    if (size <= 250_000 && (ext in textExtensions || path.endsWith("gradlew"))) {
                        blobsToFetch.add(path to item.getString("sha"))
                    }
                }
            }

            val result = linkedMapOf<String, String>()
            blobsToFetch.take(60).forEachIndexed { idx, (path, sha) ->
                onProgress("Descargando ${idx + 1}/${blobsToFetch.size.coerceAtMost(60)}: $path")
                val blobJson = JSONObject(
                    executeJson(
                        token,
                        "https://api.github.com/repos/$owner/$repo/git/blobs/$sha"
                    ).getOrThrow().second
                )
                val encoded = blobJson.getString("content").replace("\n", "")
                val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                result[path] = decoded
            }
            onLog(
                LogCategory.GITHUB_API,
                LogLevel.SUCCESS,
                "Clonar Repo",
                "Importados ${result.size} archivos desde $owner/$repo ($branch)"
            )
            result
        }
    }

    private fun parseRepo(obj: JSONObject): GitHubRepo {
        val ownerObj = obj.getJSONObject("owner")
        return GitHubRepo(
            id = obj.getLong("id"),
            name = obj.getString("name"),
            fullName = obj.getString("full_name"),
            ownerLogin = ownerObj.getString("login"),
            defaultBranch = obj.optString("default_branch", "main"),
            isPrivate = obj.optBoolean("private", false),
            htmlUrl = obj.optString("html_url"),
            description = obj.optString("description").takeIf { it != "null" }.orEmpty()
        )
    }
}
