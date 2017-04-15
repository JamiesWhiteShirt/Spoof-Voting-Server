package info.modoff.spoofvotingserver.models

data class Players(
        val max: Int,
        val online: Int
)

data class Version(
        val name: String,
        val protocol: Int
)

data class ServerStatus(
        val description: String,
        val players: Players,
        val version: Version
)

interface ITextComponent {
    val text: String
    val italic: Boolean
    val bold: Boolean
}

data class TextComponent(
        override val text: String,
        override val italic: Boolean = false,
        override val bold: Boolean = false
) : ITextComponent

data class TextComponentSiblings(
        override val text: String,
        override val italic: Boolean = false,
        override val bold: Boolean = false,
        val extra: List<ITextComponent> = emptyList()
) : ITextComponent

data class UserSession(
        val id: String,
        val name: String,
        val properties: List<Property>
) {
    data class Property(
            val name: String,
            val value: String,
            val signature: String
    )
}
