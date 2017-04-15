package info.modoff.spoofvotingserver

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import info.modoff.spoofvotingserver.models.*
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed class ConnectionState {
    object Handshaking : ConnectionState() {
        override val name get() = "Handshaking"

        override fun next(server: SpoofVotingServer, id: Int, input: DataInputStream, output: DataOutputStream): ConnectionState? {
            return when (id) {
                0 -> { //handshake
                    val protocolVersion = input.readVarInt()
                    val ipString = input.readString(255)
                    val port = input.readUnsignedShort()
                    val requestedState = input.readVarInt()
                    val hasFMLMarker = ipString.contains("\u0000FML\u0000")
                    val ip = ipString.split("\u0000FML\u0000")[0]

                    when (requestedState) {
                        -1 -> this
                        1 -> Status
                        2 -> Login.Hello
                        else -> null
                    }
                }
                else -> null
            }
        }
    }

    object Status : ConnectionState() {
        override val name: String get() = "Status"

        override fun next(server: SpoofVotingServer, id: Int, input: DataInputStream, output: DataOutputStream): ConnectionState? {
            return when (id) {
                0 -> { //serverquery
                    output.writeByteArray(ByteArrayOutputStream().apply {
                        DataOutputStream(this).apply {
                            writeVarInt(0) //serverinfo
                            val serverStatus = ServerStatus(server.name, Players(404, 404), Version(server.version, server.protocolVersion))
                            val mapper = jacksonObjectMapper()
                            writeString(mapper.writeValueAsString(serverStatus))
                        }
                    }.toByteArray())
                    this
                }
                1 -> { //ping
                    val clientTime = input.readLong()
                    output.writeByteArray(ByteArrayOutputStream().apply {
                        DataOutputStream(this).apply {
                            writeVarInt(1) //pong
                            writeLong(clientTime)
                        }
                    }.toByteArray())
                    this
                }
                else -> null
            }
        }
    }

    sealed class Login(val verifyToken: ByteArray) : ConnectionState() {
        override val name: String get() = "Login(${verifyToken.joinToString(", ")}).$subName"

        object Hello : Login(ByteArray(4).apply { Random().nextBytes(this) }) {
            override val subName: String get() = "Hello"

            override fun next(server: SpoofVotingServer, id: Int, input: DataInputStream, output: DataOutputStream): ConnectionState? {
                return when (id) {
                    0 -> { //login start
                        val username = input.readString(16)
                        output.writeByteArray(ByteArrayOutputStream().apply {
                            DataOutputStream(this).apply {
                                writeVarInt(1) //encryptionrequest
                                writeString("")
                                writeByteArray(server.keyPair.public.encoded)
                                writeByteArray(verifyToken)
                            }
                        }.toByteArray())
                        Key(verifyToken, username)
                    }
                    else -> null
                }
            }
        }

        class Key(verifyToken: ByteArray, val username: String) : Login(verifyToken) {
            companion object {
                fun textComponentForVotingCode(code: String) = TextComponentSiblings(
                        text = "Your secret voting code: ",
                        extra = listOf(
                                TextComponent(text = code, bold = true),
                                TextComponent(text = "\n\n"),
                                TextComponent(text = "Do not share your voting code with anyone", italic = true)
                        )
                )

                fun textComponentForError(e: VoteCodeException) = TextComponentSiblings(
                        text = "Something went wrong processing your vote. Try again later or poke a team member.\n\n",
                        extra = listOf(
                                TextComponent(text = "Error: ${e.message}", italic = true)
                        )
                )
            }

            override val subName: String get() = "Key($username)"

            override fun next(server: SpoofVotingServer, id: Int, input: DataInputStream, output: DataOutputStream): ConnectionState? {
                return when (id) {
                    1 -> { //encryptionresponse
                        val secretKeyEncrypted = input.readByteArray()
                        val verifyTokenEncrypted = input.readByteArray()

                        val privateKey = server.keyPair.private
                        val decryptedVerifyToken = Cipher.getInstance(privateKey.algorithm).apply {
                            init(2, privateKey)
                        }.doFinal(verifyTokenEncrypted)
                        if (Arrays.equals(verifyToken, decryptedVerifyToken)) {
                            val secretKey = SecretKeySpec(Cipher.getInstance(privateKey.algorithm).apply {
                                init(2, privateKey)
                            }.doFinal(secretKeyEncrypted), "AES")

                            val hash = BigInteger(MessageDigest.getInstance("SHA-1").apply {
                                update("".toByteArray(Charsets.ISO_8859_1))
                                update(secretKey.encoded)
                                update(server.keyPair.public.encoded)
                            }.digest()).toString(16)
                            val usernameParam = URLEncoder.encode(username, "UTF-8")
                            val httpConnection = URL("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=$usernameParam&serverId=$hash").openConnection() as HttpURLConnection
                            httpConnection.requestMethod = "GET"

                            val mapper = jacksonObjectMapper()
                            try {
                                val userSession: UserSession = mapper.readValue(httpConnection.inputStream)
                                println("Successfully authenticated user $username")

                                val cipher = Cipher.getInstance("AES/CFB8/NoPadding").apply {
                                    init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(secretKey.encoded))
                                }

                                DataOutputStream(CipherOutputStream(output, cipher)).apply {
                                    writeByteArray(ByteArrayOutputStream().apply {
                                        DataOutputStream(this).apply {
                                            writeVarInt(0) //disconnect
                                            val disconnectMessage = try {
                                                val uuid = UUID(
                                                        BigInteger(userSession.id.substring(0..15), 16).toLong(),
                                                        BigInteger(userSession.id.substring(16..31), 16).toLong()
                                                )
                                                val votingCode = server.voteCodeClient.getSync(uuid)
                                                textComponentForVotingCode(votingCode)
                                            } catch (e: VoteCodeException) {
                                                println("Error getting vote code for player $username")
                                                e.printStackTrace()
                                                textComponentForError(e)
                                            }

                                            writeString(mapper.writeValueAsString(disconnectMessage))
                                        }
                                    }.toByteArray())
                                }
                            } catch (e: IOException) {
                                //The session is invalid. uh oh!
                                println("Failed to authenticate user $username")
                            }
                        }
                        null
                    }
                    else -> {
                        null
                    }
                }
            }
        }

        abstract val subName: String
    }

    abstract val name: String

    abstract fun next(server: SpoofVotingServer, id: Int, input: DataInputStream, output: DataOutputStream): ConnectionState?
}