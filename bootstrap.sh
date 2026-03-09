#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' 'bootstrap.sh was removed with the legacy installer flow.' >&2
printf '%s\n' 'Use the Android app workflow instead:' >&2
printf '%s\n' '  1. cd mobile-app' >&2
printf '%s\n' '  2. npm ci' >&2
printf '%s\n' '  3. npx expo prebuild --platform android' >&2
printf '%s\n' '  4. npm run android' >&2
exit 1
