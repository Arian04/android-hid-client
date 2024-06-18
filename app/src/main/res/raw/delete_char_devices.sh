#!/system/bin/sh

# NOTE:
# - `rm hid.keyboard` causes an immediate hard reboot on my Pixel 5 after updating to Android 14 (LOS 21)
# - Adding this bullet point at a later date, but I patched the use-after-free bug that was causing this kernel panic, so
# 	I guess I'll add this script, but just lock it behind a "dangerous" menu that warns users that some devices have problems
# 	with it? Or something like that.

CONFIGFS_PATH="/config/usb_gadget"
USB_GADGET_PATH="${CONFIGFS_PATH}/g1"

if [ ! -d $USB_GADGET_PATH ]; then
	echo "gadget at '$USB_GADGET_PATH' doesn't exist"
	exit 1
fi

echo "Removing functions from configurations"
for func in "$USB_GADGET_PATH"/configs/*.*/hid.keyboard; do
	[ -e "$func" ] && rm "$func"
done
for func in "$USB_GADGET_PATH"/configs/*.*/hid.mouse; do
	[ -e "$func" ] && rm "$func"
done

echo "Removing functions"
for func in hid.keyboard hid.mouse; do
  func_path="$USB_GADGET_PATH"/functions/$func
  [ -d "$func_path" ] && rmdir "$func_path"
done

# Disable and re-enable the gadget
echo "" > ${USB_GADGET_PATH}/UDC
echo "$UDC" > ${USB_GADGET_PATH}/UDC

# Delete character devices
rm /dev/hidg0
rm /dev/hidg1
