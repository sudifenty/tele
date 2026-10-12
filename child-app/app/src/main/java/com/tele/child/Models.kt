package com.tele.child

data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val durationSec: Long,
    val ts: Long,
    /** incoming | outgoing | missed | rejected | blocked | voicemail | other */
    val type: String
)

data class SmsEntry(
    val id: Long,
    val address: String,
    val name: String?,
    val ts: Long,
    /** inbox | sent */
    val folder: String,
    /** Null unless Tele is the default SMS app. */
    val body: String?,
    val bodyVisible: Boolean
)
