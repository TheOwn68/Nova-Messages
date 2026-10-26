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
        setHasStableIds(true)
        submitList(items as List<Any>)
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position)
        return if (item is SimpleContact) {
            // Use contactId if valid (>0), otherwise fall back to a hash of the phone number for suggested contacts
            if (item.contactId != 0) {
                item.contactId.toLong()
            } else {
                val number = item.phoneNumbers.firstOrNull()?.normalizedNumber ?: ""
                if (number.isNotEmpty()) number.hashCode().toLong() else item.hashCode().toLong()
            }
        } else if (item is ConversationListItem) {
            item.id
        } else {
            item.hashCode().toLong()
        }
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
        holder.bindView(item, allowSingleClick = true, allowLongClick = false) { view, _ ->
            when (getItemViewType(position)) {
                VIEW_TYPE_SUGGESTION -> if (item is ConversationListItem) setupSuggestionView(view, item, holder)
                VIEW_TYPE_MODERN_PILL -> setupModernPillView(view, item, holder)
                else -> setupContactView(view, item, holder)
            }
        }
        bindViewHolder(holder)
    }

    private fun setupSuggestionView(view: View, item: ConversationListItem, holder: MyRecyclerViewListAdapter<Any>.ViewHolder) {
        val conversation = item.conversation
        ItemConversationRecentBinding.bind(view).apply {
            val mainTextColor = activity.config.mainTextColor
            recentAddress.text = conversation.title.stripCountryCodePrefix()
            recentAddress.setTextColor(mainTextColor)
            recentBody.text = "Suggested"
            recentBody.setTextColor(mainTextColor)
            recentBody.alpha = 0.7f
            recentDate.visibility = View.GONE

            SimpleContactsHelper(activity).loadContactImage(
                path = conversation.photoUri,
                imageView = recentImage,
                placeholderName = conversation.title
            )
            
            val cardColor = if (activity.config.useNewUi) Color.parseColor("#16FFFFFF") else Color.WHITE
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 1000f
                setColor(cardColor)
            }
            recentFrame.background = gd
            recentFrame.elevation = if (activity.config.useNewUi) 0f else 8f * resources.displayMetrics.density
        }
    }

    private fun setupContactView(view: View, item: Any, holder: MyRecyclerViewListAdapter<Any>.ViewHolder) {
        ItemConversationBinding.bind(view).apply {
            if (item is SimpleContact) {
                conversationAddress.text = item.name
                conversationBodyShort.text = item.phoneNumbers.firstOrNull()?.normalizedNumber?.stripCountryCodePrefix() ?: ""
                SimpleContactsHelper(activity).loadContactImage(
                    path = item.photoUri,
                    imageView = conversationImage,
                    placeholderName = item.name
                )
            } else if (item is ConversationListItem) {
                val conversation = item.conversation
                conversationAddress.text = conversation.title.stripCountryCodePrefix()
                conversationBodyShort.text = conversation.phoneNumber.stripCountryCodePrefix()
                SimpleContactsHelper(activity).loadContactImage(
                    path = conversation.photoUri,
                    imageView = conversationImage,
                    placeholderName = conversation.title
                )
            }
            
            val mainTextColor = activity.config.mainTextColor
            conversationAddress.setTextColor(mainTextColor)
            conversationBodyShort.setTextColor(mainTextColor)
            conversationBodyShort.alpha = 0.7f
            
            val density = resources.displayMetrics.density
            val cardColor = if (activity.config.useNewUi) Color.parseColor("#16FFFFFF") else Color.WHITE
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f * density
                setColor(cardColor)
            }
            conversationFrame.background = gd
        }
    }

    private fun setupModernPillView(view: View, item: Any, holder: MyRecyclerViewListAdapter<Any>.ViewHolder) {
        org.nova.messages.databinding.ItemConversationPillBinding.bind(view).apply {
            val contact = item as SimpleContact
            pillAddress.text = contact.name.stripCountryCodePrefix()
            pillAddress.setTextColor(activity.config.mainTextColor)
            
            SimpleContactsHelper(activity).loadContactImage(
                path = contact.photoUri,
                imageView = pillImage,
                placeholderName = contact.name
            )

            val density = resources.displayMetrics.density
            val cardColor = Color.parseColor("#16FFFFFF")
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 1000f
                setColor(cardColor)
            }
            
            if (activity.config.smallContactsOutline && activity.config.useNewUi) {
                val thickness = activity.config.smallContactsOutlineThickness
                val thickStroke = (thickness * density).toInt()
                gd.setStroke(thickStroke, activity.config.smallContactsOutlineColor)
                
                val layerDrawable = LayerDrawable(arrayOf(gd))
                layerDrawable.setLayerInset(0, 0, 0, 0, 0)
                pillFrame.background = layerDrawable
            } else {
                pillFrame.background = gd
            }
            
            pillFrame.elevation = 0f
        }
    }

    private class ContactsDiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem is SimpleContact && newItem is SimpleContact) return oldItem.rawId == newItem.rawId
            if (oldItem is ConversationListItem && newItem is ConversationListItem) return oldItem.id == newItem.id
            return oldItem.hashCode() == newItem.hashCode()
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }
    }

    inner class ContactViewHolder(val binding: androidx.viewbinding.ViewBinding) : ViewHolder(binding.root)
}
