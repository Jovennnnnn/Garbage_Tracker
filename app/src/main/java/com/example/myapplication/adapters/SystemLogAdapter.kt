package com.example.myapplication.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.models.SystemLog
import java.text.SimpleDateFormat
import java.util.*

class SystemLogAdapter(private val logs: List<SystemLog>) : RecyclerView.Adapter<SystemLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val indicator: View = view.findViewById(R.id.view_indicator)
        val tvMessage: TextView = view.findViewById(R.id.tv_log_message)
        val tvTime: TextView = view.findViewById(R.id.tv_log_time)
        val divider: View = view.findViewById(R.id.view_divider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_system_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        holder.tvMessage.text = log.message
        
        val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
        holder.tvTime.text = sdf.format(Date(log.timestamp))

        val color = when (log.type) {
            "LOGIN" -> "#4CAF50" // Green
            "LOGOUT" -> "#2196F3" // Blue
            "EXPORT" -> "#9C27B0" // Purple
            "NEW_DRIVER" -> "#FF9800" // Orange
            else -> "#757575"
        }
        holder.indicator.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))
        
        holder.divider.visibility = if (position == logs.size - 1) View.GONE else View.VISIBLE
    }

    override fun getItemCount() = logs.size
}
