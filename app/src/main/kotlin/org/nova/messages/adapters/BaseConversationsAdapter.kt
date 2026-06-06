package org.nova.messages.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Parcelable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.nova.messages.R
import org.nova.messages.activities.SimpleActivity
import org.nova.messages.databinding.ItemConversationBinding
import org.nova.messages.extensions.*
import org.nova.messages.models.Conversation

@Suppress("LeakingThis")
abstract class BaseConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewListAdapter<Conversation>(
    activity = activity,
    recyclerView = recyclerView,
    diffUtil = ConversationDiffCallback(),
    itemClick = itemClick,
    onRefresh = onRefresh
),
    RecyclerViewFastScroller.OnPopupTextUpdate {
    var itemTouchHelper: androidx.recyclerview.widget.ItemTouchHelper? = null
    private var lastDragTime = 0L
    private var drafts = HashMap<Long, String>()

    // Drag State for Delayed Swap
    private var isDragging = false
    private var initialDragPosition = -1
    private var lastTargetPosition = -1
    private var pendingUpdate: ArrayList<Conversation>? = null
    private var pendingNotify = false

    private var fontSize = activity.getScaledTextSize()
    private var iconSize = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.list_icon_size_medium)

    private var recyclerViewState: Parcelable? = null

    init {
        setHasStableIds(false) // Must be false because Top 2 are duplicates of items in the grid
        updateDrafts()

        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                restoreRecyclerViewState()
                updateCustomSelectionBar()
            }
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                restoreRecyclerViewState()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                restoreRecyclerViewState()
            }
            
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                updateCustomSelectionBar()
            }
            
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                updateCustomSelectionBar()
            }
        })
    }

    fun updateConversations(
        newConversations: ArrayList<Conversation>,
        commitCallback: (() -> Unit)? = null,
    ) {
        if (isDragging) {
            pendingUpdate = newConversations
            return
        }

        saveRecyclerViewState()
        submitList(newConversations.toList(), commitCallback)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateDrafts() {
        ensureBackgroundThread {
            val newDrafts = HashMap<Long, String>()
            fetchDrafts(newDrafts)
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (drafts.hashCode() != newDrafts.hashCode()) {
                    drafts = newDrafts
                    safeNotifyDataSetChanged()
                }
            }
        }
    }

    override fun getSelectableItemCount() = itemCount

    protected fun getSelectedItems() = currentList.filter {
        selectedKeys.contains(it.hashCode())
    } as ArrayList<Conversation>

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {
        updateCustomSelectionBar()
    }

    override fun onActionModeDestroyed() {
        (activity as? SimpleActivity)?.toggleCustomSelectionBar(false)
    }

    private fun updateCustomSelectionBar() {
        val simpleActivity = activity as? SimpleActivity ?: return
        val count = selectedKeys.size
        if (count > 0) {
            val actions = getCustomActions()
            simpleActivity.toggleCustomSelectionBar(true, count, actions) { actionId ->
                if (actionId == R.id.selection_cancel) {
                    finishActMode()
                } else {
                    actionItemPressed(actionId)
                }
            }
        } else {
            simpleActivity.toggleCustomSelectionBar(false)
        }
    }

    abstract fun getCustomActions(): List<Int>

    fun isSelectionModeActive() = selectedKeys.isNotEmpty()

    override fun getItemViewType(position: Int): Int {
        return if (activity.config.useNewUi) {
            if (position < 2) VIEW_TYPE_RECENT else VIEW_TYPE_PILL
        } else {
            VIEW_TYPE_DEFAULT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when (viewType) {
            VIEW_TYPE_RECENT -> org.nova.messages.databinding.ItemConversationRecentBinding.inflate(layoutInflater, parent, false)
            VIEW_TYPE_PILL -> org.nova.messages.databinding.ItemConversationPillBinding.inflate(layoutInflater, parent, false)
            else -> ItemConversationBinding.inflate(layoutInflater, parent, false)
        }
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.bindView(
            conversation,
            allowSingleClick = true, // Restore default click for opening threads
            allowLongClick = false    // Keep false to manage custom drag/select split
        ) { itemView, _ ->
            if (activity.config.useNewUi) {
                if (position < 2) setupRecentView(itemView, conversation, position, holder) else setupPillView(itemView, conversation, position, holder)
            } else {
                setupView(itemView, conversation, holder)
            }
        }
        bindViewHolder(holder)
    }

    fun onDragStarted(position: Int) {
        if (!activity.config.useNewUi || position < 2) return
        isDragging = true
        pendingNotify = false
        initialDragPosition = position
        lastTargetPosition = position
    }

    fun onItemSwapped(toPosition: Int) {
        if (!isDragging || toPosition < 2) return
        
        // Instant visual swap for buttery smooth feedback
        val list = currentList.toArrayList()
        val from = lastTargetPosition
        if (from != -1 && from < list.size && toPosition < list.size) {
            java.util.Collections.swap(list, from, toPosition)
            submitList(list)
        }
        
        lastTargetPosition = toPosition
    }

    fun onDragEnded() {
        if (!isDragging) return
        
        val start = initialDragPosition
        val end = lastTargetPosition
        
        isDragging = false
        initialDragPosition = -1
        lastTargetPosition = -1
        
        if (start != -1 && end != -1 && start != end) {
            // Save the final order (The list is already visually swapped by onItemSwapped)
            val others = currentList.drop(2)
            activity.config.conversationOrder = others.joinToString(",") { it.threadId.toString() }
        }

        // Process any refresh that happened during the drag
        pendingUpdate?.let { 
            val update = it
            pendingUpdate = null
            updateConversations(update)
        }
        
        if (pendingNotify) {
            pendingNotify = false
            notifyDataSetChanged()
        }
    }

    private val lastSenderCache = HashMap<Long, Int>()

    private fun setupRecentView(view: View, conversation: Conversation, position: Int, holder: ViewHolder) {
        org.nova.messages.databinding.ItemConversationRecentBinding.bind(view).apply {
            val mainTextColor = activity.config.mainTextColor
            recentAddress.text = conversation.title
            recentAddress.setTextColor(mainTextColor)
            
            // Priority: Real messages over drafts for "Recent" cards as per user request
            ensureBackgroundThread {
                if (isDragging) return@ensureBackgroundThread
                val liveSnippet = activity.getThreadSnippet(conversation.threadId)
                val type = activity.getLatestMessageType(conversation.threadId)
                // type 2 is SENT, type 1 is INBOX
                val isSent = type == 2 || type == 4 || type == 5 || type == 6 
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed || isDragging) return@runOnUiThread
                    recentBody.text = if (isSent && liveSnippet.isNotEmpty()) "You: $liveSnippet" else liveSnippet.ifEmpty { conversation.snippet }
                    recentBody.setTextColor(mainTextColor)
                    recentBody.alpha = 0.8f
                }
            }

            recentDate.text = (conversation.date * 1000L).formatDateOrTime(
                context = activity,
                hideTimeOnOtherDays = true,
                showCurrentYear = false
            )
            recentDate.setTextColor(mainTextColor)
            recentDate.alpha = 0.7f
            
            recentFrame.setupViewBackground(activity)
            
            val baseColor = activity.config.recentColor
            
            // Modern Vertical Gradient
            val lightened = adjustColor(baseColor, 1.2f)
            val darkened = adjustColor(baseColor, 0.8f)
            val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, baseColor, darkened))
            gd.cornerRadius = 1000f
            recentFrame.background = gd
            
            // Hard Force Elevation (High Visibility)
            recentFrame.elevation = 14f * resources.displayMetrics.density
            recentFrame.translationZ = 8f
            recentFrame.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            recentFrame.clipToOutline = false

            // Selection Glow
            val isSelected = selectedKeys.contains(conversation.hashCode())
            recentSelectionGlow.beVisibleIf(isSelected)

            // Interaction Separation: Body is only for opening (fixed cards)
            recentFrame.setOnClickListener {
                if (System.currentTimeMillis() - lastDragTime < 500) return@setOnClickListener
                
                if (isSelectionModeActive()) {
                    holder.viewLongClicked() 
                } else {
                    holder.viewClicked(conversation)
                }
            }

            recentFrame.setOnLongClickListener {
                // Recent cards are fixed, so we just toggle selection on body long-press
                lastDragTime = System.currentTimeMillis()
                holder.viewLongClicked()
                true
            }

            // Image specifically for selection
            recentImage.setOnClickListener {
                holder.viewLongClicked()
            }

            recentImage.setOnLongClickListener {
                holder.viewLongClicked()
                true
            }

            SimpleContactsHelper(activity).loadContactImage(
                path = conversation.photoUri,
                imageView = recentImage,
                placeholderName = conversation.title
            )
        }
    }

    private fun setupPillView(view: View, conversation: Conversation, position: Int, holder: ViewHolder) {
        org.nova.messages.databinding.ItemConversationPillBinding.bind(view).apply {
            val mainTextColor = activity.config.mainTextColor
            pillAddress.text = conversation.title
            pillAddress.setTextColor(mainTextColor) 
            pillFrame.setupViewBackground(activity)
            
            // Sync color by row: position 2-3 are row 0, 4-5 are row 1, etc.
            val rowIndex = (position - 2) / 2
            val baseColor = when (rowIndex % 3) {
                0 -> activity.config.row1Color
                1 -> activity.config.row2Color
                else -> activity.config.row3Color
            }
            
            // Modern Vertical Gradient (Slightly Lighter for M3 look)
            val lightened = adjustColor(baseColor, 1.2f)
            val darkened = adjustColor(baseColor, 0.8f)
            val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, baseColor, darkened))
            gd.cornerRadius = 1000f
            pillFrame.background = gd
            
            // Hard Force Elevation (High Visibility)
            pillFrame.elevation = 12f * resources.displayMetrics.density
            pillFrame.translationZ = 6f
            pillFrame.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            pillFrame.clipToOutline = false

            // Selection Glow
            val isSelected = selectedKeys.contains(conversation.hashCode())
            pillSelectionGlow.beVisibleIf(isSelected)

            pillFrame.setOnClickListener {
                // Click Guard: If we just finished a drag, ignore this click
                if (System.currentTimeMillis() - lastDragTime < 500) return@setOnClickListener
                
                if (isSelectionModeActive()) {
                    holder.viewLongClicked()
                } else {
                    holder.viewClicked(conversation)
                }
            }

            pillFrame.setOnLongClickListener {
                if (!isSelectionModeActive()) {
                    // Body Long-Press strictly for Dragging
                    lastDragTime = System.currentTimeMillis()
                    itemTouchHelper?.startDrag(holder)
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    true 
                } else {
                    // If selection active, body still acts as a reorder handle
                    lastDragTime = System.currentTimeMillis()
                    itemTouchHelper?.startDrag(holder)
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    true
                }
            }

            pillImage.setOnClickListener {
                holder.viewLongClicked()
            }

            pillImage.setOnLongClickListener {
                if (isSelectionModeActive()) {
                    // While selecting, holding the picture lets you move it
                    lastDragTime = System.currentTimeMillis()
                    itemTouchHelper?.startDrag(holder)
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                } else {
                    // Not selecting, holding image starts selection
                    holder.viewLongClicked()
                }
                true
            }

            SimpleContactsHelper(activity).loadContactImage(
                path = conversation.photoUri,
                imageView = pillImage,
                placeholderName = conversation.title
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

    override fun getItemId(position: Int) = getItem(position).threadId

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            try {
                // Ultra-Safe Image Clearing: Just find the possible image views directly
                val recentImg = holder.itemView.findViewById<ImageView>(R.id.recent_image)
                val pillImg = holder.itemView.findViewById<ImageView>(R.id.pill_image)
                val convImg = holder.itemView.findViewById<ImageView>(R.id.conversation_image)
                
                recentImg?.let { Glide.with(activity).clear(it) }
                pillImg?.let { Glide.with(activity).clear(it) }
                convImg?.let { Glide.with(activity).clear(it) }
            } catch (_: Exception) { }
        }
    }

    private fun fetchDrafts(drafts: HashMap<Long, String>) {
        drafts.clear()
        for ((threadId, draft) in activity.getAllDrafts()) {
            drafts[threadId] = draft
        }
    }

    private fun setupView(view: View, conversation: Conversation, holder: ViewHolder) {
        ItemConversationBinding.bind(view).apply {
            root.setupViewBackground(activity)
            
            // Manually re-add listeners since we disabled default ones
            root.setOnClickListener {
                if (System.currentTimeMillis() - lastDragTime < 500) return@setOnClickListener
                
                if (isSelectionModeActive()) {
                    holder.viewLongClicked()
                } else {
                    holder.viewClicked(conversation)
                }
            }
            
            root.setOnLongClickListener {
                lastDragTime = System.currentTimeMillis()
                holder.viewLongClicked()
                true
            }
            root.minimumHeight = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.two_line_list_item_min_height)
            val paddingStart = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.activity_margin)
            val paddingTop = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.medium_margin)
            root.setPadding(paddingStart, paddingTop, paddingStart, paddingTop)
            
            val currentMainTextColor = activity.config.mainTextColor
            val smsDraft = drafts[conversation.threadId]
            draftIndicator.apply {
                beVisibleIf(!smsDraft.isNullOrEmpty())
                setTextColor(currentMainTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.9f)
            }

            pinIndicator.beVisibleIf(
                activity.config.pinnedConversations.contains(conversation.threadId.toString())
            )
            pinIndicator.applyColorFilter(currentMainTextColor)

            conversationFrame.isSelected = selectedKeys.contains(conversation.hashCode())

            conversationAddress.apply {
                text = conversation.title
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.1f)
            }

            conversationBodyShort.apply {
                ensureBackgroundThread {
                    if (isDragging) return@ensureBackgroundThread
                    val liveSnippet = activity.getThreadSnippet(conversation.threadId)
                    val type = activity.getLatestMessageType(conversation.threadId)
                    val isSent = type == 2 || type == 4 || type == 5 || type == 6 
                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed || isDragging) return@runOnUiThread
                        text = if (isSent && liveSnippet.isNotEmpty()) "You: $liveSnippet" else liveSnippet.ifEmpty { smsDraft ?: conversation.snippet }
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                    }
                }
            }

            conversationDate.apply {
                text = (conversation.date * 1000L).formatDateOrTime(
                    context = context,
                    hideTimeOnOtherDays = true,
                    showCurrentYear = false
                )
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            val isUnread = !conversation.read
            val style = if (isUnread) {
                conversationBodyShort.alpha = 1f
                if (conversation.isScheduled) Typeface.BOLD_ITALIC else Typeface.BOLD
            } else {
                conversationBodyShort.alpha = 0.7f
                if (conversation.isScheduled) Typeface.ITALIC else Typeface.NORMAL
            }
            val customTypeface = (activity as SimpleActivity).getCustomTypeface()
            conversationAddress.setTypeface(customTypeface, style)
            conversationBodyShort.setTypeface(customTypeface, style)
            conversationDate.setTypeface(customTypeface, style)
            unreadCountBadge.typeface = customTypeface
            draftIndicator.typeface = Typeface.create(customTypeface, Typeface.ITALIC)

            arrayListOf(conversationAddress, conversationBodyShort, conversationDate).forEach {
                it.setTextColor(currentMainTextColor)
            }
            unreadCountBadge.setTextColor(currentMainTextColor)

            setupBadgeCount(unreadCountBadge, isUnread, conversation.unreadCount)
            // at group conversations we use an icon as the placeholder, not any letter
            val placeholder = if (conversation.isGroupConversation) {
                SimpleContactsHelper(activity).getColoredGroupIcon(conversation.title)
            } else {
                null
            }

            conversationImage.updateLayoutParams {
                width = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.list_icon_size_medium)
                height = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.list_icon_size_medium)
            }

            SimpleContactsHelper(activity).loadContactImage(
                path = conversation.photoUri,
                imageView = conversationImage,
                placeholderName = conversation.title,
                placeholderImage = placeholder
            )
        }
    }

    private fun setupBadgeCount(view: TextView, isUnread: Boolean, count: Int) {
        view.apply {
            beVisibleIf(isUnread)
            if (isUnread) {
                text = when {
                    count > MAX_UNREAD_BADGE_COUNT -> "$MAX_UNREAD_BADGE_COUNT+"
                    count == 0 -> ""
                    else -> count.toString()
                }
                setTextColor(properPrimaryColor.getContrastColor())
                background?.applyColorFilter(properPrimaryColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.7f)
                updateLayoutParams {
                    val size = (activity as SimpleActivity).getScaledDimen(org.nova.messages.R.dimen.small_icon_size)
                    width = size
                    height = size
                }
            }
        }
    }

    override fun onChange(position: Int) = currentList.getOrNull(position)?.title ?: ""

    private fun saveRecyclerViewState() {
        recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
    }

    fun updateScaling() {
        fontSize = (activity as SimpleActivity).getScaledTextSize()
        iconSize = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.list_icon_size_medium)
        safeNotifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun safeNotifyDataSetChanged() {
        if (isDragging) {
            pendingNotify = true
        } else {
            notifyDataSetChanged()
        }
    }

    private fun restoreRecyclerViewState() {
        recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
    }

    private class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return Conversation.areItemsTheSame(oldItem, newItem)
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return Conversation.areContentsTheSame(oldItem, newItem)
        }
    }

    companion object {
        private const val MAX_UNREAD_BADGE_COUNT = 99
        
        const val VIEW_TYPE_DEFAULT = 0
        const val VIEW_TYPE_RECENT = 1
        const val VIEW_TYPE_PILL = 2
    }
}
