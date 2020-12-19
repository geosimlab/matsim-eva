package org.geosimlab.eva.mobsim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.geosimlab.eva.transitSchedule.EvADeparture;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.contrib.pseudosimulation.mobsim.transitperformance.TransitEmulator;
import org.matsim.contrib.pseudosimulation.util.CollectionUtils;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteFactories;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ProjectionUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.misc.StringUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.config.TransitConfigGroup;
import org.matsim.pt.router.TransitScheduleChangedEvent;
import org.matsim.pt.transitSchedule.DepartureImpl;
import org.matsim.pt.transitSchedule.TransitRouteStopImpl;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopArea;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.utils.objectattributes.attributable.AttributesUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt.utils.TransitScheduleValidator.ValidationResult;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

/**
 * @author grauwelf
 * 
 */

public class EvASim implements Mobsim {

    private final Scenario scenario;
    private final EventsManager eventManager;

    private final static double MIN_ACT_DURATION = 1.0;

    private final static double MIN_LEG_DURATION = 0.0;

    AtomicInteger numThreads;

    private final TravelTime carLinkTravelTimes;
    private final Collection<Plan> plans;
    private final double endTime;
    
    // Encapsulates TransitPerformance, WaitTime, StopStopTime, ...
    private TransitEmulator transitEmulator = null;
    private Set<String> transitModes = new LinkedHashSet<>();
    
    // CRS transformation
    private CoordinateTransformation coordinateTransformation = new IdentityTransformation();
    private RouteFactories routeFactory;
    
    // EvA iteration counter
    private Long evaIteration = 0L;
    
    public EvASim(Scenario sc, EventsManager eventsManager, Collection<Plan> plans, TravelTime carLinkTravelTimes) {
        Logger.getLogger(getClass()).info("Constructing EvASim");
        this.scenario = sc;
        this.endTime = sc.getConfig().qsim().getEndTime().orElse(0);
        this.eventManager = eventsManager;
        int numThreads = sc.getConfig().global().getNumberOfThreads();
        this.carLinkTravelTimes = carLinkTravelTimes;
        this.plans = plans;
        /*
	if (this.scenario.getNetwork().getAttributes().getAttribute("coordinateReferenceSystem") != null) {
		this.coordinateTransformation = TransformationFactory.getCoordinateTransformation(externalInputCRS, targetCRS);
	}
	*/
    }

    public EvASim(Scenario sc, EventsManager eventsManager, Collection<Plan> plans, TravelTime carLinkTravelTimes, TransitEmulator transitEmulator, Long evaIteration) {
        this(sc, eventsManager, plans, carLinkTravelTimes);
        this.evaIteration = evaIteration;
        this.transitEmulator = transitEmulator;
        this.transitModes = ConfigUtils.addOrGetModule(sc.getConfig(), TransitConfigGroup.class).getTransitModes();
        this.routeFactory = sc.getPopulation().getFactory().getRouteFactories();
    }

    public void validateScheduleAndNetwork(TransitSchedule schedule, Network network) {
    	ValidationResult vResult = TransitScheduleValidator.validateAll(schedule, network);
		if (vResult.isValid()) {
			Logger.getLogger(this.getClass()).info("Schedule appears valid!");
		} else {
			Logger.getLogger(this.getClass()).info("Schedule is NOT valid!");
		}
		if (vResult.getErrors().size() > 0) {
			Logger.getLogger(this.getClass()).info("Validation errors:");
			for (String e : vResult.getErrors()) {
				Logger.getLogger(this.getClass()).info(e);
			}
		}
		if (vResult.getWarnings().size() > 0) {
			Logger.getLogger(this.getClass()).info("Validation warnings:");
			for (String w : vResult.getWarnings()) {
				Logger.getLogger(this.getClass()).info(w);
			}
		}
    }
    
    @Override
    public void run() {
        Logger.getLogger(this.getClass()).info("Executing Evolutionary Step");
      
	/* 
	 * Code below demonstrates basic operations on network and transit schedule
	 * which can be used for evolutionary step.
	 *
	 * Cross-entropy optimization will be performed here. 
	 *
	 */

        /*	
        Network network = scenario.getNetwork();
        
        Vehicles ptVehicles = scenario.getTransitVehicles();
        VehiclesFactory ptVehiclesFactory = ptVehicles.getFactory();
        VehicleType evaVehicleType = ptVehicles.getVehicleTypes()
        		.get(Id.create("EvATransitVehicleType", VehicleType.class));
                
        TransitSchedule schedule = scenario.getTransitSchedule();        
        TransitLine[] ptLines = schedule
        		.getTransitLines()
        		.values()
        		.toArray(new TransitLine[schedule.getTransitLines().size()]);
        Arrays.sort(ptLines, Comparator.comparing(Identifiable::getId));
        
        Id<TransitLine> ptLineId = ptLines[0].getId();
        TransitRoute[] ptRoutes = ptLines[0]
        		.getRoutes()
        		.values()
        		.toArray(new TransitRoute[ptLines[0].getRoutes().size()]);
        
        for(TransitRoute route: ptRoutes) {
        	Departure[] ptDepartures = route
        			.getDepartures()
        			.values()
        			.toArray(new Departure[route.getDepartures().size()]);
        	
        	for(Departure departure: ptDepartures) {
	        	double dt = departure.getDepartureTime();
	 
	        	Id<Vehicle> vehId = Id.create("tr_" + route.getId() + "_" + Long.toString(Math.round(dt)), Vehicle.class);
	        	if (ptVehicles.getVehicles().get(vehId) == null) {
	            	Vehicle veh = ptVehiclesFactory
	        				.createVehicle(vehId, evaVehicleType);
	        		ptVehicles.addVehicle(veh);    		
	        		EvADeparture evaDeparture = new EvADeparture(Id.create(Double.toString(dt), Departure.class), dt - 60);
	        		evaDeparture.setVehicleId(veh.getId());        	            	
	            	route.addDeparture(evaDeparture);
	            	Logger.getLogger(this.getClass())
	    				.info("Line " + ptLineId + " has route " + route.getId() + " with new departure at " + (dt - 60));        		
	        	}
        	}
        }        
        
        if (this.evaIteration == 1) {
        	// Create new transit link        	
        	String fromNodeStr = "2";
    		Node fromNode = network.getNodes().get(Id.create(fromNodeStr, Node.class));
    		String toNodeStr = "44";
    		Node toNode = network.getNodes().get(Id.create(toNodeStr, Node.class));
    		
    		Id<Link> transitLinkId = Id.create("NorthEast-Center", Link.class);
    		Link transitLink = network.getFactory().createLink(transitLinkId, fromNode, toNode);
    		transitLink.setLength(2155);
    		transitLink.setFreespeed(75);
    		transitLink.setCapacity(2000);
    		transitLink.setNumberOfLanes(1);
    		Set<String> modes = new HashSet<>();
		modes.add("train");
		transitLink.setAllowedModes(modes);
    		network.addLink(transitLink);
    		        	
        	// Create new transit line
		Id<TransitLine> evaTransitLineId = Id.create("EvA Transit Line", TransitLine.class);
		TransitLine evaTransitLine = schedule.getFactory().createTransitLine(evaTransitLineId);
		evaTransitLine.setName("EvA Transit Line");
			
	        boolean isBlocking = false;
	        
		// Stop facility in the center, near the node 1						
		Coord coord = new Coord(1040, 1050);
		TransitStopFacility stopCenter = schedule.getFactory()
				.createTransitStopFacility(
						Id.create("Iteration #" + this.evaIteration + " EvA Center", TransitStopFacility.class),
						this.coordinateTransformation.transform(coord),
						isBlocking);
		stopCenter.setLinkId(Id.create("11", Link.class));
		stopCenter.setName("EvA Center");
		schedule.addStopFacility(stopCenter);
	
		// North-east stop facility near the node 44
		coord = new Coord(3950, 3950);
		TransitStopFacility stopNorthEast = schedule.getFactory()
				.createTransitStopFacility(
						Id.create("Iteration #" + this.evaIteration + " EvA NorthEast", TransitStopFacility.class),
						this.coordinateTransformation.transform(coord),
						isBlocking);		
		stopNorthEast.setLinkId(transitLinkId);
		stopNorthEast.setName("EvA NorthEast");
		schedule.addStopFacility(stopNorthEast);
		
		// Create routes
		Id<TransitRoute> evaRouteForwardId = Id.create("Center === NorthEast", TransitRoute.class);
		List<TransitRouteStopImpl.Builder> stopBuilders = new ArrayList<>();
		
		TransitRouteStopImpl.Builder stopBuilderCenter = new TransitRouteStopImpl.Builder().stop(stopCenter);
		String arrivalOffset = "00:01:00";
		String departureOffset = "00:00:00";
		//Time.parseOptionalTime(arrivalOffset).ifDefined(stopBuilderCenter::arrivalOffset);
		Time.parseOptionalTime(departureOffset).ifDefined(stopBuilderCenter::departureOffset);		
		stopBuilderCenter.awaitDepartureTime(true);
		stopBuilders.add(stopBuilderCenter);
		
		TransitRouteStopImpl.Builder stopBuilderNorthEast = new TransitRouteStopImpl.Builder().stop(stopNorthEast);
		arrivalOffset = "00:03:00";
		Time.parseOptionalTime(arrivalOffset).ifDefined(stopBuilderNorthEast::arrivalOffset);		
		//stopBuilderNorthEast.awaitDepartureTime(true);
		stopBuilders.add(stopBuilderNorthEast);						
		
		List<TransitRouteStop> stops = new ArrayList<>(stopBuilders.size());
		stopBuilders.forEach(stopBuilder -> stops.add(stopBuilder.build()));
		
		Id<Link> firstLinkId = Id.create("11", Link.class);
		Id<Link> lastLinkId = transitLinkId;
		List<Id<Link>> linkIds = new ArrayList<>();
		linkIds.add(Id.create("12", Link.class));
		NetworkRoute route = this.routeFactory
				.createRoute(NetworkRoute.class, firstLinkId, lastLinkId);
		route.setLinkIds(firstLinkId, linkIds, lastLinkId);
		
		TransitRoute evaRouteForward = schedule.getFactory()
				.createTransitRoute(evaRouteForwardId, route, stops, "train");
		// Add departures
		for (int departureTime = 6*3600; departureTime < 23*3600; departureTime += 60) {
			Id<Vehicle> vehId = Id.create("tr_" + evaRouteForward.getId() + "_" + Long.toString(Math.round(departureTime)), Vehicle.class);
			if (ptVehicles.getVehicles().get(vehId) == null) {
			Vehicle veh = ptVehiclesFactory
						.createVehicle(vehId, evaVehicleType);
				ptVehicles.addVehicle(veh);    		
				EvADeparture evaDeparture = new EvADeparture(Id.create(Integer.toString(departureTime), Departure.class), departureTime);
				evaDeparture.setVehicleId(veh.getId());        	     
				evaRouteForward.addDeparture(evaDeparture);
			}
		}
		//AttributesUtils.copyTo(attributes, evaRouteForward.getAttributes());			
		evaTransitLine.addRoute(evaRouteForward);
		//Id<TransitRoute> evaRouteBackward = Id.create("Center <+ NorthEast", TransitRoute.class);
		schedule.addTransitLine(evaTransitLine);
        }
    	*/
        
	// We can validate transit network and schedule after changes
        //this.validateScheduleAndNetwork(schedule, network);
        
        // Notify TransitRouter about changes in transit schedule
        this.eventManager.processEvent(new TransitScheduleChangedEvent(0.0)); // I am not sure about the meaning of time = 0.0                
    }
    
}
