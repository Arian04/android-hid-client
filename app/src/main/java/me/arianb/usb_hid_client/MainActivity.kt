package me.arianb.usb_hid_client

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.arianb.usb_hid_client.hid_utils.CharacterDevice
import me.arianb.usb_hid_client.hid_utils.CharacterDevice.Companion.anyCharacterDeviceMissing
import me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation
import me.arianb.usb_hid_client.input_views.DirectInputKeyboardView
import me.arianb.usb_hid_client.input_views.TouchpadView
import me.arianb.usb_hid_client.report_senders.KeySender
import me.arianb.usb_hid_client.report_senders.MouseSender
import me.arianb.usb_hid_client.shell_utils.NoRootPermissionsException
import timber.log.Timber
import timber.log.Timber.DebugTree

// TODO: move all misc strings used in snackbars and alerts throughout the app into strings.xml for translation purposes.
// Notes on terminology:
// 		A key that has been pressed in conjunction with the shift key (ex: @ = 2 + shift, $ = 4 + shift, } = ] + shift)
// 		will be referred to as a "shifted" key. In the previous example, 2, 4, and ] would be considered
// 		the "un-shifted" keys.
class MainActivity : AppCompatActivity() {
    private var etDirectInput: DirectInputKeyboardView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getPreferences(MODE_PRIVATE)

        val characterDevice = CharacterDevice(applicationContext, Dispatchers.IO)

        if (BuildConfig.DEBUG || preferences.getBoolean("debug_mode", false)) {
            Timber.plant(DebugTree())
            Shell.enableVerboseLogging = true
        }
        setContentView(R.layout.activity_main)
        val parentLayout = findViewById<View>(android.R.id.content)

        // If this is the first time the app has been opened, then show OnboardingActivity
        val onboardingDone = preferences.getBoolean("onboarding_done", false)
        if (!onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        // Start threads to send key and mouse events
        val keySender = KeySender(characterDevice, parentLayout)
        Thread(keySender).start()
        val mouseSender = MouseSender(characterDevice, parentLayout)
        Thread(mouseSender).start()

        // Set up input Views
        val btnSubmit = findViewById<Button>(R.id.btnKeyboard)
        val etManualInput = findViewById<EditText>(R.id.etManualInput)
        etDirectInput = findViewById(R.id.etDirectInput) // can't be a local var because menu item needs access to it
        val touchpad = findViewById<TouchpadView>(R.id.tvTouchpad)
        setupManualKeyboardInput(etManualInput, btnSubmit, keySender, parentLayout)
        setupDirectKeyboardInput(etDirectInput, keySender)
        touchpad.setTouchListeners(mouseSender)

        // TODO: if this fails here, I need to make it incredibly clear that the app will not work.
        //       right now, you can still try to use it and it'll fail. It should just "lock" the inputs
        //       if this fails I think.
        if (anyCharacterDeviceMissing()) {
            showCreateCharDevicesPrompt(characterDevice, parentLayout)
        }
    }

    private fun setupManualKeyboardInput(etManualInput: EditText, btnSubmit: Button, keySender: KeySender, parentLayout: View) {
        // Button sends text in "Manual Input" EditText
        btnSubmit.setOnClickListener {
            // Save text to send
            val sendStr = etManualInput.getText().toString()

            // If empty, don't do anything
            if (sendStr.isEmpty()) {
                return@setOnClickListener
            }

            // Clear EditText if the user's preference is to clear it
            if (getPreferences(MODE_PRIVATE).getBoolean("clear_manual_input", false)) {
                runOnUiThread { etManualInput.setText("") }
            }

            // Sends all keys
            for (char in sendStr) {
                // Converts (String) key to (byte) key scan code and (byte) modifier scan code and add to queue
                val tempScanCodes = KeyCodeTranslation.convertKeyToScanCodes(char)
                if (tempScanCodes == null) {
                    val error = "key: '$char' is not supported."
                    Timber.e(error)
                    Snackbar.make(parentLayout, error, Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val modifierScanCode = tempScanCodes[0]
                val keyScanCode = tempScanCodes[1]
                keySender.addStandardKey(modifierScanCode, keyScanCode)
            }
        }
    }

    private fun setupDirectKeyboardInput(etDirectInput: DirectInputKeyboardView?, keySender: KeySender) {
        etDirectInput!!.setKeyListeners(keySender)
    }

    // Inflate the options menu when the user opens it for the first time
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    // Run code on menu item selected
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.menuDirectInput -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                etDirectInput!!.postDelayed({
                    etDirectInput!!.requestFocus()
                    imm.showSoftInput(etDirectInput, 0)
                }, 100)
            }

            R.id.menuSettings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }

            R.id.menuHelp -> {
                val intent = Intent(this, HelpActivity::class.java)
                startActivity(intent)
            }

            R.id.menuInfo -> {
                val intent = Intent(this, InfoActivity::class.java)
                startActivity(intent)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showCreateCharDevicesPrompt(characterDevice: CharacterDevice, parentLayout: View) {
        val defaultPromptActionPref = getPreferences(MODE_PRIVATE).getString("issue_prompt_action", "Ask Every Time")

        // Warns user if character device doesn't exist and shows a button to fix it
        if (defaultPromptActionPref == "Fix") {
            lifecycleScope.launch {
                try {
                    if (!characterDevice.createCharacterDevices()) {
                        Snackbar.make(parentLayout, "ERROR: Failed to create character device.", Snackbar.LENGTH_INDEFINITE).show()
                    }
                } catch (e: NoRootPermissionsException) {
                    Timber.e("Failed to create character device, missing root permissions")
                    Snackbar.make(parentLayout, "ERROR: Missing root permissions.", Snackbar.LENGTH_INDEFINITE).show()
                }
            }
        } else { // If pref isn't "fix" then it's "ask every time", so ask
            getCreateCharDevicesAlert(characterDevice, parentLayout).show()
        }
    }

    private fun getCreateCharDevicesAlert(characterDevice: CharacterDevice, parentLayout: View): AlertDialog.Builder {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Character device(s) do not exist")
        builder.setMessage("Add HID functions to the default USB gadget? This must be re-done after every reboot.\n\n**The app will not work if you decline**")
        builder.setPositiveButton("YES") { _: DialogInterface?, _: Int ->
            lifecycleScope.launch {
                try {
                    if (!characterDevice.createCharacterDevices()) {
                        Snackbar.make(parentLayout, "ERROR: Failed to create character device.", Snackbar.LENGTH_INDEFINITE).show()
                    }
                } catch (e: NoRootPermissionsException) {
                    Timber.e("Failed to create character device, missing root permissions")
                    Snackbar.make(parentLayout, "ERROR: Missing root permissions.", Snackbar.LENGTH_INDEFINITE).show()
                }
            }
        }
        builder.setNegativeButton("NO") { _: DialogInterface?, _: Int ->
            finish() // Exit app
        }

        // The response to this dialog is critical for the app to function, so don't let user skip it.
        builder.setCancelable(false)
        return builder
    }
}