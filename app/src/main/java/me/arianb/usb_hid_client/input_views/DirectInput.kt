package me.arianb.usb_hid_client.input_views

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.databinding.DirectInputViewBinding
import timber.log.Timber

// TODO: move to DirectInputKeyboardView once I convert that file to Kotlin
@Composable
fun DirectInput(mainViewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val keySender = mainViewModel.keySender

    AndroidViewBinding(DirectInputViewBinding::inflate) {
        etDirectInput.setKeyListeners(keySender)
        etDirectInput.setOnClickListener {
            Timber.d("etDirectInput onClickListener triggered")

            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            etDirectInput.postDelayed({
                etDirectInput.requestFocus()
                imm.showSoftInput(etDirectInput, 0)
            }, 100)
        }
    }
}

@Composable
fun DirectInputIconButton() {
    val localView = LocalView.current

    IconButton(
        onClick = {
            val etDirectInput = localView.findViewById<DirectInputKeyboardView>(R.id.etDirectInput)
            etDirectInput.performClick()
        }
    ) {
        Icon(
            painter = painterResource(R.drawable.keyboard),
            contentDescription = stringResource(R.string.direct_input)
        )
    }
}
