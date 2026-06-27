package org.nova.messages.helpers

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.nova.messages.adapters.BaseConversationsAdapter

class ModernDragCallback(private val adapter: BaseConversationsAdapter) : ItemTouchHelper.Callback() {

    private var recyclerView: RecyclerView? = null

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        this.recyclerView = recyclerView
        val position = viewHolder.bindingAdapterPosition
        val config = Config.newInstance(recyclerView.context)
        
        return if (position >= 2 && config.contactSortingMode == 0) {
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            makeMovementFlags(dragFlags, 0)
        } else {
            makeMovementFlags(0, 0)
        }
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        // DO NOTHING HERE. 
        // We handle target detection in onChildDraw and the swap in onDragEnded.
        // This ensures the list stays 100% stationary.
        return false
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        android.util.Log.d("DRAG_DEBUG", "onSelectedChanged: actionState=$actionState, hasViewHolder=${viewHolder != null}")
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.bindingAdapterPosition?.let { pos ->
                adapter.onDragStarted(pos)
            }
            viewHolder?.itemView?.let { view ->
                view.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start()
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                view.elevation = 40f
                view.translationZ = 20f
            }
        } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
            // Drag is finished, trigger the swap
            adapter.onDragEnded()
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
            val itemView = viewHolder.itemView
            
            // Manual Hit Detection: Center-point distance fallback
            val currentCenterX = itemView.left + dX + (itemView.width / 2)
            val currentCenterY = itemView.top + dY + (itemView.height / 2)
            
            var bestTarget = -1
            var minDistance = Float.MAX_VALUE
            
            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i)
                if (child == itemView) continue
                
                val childCenterX = child.left + (child.width / 2)
                val childCenterY = child.top + (child.height / 2)
                
                val dx = currentCenterX - childCenterX
                val dy = currentCenterY - childCenterY
                val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                
                if (dist < minDistance && dist < child.width * 0.9f) {
                    minDistance = dist
                    bestTarget = recyclerView.getChildAdapterPosition(child)
                }
            }
            
            if (bestTarget >= 2 && bestTarget != adapter.getInitialDragPosition()) {
                adapter.updateHoverTarget(bestTarget)
            } else {
                // If not hovering over a valid target (or hovering over self), clear hover
                adapter.updateHoverTarget(-1)
            }

            val density = recyclerView.resources.displayMetrics.density
            val edgeScrollRange = (130 * density).toInt() // Larger range for easier bottom scrolling
            val maxScrollAmount = (30 * density).toInt()
            
            val itemLocation = IntArray(2)
            itemView.getLocationOnScreen(itemLocation)
            val itemCenterY = itemLocation[1] + (itemView.height / 2)

            val rvLocation = IntArray(2)
            recyclerView.getLocationOnScreen(rvLocation)
            val rvTop = rvLocation[1]
            val rvBottom = rvTop + recyclerView.height

            if (itemCenterY < (rvTop + edgeScrollRange)) {
                val proximity = (rvTop + edgeScrollRange - itemCenterY).toFloat() / edgeScrollRange
                val speed = (maxScrollAmount * (proximity * proximity).coerceIn(0f, 1f)).toInt().coerceAtLeast(1)
                recyclerView.post { 
                    if (isCurrentlyActive) recyclerView.scrollBy(0, -speed)
                }
            } else if (itemCenterY > (rvBottom - edgeScrollRange)) {
                val proximity = (itemCenterY - (rvBottom - edgeScrollRange)).toFloat() / edgeScrollRange
                val speed = (maxScrollAmount * (proximity * proximity).coerceIn(0f, 1f)).toInt().coerceAtLeast(1)
                recyclerView.post {
                    if (isCurrentlyActive) recyclerView.scrollBy(0, speed)
                }
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .translationZ(0f)
            .setDuration(120)
            .start()
        viewHolder.itemView.elevation = 0f
    }

    override fun interpolateOutOfBoundsScroll(
        recyclerView: RecyclerView,
        viewSize: Int,
        viewSizeOutOfBounds: Int,
        totalSize: Int,
        msSinceStartScroll: Long
    ): Int {
        // RETURN 0: This completely disables ItemTouchHelper's internal auto-scroll.
        // We handle our own precision edge-scrolling in onChildDraw.
        return 0
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun isLongPressDragEnabled(): Boolean = false
}
