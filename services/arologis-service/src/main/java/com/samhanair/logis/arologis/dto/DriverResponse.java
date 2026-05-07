package com.samhanair.logis.arologis.dto;

import com.samhanair.logis.arologis.domain.Driver;
import com.samhanair.logis.arologis.domain.DriverSource;

/**
 * Driver 응답 DTO — UUID 비공개 가드 (driverCode + phoneNumber + source 만 노출).
 */
public record DriverResponse(
        String driverCode,
        String phoneNumber,
        String vehicleType,
        DriverSource source,
        Boolean appInstalled
) {

    public static DriverResponse from(Driver driver) {
        return new DriverResponse(
                driver.getDriverCode(),
                driver.getPhoneNumber(),
                driver.getVehicleType(),
                driver.getSource(),
                driver.getAppInstalled());
    }
}
