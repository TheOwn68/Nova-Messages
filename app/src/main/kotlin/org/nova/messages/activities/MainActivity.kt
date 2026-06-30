package org.nova.messages.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
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

    override var isSearchBarEnabled = false

    private val MAKE_DEFAULT_APP_REQUEST = 1
    private var storedTextColor = 0
    private var lastSearchedText = ""
    private var bus: EventBus? = null
    private var isActivityVisible = false
    private var isFirstResume = true
    private var lastRefreshTime = 0L
    private var isInitialized = false
    private var wasImeVisible = false
    private var isSearchExpanded = false

    private val binding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.novaTitle.text = "Nova Messages"

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))
        setupSearchEdgeToEdge()
        setupTopAppBar(binding.mainAppbar, NavigationIcon.None, Color.TRANSPARENT)

        setupNovaNavBar()
        loadMessages()
        
        org.nova.messages.helpers.UpdateHelper.checkForUpdate(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainCoordinator) { _, insets ->
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (wasImeVisible && !isImeVisible && config.useNewUi && binding.novaSearchInput.text?.isEmpty() == true) {
                shrinkSearchBar()
            }
            wasImeVisible = isImeVisible
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        applyOutlines()

        initMessenger()
        
        val now = System.currentTimeMillis()
        val currentAdapter = binding.conversationsList.adapter as? ConversationsAdapter
        if (now - lastRefreshTime > 15000 || currentAdapter == null || currentAdapter.itemCount == 0) {
            lastRefreshTime = now
            syncConversations(ArrayList())
        }

        val mainTextColor = config.mainTextColor
        getOrCreateConversationsAdapter().apply {
            if (storedTextColor != mainTextColor) updateTextColor(mainTextColor)
            updateDrafts()
        }

        binding.novaTitle.updateLayoutParams<Toolbar.LayoutParams> {
            marginEnd = 60.getScaledPx()
        }
        setupScaledToolbar(binding.mainToolbar)
        binding.conversationsFab.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
            topMargin = 3.getScaledPx()
            marginEnd = 12.getScaledPx()
        }

        binding.novaNavContainer.updateLayoutParams {
            height = 55.getScaledPx()
        }

        getOrCreateConversationsAdapter().updateScaling()
        applyCustomColors()
        setupNovaNavBar()

        binding.conversationsFab.setTextColor(config.topBarTextColor)
        binding.novaSearchInput.setTextColor(config.inputBarTextColor)
        binding.novaSearchInput.setHintTextColor(config.inputBarTextColor.withAlpha(0.5f))

        if (isFirstResume && config.useNewUi) {
            isFirstResume = false
            binding.mainAppbar.pivotY = 0f
            binding.mainAppbar.scaleY = 0.4f
            binding.mainAppbar.alpha = 0f
            binding.mainAppbar.animate()
                .scaleY(1f)
                .alpha(1f)
                .setDuration(800)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
                .start()
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
        storedTextColor = getProperTextColor()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    private fun setupNovaNavBar() = binding.apply {
        if (config.useNewUi) {
            novaNavContainer.beVisible()
            
            // Apply compact width and transparency
            novaNavContainer.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                width = 240.getScaledPx()
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            }
            novaNavContainer.alpha = 0.92f
            
            // Set icon transparency
            navHomeIcon.alpha = 0.9f // Lighter for active
            navSettingsIcon.alpha = 0.6f
            novaSearchIcon.alpha = 0.6f
            
            // Highlight Home (Current Screen) with subtle transparency
            navHomeBtn.setBackgroundColor(Color.WHITE.withAlpha(0.1f))
            
            navSearchContainer.setOnClickListener {
                if (!isSearchExpanded) expandSearchBar()
            }
            
            navSettingsBtn.setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
            
            navHomeBtn.setOnClickListener {
                binding.conversationsList.smoothScrollToPosition(0)
            }

            if (novaSearchInput.tag != "text_watcher_attached") {
                novaSearchInput.addTextChangedListener { text ->
                    searchTextChanged(text?.toString() ?: "")
                }
                novaSearchInput.tag = "text_watcher_attached"
            }
            
            // Set initial state
            if (!isSearchExpanded) {
                navDivider1.beVisible()
                navDivider2.beVisible()
                navHomeBtn.beVisible()
                navSettingsBtn.beVisible()
                novaSearchInput.beGone()
                
                // Center search icon when collapsed
                (novaSearchIcon.layoutParams as? LinearLayout.LayoutParams)?.marginStart = 0
                navSearchContainer.gravity = android.view.Gravity.CENTER
            }
        } else {
            novaNavContainer.beGone()
        }
    }

    private fun expandSearchBar() = binding.apply {
        if (isSearchExpanded) return@apply
        isSearchExpanded = true
        
        val startWidth = 240.getScaledPx()
        val endWidth = root.width - 32.getScaledPx()
        
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 400
        animator.interpolator = OvershootInterpolator(1.0f)
        
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            
            // Expand width
            val currentWidth = startWidth + ((endWidth - startWidth) * value).toInt()
            novaNavContainer.updateLayoutParams { width = currentWidth }
            
            // Shrink side buttons weight
            val weight = 1f - value
            navHomeBtn.layoutParams = (navHomeBtn.layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
            navSettingsBtn.layoutParams = (navSettingsBtn.layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
            
            // Expand search container weight
            navSearchContainer.layoutParams = (navSearchContainer.layoutParams as LinearLayout.LayoutParams).apply { this.weight = 1f + (2f * value) }
            
            // Fade out dividers and side icons
            navDivider1.alpha = 1f - value
            navDivider2.alpha = 1f - value
            navHomeIcon.alpha = 0.9f * (1f - value)
            navSettingsIcon.alpha = 0.6f * (1f - value)
            
            // Move search icon to start
            navSearchContainer.gravity = if (value > 0.5f) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.CENTER
            (novaSearchIcon.layoutParams as? LinearLayout.LayoutParams)?.marginStart = (12.getScaledPx() * value).toInt()
            
            novaNavContainer.requestLayout()
        }
        
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                novaNavContainer.alpha = 1.0f // Solid when searching
            }
            override fun onAnimationEnd(animation: Animator) {
                navHomeBtn.beGone()
                navSettingsBtn.beGone()
                navDivider1.beGone()
                navDivider2.beGone()
                novaSearchInput.beVisible()
                novaSearchInput.requestFocus()
                showKeyboard(novaSearchInput)
            }
        })
        animator.start()
    }

    private fun shrinkSearchBar() = binding.apply {
        if (!isSearchExpanded) return@apply
        isSearchExpanded = false
        
        navHomeBtn.beVisible()
        navSettingsBtn.beVisible()
        navDivider1.beVisible()
        navDivider2.beVisible()
        novaSearchInput.beGone()
        hideKeyboard()
        
        val startWidth = novaNavContainer.width
        val endWidth = 240.getScaledPx()
        
        val animator = ValueAnimator.ofFloat(1f, 0f)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            
            // Shrink width
            val currentWidth = endWidth + ((startWidth - endWidth) * value).toInt()
            novaNavContainer.updateLayoutParams { width = currentWidth }
            
            val weight = 1f - value
            navHomeBtn.layoutParams = (navHomeBtn.layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
            navSettingsBtn.layoutParams = (navSettingsBtn.layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
            navSearchContainer.layoutParams = (navSearchContainer.layoutParams as LinearLayout.LayoutParams).apply { this.weight = 1f + (2f * value) }
            
            navDivider1.alpha = 1f - value
            navDivider2.alpha = 1f - value
            navHomeIcon.alpha = 0.9f * (1f - value)
            navSettingsIcon.alpha = 0.6f * (1f - value)
            
            // Recenter search icon
            navSearchContainer.gravity = if (value < 0.5f) android.view.Gravity.CENTER else android.view.Gravity.CENTER_VERTICAL
            (novaSearchIcon.layoutParams as? LinearLayout.LayoutParams)?.marginStart = (12.getScaledPx() * value).toInt()
            
            novaNavContainer.requestLayout()
        }
        
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                novaNavContainer.alpha = 0.92f // Less transparent when idle
            }
        })
        animator.start()
    }

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

    private fun initMessenger(isManualReorder: Boolean = false) {
        if (isFinishing || isDestroyed) return
        try {
            if (!isInitialized) {
                setupOneTimeViews()
                isInitialized = true
            }
            checkWhatsNewDialog()
            getCachedConversations(isManualReorder)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to init messenger during transition", e)
        }
    }

    private fun setupOneTimeViews() {
        binding.noConversationsPlaceholder2.setOnClickListener { launchNewConversation() }
        binding.conversationsFab.setOnClickListener { launchNewConversation() }

        val fabAnim = AnimationUtils.loadAnimation(this, R.anim.fab_in)
        binding.conversationsFab.startAnimation(fabAnim)

        val searchAnim = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom)
        binding.novaNavContainer.startAnimation(searchAnim)

        binding.novaSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
    }

    private fun getCachedConversations(isManualReorder: Boolean = false) {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) { ArrayList() }

            val archived = try { conversationsDB.getAllArchived() } catch (_: Exception) { listOf() }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setupConversations(conversations, cached = true, isManualReorder = isManualReorder)
                syncConversations((conversations + archived).toMutableList() as ArrayList<Conversation>, isManualReorder = isManualReorder)
                applyOutlines()
            }

            conversations.forEach { clearExpiredScheduledMessages(it.threadId) }
        }
    }

    private fun syncConversations(cached: ArrayList<Conversation>, isManualReorder: Boolean = false) {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val conversations = getConversations(privateContacts = privateContacts)
            insertOrUpdateConversations(conversations)
            val all = conversationsDB.getNonArchived() as ArrayList<Conversation>
            runOnUiThread { 
                if (!isFinishing && !isDestroyed) {
                    setupConversations(all, isManualReorder = isManualReorder)
                    applyOutlines()
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

    private fun setupConversations(conversations: ArrayList<Conversation>, cached: Boolean = false, isManualReorder: Boolean = false) {
        val useNewUi = config.useNewUi
        val currentAdapter = binding.conversationsList.adapter as? ConversationsAdapter
        if (currentAdapter != null && currentAdapter.itemCount > 0) {
             val isCurrentlyNewUi = binding.conversationsList.layoutManager is androidx.recyclerview.widget.GridLayoutManager
             if (isCurrentlyNewUi != useNewUi) {
                 binding.conversationsList.adapter = null
                 while (binding.conversationsList.itemDecorationCount > 0) {
                     binding.conversationsList.removeItemDecorationAt(0)
                 }
             }
        }

        val sorted = if (useNewUi) {
            val allSortedByDate = conversations.sortedByDescending { it.date }
            if (allSortedByDate.isEmpty()) {
                allSortedByDate
            } else {
                val top2 = allSortedByDate.take(2)
                val remaining = allSortedByDate.filter { conv -> !top2.any { it.threadId == conv.threadId } }
                
                val sortedPills = when (config.contactSortingMode) {
                    1 -> remaining.sortedBy { it.title.lowercase() }
                    2 -> remaining.sortedByDescending { it.date }
                    else -> {
                        val manualOrder = config.conversationOrder.split(",").filter { it.isNotEmpty() }.map { it.toLong() }
                        val manuallySorted = ArrayList<Conversation>()
                        manualOrder.forEach { id ->
                            remaining.find { it.threadId == id }?.let { manuallySorted.add(it) }
                        }
                        remaining.forEach { conv ->
                            if (!manuallySorted.any { it.threadId == conv.threadId }) {
                                manuallySorted.add(conv)
                            }
                        }
                        manuallySorted
                    }
                }
                top2 + sortedPills
            }
        } else {
            conversations.sortedWith(
                compareByDescending<Conversation> { config.pinnedConversations.contains(it.threadId.toString()) }
                    .thenByDescending { it.date }
            )
        }.toMutableList() as ArrayList<Conversation>

        if (cached && config.appRunCount == 1) {
            showOrHideProgress(conversations.isEmpty())
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(conversations.isEmpty())
        }

        if (useNewUi) {
            if (binding.conversationsList.layoutManager !is androidx.recyclerview.widget.GridLayoutManager) {
                val gm = androidx.recyclerview.widget.GridLayoutManager(this, 2)
                gm.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int = if (position < 2) 2 else 1
                }
                binding.conversationsList.layoutManager = gm
                binding.conversationsList.addItemDecoration(object : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
                    override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
                        val position = parent.getChildAdapterPosition(view)
                        if (position == 2 || position == 3) outRect.top = 32.getScaledPx()
                    }
                })
                val callback = org.nova.messages.helpers.ModernDragCallback(getOrCreateConversationsAdapter())
                val touchHelper = androidx.recyclerview.widget.ItemTouchHelper(callback)
                touchHelper.attachToRecyclerView(binding.conversationsList)
                getOrCreateConversationsAdapter().itemTouchHelper = touchHelper
            }
        } else {
            if (binding.conversationsList.layoutManager !is org.fossify.commons.views.MyLinearLayoutManager) {
                binding.conversationsList.layoutManager = org.fossify.commons.views.MyLinearLayoutManager(this)
            }
        }

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sorted, shouldSuppressStateRestoration = isManualReorder) {
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
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder2.beVisibleIf(show)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged(isManualReorder: Boolean = false) {
        getOrCreateConversationsAdapter().safeNotifyDataSetChanged(shouldSuppressStateRestoration = isManualReorder)
    }

    private fun handleConversationClick(any: Any) {
        val conv = any as Conversation
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, conv.threadId)
            putExtra(THREAD_TITLE, conv.title)
            startActivity(this)
        }
    }

    private fun launchNewConversation() {
        hideKeyboard()
        startActivity(Intent(this, NewConversationActivity::class.java))
    }

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
            binding.novaNavContainer.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                bottomMargin = (if (imeInsets.bottom > 0) imeInsets.bottom else systemBars.bottom) + 16.getScaledPx()
            }
            insets
        }
    }

    private fun showSearchResults(messages: List<Message>, conversations: List<Conversation>, searchedText: String) {
        val results = ArrayList<SearchResult>()
        conversations.forEach { conv ->
            val date = (conv.date * 1000L).formatDateOrTime(context = this, hideTimeOnOtherDays = true, showCurrentYear = true)
            results.add(SearchResult(messageId = -1, title = conv.title, snippet = conv.phoneNumber, date = date, threadId = conv.threadId, photoUri = conv.photoUri))
        }
        messages.sortedByDescending { it.id }.forEach { msg ->
            var recipient = msg.senderName
            if (recipient.isEmpty() && msg.participants.isNotEmpty()) {
                recipient = TextUtils.join(", ", msg.participants.map { it.name })
            }
            val date = (msg.date * 1000L).formatDateOrTime(context = this, hideTimeOnOtherDays = true, showCurrentYear = true)
            results.add(SearchResult(messageId = msg.id, title = recipient, snippet = msg.body, date = date, threadId = msg.threadId, photoUri = msg.senderPhotoUri))
        }
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            binding.searchPlaceholder.beGoneIf(results.isNotEmpty())
            binding.searchPlaceholder2.beGoneIf(results.isNotEmpty())
            val curr = binding.searchResultsList.adapter
            if (curr == null) {
                SearchResultsAdapter(this, results, binding.searchResultsList, searchedText) {
                    hideKeyboard()
                    Intent(this, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, (it as SearchResult).threadId)
                        putExtra(THREAD_TITLE, it.title)
                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
                        startActivity(this)
                    }
                }.also { binding.searchResultsList.adapter = it }
            } else {
                (curr as SearchResultsAdapter).updateItems(results, searchedText)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        if (isActivityVisible && !isFinishing && !isDestroyed) {
            initMessenger(isManualReorder = event.isManualReorder)
        }
    }

    private fun checkWhatsNewDialog() {}

    private fun applyOutlines() = binding.apply {
        val density = resources.displayMetrics.density
        val inputBarTextColor = config.inputBarTextColor
        val isNewUi = config.useNewUi
        
        if (config.topBarOutline && isNewUi) {
            val r26 = 26f * density
            val thickness = config.topBarOutlineThickness
            val thickStroke = (thickness * density).toInt()
            val outline = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke(thickStroke * 2, config.topBarOutlineColor)
                setColor(Color.TRANSPARENT)
                val r_adj = r26 + thickStroke
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r_adj, r_adj, r_adj, r_adj)
            }
            val drawable = android.graphics.drawable.LayerDrawable(arrayOf(outline))
            val inset = -thickStroke + 1
            drawable.setLayerInset(0, inset, inset, inset, 0)
            binding.mainAppbar.foreground = drawable
        } else {
            binding.mainAppbar.foreground = null
        }

        if (config.searchBarOutline && isNewUi) {
            val thickness = config.searchBarOutlineThickness
            val thickStroke = (thickness * density).toInt()
            val r_base = 100f * density
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke(thickStroke * 2, config.searchBarOutlineColor)
                cornerRadius = r_base + thickStroke
                setColor(Color.TRANSPARENT)
            }
            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(drawable))
            val inset = -thickStroke + 1
            layerDrawable.setLayerInset(0, inset, inset, inset, inset)
            binding.novaNavContainer.foreground = layerDrawable
            
            // Sync icon and divider colors with search bar text color
            binding.navHomeIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.navSettingsIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.novaSearchIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.navDivider1.setBackgroundColor(inputBarTextColor.withAlpha(0.2f))
            binding.navDivider2.setBackgroundColor(inputBarTextColor.withAlpha(0.2f))
        } else {
            binding.novaNavContainer.foreground = null
        }
    }
}
