package org.nova.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "reactions", primaryKeys = ["message_id", "is_mms"])
data class Reaction(
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "is_mms") val isMms: Boolean,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "emoji") val emoji: String
)
