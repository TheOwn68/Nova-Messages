package org.nova.messages.interfaces

import androidx.room.Dao
import androidx.room.Query
import org.nova.messages.models.Attachment

@Dao
interface AttachmentsDao {
    @Query("SELECT * FROM attachments")
    fun getAll(): List<Attachment>
}
