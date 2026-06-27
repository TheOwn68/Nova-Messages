package org.nova.messages.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.SimpleContact
import org.nova.messages.R
import org.nova.messages.adapters.ContactsAdapter
import org.nova.messages.databinding.ActivityNewConversationBinding
import org.nova.messages.extensions.*
import org.nova.messages.helpers.SmsIntentParser
import org.nova.messages.helpers.THREAD_ATTACHMENT_URI
import org.nova.messages.helpers.THREAD_ATTACHMENT_URIS
import org.nova.messages.helpers.THREAD_ID
import org.nova.messages.helpers.THREAD_NUMBER
import org.nova.messages.helpers.THREAD_TEXT
import org.nova.messages.helpers.THREAD_TITLE
import org.nova.messages.messaging.isShortCodeWithLetters
import java.util.Locale

class NewConversationActivity : SimpleActivity() {
    private var allContacts = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()
    private var wasImeVisible = false

    private val binding by viewBinding(ActivityNewConversationBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = getString(R.string.new_conversation)
        updateTextColors(binding.newConversationHolder)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.contactsList))
        setupSearchEdgeToEdge()

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        binding.newConversationAddress.requestFocus()

        // READ_CONTACTS permission is not mandatory, but without it we won't be able to show any suggestions during typing
        handlePermission(PERMISSION_READ_CONTACTS) {
            initContacts()
        }

        // Keyboard Sync for Search Bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.newConversationCoordinator) { _, insets ->
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (wasImeVisible && !isImeVisible && config.useNewUi && binding.newConversationAddress.text?.isEmpty() == true) {
                shrinkSearchBar()
            }
            wasImeVisible = isImeVisible
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        applyOutlines()
        setupTopAppBar(binding.newConversationAppbar, NavigationIcon.Arrow)
        binding.newConversationToolbar.setNavigationOnClickListener {
            finish()
        }
        binding.newConversationToolbar.title = "" // Clear standard title to use custom TextView

        binding.noContactsPlaceholder2.setTextColor(getProperPrimaryColor())
        binding.noContactsPlaceholder2.underlineText()
        updateActivityCustomColors()

        setupModernSearchBar()
    }

    private fun setupModernSearchBar() = binding.apply {
        if (!config.useNewUi) return@apply
        
        val collapsedWidth = 240.getScaledPx()
        
        // New UI: Centered search bar that expands
        newConversationSearchContainer.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
            width = if (newConversationAddress.text.isNullOrEmpty() && !newConversationAddress.hasFocus()) collapsedWidth else androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
        }

        newConversationSearchContainer.setOnClickListener {
            if (!newConversationAddress.isFocusable) {
                expandSearchBar()
            } else {
                showKeyboard(newConversationAddress)
            }
        }

        newConversationAddress.isFocusable = !newConversationAddress.text.isNullOrEmpty() || newConversationAddress.hasFocus()
        newConversationAddress.isFocusableInTouchMode = newConversationAddress.isFocusable

        newConversationAddress.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                expandSearchBar()
            } else if (newConversationAddress.text.isNullOrEmpty()) {
                shrinkSearchBar()
            }
        }
    }

    private fun expandSearchBar() = binding.apply {
        val startWidth = newConversationSearchContainer.width
        val endWidth = root.width - 32.getScaledPx()
        
        if (startWidth >= endWidth - 5) return@apply

        val animator = ValueAnimator.ofInt(startWidth, endWidth)
        animator.duration = 450
        animator.interpolator = android.view.animation.OvershootInterpolator(1.2f)
        animator.addUpdateListener { animation ->
            newConversationSearchContainer.updateLayoutParams {
                width = animation.animatedValue as Int
            }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                newConversationAddress.isFocusable = true
                newConversationAddress.isFocusableInTouchMode = true
                newConversationAddress.requestFocus()
                showKeyboard(newConversationAddress)
            }
        })
        animator.start()
    }

    private fun shrinkSearchBar() = binding.apply {
        val startWidth = newConversationSearchContainer.width
        val endWidth = 240.getScaledPx()
        
        if (startWidth <= endWidth + 5) return@apply

        val animator = ValueAnimator.ofInt(startWidth, endWidth)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            newConversationSearchContainer.updateLayoutParams {
                width = animation.animatedValue as Int
            }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                newConversationAddress.isFocusable = false
                newConversationAddress.isFocusableInTouchMode = false
                hideKeyboard()
            }
        })
        animator.start()
    }

    private fun updateActivityCustomColors() {
        applyCustomColors()
        val mainTextColor = config.mainTextColor
        binding.newConversationAddress.setTextColor(config.inputBarTextColor)
        binding.newConversationAddress.setHintTextColor(config.inputBarTextColor.withAlpha(0.5f))
        binding.newConversationConfirm.applyColorFilter(config.inputBarTextColor)

        // Fast Scroller Sync
        val properPrimaryColor = getProperPrimaryColor()
        binding.contactsLetterFastscroller.textColor = mainTextColor.getColorStateList()
        binding.contactsLetterFastscroller.pressedTextColor = properPrimaryColor
        binding.contactsLetterFastscrollerThumb.setupWithFastScroller(binding.contactsLetterFastscroller)
        binding.contactsLetterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
        binding.contactsLetterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
    }

    private fun initContacts() {
        if (isThirdPartyIntent()) {
            return
        }

        fetchContacts()
        binding.newConversationAddress.onTextChangeListener { searchString ->
            val filteredContacts = ArrayList<SimpleContact>()
            allContacts.forEach { contact ->
                if (contact.phoneNumbers.any { it.normalizedNumber.contains(searchString, true) } ||
                    contact.name.contains(searchString, true) ||
                    contact.name.contains(searchString.normalizeString(), true) ||
                    contact.name.normalizeString().contains(searchString, true)) {
                    filteredContacts.add(contact)
                }
            }

            filteredContacts.sortWith(compareBy { !it.name.startsWith(searchString, true) })

            if (config.useNewUi && searchString.isEmpty()) {
                fillSuggestedContacts {
                    setupAdapter(filteredContacts)
                }
            } else {
                setupAdapter(filteredContacts)
            }

            val shortCodeWithLetters = isShortCodeWithLetters(searchString)
            binding.newConversationConfirm.beVisibleIf(searchString.isNotEmpty() && !shortCodeWithLetters)
            binding.newConversationConfirm.applyColorFilter(getProperTextColor())
            binding.newConversationConfirm.setOnClickListener {
                if (searchString.isPhoneNumber()) {
                    launchThreadActivity(searchString, searchString)
                } else if (shortCodeWithLetters) {
                    toast(R.string.invalid_short_code, length = Toast.LENGTH_LONG)
                } else {
                    launchThreadActivity(searchString, searchString)
                }
            }
        }
    }

    private fun isThirdPartyIntent(): Boolean {
        val result = SmsIntentParser.parse(intent)
        if (result != null && (result.first.isNotEmpty() || result.second.isNotEmpty())) {
            val phoneNumber = result.first
            val body = result.second
            launchThreadActivity(phoneNumber, phoneNumber, body)
            finish()
            return true
        }
        return false
    }

    private fun fetchContacts() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                ensureBackgroundThread {
                    SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
                        allContacts = contacts as ArrayList<SimpleContact>
                        
                        val privateCursor = getMyContactsCursor(false, true)
                        privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
                        
                        if (privateContacts.isNotEmpty()) {
                            allContacts.addAll(privateContacts)
                            allContacts.sortBy { it.name.lowercase() }
                        }

                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            if (config.useNewUi) {
                                fillSuggestedContacts {
                                    setupAdapter(allContacts)
                                }
                            } else {
                                setupAdapter(allContacts)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupSearchEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.newConversationSearchContainer) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navigationHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                bottomMargin = 16.getScaledPx() + Math.max(imeHeight, navigationHeight)
            }
            insets
        }
    }

    private fun setupAdapter(contacts: ArrayList<SimpleContact>) {
        val hasContacts = contacts.isNotEmpty()
        binding.contactsList.beVisibleIf(hasContacts)
        binding.noContactsPlaceholder.beVisibleIf(!hasContacts)
        binding.noContactsPlaceholder2.beVisibleIf(!hasContacts && !hasPermission(PERMISSION_READ_CONTACTS))

        val placeholderText = if (hasPermission(PERMISSION_READ_CONTACTS)) org.fossify.commons.R.string.no_contacts_found else org.fossify.commons.R.string.no_access_to_contacts
        binding.noContactsPlaceholder.text = getString(placeholderText)

        val currAdapter = binding.contactsList.adapter
        if (currAdapter == null) {
            ContactsAdapter(this, contacts, binding.contactsList) {
                val contact = it as SimpleContact
                if (contact.phoneNumbers.size == 1) {
                    launchThreadActivity(contact.phoneNumbers.first().normalizedNumber, contact.name)
                } else {
                    maybeShowNumberPickerDialog(contact.phoneNumbers) { number ->
                        launchThreadActivity(number.normalizedNumber, contact.name)
                    }
                }
            }.apply {
                binding.contactsList.adapter = this
            }

            if (areSystemAnimationsEnabled) {
                binding.contactsList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).updateContacts(contacts as List<Any>)
        }

        setupLetterFastscroller(contacts)
    }

    private fun fillSuggestedContacts(callback: () -> Unit) {
        ensureBackgroundThread {
            val privateCursor = getMyContactsCursor(false, true)
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            
            val suggestions = getSuggestedContacts(privateContacts)
            
            // Add suggested contacts to the list if they're not already there
            suggestions.forEach { contact ->
                if (!allContacts.any { it.contactId == contact.contactId }) {
                    allContacts.add(contact)
                }
            }
            
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                callback()
            }
        }
    }

    private fun setupLetterFastscroller(contacts: ArrayList<SimpleContact>) {
        binding.contactsLetterFastscroller.setupWithRecyclerView(binding.contactsList, { position ->
            try {
                val name = contacts[position].name
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(
                    character.uppercase(Locale.getDefault()).normalizeString()
                )
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }

    private fun launchThreadActivity(phoneNumber: String, name: String, body: String = "") {
        hideKeyboard()
        val numbers = phoneNumber.split(";").toSet()
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, this@NewConversationActivity.getThreadId(numbers))
            putExtra(THREAD_TITLE, name)
            putExtra(THREAD_NUMBER, phoneNumber)
            putExtra(THREAD_TEXT, body)
            
            val uri = intent.getParcelableExtra<Uri>(THREAD_ATTACHMENT_URI)
            if (uri != null) {
                putExtra(THREAD_ATTACHMENT_URI, uri.toString())
            } else if (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.extras?.containsKey(
                    Intent.EXTRA_STREAM
                ) == true
            ) {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URIS, uris)
            }

            startActivity(this)
        }
    }

    override fun getAppIconIDs() = arrayListOf(R.mipmap.ic_launcher)
    override fun getAppLauncherName() = getString(R.string.app_launcher_name)
    override fun getRepositoryName() = "Messages"

    private fun applyOutlines() = binding.apply {
        val density = resources.displayMetrics.density
        val thickStroke = (2.5 * density).toInt()
        val isNewUi = config.useNewUi
        
        // Top Bar Outline (New Conversation)
        if (config.topBarOutline && isNewUi) {
            val r26 = 26f * density
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.topBarOutlineColor)
                setColor(Color.TRANSPARENT)
                // Match the 26dp bottom corners of the modern top bar
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r26, r26, r26, r26)
            }
            binding.newConversationAppbar.foreground = drawable
        } else {
            binding.newConversationAppbar.foreground = null
        }

        // Search Bar Outline
        if (config.searchBarOutline && isNewUi) {
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.searchBarOutlineColor)
                cornerRadius = 100f * density
                setColor(Color.TRANSPARENT)
            }
            binding.newConversationSearchContainer.foreground = drawable
        } else {
            binding.newConversationSearchContainer.foreground = null
        }
    }
}
