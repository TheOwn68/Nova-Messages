package org.nova.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.nova.messages.models.Reaction

@Dao
interface ReactionsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(reaction: Reaction)

    @Query("SELECT * FROM reactions WHERE thread_id = :threadId")
    fun getThreadReactions(threadId: Long): List<Reaction>

    @Query("DELETE FROM reactions WHERE message_id = :messageId AND is_mms = :isMms")
    fun deleteReaction(messageId: Long, isMms: Boolean)
}
