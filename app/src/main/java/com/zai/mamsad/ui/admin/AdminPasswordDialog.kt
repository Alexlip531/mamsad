package com.zai.mamsad.ui.admin

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.zai.mamsad.R
import com.zai.mamsad.admin.AdminPrefs

/**
 * Password dialog shown when admin taps "version" 7 times.
 *
 * On success: marks session as unlocked in AdminPrefs and pops the dialog.
 * The host fragment is responsible for navigating to the admin screen via
 * its own observer — we use parentFragmentManager to find it and call back.
 *
 * Note: we pass a callback through the host fragment rather than a constructor
 * argument, so the dialog survives configuration changes (rotation).
 */
class AdminPasswordDialog : DialogFragment() {

    interface Host {
        fun onAdminUnlocked()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val inputLayout = TextInputLayout(ctx).apply {
            hint = ctx.getString(R.string.admin_password_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(56, 24, 56, 8)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        inputLayout.addView(editText)

        return AlertDialog.Builder(ctx)
            .setTitle(R.string.admin_password_title)
            .setMessage(R.string.admin_password_message)
            .setView(inputLayout)
            .setPositiveButton(R.string.admin_btn_unlock) { _, _ ->
                val candidate = editText.text?.toString().orEmpty()
                if (AdminPrefs.verify(ctx, candidate)) {
                    AdminPrefs.setUnlocked(ctx, true)
                    Toast.makeText(ctx, R.string.admin_unlocked, Toast.LENGTH_SHORT).show()
                    (parentFragment as? Host)?.onAdminUnlocked()
                } else {
                    Toast.makeText(ctx, R.string.admin_wrong_password, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .create()
    }

    companion object {
        const val TAG = "admin_pwd_dialog"
    }
}
