package com.stockvision.alert.domain;

/**
 * Cooldown and hysteresis state preventing repeated alert storms on consecutive evaluations.
 */
public enum AlertTriggerState {
    ARMED,
    TRIGGERED
}
