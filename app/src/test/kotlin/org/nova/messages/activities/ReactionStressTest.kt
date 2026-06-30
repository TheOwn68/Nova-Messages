package org.nova.messages.activities

import org.nova.messages.models.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.ArrayList

class ReactionStressTest {

    @Test
    fun testProcessReactions_Stress() {
        val messages = ArrayList<Message>()
        
        // 1. Create a set of base messages
        for (i in 0 until 100) {
            messages.add(createMessage(i.toLong(), "Message $i", 1000 + i))
        }

        // 2. Add multiple reaction messages for "Message 50" (snippet "Message 50")
        
        // Late reaction (Latest should win)
        messages.add(createReactionMessage(200, "Liked message 'Message 50'", 2000))
        // Early reaction (Should be ignored by date check if processed after late one)
        messages.add(createReactionMessage(201, "Loved message 'Message 50'", 1500))
        // Another late reaction with different emoji
        messages.add(createReactionMessage(202, "Angry at message 'Message 50'", 2500))

        // 3. Process reactions
        val result = processReactionsSimulated(messages)

        // 4. Verify results
        val msg50 = result.find { it.id == 50L }
        assertNotNull("Message 50 should still exist", msg50)
        // 2500 is the latest date, so "Angry at" -> "😡" should win
        assertEquals("😡", msg50?.reaction)
        
        // Verify reaction messages are filtered out
        assertNull("Reaction message 200 should be filtered", result.find { it.id == 200L })
        assertNull("Reaction message 201 should be filtered", result.find { it.id == 201L })
        assertNull("Reaction message 202 should be filtered", result.find { it.id == 202L })
    }

    @Test
    fun testProcessReactions_DuplicateSnippets() {
        val messages = ArrayList<Message>()
        
        // Two messages with same snippet
        messages.add(createMessage(1L, "Hello world", 1000))
        messages.add(createMessage(2L, "Hello world", 1100))
        
        // Reaction message for "Hello world" at time 1200
        messages.add(createReactionMessage(100L, "Liked message 'Hello world'", 1200))
        
        val result = processReactionsSimulated(messages)
        
        val msg1 = result.find { it.id == 1L }
        val msg2 = result.find { it.id == 2L }
        
        // In current implementation, if multiple messages have same snippet, 
        // the last one encountered while building snippetMap wins.
        assertNull("Msg1 should NOT have reaction because it was overwritten in snippetMap", msg1?.reaction)
        assertEquals("Msg2 should have reaction", "👍", msg2?.reaction)
    }

    private fun createMessage(id: Long, body: String, date: Int): Message {
        return Message(
            id = id,
            body = body,
            type = 1, // Inbox
            status = 0,
            participants = ArrayList(),
            date = date,
            read = true,
            threadId = 1L,
            isMMS = false,
            attachment = null,
            senderPhoneNumber = "123",
            senderName = "Sender",
            senderPhotoUri = "",
            subscriptionId = 0
        )
    }

    private fun createReactionMessage(id: Long, body: String, date: Int): Message {
        return createMessage(id, body, date)
    }

    private fun processReactionsSimulated(messages: List<Message>): List<Message> {
        val (regex, map) = getReactionTools()
        
        val snippetMap = messages.filter { !it.body.isNullOrBlank() }
            .associateBy({ it.body.take(30).trim() }, { it })

        val result = messages.toMutableList()
        
        for (i in result.indices.reversed()) {
            val msg = result[i]
            val match = regex.find(msg.body)
            
            if (match != null) {
                msg.isReactionMessage = true
                
                val prefix = match.groupValues[1].ifEmpty { match.groupValues[2] }
                val quotedText = match.groupValues[3].trim()
                val emoji = map[prefix] ?: "👍"

                val target = snippetMap[quotedText]
                if (target != null && target.id != msg.id && target.date <= msg.date) {
                    if (target.reaction == null || msg.date > target.reactionDate) {
                        target.reaction = emoji
                        target.reactionDate = msg.date
                    }
                }
            }
        }

        return result.filter { !it.isReactionMessage }
    }

    private fun getReactionTools(): Pair<Regex, Map<String, String>> {
        val reactionRegex = Regex("^(?:(Liked|Loved|Disliked|Laughed at|Surprised by|Cried at|Angry at|Emphasized|Questioned)\\s+(?:message\\s+)?|([👍❤️👎😂😮😢😡‼️❓])\\s+to\\s+)['\"](.*?)['\"]?\\s*$")
        val reactionMap = mapOf(
            "Liked" to "👍", "Loved" to "❤️", "Disliked" to "👎", "Laughed at" to "😂",
            "Surprised by" to "😮", "Cried at" to "😢", "Angry at" to "😡", "Emphasized" to "‼️", "Questioned" to "❓",
            "👍" to "👍", "❤️" to "❤️", "👎" to "👎", "😂" to "😂", "😮" to "😮", "😢" to "😢", "😡" to "😡", "‼️" to "‼️", "❓" to "❓"
        )
        return Pair(reactionRegex, reactionMap)
    }
}
