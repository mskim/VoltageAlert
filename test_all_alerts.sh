#!/bin/bash

ADB=~/Library/Android/sdk/platform-tools/adb
APP_PACKAGE="com.voltagealert"
MAIN_ACTIVITY=".ui.MainActivity"

echo "Testing all voltage alert buttons..."

# Function to test a voltage button
test_voltage() {
    local name=$1
    local tap_x=$2
    local tap_y=$3
    
    echo ""
    echo "Testing $name..."
    
    # Open app
    $ADB shell am start -n $APP_PACKAGE/$MAIN_ACTIVITY > /dev/null 2>&1
    sleep 2
    
    # Click voltage button
    echo "  - Clicking $name button..."
    $ADB shell input tap $tap_x $tap_y
    sleep 3
    
    # Check if AlertActivity is visible
    current=$($ADB shell dumpsys window | grep -i "mCurrentFocus" | grep -o "AlertActivity")
    
    if [ -n "$current" ]; then
        echo "  ✓ Alert appeared"
        
        # Click OK button (center-bottom)
        echo "  - Clicking OK button..."
        $ADB shell input tap 540 2190
        sleep 3
        
        # Check if back to MainActivity
        current=$($ADB shell dumpsys window | grep -i "mCurrentFocus" | grep -o "MainActivity")
        
        if [ -n "$current" ]; then
            echo "  ✓ OK button works - returned to main screen!"
        else
            echo "  ✗ FAILED - stuck on alert screen"
        fi
    else
        echo "  ✗ Alert did not appear"
    fi
}

# Test dangerous voltages (should show alerts)
test_voltage "380V" 539 970
test_voltage "154KV" 851 970
test_voltage "345KV" 306 1117
test_voltage "765KV" 773 1117

# Test safe voltage (should NOT show alert)
echo ""
echo "Testing 220V (should NOT show alert)..."
$ADB shell am start -n $APP_PACKAGE/$MAIN_ACTIVITY > /dev/null 2>&1
sleep 2
$ADB shell input tap 228 970
sleep 2
current=$($ADB shell dumpsys window | grep -i "mCurrentFocus" | grep -o "MainActivity")
if [ -n "$current" ]; then
    echo "  ✓ 220V correctly did NOT trigger alert (safe voltage)"
else
    echo "  ✗ FAILED - 220V should not trigger alert!"
fi

echo ""
echo "==================================="
echo "Test complete!"
echo "==================================="
