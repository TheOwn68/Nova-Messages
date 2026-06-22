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
        
        return if (position >= 2) {
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            makeMovementFlags(dragFlags, 0)
        } else {
            makeMovementFlags(0, 0)
        }
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        val to = target.bindingAdapterPosition
        if (to < 2) return false
        
        adapter.onItemSwapped(to)
        return true
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.bindingAdapterPosition?.let { pos ->
                adapter.onDragStarted(pos)
            }
            viewHolder?.itemView?.let { view ->
                view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
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
        isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
            val itemView = viewHolder.itemView
            val density = recyclerView.resources.displayMetrics.density
            val edgeScrollRange = (80 * density).toInt() // Active scroll zone near edges
            val maxScrollAmount = (15 * density).toInt() // Max scroll speed
            
            val itemLocation = IntArray(2)
            itemView.getLocationOnScreen(itemLocation)
            val itemCenterY = itemLocation[1] + (itemView.height / 2)

            val rvLocation = IntArray(2)
            recyclerView.getLocationOnScreen(rvLocation)
            val rvTop = rvLocation[1]
            val rvBottom = rvTop + recyclerView.height

            // Gravity-based scrolling: speed depends on how close you are to the extreme edge
            if (itemCenterY < rvTop + edgeScrollRange) {
                val proximity = (rvTop + edgeScrollRange - itemCenterY).toFloat() / edgeScrollRange
                val speed = (maxScrollAmount * proximity.coerceIn(0f, 1f)).toInt()
                recyclerView.scrollBy(0, -speed)
            } else if (itemCenterY > rvBottom - edgeScrollRange) {
                val proximity = (itemCenterY - (rvBottom - edgeScrollRange)).toFloat() / edgeScrollRange
                val speed = (maxScrollAmount * proximity.coerceIn(0f, 1f)).toInt()
                recyclerView.scrollBy(0, speed)
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun isLongPressDragEnabled(): Boolean = false
}
