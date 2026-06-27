package org.nova.messages.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.models.SimpleContact
import org.fossify.commons.views.MyRecyclerView
import org.nova.messages.R
import org.nova.messages.activities.SimpleActivity
import org.nova.messages.extensions.*
import org.nova.messages.databinding.ItemConversationBinding
import org.nova.messages.databinding.ItemConversationRecentBinding
import org.nova.messages.models.ConversationListItem
import java.util.ArrayList

class ContactsAdapter(
    activity: SimpleActivity,
    items: ArrayList<out Any>,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewListAdapter<Any>(
    activity = activity,
    recyclerView = recyclerView,
    diffUtil = ContactsDiffCallback(),
    itemClick = itemClick,
    onRefresh = {}
) {

    private var suggestionsCount: Int = 0

    companion object {
        const val VIEW_TYPE_SUGGESTION = 1
        const val VIEW_TYPE_CONTACT = 2
    }

    init {
        submitList(items as List<Any>)
    }

    override fun getActionMenuId() = 0
    override fun prepareActionMode(menu: android.view.Menu) {}
    override fun actionItemPressed(id: Int) {}
    override fun getSelectableItemCount() = 0
    override fun getIsItemSelectable(position: Int) = false
    override fun getItemSelectionKey(position: Int) = null
    override fun getItemKeyPosition(key: Int) = -1
    override fun onActionModeCreated() {}
    override fun onActionModeDestroyed() {}

    fun setSuggestionsCount(count: Int) {
        suggestionsCount = count
        notifyDataSetChanged()
    }

    fun updateContacts(newItems: List<Any>) {
        submitList(newItems)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < suggestionsCount) VIEW_TYPE_SUGGESTION else VIEW_TYPE_CONTACT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyRecyclerViewListAdapter<Any>.ViewHolder {
        val binding = when (viewType) {
            VIEW_TYPE_SUGGESTION -> ItemConversationRecentBinding.inflate(layoutInflater, parent, false)
            else -> ItemConversationBinding.inflate(layoutInflater, parent, false)
        }
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewListAdapter<Any>.ViewHolder, position: Int) {
        val item = getItem(position)
        if (getItemViewType(position) == VIEW_TYPE_SUGGESTION && item is ConversationListItem) {
            setupSuggestionView(holder.itemView, item, holder)
        } else {
            setupContactView(holder.itemView, item, holder)
        }
        bindViewHolder(holder)
    }

    private fun setupSuggestionView(view: View, item: ConversationListItem, holder: MyRecyclerViewListAdapter<Any>.ViewHolder) {
        val conversation = item.conversation
        ItemConversationRecentBinding.bind(view).apply {
            val mainTextColor = activity.config.mainTextColor
            recentAddress.text = conversation.title
            recentAddress.setTextColor(mainTextColor)
            recentBody.text = "Suggested"
            recentBody.setTextColor(mainTextColor)
            recentBody.alpha = 0.7f
            recentDate.visibility = View.GONE
            
            val baseColor = activity.config.recentColor
            val lightened = baseColor.adjustColor(1.2f)
            val darkened = baseColor.adjustColor(0.8f)
            val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, baseColor, darkened))
            gd.cornerRadius = 1000f
            recentFrame.background = gd
            recentFrame.elevation = 8f * resources.displayMetrics.density
            recentFrame.setOnClickListener { holder.viewClicked(item) }
        }
    }

    private fun setupContactView(view: View, item: Any, holder: MyRecyclerViewListAdapter<Any>.ViewHolder) {
        ItemConversationBinding.bind(view).apply {
            val mainTextColor = activity.config.mainTextColor
            if (item is SimpleContact) {
                conversationAddress.text = item.name
                conversationBodyShort.text = item.phoneNumbers.firstOrNull()?.normalizedNumber ?: ""
            } else if (item is ConversationListItem) {
                conversationAddress.text = item.conversation.title
                conversationBodyShort.text = item.conversation.phoneNumber
            }
            
            conversationAddress.setTextColor(mainTextColor)
            conversationBodyShort.setTextColor(mainTextColor)
            conversationBodyShort.alpha = 0.7f
            
            val baseColor = activity.config.mainBackgroundColor.adjustColor(1.1f)
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f * resources.displayMetrics.density
                setColor(baseColor)
            }
            conversationFrame.background = gd
            conversationFrame.setOnClickListener { holder.viewClicked(item) }
        }
    }

    private class ContactsDiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem is SimpleContact && newItem is SimpleContact) return oldItem.rawId == newItem.rawId
            if (oldItem is ConversationListItem && newItem is ConversationListItem) return oldItem.id == newItem.id
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return oldItem == newItem
        }
    }

    inner class ContactViewHolder(val binding: androidx.viewbinding.ViewBinding) : ViewHolder(binding.root)
}
