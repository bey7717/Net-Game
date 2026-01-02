package com.mobileapp.puzzlegame

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mobileapp.puzzlegame.databinding.FragmentCellSizeSettingsBinding

class CellSizeSettingsFragment : Fragment() {

    private var _binding: FragmentCellSizeSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCellSizeSettingsBinding.inflate(inflater, container, false)

        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        setupSizeOptions()
        setupCustomSettings()

        return binding.root
    }

    private fun setupSizeOptions() {
        val context = requireContext()
        val settingsManager = SettingsManager
        
        val sizeOptions = listOf(
            binding.size5x5 to Triple(5, 5, false),
            binding.size7x7 to Triple(7, 7, false),
            binding.size8x8 to Triple(8, 8, false),
            binding.size11x11 to Triple(11, 11, false),
            binding.size13x13 to Triple(13, 13, false),
            binding.size5x5Wrapping to Triple(5, 5, true),
            binding.size7x7Wrapping to Triple(7, 7, true),
            binding.size9x9Wrapping to Triple(9, 9, true),
            binding.size11x11Wrapping to Triple(11, 11, true),
            binding.size13x13Wrapping to Triple(13, 13, true)
        )

        sizeOptions.forEach { (view, sizeData) ->
            view.setOnClickListener {
                settingsManager.setGameWidth(context, sizeData.first)
                settingsManager.setGameHeight(context, sizeData.second)
                settingsManager.setWrapping(context, sizeData.third)

                val navDirections =
                    CellSizeSettingsFragmentDirections.actionCellSizeSettingsFragmentToGameFragment(
                        true
                    )
                findNavController().navigate(navDirections)
            }
        }

        binding.sizeCustom.setOnClickListener {
            binding.customSettingsOverlay.visibility = View.VISIBLE
        }
    }

    private fun setupCustomSettings() {
        binding.customSettingsOverlay.setOnClickListener {
            binding.customSettingsOverlay.visibility = View.GONE
        }

        binding.customSettingsCard.setOnClickListener {
            // Prevent clicks on card from closing overlay
        }

        binding.customOkButton.setOnClickListener {
            val context = requireContext()
            val settingsManager = SettingsManager
            
            val width = binding.widthInput.text.toString().toIntOrNull()?.coerceIn(3, 20) ?: 5
            val height = binding.heightInput.text.toString().toIntOrNull()?.coerceIn(3, 20) ?: 5
            val wrapping = binding.wallsWrapCheckbox.isChecked
            val unique = binding.uniqueSolutionCheckbox.isChecked

            settingsManager.setGameWidth(context, width)
            settingsManager.setGameHeight(context, height)
            settingsManager.setWrapping(context, wrapping)
            settingsManager.setUnique(context, unique)
            
            binding.customSettingsOverlay.visibility = View.GONE
            findNavController().navigateUp()
        }

        binding.customCancelButton.setOnClickListener {
            binding.customSettingsOverlay.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

