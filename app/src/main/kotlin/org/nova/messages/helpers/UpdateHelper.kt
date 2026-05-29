package org.nova.messages.helpers

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.nova.messages.BuildConfig
import org.nova.messages.R
import org.nova.messages.activities.SimpleActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateHelper {

    private const val GITHUB_API_URL = "https://api.github.com/repos/NovaOrg/Messages/releases/latest"
    private const val GITHUB_RELEASE_URL = "https://github.com/NovaOrg/Messages/releases/latest"

    fun checkForUpdate(activity: SimpleActivity) {
        ensureBackgroundThread {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val latestVersion = json.getString("tag_name").replace("v", "").trim()
                    val currentVersion = BuildConfig.VERSION_NAME.trim()

                    if (isVersionNewer(latestVersion, currentVersion)) {
                        activity.runOnUiThread {
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                showUpdateDialog(activity, latestVersion)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UpdateHelper", "Failed to check for updates", e)
            }
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toInt() }
            val currentParts = current.split(".").map { it.toInt() }
            
            val length = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until length) {
                val l = if (i < latestParts.size) latestParts[i] else 0
                val c = if (i < currentParts.size) currentParts[i] else 0
                if (l > c) return true
                if (l < c) return false
            }
        } catch (_: Exception) {}
        return latest > current // Fallback
    }

    private fun showUpdateDialog(activity: SimpleActivity, version: String) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update_checker, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .create()

        val bgColor = activity.getProperBackgroundColor()
        val textColor = activity.getProperTextColor()

        view.findViewById<View>(R.id.update_holder)?.setBackgroundColor(bgColor)
        view.findViewById<TextView>(R.id.update_title)?.setTextColor(textColor)
        view.findViewById<TextView>(R.id.update_description)?.apply {
            setTextColor(textColor)
            text = "A new version ($version) of Nova Messages is available on GitHub. Would you like to download the update?"
        }

        view.findViewById<View>(R.id.update_later)?.setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.update_download)?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASE_URL))
                activity.startActivity(intent)
            } catch (_: Exception) {}
            dialog.dismiss()
        }

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Slide up animation
            window.attributes.windowAnimations = android.R.style.Animation_Dialog
            // Dim background
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.6f)
        }

        dialog.show()
    }
}
