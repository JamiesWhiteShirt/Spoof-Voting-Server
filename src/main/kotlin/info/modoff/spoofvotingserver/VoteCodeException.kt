package info.modoff.spoofvotingserver

class VoteCodeException : Exception {
    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)
}
