#!/system/bin/sh

# This will be recognized as a "Cherry GmbH Wireless Mouse & Keyboard"
# Source: http://www.linux-usb.org/usb.ids
vendor_id="0x046a"
product_id="0x002a"

manufacturer="Arian"
serial_number="101" # I don't know if the value of this is important in any way so I just chose a number
product="USB HID Client"
config_name="Configuration"
max_power=100 # in mA
protocol=1 # 1 for keyboard according to USB spec
subclass=1 # Don't know what this is for tbh
report_length=8 # report length in bytes
udc="$(getprop sys.usb.controller)"

# Paths
usb_gadget_path="/config/usb_gadget/keyboard"
configs_path="${usb_gadget_path}/configs/c.1/"
strings_path="${usb_gadget_path}/strings/0x409/"

mkdir -p "${usb_gadget_path}/functions/hid.keyboard"
cd "${usb_gadget_path}/functions/hid.keyboard" || exit 1
echo $protocol > protocol
echo $subclass > subclass
echo $report_length > report_length

# report descriptor
echo -ne \\x05\\x01\\x09\\x06\\xa1\\x01\\x05\\x07\\x19\\xe0\\x29\\xe7\\x15\\x00\\x25\\x01\\x75\\x01\\x95\\x08\\x81\\x02\\x95\\x01\\x75\\x08\\x81\\x03\\x95\\x05\\x75\\x01\\x05\\x08\\x19\\x01\\x29\\x05\\x91\\x02\\x95\\x01\\x75\\x03\\x91\\x03\\x95\\x06\\x75\\x08\\x15\\x00\\x25\\x65\\x05\\x07\\x19\\x00\\x29\\x65\\x81\\x00\\xc0 > report_desc

cd $usb_gadget_path || exit 1
echo $vendor_id > idVendor
echo $product_id > idProduct

mkdir $strings_path
cd $strings_path || exit 1
echo $manufacturer > manufacturer
echo "$product" > product
echo $serial_number > serialnumber

mkdir $configs_path
cd $configs_path || exit 1
echo $max_power > MaxPower

mkdir ${configs_path}/strings/0x409
echo $config_name > strings/0x409/configuration

ln -s ${usb_gadget_path}/functions/hid.keyboard ${configs_path}/hid.keyboard

# Disable all gadgets
find /config/usb_gadget/  -name UDC -type f -exec sh -c 'echo "" >  "$@"' _ {} \;

# Enable this gadget
echo "$udc" > ${usb_gadget_path}/UDC