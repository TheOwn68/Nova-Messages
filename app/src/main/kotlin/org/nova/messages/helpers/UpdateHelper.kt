package org.nova.messages.helpers

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.helpers.ensureBackgroundThread
import org.nova.messages.BuildConfig
import org.nova.messages.R
import org.nova.messages.activities.SimpleActivity
import org.nova.messages.databinding.DialogUpdateCheckerBinding
import org.nova.messages.extensions.config
import java.net.HttpURLConnection
import java.net.URL

object UpdateHelper {

    private const val LATEST_RELEASE_URL = "https://github.com/TheOwn68/Nova-Messages/releases/latest"

    fun checkForUpdate(activity: SimpleActivity) {
        ensureBackgroundThread {
            try {
                val url = URL(LATEST_RELEASE_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connect()
                
                val location = connection.getHeaderField("Location")
                if (location != null) {
                    val latestVersionTag = location.substringAfterLast("/")
                    val currentVersion = "v${BuildConfig.VERSION_NAME}"
                    
                    if (isNewer(latestVersionTag, currentVersion)) {
                        activity.runOnUiThread {
                            showUpdateDialog(activity, location)
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("UpdateHelper", "Failed to check for updates", e)
            }
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        // Simple version comparison (e.g. v2.0.8 vs v2.0.7)
        // Strip 'v' prefix
        val latestClean = latest.removePrefix("v")
        val currentClean = current.removePrefix("v")
        
        val latestParts = latestClean.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }
        
        val length = kotlin.math.min(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }

    private fun showUpdateDialog(activity: SimpleActivity, updateUrl: String) {
        if (activity.isFinishing || activity.isDestroyed) return

        val binding = DialogUpdateCheckerBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Standard dimming
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.7f)
            
            // Modern slide-up animation
            window.setWindowAnimations(android.R.style.Animation_InputMethod)
        }

        // Apply Theming
        val barColor = if (activity.config.topBarColor != 0) activity.config.topBarColor else Color.BLACK
        val barTextColor = activity.config.topBarTextColor
        val density = activity.resources.displayMetrics.density
        
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24 * density
            setColor(barColor)
        }
        binding.updateDialogHolder.background = bg
        
        binding.updateDialogTitle.setTextColor(barTextColor)
        binding.updateDialogDescription.setTextColor(barTextColor)
        binding.updateDialogDescription.alpha = 0.8f
        
        binding.updateDialogLater.setTextColor(barTextColor)
        binding.updateDialogUpdate.setTextColor(barTextColor)

        binding.updateDialogLater.setOnClickListener {
            dialog.dismiss()
        }

        binding.updateDialogUpdate.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
            activity.startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
        
        // Manual smooth animation if system one isn't enough
        val anim = AnimationUtils.loadAnimation(activity, R.anim.slide_in_bottom)
        binding.root.startAnimation(anim)
    }
}
