#!/system/bin/sh
# Run as root. Changes only this module's settings, component and exemption.
set -e
settings put global lichen_location_bridge_gms_enabled 0
settings delete global lichen_location_bridge_enabled
/data/adb/modules/zygisk_vector/cli modules disable io.github.lichen.locationbridge
pm disable --user 0 io.github.lichen.locationbridge/.NetworkLocationService
am force-stop --user 0 io.github.lichen.locationbridge
cmd deviceidle whitelist -io.github.lichen.locationbridge
echo "Bridge disabled. Reboot manually if the previous provider binding remains."
