package org.geosimlab.eva;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.google.inject.Inject;
import org.apache.log4j.Logger;
import org.geosimlab.eva.replanning.EvAPlanCatcher;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.IterationStartsListener;

/**
 * @author grauwelf
 *
 */

public class EvASwitcher implements IterationEndsListener,
		IterationStartsListener, BeforeMobsimListener {
	private final Scenario scenario;
	private boolean isEvASimIteration = false;
	private int qSimIterationCount = 0;	
	private int qSimIterationsPerCycle = 0;
	private Map<Id<Person>, Double> selectedPlanScoreMemory;
	@Inject private EvAPlanCatcher plancatcher;

	@Inject
	public EvASwitcher(EvAConfigGroup evaConfigGroup, Scenario scenario) {
		this.qSimIterationsPerCycle = evaConfigGroup.getIterationsPerCycle();
		this.scenario = scenario;
	}

	public boolean isEvASimIteration() {
		return isEvASimIteration;
	}

	private boolean determineIfEvASimIter(int iteration) {
		if (iteration == scenario.getConfig().controler().getLastIteration() || iteration == scenario.getConfig().controler().getFirstIteration() ) {
			isEvASimIteration = false;
			return isEvASimIteration;
		}
		if (isEvASimIteration && qSimIterationCount == 0) {
			isEvASimIteration = false;
			qSimIterationCount++;
			return isEvASimIteration;
		}
		if (qSimIterationCount >= qSimIterationsPerCycle) {
			isEvASimIteration = true;		
			qSimIterationCount = 0;
			return isEvASimIteration;
		}
		if (!isEvASimIteration) 
			qSimIterationCount++;
		
		return isEvASimIteration;
	}
		
	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		if (determineIfEvASimIter(event.getIteration())) {
			Logger.getLogger(this.getClass()).info("Running EvASim");
			plancatcher.init();
			for (Person person : scenario.getPopulation().getPersons().values()) {
					plancatcher.addPlansForEvASim(person.getSelectedPlan());
			}
		} else {
			Logger.getLogger(this.getClass()).info("Running regular QSim");
		}
	}

	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
		if (!this.isEvASimIteration())
			return;

		for (Person person : scenario.getPopulation().getPersons().values()) {
				plancatcher.removeExistingPlanOrAddNewPlan(person.getSelectedPlan());
		}

		selectedPlanScoreMemory = new HashMap<>(scenario.getPopulation().getPersons().size());

		for (Person person : scenario.getPopulation().getPersons().values()) {
			selectedPlanScoreMemory.put(person.getId(), person.getSelectedPlan().getScore());
		}
		for (Plan plan : plancatcher.getPlansForEvASim()) {
			selectedPlanScoreMemory.remove(plan.getPerson().getId());
		}

	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		if (!this.isEvASimIteration())
			return;
		Iterator<Map.Entry<Id<Person>, Double>> iterator = selectedPlanScoreMemory.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Id<Person>, Double> entry = iterator.next();
			scenario.getPopulation().getPersons().get(entry.getKey()).getSelectedPlan().setScore(entry.getValue());
		}
	}
}