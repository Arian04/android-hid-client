package me.arianb.usb_hid_client;

import java.util.HashMap;
import java.util.Map;

public class KeyCodeTranslation {

	protected final Map<Integer, String> modifierKeys;
	protected final Map<Integer, String> keyEventCodes;
	protected final Map<String, String> shiftChars;
	protected final Map<String, Byte> hidKeyCodes;
	protected final Map<String, Byte> hidModifierCodes;

	public KeyCodeTranslation() {
		// TODO: if i feel like it later, I can change the maps to be more efficient, but if i do, then
		// 		 I should probably add comments labeling each line with its human-readable key
		// 		 current: keycode/char -> human-readable key -> hid code
		// 		 proposed: keycode/char -> hid code

		modifierKeys = new HashMap<>();
		keyEventCodes = new HashMap<>();
		shiftChars = new HashMap<>();
		hidKeyCodes = new HashMap<>();
		hidModifierCodes = new HashMap<>();

		// Translate modifier keycodes into key
		modifierKeys.put(113, "left-ctrl");
		modifierKeys.put(114, "right-ctrl");
		modifierKeys.put(59, "left-shift");
		modifierKeys.put(60, "right-shift");
		modifierKeys.put(57, "left-alt");
		modifierKeys.put(58, "right-alt");
		modifierKeys.put(117, "left-meta");
		modifierKeys.put(118, "right-meta");

		// Translate keycodes into key
		keyEventCodes.put(29, "a");
		keyEventCodes.put(30, "b");
		keyEventCodes.put(31, "c");
		keyEventCodes.put(32, "d");
		keyEventCodes.put(33, "e");
		keyEventCodes.put(34, "f");
		keyEventCodes.put(35, "g");
		keyEventCodes.put(36, "h");
		keyEventCodes.put(37, "i");
		keyEventCodes.put(38, "j");
		keyEventCodes.put(39, "k");
		keyEventCodes.put(40, "l");
		keyEventCodes.put(41, "m");
		keyEventCodes.put(42, "n");
		keyEventCodes.put(43, "o");
		keyEventCodes.put(44, "p");
		keyEventCodes.put(45, "q");
		keyEventCodes.put(46, "r");
		keyEventCodes.put(47, "s");
		keyEventCodes.put(48, "t");
		keyEventCodes.put(49, "u");
		keyEventCodes.put(50, "v");
		keyEventCodes.put(51, "w");
		keyEventCodes.put(52, "x");
		keyEventCodes.put(53, "y");
		keyEventCodes.put(54, "z");
		keyEventCodes.put(131, "f1");
		keyEventCodes.put(132, "f2");
		keyEventCodes.put(133, "f3");
		keyEventCodes.put(134, "f4");
		keyEventCodes.put(135, "f5");
		keyEventCodes.put(136, "f6");
		keyEventCodes.put(137, "f7");
		keyEventCodes.put(138, "f8");
		keyEventCodes.put(139, "f9");
		keyEventCodes.put(140, "f10");
		keyEventCodes.put(141, "f11");
		keyEventCodes.put(142, "f12");
		keyEventCodes.put(19, "up");
		keyEventCodes.put(20, "down");
		keyEventCodes.put(21, "left");
		keyEventCodes.put(22, "right");
		keyEventCodes.put(61, "tab");
		keyEventCodes.put(67, "backspace");
		keyEventCodes.put(111, "escape");
		keyEventCodes.put(120, "print"); // and SysRq
		keyEventCodes.put(116, "scroll-lock");
		keyEventCodes.put(143, "num-lock");
		keyEventCodes.put(121, "pause");
		keyEventCodes.put(124, "insert");
		keyEventCodes.put(112, "delete");

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
		hidKeyCodes.put("#", (byte) 0x32); // I think this is only for non-US layouts
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
	}
}
