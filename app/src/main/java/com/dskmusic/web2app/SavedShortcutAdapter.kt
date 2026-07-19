package com.dskmusic.web2app

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.dskmusic.web2app.databinding.ItemSavedShortcutBinding

class SavedShortcutAdapter(
    private val items: MutableList<SavedShortcut>,
    private val onEdit: (SavedShortcut) -> Unit,
    private val onRepin: (SavedShortcut) -> Unit,
    private val onDelete: (SavedShortcut) -> Unit
) : RecyclerView.Adapter<SavedShortcutAdapter.ViewHolder>() {

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

    fun submitList(newItems: List<SavedShortcut>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
