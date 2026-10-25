#!/system/bin/sh
# Run as root. Original GMS requests, GPS and all application data are retained.
set -e
settings put global lichen_location_bridge_gms_enabled 0
echo "GMS supplemental listener disabled."
