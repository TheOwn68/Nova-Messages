package org.nova.messages.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.helpers.SimpleContactsHelper
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

    private var useModernPills: Boolean = false
    private var suggestionsCount: Int = 0

    fun setUseModernPills(use: Boolean) {
        useModernPills = use
    }

    companion object {
        const val VIEW_TYPE_SUGGESTION = 1
        const val VIEW_TYPE_CONTACT = 2
        const val VIEW_TYPE_MODERN_PILL = 3
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
        if (useModernPills) return VIEW_TYPE_MODERN_PILL
        return if (position < suggestionsCount) VIEW_TYPE_SUGGESTION else VIEW_TYPE_CONTACT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyRecyclerViewListAdapter<Any>.ViewHolder {
        val binding = when (viewType) {
            VIEW_TYPE_SUGGESTION -> org.nova.messages.databinding.ItemConversationRecentBinding.inflate(layoutInflater, parent, false)
            VIEW_TYPE_MODERN_PILL -> org.nova.messages.databinding.ItemConversationPillBinding.inflate(layoutInflater, parent, false)
            else -> ItemConversationBinding.inflate(layoutInflater, parent, false)
        }
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewListAdapter<Any>.ViewHolder, position: Int) {
        val item = getItem(position)
        val view = holder.itemView
        when (getItemViewType(position)) {
            VIEW_TYPE_SUGGESTION -> if (item is ConversationListItem) setupSuggestionView(view, item, holder)
            VIEW_TYPE_MODERN_PILL -> setupModernPillView(view, item, holder)
            else -> setupContactView(view, item, holder)
        }
        bindViewHolder(holder)
    }

    private fun setupSuggestionView(view: View, item: ConversationListItem, holder: MyRecyclerViewListAdapter<Any>.ViewHolder) {
        val conversation = item.conversation
        ItemConversationRecentBinding.bind(view).apply {
            recentAddress.text = conversation.title
            recentAddress.setTextColor(Color.BLACK)
            recentBody.text = "Suggested"
            recentBody.setTextColor(Color.BLACK)
            recentBody.alpha = 0.7f
            recentDate.visibility = View.GONE

            SimpleContactsHelper(activity).loadContactImage(
                path = conversation.photoUri,
                imageView = recentImage,
                placeholderName = conversation.title
            )
            
            val baseColor = Color.WHITE
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
            if (item is SimpleContact) {
                conversationAddress.text = item.name
                conversationBodyShort.text = item.phoneNumbers.firstOrNull()?.normalizedNumber ?: ""
                SimpleContactsHelper(activity).loadContactImage(
                    path = item.photoUri,
                    imageView = conversationImage,
                    placeholderName = item.name
                )
            } else if (item is ConversationListItem) {
                val conversation = item.conversation
                conversationAddress.text = conversation.title
                conversationBodyShort.text = conversation.phoneNumber
                SimpleContactsHelper(activity).loadContactImage(
                    path = conversation.photoUri,
                    imageView = conversationImage,
                    placeholderName = conversation.title
                )
            }
            
            conversationAddress.setTextColor(Color.BLACK)
            conversationBodyShort.setTextColor(Color.BLACK)
            conversationBodyShort.alpha = 0.7f
            
            val density = resources.displayMetrics.density
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f * density
                setColor(Color.WHITE)
            }
            conversationFrame.background = gd
            conversationFrame.setOnClickListener { holder.viewClicked(item) }
        }
    }

    private fun setupModernPillView(view: View, item: Any, holder: MyRecyclerViewListAdapter<Any>.ViewHolder) {
        org.nova.messages.databinding.ItemConversationPillBinding.bind(view).apply {
            val contact = item as SimpleContact
            pillAddress.text = contact.name
            pillAddress.setTextColor(Color.BLACK)
            
            SimpleContactsHelper(activity).loadContactImage(
                path = contact.photoUri,
                imageView = pillImage,
                placeholderName = contact.name
            )

            // Setup modern gradient/outline
            val baseColor = Color.WHITE
            val lightened = baseColor.adjustColor(1.2f)
            val darkened = baseColor.adjustColor(0.8f)
            val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, baseColor, darkened))
            val r_base = 1000f
            
            val density = resources.displayMetrics.density
            if (activity.config.smallContactsOutline && activity.config.useNewUi) {
                val thickness = activity.config.smallContactsOutlineThickness
                val thickStroke = (thickness * density).toInt()
                gd.cornerRadius = r_base
                gd.setStroke(thickStroke, activity.config.smallContactsOutlineColor)
                
                val layerDrawable = LayerDrawable(arrayOf(gd))
                layerDrawable.setLayerInset(0, 0, 0, 0, 0)
                pillFrame.background = layerDrawable
            } else {
                gd.cornerRadius = r_base
                pillFrame.background = gd
            }
            
            pillFrame.elevation = 6f * density
            pillFrame.setOnClickListener { holder.viewClicked(item) }
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
