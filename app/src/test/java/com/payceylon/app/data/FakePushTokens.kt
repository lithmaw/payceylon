package com.payceylon.app.data

/** Hands out "token-1", "token-2", … ; deleting moves on to the next, like FCM. */
class FakePushTokens : PushTokens {
    private var serial = 1
    var deleted = 0

    override suspend fun current(): String = "token-$serial"

    override suspend fun delete() {
        deleted++
        serial++
    }
}