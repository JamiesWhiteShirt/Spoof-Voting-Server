package info.modoff.spoofvotingserver

import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.inf.ArgumentParserException
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.URL
import java.security.KeyPairGenerator

fun main(args: Array<String>) {
    val parser = ArgumentParsers.newArgumentParser("spoof-voting-server").apply {
        description("Run a spoof minecraft server that kicks players with a voting code")
        addArgument("url").apply {
            help("URL to the vote code API")
        }
        addArgument("secret").apply {
            help("API secret")
        }
        addArgument("-u", "--userAgent").apply {
            help("Request user agent")
            default = "MC-SPOOF"
        }
        addArgument("-t", "--timeout").apply {
            type(Integer::class.java)
            help("Timeout for the API request in milliseconds")
            default = 2000
        }
        addArgument("-m", "--motd").apply {
            help("Minecraft server MOTD")
            default = "Connect to get your voting code using any version of Minecraft!"
        }
        addArgument("-v", "--version").apply {
            help("Minecraft version")
            default = "1.11"
        }
        addArgument("-r", "--protocol").apply {
            type(Integer::class.java)
            help("Minecraft protocol version")
            default = 316
        }
        addArgument("-p", "--port").apply {
            type(Integer::class.java)
            help("Minecraft server port")
            default = 25565
        }
    }

    try {
        val result = parser.parseArgs(args)
        val voteCodeClient = VoteCodeClient(URL(result.getString("url")), result.getString("secret"), result.getInt("timeout"), result.getString("userAgent"))
        val server = SpoofVotingServer(result.getInt("port"), result.getString("motd"), result.getString("version"), result.getInt("protocol"), voteCodeClient)
        server.listen()
    } catch (e: ArgumentParserException) {
        parser.handleError(e)
    }
}

class SpoofVotingServer(port: Int, val name: String, val version: String, val protocolVersion: Int, val voteCodeClient: VoteCodeClient) {
    private val serverSocket = ServerSocket(port)
    val keyPair = KeyPairGenerator.getInstance("RSA").let {
        it.initialize(1024)
        it.generateKeyPair()
    }

    fun listen() {
        var idCounter = 0
        while (true) {
            val socket = serverSocket.accept()
            val id = idCounter++
            Thread({
                println("Connected ($id)")
                val inputStream = DataInputStream(socket.getInputStream())
                val outputStream = DataOutputStream(socket.getOutputStream())
                var connectionState: ConnectionState? = ConnectionState.Handshaking

                val disconnectMessage = try {
                    while (connectionState != null) {
                        val input = DataInputStream(ByteArrayInputStream(inputStream.readByteArray()))
                        val packetId = input.readVarInt()
                        println("Received ${connectionState.name} packet with ID $packetId")
                        connectionState = connectionState.next(this, packetId, input, outputStream)
                    }
                    "Closed by server"
                } catch (e: IOException) {
                    "Closed by client"
                }
                println("Disconnected: $disconnectMessage ($id)")
            }).run()
        }
    }
}
