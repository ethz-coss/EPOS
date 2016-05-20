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
package agents.dataset;

import agents.plan.Plan;
import java.util.Collections;
import java.util.List;
import org.joda.time.DateTime;

/**
 *
 * @author Peter
 */
public class OrderedFileDataset extends FileDataset {
    
    public OrderedFileDataset(String location, String config) {
        super(location, config);
    }

    @Override
    AgentDataset createAgentDataset(String planLocation, String config, String id, String format, int planSize) {
        return new FileAgentDataset(planLocation, config, id, format, planSize) {
            @Override
            public List<Plan> getPlans(DateTime phase) {
                List<Plan> plans = super.getPlans(phase);
                
                Collections.sort(plans, (a, b) -> {
                    for (int i = 0; i < a.getNumberOfStates(); i++) {
                        if (a.getValue(i) < b.getValue(i)) {
                            return -1;
                        }
                        if (a.getValue(i) > b.getValue(i)) {
                            return 1;
                        }
                    }
                    return 0;
                });
                
                return plans;
            }
            
        };
    }
    
}
