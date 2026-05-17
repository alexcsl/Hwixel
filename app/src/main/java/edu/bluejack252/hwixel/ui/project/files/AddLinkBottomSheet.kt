package edu.bluejack252.hwixel.ui.project.files

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import edu.bluejack252.hwixel.R

class AddLinkBottomSheet : BottomSheetDialogFragment() {

    var onAdd: ((label: String, url: String, type: String, versionNotes: String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_add_link, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val labelInput = view.findViewById<TextInputEditText>(R.id.linkLabelEditText)
        val urlInput = view.findViewById<TextInputEditText>(R.id.linkUrlEditText)
        val typeGroup = view.findViewById<RadioGroup>(R.id.linkTypeRadioGroup)
        val notesInput = view.findViewById<TextInputEditText>(R.id.linkVersionNotesEditText)
        val saveButton = view.findViewById<MaterialButton>(R.id.saveLinkButton)
        val cancelButton = view.findViewById<MaterialButton>(R.id.cancelLinkButton)

        saveButton.setOnClickListener {
            val label = labelInput.text?.toString().orEmpty().trim()
            val url = urlInput.text?.toString().orEmpty().trim()
            val type = when (typeGroup.checkedRadioButtonId) {
                R.id.radioTypeDrive -> "drive"
                R.id.radioTypeGithub -> "github"
                else -> "other"
            }
            val notes = notesInput.text?.toString().orEmpty().trim()
            onAdd?.invoke(label, url, type, notes)
            dismiss()
        }

        cancelButton.setOnClickListener { dismiss() }
    }
}
