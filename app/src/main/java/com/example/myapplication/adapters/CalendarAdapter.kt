package com.example.myapplication.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import java.util.*

class CalendarAdapter(
    private var days: List<Calendar?>,
    private val onDateClick: (Calendar) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    private var selectedStartDate: Calendar? = null
    private var selectedEndDate: Calendar? = null

    fun setRange(start: Calendar?, end: Calendar?) {
        selectedStartDate = start
        selectedEndDate = end
        notifyDataSetChanged()
    }
    
    fun updateDays(newDays: List<Calendar?>) {
        this.days = newDays
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val date = days[position]
        holder.bind(date)
    }

    override fun getItemCount(): Int = days.size

    inner class CalendarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDay: TextView = view.findViewById(R.id.tv_day)
        private val viewSelectionCircle: View = view.findViewById(R.id.view_selection_circle)
        private val viewRangeBg: View = view.findViewById(R.id.view_range_bg)

        fun bind(date: Calendar?) {
            if (date == null) {
                tvDay.text = ""
                viewSelectionCircle.visibility = View.GONE
                viewRangeBg.visibility = View.GONE
                itemView.setOnClickListener(null)
                return
            }

            tvDay.text = date.get(Calendar.DAY_OF_MONTH).toString()
            
            val isStart = isSameDay(date, selectedStartDate)
            val isEnd = isSameDay(date, selectedEndDate)
            val isInRange = isInRange(date)

            when {
                isStart || isEnd -> {
                    viewSelectionCircle.visibility = View.VISIBLE
                    tvDay.setTextColor(Color.WHITE)
                    viewRangeBg.visibility = if (selectedStartDate != null && selectedEndDate != null) View.VISIBLE else View.GONE
                    
                    // Round the range background based on start/end
                    if (isStart && isEnd) {
                        viewRangeBg.visibility = View.GONE
                    }
                }
                isInRange -> {
                    viewSelectionCircle.visibility = View.GONE
                    tvDay.setTextColor(Color.BLACK)
                    viewRangeBg.visibility = View.VISIBLE
                }
                else -> {
                    viewSelectionCircle.visibility = View.GONE
                    tvDay.setTextColor(Color.BLACK)
                    viewRangeBg.visibility = View.GONE
                }
            }

            itemView.setOnClickListener { onDateClick(date) }
        }

        private fun isSameDay(cal1: Calendar, cal2: Calendar?): Boolean {
            if (cal2 == null) return false
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        private fun isInRange(date: Calendar): Boolean {
            if (selectedStartDate == null || selectedEndDate == null) return false
            return date.after(selectedStartDate) && date.before(selectedEndDate)
        }
    }
}
