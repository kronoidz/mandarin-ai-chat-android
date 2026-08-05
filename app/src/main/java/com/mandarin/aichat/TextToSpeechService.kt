package com.mandarin.aichat

import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Google Cloud Text-to-Speech service using service account authentication.
 *
 * Credentials are loaded from BuildConfig.GOOGLE_TTS_CREDENTIALS which is populated at build time
 * from a service-account JSON key file pointed to by the GOOGLE_TTS_CREDENTIALS_PATH property in
 * local.properties.
 */
object TextToSpeechService {

    private const val TAG = "TTS"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val TTS_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"
    private const val SCOPE = "https://www.googleapis.com/auth/cloud-platform"

    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

    /** Returns true when credentials have been configured. */
    val isAvailable: Boolean
        get() = BuildConfig.GOOGLE_TTS_CREDENTIALS.isNotEmpty()

    /**
     * Synthesizes speech for [text] in Mandarin Chinese and returns raw MP3 audio bytes. Call from
     * a coroutine on a background dispatcher.
     */
    suspend fun synthesize(text: String): ByteArray =
            withContext(Dispatchers.IO) {
                val credentialsJson = BuildConfig.GOOGLE_TTS_CREDENTIALS
                if (credentialsJson.isEmpty()) {
                    throw IllegalStateException(
                            "Google TTS credentials not configured. " +
                                    "Set GOOGLE_TTS_CREDENTIALS_PATH in local.properties."
                    )
                }

                val credentials = JSONObject(credentialsJson as String)
                val accessToken = fetchAccessToken(credentials)
                synthesizeWithToken(accessToken, text)
            }

    private fun fetchAccessToken(credentials: JSONObject): String {
        val clientEmail = credentials.getString("client_email")
        val privateKey = credentials.getString("private_key")
        val tokenUri = credentials.optString("token_uri", TOKEN_URL)

        val jwt = createJwt(clientEmail, privateKey, tokenUri)
        val jwtAssertion = buildJwtAssertion(jwt)

        val body =
                ("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer" +
                                "&assertion=${jwtAssertion}")
                        .toRequestBody("application/x-www-form-urlencoded".toMediaType())

        val request = Request.Builder().url(tokenUri).post(body).build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            Log.e(TAG, "Token exchange failed: $responseBody")
            throw RuntimeException("OAuth token exchange failed: ${response.code}")
        }

        return JSONObject(responseBody).getString("access_token")
    }

    /**
     * Creates a signed JWT containing header, claim set, and signature. Returns a triple of
     * (header, payload, signature) – all base64url-encoded.
     */
    private fun createJwt(
            clientEmail: String,
            privateKeyPem: String,
            tokenUri: String
    ): Triple<String, String, String> {
        val now = System.currentTimeMillis() / 1000

        val header =
                JSONObject().apply {
                    put("alg", "RS256")
                    put("typ", "JWT")
                }

        val claimSet =
                JSONObject().apply {
                    put("iss", clientEmail)
                    put("scope", SCOPE)
                    put("aud", tokenUri)
                    put("exp", now + 3600)
                    put("iat", now)
                }

        val headerB64 = base64UrlEncode(header.toString().toByteArray())
        val payloadB64 = base64UrlEncode(claimSet.toString().toByteArray())
        val signingInput = "$headerB64.$payloadB64"

        val signature = signRsa256(signingInput.toByteArray(), privateKeyPem)
        val signatureB64 = base64UrlEncode(signature)

        return Triple(headerB64, payloadB64, signatureB64)
    }

    private fun buildJwtAssertion(jwt: Triple<String, String, String>): String {
        return "${jwt.first}.${jwt.second}.${jwt.third}"
    }

    /** Signs data using RS256 with the PEM-encoded private key from the service account. */
    private fun signRsa256(data: ByteArray, privateKeyPem: String): ByteArray {
        // Strip PEM header/footer and newlines
        val keyContent =
                privateKeyPem
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("\\n", "")
                        .replace("\n", "")
                        .trim()

        val keyBytes = Base64.decode(keyContent, Base64.DEFAULT)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(spec)

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    private fun synthesizeWithToken(accessToken: String, text: String): ByteArray {
        val bodyJson =
                JSONObject().apply {
                    put("input", JSONObject().apply { put("text", text) })
                    put(
                            "voice",
                            JSONObject().apply {
                                put("languageCode", "cmn-CN")
                                put("name", "cmn-CN-Chirp3-HD-Puck")
                            }
                    )
                    put("audioConfig", JSONObject().apply { put("audioEncoding", "MP3") })
                }

        val body = bodyJson.toString().toRequestBody("application/json".toMediaType())

        val request =
                Request.Builder()
                        .url(TTS_URL)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(body)
                        .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            Log.e(TAG, "TTS synthesis failed: $responseBody")
            throw RuntimeException("TTS synthesis failed: ${response.code}")
        }

        val audioBase64 = JSONObject(responseBody).getString("audioContent")
        return Base64.decode(audioBase64, Base64.DEFAULT)
    }
}
