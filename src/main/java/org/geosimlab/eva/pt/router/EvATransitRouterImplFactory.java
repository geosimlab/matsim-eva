package org.geosimlab.eva.pt.router;

import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterNetwork;
import org.matsim.pt.router.TransitScheduleChangedEventHandler;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * @author grauwelf
 */

@Singleton
public class EvATransitRouterImplFactory implements Provider<TransitRouter> {

	private TransitRouterConfig config;
	private TransitSchedule transitSchedule;
	private TransitRouterNetwork routerNetwork;
	private PreparedTransitSchedule preparedTransitSchedule;

	@Inject
	EvATransitRouterImplFactory(final TransitSchedule schedule, final EventsManager events, final Config config) {
		this(schedule, new TransitRouterConfig(
				config.planCalcScore(),
				config.plansCalcRoute(),
				config.transitRouter(),
				config.vspExperimental()));
		
		events.addHandler((TransitScheduleChangedEventHandler) event -> {
			routerNetwork = null;
			preparedTransitSchedule = null;
		});
	}

	public EvATransitRouterImplFactory(final TransitSchedule schedule, final TransitRouterConfig config) {
		this.config = config;
		this.transitSchedule = schedule;
	}

	@Override
	public TransitRouter get() {
		if (this.routerNetwork == null) {
			this.routerNetwork = TransitRouterNetwork.createFromSchedule(transitSchedule, this.config.getBeelineWalkConnectionDistance());
		}
		if (this.preparedTransitSchedule == null) {
			this.preparedTransitSchedule = new PreparedTransitSchedule(transitSchedule);
		}
				
		EvATransitRouterNetworkTravelTimeAndDisutility ttCalculator = new EvATransitRouterNetworkTravelTimeAndDisutility(this.config, this.preparedTransitSchedule);
		return new EvATransitRouterImpl(this.config, this.preparedTransitSchedule, this.routerNetwork, ttCalculator, ttCalculator);
	}
	
}
