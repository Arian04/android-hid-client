package me.arianb.usb_hid_client.report_senders;

import static me.arianb.usb_hid_client.hid_utils.CharacterDevice.KEYBOARD_DEVICE_PATH;

import android.view.View;

public class KeySender extends ReportSender {
    // Report IDs
    private static final byte STANDARD_KEY = 0x01;
    private static final byte MEDIA_KEY = 0x02;

    public KeySender(View parentLayout) {
        super(KEYBOARD_DEVICE_PATH, true, parentLayout);
    }

    public void addStandardKey(byte modifier, byte key) {
        super.addReportWithLock(new byte[]{STANDARD_KEY, modifier, 0, key, 0});
    }

    public void addMediaKey(byte key) {
        super.addReportWithLock(new byte[]{MEDIA_KEY, key, 0});
    }
}