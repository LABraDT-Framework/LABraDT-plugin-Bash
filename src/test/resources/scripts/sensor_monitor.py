#!/usr/bin/env python3
"""
Sensor Monitoring Script 
Monitors sensor values and generates alerts for threshold violations
"""

import os
import sys

def main():
    # Get environment variables
    temperature = float(os.environ.get('temperature', '0'))
    pressure = float(os.environ.get('pressure', '0'))
    rpm = float(os.environ.get('rpm', '0'))
    
    # Thresholds
    MAX_TEMP = 80
    MAX_PRESSURE = 10
    MAX_RPM = 3000
    
    violations = []
    
    # Check thresholds
    print(f"Temperature: {temperature}C", end="")
    if temperature > MAX_TEMP:
        print(f" - WARNING: {temperature}C exceeds limit {MAX_TEMP}C")
        violations.append(f"Temperature {temperature}C exceeds limit {MAX_TEMP}C")
    else:
        print(" - OK")
    
    print(f"Pressure: {pressure} bar", end="")
    if pressure > MAX_PRESSURE:
        print(f" - WARNING: {pressure} bar exceeds limit {MAX_PRESSURE} bar")
        violations.append(f"Pressure {pressure} bar exceeds limit {MAX_PRESSURE} bar")
    else:
        print(" - OK")
    
    print(f"RPM: {rpm}", end="")
    if rpm > MAX_RPM:
        print(f" - WARNING: {rpm} exceeds limit {MAX_RPM}")
        violations.append(f"RPM {rpm} exceeds limit {MAX_RPM}")
    else:
        print(" - OK")
    
    # Final status
    if violations:
        print(f"\nALERT: {len(violations)} threshold violations detected!")
        for violation in violations:
            print(f"  - {violation}")
        sys.exit(1)
    else:
        print("\nALL CLEAR - All sensors within normal range")
        sys.exit(0)

if __name__ == '__main__':
    main()
