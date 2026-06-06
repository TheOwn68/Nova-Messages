package org.nova.messages.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Bundle
import android.provider.Settings
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
                setupParticipants()
                setupCustomNotifications()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.conversationDetailsAppbar, NavigationIcon.Arrow)
        applyCustomColors()
        
        // Final force-binding for modernization
        val mainTextColor = config.mainTextColor
        binding.membersHeadingLabel.setTextColor(mainTextColor)
        
        // Setup Hero Gradient
        val baseColor = config.recentColor
        val lightened = adjustColor(baseColor, 1.2f)
        val darkened = adjustColor(baseColor, 0.8f)
        val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, baseColor, darkened))
        gd.cornerRadius = 24.getScaledPx().toFloat()
        binding.detailsHeroGradient.background = gd
        
        // Hero Shadows
        binding.detailsHeroSection.elevation = 10f * resources.displayMetrics.density
        binding.detailsHeroSection.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
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

    private fun setupCustomNotifications() {
        binding.apply {
            customNotificationsSwitch.isChecked = config.customNotifications.contains(threadId.toString())
            detailsCustomizePill.beVisibleIf(customNotificationsSwitch.isChecked)

            detailsNotificationsPill.setOnClickListener {
                customNotificationsSwitch.toggle()
                if (customNotificationsSwitch.isChecked) {
                    detailsCustomizePill.beVisible()
                    config.addCustomNotificationsByThreadId(threadId)
                    createNotificationChannel()
                } else {
                    detailsCustomizePill.beGone()
                    config.removeCustomNotificationsByThreadId(threadId)
                    removeNotificationChannel()
                }
            }

            detailsCustomizePill.setOnClickListener {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, threadId.toString())
                    startActivity(this)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val name = conversation?.title
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        NotificationChannel(threadId.toString(), name, NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                audioAttributes
            )
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }

    private fun removeNotificationChannel() {
        notificationManager.deleteNotificationChannel(threadId.toString())
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
        binding.participantsGrid.adapter = adapter
    }
}
