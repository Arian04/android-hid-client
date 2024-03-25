package me.arianb.usb_hid_client.hid_utils;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

// TODO: if i feel like it later, I can change the maps to be more efficient, but if i do, then
// 		 I should probably add comments labeling each line with its human-readable key.
// 		 current: keycode/char -> human-readable key -> hid code
// 		 proposed: keycode/char -> hid code (comment: human-readable key)
public abstract class KeyCodeTranslation {
    public static final Map<Integer, String> keyEventModifierKeys;
    public static final Map<Integer, String> keyEventKeys;
    public static final Map<String, String> shiftChars;
    public static final Map<String, Byte> hidModifierCodes;
    public static final Map<String, Byte> hidKeyCodes;
    public static final Map<String, Byte> hidMediaKeyCodes;

    // Converts key to two scan codes
    // First element in array is the scan code for the modifier
    // Second element in array is the scan code for the key
    public static byte[] convertKeyToScanCodes(String key) {
        byte[] keyScanCodes = {0, 0};

        //Log.d(TAG, "converting following key into scan code: " + key);
        if (key == null) {
            Timber.d("convertKeyToScanCodes(): key is null");
            return null;
        }

        // If key is shift + another key, add left-shift scan code
        if (shiftChars.containsKey(key)) {
            keyScanCodes[0] += 0x02; // Add left-shift modifier
            key = shiftChars.get(key);
        } else if (key.length() == 1 && Character.isUpperCase(key.charAt(0))) {
            keyScanCodes[0] += 0x02; // Add left-shift modifier
            key = key.toLowerCase();
        }

        // Convert key to HID code
        Byte temp = hidKeyCodes.get(key);
        if (temp != null) {
            keyScanCodes[1] = temp;
        } else {
            Timber.e("key: '" + key + "' could not be converted to an HID code (it wasn't found in the map).");
            return null;
        }
        
        return keyScanCodes;
    }

    // Fill maps
    static {
        keyEventModifierKeys = new HashMap<>();
        keyEventKeys = new HashMap<>();
        shiftChars = new HashMap<>();
        hidModifierCodes = new HashMap<>();
        hidMediaKeyCodes = new HashMap<>();
        hidKeyCodes = new HashMap<>();

        // Translate modifier keycodes into key
        keyEventModifierKeys.put(113, "left-ctrl");
        keyEventModifierKeys.put(114, "right-ctrl");
        keyEventModifierKeys.put(59, "left-shift");
        keyEventModifierKeys.put(60, "right-shift");
        keyEventModifierKeys.put(57, "left-alt");
        keyEventModifierKeys.put(58, "right-alt");
        keyEventModifierKeys.put(117, "left-meta");
        keyEventModifierKeys.put(118, "right-meta");

        // Translate keycodes into key
        keyEventKeys.put(7, "0");
        keyEventKeys.put(8, "1");
        keyEventKeys.put(9, "2");
        keyEventKeys.put(10, "3");
        keyEventKeys.put(11, "4");
        keyEventKeys.put(12, "5");
        keyEventKeys.put(13, "6");
        keyEventKeys.put(14, "7");
        keyEventKeys.put(15, "8");
        keyEventKeys.put(16, "9");
        keyEventKeys.put(17, "*");
        keyEventKeys.put(18, "#");

        keyEventKeys.put(19, "up");
        keyEventKeys.put(20, "down");
        keyEventKeys.put(21, "left");
        keyEventKeys.put(22, "right");

        keyEventKeys.put(24, "volume-up");
        keyEventKeys.put(25, "volume-down");

        keyEventKeys.put(29, "a");
        keyEventKeys.put(30, "b");
        keyEventKeys.put(31, "c");
        keyEventKeys.put(32, "d");
        keyEventKeys.put(33, "e");
        keyEventKeys.put(34, "f");
        keyEventKeys.put(35, "g");
        keyEventKeys.put(36, "h");
        keyEventKeys.put(37, "i");
        keyEventKeys.put(38, "j");
        keyEventKeys.put(39, "k");
        keyEventKeys.put(40, "l");
        keyEventKeys.put(41, "m");
        keyEventKeys.put(42, "n");
        keyEventKeys.put(43, "o");
        keyEventKeys.put(44, "p");
        keyEventKeys.put(45, "q");
        keyEventKeys.put(46, "r");
        keyEventKeys.put(47, "s");
        keyEventKeys.put(48, "t");
        keyEventKeys.put(49, "u");
        keyEventKeys.put(50, "v");
        keyEventKeys.put(51, "w");
        keyEventKeys.put(52, "x");
        keyEventKeys.put(53, "y");
        keyEventKeys.put(54, "z");

        keyEventKeys.put(55, ",");
        keyEventKeys.put(56, ".");
        keyEventKeys.put(68, "`");
        keyEventKeys.put(69, "-");
        keyEventKeys.put(70, "=");
        keyEventKeys.put(71, "[");
        keyEventKeys.put(72, "]");
        keyEventKeys.put(73, "\\");
        keyEventKeys.put(74, ";");
        keyEventKeys.put(75, "'");
        keyEventKeys.put(76, "/");
        keyEventKeys.put(77, "@");

        keyEventKeys.put(81, "+");

        keyEventKeys.put(131, "f1");
        keyEventKeys.put(132, "f2");
        keyEventKeys.put(133, "f3");
        keyEventKeys.put(134, "f4");
        keyEventKeys.put(135, "f5");
        keyEventKeys.put(136, "f6");
        keyEventKeys.put(137, "f7");
        keyEventKeys.put(138, "f8");
        keyEventKeys.put(139, "f9");
        keyEventKeys.put(140, "f10");
        keyEventKeys.put(141, "f11");
        keyEventKeys.put(142, "f12");

        keyEventKeys.put(61, "tab");
        keyEventKeys.put(62, " ");
        keyEventKeys.put(66, "\n"); // enter
        keyEventKeys.put(67, "backspace");
        keyEventKeys.put(111, "escape");
        keyEventKeys.put(112, "delete");
        keyEventKeys.put(116, "scroll-lock");
        keyEventKeys.put(120, "print"); // and SysRq
        keyEventKeys.put(121, "pause");
        keyEventKeys.put(122, "home");
        keyEventKeys.put(123, "end");
        keyEventKeys.put(124, "insert");
        keyEventKeys.put(143, "num-lock");
        keyEventKeys.put(92, "page-up");
        keyEventKeys.put(93, "page-down");

        // Keys that are represented by another key + shift
        shiftChars.put("<", ",");
        shiftChars.put(">", ".");
        shiftChars.put("?", "/");
        shiftChars.put(":", ";");
        shiftChars.put("\"", "'");
        shiftChars.put("{", "[");
        shiftChars.put("}", "]");
        shiftChars.put("|", "\\");
        shiftChars.put("~", "`");
        shiftChars.put("!", "1");
        shiftChars.put("@", "2");
        shiftChars.put("#", "3");
        shiftChars.put("$", "4");
        shiftChars.put("%", "5");
        shiftChars.put("^", "6");
        shiftChars.put("&", "7");
        shiftChars.put("*", "8");
        shiftChars.put("(", "9");
        shiftChars.put(")", "0");
        shiftChars.put("_", "-");
        shiftChars.put("+", "=");

        // convert modifier to its HID scan code
        hidModifierCodes.put("no-modifier", (byte) 0);
        hidModifierCodes.put("left-ctrl", (byte) 0x01);
        hidModifierCodes.put("left-shift", (byte) 0x02);
        hidModifierCodes.put("left-alt", (byte) 0x04);
        hidModifierCodes.put("left-meta", (byte) 0x08);
        hidModifierCodes.put("right-ctrl", (byte) 0x10);
        hidModifierCodes.put("right-shift", (byte) 0x20);
        hidModifierCodes.put("right-alt", (byte) 0x40);
        hidModifierCodes.put("right-meta", (byte) 0x80);

        // convert media key name to its HID scan code
        hidMediaKeyCodes.put("next", (byte) 0xb5);
        hidMediaKeyCodes.put("previous", (byte) 0xb6);
        hidMediaKeyCodes.put("play-pause", (byte) 0xcd);
        hidMediaKeyCodes.put("volume-up", (byte) 0xe9);
        hidMediaKeyCodes.put("volume-down", (byte) 0xea);

        // convert character to its HID scan code
        hidKeyCodes.put("a", (byte) 0x04);
        hidKeyCodes.put("b", (byte) 0x05);
        hidKeyCodes.put("c", (byte) 0x06);
        hidKeyCodes.put("d", (byte) 0x07);
        hidKeyCodes.put("e", (byte) 0x08);
        hidKeyCodes.put("f", (byte) 0x09);
        hidKeyCodes.put("g", (byte) 0x0a);
        hidKeyCodes.put("h", (byte) 0x0b);
        hidKeyCodes.put("i", (byte) 0x0c);
        hidKeyCodes.put("j", (byte) 0x0d);
        hidKeyCodes.put("k", (byte) 0x0e);
        hidKeyCodes.put("l", (byte) 0x0f);
        hidKeyCodes.put("m", (byte) 0x10);
        hidKeyCodes.put("n", (byte) 0x11);
        hidKeyCodes.put("o", (byte) 0x12);
        hidKeyCodes.put("p", (byte) 0x13);
        hidKeyCodes.put("q", (byte) 0x14);
        hidKeyCodes.put("r", (byte) 0x15);
        hidKeyCodes.put("s", (byte) 0x16);
        hidKeyCodes.put("t", (byte) 0x17);
        hidKeyCodes.put("u", (byte) 0x18);
        hidKeyCodes.put("v", (byte) 0x19);
        hidKeyCodes.put("w", (byte) 0x1a);
        hidKeyCodes.put("x", (byte) 0x1b);
        hidKeyCodes.put("y", (byte) 0x1c);
        hidKeyCodes.put("z", (byte) 0x1d);

        hidKeyCodes.put("1", (byte) 0x1e);
        hidKeyCodes.put("2", (byte) 0x1f);
        hidKeyCodes.put("3", (byte) 0x20);
        hidKeyCodes.put("4", (byte) 0x21);
        hidKeyCodes.put("5", (byte) 0x22);
        hidKeyCodes.put("6", (byte) 0x23);
        hidKeyCodes.put("7", (byte) 0x24);
        hidKeyCodes.put("8", (byte) 0x25);
        hidKeyCodes.put("9", (byte) 0x26);
        hidKeyCodes.put("0", (byte) 0x27);

        hidKeyCodes.put("\n", (byte) 0x28); // line break -> enter
        hidKeyCodes.put("escape", (byte) 0x29);
        hidKeyCodes.put("backspace", (byte) 0x2a);
        hidKeyCodes.put("tab", (byte) 0x2b);
        hidKeyCodes.put(" ", (byte) 0x2c); // " " -> space
        hidKeyCodes.put("-", (byte) 0x2d);
        hidKeyCodes.put("=", (byte) 0x2e);
        hidKeyCodes.put("[", (byte) 0x2f);
        hidKeyCodes.put("]", (byte) 0x30);
        hidKeyCodes.put("\\", (byte) 0x31);
        hidKeyCodes.put("#", (byte) 0x32); // I think this is only for special layouts with a dedicated key
        hidKeyCodes.put(";", (byte) 0x33);
        hidKeyCodes.put("'", (byte) 0x34);
        hidKeyCodes.put("`", (byte) 0x35);
        hidKeyCodes.put(",", (byte) 0x36);
        hidKeyCodes.put(".", (byte) 0x37);
        hidKeyCodes.put("/", (byte) 0x38);
        hidKeyCodes.put("caps-lock", (byte) 0x39); // Haven't tested a keyboard with a caps-lock key

        hidKeyCodes.put("f1", (byte) 0x3a);
        hidKeyCodes.put("f2", (byte) 0x3b);
        hidKeyCodes.put("f3", (byte) 0x3c);
        hidKeyCodes.put("f4", (byte) 0x3d);
        hidKeyCodes.put("f5", (byte) 0x3e);
        hidKeyCodes.put("f6", (byte) 0x3f);
        hidKeyCodes.put("f7", (byte) 0x40);
        hidKeyCodes.put("f8", (byte) 0x41);
        hidKeyCodes.put("f9", (byte) 0x42);
        hidKeyCodes.put("f10", (byte) 0x43);
        hidKeyCodes.put("f11", (byte) 0x44);
        hidKeyCodes.put("f12", (byte) 0x45);

        hidKeyCodes.put("print", (byte) 0x46); // and SysRq
        hidKeyCodes.put("scroll-lock", (byte) 0x47);
        hidKeyCodes.put("pause", (byte) 0x48);
        hidKeyCodes.put("insert", (byte) 0x49);
        hidKeyCodes.put("home", (byte) 0x4a);
        hidKeyCodes.put("page-up", (byte) 0x4b);
        hidKeyCodes.put("delete", (byte) 0x4c);
        hidKeyCodes.put("end", (byte) 0x4d);
        hidKeyCodes.put("page-down", (byte) 0x4e);

        hidKeyCodes.put("right", (byte) 0x4f);
        hidKeyCodes.put("left", (byte) 0x50);
        hidKeyCodes.put("down", (byte) 0x51);
        hidKeyCodes.put("up", (byte) 0x52);
        hidKeyCodes.put("num-lock", (byte) 0x53);
    }
}
