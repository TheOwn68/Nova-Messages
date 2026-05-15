package org.nova.messages.adapters

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.nova.messages.R
import org.nova.messages.activities.NewConversationActivity
import org.nova.messages.activities.SimpleActivity
import org.nova.messages.activities.ThreadActivity
import org.nova.messages.activities.VCardViewerActivity
import org.nova.messages.databinding.*
import org.nova.messages.extensions.*
import org.nova.messages.helpers.*
import org.nova.messages.models.Attachment
import org.nova.messages.models.Message
import org.nova.messages.models.ThreadItem
import org.nova.messages.models.ThreadItem.ThreadDateTime
import org.nova.messages.models.ThreadItem.ThreadError
import org.nova.messages.models.ThreadItem.ThreadSending
import org.nova.messages.models.ThreadItem.ThreadSent

class ThreadAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
    private val isRecycleBin: Boolean,
    private val deleteMessages: (messages: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) -> Unit
) : MyRecyclerViewListAdapter<ThreadItem>(activity, recyclerView, ThreadItemDiffCallback(), itemClick, {}) {

    private val hasMultipleSIMCards = activity.subscriptionManagerCompat().activeSubscriptionInfoList?.size ?: 0 > 1
    private val uiScale get() = (activity as SimpleActivity).uiScale
    private val maxChatBubbleWidth = (activity.usableScreenSize.x * 0.75f).toInt()
    private var fontSize = (activity as SimpleActivity).getScaledTextSize()

    fun updateScaling() {
        fontSize = (activity as SimpleActivity).getScaledTextSize()
        notifyDataSetChanged()
    }

    companion object {
        private const val MAX_MEDIA_HEIGHT_RATIO = 2
        private const val SIM_BITS = 10
        private const val SIM_MASK = (1L shl SIM_BITS) - 1
    }

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_thread

    override fun prepareActionMode(menu: Menu) {
        val selectedItems = getSelectedItems()
        if (selectedItems.isEmpty()) {
            return
        }

        val isOneItemSelected = selectedItems.size == 1
        val selectedMessage = selectedItems.first() as? Message
        val isMms = selectedMessage?.isMMS == true

        menu.findItem(R.id.cab_copy_to_clipboard).isVisible = isOneItemSelected && !isMms
        menu.findItem(R.id.cab_save_as).isVisible = isOneItemSelected && isMms && selectedMessage?.attachment?.attachments?.isNotEmpty() == true
        menu.findItem(R.id.cab_share).isVisible = isOneItemSelected && !isMms
        menu.findItem(R.id.cab_select_text).isVisible = isOneItemSelected && !isMms
        menu.findItem(R.id.cab_properties).isVisible = isOneItemSelected && selectedMessage != null
        menu.findItem(R.id.cab_forward_message).isVisible = isOneItemSelected && selectedMessage != null
        menu.findItem(R.id.cab_restore).isVisible = isRecycleBin
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_to_clipboard -> copyToClipboard()
            R.id.cab_save_as -> saveAs()
            R.id.cab_share -> shareText()
            R.id.cab_select_text -> selectText()
            R.id.cab_properties -> showProperties()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_restore -> askConfirmRestore()
            R.id.cab_forward_message -> forwardMessage()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = currentList.filterIsInstance<Message>().size

    override fun getIsItemSelectable(position: Int) = currentList[position] is Message

    override fun getItemSelectionKey(position: Int): Int? {
        return (currentList.getOrNull(position) as? Message)?.getSelectionKey()
    }

    override fun getItemKeyPosition(key: Int): Int {
        return currentList.indexOfFirst { (it as? Message)?.getSelectionKey() == key }
    }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when (viewType) {
            THREAD_RECEIVED_MESSAGE, THREAD_SENT_MESSAGE -> ItemMessageBinding.inflate(layoutInflater, parent, false)
            THREAD_DATE_TIME -> ItemThreadDateTimeBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_ERROR -> ItemThreadErrorBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_SENDING -> ItemThreadSendingBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_SENT -> ItemThreadSuccessBinding.inflate(layoutInflater, parent, false)
            else -> ItemThreadSuccessBinding.inflate(layoutInflater, parent, false)
        }
        return ThreadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = currentList[position]
        val binding = (holder as ThreadViewHolder).binding
        when (item) {
            is Message -> setupView(holder, binding.root, item)
            is ThreadDateTime -> setupDateTime(binding.root, item)
            is ThreadError -> setupThreadError(binding.root)
            is ThreadSending -> setupThreadSending(binding.root)
            is ThreadSent -> setupThreadSuccess(binding.root, item.delivered)
        }
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
    }

    override fun getItemId(position: Int): Long {
        val item = currentList.getOrNull(position) ?: return 0L
        return getItemIdForRawItem(item)
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = currentList[position]) {
            is Message -> if (item.isReceivedMessage()) THREAD_RECEIVED_MESSAGE else THREAD_SENT_MESSAGE
            is ThreadDateTime -> THREAD_DATE_TIME
            is ThreadError -> THREAD_SENT_MESSAGE_ERROR
            is ThreadSending -> THREAD_SENT_MESSAGE_SENDING
            is ThreadSent -> THREAD_SENT_MESSAGE_SENT
        }
    }

    private fun copyToClipboard() {
        val message = getSelectedItems().first() as Message
        activity.copyToClipboard(message.body)
        finishActMode()
    }

    private fun getSelectedAttachments(): List<Attachment> {
        val message = getSelectedItems().first() as Message
        return message.attachment?.attachments ?: emptyList()
    }

    private fun saveAs() {
        val attachments = getSelectedAttachments()
        (activity as ThreadActivity).saveMMS(attachments)
        finishActMode()
    }

    private fun shareText() {
        val message = getSelectedItems().first() as Message
        activity.shareTextIntent(message.body)
        finishActMode()
    }

    private fun selectText() {
        val message = getSelectedItems().first() as Message
        val binding = DialogSelectTextBinding.inflate(layoutInflater)
        binding.dialogSelectTextValue.text = message.body
        (activity as SimpleActivity).updateAppFonts(binding.root)
        ConfirmationDialog(activity, "", 0, org.fossify.commons.R.string.ok, 0, false, message.body) {
            // Nothing to do
        }
    }

    private fun showProperties() {
        val message = getSelectedItems().first() as Message
        (activity as ThreadActivity).showProperties(message)
        finishActMode()
    }

    private fun askConfirmDelete() {
        val items = getSelectedItems().filterIsInstance<Message>()
        val binding = DialogDeleteConfirmationBinding.inflate(layoutInflater)
        binding.skipTheRecycleBinCheckbox.beVisible()
        (activity as SimpleActivity).updateAppFonts(binding.root)

        val baseString = if (isRecycleBin) {
            org.fossify.commons.R.string.deletion_confirmation
        } else {
            org.fossify.commons.R.string.move_to_recycle_bin_confirmation
        }

        val message = String.format(activity.getString(baseString), items.size)
        ConfirmationDialog(activity, message) {
            deleteMessages(items, !isRecycleBin && !binding.skipTheRecycleBinCheckbox.isChecked, isRecycleBin)
            finishActMode()
        }
    }

    private fun askConfirmRestore() {
        val items = getSelectedItems().filterIsInstance<Message>()
        val message = if (items.size == 1) {
            activity.getString(R.string.restore_confirmation, items.first().senderName)
        } else {
            activity.getString(org.fossify.commons.R.string.files_restored_successfully, items.size)
        }

        ConfirmationDialog(activity, message) {
            deleteMessages(items, false, true)
            finishActMode()
        }
    }

    private fun forwardMessage() {
        val message = getSelectedItems().first() as Message
        val intent = Intent(activity, NewConversationActivity::class.java).apply {
            putExtra(THREAD_TEXT, message.body)
            if (message.isMMS) {
                putExtra(THREAD_ATTACHMENT_URIS, ArrayList(message.attachment?.attachments?.map { it.getUri().toString() } ?: emptyList()))
            }
        }
        activity.startActivity(intent)
        finishActMode()
    }

    private fun getSelectedItems(): ArrayList<ThreadItem> {
        val items = ArrayList<ThreadItem>()
        selectedKeys.forEach { key ->
            val item = currentList.firstOrNull { (it as? Message)?.getSelectionKey() == key }
            if (item != null) {
                items.add(item)
            }
        }
        return items
    }

    fun updateMessages(newMessages: ArrayList<ThreadItem>, searchedMessageId: Int = -1, callback: (() -> Unit)? = null) {
        submitList(newMessages) {
            callback?.invoke()
        }
    }

    private fun setupView(holder: ViewHolder, view: View, message: Message) {
        ItemMessageBinding.bind(view).apply {
            threadMessageHolder.isSelected = selectedKeys.contains(message.getSelectionKey())
            threadMessageBody.apply {
                text = message.body
                beVisibleIf(message.body.isNotEmpty())
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                
                val customTypeface = (activity as SimpleActivity).getCustomTypeface()
                val style = if (message.isScheduled) Typeface.ITALIC else Typeface.NORMAL
                typeface = Typeface.create(customTypeface, style)

                setOnLongClickListener {
                    holder.viewLongClicked()
                    true
                }

                setOnClickListener {
                    holder.viewClicked(message)
                }
            }

            if (message.isReceivedMessage()) {
                setupReceivedMessageView(messageBinding = this)
            } else {
                setupSentMessageView(messageBinding = this, message = message)
            }

            if (message.attachment?.attachments?.isNotEmpty() == true) {
                threadMessageAttachmentsHolder.beVisible()
                threadMessageAttachmentsHolder.removeAllViews()
                for (attachment in message.attachment.attachments) {
                    val mimetype = attachment.mimetype
                    when {
                        mimetype.isImageMimeType() || mimetype.isVideoMimeType() -> setupImageView(holder, binding = this, message, attachment)
                        mimetype.isVCardMimeType() -> setupVCardView(holder, threadMessageAttachmentsHolder, message, attachment)
                        else -> setupFileView(holder, threadMessageAttachmentsHolder, message, attachment)
                    }

                    threadMessagePlayOutline.beVisibleIf(mimetype.startsWith("video/"))
                }
            } else {
                threadMessageAttachmentsHolder.beGone()
                threadMessagePlayOutline.beGone()
            }
        }
    }

    private fun setupReceivedMessageView(messageBinding: ItemMessageBinding) {
        messageBinding.apply {
            with(ConstraintSet()) {
                clone(threadMessageHolder)
                clear(threadMessageWrapper.id, ConstraintSet.END)
                connect(threadMessageWrapper.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                applyTo(threadMessageHolder)
            }

            threadMessageSenderPhoto.beGone()
            threadMessageCarrierWarning.beGone()

            threadMessageBody.apply {
                val backgroundDrawable = AppCompatResources.getDrawable(activity, R.drawable.item_received_background)
                if (backgroundDrawable is GradientDrawable) {
                    backgroundDrawable.setColor(activity.config.receivedBubbleColor)
                } else if (backgroundDrawable != null) {
                    backgroundDrawable.applyColorFilter(activity.config.receivedBubbleColor)
                }
                background = backgroundDrawable
                
                setTextColor(activity.config.receivedBubbleTextColor)
                setLinkTextColor(activity.getProperPrimaryColor())

                val customTypeface = (activity as SimpleActivity).getCustomTypeface()
                typeface = Typeface.create(customTypeface, Typeface.NORMAL)
            }
        }
    }

    private fun setupSentMessageView(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            with(ConstraintSet()) {
                clone(threadMessageHolder)
                clear(threadMessageWrapper.id, ConstraintSet.START)
                connect(threadMessageWrapper.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                applyTo(threadMessageHolder)
            }

            val hasImage = message.attachment?.attachments?.any { it.mimetype.isImageMimeType() } == true
            threadMessageCarrierWarning.beVisibleIf(hasImage)

            threadMessageBody.apply {
                updateLayoutParams<RelativeLayout.LayoutParams> {
                    removeRule(RelativeLayout.END_OF)
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                }

                val backgroundDrawable = AppCompatResources.getDrawable(activity, R.drawable.item_sent_background)
                if (backgroundDrawable is GradientDrawable) {
                    backgroundDrawable.setColor(activity.config.sentBubbleColor)
                } else if (backgroundDrawable != null) {
                    backgroundDrawable.applyColorFilter(activity.config.sentBubbleColor)
                }
                background = backgroundDrawable

                setTextColor(activity.config.sentBubbleTextColor)
                setLinkTextColor(activity.config.sentBubbleTextColor)

                val customTypeface = (activity as SimpleActivity).getCustomTypeface()
                val style = if (message.isScheduled) Typeface.ITALIC else Typeface.NORMAL
                typeface = Typeface.create(customTypeface, style)

                if (message.isScheduled) {
                    val scheduledDrawable = AppCompatResources.getDrawable(activity, org.fossify.commons.R.drawable.ic_clock_vector)?.apply {
                        applyColorFilter(activity.config.sentBubbleTextColor)
                        val size = lineHeight
                        setBounds(0, 0, size, size)
                    }

                    setCompoundDrawables(null, null, scheduledDrawable, null)
                } else {
                    setCompoundDrawables(null, null, null, null)
                }
            }
        }
    }

    private fun setupImageView(holder: ViewHolder, binding: ItemMessageBinding, message: Message, attachment: Attachment) = binding.apply {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()

        val imageView = ItemAttachmentImageBinding.inflate(layoutInflater)
        threadMessageAttachmentsHolder.addView(imageView.root)

        val placeholderDrawable = Color.TRANSPARENT.toDrawable()
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(placeholderDrawable)
            .transform(FitCenter())

        Glide.with(root.context)
            .load(uri)
            .apply(options)
            .dontAnimate()
            .override(maxChatBubbleWidth, (maxChatBubbleWidth * MAX_MEDIA_HEIGHT_RATIO))
            .downsample(DownsampleStrategy.AT_MOST)
            .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean {
                    imageView.attachmentImage.setImageResource(R.drawable.ic_image_vector)
                    imageView.attachmentImage.applyColorFilter(activity.getProperPrimaryColor())
                    threadMessagePlayOutline.beGone()
                    return true
                }

                override fun onResourceReady(dr: android.graphics.drawable.Drawable, a: Any, t: Target<android.graphics.drawable.Drawable>, d: DataSource, i: Boolean) = false
            })
            .into(imageView.attachmentImage)

        imageView.attachmentImage.updateLayoutParams<ViewGroup.LayoutParams> {
            width = maxChatBubbleWidth
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        imageView.attachmentImage.setOnClickListener {
            if (actModeCallback.isSelectable) {
                holder.viewClicked(message)
            } else {
                activity.launchViewIntent(uri, mimetype, attachment.filename)
            }
        }
        imageView.root.setOnLongClickListener {
            holder.viewLongClicked()
            true
        }
    }

    private fun setupVCardView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val uri = attachment.getUri()
        val vCardView = ItemAttachmentVcardBinding.inflate(layoutInflater).apply {
            setupVCardPreview(
                activity = activity,
                uri = uri,
                onClick = {
                    if (actModeCallback.isSelectable) {
                        holder.viewClicked(message)
                    } else {
                        val intent = Intent(activity, VCardViewerActivity::class.java).also {
                            it.putExtra(EXTRA_VCARD_URI, uri)
                        }
                        activity.startActivity(intent)
                    }
                },
                onLongClick = { holder.viewLongClicked() }
            )
        }.root

        parent.addView(vCardView)
    }

    private fun setupFileView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()
        val attachmentView = ItemAttachmentDocumentBinding.inflate(layoutInflater).apply {
            setupDocumentPreview(
                uri = uri,
                title = attachment.filename,
                mimeType = attachment.mimetype,
                onClick = {
                    if (actModeCallback.isSelectable) {
                        holder.viewClicked(message)
                    } else {
                        activity.launchViewIntent(uri, mimetype, attachment.filename)
                    }
                },
                onLongClick = { holder.viewLongClicked() }
            )
        }.root

        parent.addView(attachmentView)
    }

    private fun setupDateTime(view: View, dateTime: ThreadDateTime) {
        ItemThreadDateTimeBinding.bind(view).apply {
            threadDateTime.apply {
                text = (dateTime.date * 1000L).formatDateOrTime(
                    context = context,
                    hideTimeOnOtherDays = false,
                    showCurrentYear = false
                )
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
                val customTypeface = (activity as SimpleActivity).getCustomTypeface()
                typeface = Typeface.create(customTypeface, Typeface.NORMAL)
            }
            threadDateTime.setTextColor(activity.getProperTextColor())

            threadSimIcon.beVisibleIf(hasMultipleSIMCards)
            threadSimNumber.beVisibleIf(hasMultipleSIMCards)
            if (hasMultipleSIMCards) {
                threadSimNumber.text = dateTime.simID
                threadSimNumber.setTextColor(activity.getProperTextColor().getContrastColor())
                threadSimIcon.applyColorFilter(activity.getProperTextColor())
                threadSimNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.6f)
                
                threadSimIcon.updateLayoutParams {
                    width = (fontSize * 1.2f).toInt()
                    height = (fontSize * 1.2f).toInt()
                }
            }
        }
    }

    private fun setupThreadSuccess(view: View, isDelivered: Boolean) {
        ItemThreadSuccessBinding.bind(view).apply {
            threadSuccess.setImageResource(if (isDelivered) R.drawable.ic_check_double_vector else org.fossify.commons.R.drawable.ic_check_vector)
            threadSuccess.applyColorFilter(Color.GRAY)
        }
    }

    private fun setupThreadError(view: View) {
        val binding = ItemThreadErrorBinding.bind(view)
        binding.threadError.setTextColor(activity.getProperTextColor())
    }

    private fun setupThreadSending(view: View) {
        ItemThreadSendingBinding.bind(view).threadSending.apply {
            setTextColor(activity.getProperTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val binding = (holder as ThreadViewHolder).binding
            if (binding is ItemMessageBinding) {
                Glide.with(activity).clear(binding.threadMessageSenderPhoto)
            }
        }
    }

    inner class ThreadViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)

    private fun getItemIdForRawItem(item: ThreadItem): Long {
        return when (item) {
            is Message -> item.getStableId()
            is ThreadDateTime -> {
                val sim = (item.simID.hashCode().toLong() and SIM_MASK)
                val key = (item.date.toLong() shl SIM_BITS) or sim
                generateStableId(THREAD_DATE_TIME, key)
            }
            is ThreadError -> generateStableId(THREAD_SENT_MESSAGE_ERROR, item.messageId)
            is ThreadSending -> generateStableId(THREAD_SENT_MESSAGE_SENDING, item.messageId)
            is ThreadSent -> generateStableId(THREAD_SENT_MESSAGE_SENT, item.messageId)
        }
    }
}

private class ThreadItemDiffCallback : DiffUtil.ItemCallback<ThreadItem>() {

    override fun areItemsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadError -> oldItem.messageId == (newItem as ThreadError).messageId
            is ThreadSent -> oldItem.messageId == (newItem as ThreadSent).messageId
            is ThreadSending -> oldItem.messageId == (newItem as ThreadSending).messageId
            is Message -> Message.areItemsTheSame(oldItem, newItem as Message)
            is ThreadDateTime -> {
                val new = newItem as ThreadDateTime
                oldItem.date == new.date && oldItem.simID == new.simID
            }
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadSending -> true
            is ThreadDateTime -> oldItem.simID == (newItem as ThreadDateTime).simID
            is ThreadError -> oldItem.messageText == (newItem as ThreadError).messageText
            is ThreadSent -> oldItem.delivered == (newItem as ThreadSent).delivered
            is Message -> Message.areContentsTheSame(oldItem, newItem as Message)
            else -> false
        }
    }
}
