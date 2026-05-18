package org.nova.messages.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Telephony
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import org.fossify.commons.activities.AboutActivity
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.nova.messages.BuildConfig
import org.nova.messages.R
import org.nova.messages.adapters.ConversationsAdapter
import org.nova.messages.adapters.SearchResultsAdapter
import org.nova.messages.databinding.ActivityMainBinding
import org.nova.messages.extensions.*
import org.nova.messages.helpers.SEARCHED_MESSAGE_ID
import org.nova.messages.helpers.THREAD_ID
import org.nova.messages.helpers.THREAD_TITLE
import org.nova.messages.models.Conversation
import org.nova.messages.models.Events
import org.nova.messages.models.Message
import org.nova.messages.models.SearchResult
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : SimpleActivity() {

    override var isSearchBarEnabled = false   // disable Nova search bar


    private val MAKE_DEFAULT_APP_REQUEST = 1
    private var storedTextColor = 0
    private var lastSearchedText = ""
    private var bus: EventBus? = null
    private var isActivityVisible = false
    private var lastRefreshTime = 0L
    private var isInitialized = false

    private val binding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force the app into light mode immediately so night resources won't make text white
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Nova title
        binding.novaTitle.text = "Nova Messages"

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))
        setupSearchEdgeToEdge()
        setupTopAppBar(binding.mainAppbar, NavigationIcon.None, Color.TRANSPARENT)

        // appLaunched(BuildConfig.APPLICATION_ID)

        setupNovaSearchBar()
        loadMessages()
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true

        initMessenger()
        getOrCreateConversationsAdapter().apply {
            if (storedTextColor != getProperTextColor()) updateTextColor(getProperTextColor())
            updateDrafts()
        }

        updateAppFonts(binding.root)
        updateTextColors(binding.mainCoordinator)
        applyCustomColors()
        binding.novaTitle.updateLayoutParams<Toolbar.LayoutParams> {
            marginEnd = 60.getScaledPx()
        }
        setupScaledToolbar(binding.mainToolbar)
        binding.conversationsFab.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
            topMargin = 10.getScaledPx()
            marginEnd = 20.getScaledPx()
        }

        binding.settingsGear.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
            topMargin = 55.getScaledPx()
        }

        binding.novaSearchBar.updateLayoutParams {
            height = 55.getScaledPx()
        }

        getOrCreateConversationsAdapter().updateScaling()

        android.util.Log.d("NAV_TRACE", "MainActivity.onResume finished")
    }

    override fun onPause() {
        android.util.Log.d("NAV_TRACE", "MainActivity.onPause starting")
        super.onPause()
        isActivityVisible = false
        storedTextColor = getProperTextColor()
        android.util.Log.d("NAV_TRACE", "MainActivity.onPause finished")
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    // -----------------------------
    // NOVA SEARCH BAR
    // -----------------------------
    private fun setupNovaSearchBar() {
        binding.novaSearchBar.setOnClickListener {
            binding.novaSearchInput.requestFocus()
            showKeyboard(binding.novaSearchInput)
        }

        binding.novaSearchInput.addTextChangedListener { text ->
            searchTextChanged(text?.toString() ?: "")
        }
    }

    // -----------------------------
    // DEFAULT SMS APP + PERMISSIONS
    // -----------------------------
    private fun loadMessages() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
                }
            } else if (!isFinishing && !isDestroyed && config.appRunCount <= 1) {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
            if (isQPlus()) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true) {
                    askPermissions()
                } else if (!isFinishing && !isDestroyed && config.appRunCount <= 1) {
                    finish()
                }
            } else {
                if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                    askPermissions()
                } else if (!isFinishing && !isDestroyed && config.appRunCount <= 1) {
                    finish()
                }
            }
        }
    }

    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) { readSms ->
            if (!readSms) {
                if (!isFinishing && !isDestroyed) {
                    finish()
                }
                return@handlePermission
            }

            handlePermission(PERMISSION_SEND_SMS) { sendSms ->
                if (!sendSms) {
                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                    return@handlePermission
                }

                handlePermission(PERMISSION_READ_CONTACTS) {
                    handleNotificationPermission { granted ->
                        if (!granted) {
                            PermissionRequiredDialog(
                                activity = this,
                                textId = org.fossify.commons.R.string.allow_notifications_incoming_messages,
                                positiveActionCallback = { openNotificationSettings() }
                            )
                        }
                    }

                    initMessenger()
                    bus = EventBus.getDefault()
                    try { bus!!.register(this) } catch (_: Exception) {}
                }
            }
        }
    }

    // -----------------------------
    // LOADING + DISPLAYING MESSAGES
    // -----------------------------
    private fun initMessenger() {
        if (isFinishing || isDestroyed) return
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < 1000) return
        lastRefreshTime = now

        try {
            if (!isInitialized) {
                setupOneTimeViews()
                isInitialized = true
            }

            checkWhatsNewDialog()
            getCachedConversations()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to init messenger during transition", e)
        }
    }

    private fun setupOneTimeViews() {
        binding.noConversationsPlaceholder2.setOnClickListener { launchNewConversation() }
        binding.conversationsFab.setOnClickListener { launchNewConversation() }
        binding.settingsGear.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        val fabAnim = AnimationUtils.loadAnimation(this, R.anim.fab_in)
        binding.conversationsFab.startAnimation(fabAnim)
        binding.settingsGear.startAnimation(fabAnim)

        val searchAnim = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom)
        binding.novaSearchBar.startAnimation(searchAnim)

        binding.novaSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
    }

    private fun getCachedConversations() {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) { ArrayList() }

            val archived = try { conversationsDB.getAllArchived() } catch (_: Exception) { listOf() }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setupConversations(conversations, cached = true)
                getNewConversations((conversations + archived).toMutableList() as ArrayList<Conversation>)
            }

            conversations.forEach { clearExpiredScheduledMessages(it.threadId) }
        }
    }

    private fun getNewConversations(cached: ArrayList<Conversation>) {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val conversations = getConversations(privateContacts = privateContacts)

            conversations.forEach { conv ->
                if (!cached.map { it.threadId }.contains(conv.threadId)) {
                    conversationsDB.insertOrUpdate(conv)
                    cached.add(conv)
                }
            }

            val all = conversationsDB.getNonArchived() as ArrayList<Conversation>
            runOnUiThread { 
                if (!isFinishing && !isDestroyed) {
                    setupConversations(all)
                }
            }

            if (config.appRunCount == 1) {
                conversations.map { it.threadId }.forEach { threadId ->
                    val messages = getMessages(threadId, includeScheduledMessages = false)
                    messages.chunked(30).forEach { chunk ->
                        messagesDB.insertMessages(*chunk.toTypedArray())
                    }
                }
            }
        }
    }

    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        var curr = binding.conversationsList.adapter
        if (curr == null) {
            hideKeyboard()
            curr = ConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )
            binding.conversationsList.adapter = curr
            if (areSystemAnimationsEnabled) binding.conversationsList.scheduleLayoutAnimation()
        }
        return curr as ConversationsAdapter
    }

    private fun setupConversations(conversations: ArrayList<Conversation>, cached: Boolean = false) {
        val sorted = conversations.sortedWith(
            compareByDescending<Conversation> { config.pinnedConversations.contains(it.threadId.toString()) }
                .thenByDescending { it.date }
        ).toMutableList() as ArrayList<Conversation>

        if (cached && config.appRunCount == 1) {
            showOrHideProgress(conversations.isEmpty())
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(conversations.isEmpty())
        }

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sorted) {
                    if (!cached) showOrHidePlaceholder(currentList.isEmpty())
                }
            }
        } catch (_: Exception) {}
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
        } else {
            binding.conversationsProgressBar.hide()
            binding.noConversationsPlaceholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        binding.conversationsFastscroller.beGoneIf(show)
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder2.beVisibleIf(show)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        val conv = any as Conversation
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, conv.threadId)
            putExtra(THREAD_TITLE, conv.title)
            startActivity(this)
        }
    }

    private fun handleLongClick(any: Any) {
        // Not used
    }

    private fun launchNewConversation() {
        hideKeyboard()
        startActivity(Intent(this, NewConversationActivity::class.java))
    }

    // -----------------------------
    // SEARCH RESULTS
    // -----------------------------
    private fun searchTextChanged(text: String) {
        lastSearchedText = text

        if (text.length >= 2) {
            binding.mainNestedScrollview.getChildAt(0).beGone()
            binding.searchHolder.beVisible()
            binding.searchHolder.animate().alpha(1f).setDuration(200L).start()

            ensureBackgroundThread {
                val searchQuery = "%$text%"
                val messages = messagesDB.getMessagesWithText(searchQuery)
                val conversations = conversationsDB.getConversationsWithText(searchQuery)
                if (text == lastSearchedText) showSearchResults(messages, conversations, text)
            }
        } else {
            binding.mainNestedScrollview.getChildAt(0).beVisible()
            binding.searchHolder.beGone()
            binding.searchHolder.alpha = 0f
        }
    }

    private fun setupSearchEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.novaSearchInput) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.novaSearchBar.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                bottomMargin = if (imeInsets.bottom > 0) {
                    imeInsets.bottom + 16.getScaledPx()
                } else {
                    systemBars.bottom + 16.getScaledPx()
                }
            }
            insets
        }
    }

    private fun showSearchResults(
        messages: List<Message>,
        conversations: List<Conversation>,
        searchedText: String
    ) {
        val results = ArrayList<SearchResult>()

        conversations.forEach { conv ->
            val date = (conv.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )
            results.add(
                SearchResult(
                    messageId = -1,
                    title = conv.title,
                    snippet = conv.phoneNumber,
                    date = date,
                    threadId = conv.threadId,
                    photoUri = conv.photoUri
                )
            )
        }

        messages.sortedByDescending { it.id }.forEach { msg ->
            var recipient = msg.senderName
            if (recipient.isEmpty() && msg.participants.isNotEmpty()) {
                recipient = TextUtils.join(", ", msg.participants.map { it.name })
            }

            val date = (msg.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            results.add(
                SearchResult(
                    messageId = msg.id,
                    title = recipient,
                    snippet = msg.body,
                    date = date,
                    threadId = msg.threadId,
                    photoUri = msg.senderPhotoUri
                )
            )
        }

        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            binding.searchPlaceholder.beGoneIf(results.isNotEmpty())
            binding.searchPlaceholder2.beGoneIf(results.isNotEmpty())

            val curr = binding.searchResultsList.adapter
            if (curr == null) {
                SearchResultsAdapter(
                    this,
                    results,
                    binding.searchResultsList,
                    searchedText
                ) {
                    hideKeyboard()
                    Intent(this, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, (it as SearchResult).threadId)
                        putExtra(THREAD_TITLE, it.title)
                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
                        startActivity(this)
                    }
                }.also {
                    binding.searchResultsList.adapter = it
                }
            } else {
                (curr as SearchResultsAdapter).updateItems(results, searchedText)
            }
        }
    }

    // -----------------------------
    // SHORTCUTS + WHATS NEW
    // -----------------------------
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        if (isActivityVisible && !isFinishing && !isDestroyed) {
            initMessenger()
        }
    }

    private fun checkWhatsNewDialog() {
        // arrayListOf<org.fossify.commons.models.Release>().apply {
        //     checkWhatsNew(this, BuildConfig.VERSION_CODE)
        // }
    }
}
