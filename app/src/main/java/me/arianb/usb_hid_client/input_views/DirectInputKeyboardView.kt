package me.arianb.usb_hid_client.input_views

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.databinding.DirectInputViewBinding
import me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation
import me.arianb.usb_hid_client.report_senders.KeySender
import me.arianb.usb_hid_client.settings.SettingsViewModel
import timber.log.Timber

class DirectInputKeyboardView : AppCompatEditText {
    private lateinit var keySender: KeySender
    private lateinit var myInputConnection: MyInputConnection

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        Timber.i("onCreateInputConnection() called")
        if (keySender == null) {
            Timber.wtf("KEY SENDER IS NULL, SOMETHING IS TERRIBLY WRONG")
        }
        myInputConnection = MyInputConnection(keySender, this, false)
        return myInputConnection
    }

    fun sendKeyEvent(event: KeyEvent): Boolean {
        return myInputConnection?.sendKeyEvent(event) ?: false
    }

    fun setKeySender(keySender: KeySender) {
        this.keySender = keySender
    }
}

@Composable
fun DirectInput(
    mainViewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val keySender by mainViewModel.keySender.collectAsState()
    val userPreferencesState by settingsViewModel.userPreferencesFlow.collectAsState()

    AndroidViewBinding(
        factory = DirectInputViewBinding::inflate,
        update = {
            // Forces the input method to run in a limited mode that generates raw `KeyEvent`s, which
            // allows me to handle the keys easier.
            // Source: https://developer.android.com/reference/android/text/InputType#TYPE_NULL
            etDirectInput.inputType = InputType.TYPE_NULL

            etDirectInput.setKeySender(keySender)

            // For some reason, the input connection doesn't receive media keys, but this listener *does*, so
            // I'm listening for them here and just passing them through.
            //
            // As another note, this key listener seems to be triggered all the time when the key is sent from a
            // hardware keyboard (during admittedly limited testing).
            etDirectInput.setOnKeyListener { _, keyCode, event ->
                Timber.d("OnKeyListener received KeyEvent: %s", event.toString())
                // If key is a media key and user doesn't want us to pass it through, then just
                // ignore it and let the system handle it normally. Otherwise, send it.
                // TODO: rename this preference to "media key passthrough" or something similar since that's more accurate
                if (KeyCodeTranslation.isMediaKey(keyCode)) {
                    if (!userPreferencesState.isVolumeButtonPassthroughEnabled) {
                        return@setOnKeyListener false
                    }
                }

                return@setOnKeyListener etDirectInput.sendKeyEvent(event)
            }
        }
    )
}

@Composable
fun DirectInputIconButton() {
    val localView = LocalView.current
    val context = LocalContext.current

    IconButton(
        onClick = {
            val etDirectInput = localView.findViewById<DirectInputKeyboardView>(R.id.etDirectInput)
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            etDirectInput.requestFocus()
            imm.showSoftInput(etDirectInput, 0)
        }
    ) {
        Icon(
            painter = painterResource(R.drawable.keyboard),
            contentDescription = stringResource(R.string.direct_input)
        )
    }
}
