package org.geosimlab.eva.replanning;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Plan;

import java.util.*;

@Singleton
public class EvAPlanCatcher {
    @Inject
	public EvAPlanCatcher(){}

    private Map<Id,Plan> plansForEvASim;

    public Collection<Plan> getPlansForEvASim() {
        return plansForEvASim.values();
    }

    public void addPlansForEvASim(Plan plan) {
        if (plansForEvASim == null)
            plansForEvASim = new HashMap<>();
        plansForEvASim.put(plan.getPerson().getId(), plan);
    }

    public void init() {
        plansForEvASim = new HashMap<>();
    }

	public void removeExistingPlanOrAddNewPlan(Plan plan) {
		if (plansForEvASim == null)
			plansForEvASim = new HashMap<>();
		if (plansForEvASim.get(plan.getPerson().getId()) == plan)
			plansForEvASim.remove(plan.getPerson().getId());
		else
			plansForEvASim.put(plan.getPerson().getId(), plan);
	}
}
