package com.dskmusic.web2app

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.dskmusic.web2app.databinding.ItemSavedShortcutBinding

class SavedShortcutAdapter(
    private val items: MutableList<SavedShortcut>,
    private val onEdit: (SavedShortcut) -> Unit,
    private val onRepin: (SavedShortcut) -> Unit,
    private val onDelete: (SavedShortcut) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<SavedShortcutAdapter.ViewHolder>() {

    private var selectionMode = false
    private val selectedIds = mutableSetOf<String>()

    class ViewHolder(val binding: ItemSavedShortcutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSavedShortcutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvName.text = item.name
        holder.binding.tvUrl.text = item.url

        val context = holder.binding.root.context
        val iconFile = ShortcutStore.iconFile(context, item.id)
        if (iconFile.exists()) {
            val adaptive = BitmapFactory.decodeFile(iconFile.absolutePath)
            holder.binding.ivIcon.setImageBitmap(BitmapUtils.previewCrop(adaptive))
        } else {
            holder.binding.ivIcon.setImageResource(R.mipmap.ic_launcher)
        }

        val selected = item.id in selectedIds
        holder.binding.cbSelect.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.binding.cbSelect.isChecked = selected
        holder.binding.btnMore.visibility = if (selectionMode) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener {
            if (selectionMode) toggleSelection(item.id) else onEdit(item)
        }
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
                toggleSelection(item.id)
            }
            true
        }

        holder.binding.btnMore.setOnClickListener { anchor ->
            PopupMenu(context, anchor).apply {
                menuInflater.inflate(R.menu.menu_saved_shortcut_item, menu)
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_edit -> { onEdit(item); true }
                        R.id.action_repin -> { onRepin(item); true }
                        R.id.action_delete -> { onDelete(item); true }
                        else -> false
                    }
                }
            }.show()
        }
    }

    private fun toggleSelection(id: String) {
        if (!selectedIds.add(id)) selectedIds.remove(id)
        if (selectedIds.isEmpty()) selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun isInSelectionMode(): Boolean = selectionMode

    fun getSelectedIds(): List<String> = selectedIds.toList()

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun submitList(newItems: List<SavedShortcut>) {
        items.clear()
        items.addAll(newItems)
        selectedIds.retainAll(newItems.map { it.id }.toSet())
        if (selectedIds.isEmpty()) selectionMode = false
        notifyDataSetChanged()
    }
}
