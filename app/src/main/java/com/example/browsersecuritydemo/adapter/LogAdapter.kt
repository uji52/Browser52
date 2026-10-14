package com.example.browsersecuritydemo.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.browsersecuritydemo.databinding.ItemInterceptedLogBinding
import com.example.browsersecuritydemo.model.InterceptedData

class LogAdapter(
    private var items: List<InterceptedData> = emptyList()
) : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemInterceptedLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInterceptedLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvBadge.text = item.eventType
            when (item.eventType) {
                "INPUT" -> tvBadge.setBackgroundColor(Color.parseColor("#E53935")) // Red
                "SUBMIT" -> tvBadge.setBackgroundColor(Color.parseColor("#8E24AA")) // Purple
                "KEYPRESS" -> tvBadge.setBackgroundColor(Color.parseColor("#FB8C00")) // Orange
                "COOKIE" -> tvBadge.setBackgroundColor(Color.parseColor("#3949AB")) // Indigo
                "REQUEST" -> tvBadge.setBackgroundColor(Color.parseColor("#00897B")) // Teal
                else -> tvBadge.setBackgroundColor(Color.parseColor("#546E7A"))
            }

            tvSource.text = "[${item.source}]"
            tvTime.text = item.formattedTime
            tvFieldName.text = item.fieldName
            tvFieldType.text = "(${item.fieldType})"
            tvValue.text = item.value.ifEmpty { "(空)" }
            tvUrl.text = "URL: ${item.pageUrl}"
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<InterceptedData>) {
        items = newItems
        notifyDataSetChanged()
    }
}
