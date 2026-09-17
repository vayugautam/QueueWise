package com.queuewise.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ServiceHoursId implements Serializable {

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "day_of_week")
    private short dayOfWeek;

    protected ServiceHoursId() { }

    public ServiceHoursId(UUID serviceId, short dayOfWeek) {
        this.serviceId = serviceId;
        this.dayOfWeek = dayOfWeek;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServiceHoursId that)) return false;
        return dayOfWeek == that.dayOfWeek && Objects.equals(serviceId, that.serviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceId, dayOfWeek);
    }
}
