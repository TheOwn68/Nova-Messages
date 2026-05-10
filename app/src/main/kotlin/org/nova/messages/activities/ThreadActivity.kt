package org.nova.messages.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.provider.Telephony.Sms.MESSAGE_TYPE_QUEUED
import android.provider.Telephony.Sms.STATUS_NONE
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionInfo
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.nova.messages.BuildConfig
import org.nova.messages.R
import org.nova.messages.adapters.AttachmentsAdapter
import org.nova.messages.adapters.AutoCompleteTextViewAdapter
import org.nova.messages.adapters.ConversationsAdapter
import org.nova.messages.adapters.ThreadAdapter
import org.nova.messages.databinding.ActivityThreadBinding
import org.nova.messages.databinding.ItemSelectedContactBinding
import org.nova.messages.dialogs.InvalidNumberDialog
import org.nova.messages.dialogs.MessageDetailsDialog
import org.nova.messages.dialogs.RenameConversationDialog
import org.nova.messages.dialogs.ScheduleMessageDialog
import org.nova.messages.extensions.*
import org.nova.messages.helpers.*
import com.klinker.android.send_message.Settings
import org.nova.messages.messaging.isLongMmsMessage
import org.nova.messages.messaging.isShortCodeWithLetters
import org.nova.messages.models.*
import org.nova.messages.models.ThreadItem.ThreadDateTime
import org.nova.messages.models.ThreadItem.ThreadError
import org.nova.messages.models.ThreadItem.ThreadSending
import org.nova.messages.models.ThreadItem.ThreadSent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.joda.time.DateTime
import java.io.File
import org.fossify.commons.models.SimpleContact
import org.nova.messages.messaging.*

class ThreadActivity : SimpleActivity() {

    private var threadId = 0L
    private var currentSIMCardIndex = 0
    private var isActivityVisible = false
    private var refreshedSinceSent = false
    private var threadItems = ArrayList<ThreadItem>()
    private var bus: EventBus? = null
    private var conversation: Conversation? = null
    private var participants = ArrayList<SimpleContact>()
    private var privateContactsMap = HashMap<Int, SimpleContact>()
    private var messages = ArrayList<Message>()
    private val availableSIMCards = ArrayList<SIMCard>()
    private var pendingAttachmentsToSave: List<Attachment>? = null
    private var capturedImageUri: Uri? = null
    private var loadingOlderMessages = false
    private var allMessagesFetched = false
    private var isJumpingToMessage = false
    private var isRecycleBin = false
    private var isLaunchedFromShortcut = false

    private var isScheduledMessage: Boolean = false
    private var messageToResend: Long? = null
    private var scheduledMessage: Message? = null
    private lateinit var scheduledDateTime: DateTime

    private var isAttachmentPickerVisible = false

    private val binding by viewBinding(ActivityThreadBinding::inflate)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finish()
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()
        refreshMenuItems()
        setupEdgeToEdge(
            padBottomImeAndSystem = listOf(
                binding.messageHolder.root,
                binding.shortCodeHolder.root
            )
        )
        setupMessagingEdgeToEdge()
        setupMaterialScrollListener(null, binding.threadAppbar)

        val extras = intent.extras
        if (extras == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            finish()
            return
        }

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        intent.getStringExtra(THREAD_TITLE)?.let {
            binding.threadToolbarTitle.text = it
        }
        isRecycleBin = intent.getBooleanExtra(IS_RECYCLE_BIN, false)
        isLaunchedFromShortcut = intent.getBooleanExtra(IS_LAUNCHED_FROM_SHORTCUT, false)

        bus = EventBus.getDefault()
        bus!!.register(this)

        loadConversation()
        setupAttachmentPickerView()
        hideAttachmentPicker()
        maybeSetupRecycleBinView()
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing || isDestroyed) return
        
        currentThreadId = threadId
        setupTopAppBar(
            topAppBar = binding.threadAppbar,
            navigationIcon = NavigationIcon.Arrow,
            topBarColor = Color.TRANSPARENT
        )
        binding.threadToolbar.navigationIcon?.setTint(Color.WHITE)
        binding.threadToolbar.overflowIcon?.setTint(Color.WHITE)
        binding.threadAppbar.setBackgroundResource(R.drawable.nova_topbar_bg)
        binding.threadToolbar.setTitleTextAppearance(this, R.style.NovaToolbarTitle)
        binding.threadToolbar.setTitleTextColor(Color.WHITE)
        
        // Ensure this listener is set last to avoid being overwritten by setupTopAppBar
        binding.threadToolbar.setNavigationOnClickListener {
            finish()
        }

        isActivityVisible = true

        notificationManager.cancel(threadId.hashCode())

        ensureBackgroundThread {
            val newConv = conversationsDB.getConversationWithThreadId(threadId)
            if (newConv != null) {
                conversation = newConv
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    setupThreadTitle()
                }
            }

            val smsDraft = getSmsDraft(threadId)
            if (smsDraft.isNotEmpty()) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    binding.messageHolder.threadTypeMessage.setText(smsDraft)
                    binding.messageHolder.threadTypeMessage.setSelection(smsDraft.length)
                }
            }

            markThreadMessagesRead(threadId)
        }

        binding.messageHolder.root.setBackgroundColor(Color.TRANSPARENT)
        binding.shortCodeHolder.root.setBackgroundColor(Color.TRANSPARENT)
        updateAppFonts(binding.root)

        binding.threadToolbarTitle.updateLayoutParams<Toolbar.LayoutParams> {
            marginEnd = 100.getScaledPx()
        }
        setupScaledToolbar(binding.threadToolbar)

        binding.scrollToBottomFab.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
            marginEnd = 20.getScaledPx()
            bottomMargin = 20.getScaledPx()
        }

        binding.messageHolder.novaMessageInputBar.apply {
            minimumHeight = 55.getScaledPx()
        }

        getOrCreateThreadAdapter().updateScaling()

        val bottomAnim = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom)
        binding.messageHolder.root.startAnimation(bottomAnim)
        android.util.Log.d("NAV_TRACE", "ThreadActivity.onResume finished")
    }

    override fun onPause() {
        android.util.Log.d("NAV_TRACE", "ThreadActivity.onPause starting")
        super.onPause()
        currentThreadId = 0L
        saveDraftMessage()
        isActivityVisible = false
        android.util.Log.d("NAV_TRACE", "ThreadActivity.onPause finished")
    }

    override fun onStop() {
        android.util.Log.d("NAV_TRACE", "ThreadActivity.onStop starting")
        super.onStop()
        saveDraftMessage()
        bus?.post(Events.RefreshConversations())
        android.util.Log.d("NAV_TRACE", "ThreadActivity.onStop finished")
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode != Activity.RESULT_OK) return
        
        val data = resultData?.data
        val clipData = resultData?.clipData
        messageToResend = null

        try {
            when (requestCode) {
                CAPTURE_PHOTO_INTENT -> {
                    if (capturedImageUri != null) {
                        addAttachment(capturedImageUri!!)
                    }
                }
                CAPTURE_VIDEO_INTENT,
                PICK_DOCUMENT_INTENT,
                CAPTURE_AUDIO_INTENT,
                PICK_PHOTO_INTENT,
                PICK_VIDEO_INTENT -> {
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            addAttachment(clipData.getItemAt(i).uri)
                        }
                    } else if (data != null) {
                        addAttachment(data)
                    }
                }

                PICK_CONTACT_INTENT -> data?.let { addContactAttachment(it) }
                PICK_SAVE_FILE_INTENT -> saveAttachments(resultData!!)
                PICK_SAVE_DIR_INTENT -> saveAttachments(resultData!!)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun setupCachedMessages(callback: () -> Unit) {
        ensureBackgroundThread {
            messages = try {
                if (isRecycleBin) {
                    messagesDB.getThreadMessagesFromRecycleBin(threadId)
                } else {
                    if (config.useRecycleBin) {
                        messagesDB.getNonRecycledThreadMessages(threadId)
                    } else {
                        messagesDB.getThreadMessages(threadId)
                    }
                }.toMutableList() as ArrayList<Message>
            } catch (e: Exception) {
                ArrayList()
            }
            clearExpiredScheduledMessages(threadId, messages)
            messages.removeAll { it.isScheduled && it.millis() < System.currentTimeMillis() }

            messages.sortBy { it.date }
            if (messages.size > MESSAGES_LIMIT) {
                messages = ArrayList(messages.takeLast(MESSAGES_LIMIT))
            }

            setupParticipants()
            setupAdapter()

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (messages.isEmpty() && !isSpecialNumber()) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    binding.messageHolder.threadTypeMessage.requestFocus()
                }

                setupThreadTitle()
                setupSIMSelector()
                updateMessageType()
                callback()
            }
        }
    }

    private fun setupThread(callback: () -> Unit) {
        if (conversation == null && isLaunchedFromShortcut) {
            if (isTaskRoot) {
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(this)
                }
            }
            finish()
            return
        }
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            privateContacts.forEach { privateContactsMap[it.contactId] = it }

            val cachedMessagesCode = messages.clone().hashCode()
            if (!isRecycleBin) {
                messages = getMessages(threadId)
                if (config.useRecycleBin) {
                    val recycledMessages = messagesDB.getThreadMessagesFromRecycleBin(threadId)
                    messages = messages.filterNotInByKey(recycledMessages) { it.getStableId() }
                }
            }

            val hasParticipantWithoutName = participants.any { contact ->
                contact.phoneNumbers.map { it.normalizedNumber }.contains(contact.name)
            }

            try {
                if (participants.isNotEmpty() && messages.hashCode() == cachedMessagesCode && !hasParticipantWithoutName) {
                    setupAdapter()
                    runOnUiThread { 
                        if (!isFinishing && !isDestroyed) {
                            callback()
                        }
                    }
                    return@ensureBackgroundThread
                }
            } catch (ignored: Exception) {
            }

            setupParticipants()
            setupAdapter()

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setupThreadTitle()
                setupSIMSelector()
                updateMessageType()
                callback()
            }
        }
    }

    private fun getOrCreateThreadAdapter(): ThreadAdapter {
        var currAdapter = binding.threadMessagesList.adapter
        if (currAdapter == null) {
            currAdapter = ThreadAdapter(
                activity = this,
                recyclerView = binding.threadMessagesList,
                itemClick = { handleItemClick(it) },
                isRecycleBin = isRecycleBin,
                deleteMessages = { messages, toRecycleBin, fromRecycleBin ->
                    deleteMessages(
                        messages,
                        toRecycleBin,
                        fromRecycleBin
                    )
                }
            )

            binding.threadMessagesList.adapter = currAdapter
        }
        return currAdapter as ThreadAdapter
    }

    private fun setupAdapter() {
        threadItems = getThreadItems()

        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            refreshMenuItems()
            getOrCreateThreadAdapter().apply {
                val isFirstLoad = currentList.isEmpty()
                val isNewMessageAtBottom = currentList.isNotEmpty() && threadItems.size > currentList.size
                
                updateMessages(threadItems, callback = {
                    if (isFinishing || isDestroyed) return@updateMessages
                    if (isFirstLoad || isNewMessageAtBottom) {
                        binding.threadMessagesList.post {
                            if (isFinishing || isDestroyed) return@post
                            binding.threadMessagesList.scrollToPosition(threadItems.size - 1)
                            binding.threadMessagesList.postDelayed({
                                if (isFinishing || isDestroyed) return@postDelayed
                                binding.threadMessagesList.scrollToPosition(threadItems.size - 1)
                            }, 100)
                        }
                    }
                })
            }
        }

        SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
            if (isFinishing || isDestroyed) return@getAvailableContacts
            contacts.addAll(privateContactsMap.values)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val adapter = AutoCompleteTextViewAdapter(this, contacts)
                binding.addContactOrNumber.setAdapter(adapter)
                binding.addContactOrNumber.imeOptions = EditorInfo.IME_ACTION_NEXT
                binding.addContactOrNumber.setOnItemClickListener { _, _, position, _ ->
                    val currContacts = (binding.addContactOrNumber.adapter as AutoCompleteTextViewAdapter).resultList
                    val contact = currContacts.getOrNull(position) ?: return@setOnItemClickListener
                    val contactId = contact.contactId
                    if (participants.any { it.contactId == contactId }) {
                        return@setOnItemClickListener
                    }
                    addParticipant(contact)
                    binding.addContactOrNumber.setText("")
                }
            }
        }
    }

    private fun addParticipant(contact: SimpleContact) {
        participants.add(contact)
        updateParticipants()
    }

    private fun updateParticipants() {
        participants = participants.distinctBy { it.contactId }.toArrayList()
        showSelectedContacts()
        setupAdapter()
        updateMessageType()
        setupThreadTitle()
        checkSendMessageAvailability()
    }

    private fun setupScrollListener() {
        binding.threadMessagesList.onScroll(
            onScrolled = { _, _ ->
                tryLoadMoreMessages()
                val layoutManager = binding.threadMessagesList.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition()
                val isCloseToBottom =
                    lastVisibleItemPosition >= getOrCreateThreadAdapter().itemCount - SCROLL_TO_BOTTOM_FAB_LIMIT
                val fab = binding.scrollToBottomFab
                if (isCloseToBottom) fab.beGone() else fab.beVisible()
            },
            onScrollStateChanged = { newState ->
                if (newState == RecyclerView.SCROLL_STATE_IDLE) tryLoadMoreMessages()
            }
        )
    }

    private fun handleItemClick(any: Any) {
        when {
            any is Message && any.isScheduled -> showScheduledMessageInfo(any)
            any is ThreadError -> {
                binding.messageHolder.threadTypeMessage.setText(any.messageText)
                binding.messageHolder.threadTypeMessage.setSelection(any.messageText.length)
                messageToResend = any.messageId
                checkSendMessageAvailability()
            }

            any is Message -> {
                if (any.attachment?.attachments?.isNotEmpty() == true) {
                    val firstAttachment = any.attachment.attachments.first()
                    val mimetype = firstAttachment.mimetype
                    if (mimetype.isImageMimeType() || mimetype.isVideoMimeType()) {
                        launchViewIntent(firstAttachment.getUri(), mimetype, firstAttachment.filename)
                    }
                }
            }
        }
    }

    private fun tryLoadMoreMessages() {
        if (isJumpingToMessage) return
        val layoutManager = binding.threadMessagesList.layoutManager as LinearLayoutManager
        if (layoutManager.findFirstVisibleItemPosition() <= PREFETCH_THRESHOLD) {
            loadMoreMessages()
        }
    }

    private fun loadMoreMessages() {
        if (messages.isEmpty() || allMessagesFetched || loadingOlderMessages) return
        loadingOlderMessages = true
        val cutoff = messages.first().date
        ensureBackgroundThread {
            fetchOlderMessages(cutoff)
            threadItems = getThreadItems()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loadingOlderMessages = false
                getOrCreateThreadAdapter().updateMessages(threadItems)
            }
        }
    }

    private fun fetchOlderMessages(cutoff: Int): List<Message> {
        val older = getMessages(threadId, cutoff)
            .filterNotInByKey(messages) { it.getStableId() }

        if (older.isEmpty()) {
            allMessagesFetched = true
            return emptyList()
        }

        messages.addAll(0, older)
        messages.sortBy { it.date }
        return older
    }

    private fun loadConversation() {
        handlePermission(PERMISSION_READ_PHONE_STATE) { granted ->
            if (granted) {
                setupButtons()
                ensureBackgroundThread {
                    conversation = conversationsDB.getConversationWithThreadId(threadId)
                    setupThread {
                        val searchedMessageId = intent.getLongExtra(SEARCHED_MESSAGE_ID, -1L)
                        intent.removeExtra(SEARCHED_MESSAGE_ID)
                        if (searchedMessageId != -1L) {
                            jumpToMessage(searchedMessageId)
                        }
                    }
                    runOnUiThread {
                        setupScrollListener()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun setupConversation() {
    }

    private fun setupButtons() = binding.apply {
        updateTextColors(threadMessagesList)
        val textColor = Color.WHITE

        binding.messageHolder.apply {
            threadSendMessage.setTextColor(textColor)
            threadSendMessage.compoundDrawables.forEach {
                it?.applyColorFilter(textColor)
            }

            confirmManageContacts.applyColorFilter(getProperTextColor())
            threadAddAttachment.applyColorFilter(textColor)

            val properPrimaryColor = getProperPrimaryColor()
            threadMessagesFastscroller.updateColors(properPrimaryColor)

            threadCharacterCounter.beVisibleIf(config.showCharacterCounter)
            threadCharacterCounter.setTextColor(textColor)
            threadCharacterCounter.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())

            threadTypeMessage.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
            threadTypeMessage.setTextColor(Color.WHITE)
            threadTypeMessage.setHintTextColor(Color.parseColor("#888888"))
            threadSendMessage.setOnClickListener {
                sendMessage()
            }

            threadSendMessage.setOnLongClickListener {
                if (!isScheduledMessage) {
                    launchScheduleSendDialog()
                }
                true
            }

            threadSendMessage.isClickable = false
            threadTypeMessage.onTextChangeListener {
                messageToResend = null
                checkSendMessageAvailability()
                val messageString = if (config.useSimpleCharacters) {
                    it.normalizeString()
                } else {
                    it
                }
                val messageLength = SmsMessage.calculateLength(messageString, false)
                @SuppressLint("SetTextI18n")
                threadCharacterCounter.text = "${messageLength[2]}/${messageLength[0]}"
            }

            if (config.sendOnEnter) {
                threadTypeMessage.inputType = EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
                threadTypeMessage.imeOptions = EditorInfo.IME_ACTION_SEND
                threadTypeMessage.setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_SEND) {
                        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                        return@setOnEditorActionListener true
                    }
                    false
                }

                threadTypeMessage.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                        sendMessage()
                        return@setOnKeyListener true
                    }
                    false
                }
            }

            confirmManageContacts.setOnClickListener {
                hideKeyboard()
                threadAddContacts.beGone()

                val numbers = HashSet<String>()
                participants.forEach { contact ->
                    contact.phoneNumbers.forEach {
                        numbers.add(it.normalizedNumber)
                    }
                }

                val newThreadId = getThreadId(numbers)
                if (threadId != newThreadId) {
                    hideKeyboard()
                    Intent(this@ThreadActivity, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, newThreadId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(this)
                    }
                }
            }

            threadTypeMessage.setText(intent.getStringExtra(THREAD_TEXT))
            threadAddAttachment.setOnClickListener {
                if (attachmentPickerHolder.isVisible()) {
                    isAttachmentPickerVisible = false
                    hideAttachmentPicker()
                    window.insetsController(binding.messageHolder.threadTypeMessage)
                        .show(WindowInsetsCompat.Type.ime())
                } else {
                    isAttachmentPickerVisible = true
                    showAttachmentPicker()
                    window.insetsController(binding.messageHolder.threadTypeMessage)
                        .hide(WindowInsetsCompat.Type.ime())
                }
                binding.messageHolder.threadTypeMessage.requestApplyInsets()
            }

            if (intent.extras?.containsKey(THREAD_ATTACHMENT_URI) == true) {
                val uri = intent.getStringExtra(THREAD_ATTACHMENT_URI)!!.toUri()
                addAttachment(uri)
            } else if (intent.extras?.containsKey(THREAD_ATTACHMENT_URIS) == true) {
                val uris = intent.getParcelableArrayListExtra<Uri>(THREAD_ATTACHMENT_URIS)
                uris?.forEach { addAttachment(it) }
            }
            scrollToBottomFab.setOnClickListener {
                scrollToBottom()
            }
            scrollToBottomFab.backgroundTintList = ColorStateList.valueOf(getBottomBarColor())

        }

        setupScheduleSendUi()
    }

    private fun askForExactAlarmPermissionIfNeeded(callback: () -> Unit = {}) {
        if (isSPlus()) {
            val alarmManager: AlarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                callback()
            } else {
                PermissionRequiredDialog(
                    activity = this,
                    textId = org.fossify.commons.R.string.allow_alarm_scheduled_messages,
                    positiveActionCallback = {
                        openRequestExactAlarmSettings(BuildConfig.APPLICATION_ID)
                    },
                )
            }
        } else {
            callback()
        }
    }

    private fun launchScheduleSendDialog(dateTime: DateTime = DateTime.now().plusMinutes(10)) {
        askForExactAlarmPermissionIfNeeded {
            ScheduleMessageDialog(this, dateTime) {
                if (it != null) {
                    scheduledDateTime = it
                    isScheduledMessage = true
                    updateMessageType()
                    checkSendMessageAvailability()
                }
            }
        }
    }

    private fun setupScheduleSendUi() {
        binding.messageHolder.scheduledMessageButton.setOnClickListener {
            launchScheduleSendDialog(scheduledDateTime)
        }

        binding.messageHolder.discardScheduledMessage.setOnClickListener {
            isScheduledMessage = false
            updateMessageType()
            checkSendMessageAvailability()
        }
    }

    private fun showScheduledMessageInfo(message: Message) {
        // ...
    }

    private fun clearCurrentMessage() {
        binding.messageHolder.threadTypeMessage.setText("")
        getAttachmentsAdapter()?.clear()
        checkSendMessageAvailability()
    }

    private fun setupOptionsMenu() {
        binding.threadToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.dial_number -> dialNumber()
                R.id.archive -> archiveThread()
                R.id.unarchive -> unarchiveThread()
                R.id.manage_people -> managePeople()
                R.id.add_number_to_contact -> addNumberToContact()
                R.id.copy_number -> copyNumberToClipboard()
                R.id.rename_conversation -> renameConversation()
                R.id.conversation_details -> launchConversationDetails(threadId)
                R.id.mark_as_unread -> markAsUnread()
                R.id.block_number -> tryBlocking()
                R.id.delete -> askConfirmDelete()
                R.id.restore -> restoreMessages()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    private fun refreshMenuItems() {
        val firstPhoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        val archiveAvailable = config.isArchiveAvailable
        binding.threadToolbar.menu.apply {
            findItem(R.id.delete)?.isVisible = threadItems.isNotEmpty()
            findItem(R.id.restore)?.isVisible = threadItems.isNotEmpty() && isRecycleBin
            findItem(R.id.archive)?.isVisible =
                threadItems.isNotEmpty() && conversation?.isArchived == false && !isRecycleBin && archiveAvailable
            findItem(R.id.unarchive)?.isVisible =
                threadItems.isNotEmpty() && conversation?.isArchived == true && !isRecycleBin && archiveAvailable
            findItem(R.id.conversation_details)?.isVisible = conversation != null && !isRecycleBin
            findItem(R.id.block_number)?.title =
                getString(org.fossify.commons.R.string.block_number)
            findItem(R.id.block_number)?.isVisible = !isRecycleBin
            findItem(R.id.dial_number)?.isVisible =
                participants.size == 1 && !isSpecialNumber() && !isRecycleBin
            findItem(R.id.manage_people)?.isVisible = !isSpecialNumber() && !isRecycleBin
            findItem(R.id.mark_as_unread)?.isVisible = threadItems.isNotEmpty() && !isRecycleBin

            // allow saving number in cases when we don't have it stored yet
            findItem(R.id.add_number_to_contact)?.isVisible =
                participants.size == 1 && participants.first().name == firstPhoneNumber && !isRecycleBin
            findItem(R.id.copy_number)?.isVisible =
                participants.size == 1 && !firstPhoneNumber.isNullOrEmpty() && !isRecycleBin
        }
    }

    private fun checkSendMessageAvailability() {
        val text = binding.messageHolder.threadTypeMessage.text.toString().trim()
        val hasText = text.isNotEmpty()
        val hasAttachments = getAttachmentSelections().isNotEmpty()
        val isAttachmentPending = getAttachmentSelections().any { it.isPending }
        val isSendable = (hasText || hasAttachments) && !isAttachmentPending && participants.isNotEmpty()

        binding.messageHolder.threadSendMessage.apply {
            alpha = if (isSendable) 1.0f else 0.4f
            isClickable = isSendable
        }

        binding.messageHolder.scheduledMessageHolder.beVisibleIf(isScheduledMessage)
        if (isScheduledMessage) {
            val format = "${config.dateFormat}, ${getTimeFormat()}"
            binding.messageHolder.scheduledMessageButton.text = scheduledDateTime.toString(format)
        }
    }

    private fun setupParticipants() {
        ensureBackgroundThread {
            participants = getThreadParticipants(threadId, privateContactsMap)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                showSelectedContacts()
                setupThreadTitle()
                checkSendMessageAvailability()
                refreshMessages()
            }
        }
    }

    private fun showSelectedContacts() {
        binding.selectedContacts.removeAllViews()
        participants.forEach { contact ->
            val contactBinding = ItemSelectedContactBinding.inflate(layoutInflater, binding.selectedContacts, false)
            contactBinding.selectedContactName.text = contact.name
            contactBinding.selectedContactRemove.setOnClickListener {
                participants.remove(contact)
                updateParticipants()
            }
            binding.selectedContacts.addView(contactBinding.root)
        }

        binding.threadAddContacts.beVisibleIf(participants.size > 1 || conversation == null)
    }

    private fun setupThreadTitle() {
        val title = conversation?.title
        val finalTitle = if (!title.isNullOrEmpty()) {
            title
        } else {
            participants.getThreadTitle()
        }

        binding.threadToolbar.title = null
        binding.threadToolbarTitle.text = finalTitle
    }

    private fun isSpecialNumber() = participants.size == 1 && isShortCodeWithLetters(participants.first().phoneNumbers.first().normalizedNumber)

    private fun jumpToMessage(messageId: Long) {
        ensureBackgroundThread {
            val messageIndex = messages.indexOfFirstOrNull { it.id == messageId }
            if (messageIndex != null) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    getOrCreateThreadAdapter().updateMessages(getThreadItems(), messageIndex)
                }
            }
        }
    }

    private fun deleteMessages(messages: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) {
        ensureBackgroundThread {
            if (fromRecycleBin) {
                messages.forEach { restoreMessageFromRecycleBin(it.id) }
            } else if (toRecycleBin) {
                messages.forEach { moveMessageToRecycleBin(it.id) }
            } else {
                // delete permanently
            }
            refreshMessages()
        }
    }

    private fun saveSmsDraftInternal(text: String, threadId: Long) {
        ensureBackgroundThread {
            saveSmsDraft(text, threadId)
        }
    }

    private fun saveDraftMessage() {
        val text = binding.messageHolder.threadTypeMessage.text.toString()
        saveSmsDraftInternal(text, threadId)
    }

    private fun getFileSize(uri: Uri): Long {
        return this.getFileSizeFromUri(uri)
    }

    private fun getAttachmentSelections() = getAttachmentsAdapter()?.attachments ?: emptyList()

    private fun addAttachment(uri: Uri) {
        val id = uri.toString()
        if (getAttachmentSelections().any { it.id == id }) {
            return
        }

        // Try to keep URI permissions alive
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Some apps don't support persistable permissions, ignore
        }

        var mimeType = contentResolver.getType(uri)
        if (mimeType == null) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (extension.isNotEmpty()) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            }
        }

        // Final fallback for mimeType
        if (mimeType == null) {
            mimeType = "image/jpeg" // Assume image if we can't tell, the compressor will handle if it's not
        }

        val isImage = mimeType.isImageMimeType()
        val isGif = mimeType.isGifMimeType()
        val isRawImage = mimeType.contains("dng", true) || mimeType.contains("raw", true)

        val fileSize = getFileSizeFromUri(uri)
        val mmsFileSizeLimit = config.mmsFileSizeLimit
        
        // If not an image (or a type we won't compress like GIF/RAW), check the limit strictly
        if (mmsFileSizeLimit != FILE_SIZE_NONE && fileSize > mmsFileSizeLimit && (!isImage || isGif || isRawImage)) {
            toast(R.string.attachment_sized_exceeds_max_limit, length = Toast.LENGTH_LONG)
            return
        }

        var adapter = getAttachmentsAdapter()
        if (adapter == null) {
            adapter = AttachmentsAdapter(
                activity = this,
                recyclerView = binding.messageHolder.threadAttachmentsRecyclerview,
                onAttachmentsRemoved = {
                    if (getAttachmentSelections().isEmpty()) {
                        binding.messageHolder.threadAttachmentsRecyclerview.beGone()
                    }
                    checkSendMessageAvailability()
                },
                onReady = { 
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        checkSendMessageAvailability() 
                    }
                }
            )
            binding.messageHolder.threadAttachmentsRecyclerview.adapter = adapter
        }

        binding.messageHolder.threadAttachmentsRecyclerview.beVisible()
        val attachment = AttachmentSelection(
            id = id,
            uri = uri,
            mimetype = mimeType,
            filename = getFilenameFromUri(uri),
            isPending = isImage && !isGif && !isRawImage
        )
        adapter.addAttachment(attachment)
        
        // Safety: if it's not an image that needs compression, it's ready immediately
        if (!attachment.isPending) {
            checkSendMessageAvailability()
        }
    }

    fun saveMMS(attachments: List<Attachment>) {
        pendingAttachmentsToSave = attachments
        if (attachments.size == 1) {
            val attachment = attachments.first()
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = attachment.mimetype
                putExtra(Intent.EXTRA_TITLE, attachment.filename)
            }
            startActivityForResult(intent, PICK_SAVE_FILE_INTENT)
        } else {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, PICK_SAVE_DIR_INTENT)
        }
    }

    private fun saveAttachments(resultData: Intent) {
        applicationContext.contentResolver.takePersistableUriPermission(
            resultData.data!!, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val destinationUri = resultData.data ?: return
        ensureBackgroundThread {
            try {
                if (DocumentsContract.isTreeUri(destinationUri)) {
                    val outputDir = DocumentFile.fromTreeUri(this, destinationUri)
                        ?: return@ensureBackgroundThread
                    pendingAttachmentsToSave?.forEach { attachment ->
                        val documentFile = outputDir.createFile(
                            attachment.mimetype,
                            attachment.filename.takeIf { it.isNotBlank() }
                                ?: attachment.uriString.getFilenameFromPath()
                        ) ?: return@forEach
                        copyToUri(src = attachment.getUri(), dst = documentFile.uri)
                    }
                } else {
                    copyToUri(pendingAttachmentsToSave!!.first().getUri(), resultData.data!!)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun getAttachmentsAdapter(): AttachmentsAdapter? {
        return binding.messageHolder.threadAttachmentsRecyclerview.adapter as? AttachmentsAdapter
    }

    private fun sendMessage() {
        val text = binding.messageHolder.threadTypeMessage.text.toString().trim()
        val attachments = getAttachmentSelections()

        if (text.isEmpty() && attachments.isEmpty()) {
            return
        }

        if (participants.isEmpty() || threadId == 0L) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }

        ensureBackgroundThread {
            val subscriptionId = currentSIMCardIndex // use selected SIM
            if (attachments.isNotEmpty()) {
                sendMmsMessage(text, attachments, subscriptionId)
            } else {
                sendNormalMessage(text, subscriptionId)
            }
            
            refreshMessages()
            refreshConversations()

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                clearCurrentMessage()
                scrollToBottom()
            }
        }
    }

    private fun sendNormalMessage(text: String, subscriptionId: Int) {
        val addresses = participants.flatMap { it.phoneNumbers.map { pn -> pn.normalizedNumber } }.distinct()
        if (addresses.isNotEmpty()) {
            val subId = availableSIMCards.getOrNull(subscriptionId)?.subscriptionId
            sendMessageCompat(text, addresses, subId, emptyList())
        }
    }

    private fun sendMmsMessage(text: String, attachments: List<AttachmentSelection>, subscriptionId: Int) {
        val addresses = participants.flatMap { it.phoneNumbers.map { pn -> pn.normalizedNumber } }.distinct()
        if (addresses.isNotEmpty()) {
            val subId = availableSIMCards.getOrNull(subscriptionId)?.subscriptionId
            val mmsAttachments = attachments.map {
                org.nova.messages.models.Attachment(
                    null,
                    0L,
                    it.uri.toString(),
                    it.mimetype,
                    0,
                    0,
                    it.filename
                )
            }
            sendMessageCompat(text, addresses, subId, mmsAttachments)
        }
    }

    private fun dialNumber() {
        val phoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        if (phoneNumber != null) {
            dialNumber(phoneNumber)
        }
    }

    fun showProperties(message: Message) {
        MessageDetailsDialog(this, message)
    }

    private fun archiveThread() {
        try {
            updateConversationArchivedStatus(threadId, true)
            finish()
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun unarchiveThread() {
        updateConversationArchivedStatus(threadId, false)
        refreshMenuItems()
    }

    private fun managePeople() {}
    private fun addNumberToContact() {}
    private fun copyNumberToClipboard() {}
    private fun renameConversation() {
        if (conversation != null) {
            RenameConversationDialog(this, conversation!!) {
                ensureBackgroundThread {
                    val updatedConv = renameConversation(conversation!!, it)
                    conversation = updatedConv
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        setupThreadTitle()
                    }
                }
            }
        }
    }
    private fun markAsUnread() {
        markThreadMessagesUnread(threadId)
        finish()
    }
    private fun tryBlocking() {
        val numbers = participants.getAddresses()
        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(
            resources.getString(org.fossify.commons.R.string.block_confirmation),
            numbersString
        )

        ConfirmationDialog(this, question) {
            ensureBackgroundThread {
                numbers.forEach {
                    addBlockedNumber(it)
                }
                refreshConversations()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    finish()
                }
            }
        }
    }
    private fun askConfirmDelete() {
        val question = resources.getString(R.string.delete_whole_conversation_confirmation)
        ConfirmationDialog(this, question) {
            ensureBackgroundThread {
                deleteConversation(threadId)
                refreshConversations()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    finish()
                }
            }
        }
    }
    private fun restoreMessages() {
        ensureBackgroundThread {
            restoreAllMessagesFromRecycleBinForConversation(threadId)
            refreshConversations()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                finish()
            }
        }
    }
    private fun addContactAttachment(data: Uri) {}

    private fun getThreadItems(): ArrayList<ThreadItem> {
        val items = ArrayList<ThreadItem>()
        var prevDateTime = 0L
        messages.forEach { message ->
            val isSentFromDifferentKnownSIM = false
            if (message.date - prevDateTime > MIN_DATE_TIME_DIFF_SECS || isSentFromDifferentKnownSIM) {
                items.add(ThreadDateTime(message.date, ""))
                prevDateTime = message.date.toLong()
            }
            items.add(message)
            
            if (!message.isReceivedMessage() && !message.isScheduled) {
                if (message.type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                    items.add(ThreadSent(message.id, delivered = message.status == Telephony.Sms.STATUS_COMPLETE))
                } else if (message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || message.type == Telephony.Sms.MESSAGE_TYPE_QUEUED) {
                    items.add(ThreadSending(message.id))
                } else if (message.type == Telephony.Sms.MESSAGE_TYPE_FAILED || message.status == Telephony.Sms.STATUS_FAILED) {
                    items.add(ThreadError(message.id, getString(org.fossify.commons.R.string.unknown_error_occurred)))
                }
            }
        }
        return items
    }

    private fun scrollToBottom() {
        val adapter = getOrCreateThreadAdapter()
        if (adapter.itemCount > 0) {
            binding.threadMessagesList.post {
                if (!isFinishing && !isDestroyed) {
                    binding.threadMessagesList.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }

    private fun insertOrUpdateMessage(message: Message) {
        if (messages.map { it.id }.contains(message.id)) {
            val messageToReplace = messages.find { it.id == message.id }
            messages[messages.indexOf(messageToReplace)] = message
        } else {
            messages.add(message)
        }

        val newItems = getThreadItems()
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            getOrCreateThreadAdapter().updateMessages(newItems, callback = {
                if (isFinishing || isDestroyed) return@updateMessages
                scrollToBottom()
            })
            if (!refreshedSinceSent) {
                refreshMessages()
            }
        }
        messagesDB.insertOrUpdate(message)
        updateLastConversationMessage(threadId)
        if (shouldUnarchive()) {
            updateConversationArchivedStatus(message.threadId, false)
        }
        refreshConversations()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshMessages(@Suppress("unused") event: Events.RefreshMessages) {
        if (isRecycleBin || isDestroyed || isFinishing) {
            return
        }

        refreshedSinceSent = true
        allMessagesFetched = false

        if (isActivityVisible) {
            notificationManager.cancel(threadId.hashCode())
        }

        ensureBackgroundThread {
            if (isDestroyed || isFinishing) return@ensureBackgroundThread
            val addresses = participants.getAddresses().toSet()
            if (addresses.isEmpty()) return@ensureBackgroundThread
            
            val newThreadId = getThreadId(addresses)
            val newMessages = getMessages(newThreadId, includeScheduledMessages = false)
            
            messages = newMessages.apply {
                val scheduledMessages = messagesDB.getScheduledThreadMessages(threadId)
                    .filterNot { it.isScheduled && it.millis() < System.currentTimeMillis() }
                addAll(scheduledMessages)
            }

            if (!isDestroyed && !isFinishing) {
                setupAdapter()
            }
        }
    }

    private fun isMmsMessage(text: String): Boolean {
        return getAttachmentSelections().isNotEmpty() || participants.size > 1 || isLongMmsMessage(text)
    }

    private fun updateMessageType() {
        val text = binding.messageHolder.threadTypeMessage.text.toString()
        val stringId = if (isMmsMessage(text)) R.string.mms else R.string.sms
        binding.messageHolder.threadSendMessage.text = getString(stringId)
    }

    @SuppressLint("MissingPermission")
    private fun setupSIMSelector() {
        val manager = subscriptionManagerCompat()
        val activeSubscriptions = manager.activeSubscriptionInfoList ?: emptyList()
        availableSIMCards.clear()
        activeSubscriptions.forEachIndexed { index, info ->
            availableSIMCards.add(SIMCard(index + 1, info.subscriptionId, info.displayName.toString()))
        }
    }

    private fun setupMessagingEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(
            binding.messageHolder.threadTypeMessage
        ) { _, insets ->
            val type = WindowInsetsCompat.Type.ime()
            val isKeyboardVisible = insets.isVisible(type)
            if (isKeyboardVisible) {
                val keyboardHeight = insets.getInsets(type).bottom
                val bottomBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom

                config.keyboardHeight = if (keyboardHeight > 150) {
                    keyboardHeight - bottomBarHeight
                } else {
                    getDefaultKeyboardHeight()
                }
                hideAttachmentPicker()
            } else if (isAttachmentPickerVisible) {
                showAttachmentPicker()
            }

            insets
        }
    }

    private fun showAttachmentPicker() {
        binding.messageHolder.attachmentPickerDivider.showWithAnimation()
        binding.messageHolder.attachmentPickerHolder.showWithAnimation()
        animateAttachmentButton(rotation = -135f)
    }

    private fun hideAttachmentPicker() {
        binding.messageHolder.attachmentPickerDivider.beGone()
        binding.messageHolder.attachmentPickerHolder.beGone()
        animateAttachmentButton(rotation = 0f)
    }

    private fun animateAttachmentButton(rotation: Float) {
        binding.messageHolder.threadAddAttachment.animate()
            .rotation(rotation)
            .setDuration(500L)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun maybeSetupRecycleBinView() {
        if (isRecycleBin) {
            binding.messageHolder.root.beGone()
        }
    }

    private fun getBottomBarColor() = if (isDynamicTheme()) {
        resources.getColor(org.fossify.commons.R.color.you_bottom_bar_color)
    } else {
        getBottomNavigationBackgroundColor()
    }

    private fun setupAttachmentPickerView() {
        binding.messageHolder.attachmentPicker.apply {
            val textColor = Color.WHITE
            listOf(
                choosePhotoText,
                chooseVideoText,
                takePhotoText,
                recordVideoText,
                recordAudioText,
                pickFileText,
                pickContactText,
                scheduleMessageText
            ).forEach { it.setTextColor(textColor) }

            val imageClickListener = View.OnClickListener {
                try {
                    launchGetContentIntent(arrayOf("image/*"), PICK_PHOTO_INTENT)
                } catch (e: Exception) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
            }
            choosePhoto.setOnClickListener(imageClickListener)
            choosePhotoIcon.setOnClickListener(imageClickListener)
            choosePhotoText.setOnClickListener(imageClickListener)

            chooseVideo.setOnClickListener {
                launchGetContentIntent(arrayOf("video/*"), PICK_VIDEO_INTENT)
            }
            takePhoto.setOnClickListener {
                launchCapturePhotoIntent()
            }
            recordVideo.setOnClickListener {
                launchCaptureVideoIntent()
            }
            recordAudio.setOnClickListener {
                launchCaptureAudioIntent()
            }
            pickFile.setOnClickListener {
                launchGetContentIntent(arrayOf("*/*"), PICK_DOCUMENT_INTENT)
            }
            pickContact.setOnClickListener {
                launchPickContactIntent()
            }
            scheduleMessage.setOnClickListener {
                if (isScheduledMessage) {
                    launchScheduleSendDialog(scheduledDateTime)
                } else {
                    launchScheduleSendDialog()
                }
            }
        }
    }

    private fun launchGetContentIntent(types: Array<String>, requestCode: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = types.first()
            if (types.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, types)
            }
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            toast(org.fossify.commons.R.string.no_app_found)
        }
    }

    private fun launchCapturePhotoIntent() {
        handlePermission(PERMISSION_CAMERA) {
            if (it) {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (intent.resolveActivity(packageManager) != null) {
                    val file = File(cacheDir, "photo.jpg")
                    capturedImageUri = getMyFileUri(file)
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri)
                    startActivityForResult(intent, CAPTURE_PHOTO_INTENT)
                }
            }
        }
    }

    private fun launchCaptureVideoIntent() {
        handlePermission(PERMISSION_CAMERA) {
            if (it) {
                val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                startActivityForResult(intent, CAPTURE_VIDEO_INTENT)
            }
        }
    }

    private fun launchCaptureAudioIntent() {
        val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        startActivityForResult(intent, CAPTURE_AUDIO_INTENT)
    }

    private fun launchPickContactIntent() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        startActivityForResult(intent, PICK_CONTACT_INTENT)
    }

    companion object {
        var currentThreadId = 0L
        private const val TYPE_EDIT = 14
        private const val TYPE_SEND = 15
        private const val TYPE_DELETE = 16
        private const val MIN_DATE_TIME_DIFF_SECS = 300
        private const val SCROLL_TO_BOTTOM_FAB_LIMIT = 20
        private const val PREFETCH_THRESHOLD = 45
    }
}
