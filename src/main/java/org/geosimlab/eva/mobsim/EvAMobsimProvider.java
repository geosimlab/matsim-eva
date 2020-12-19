package org.geosimlab.eva.mobsim;

import com.google.inject.Inject;
import com.google.inject.Provider;

import org.geosimlab.eva.EvASwitcher;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.mobsim.jdeqsim.JDEQSimConfigGroup;
import org.matsim.core.mobsim.jdeqsim.JDEQSimulation;
import org.matsim.core.mobsim.qsim.QSimProvider;

/**
 * @author grauwelf
 *
 */

public class EvAMobsimProvider implements Provider<Mobsim> {

    @Inject private Config config;
    @Inject private Scenario scenario;
    @Inject private EventsManager eventsManager;
    @Inject private EvASwitcher evaSwitcher;
    @Inject private EvASimProvider evaSimProvider;
    @Inject private QSimProvider qsimProvider;


    @Override
    public Mobsim get() {  	
        if (evaSwitcher.isEvASimIteration()) {
        	evaSimProvider.incrementEvAIterationCounter();
        	return evaSimProvider.get();
        } else {
            String mobsim = config.controler().getMobsim();
            if (mobsim.equals("jdeqsim")) {
                return new JDEQSimulation(ConfigUtils.addOrGetModule(scenario.getConfig(), 
                		JDEQSimConfigGroup.NAME, JDEQSimConfigGroup.class), scenario, eventsManager);
            } else {
            	return qsimProvider.get();
            }            
        }
    }

}