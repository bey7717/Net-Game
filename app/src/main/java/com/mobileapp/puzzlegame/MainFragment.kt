package com.mobileapp.puzzlegame

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mobileapp.puzzlegame.databinding.FragmentMainBinding

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)

        setupButtons()
        updateResumeVisibility()

        return binding.root
    }

    private fun setupButtons() {
        binding.newGameButton.setOnClickListener {
            val navDirections = MainFragmentDirections.actionMainFragmentToGameFragment(true)
            findNavController().navigate(navDirections)
        }

        binding.resumeButton.setOnClickListener {
            val navDirections = MainFragmentDirections.actionMainFragmentToGameFragment(false)
            findNavController().navigate(navDirections)
        }

        binding.settingsImageButton.setOnClickListener {
            findNavController().navigate(R.id.action_mainFragment_to_sampleFragment)
        }

        binding.placeholderImageButton.setOnClickListener {
            findNavController().navigate(R.id.action_mainFragment_to_gameHistoryFragment)
        }

        binding.infoImageButton.setOnClickListener {
            binding.infoOverlay.visibility = View.VISIBLE
        }

        binding.closeInfoButton.setOnClickListener {
            binding.infoOverlay.visibility = View.GONE
        }

        // Dismiss overlay when tapping outside the card
        binding.infoOverlay.setOnClickListener {
            binding.infoOverlay.visibility = View.GONE
        }
    }

    private fun updateResumeVisibility() {
        val hasGame = SettingsManager.getSavedGameString(requireContext()) != null
        binding.resumeButton.visibility = if (hasGame) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


