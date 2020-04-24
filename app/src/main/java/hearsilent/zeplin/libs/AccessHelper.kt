package hearsilent.zeplin.libs

import android.content.Context
import android.text.TextUtils
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import hearsilent.zeplin.callback.ScreenCallback
import hearsilent.zeplin.callback.TokenCallback
import hearsilent.zeplin.models.ScreenModel
import hearsilent.zeplin.models.TokenModel
import okhttp3.*
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

object AccessHelper {

    const val ZEPLIN_AUTHORIZE_URL =
        "https://api.zeplin.dev/v1/oauth/authorize?client_id=${Constant.CLIENT_ID}&redirect_uri=${Constant.REDIRECT_URI}&response_type=code"
    private const val ZEPLIN_OAUTH_ACCESS_URL =
        "https://api.zeplin.dev/v1/oauth/token"
    private const val ZEPLIN_SCREEN_URL = "https://api.zeplin.dev/v1/projects/%s/screens/%s"

    private var mClient: OkHttpClient = init()

    private fun init(): OkHttpClient {
        return OkHttpClient().newBuilder().followRedirects(true).followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS).build()
    }

    private fun getOauthToken(context: Context): String? {
        return Memory.getObject(
            context,
            Constant.PREF_ZEPLIN_TOKEN,
            TokenModel::class.java
        )?.access_token
    }

    fun zeplinOauth(code: String, callback: TokenCallback) {
        val builder = FormBody.Builder()
        builder.add("grant_type", "authorization_code")
        builder.add("code", code)
        builder.add("redirect_uri", Constant.REDIRECT_URI)
        builder.add("client_id", Constant.CLIENT_ID)
        builder.add("client_secret", Constant.CLIENT_SECRET)

        val request: Request = Request.Builder()
            .url(ZEPLIN_OAUTH_ACCESS_URL).post(builder.build())
            .build()

        mClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onFail(e.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBodyCopy = response.peekBody(Long.MAX_VALUE)
                    val body = responseBodyCopy.string()
                    val model = jacksonObjectMapper().readerFor(TokenModel::class.java)
                        .readValue<TokenModel>(body)
                    callback.onSuccess(model)
                } catch (e: Exception) {
                    callback.onFail(e.toString())
                }
            }
        })
    }

    fun getScreen(context: Context, pid: String, sid: String, callback: ScreenCallback) {
        val token = getOauthToken(context)
        if (TextUtils.isEmpty(token)) {
            callback.onFail("Token is empty.")
            return
        }

        val url = String.format(Locale.getDefault(), ZEPLIN_SCREEN_URL, pid, sid)
        val request: Request =
            Request.Builder().header("authorization", "Bearer $token").url(url).get().build()

        mClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onFail(e.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBodyCopy = response.peekBody(Long.MAX_VALUE)
                    val body = responseBodyCopy.string()
                    val model = jacksonObjectMapper().readerFor(ScreenModel::class.java)
                        .readValue<ScreenModel>(body)
                    callback.onSuccess(model)
                } catch (e: Exception) {
                    callback.onFail(e.toString())
                }
            }
        })
    }

}