package org.nova.messages.helpers

import android.util.LruCache
import org.fossify.commons.models.SimpleContact
import org.nova.messages.models.NamePhoto

private const val CACHE_SIZE = 512

object MessagingCache {
    val namePhoto = LruCache<String, NamePhoto>(CACHE_SIZE)
    val participantsCache = LruCache<Long, ArrayList<SimpleContact>>(CACHE_SIZE)
    
    // Key is "id_isMms" (e.g., "123_false" for SMS, "456_true" for MMS)
    val reactionsCache = HashMap<String, String>() 

    fun getReactionKey(id: Long, isMms: Boolean) = "${id}_$isMms"
}
