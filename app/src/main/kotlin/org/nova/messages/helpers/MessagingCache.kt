package org.nova.messages.helpers

import android.util.LruCache
import android.content.Context
import org.fossify.commons.models.SimpleContact
import org.nova.messages.models.NamePhoto
import org.nova.messages.models.Conversation
import org.nova.messages.extensions.conversationsDB

private const val CACHE_SIZE = 128

object MessagingCache {
    val namePhoto = LruCache<String, NamePhoto>(CACHE_SIZE)
    val participantsCache = LruCache<Long, ArrayList<SimpleContact>>(CACHE_SIZE)
    val conversationCache = LruCache<Long, Conversation>(CACHE_SIZE)
    val addressIdCache = LruCache<Int, String>(CACHE_SIZE)
    
    // Key is "id_isMms" (e.g., "123_false" for SMS, "456_true" for MMS)
    val reactionsCache = HashMap<String, String>() 

    fun getReactionKey(id: Long, isMms: Boolean) = "${id}_$isMms"

    fun getConversation(context: Context, threadId: Long): Conversation? {
        conversationCache.get(threadId)?.let { return it }
        val conversation = context.conversationsDB.getConversationWithThreadId(threadId)
        if (conversation != null) {
            conversationCache.put(threadId, conversation)
        }
        return conversation
    }
}
