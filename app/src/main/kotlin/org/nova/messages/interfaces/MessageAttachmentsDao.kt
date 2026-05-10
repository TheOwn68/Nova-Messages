package org.nova.messages.interfaces

import androidx.room.Dao
import androidx.room.Query
import org.nova.messages.models.MessageAttachment

@Dao
interface MessageAttachmentsDao {
    @Query("SELECT * FROM message_attachments")
    fun getAll(): List<MessageAttachment>
}
