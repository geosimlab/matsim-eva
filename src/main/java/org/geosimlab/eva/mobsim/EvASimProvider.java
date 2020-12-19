/**
 *
 */
package org.geosimlab.eva.mobsim;

import org.geosimlab.eva.replanning.EvAPlanCatcher;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.pseudosimulation.mobsim.transitperformance.TransitEmulator;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * @author grauwelf
 * 
 */

public class EvASimProvider implements Provider<Mobsim> {

    @Inject private EvAPlanCatcher plans;
    @Inject private TravelTime travelTime;
    @Inject TransitEmulator transitEmulator;
    private final Scenario scenario;
    private final EventsManager eventsManager;
    private Long evaIteration = 0L;

    @Inject
	public EvASimProvider(Scenario scenario, EventsManager eventsManager) {
        this.scenario = scenario;
        this.eventsManager = eventsManager;
    }

    @Override
    public Mobsim get() {
        return new EvASim(scenario, eventsManager, plans.getPlansForEvASim(), travelTime, transitEmulator, this.evaIteration);
    }

    public void setTravelTime(TravelTime travelTime) {
        this.travelTime = travelTime;
    }
    
    public void incrementEvAIterationCounter() {
    	this.evaIteration++;
    }
}
