package eu.kanade.tachiyomi.data.sync

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Inject
@SingleIn(AppScope::class)
class OneDriveSyncClient(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val initializationMutex = Mutex()
    private val httpClient = OkHttpClient()

    @Volatile
    private var application: ISingleAccountPublicClientApplication? = null

    /**
     * MSAL redirect URI of this installation: `msauth://<package name>/<URL-encoded signature hash>`.
     *
     * It is derived from the installed package instead of being hard-coded, so debug and release
     * builds (different application IDs and signing keys) both work. Every value must also be
     * registered in the Azure app registration.
     */
    val redirectUri: String by lazy {
        "msauth://${applicationContext.packageName}/${URLEncoder.encode(signatureHash(), "UTF-8")}"
    }

    suspend fun currentAccount(): OneDriveAccount? {
        val account = withContext(Dispatchers.IO) {
            getApplication().currentAccount.currentAccount
        }
        return account?.toOneDriveAccount()
    }

    suspend fun signIn(activity: Activity): OneDriveAccount {
        val application = getApplication()
        currentAccount()?.let { return it }

        return suspendCoroutine { continuation ->
            val parameters = SignInParameters.builder()
                .withActivity(activity)
                .withScopes(SCOPES)
                .withCallback(
                    object : AuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            continuation.resume(authenticationResult.account.toOneDriveAccount())
                        }

                        override fun onError(exception: MsalException) {
                            continuation.resumeWithException(
                                OneDriveSyncException("Microsoft sign-in failed", exception),
                            )
                        }

                        override fun onCancel() {
                            continuation.resumeWithException(
                                OneDriveSyncException("Microsoft sign-in was canceled"),
                            )
                        }
                    },
                )
                .build()
            application.signIn(parameters)
        }
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            getApplication().signOut()
        }
    }

    suspend fun readDocument(fileName: String = LIBRARY_FILE): OneDriveSyncDocument {
        val accessToken = acquireAccessToken()
        val request = Request.Builder()
            .url(fileUrl(fileName))
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.newCall(request).await().use { response ->
            when {
                response.code == 403 || response.code == 404 ->
                    OneDriveSyncDocument(contents = null, etag = null)
                !response.isSuccessful -> throw response.toGraphException("download", fileName)
                else -> OneDriveSyncDocument(
                    contents = response.body.string(),
                    etag = response.header("ETag"),
                )
            }
        }
    }

    /**
     * Writes [contents] to [fileName]. With [etag] the write only succeeds if the file did not
     * change since it was read; without it, only if the file does not exist yet.
     */
    suspend fun writeDocument(fileName: String = LIBRARY_FILE, contents: String, etag: String?) {
        val accessToken = acquireAccessToken()
        val request = Request.Builder()
            .url(fileUrl(fileName))
            .header("Authorization", "Bearer $accessToken")
            .apply {
                if (etag != null) {
                    header("If-Match", etag)
                } else {
                    header("If-None-Match", "*")
                }
            }
            .put(contents.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).await().use { response ->
            when {
                response.code == 412 -> throw OneDriveSyncConflictException(
                    "The OneDrive sync file changed on another device. Sync again to merge the latest copy.",
                )
                !response.isSuccessful -> throw response.toGraphException("upload", fileName)
            }
        }
    }

    /** The bytes of [fileName], null if there is none; [onProgress] gets the bytes read and the size (-1 if unknown). */
    suspend fun readBytes(fileName: String, onProgress: ((read: Long, size: Long) -> Unit)? = null): ByteArray? {
        val accessToken = acquireAccessToken()
        val request = Request.Builder()
            .url(fileUrl(fileName))
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.newCall(request).await().use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful -> throw response.toGraphException("download", fileName)
                onProgress == null -> response.body.bytes()
                else -> readWithProgress(response.body, onProgress)
            }
        }
    }

    private fun readWithProgress(body: ResponseBody, onProgress: (read: Long, size: Long) -> Unit): ByteArray {
        val size = body.contentLength()
        val out = ByteArrayOutputStream(if (size in 1..Int.MAX_VALUE) size.toInt() else DEFAULT_BUFFER_SIZE)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
        var read = 0L
        onProgress(0, size)
        body.byteStream().use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
                read += count
                onProgress(read, size)
            }
        }
        return out.toByteArray()
    }

    /** ETag of [path] in the app folder, null if there is no such file. */
    suspend fun eTag(path: String): String? {
        val accessToken = acquireAccessToken()
        val request = Request.Builder()
            .url("${itemUrl(path)}?\$select=eTag")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.newCall(request).await().use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful -> throw response.toGraphException("read", path)
                else -> runCatching { JSONObject(response.body.string()).optString("eTag") }
                    .getOrNull()
                    ?.takeIf(String::isNotBlank)
            }
        }
    }

    /** Uploads [bytes] to [fileName], replacing any existing file. */
    suspend fun writeBytes(fileName: String, bytes: ByteArray) {
        val accessToken = acquireAccessToken()
        val request = Request.Builder()
            .url(fileUrl(fileName))
            .header("Authorization", "Bearer $accessToken")
            .put(bytes.toRequestBody(BINARY_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw response.toGraphException("upload", fileName)
        }
    }

    suspend fun deleteFile(fileName: String) {
        val accessToken = acquireAccessToken()
        val request = Request.Builder()
            .url(fileUrl(fileName).removeSuffix(":/content"))
            .header("Authorization", "Bearer $accessToken")
            .delete()
            .build()

        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful && response.code != 404) throw response.toGraphException("delete", fileName)
        }
    }

    /** Lists the files in the app folder whose name starts with [prefix]. */
    suspend fun listFiles(prefix: String): List<OneDriveFile> {
        val accessToken = acquireAccessToken()
        val files = mutableListOf<OneDriveFile>()
        var url: String? = "$APP_FOLDER_URL/children?\$select=name,size,lastModifiedDateTime&\$top=200"
        while (url != null) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            url = httpClient.newCall(request).await().use { response ->
                if (response.code == 404) return files
                if (!response.isSuccessful) throw response.toGraphException("list", "app folder")
                val json = JSONObject(response.body.string())
                val items = json.optJSONArray("value")
                for (index in 0..<(items?.length() ?: 0)) {
                    val item = items!!.getJSONObject(index)
                    val name = item.optString("name")
                    if (!name.startsWith(prefix)) continue
                    files += OneDriveFile(
                        name = name,
                        size = item.optLong("size"),
                        lastModified = item.optString("lastModifiedDateTime")
                            .takeIf(String::isNotBlank)
                            ?.let { runCatching { kotlin.time.Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
                            ?: 0L,
                    )
                }
                json.optString("@odata.nextLink").takeIf(String::isNotBlank)
            }
        }
        return files
    }

    private suspend fun acquireAccessToken(): String {
        val application = getApplication()
        val account = withContext(Dispatchers.IO) {
            application.currentAccount.currentAccount
        } ?: throw OneDriveSyncException("Sign in to OneDrive before syncing")

        return try {
            withContext(Dispatchers.IO) {
                application.acquireTokenSilent(
                    AcquireTokenSilentParameters.Builder()
                        .withScopes(SCOPES)
                        .forAccount(account)
                        .fromAuthority(account.authority)
                        .build(),
                ).accessToken
            }
        } catch (e: MsalUiRequiredException) {
            throw OneDriveSyncAuthException("Microsoft sign-in has expired. Sign in again.", e)
        } catch (e: MsalException) {
            throw OneDriveSyncException("Could not get a Microsoft sign-in token: ${e.message}", e)
        }
    }

    private suspend fun getApplication(): ISingleAccountPublicClientApplication {
        application?.let { return it }
        return initializationMutex.withLock {
            application ?: try {
                withContext(Dispatchers.IO) {
                    PublicClientApplication.createSingleAccountPublicClientApplication(
                        applicationContext,
                        writeConfigFile(),
                    )
                }.also { application = it }
            } catch (e: MsalException) {
                throw OneDriveSyncException("Could not initialize Microsoft sign-in", e)
            }
        }
    }

    /**
     * Writes the MSAL configuration from `res/raw/auth_config_single_account.json`, with
     * [redirectUri] filled in for this installation.
     */
    private fun writeConfigFile(): File {
        val template = applicationContext.resources.openRawResource(R.raw.auth_config_single_account)
            .bufferedReader()
            .use { it.readText() }
        val config = JSONObject(template).put("redirect_uri", redirectUri)
        return File(applicationContext.noBackupFilesDir, "msal_auth_config.json")
            .apply { writeText(config.toString()) }
    }

    @Suppress("DEPRECATION")
    private fun signatureHash(): String {
        val packageManager = applicationContext.packageManager
        val packageName = applicationContext.packageName
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val signature = signatures?.firstOrNull()
            ?: throw OneDriveSyncException("Could not read the app signing certificate")
        val digest = MessageDigest.getInstance("SHA").digest(signature.toByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun IAccount.toOneDriveAccount() = OneDriveAccount(
        id = id,
        name = username.ifBlank { id },
    )

    private fun fileUrl(fileName: String): String {
        return "${itemUrl(fileName)}:/content"
    }

    /** [path] may name a file in a subfolder, like the EhViewer files under `ehviewer/`. */
    private fun itemUrl(path: String): String {
        val encoded = path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return "$APP_FOLDER_URL:/$encoded"
    }

    private fun Response.toGraphException(operation: String, fileName: String): OneDriveSyncException {
        val graphError = runCatching {
            JSONObject(body.string()).optJSONObject("error")
        }.getOrNull()
        val graphCode = graphError?.optString("code")?.takeIf(String::isNotBlank)
        val graphMessage = graphError?.optString("message")?.takeIf(String::isNotBlank)
        val details = listOfNotNull(graphCode, graphMessage).joinToString(": ")
        val message = buildString {
            append("Could not $operation $fileName on OneDrive (HTTP $code)")
            if (details.isNotBlank()) append(": $details")
        }
        Log.e("OneDriveSync", message)
        return OneDriveSyncException(message)
    }

    companion object {
        /** Library membership, see [LibrarySyncService]. */
        const val LIBRARY_FILE = "mihon-library-sync.json"

        private val SCOPES = listOf("Files.ReadWrite")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val BINARY_MEDIA_TYPE = "application/octet-stream".toMediaType()
        private const val APP_FOLDER_URL = "https://graph.microsoft.com/v1.0/me/drive/special/approot"
    }
}

data class OneDriveAccount(
    val id: String,
    val name: String,
)

data class OneDriveFile(
    val name: String,
    val size: Long,
    /** Epoch milliseconds, 0 if unknown. */
    val lastModified: Long,
)

data class OneDriveSyncDocument(
    val contents: String?,
    val etag: String?,
)

open class OneDriveSyncException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Another device wrote the sync file between our read and write (HTTP 412). */
class OneDriveSyncConflictException(message: String) : OneDriveSyncException(message)

/** Signing in again needs the user; background syncs stop retrying. */
class OneDriveSyncAuthException(message: String, cause: Throwable? = null) : OneDriveSyncException(message, cause)
