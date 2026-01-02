package com.mobileapp.puzzlegame

import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.graphics.toColorInt
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mobileapp.puzzlegame.databinding.FragmentSampleBinding

val COLOR_NAMES = arrayOf(
    "Blue",
    "Red",
    "Green",
    "Yellow",
    "Magenta",
    "Cyan",
    "Orange",
    "Purple",
    "Pink"
)

class SampleFragment : Fragment() {
    private var _binding: FragmentSampleBinding? = null
    private val binding get() = _binding!!

    private val sampleBoard: MutableList2D<Cell>

    init {
        // create the sample board for previewing the settings
        val iter = listOf(
            Links(false, false, false, true),
            Links(false, false, false, true),
            Links(false, false, false, true),
            Links(false, true, false, true),
            Links(true, true, false, true),
            Links(false, true, true, false),
            Links(true, true, false, false),
            Links(true, true, true, false),
            Links(false, false, true, false),
        ).map {
            Cell(it, false, true)
        }.iterator()

        sampleBoard = MutableList2D(3, 3) {
            iter.next()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment, with bindings
        _binding = FragmentSampleBinding.inflate(inflater, container, false)

        val context = requireContext()

        // make the back button work
        binding.backButton.setOnClickListener { findNavController().navigateUp() }

        // set the values on the sliders and radio buttons, loaded from settings
        binding.curvinessSlider.value = SettingsManager.getCurveGap(context)
        binding.outerThicknessSlider.value = SettingsManager.getThick(context)
        binding.innerThicknessSlider.value = SettingsManager.getThickFill(context)

        //
        val tileCurveProvider = TileCurveProvider(SettingsManager.getCurveGap(context))
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = "#000000".toColorInt()
            strokeWidth = SettingsManager.getThick(context)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = SettingsManager.getColor(context)
            strokeWidth = outlinePaint.strokeWidth * SettingsManager.getThickFill(context)
        }
        val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#C00000".toColorInt()
            strokeWidth = fillPaint.strokeWidth
        }

        binding.sampleBoard.background = GameBoardDrawable(
            3,
            3,
            sampleBoard,
            HashMap(),
            tileCurveProvider,
            outlinePaint,
            fillPaint,
            errorPaint
        )

        fun updateSettings() {
            val curveGap = binding.curvinessSlider.value
            val thick = binding.outerThicknessSlider.value
            val thickFill = binding.innerThicknessSlider.value

            SettingsManager.setCurveGap(context, curveGap)
            SettingsManager.setThick(context, thick)
            SettingsManager.setThickFill(context, thickFill)

            tileCurveProvider.curveGap = curveGap
            outlinePaint.strokeWidth = thick
            fillPaint.strokeWidth = thick * thickFill
            errorPaint.strokeWidth = fillPaint.strokeWidth

            fillPaint.color = SettingsManager.getColor(context)

            binding.sampleBoard.invalidate()
        }


        val colorRadioButtons = binding.colorRadioGrid.children.filterIsInstance<RadioButton>()
        val selectedColorIndex = SettingsManager.getColorIndex(context)

        colorRadioButtons.forEachIndexed { index, radioButton ->
            val color = SettingsManager.COLORS[index].toColorInt()

            radioButton.text = COLOR_NAMES[index]
            radioButton.buttonTintList = ColorStateList.valueOf(color)
            radioButton.isChecked = index == selectedColorIndex

            radioButton.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // save the new setting
                    SettingsManager.setColorIndex(context, index)

                    // update
                    updateSettings()

                    // uncheck any other radio buttons
                    colorRadioButtons.forEachIndexed { otherIndex, otherButton ->
                        if (otherIndex != index) {
                            otherButton.isChecked = false
                        }
                    }
                }
            }
        }

        binding.curvinessSlider.addOnChangeListener { _, _, _ ->
            updateSettings()
        }

        binding.outerThicknessSlider.addOnChangeListener { _, _, _ ->
            updateSettings()
        }

        binding.innerThicknessSlider.addOnChangeListener { _, _, _ ->
            updateSettings()
        }

        updateSettings()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}