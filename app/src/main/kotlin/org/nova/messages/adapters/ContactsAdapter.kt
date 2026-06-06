package org.nova.messages.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.databinding.ItemContactWithNumberBinding
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.models.SimpleContact
import org.fossify.commons.views.MyRecyclerView
import org.nova.messages.R
import org.nova.messages.activities.SimpleActivity
import org.nova.messages.extensions.config

class ContactsAdapter(
    activity: SimpleActivity, var contacts: ArrayList<SimpleContact>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {
    private var fontSize = activity.getScaledTextSize()
    private var suggestionsCount = 0

    fun setSuggestionsCount(count: Int) {
        suggestionsCount = count
    }

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = contacts.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = contacts.getOrNull(position)?.rawId

    override fun getItemKeyPosition(key: Int) = contacts.indexOfFirst { it.rawId == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun getItemViewType(position: Int): Int {
        return if (activity.config.useNewUi) {
            if (position < suggestionsCount) VIEW_TYPE_SUGGESTION else VIEW_TYPE_CONTACT
        } else {
            VIEW_TYPE_DEFAULT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when (viewType) {
            VIEW_TYPE_SUGGESTION -> org.nova.messages.databinding.ItemConversationRecentBinding.inflate(layoutInflater, parent, false)
            VIEW_TYPE_CONTACT -> org.nova.messages.databinding.ItemConversationPillBinding.inflate(layoutInflater, parent, false)
            else -> ItemContactWithNumberBinding.inflate(layoutInflater, parent, false)
        }
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bindView(contact, allowSingleClick = true, allowLongClick = false) { itemView, _ ->
            if (activity.config.useNewUi) {
                if (position < suggestionsCount) setupSuggestionView(itemView, contact, position) else setupPillView(itemView, contact, position)
            } else {
                setupView(itemView, contact)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: ArrayList<SimpleContact>) {
        val oldHashCode = contacts.hashCode()
        val newHashCode = newContacts.hashCode()
        if (newHashCode != oldHashCode) {
            contacts = newContacts
            notifyDataSetChanged()
        }
    }

    private fun setupSuggestionView(view: View, contact: SimpleContact, position: Int) {
        org.nova.messages.databinding.ItemConversationRecentBinding.bind(view).apply {
            val mainTextColor = activity.config.mainTextColor
            recentAddress.text = contact.name
            recentAddress.setTextColor(mainTextColor)
            
            recentBody.text = TextUtils.join(", ", contact.phoneNumbers.map { it.normalizedNumber })
            recentBody.setTextColor(mainTextColor)
            recentBody.alpha = 0.8f

            recentDate.visibility = View.GONE
            recentFrame.setupViewBackground(activity)
            
            val baseColor = activity.config.recentColor
            val lightened = adjustColor(baseColor, 1.2f)
            val darkened = adjustColor(baseColor, 0.8f)
            val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, baseColor, darkened))
            gd.cornerRadius = 1000f
            recentFrame.background = gd
            
            recentFrame.elevation = 14f * resources.displayMetrics.density
            recentFrame.translationZ = 8f
            recentFrame.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            recentFrame.clipToOutline = false

            recentSelectionGlow.beVisibleIf(false) // No selection in this screen

            SimpleContactsHelper(activity).loadContactImage(
                path = contact.photoUri,
                imageView = recentImage,
                placeholderName = contact.name
            )
        }
    }

    private fun setupPillView(view: View, contact: SimpleContact, position: Int) {
        org.nova.messages.databinding.ItemConversationPillBinding.bind(view).apply {
            val mainTextColor = activity.config.mainTextColor
            pillAddress.text = contact.name
            pillAddress.setTextColor(mainTextColor) 
            pillFrame.setupViewBackground(activity)
            
            val rowIndex = (position - suggestionsCount) / 2
            val baseColor = when (rowIndex % 3) {
                0 -> activity.config.row1Color
                1 -> activity.config.row2Color
                else -> activity.config.row3Color
            }
            
            val lightened = adjustColor(baseColor, 1.2f)
            val darkened = adjustColor(baseColor, 0.8f)
            val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, baseColor, darkened))
            gd.cornerRadius = 1000f
            pillFrame.background = gd
            
            pillFrame.elevation = 12f * resources.displayMetrics.density
            pillFrame.translationZ = 6f
            pillFrame.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            pillFrame.clipToOutline = false

            pillSelectionGlow.beVisibleIf(false)

            SimpleContactsHelper(activity).loadContactImage(
                path = contact.photoUri,
                imageView = pillImage,
                placeholderName = contact.name
            )
        }
    }

    private fun adjustColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = Math.round(Color.red(color) * factor).coerceIn(0, 255)
        val g = Math.round(Color.green(color) * factor).coerceIn(0, 255)
        val b = Math.round(Color.blue(color) * factor).coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    private fun setupView(view: View, contact: SimpleContact) {
        ItemContactWithNumberBinding.bind(view).apply {
            val customTypeface = (activity as SimpleActivity).getCustomTypeface()
            val mainTextColor = (activity as SimpleActivity).config.mainTextColor
            
            itemContactName.apply {
                text = contact.name
                setTextColor(mainTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.2f)
                typeface = Typeface.create(customTypeface, Typeface.NORMAL)
            }

            itemContactNumber.apply {
                text = TextUtils.join(", ", contact.phoneNumbers.map { it.normalizedNumber })
                setTextColor(mainTextColor)
                alpha = 0.7f
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                typeface = Typeface.create(customTypeface, Typeface.NORMAL)
            }

            SimpleContactsHelper(activity).loadContactImage(contact.photoUri, itemContactImage, contact.name)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            try {
                val recentImg = holder.itemView.findViewById<ImageView>(R.id.recent_image)
                val pillImg = holder.itemView.findViewById<ImageView>(R.id.pill_image)
                val contactImg = holder.itemView.findViewById<ImageView>(org.fossify.commons.R.id.item_contact_image)
                
                recentImg?.let { Glide.with(activity).clear(it) }
                pillImg?.let { Glide.with(activity).clear(it) }
                contactImg?.let { Glide.with(activity).clear(it) }
            } catch (_: Exception) {}
        }
    }

    companion object {
        const val VIEW_TYPE_DEFAULT = 0
        const val VIEW_TYPE_SUGGESTION = 1
        const val VIEW_TYPE_CONTACT = 2
    }
}
