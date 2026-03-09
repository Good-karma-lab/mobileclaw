#!/usr/bin/env bash
set -euo pipefail

echo 'scripts/install.sh was removed with the legacy installer flow.' >&2
echo 'Build and run Guappa from the mobile app workspace instead:' >&2
echo '  cd mobile-app' >&2
echo '  npm ci' >&2
echo '  npx expo prebuild --platform android' >&2
echo '  npm run android' >&2
exit 1
