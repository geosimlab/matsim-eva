package org.geosimlab.eva;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.mobsim.qsim.QSimProvider;
import org.matsim.core.scenario.ScenarioUtils;

import org.matsim.pt.router.TransitRouter;

import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;

import org.matsim.contrib.pseudosimulation.mobsim.transitperformance.TransitEmulator;
import org.matsim.contrib.pseudosimulation.mobsim.transitperformance.NoTransitEmulator;

import org.geosimlab.eva.EvAConfigGroup;
import org.geosimlab.eva.mobsim.EvAMobsimProvider;
import org.geosimlab.eva.mobsim.EvASimProvider;
import org.geosimlab.eva.pt.router.EvATransitRouterImplFactory;
import org.geosimlab.eva.replanning.EvAPlanCatcher;

/**
 * @author grauwelf
 *
 */

public class RunEvAMatsim{
	
	private Scenario scenario;
	
	private Controler matsimControler;
	
	public RunEvAMatsim(Config config, EvAConfigGroup evaConfigGroup) {
		config.parallelEventHandling().setSynchronizeOnSimSteps(false);
		
		this.scenario = ScenarioUtils.loadScenario(config);
    	
		// Create specific VehicleType for additional transit vehicles 
		Vehicles ptVehicles = scenario.getTransitVehicles(); 
        	VehiclesFactory ptVehiclesFactory = ptVehicles.getFactory();  
		VehicleType evaVehicleType = ptVehiclesFactory
				.createVehicleType(Id.create("EvATransitVehicleType", VehicleType.class));
		evaVehicleType.setLength(12);
		evaVehicleType.setMaximumVelocity(65);
		evaVehicleType.getCapacity().setSeats(42);
		evaVehicleType.getCapacity().setStandingRoom(0);
		ptVehicles.addVehicleType(evaVehicleType);		
		
		this.matsimControler = new Controler(scenario);		
		
		EvASwitcher evaSwitcher = new EvASwitcher(evaConfigGroup, scenario);
		matsimControler.addControlerListener(evaSwitcher);			
		
		matsimControler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bind(TransitEmulator.class).to(NoTransitEmulator.class);
				bind(TravelTime.class).toInstance(new FreeSpeedTravelTime());
				bind(TransitRouter.class).toProvider(EvATransitRouterImplFactory.class);
				bind(EvASwitcher.class).toInstance(evaSwitcher);
				bindMobsim().toProvider(EvAMobsimProvider.class);
				bind(EvAPlanCatcher.class).toInstance(new EvAPlanCatcher());
				bind(EvASimProvider.class).toInstance(new EvASimProvider(scenario, matsimControler.getEvents()));
				bind(QSimProvider.class);																
			}
		});		
	}
	
	
	public static void main(String[] args) {

		Logger logger = Logger.getLogger(RunEvAMatsim.class);
		
		Config config;
		if ( args==null || args.length==0 || args[0]==null ){
			//config = ConfigUtils.loadConfig("scenarios/pt-tutorial-2only/0.config.xml");
			config = ConfigUtils.loadConfig("scenarios/pt-tutorial/0.config.xml");
		} else {
			config = ConfigUtils.loadConfig(args);
		}
		config.controler().setCreateGraphs(true);
		//config.controler().setOutputDirectory("scenarios/pt-tutorial-2only/output");
		config.controler().setOutputDirectory("scenarios/pt-tutorial/output");
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setLastIteration(15);
		
		EvAConfigGroup evaConfigGroup = new EvAConfigGroup();
		evaConfigGroup.setIterationsPerCycle(5);
		config.addModule(evaConfigGroup);

		new RunEvAMatsim(config, evaConfigGroup).run();
	}
	
	public MatsimServices getMatsimControler() {
		return matsimControler;
	}

	public void run() {
		matsimControler.run();
	}
	
}
