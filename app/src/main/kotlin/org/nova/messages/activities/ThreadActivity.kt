package org.nova.messages.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionInfo
import android.text.TextUtils
import android.util.TypedValue
import android.view.KeyEvent
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.nova.messages.BuildConfig
import org.nova.messages.R
import org.nova.messages.adapters.AttachmentsAdapter
import org.nova.messages.adapters.AutoCompleteTextViewAdapter
import org.nova.messages.adapters.ThreadAdapter
import org.nova.messages.databinding.ActivityThreadBinding
import org.nova.messages.databinding.ItemSelectedContactBinding
import org.nova.messages.dialogs.MessageDetailsDialog
import org.nova.messages.dialogs.RenameConversationDialog
import org.nova.messages.dialogs.ScheduleMessageDialog
import org.nova.messages.extensions.*
import org.nova.messages.helpers.*
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
import org.nova.messages.dialogs.AttachmentPickerDialog

class ThreadActivity : SimpleActivity() {

    private var threadId = 0L
    private var currentSIMCardIndex = 0
    private var isActivityVisible = false
    private var isFirstResume = true
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
    private var isFromNotification = false

    private var isScheduledMessage: Boolean = false
    private var messageToResend: Long? = null
    private lateinit var scheduledDateTime: DateTime
    private var isRefreshing = false
    private var isSendingMessage = false
    private var wasImeVisible = false

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
        isFromNotification = intent.getBooleanExtra(IS_FROM_NOTIFICATION, false)

        bus = EventBus.getDefault()
        bus!!.register(this)

        binding.threadMessagesList.itemAnimator = null
        binding.threadMessagesList.setItemViewCacheSize(20)
        (binding.threadMessagesList.layoutManager as LinearLayoutManager).stackFromEnd = true
        loadConversation()
        setupExpandingInputBar()

        // Keyboard Sync: Shrink input bar when keyboard goes down
        ViewCompat.setOnApplyWindowInsetsListener(binding.threadHolder) { _, insets ->
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (wasImeVisible && !isImeVisible && config.useNewUi && binding.messageHolder.threadTypeMessage.text?.isEmpty() == true && !config.alwaysExpandSearchBar) {
                shrinkInputBar()
            }
            wasImeVisible = isImeVisible
            insets
        }
    }

    private fun setupExpandingInputBar() {
        val inputBar = binding.messageHolder.novaMessageInputBar
        val inputField = binding.messageHolder.threadTypeMessage
        
        if (config.useNewUi) {
            val alwaysExpand = config.alwaysExpandSearchBar
            
            // New UI: Small centered input bar that expands
            inputBar.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                width = if (alwaysExpand) androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT else 240.getScaledPx()
                startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            }
            inputBar.alpha = 0.95f // 95% opacity
            inputBar.elevation = 10f * resources.displayMetrics.density
            inputBar.translationZ = 4f
            
            inputField.isFocusable = alwaysExpand
            inputField.isFocusableInTouchMode = alwaysExpand
            inputField.isEnabled = true
            
            inputBar.setOnClickListener {
                if (!inputField.isFocusable) {
                    expandInputBar()
                }
            }
            
            inputField.setOnClickListener {
                if (!inputField.isFocusable) {
                    expandInputBar()
                }
            }
            
            inputField.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && inputField.text?.isEmpty() == true && !alwaysExpand) {
                    shrinkInputBar()
                }
            }
        } else {
            // Classic UI: Full width, always focusable
            inputBar.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                width = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
                startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            }
            inputBar.alpha = 1.0f
            inputBar.elevation = 0f
            inputBar.translationZ = 0f
            
            inputField.isFocusable = true
            inputField.isFocusableInTouchMode = true
            inputField.isEnabled = true
            
            inputBar.setOnClickListener(null)
            inputField.setOnClickListener(null)
            inputField.onFocusChangeListener = null
        }
        inputBar.requestLayout()
    }

    private fun expandInputBar() {
        val inputBar = binding.messageHolder.novaMessageInputBar
        val inputField = binding.messageHolder.threadTypeMessage
        
        val startWidth = inputBar.width
        val endWidth = binding.threadHolder.width - 32.getScaledPx()
        
        if (startWidth >= endWidth - 5) return // Already expanded
        
        val animator = android.animation.ValueAnimator.ofInt(startWidth, endWidth)
        animator.duration = 450 // Slightly longer for the bounce to feel natural
        // OvershootInterpolator provides the "bounce at the end" effect
        animator.interpolator = android.view.animation.OvershootInterpolator(1.2f)
        animator.addUpdateListener { animation ->
            inputBar.updateLayoutParams {
                width = animation.animatedValue as Int
            }
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                inputField.isFocusable = true
                inputField.isFocusableInTouchMode = true
                inputField.requestFocus()
                showKeyboard(inputField)
            }
        })
        animator.start()
    }

    private fun shrinkInputBar() {
        if (config.alwaysExpandSearchBar) return

        val inputBar = binding.messageHolder.novaMessageInputBar
        val inputField = binding.messageHolder.threadTypeMessage
        
        val startWidth = inputBar.width
        val endWidth = 240.getScaledPx()
        
        if (startWidth <= endWidth + 5) return // Already shrunk
        
        val animator = android.animation.ValueAnimator.ofInt(startWidth, endWidth)
        animator.duration = 300 // Slightly slower shrink for smoothness
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            inputBar.updateLayoutParams {
                width = animation.animatedValue as Int
            }
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                inputField.isFocusable = false
                inputField.isFocusableInTouchMode = false
                hideKeyboard()
            }
        })
        animator.start()
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing || isDestroyed) return
        applyOutlines()
        
        currentThreadId = threadId
        setupTopAppBar(
            topAppBar = binding.threadAppbar,
            navigationIcon = NavigationIcon.Arrow,
            topBarColor = Color.TRANSPARENT
        )
        
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

        applyCustomColors()

        if (isFirstResume && config.useNewUi) {
            isFirstResume = false
            // Anchor at the top for stretching effect
            binding.threadAppbar.pivotY = 0f
            binding.threadAppbar.scaleY = 0.4f
            binding.threadAppbar.alpha = 0f
            binding.threadAppbar.animate()
                .scaleY(1f)
                .alpha(1f)
                .setDuration(800)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
                .start()
        }
    }

    override fun onPause() {
        super.onPause()
        currentThreadId = 0L
        saveDraftMessage()
        isActivityVisible = false
    }

    override fun onStop() {
        super.onStop()
        saveDraftMessage()
        bus?.post(Events.RefreshConversations())
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
                        addAttachment(capturedImageUri!!, isFromCamera = true)
                    }
                }
                CAPTURE_VIDEO_INTENT,
                PICK_DOCUMENT_INTENT,
                CAPTURE_AUDIO_INTENT,
                PICK_AUDIO_INTENT,
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

            val cachedMessagesCode = messages.hashCode()
            if (!isRecycleBin) {
                val rawMessages = getMessages(threadId)
                messages = ArrayList(processReactions(rawMessages))

                if (config.useRecycleBin) {
                    val recycledMessages = try { messagesDB.getThreadMessagesFromRecycleBin(threadId) } catch (e: Exception) { emptyList() }
                    messages = ArrayList(messages.filterNotInByKey(recycledMessages) { it.getStableId() })
                }
            }

            setupParticipants()
            setupAdapter(forceScroll = true)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                updateMessageType()
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

    private fun setupAdapter(forceScroll: Boolean = false) {
        if (isRefreshing) return
        isRefreshing = true
        ensureBackgroundThread {
            val items = getThreadItems()
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    isRefreshing = false
                    return@runOnUiThread
                }
                threadItems = items
                refreshMenuItems()
                val forceScrollOnOpen = isFromNotification
                isFromNotification = false
                getOrCreateThreadAdapter().apply {
                    updateMessages(threadItems) {
                        isRefreshing = false
                        if (isFinishing || isDestroyed) return@updateMessages
                        scrollToBottom(forceScroll || forceScrollOnOpen)
                    }
                }
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
        
        // Solid Snap on Layout Change (e.g. Keyboard pop-up)
        binding.threadMessagesList.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                // Keyboard opened or size decreased, force snap to bottom
                scrollToBottom(forceScroll = true)
            }
        }
    }

    private fun handleItemClick(any: Any) {
        when (any) {
            is Message -> {
                if (any.isScheduled) {
                    // Show scheduled info
                } else if (any.attachment?.attachments?.isNotEmpty() == true) {
                    val firstAttachment = any.attachment.attachments.first()
                    val mimetype = firstAttachment.mimetype
                    if (mimetype.isImageMimeType() || mimetype.isVideoMimeType()) {
                        launchViewIntent(firstAttachment.getUri(), mimetype, firstAttachment.filename)
                    }
                }
            }
            is ThreadError -> {
                binding.messageHolder.threadTypeMessage.setText(any.messageText)
                binding.messageHolder.threadTypeMessage.setSelection(any.messageText.length)
                messageToResend = any.messageId
                checkSendMessageAvailability()
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

    private fun setupButtons() = binding.apply {
        val inputBarColor = config.inputBarTextColor
        val mainTextColor = config.mainTextColor

        binding.messageHolder.apply {
            threadSendMessage.setTextColor(inputBarColor)
            threadSendMessage.compoundDrawables.forEach {
                it?.applyColorFilter(inputBarColor)
            }

            confirmManageContacts.applyColorFilter(mainTextColor)
            threadAddAttachment.applyColorFilter(inputBarColor)
            threadAddAttachment.alpha = 1.0f

            val properPrimaryColor = getProperPrimaryColor()
            // threadMessagesFastscroller removed

            threadCharacterCounter.beGone()
            threadCharacterCounter.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())

            threadTypeMessage.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
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
                val messageString = if (config.useSimpleCharacters) it.normalizeString() else it
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
                showAttachmentPickerDialog()
            }

            if (intent.extras?.containsKey(THREAD_ATTACHMENT_URI) == true) {
                val uri = intent.getStringExtra(THREAD_ATTACHMENT_URI)!!.toUri()
                addAttachment(uri)
            } else if (intent.extras?.containsKey(THREAD_ATTACHMENT_URIS) == true) {
                val uris = intent.getParcelableArrayListExtra<Uri>(THREAD_ATTACHMENT_URIS)
                uris?.forEach { addAttachment(it) }
            }
            scrollToBottomFab.setOnClickListener {
                scrollToBottom(isManual = true)
            }
            scrollToBottomFab.backgroundTintList = ColorStateList.valueOf(getBottomBarColor())
        }
        setupScheduleSendUi()
        binding
    }

    private fun showAttachmentPickerDialog() {
        AttachmentPickerDialog { id ->
            when (id) {
                R.id.picker_image -> launchGetContentIntent(arrayOf("image/*"), PICK_PHOTO_INTENT)
                R.id.picker_video -> launchGetContentIntent(arrayOf("video/*"), PICK_VIDEO_INTENT)
                R.id.picker_camera -> launchCapturePhotoIntent()
                R.id.picker_camera_video -> launchCaptureVideoIntent()
                R.id.picker_audio -> launchCaptureAudioIntent()
                R.id.picker_file -> launchGetContentIntent(arrayOf("*/*"), PICK_DOCUMENT_INTENT)
                R.id.picker_contact -> launchPickContactIntent()
                R.id.picker_schedule -> {
                    if (isScheduledMessage) {
                        launchScheduleSendDialog(scheduledDateTime)
                    } else {
                        launchScheduleSendDialog()
                    }
                }
            }
        }.show(supportFragmentManager, AttachmentPickerDialog.TAG)
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

    private fun clearCurrentMessage() {
        binding.messageHolder.threadTypeMessage.setText("")
        getAttachmentsAdapter()?.clear()
        checkSendMessageAvailability()
    }

    private fun setupOptionsMenu() {
        val toolbar = binding.threadToolbar
        toolbar.menu.clear()
        
        // Add frequently used icons if needed (e.g. Call)
        if (participants.size == 1 && !isSpecialNumber() && !isRecycleBin) {
            val callItem = toolbar.menu.add(0, org.nova.messages.R.id.dial_number, 0, getString(org.fossify.commons.R.string.dial_number))
            callItem.setIcon(org.fossify.commons.R.drawable.ic_phone_vector)
            callItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        // Add a single custom overflow item
        val moreItem = toolbar.menu.add(0, org.nova.messages.R.id.more_options, 1, "More")
        moreItem.setIcon(org.fossify.commons.R.drawable.ic_three_dots_vector)
        moreItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                org.nova.messages.R.id.dial_number -> dialNumber()
                org.nova.messages.R.id.more_options -> {
                    // Trigger modern menu for everything else
                    val overflowView = toolbar.findViewById<android.view.View>(menuItem.itemId) ?: toolbar
                    showThreadModernMenu(overflowView)
                }
            }
            true
        }
        
        applyCustomColors() // Ensure the new programmatically added icons are tinted
    }

    private fun showThreadModernMenu(anchor: android.view.View) {
        val items = mutableListOf<Pair<Int, String>>()
        val firstPhoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        val archiveAvailable = config.isArchiveAvailable

        if (threadItems.isNotEmpty()) {
            items.add(R.id.delete to getString(org.fossify.commons.R.string.delete))
            items.add(R.id.mark_as_unread to getString(R.string.mark_as_unread))
        }

        if (threadItems.isNotEmpty() && archiveAvailable) {
            if (conversation?.isArchived == false && !isRecycleBin) {
                items.add(R.id.archive to getString(R.string.archive))
            } else if (conversation?.isArchived == true && !isRecycleBin) {
                items.add(R.id.unarchive to getString(R.string.unarchive))
            }
        }

        if (conversation != null && !isRecycleBin) {
            items.add(R.id.conversation_details to getString(R.string.conversation_details))
            items.add(R.id.rename_conversation to getString(R.string.rename_conversation))
        }

        if (!isRecycleBin) {
            items.add(R.id.block_number to getString(org.fossify.commons.R.string.block_number))
            if (!isSpecialNumber()) {
                items.add(R.id.manage_people to getString(R.string.add_person))
            }
        }

        if (isRecycleBin && threadItems.isNotEmpty()) {
            items.add(R.id.restore to getString(R.string.restore))
        }

        if (participants.size == 1 && !isRecycleBin) {
            if (participants.first().name == firstPhoneNumber) {
                items.add(R.id.add_number_to_contact to getString(org.fossify.commons.R.string.add_number_to_contact))
            }
            if (!firstPhoneNumber.isNullOrEmpty()) {
                items.add(R.id.copy_number to getString(org.fossify.commons.R.string.copy_to_clipboard))
            }
        }

        showModernMenu(anchor, items) { itemId ->
            when (itemId) {
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
            }
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
            findItem(R.id.block_number)?.title = getString(org.fossify.commons.R.string.block_number)
            findItem(R.id.block_number)?.isVisible = !isRecycleBin
            findItem(R.id.dial_number)?.isVisible =
                participants.size == 1 && !isSpecialNumber() && !isRecycleBin
            findItem(R.id.manage_people)?.isVisible = !isSpecialNumber() && !isRecycleBin
            findItem(R.id.mark_as_unread)?.isVisible = threadItems.isNotEmpty() && !isRecycleBin

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
        val finalTitle = if (!title.isNullOrEmpty()) title else participants.getThreadTitle()

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
                    getOrCreateThreadAdapter().updateMessages(getThreadItems()) {
                        if (isFinishing || isDestroyed) return@updateMessages
                        binding.threadMessagesList.scrollToPosition(messageIndex)
                    }
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
                messages.forEach { deleteMessage(it.id, it.isMMS) }
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

    private fun getAttachmentSelections() = getAttachmentsAdapter()?.attachments ?: emptyList()

    private fun addAttachment(uri: Uri, isFromCamera: Boolean = false) {
        val id = uri.toString()
        if (getAttachmentSelections().any { it.id == id }) return

        try {
            contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}

        var mimeType = contentResolver.getType(uri)
        if (mimeType == null) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (extension.isNotEmpty()) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            }
        }

        if (mimeType == null) mimeType = "image/jpeg"

        val isImage = mimeType.isImageMimeType()
        val isGif = mimeType.isGifMimeType()
        val isRawImage = mimeType.contains("dng", true) || mimeType.contains("raw", true)
        val isVideo = mimeType.isVideoMimeType()

        val fileSize = getFileSizeFromUri(uri)
        val mmsFileSizeLimit = config.mmsFileSizeLimit
        
        if (mmsFileSizeLimit != FILE_SIZE_NONE && fileSize > mmsFileSizeLimit && (!isImage || isGif || isRawImage) && !isVideo) {
            toast(R.string.attachment_sized_exceeds_max_limit, Toast.LENGTH_LONG)
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
            isPending = isFromCamera || (((isImage && !isGif && !isRawImage) || isVideo) && (mmsFileSizeLimit == FILE_SIZE_NONE || fileSize > mmsFileSizeLimit))
        )
        adapter.addAttachment(attachment)
        
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
        val destinationUri = resultData.data ?: return
        try {
            applicationContext.contentResolver.takePersistableUriPermission(
                destinationUri, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
        
        ensureBackgroundThread {
            try {
                if (DocumentsContract.isTreeUri(destinationUri)) {
                    val outputDir = DocumentFile.fromTreeUri(this, destinationUri) ?: return@ensureBackgroundThread
                    pendingAttachmentsToSave?.forEach { attachment ->
                        val documentFile = outputDir.createFile(
                            attachment.mimetype,
                            attachment.filename.takeIf { it.isNotBlank() } ?: attachment.uriString.getFilenameFromPath()
                        ) ?: return@forEach
                        copyToUri(src = attachment.getUri(), dst = documentFile.uri)
                    }
                } else {
                    copyToUri(pendingAttachmentsToSave!!.first().getUri(), destinationUri)
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
        val attachments = ArrayList(getAttachmentSelections())

        if (text.isEmpty() && attachments.isEmpty()) return

        if (participants.isEmpty() || threadId == 0L) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }

        clearCurrentMessage()
        ensureBackgroundThread {
            val subscriptionId = currentSIMCardIndex 
            
            isSendingMessage = true
            try {
                if (attachments.isNotEmpty()) {
                    sendMmsMessage(text, attachments, subscriptionId)
                } else {
                    sendNormalMessage(text, subscriptionId)
                }
                
                updateLastConversationMessage(threadId)
                refreshMessages()
                refreshConversations()
            } finally {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    isSendingMessage = false
                }
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
                org.nova.messages.models.Attachment(null, 0L, it.uri.toString(), it.mimetype, 0, 0, it.filename)
            }
            sendMessageCompat(text, addresses, subId, mmsAttachments)
        }
    }

    private fun dialNumber() {
        val phoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        if (phoneNumber != null) dialNumber(phoneNumber)
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

    private fun managePeople() {
        val numbers = participants.flatMap { it.phoneNumbers.map { pn -> pn.normalizedNumber } }.distinct()
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, numbers.joinToString(";"))
            startActivity(this)
        }
    }

    private fun addNumberToContact() {
        val number = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value ?: return
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
            startActivity(this)
        }
    }

    private fun copyNumberToClipboard() {
        val number = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value ?: return
        copyToClipboard(number)
    }
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
        val question = String.format(resources.getString(org.fossify.commons.R.string.block_confirmation), numbersString)

        ConfirmationDialog(this, question) {
            ensureBackgroundThread {
                numbers.forEach { addBlockedNumber(it) }
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
            if (message.date - prevDateTime > MIN_DATE_TIME_DIFF_SECS) {
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

    private fun scrollToBottom(forceScroll: Boolean = false, isManual: Boolean = false) {
        val adapter = getOrCreateThreadAdapter()
        if (adapter.itemCount > 0) {
            // Guard: Never auto-scroll while selection mode is active, UNLESS it's a manual scroll request (FAB)
            if (!isManual && adapter.isSelectionModeActive()) {
                return
            }

            val recyclerView = binding.threadMessagesList
            // Re-calculate the "at bottom" check. canScrollVertically(1) returns false if at the very end.
            val isAtBottom = !recyclerView.canScrollVertically(1)
            
            if (forceScroll || isAtBottom || isManual) {
                recyclerView.post {
                    if (!isFinishing && !isDestroyed) {
                        // Smoothly glide to the bottom instead of instant jump
                        if (forceScroll || isManual) {
                            // For forced scrolls (sending) or manual, use custom smooth scroller for perfect speed
                            val scroller = object : androidx.recyclerview.widget.LinearSmoothScroller(this) {
                                override fun getVerticalSnapPreference(): Int = SNAP_TO_END
                                override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                                    return 120f / displayMetrics.densityDpi
                                }
                            }
                            scroller.targetPosition = adapter.itemCount - 1
                            recyclerView.layoutManager?.startSmoothScroll(scroller)
                        } else {
                            // Standard smooth scroll for background receipts
                            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                        }
                    }
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshMessages(@Suppress("unused") event: Events.RefreshMessages) {
        if (isRecycleBin || isDestroyed || isFinishing) return

        if (isActivityVisible) {
            notificationManager.cancel(threadId.hashCode())
        }

        ensureBackgroundThread {
            if (isDestroyed || isFinishing) return@ensureBackgroundThread
            val addresses = participants.getAddresses().toSet()
            if (addresses.isEmpty()) return@ensureBackgroundThread
            
            val newThreadId = getThreadId(addresses)
            val newConv = conversationsDB.getConversationWithThreadId(newThreadId)
            if (newConv != null) {
                conversation = newConv
            }

            val newMessages = getMessages(newThreadId, includeScheduledMessages = false)
            
            val scheduledMessages = try {
                messagesDB.getScheduledThreadMessages(threadId)
                    .filterNot { it.isScheduled && it.millis() < System.currentTimeMillis() }
            } catch (e: Exception) { emptyList() }
            
            val combinedRawMessages = ArrayList<Message>(newMessages)
            combinedRawMessages.addAll(scheduledMessages)
            
            // Centralized processing handles both DB persistence and wire-format hiding
            val combinedMessages = processReactions(combinedRawMessages)
            
            if (messages.size != combinedMessages.size || messages.hashCode() != combinedMessages.hashCode()) {
                messages = ArrayList(combinedMessages)
                val forceScroll = isSendingMessage
                allMessagesFetched = false
                if (!isDestroyed && !isFinishing) {
                    setupAdapter(forceScroll)
                    updateMessageType()
                    if (forceScroll) {
                        runOnUiThread {
                            binding.threadMessagesList.postDelayed({
                                scrollToBottom(forceScroll = true)
                                isSendingMessage = false
                            }, 100)
                        }
                    }
                }
            } else {
                if (isSendingMessage) {
                    isSendingMessage = false
                    runOnUiThread { scrollToBottom(forceScroll = true) }
                }
            }
        }
    }

    fun onReactionPicked(message: Message, emoji: String) {
        if (message.reaction == emoji) return
        
        val bodySnippet = message.body.take(30).trim()
        val text = "$emoji to '$bodySnippet'"
        
        runOnUiThread {
            message.reaction = emoji
            val cacheKey = MessagingCache.getReactionKey(message.id, message.isMMS)
            MessagingCache.reactionsCache[cacheKey] = emoji
            val adapter = getOrCreateThreadAdapter()
            val index = adapter.currentList.indexOf(message)
            if (index != -1) {
                adapter.notifyItemChanged(index)
            }
        }

        ensureBackgroundThread {
            try {
                reactionsDB.insertOrUpdate(Reaction(message.id, message.isMMS, threadId, emoji))
            } catch (e: Exception) {
                android.util.Log.e("ReactionError", "Failed to save reaction", e)
            }
            
            val subscriptionId = currentSIMCardIndex 
            sendNormalMessage(text, subscriptionId)
            updateLastConversationMessage(threadId)
            runOnUiThread {
                refreshMessages()
                refreshConversations()
            }
        }
    }

    private fun processReactions(messages: List<Message>): List<Message> {
        val (regex, map) = getReactionTools()
        
        // 1. Efficiently load stored reactions from the dedicated table
        val persistedReactions = try { reactionsDB.getThreadReactions(threadId) } catch (e: Exception) { emptyList() }
        val reactionMapDB = persistedReactions.associateBy({ MessagingCache.getReactionKey(it.messageId, it.isMms) }, { it.emoji })
        
        // 2. Build a snippet-based index for fast O(1) target lookup
        // We use a snippet of the body to match reaction texts
        val snippetMap = messages.filter { !it.body.isNullOrBlank() }
            .associateBy({ it.body.take(30).trim() }, { it })

        val result = messages.toMutableList()
        
        // Pass 1: Handle incoming reaction texts and UI filtering
        for (i in result.indices.reversed()) {
            val msg = result[i]
            val match = regex.find(msg.body)
            
            if (match != null) {
                // IT IS A REACTION MESSAGE - HIDE IT IMMEDIATELY
                msg.isReactionMessage = true
                
                val prefix = match.groupValues[1].ifEmpty { match.groupValues[2] }
                val quotedText = match.groupValues[3].trim()
                val emoji = map[prefix] ?: "👍"

                // Fast O(1) lookup in our snippet index
                val target = snippetMap[quotedText]
                if (target != null && target.id != msg.id && target.date <= msg.date) {
                    val targetKey = MessagingCache.getReactionKey(target.id, target.isMMS)
                    target.reaction = emoji
                    MessagingCache.reactionsCache[targetKey] = emoji
                    
                    // Persist newly discovered reaction to DB
                    if (reactionMapDB[targetKey] != emoji) {
                        ensureBackgroundThread {
                            reactionsDB.insertOrUpdate(Reaction(target.id, target.isMMS, threadId, emoji))
                        }
                    }
                }
            } else {
                // Pass 2: Re-apply already known reactions from DB or Cache
                val cacheKey = MessagingCache.getReactionKey(msg.id, msg.isMMS)
                msg.reaction = reactionMapDB[cacheKey] ?: MessagingCache.reactionsCache[cacheKey]
            }
        }

        return result.filter { !it.isReactionMessage }
    }

    private fun getReactionTools(): Pair<Regex, Map<String, String>> {
        // Regex 1: "Liked message '...'" (Old format)
        // Regex 2: "👍 to '...'" (Modern format)
        val reactionRegex = Regex("^(?:(Liked|Loved|Disliked|Laughed at|Surprised by|Cried at|Angry at|Emphasized|Questioned)\\s+(?:message\\s+)?|([👍❤️👎😂😮😢😡⁉️❓🔥💯👏✅🎉]+|\\p{So}+)\\s+to\\s+)['\"](.*?)['\"]?\\s*$")
        
        val reactionMap = mapOf(
            "Liked" to "👍",
            "Loved" to "❤️",
            "Disliked" to "👎",
            "Laughed at" to "😂",
            "Surprised by" to "😮",
            "Cried at" to "😢",
            "Angry at" to "😡",
            "Emphasized" to "‼️",
            "Questioned" to "❓",
            // Direct emoji mapping (identity)
            "👍" to "👍", "❤️" to "❤️", "😂" to "😂", "😮" to "😮", "😢" to "😢", "😡" to "😡", "👎" to "👎", 
            "⁉️" to "⁉️", "❓" to "❓", "🔥" to "🔥", "💯" to "💯", "👏" to "👏", "✅" to "✅", "🎉" to "🎉",
            "‼️" to "‼️"
        )
        return Pair(reactionRegex, reactionMap)
    }

    private fun isMmsMessage(text: String): Boolean {
        return getAttachmentSelections().isNotEmpty() || participants.size > 1 || isLongMmsMessage(text)
    }

    private fun updateMessageType() {
        binding.messageHolder.threadSendMessage.text = ""
        binding.messageHolder.threadNovaLock.beVisibleIf(conversation?.isNovaUser == true)
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.threadMessagesList) { view, insets ->
            val imeType = WindowInsetsCompat.Type.ime()
            val systemBarsType = WindowInsetsCompat.Type.systemBars()
            
            val isImeVisible = insets.isVisible(imeType)
            val imeHeight = insets.getInsets(imeType).bottom
            val systemBarsHeight = insets.getInsets(systemBarsType).bottom
            
            // Modern Floating Sync: Messages must push up precisely with the keyboard
            // 86dp is the standard floating bar bottom padding we established
            val basePadding = 86.getScaledPx()
            val finalBottomPadding = if (isImeVisible) {
                // When keyboard is up, we need to add the keyboard height but subtract 
                // the overlapping system bar height to get pure delta
                imeHeight + basePadding - systemBarsHeight
            } else {
                basePadding
            }
            
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                finalBottomPadding
            )

            if (isImeVisible) {
                // Force sync scroll to keep latest messages in view above the keyboard
                scrollToBottom(forceScroll = true)
            }

            // Keep the library's keyboard height tracker updated
            if (isImeVisible && imeHeight > 150) {
                config.keyboardHeight = imeHeight - systemBarsHeight
            }

            insets
        }
    }

    private fun getBottomBarColor() = if (isDynamicTheme()) resources.getColor(org.fossify.commons.R.color.you_bottom_bar_color) else getBottomNavigationBackgroundColor()

    private fun launchGetContentIntent(types: Array<String>, requestCode: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = types.first()
            if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(intent, requestCode)
        } catch (_: Exception) {
            toast(org.fossify.commons.R.string.no_app_found)
        }
    }

    private fun launchCapturePhotoIntent() {
        handlePermission(PERMISSION_CAMERA) {
            if (it) {
                try {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    val file = File(cacheDir, "photo.jpg")
                    capturedImageUri = getMyFileUri(file)
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    startActivityForResult(intent, CAPTURE_PHOTO_INTENT)
                } catch (e: Exception) {
                    // Fallback to general capture if specific file path fails
                    try {
                        startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), CAPTURE_PHOTO_INTENT)
                    } catch (e2: Exception) {
                        toast(org.fossify.commons.R.string.no_app_found)
                    }
                }
            }
        }
    }

    private fun launchCaptureVideoIntent() {
        handlePermission(PERMISSION_CAMERA) {
            if (it) {
                try {
                    startActivityForResult(Intent(MediaStore.ACTION_VIDEO_CAPTURE), CAPTURE_VIDEO_INTENT)
                } catch (e: Exception) {
                    toast(org.fossify.commons.R.string.no_app_found)
                }
            }
        }
    }

    private fun launchCaptureAudioIntent() {
        handlePermission(PERMISSION_RECORD_AUDIO) {
            if (it) {
                try {
                    startActivityForResult(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION), CAPTURE_AUDIO_INTENT)
                } catch (e: Exception) {
                    val systemRecorderIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "audio/*"
                    }
                    try {
                        startActivityForResult(systemRecorderIntent, PICK_AUDIO_INTENT)
                    } catch (e2: Exception) {
                        toast(org.fossify.commons.R.string.no_app_found)
                    }
                }
            }
        }
    }

    private fun launchPickContactIntent() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        startActivityForResult(intent, PICK_CONTACT_INTENT)
    }

    private fun applyOutlines() = binding.apply {
        val density = resources.displayMetrics.density
        val isNewUi = config.useNewUi
        
        // Top Bar Outline (Matching nova_topbar_bg corners)
        if (config.topBarOutline && isNewUi) {
            val r26 = 26f * density
            val thickness = config.topBarOutlineThickness
            val thickStroke = (thickness * density).toInt()
            val outline = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.topBarOutlineColor)
                setColor(Color.TRANSPARENT)
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r26, r26, r26, r26)
            }
            val drawable = android.graphics.drawable.LayerDrawable(arrayOf(outline))
            drawable.setLayerInset(0, -thickStroke, -thickStroke, -thickStroke, 0)
            binding.threadAppbar.foreground = drawable
        } else {
            binding.threadAppbar.foreground = null
        }

        // Input Bar Outline
        val inputBar = binding.messageHolder.root.findViewById<android.view.View>(R.id.nova_message_input_bar)
        if (config.searchBarOutline && isNewUi && inputBar != null) {
            val thickness = config.searchBarOutlineThickness
            val thickStroke = (thickness * density).toInt()
            val r_base = 100f * density
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.searchBarOutlineColor)
                cornerRadius = r_base
                setColor(Color.TRANSPARENT)
            }
            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(drawable))
            layerDrawable.setLayerInset(0, 0, 0, 0, 0)
            inputBar.foreground = layerDrawable
        } else if (inputBar != null) {
            inputBar.foreground = null
        }
    }

    companion object {
        var currentThreadId = 0L
        private const val MIN_DATE_TIME_DIFF_SECS = 300
        private const val SCROLL_TO_BOTTOM_FAB_LIMIT = 20
        private const val PREFETCH_THRESHOLD = 45
    }
}
