package org.nova.messages.adapters

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
    private var hoveredPosition = -1
    private var pendingUpdate: ArrayList<Conversation>? = null
    private var pendingNotify = false
    private var suppressStateRestoration = false

    private var fontSize = activity.getScaledTextSize()
    private var iconSize = activity.getScaledDimen(org.fossify.commons.R.dimen.list_icon_size_medium)

    private var recyclerViewState: Parcelable? = null

    init {
        setHasStableIds(false) // Must be false because Top 2 are duplicates of items in the grid
        updateDrafts()

        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                if (!suppressStateRestoration) restoreRecyclerViewState()
                updateCustomSelectionBar()
            }
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                if (!suppressStateRestoration) restoreRecyclerViewState()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (!suppressStateRestoration) restoreRecyclerViewState()
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
        shouldSuppressStateRestoration: Boolean = false,
        commitCallback: (() -> Unit)? = null,
    ) {
        if (isDragging) {
            pendingUpdate = newConversations
            return
        }

        // OPTIMIZATION: Check if the list actually changed to avoid redundant DiffUtil work
        if (currentList.size == newConversations.size && currentList.hashCode() == newConversations.hashCode()) {
            commitCallback?.invoke()
            return
        }

        suppressStateRestoration = shouldSuppressStateRestoration
        if (!suppressStateRestoration) saveRecyclerViewState()
        submitList(newConversations.toList()) {
            commitCallback?.invoke()
            suppressStateRestoration = false // Reset after update is committed
        }
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(HOVER_PAYLOAD)) {
            if (activity.config.useNewUi && position >= 2) {
                updatePillHoverState(holder, position)
            }
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private fun updatePillHoverState(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        org.nova.messages.databinding.ItemConversationPillBinding.bind(holder.itemView).apply {
            val isSelected = selectedKeys.contains(conversation.hashCode())
            val isHoverTarget = hoveredPosition == position
            pillSelectionGlow.beVisibleIf(isSelected || isHoverTarget)
            if (isHoverTarget) {
                pillSelectionGlow.background?.applyColorFilter(properPrimaryColor)
                pillSelectionGlow.alpha = 0.5f
            } else {
                pillSelectionGlow.background?.applyColorFilter(properPrimaryColor)
                pillSelectionGlow.alpha = 1.0f
            }
        }
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
        android.util.Log.d("DRAG_DEBUG", "Drag STARTED at position: $position")
        isDragging = true
        pendingNotify = false
        initialDragPosition = position
        lastTargetPosition = position
        hoveredPosition = -1 // Reset hover target at start
        
        // Prevent the dragged View from being recycled by boosting the off-screen cache.
        // This stops ItemTouchHelper from cancelling the drag if we scroll far from the origin.
        recyclerView.setItemViewCacheSize(100)
        
        // Explicitly tell the LayoutManager/RecyclerView not to recycle the view we are holding
        recyclerView.findViewHolderForAdapterPosition(position)?.setIsRecyclable(false)
    }

    fun getInitialDragPosition() = initialDragPosition

    fun updateHoverTarget(toPosition: Int) {
        if (!isDragging || toPosition == hoveredPosition) return
        
        // Block target detection for the top "Recent" cards (0 and 1)
        val validTarget = if (toPosition >= 2 && toPosition != initialDragPosition) toPosition else -1
        
        val oldHover = hoveredPosition
        hoveredPosition = validTarget
        val newHover = hoveredPosition

        // Wrap UI updates in post to avoid IllegalStateException during scroll/layout
        recyclerView.post {
            if (!isDragging) return@post
            if (oldHover != -1) notifyItemChanged(oldHover, HOVER_PAYLOAD)
            if (newHover != -1) notifyItemChanged(newHover, HOVER_PAYLOAD)
        }
    }

    fun onDragEnded() {
        if (!isDragging) {
            android.util.Log.d("DRAG_DEBUG", "onDragEnded called but isDragging was FALSE (already handled or never started)")
            return
        }
        
        // Save state immediately
        val start = initialDragPosition
        val end = hoveredPosition 
        val oldHover = hoveredPosition

        android.util.Log.d("DRAG_DEBUG", "Drag ENDED. Start: $start, End (Hover): $end")

        // Immediately set isDragging to false to prevent multiple calls or interruptions
        isDragging = false
        initialDragPosition = -1
        lastTargetPosition = -1
        hoveredPosition = -1

        // Restore normal cache size
        recyclerView.setItemViewCacheSize(2)
        
        // Allow the previously dragged view to be recycled again
        if (start != -1) {
            recyclerView.findViewHolderForAdapterPosition(start)?.setIsRecyclable(true)
        }

        // Defer all UI updates to post to avoid IllegalStateException during scroll/layout
        recyclerView.post {
            // ... (rest of the post block remains the same)
            // Clear visual hover states immediately
            if (oldHover != -1) notifyItemChanged(oldHover, HOVER_PAYLOAD)
            
            // Force cleanup of the visual item view to prevent it getting stuck on screen
            val cleanupView = { view: View ->
                view.translationX = 0f
                view.translationY = 0f
                view.translationZ = 0f
                view.scaleX = 1f
                view.scaleY = 1f
                view.elevation = 0f
                view.alpha = 1f
            }

            if (start != -1) recyclerView.findViewHolderForAdapterPosition(start)?.itemView?.let { cleanupView(it) }
            if (end != -1) recyclerView.findViewHolderForAdapterPosition(end)?.itemView?.let { cleanupView(it) }
            
            // Comprehensive pass on all visible children
            for (i in 0 until recyclerView.childCount) {
                recyclerView.getChildAt(i)?.let { cleanupView(it) }
            }

            if (start >= 2 && end >= 2 && start != end) {
                // Atomic One-to-One Swap: Direct and Stable
                val list = currentList.toArrayList()
                if (start < list.size && end < list.size) {
                    java.util.Collections.swap(list, start, end)
                    
                    // Save the final order
                    val others = list.drop(2)
                    activity.config.conversationOrder = others.joinToString(",") { it.threadId.toString() }
                    
                    // Finalize the list state with a fresh copy to ensure clean animations
                    // SKIP state restoration here to prevent the "jump" and "flash"
                    suppressStateRestoration = true
                    submitList(list.toList()) {
                        suppressStateRestoration = false
                        // Visual cleanup
                        notifyItemChanged(start, HOVER_PAYLOAD)
                        notifyItemChanged(end, HOVER_PAYLOAD)
                    }
                }
            }

            // Process any refresh that happened during the drag
            pendingUpdate?.let { 
                val update = it
                pendingUpdate = null
                updateConversations(update)
            }
            
            if (pendingNotify) {
                pendingNotify = false
                safeNotifyDataSetChanged()
            }
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
            val lightened = baseColor.adjustColor(1.2f)
            val darkened = baseColor.adjustColor(0.8f)
            val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, baseColor, darkened))
            val r_base = 1000f
            
            if (activity.config.bigContactsOutline && activity.config.useNewUi) {
                val thickness = activity.config.bigContactsOutlineThickness
                val thickStroke = (thickness * resources.displayMetrics.density).toInt()
                gd.cornerRadius = r_base + thickStroke
                gd.setStroke(thickStroke * 2, activity.config.bigContactsOutlineColor)
                
                val layerDrawable = LayerDrawable(arrayOf(gd))
                val inset = -thickStroke + 1
                layerDrawable.setLayerInset(0, inset, inset, inset, inset)
                recentFrame.background = layerDrawable
            } else {
                gd.cornerRadius = r_base
                recentFrame.background = gd
            }
            
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
            val lightened = baseColor.adjustColor(1.2f)
            val darkened = baseColor.adjustColor(0.8f)
            val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, baseColor, darkened))
            val r_base = 1000f
            
            if (activity.config.smallContactsOutline && activity.config.useNewUi) {
                val thickness = activity.config.smallContactsOutlineThickness
                val thickStroke = (thickness * resources.displayMetrics.density).toInt()
                gd.cornerRadius = r_base + thickStroke
                gd.setStroke(thickStroke * 2, activity.config.smallContactsOutlineColor)
                
                val layerDrawable = LayerDrawable(arrayOf(gd))
                val inset = -thickStroke + 1
                layerDrawable.setLayerInset(0, inset, inset, inset, inset)
                pillFrame.background = layerDrawable
            } else {
                gd.cornerRadius = r_base
                pillFrame.background = gd
            }
            
            // Hard Force Elevation (High Visibility)
            pillFrame.elevation = 12f * resources.displayMetrics.density
            pillFrame.translationZ = 6f
            pillFrame.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            pillFrame.clipToOutline = false

            // Selection Glow
            val isSelected = selectedKeys.contains(conversation.hashCode())
            val isHoverTarget = hoveredPosition == position
            pillSelectionGlow.beVisibleIf(isSelected || isHoverTarget)
            if (isHoverTarget) {
                pillSelectionGlow.background?.applyColorFilter(properPrimaryColor)
                pillSelectionGlow.alpha = 0.5f
            } else {
                pillSelectionGlow.background?.applyColorFilter(properPrimaryColor)
                pillSelectionGlow.alpha = 1.0f
            }

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
        if (suppressStateRestoration) return
        recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
    }

    fun updateScaling() {
        fontSize = (activity as SimpleActivity).getScaledTextSize()
        iconSize = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.list_icon_size_medium)
        safeNotifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun safeNotifyDataSetChanged(shouldSuppressStateRestoration: Boolean = false) {
        if (isDragging) {
            pendingNotify = true
        } else {
            suppressStateRestoration = shouldSuppressStateRestoration
            notifyDataSetChanged()
            if (shouldSuppressStateRestoration) {
                // Reset flag soon after notifyDataSetChanged starts processing
                recyclerView.post { suppressStateRestoration = false }
            }
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
        private const val HOVER_PAYLOAD = "hover_payload"
    }
}
