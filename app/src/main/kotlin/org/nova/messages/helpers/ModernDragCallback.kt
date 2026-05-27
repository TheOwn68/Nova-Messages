package org.nova.messages.helpers

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.nova.messages.adapters.BaseConversationsAdapter

class ModernDragCallback(private val adapter: BaseConversationsAdapter) : ItemTouchHelper.Callback() {

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val position = viewHolder.bindingAdapterPosition
        // Disable dragging if any items are selected (Action Mode active)
        val isSelectionActive = adapter.isSelectionModeActive()
        
        return if (position >= 2 && !isSelectionActive) {
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            makeMovementFlags(dragFlags, 0)
        } else {
            makeMovementFlags(0, 0)
        }
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from < 2 || to < 2) return false
        
        adapter.onItemMoved(from, to)
        return true
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.let { view ->
                view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
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
