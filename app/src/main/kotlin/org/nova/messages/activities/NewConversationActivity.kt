package org.nova.messages.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.gson.Gson
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getColorStateList
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.maybeShowNumberPickerDialog
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.SimpleContact
import org.nova.messages.R
import org.nova.messages.adapters.ContactsAdapter
import org.nova.messages.databinding.ActivityNewConversationBinding
import org.nova.messages.databinding.ItemSuggestedContactBinding
import org.nova.messages.extensions.*
import org.nova.messages.helpers.SmsIntentParser
import org.nova.messages.helpers.THREAD_ATTACHMENT_URI
import org.nova.messages.helpers.THREAD_ATTACHMENT_URIS
import org.nova.messages.helpers.THREAD_ID
import org.nova.messages.helpers.THREAD_NUMBER
import org.nova.messages.helpers.THREAD_TEXT
import org.nova.messages.helpers.THREAD_TITLE
import org.nova.messages.messaging.isShortCodeWithLetters
import java.net.URLDecoder
import java.util.Locale

class NewConversationActivity : SimpleActivity() {
    private var allContacts = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()

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
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.newConversationAppbar, NavigationIcon.Arrow)
        binding.newConversationToolbar.setNavigationOnClickListener {
            finish()
        }
        binding.newConversationToolbar.title = "" // Clear standard title to use custom TextView

        val mainTextColor = config.mainTextColor
        binding.noContactsPlaceholder2.setTextColor(getProperPrimaryColor())
        binding.noContactsPlaceholder2.underlineText()
        applyCustomColors()
        
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
                fetchContacts() // Restore suggestions if search is cleared
            } else {
                val adapter = binding.contactsList.adapter as? ContactsAdapter
                adapter?.setSuggestionsCount(0) // Hide suggestions during search
                setupAdapter(filteredContacts)
            }

            binding.newConversationConfirm.beVisibleIf(searchString.length > 2)
        }

        binding.newConversationConfirm.applyColorFilter(getProperTextColor())
        binding.newConversationConfirm.setOnClickListener {
            val number = binding.newConversationAddress.value
            if (isShortCodeWithLetters(number)) {
                binding.newConversationAddress.setText("")
                toast(R.string.invalid_short_code, length = Toast.LENGTH_LONG)
                return@setOnClickListener
            }
            launchThreadActivity(number, number)
        }

        binding.noContactsPlaceholder2.setOnClickListener {
            handlePermission(PERMISSION_READ_CONTACTS) {
                if (it) {
                    fetchContacts()
                }
            }
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.contactsLetterFastscroller.textColor = getProperTextColor().getColorStateList()
        binding.contactsLetterFastscroller.pressedTextColor = properPrimaryColor
        binding.contactsLetterFastscrollerThumb.setupWithFastScroller(binding.contactsLetterFastscroller)
        binding.contactsLetterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
        binding.contactsLetterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
    }

    private fun isThirdPartyIntent(): Boolean {
        val result = SmsIntentParser.parse(intent)

        if (result != null && (result.first.isNotEmpty() || result.second.isNotEmpty())) {
            val (body, recipients) = result
            launchThreadActivity(
                phoneNumber = URLDecoder.decode(recipients.replace("+", "%2b").trim()),
                name = "",
                body = body
            )
            finish()
            return true
        }
        return false
    }

    private fun fetchContacts() {
        fillSuggestedContacts {
            SimpleContactsHelper(this).getAvailableContacts(false) {
                allContacts = it

                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    
                    if (config.useNewUi) {
                        // fillSuggestedContacts already calls setupAdapter for New UI
                    } else {
                        setupAdapter(allContacts)
                    }
                }
            }
        }
    }

    private fun setupSearchEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.newConversationAddress) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.newConversationSearchContainer.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                bottomMargin = if (imeInsets.bottom > 0) {
                    imeInsets.bottom + 16.getScaledPx()
                } else {
                    systemBars.bottom + 16.getScaledPx()
                }
            }
            insets
        }
    }

    private fun setupAdapter(contacts: ArrayList<SimpleContact>) {
        val hasContacts = contacts.isNotEmpty()
        binding.contactsList.beVisibleIf(hasContacts)
        binding.noContactsPlaceholder.beVisibleIf(!hasContacts)
        binding.noContactsPlaceholder2.beVisibleIf(
            !hasContacts && !hasPermission(
                PERMISSION_READ_CONTACTS
            )
        )

        if (!hasContacts) {
            val placeholderText = if (hasPermission(PERMISSION_READ_CONTACTS)) {
                org.fossify.commons.R.string.no_contacts_found
            } else {
                org.fossify.commons.R.string.no_access_to_contacts
            }

            binding.noContactsPlaceholder.text = getString(placeholderText)
        }

        val useNewUi = config.useNewUi
        if (useNewUi) {
            if (binding.contactsList.layoutManager !is androidx.recyclerview.widget.GridLayoutManager) {
                val gm = androidx.recyclerview.widget.GridLayoutManager(this, 2)
                gm.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        val adapter = binding.contactsList.adapter as? ContactsAdapter
                        return if (adapter?.getItemViewType(position) == ContactsAdapter.VIEW_TYPE_SUGGESTION) 2 else 1
                    }
                }
                binding.contactsList.layoutManager = gm
            }
        } else {
            if (binding.contactsList.layoutManager !is org.fossify.commons.views.MyLinearLayoutManager) {
                binding.contactsList.layoutManager = org.fossify.commons.views.MyLinearLayoutManager(this)
            }
        }

        val currAdapter = binding.contactsList.adapter as? ContactsAdapter
        if (currAdapter == null) {
            ContactsAdapter(this, contacts, binding.contactsList) {
                hideKeyboard()
                val contact = it as SimpleContact
                maybeShowNumberPickerDialog(contact.phoneNumbers) { number ->
                    launchThreadActivity(number.normalizedNumber, contact.name)
                }
            }.apply {
                binding.contactsList.adapter = this
            }

            if (areSystemAnimationsEnabled) {
                binding.contactsList.scheduleLayoutAnimation()
            }
        } else {
            currAdapter.updateContacts(contacts)
        }

        setupLetterFastscroller(contacts)
    }

    private fun fillSuggestedContacts(callback: () -> Unit) {
        val privateCursor = getMyContactsCursor(false, true)
        ensureBackgroundThread {
            privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val suggestions = getSuggestedContacts(privateContacts)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                
                // Merge suggestions into the main list for the modern layout
                if (config.useNewUi) {
                    val mergedList = ArrayList<SimpleContact>()
                    suggestions.forEach { mergedList.add(it) }
                    allContacts.forEach { contact ->
                        if (!suggestions.any { it.contactId == contact.contactId }) {
                            mergedList.add(contact)
                        }
                    }
                    val adapter = binding.contactsList.adapter as? ContactsAdapter
                    adapter?.setSuggestionsCount(suggestions.size)
                    setupAdapter(mergedList)
                }
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
        val number = if (numbers.size == 1) phoneNumber else Gson().toJson(numbers)
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, getThreadId(numbers))
            putExtra(THREAD_TITLE, name)
            putExtra(THREAD_TEXT, body.ifEmpty { intent.getStringExtra(Intent.EXTRA_TEXT) })
            putExtra(THREAD_NUMBER, number)

            if (intent.action == Intent.ACTION_SEND && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URI, uri?.toString())
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
}
