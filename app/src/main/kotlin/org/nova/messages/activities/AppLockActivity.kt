package org.nova.messages.activities

import android.os.Bundle
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.nova.messages.R
import org.nova.messages.databinding.ActivityAppLockBinding
import org.nova.messages.extensions.config
import org.nova.messages.helpers.*
import java.security.MessageDigest
import java.util.concurrent.Executor

class AppLockActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAppLockBinding::inflate)
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
            }
        })

        val lockType = config.appLockType
        if (lockType == LOCK_NONE) {
            unlock()
            return
        }

        setupUI(lockType)
        applyCustomColors()
        val mainTextColor = config.mainTextColor
        binding.appLockTitle.setTextColor(mainTextColor)
        binding.appLockDesc.setTextColor(mainTextColor.withAlpha(0.7f))
        binding.appLockInput.setTextColor(config.inputBarTextColor)
        
        if (lockType == LOCK_FINGERPRINT) {
            setupBiometric()
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun setupUI(lockType: Int) = binding.apply {
        appLockInput.beVisibleIf(lockType == LOCK_PIN || lockType == LOCK_PASSWORD)
        appLockConfirm.beVisibleIf(lockType == LOCK_PIN || lockType == LOCK_PASSWORD)
        appLockFingerprint.beVisibleIf(lockType == LOCK_FINGERPRINT)

        appLockDesc.text = when (lockType) {
            LOCK_PIN -> "Enter PIN"
            LOCK_PASSWORD -> "Enter Password"
            LOCK_FINGERPRINT -> "Unlock with Fingerprint"
            else -> ""
        }

        appLockInput.inputType = if (lockType == LOCK_PIN) {
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        } else {
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        appLockConfirm.setOnClickListener {
            val input = appLockInput.text.toString()
            if (input.toSha256() == config.appLockPassword) {
                unlock()
            } else {
                toast("Wrong code")
            }
        }

        appLockFingerprint.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun setupBiometric() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showErrorToast(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    unlock()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    toast("Authentication failed")
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for Nova Messages")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Cancel")
            .build()
    }

    private fun unlock() {
        isUnlocked = true
        finish()
    }

    private fun String.toSha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        var isUnlocked = false
    }
}
