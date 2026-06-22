package org.nova.messages.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.nova.messages.R
import org.nova.messages.databinding.ActivityImageCropperBinding
import org.nova.messages.helpers.*
import java.io.File
import java.io.FileOutputStream

class ImageCropperActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityImageCropperBinding::inflate)
    private var targetType = CROP_TARGET_BACKGROUND

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val uriString = intent.getStringExtra("uri") ?: return finish()
        targetType = intent.getIntExtra(CROP_TARGET, CROP_TARGET_BACKGROUND)

        setupTargetOverlay()

        Glide.with(this).asBitmap().load(uriString).into(object : SimpleTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                binding.cropperView.setImageBitmap(resource)
            }
        })

        binding.cropperCancel.setOnClickListener { finish() }
        binding.cropperSet.setOnClickListener { performCrop() }
    }

    private fun setupTargetOverlay() {
        val params = binding.cropperTargetArea.layoutParams as ConstraintLayout.LayoutParams
        val density = resources.displayMetrics.density
        
        // Get status bar height
        var statusBarHeight = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(resourceId)
        }

        when (targetType) {
            CROP_TARGET_TOP_BAR -> {
                params.height = (70 * density).toInt() + statusBarHeight
                params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                params.bottomToBottom = -1
                params.bottomMargin = 0
                val outline = GradientDrawable().apply {
                    setStroke((2 * density).toInt(), Color.WHITE)
                    setColor(0x44FFFFFF)
                    // Match the 26dp rounding from SimpleActivity
                    val barRadius = 26 * density
                    cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, barRadius, barRadius, barRadius, barRadius)
                }
                binding.cropperTargetArea.background = outline
            }
            CROP_TARGET_SEARCH_BAR -> {
                params.height = (55 * density).toInt()
                params.topToTop = -1
                params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                params.bottomMargin = (100 * density).toInt() // Above buttons
                val outline = GradientDrawable().apply {
                    setStroke((2 * density).toInt(), Color.WHITE)
                    setColor(0x44FFFFFF)
                    cornerRadius = 28 * density
                }
                binding.cropperTargetArea.background = outline
            }
            CROP_TARGET_BACKGROUND -> {
                params.height = 0 // Match constraints
                params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                params.bottomMargin = 0
                binding.cropperTargetArea.background = ColorDrawable(0x44FFFFFF)
            }
        }
        binding.cropperTargetArea.layoutParams = params
    }

    private fun performCrop() {
        val targetArea = binding.cropperTargetArea
        // Get absolute coordinates on screen
        val location = IntArray(2)
        targetArea.getLocationOnScreen(location)
        
        val cropperLocation = IntArray(2)
        binding.cropperView.getLocationOnScreen(cropperLocation)

        val rect = RectF(
            (location[0] - cropperLocation[0]).toFloat(),
            (location[1] - cropperLocation[1]).toFloat(),
            (location[0] - cropperLocation[0] + targetArea.width).toFloat(),
            (location[1] - cropperLocation[1] + targetArea.height).toFloat()
        )

        val croppedBitmap = binding.cropperView.crop(rect)
        if (croppedBitmap != null) {
            val file = File(cacheDir, "cropped_${System.currentTimeMillis()}.png")
            try {
                val out = FileOutputStream(file)
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
                out.close()

                val resultIntent = Intent()
                resultIntent.putExtra("cropped_uri", Uri.fromFile(file).toString())
                resultIntent.putExtra(CROP_TARGET, targetType)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } catch (e: Exception) {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
            }
        } else {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
        }
    }
}
