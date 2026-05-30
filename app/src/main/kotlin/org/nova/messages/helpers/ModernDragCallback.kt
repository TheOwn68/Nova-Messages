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
            val scrollThreshold = (60 * density).toInt() // 60dp from top/bottom
            val scrollAmount = (12 * density).toInt()    // 12dp to scroll per frame
            
            // Raw screen Y coordinates for absolute precision
            val itemLocation = IntArray(2)
            itemView.getLocationOnScreen(itemLocation)
            val itemTop = itemLocation[1]
            val itemBottom = itemTop + itemView.height

            val rvLocation = IntArray(2)
            recyclerView.getLocationOnScreen(rvLocation)
            val rvTop = rvLocation[1]
            val rvBottom = rvTop + recyclerView.height
            
            if (itemTop < rvTop + scrollThreshold) {
                recyclerView.scrollBy(0, -scrollAmount)
            } else if (itemBottom > rvBottom - scrollThreshold) {
                recyclerView.scrollBy(0, scrollAmount)
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
