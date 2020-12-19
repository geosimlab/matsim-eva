package org.geosimlab.eva.transitSchedule;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.Vehicle;

public class EvADeparture implements Departure {

	private final Id<Departure> id;
	private final double departureTime;
	private Id<Vehicle> vehicleId = null;
	private final Attributes attributes = new Attributes();
	
	public EvADeparture(final Id<Departure> id, final double departureTime) {
		this.id = id;
		this.departureTime = departureTime;
	}

	@Override
	public Id<Departure> getId() {
		return this.id;
	}

	@Override
	public double getDepartureTime() {
		return this.departureTime;
	}

	@Override
	public void setVehicleId(final Id<Vehicle> vehicleId) {
		this.vehicleId = vehicleId;
	}

	@Override
	public Id<Vehicle> getVehicleId() {
		return this.vehicleId;
	}

	@Override
	public Attributes getAttributes() {
		return this.attributes;
	}

	@Override
	public String toString() {
		return "[DepartureImpl: id=" + this.id + ", depTime=" + this.departureTime + "]";
	}
	
}