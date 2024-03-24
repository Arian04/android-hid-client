#!/system/bin/sh

# This will be recognized as a "Linux Foundation Multifunction Composite Gadget"
# Source: http://www.linux-usb.org/usb.ids
VENDOR_ID="0x1d6b"
PRODUCT_ID="0x0104"

MANUFACTURER="Arian"
SERIAL_NUMBER="101" # I don't know if the value of this is important in any way so I just chose a number
PRODUCT="USB HID Client"
CONFIG_NAME="Configuration"
MAX_POWER=100 # in mA
UDC="$(getprop sys.usb.controller)" # or `ls /sys/class/udc`

KB_PROTOCOL=1 # 1 for keyboard according to USB spec
KB_SUBCLASS=1 # Don't know what this is for tbh
KB_REPORT_LENGTH=4 # report length in bytes
KEYBOARD_REPORT_DESCRIPTOR='\x05\x01\x09\x06\xA1\x01\x85\x01\x75\x01\x95\x08\x05\x07\x19\xE0\x29\xE7\x15\x00\x25\x01\x81\x02\x75\x01\x95\x08\x81\x03\x95\x02\x75\x08\x15\x00\x25\xFF\x05\x07\x19\x00\x29\xFF\x81\x00\xC0\x05\x0C\x09\x01\xA1\x01\x85\x02\x75\x10\x95\x01\x26\xFF\x07\x19\x00\x2A\xFF\x07\x81\x00\xC0'

# Paths
USB_GADGET_PATH="/config/usb_gadget/keyboard"
CONFIGS_PATH="${USB_GADGET_PATH}/configs/c.1/"
STRINGS_PATH="${USB_GADGET_PATH}/strings/0x409/"
KB_FUNCTION_PATH="${USB_GADGET_PATH}/functions/hid.keyboard"

mkdir -p $KB_FUNCTION_PATH
cd $KB_FUNCTION_PATH || exit 1
echo $KB_PROTOCOL > protocol
echo $KB_SUBCLASS > subclass
echo $KB_REPORT_LENGTH > report_length

# shellcheck disable=SC2039
# I'll figure out how to switch this to use printf if it causes a bug, but it works perfectly fine so far.
# I also couldn't get printf working on my phone for the life of me.
echo -ne $KEYBOARD_REPORT_DESCRIPTOR > report_desc

cd $USB_GADGET_PATH || exit 1
echo $VENDOR_ID > idVendor
echo $PRODUCT_ID > idProduct

mkdir $STRINGS_PATH
cd $STRINGS_PATH || exit 1
echo $MANUFACTURER > manufacturer
echo "$PRODUCT" > product
echo $SERIAL_NUMBER > serialnumber

mkdir $CONFIGS_PATH
cd $CONFIGS_PATH || exit 1
echo $MAX_POWER > MaxPower

mkdir ${CONFIGS_PATH}/strings/0x409
echo $CONFIG_NAME > strings/0x409/configuration

ln -s $KB_FUNCTION_PATH ${CONFIGS_PATH}/hid.keyboard

# Disable all gadgets
find /config/usb_gadget/ -name UDC -type f -exec sh -c 'echo "" >  "$@"' _ {} \;

# Enable this gadget
echo "$UDC" > ${USB_GADGET_PATH}/UDC