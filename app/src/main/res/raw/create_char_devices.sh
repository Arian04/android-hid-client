#!/system/bin/sh

UDC="$(getprop sys.usb.controller)" # or `ls /sys/class/udc`

KB_PROTOCOL=1 # 1 for keyboard according to USB spec
KB_SUBCLASS=1 # Don't know what this is for tbh
KB_REPORT_LENGTH=4 # report length in bytes
KEYBOARD_REPORT_DESCRIPTOR='\x05\x01\x09\x06\xA1\x01\x85\x01\x75\x01\x95\x08\x05\x07\x19\xE0\x29\xE7\x15\x00\x25\x01\x81\x02\x75\x01\x95\x08\x81\x03\x95\x02\x75\x08\x15\x00\x25\xFF\x05\x07\x19\x00\x29\xFF\x81\x00\xC0\x05\x0C\x09\x01\xA1\x01\x85\x02\x75\x10\x95\x01\x26\xFF\x07\x19\x00\x2A\xFF\x07\x81\x00\xC0'

MOUSE_PROTOCOL=2 # 2 for mouse according to USB spec
MOUSE_SUBCLASS=0 # Don't know what this is for tbh
MOUSE_REPORT_LENGTH=3 # report length in bytes
MOUSE_REPORT_DESCRIPTOR='\x05\x01\x09\x02\xA1\x01\x09\x01\xA1\x00\x05\x09\x19\x01\x29\x03\x15\x00\x25\x01\x95\x03\x75\x01\x81\x02\x95\x01\x75\x05\x81\x03\x05\x01\x09\x30\x09\x31\x15\x81\x25\x7F\x75\x08\x95\x02\x81\x06\xC0\xC0'

# Get default Android USB gadget path
CONFIGFS_PATH="/config/usb_gadget"
USB_GADGET_PATH="${CONFIGFS_PATH}/g1"
# if g1 doesn't exist
if [ ! -d $USB_GADGET_PATH ]; then
  USB_GADGET_PATH="${CONFIGFS_PATH}/g2"
  echo "g1 doesn't exist, trying g2"

  # if g2 doesn't exist
  if [ ! -d $USB_GADGET_PATH ]; then
    echo "g2 doesn't exist???? never seen that before."
    for dir in "$CONFIGFS_PATH"/*; do
        [ -d "$dir" ] || continue
        [ -s "$dir"/UDC ] || continue # ensure non-empty UDC file
        USB_GADGET_PATH="${dir}"
    done
  fi
fi

echo "USB_GADGET_PATH = ${USB_GADGET_PATH}"

# Disable gadget (temporarily)
echo "" > "${USB_GADGET_PATH}/UDC"

# Other paths
CONFIGS_PATH="${USB_GADGET_PATH}/configs/b.1/"
KB_FUNCTION_PATH="${USB_GADGET_PATH}/functions/hid.keyboard"
MOUSE_FUNCTION_PATH="${USB_GADGET_PATH}/functions/hid.mouse"

mkdir -p $KB_FUNCTION_PATH
cd $KB_FUNCTION_PATH || exit 1
echo $KB_PROTOCOL > protocol
echo $KB_SUBCLASS > subclass
echo 1 > no_out_endpoint
echo $KB_REPORT_LENGTH > report_length

# shellcheck disable=SC2039
# I'll figure out how to switch this to use printf if it causes a bug, but it works perfectly fine so far.
# I also couldn't get printf working on my phone for the life of me.
echo -ne $KEYBOARD_REPORT_DESCRIPTOR > report_desc

mkdir -p $MOUSE_FUNCTION_PATH
cd $MOUSE_FUNCTION_PATH || exit 1
echo $MOUSE_PROTOCOL > protocol
echo $MOUSE_SUBCLASS > subclass
echo 1 > no_out_endpoint
echo $MOUSE_REPORT_LENGTH > report_length

# shellcheck disable=SC2039
# same reason as above
echo -ne $MOUSE_REPORT_DESCRIPTOR > report_desc

mkdir -p $CONFIGS_PATH
cd $CONFIGS_PATH || exit 1
ln -s $KB_FUNCTION_PATH ${CONFIGS_PATH}/hid.keyboard
ln -s $MOUSE_FUNCTION_PATH ${CONFIGS_PATH}/hid.mouse

# Disable and re-enable the gadget
echo "" > ${USB_GADGET_PATH}/UDC
echo "$UDC" > ${USB_GADGET_PATH}/UDC
