package me.arianb.usb_hid_client;

import java.util.HashMap;
import java.util.Map;

public class KeyCodeTranslation {

	protected final Map<Integer, String> modifierKeys;
	protected final Map<Integer, String> keyEventCodes;
	protected final Map<String, String> shiftChars;
	protected final Map<String, String> hidKeyCodes;
	protected final Map<String, String> hidModifierCodes;

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
		hidModifierCodes.put("no-modifier", "0");
		hidModifierCodes.put("left-ctrl", "x01");
		hidModifierCodes.put("left-shift", "x02");
		hidModifierCodes.put("left-alt", "x04");
		hidModifierCodes.put("left-meta", "x08");
		hidModifierCodes.put("right-ctrl", "x10");
		hidModifierCodes.put("right-shift", "x20");
		hidModifierCodes.put("right-alt", "x40");
		hidModifierCodes.put("right-meta", "x80");

		// convert character to its HID scan code
		hidKeyCodes.put("a", "x04");
		hidKeyCodes.put("b", "x05");
		hidKeyCodes.put("c", "x06");
		hidKeyCodes.put("d", "x07");
		hidKeyCodes.put("e", "x08");
		hidKeyCodes.put("f", "x09");
		hidKeyCodes.put("g", "x0a");
		hidKeyCodes.put("h", "x0b");
		hidKeyCodes.put("i", "x0c");
		hidKeyCodes.put("j", "x0d");
		hidKeyCodes.put("k", "x0e");
		hidKeyCodes.put("l", "x0f");
		hidKeyCodes.put("m", "x10");
		hidKeyCodes.put("n", "x11");
		hidKeyCodes.put("o", "x12");
		hidKeyCodes.put("p", "x13");
		hidKeyCodes.put("q", "x14");
		hidKeyCodes.put("r", "x15");
		hidKeyCodes.put("s", "x16");
		hidKeyCodes.put("t", "x17");
		hidKeyCodes.put("u", "x18");
		hidKeyCodes.put("v", "x19");
		hidKeyCodes.put("w", "x1a");
		hidKeyCodes.put("x", "x1b");
		hidKeyCodes.put("y", "x1c");
		hidKeyCodes.put("z", "x1d");

		hidKeyCodes.put("1", "x1e");
		hidKeyCodes.put("2", "x1f");
		hidKeyCodes.put("3", "x20");
		hidKeyCodes.put("4", "x21");
		hidKeyCodes.put("5", "x22");
		hidKeyCodes.put("6", "x23");
		hidKeyCodes.put("7", "x24");
		hidKeyCodes.put("8", "x25");
		hidKeyCodes.put("9", "x26");
		hidKeyCodes.put("0", "x27");

		hidKeyCodes.put("\n", "x28"); // line break -> enter
		hidKeyCodes.put("escape", "x29");
		hidKeyCodes.put("backspace", "x2a");
		hidKeyCodes.put("tab", "x2b");
		hidKeyCodes.put(" ", "x2c"); // " " -> space
		hidKeyCodes.put("-", "x2d");
		hidKeyCodes.put("=", "x2e");
		hidKeyCodes.put("[", "x2f");
		hidKeyCodes.put("]", "x30");
		hidKeyCodes.put("\\", "x31");
		hidKeyCodes.put("#", "x32"); // I think this is only for non-US layouts
		hidKeyCodes.put(";", "x33");
		hidKeyCodes.put("'", "x34");
		hidKeyCodes.put("`", "x35");
		hidKeyCodes.put(",", "x36");
		hidKeyCodes.put(".", "x37");
		hidKeyCodes.put("/", "x38");
		hidKeyCodes.put("caps-lock", "x39"); // Haven't tested a keyboard with a caps-lock key

		hidKeyCodes.put("f1", "x3a");
		hidKeyCodes.put("f2", "x3b");
		hidKeyCodes.put("f3", "x3c");
		hidKeyCodes.put("f4", "x3d");
		hidKeyCodes.put("f5", "x3e");
		hidKeyCodes.put("f6", "x3f");
		hidKeyCodes.put("f7", "x40");
		hidKeyCodes.put("f8", "x41");
		hidKeyCodes.put("f9", "x42");
		hidKeyCodes.put("f10", "x43");
		hidKeyCodes.put("f11", "x44");
		hidKeyCodes.put("f12", "x45");

		hidKeyCodes.put("print", "x46"); // and SysRq
		hidKeyCodes.put("scroll-lock", "x47");
		hidKeyCodes.put("pause", "x48");
		hidKeyCodes.put("insert", "x49");
		hidKeyCodes.put("home", "x4a");
		hidKeyCodes.put("page-up", "x4b");
		hidKeyCodes.put("delete", "x4c");
		hidKeyCodes.put("end", "x4d");
		hidKeyCodes.put("page-down", "x4e");

		hidKeyCodes.put("right", "x4f");
		hidKeyCodes.put("left", "x50");
		hidKeyCodes.put("down", "x51");
		hidKeyCodes.put("up", "x52");
	}
}
