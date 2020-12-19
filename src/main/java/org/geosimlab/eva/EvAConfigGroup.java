package org.geosimlab.eva;

import org.matsim.core.config.ReflectiveConfigGroup;

/**
 * @author grauwelf
 *
 */

public class EvAConfigGroup extends ReflectiveConfigGroup {
	public static final String GROUP_NAME = "eva";

	public static final String ITERATIONS_PER_CYCLE = "iterationsPerCycle";
	private int iterationsPerCycle = 5;

	public EvAConfigGroup() {
		super(GROUP_NAME);
	}

	@StringGetter(ITERATIONS_PER_CYCLE)
	public int getIterationsPerCycle() {
		return iterationsPerCycle;
	}

	@StringSetter(ITERATIONS_PER_CYCLE)
	public  void setIterationsPerCycle(int iterationsPerCycle) {
		this.iterationsPerCycle = iterationsPerCycle;
	}

}