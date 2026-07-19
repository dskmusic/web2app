package com.dskmusic.web2app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PixabayAdapter(
    private val items: List<PixabayImage>,
    private val scope: CoroutineScope,
    private val onClick: (PixabayImage) -> Unit
) : RecyclerView.Adapter<PixabayAdapter.ViewHolder>() {

    class ViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pixabay_image, parent, false) as ImageView
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.imageView.setImageResource(R.drawable.ic_image_placeholder)
        scope.launch {
            val bitmap = BitmapUtils.download(item.previewURL)
            if (bitmap != null && holder.bindingAdapterPosition == position) {
                holder.imageView.setImageBitmap(bitmap)
            }
        }
        holder.imageView.setOnClickListener { onClick(item) }
    }
}
// ponytail: per-item download with position-check, no LruCache/Coil; add if scroll perf suffers.
