package com.mobileapp.puzzlegame

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mobileapp.puzzlegame.databinding.FragmentGameHistoryBinding
import com.mobileapp.puzzlegame.databinding.ItemGameHistoryBinding

data class GameHistoryItem(
    val size: String,
    val moves: Int,
    val timeSeconds: Long
)

class GameHistoryAdapter(private val items: List<GameHistoryItem>) :
    RecyclerView.Adapter<GameHistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemGameHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGameHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.binding.sizeText.text = item.size
        holder.binding.movesText.text = context.getString(R.string.history_moves_format, item.moves)
        
        val minutes = item.timeSeconds / 60
        val seconds = item.timeSeconds % 60
        holder.binding.timeText.text = context.getString(R.string.history_time_format, minutes, seconds)
    }

    override fun getItemCount() = items.size
}

class GameHistoryFragment : Fragment() {

    private var _binding: FragmentGameHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameHistoryBinding.inflate(inflater, container, false)

        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        // Load history from GameHistoryManager
        val historyItems = GameHistoryManager.getHistory(requireContext())

        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecyclerView.adapter = GameHistoryAdapter(historyItems)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

