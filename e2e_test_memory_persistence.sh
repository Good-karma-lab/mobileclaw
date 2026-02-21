#!/bin/bash
set -e
EMULATOR="emulator-5554"

echo "=== Memory Persistence Test ==="
maestro test --device $EMULATOR mobile-app/.maestro/test_scenario_memory_persistence.yaml
echo "✅ Memory persistence test passed (agent recalled wife's name 'Maria')"
