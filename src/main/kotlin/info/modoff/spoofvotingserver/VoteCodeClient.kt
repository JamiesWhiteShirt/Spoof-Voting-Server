package info.modoff.spoofvotingserver

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.DataOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class VoteCodeClient(val url: URL, val secret: String, val timeout: Int, val userAgent: String) {
    @Throws(VoteCodeException::class)
    fun getSync(uuid: UUID): String {
        val urlParameters: String
        try {
            urlParameters = "secret=" + URLEncoder.encode(secret, "UTF-8") + "&uuid=" + URLEncoder.encode(uuid.toString(), "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            // This should never happen
            throw VoteCodeException("Unsupported encoding", e)
        }

        val postData = urlParameters.toByteArray(StandardCharsets.UTF_8)

        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = timeout
            connection.doOutput = true
            connection.instanceFollowRedirects = true
            connection.requestMethod = "POST"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("charset", "utf-8")
            connection.setRequestProperty("Content-Length", Integer.toString(postData.size))
            connection.useCaches = false

            val wr = DataOutputStream(connection.outputStream)
            wr.write(postData)

            val mapper = ObjectMapper()
            val response = mapper.readTree(connection.inputStream)
            if (response.isObject) {
                val success = response["success"]
                if (success.isBoolean) {
                    if (success.booleanValue()) {
                        val code = response["code"]
                        if (code.isTextual) {
                            return code.textValue()
                        }
                    }

                    val message = response["message"]
                    if (message.isTextual) {
                        throw VoteCodeException("Request was unsuccessful (${message.textValue()})")
                    } else {
                        throw VoteCodeException("Request was unsuccessful (no message)")
                    }
                } else {
                    throw VoteCodeException("Response is malformed")
                }
            } else {
                throw VoteCodeException("Response is malformed")
            }
        } catch (e: IOException) {
            throw VoteCodeException("IO error (" + e.message + ")", e)
        }
    }
}
