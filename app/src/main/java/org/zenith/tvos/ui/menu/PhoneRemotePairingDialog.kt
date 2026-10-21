package org.zenith.tvos.ui.menu

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.TextView
import org.zenith.tvos.R

/**
 * On-screen TV modal presenting the local Wi-Fi remote URL for immediate phone pairing.
 */
class PhoneRemotePairingDialog(
    context: Context,
    private val remoteUrl: String
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(R.layout.dialog_phone_remote)

        val tvRemoteUrl = findViewById<TextView>(R.id.tvRemoteUrl)
        val btnDismiss = findViewById<Button>(R.id.btnDismissRemoteDialog)

        tvRemoteUrl.text = remoteUrl

        btnDismiss.setOnClickListener {
            dismiss()
        }

        btnDismiss.requestFocus()
    }
}
