#!/usr/bin/env bash
set -euo pipefail

FLUTTER_HOME="/tmp/flutter"

if [ ! -x "$FLUTTER_HOME/bin/flutter" ]; then
  git clone https://github.com/flutter/flutter.git -b stable --depth 1 "$FLUTTER_HOME"
fi

export PATH="$FLUTTER_HOME/bin:$PATH"

flutter config --enable-web
flutter pub get
flutter build web --release \
  --dart-define=CAREOS_API_BASE_URL="${CAREOS_API_BASE_URL:-https://careos-api-v9k0.onrender.com/v1}" \
  --dart-define=CAREOS_ENVIRONMENT="${CAREOS_ENVIRONMENT:-staging}"
