package me.arianb.usb_hid_client.report_senders;

import static me.arianb.usb_hid_client.hid_utils.CharacterDevice.MOUSE_DEVICE_PATH;

import android.view.View;

import me.arianb.usb_hid_client.hid_utils.CharacterDevice;

public class MouseSender extends ReportSender {
    public final static byte MOUSE_BUTTON_LEFT = 0b001;
    public final static byte MOUSE_BUTTON_RIGHT = 0b010;
    public final static byte MOUSE_BUTTON_MIDDLE = 0b100;

    public MouseSender(CharacterDevice characterDevice, View parentLayout) {
        super(MOUSE_DEVICE_PATH, false, characterDevice, parentLayout);
    }

    public void click(byte button) {
        super.addReportWithLock(new byte[]{button, 0, 0});
    }

    public void move(byte x, byte y) {
        super.addReportWithLock(new byte[]{0, x, y});
    }
}