#!/system/bin/sh
# Starts the app with HWAddressSanitizer's runtime. Packed only for -PnativeSanitize=hwasan.
export LD_HWASAN=1
exec "$@"
