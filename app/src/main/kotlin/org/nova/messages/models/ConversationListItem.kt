package org.nova.messages.models

sealed class ConversationListItem {
    abstract val id: Long
    abstract val conversation: Conversation

    data class Recent(override val conversation: Conversation) : ConversationListItem() {
        override val id: Long = -conversation.threadId
    }

    data class Pill(override val conversation: Conversation) : ConversationListItem() {
        override val id: Long = conversation.threadId
    }
    
    data class Default(override val conversation: Conversation) : ConversationListItem() {
        override val id: Long = conversation.threadId
    }
}
