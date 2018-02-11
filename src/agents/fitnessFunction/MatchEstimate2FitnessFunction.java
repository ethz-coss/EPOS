/*
 * Copyright (C) 2016 Evangelos Pournaras
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package agents.fitnessFunction;

import agents.Agent;
import agents.AgentPlans;
import agents.plan.AggregatePlan;
import agents.plan.GlobalPlan;
import agents.plan.Plan;
import java.util.List;

/**
 *
 * @author Peter
 */
public class MatchEstimate2FitnessFunction extends FitnessFunction {

    @Override
    public double getRobustness(Plan globalPlan, Plan pattern, AgentPlans historic) {
        return globalPlan.rootMeanSquareError(pattern);
    }

    @Override
    public int select(Agent agent, Plan aggregatePlan, List<Plan> combinationalPlans, Plan pattern, AgentPlans historic) {
        double minRootMeanSquaredError = Double.MAX_VALUE;
        int selected = -1;

        for (int i = 0; i < combinationalPlans.size(); i++) {
            Plan combinationalPlan = combinationalPlans.get(i);
            Plan testAggregatePlan = new AggregatePlan(agent);
            testAggregatePlan.add(aggregatePlan);
            testAggregatePlan.add(combinationalPlan);

            Plan normalizedPatternPlan = new GlobalPlan(agent);
            normalizedPatternPlan.add(pattern);
            normalizedPatternPlan.reverse();
            normalizedPatternPlan.subtract(normalizedPatternPlan.avg());
            normalizedPatternPlan.multiply(testAggregatePlan.stdDeviation() / normalizedPatternPlan.stdDeviation());
            normalizedPatternPlan.add(testAggregatePlan.avg());

            double rootMeanSquaredError = normalizedPatternPlan.rootMeanSquareError(testAggregatePlan);
            if (rootMeanSquaredError < minRootMeanSquaredError) {
                minRootMeanSquaredError = rootMeanSquaredError;
                selected = i;
            }
        }

        return selected;
    }
}