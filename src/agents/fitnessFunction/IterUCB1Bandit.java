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
import agents.fitnessFunction.iterative.MostRecentCombinator;
import agents.fitnessFunction.iterative.PlanCombinator;
import agents.plan.Plan;
import java.util.List;

/**
 *
 * @author Peter
 */
public class IterUCB1Bandit extends IterativeFitnessFunction {

    private double[] estimate;
    private double[] timesSelected;
    private int prevSelected;
    
    private PlanCombinator combinator = MostRecentCombinator.getInstance();
    private Plan previousGlobalPlan;
    private Plan previousSelectedPlan;

    @Override
    public double getRobustness(Plan plan, Plan costSignal, AgentPlans historic) {
        return Math.sqrt(plan.variance());
    }
    
    @Override
    public void afterIteration(AgentPlans current, Plan costSignal, int iteration, int numNodes) {
        previousGlobalPlan = combinator.combine(previousGlobalPlan, current.global, iteration);
        previousSelectedPlan = combinator.combine(previousSelectedPlan, current.selectedPlan, iteration);
    }

    @Override
    public int select(Agent agent, Plan aggregate, List<Plan> plans, Plan costSignal, int numNodes, int numNodesSubtree, int layer, double avgChildren, int iteration) {
        int selected = -1;
        
        if (iteration == 0) {
            estimate = new double[plans.size()];
            timesSelected = new double[plans.size()];

            selected = (int) (Math.random() * plans.size());
        } else {
            Plan g = previousGlobalPlan.clone();
            Plan s = previousSelectedPlan.clone();
            g.subtract(g.avg());
            s.subtract(s.avg());
            double reward = 0.5-0.5*g.dot(s) / Math.max(0.000001, g.norm()*s.norm());
            
            estimate[prevSelected] += (reward - estimate[prevSelected]) / timesSelected[prevSelected];

            double maxUCB = 0;
            int numOpt = 0;
            for (int i = 0; i < plans.size(); i++) {
                double UCB = estimate[i] + Math.sqrt(2.0 * Math.log(iteration) / timesSelected[i]);
                if (UCB > maxUCB) {
                    maxUCB = UCB;
                    selected = i;
                    numOpt = 1;
                } else if(UCB == maxUCB) {
                    numOpt++;
                    if(Math.random()<=1.0/numOpt) {
                        selected = i;
                    }
                }
            }
        }
        iteration++;

        timesSelected[selected]++;
        prevSelected = selected;
        return selected;
    }

    @Override
    public String toString() {
        return "IterUCB1Bandit";
    }
}