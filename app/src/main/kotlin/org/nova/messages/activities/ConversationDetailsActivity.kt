package org.nova.messages.activities

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.ContactsContract
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.SimpleContact
import org.nova.messages.adapters.ContactsAdapter
import org.nova.messages.databinding.ActivityConversationDetailsBinding
import org.nova.messages.dialogs.RenameConversationDialog
import org.nova.messages.extensions.*
import org.nova.messages.helpers.THREAD_ID
import org.nova.messages.models.Conversation

class ConversationDetailsActivity : SimpleActivity() {

    private var threadId: Long = 0L
    private var conversation: Conversation? = null
    private lateinit var participants: ArrayList<SimpleContact>

    private val binding by viewBinding(ActivityConversationDetailsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.conversationDetailsNestedScrollview))

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        ensureBackgroundThread {
            conversation = conversationsDB.getConversationWithThreadId(threadId)
            participants = if (conversation != null && conversation!!.isScheduled) {
                val message = messagesDB.getThreadMessages(conversation!!.threadId).firstOrNull()
                message?.participants ?: arrayListOf()
            } else {
                getThreadParticipants(threadId, null)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setupHeroSection()
                setupRenaming()
                setupMuting()
                setupAddPerson()
                setupParticipants()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.conversationDetailsAppbar, NavigationIcon.Arrow)
        applyCustomColors()
        
        // Final force-binding for modernization
        val mainTextColor = config.mainTextColor
        val topTextColor = config.topBarTextColor
        val customTypeface = getCustomTypeface()
        
        binding.conversationDetailsToolbarTitle.apply {
            setTextColor(topTextColor)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.75f))
            typeface = android.graphics.Typeface.create(customTypeface, android.graphics.Typeface.BOLD)
        }

        binding.conversationNameLabel.apply {
            setTextColor(mainTextColor)
            typeface = android.graphics.Typeface.create(customTypeface, android.graphics.Typeface.BOLD)
        }

        binding.membersHeadingLabel.setTextColor(mainTextColor)
        binding.detailsHeroName.typeface = android.graphics.Typeface.create(customTypeface, android.graphics.Typeface.BOLD)
        
        // Setup Hero Gradient
        val baseColor = config.recentColor
        val lightened = baseColor.adjustColor(1.2f)
        val darkened = baseColor.adjustColor(0.8f)
        val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, baseColor, darkened))
        gd.cornerRadius = 24.getScaledPx().toFloat()
        binding.detailsHeroGradient.background = gd
        
        // Hero Shadows
        binding.detailsHeroSection.elevation = 10f * resources.displayMetrics.density
        binding.detailsHeroSection.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        // Apply Outlines for Hero
        if (config.topBarOutline && config.useNewUi) {
            val thickness = config.topBarOutlineThickness
            val thickStroke = (thickness * resources.displayMetrics.density).toInt()
            val r_base = 24.getScaledPx().toFloat()
            val outline = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.topBarOutlineColor)
                setColor(android.graphics.Color.TRANSPARENT)
                cornerRadius = r_base
            }
            val drawable = LayerDrawable(arrayOf(outline))
            drawable.setLayerInset(0, 0, 0, 0, 0)
            binding.detailsHeroSection.foreground = drawable
        } else {
            binding.detailsHeroSection.foreground = null
        }

        // Apply theme to settings pod
        binding.muteLabel.setTextColor(mainTextColor)
        binding.addPersonLabel.setTextColor(mainTextColor)
        binding.detailsRenameIcon.applyColorFilter(mainTextColor)
        binding.detailsAddPersonIcon.applyColorFilter(mainTextColor)
    }

    private fun setupHeroSection() {
        val title = conversation?.title ?: participants.getThreadTitle()
        binding.detailsHeroName.text = title
        
        SimpleContactsHelper(this).loadContactImage(
            path = participants.firstOrNull()?.photoUri ?: "",
            imageView = binding.detailsHeroImage,
            placeholderName = title
        )
    }

    private fun setupRenaming() {
        binding.detailsRenamePill.setOnClickListener {
            RenameConversationDialog(this, conversation!!) { title ->
                binding.detailsHeroName.text = title
                ensureBackgroundThread {
                    conversation = renameConversation(conversation!!, newTitle = title)
                }
            }
        }
    }

    private fun setupMuting() {
        binding.apply {
            muteSwitch.isChecked = config.mutedThreads.contains(threadId.toString())
            detailsMutePill.setOnClickListener {
                muteSwitch.toggle()
                if (muteSwitch.isChecked) {
                    config.addMutedThread(threadId)
                } else {
                    config.removeMutedThread(threadId)
                }
            }
        }
    }

    private fun setupAddPerson() {
        binding.detailsAddPersonPill.setOnClickListener {
            val numbers = participants.flatMap { it.phoneNumbers.map { pn -> pn.normalizedNumber } }.distinct()
            Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, numbers.joinToString(";"))
                startActivity(this)
            }
        }
    }

    private fun setupParticipants() {
        // Force 2-column grid layout for members
        binding.participantsGrid.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        
        val adapter = ContactsAdapter(this, participants, binding.participantsGrid) {
            val contact = it as SimpleContact
            val address = contact.phoneNumbers.first().normalizedNumber
            getContactFromAddress(address) { simpleContact ->
                if (simpleContact != null) {
                    startContactDetailsIntent(simpleContact)
                }
            }
        }
        adapter.setUseModernPills(true)
        binding.participantsGrid.adapter = adapter
    }
}
