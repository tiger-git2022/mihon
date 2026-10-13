package eu.kanade.tachiyomi.data.sync

import android.app.Activity
import android.content.Context
import android.util.Log
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException
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
import org.json.JSONObject
import java.io.IOException
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

    suspend fun readDocument(): OneDriveSyncDocument {
        val accessToken = acquireAccessToken()
        val request = Request.Builder()
            .url(SYNC_FILE_URL)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.newCall(request).await().use { response ->
            when {
                response.code == 403 || response.code == 404 ->
                    OneDriveSyncDocument(contents = null, etag = null)
                !response.isSuccessful -> throw response.toGraphException("download")
                else -> OneDriveSyncDocument(
                    contents = response.body.string(),
                    etag = response.header("ETag"),
                )
            }
        }
    }

    suspend fun writeDocument(contents: String, etag: String?) {
        val accessToken = acquireAccessToken()
        val request = Request.Builder()
            .url(SYNC_FILE_URL)
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
                response.code == 412 -> throw OneDriveSyncException(
                    "The OneDrive sync file changed on another device. Sync again to merge the latest copy.",
                )
                !response.isSuccessful -> throw response.toGraphException("upload")
            }
        }
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
        } catch (e: MsalException) {
            throw OneDriveSyncException("Microsoft sign-in has expired. Sign in again.", e)
        }
    }

    private suspend fun getApplication(): ISingleAccountPublicClientApplication {
        application?.let { return it }
        return initializationMutex.withLock {
            application ?: try {
                withContext(Dispatchers.IO) {
                    PublicClientApplication.createSingleAccountPublicClientApplication(
                        applicationContext,
                        R.raw.auth_config_single_account,
                    )
                }.also { application = it }
            } catch (e: MsalException) {
                throw OneDriveSyncException("Could not initialize Microsoft sign-in", e)
            }
        }
    }

    private fun IAccount.toOneDriveAccount() = OneDriveAccount(
        id = id,
        name = username.ifBlank { id },
    )

    private fun Response.toGraphException(operation: String): OneDriveSyncException {
        val graphError = runCatching {
            JSONObject(body.string()).optJSONObject("error")
        }.getOrNull()
        val graphCode = graphError?.optString("code")?.takeIf(String::isNotBlank)
        val graphMessage = graphError?.optString("message")?.takeIf(String::isNotBlank)
        val details = listOfNotNull(graphCode, graphMessage).joinToString(": ")
        val message = buildString {
            append("Could not $operation the OneDrive sync file (HTTP $code)")
            if (details.isNotBlank()) append(": $details")
        }
        Log.e("OneDriveSync", message)
        return OneDriveSyncException(message)
    }

    private companion object {
        val SCOPES = listOf("Files.ReadWrite")
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val SYNC_FILE_URL =
            "https://graph.microsoft.com/v1.0/me/drive/special/approot:/mihon-library-sync.json:/content"
    }
}

data class OneDriveAccount(
    val id: String,
    val name: String,
)

data class OneDriveSyncDocument(
    val contents: String?,
    val etag: String?,
)

class OneDriveSyncException(message: String, cause: Throwable? = null) : IOException(message, cause)
