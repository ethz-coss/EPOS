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
package agents;

import agents.energyPlan.AggregatePlan;
import agents.energyPlan.Plan;
import agents.fitnessFunction.FitnessFunction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import messages.OPTAggregate;
import messages.OPTOptimal;
import org.joda.time.DateTime;
import protopeer.Finger;
import protopeer.measurement.MeasurementLog;
import protopeer.network.Message;
import protopeer.network.NetworkAddress;

/**
 *
 * @author Peter
 */
public class OptimalAgent extends Agent {

    private FitnessFunction fitnessFunction;

    private int activeChild = -1;

    public OptimalAgent(String experimentID, String plansLocation, String planConfigurations, String treeStamp, String agentMeterID, DateTime initialPhase, String plansFormat, int planSize, Plan costSignal, FitnessFunction fitnessFunction) {
        super(experimentID, plansLocation, planConfigurations, treeStamp, agentMeterID, initialPhase, plansFormat, planSize, costSignal);
        this.fitnessFunction = fitnessFunction;
    }

    @Override
    void runPhase() {
        if (isRoot()) {
            OPTAggregate msg = new OPTAggregate();
            for (int i = 0; i < possiblePlans.size(); i++) {
                Map<NetworkAddress, Integer> selection = new HashMap<>();
                selection.put(getPeer().getNetworkAddress(), i);
                msg.aggregatedPossiblePlans.put(possiblePlans.get(i), selection);
            }
            activeChild++;
            if(activeChild < children.size()) {
                getPeer().sendMessage(children.get(activeChild).getNetworkAddress(), msg);
            }
        }
    }

    @Override
    public void handleIncomingMessage(Message message) {
        if (message instanceof OPTAggregate) {
            OPTAggregate msg = (OPTAggregate) message;
            
            if(activeChild == -1) {  
                Map<Plan, Map<NetworkAddress, Integer>> aggregatedPossiblePlans = new HashMap<>();
                for (Map.Entry<Plan, Map<NetworkAddress, Integer>> entry : msg.aggregatedPossiblePlans.entrySet()) {
                    Plan aggregatedPlan = entry.getKey();
                    Map<NetworkAddress, Integer> selection = entry.getValue();

                    for (int i = 0; i < possiblePlans.size(); i++) {
                        Plan localPlan = possiblePlans.get(i);
                        Plan newAggregatedPlan = new AggregatePlan();
                        newAggregatedPlan.set(aggregatedPlan);
                        newAggregatedPlan.add(localPlan);

                        HashMap<NetworkAddress, Integer> newSelection = new HashMap<>(selection);
                        newSelection.put(getPeer().getNetworkAddress(), i);

                        aggregatedPossiblePlans.put(newAggregatedPlan, newSelection);
                    }
                }
                
                msg = new OPTAggregate();
                msg.aggregatedPossiblePlans = aggregatedPossiblePlans;
            }
                
            if(activeChild+1 < children.size()) {
                activeChild++;
                getPeer().sendMessage(children.get(activeChild).getNetworkAddress(), msg);
            } else if(!isRoot()) {
                getPeer().sendMessage(parent.getNetworkAddress(), msg);
            } else {
                this.globalPlan = fitnessFunction.select(this, new AggregatePlan(this), new ArrayList<>(msg.aggregatedPossiblePlans.keySet()), costSignal, null);
                Map<NetworkAddress,Integer> selection = msg.aggregatedPossiblePlans.get(globalPlan);
                int selectedPlanIdx = selection.get(getPeer().getNetworkAddress());
                this.selectedPlan = possiblePlans.get(selectedPlanIdx);
                
                OPTOptimal m = new OPTOptimal();
                m.globalPlan = globalPlan;
                m.selection = selection;
                for(Finger c : children) {
                    getPeer().sendMessage(c.getNetworkAddress(), m);
                }
            }
        } else if (message instanceof OPTOptimal) {
            OPTOptimal msg = (OPTOptimal) message;
            
            globalPlan = msg.globalPlan;
            selectedPlan = possiblePlans.get(msg.selection.get(getPeer().getNetworkAddress()));
            for(Finger c : children) {
                getPeer().sendMessage(c.getNetworkAddress(), msg);
            }
        }
    }

    @Override
    void measure(MeasurementLog log, int epochNumber) {
        if (epochNumber == 2) {
            if (this.isRoot()) {
                log.log(epochNumber, globalPlan, 1.0);
                log.log(epochNumber, EPOSMeasures.PLAN_SIZE, globalPlan.getNumberOfStates());
            }
            log.log(epochNumber, selectedPlan, 1.0);
            log.log(epochNumber, EPOSMeasures.DISCOMFORT, selectedPlan.getDiscomfort());
            //log.log(epochNumber, Measurements.SELECTED_PLAN_VALUE, selectedPlan.getArithmeticState(0).getValue());
            writeGraphData(epochNumber);
        }
    }
    
    private void writeGraphData(int epochNumber) {
        System.out.println(getPeer().getNetworkAddress().toString() + ","
                + ((parent != null) ? parent.getNetworkAddress().toString() : "-") + ","
                + findSelectedPlan());
    }


    private int findSelectedPlan() {
        for (int i = 0; i < possiblePlans.size(); i++) {
            if (possiblePlans.get(i).equals(selectedPlan)){
                return i;
            }
        }
        return -1;
    }
}