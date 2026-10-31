package com.swiftdrop.identity.courier;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.swiftdrop.identity.approval.Application;
import com.swiftdrop.identity.user.User;

/** A courier application. The courier is identified by their user ID. */
@Entity
@Table(name = "couriers")
public class Courier extends Application {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    /** Read-only link, for the courier's name. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "national_id_number", nullable = false, length = 20)
    private String nationalIdNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "driving_licence_number", length = 20)
    private String drivingLicenceNumber;

    @Column(name = "vehicle_registration_number", length = 20)
    private String vehicleRegistrationNumber;

    protected Courier() {
    }

    Courier(User user, CourierApplicationRequest request, Instant now) {
        super(now);
        this.userId = user.getId();
        this.user = user;
        setDetails(request);
    }

    void replaceDetails(CourierApplicationRequest request, Instant now) {
        setDetails(request);
        touch(now);
    }

    private void setDetails(CourierApplicationRequest request) {
        this.nationalIdNumber = request.nationalIdNumber().trim().toUpperCase(Locale.ROOT);
        this.vehicleType = request.vehicleType();
        // A bicycle has no licence or registration; don't keep stale values from an earlier vehicle.
        this.drivingLicenceNumber = vehicleType.requiresDocuments() ? normalize(request.drivingLicenceNumber()) : null;
        this.vehicleRegistrationNumber = vehicleType.requiresDocuments()
                ? normalize(request.vehicleRegistrationNumber()) : null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFullName() {
        return user.getFullName();
    }

    String getNationalIdNumber() {
        return nationalIdNumber;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    String getDrivingLicenceNumber() {
        return drivingLicenceNumber;
    }

    String getVehicleRegistrationNumber() {
        return vehicleRegistrationNumber;
    }
}
