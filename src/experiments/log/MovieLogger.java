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
package experiments.log;

import agents.Agent;
import agents.fitnessFunction.costFunction.IterativeCostFunction;
import agents.fitnessFunction.costFunction.VarCostFunction;
import agents.plan.Plan;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import protopeer.measurement.MeasurementLog;

/**
 *
 * @author Peter
 */
public class MovieLogger extends AgentLogger {

    private String dir;

    private Plan costSignal;
    private Plan iterativeCost;

    private IterativeCostFunction measure = new VarCostFunction();

    public MovieLogger(String dir) {
        this.dir = "output-data/" + dir;
    }

    @Override
    public void init(int agentId) {
    }

    @Override
    public void initRoot(Plan costSignal) {
        this.costSignal = costSignal;
    }

    @Override
    public void log(MeasurementLog log, int epoch, Agent agent) {
    }

    @Override
    public void logRoot(MeasurementLog log, int epoch, Agent agent, Plan global) {
        if (iterativeCost == null) {
            iterativeCost = measure.calcGradient(global, costSignal);
        } else {
            iterativeCost = iterativeCost.clone();
            iterativeCost.add(measure.calcGradient(global, costSignal));
        }

        Entry entry = new Entry();
        entry.iteration = agent.getIteration();
        entry.costSignal = agent.getIteration() == 0 ? costSignal : null;
        entry.global = global;
        entry.iterativeCost = iterativeCost;

        log.log(epoch, entry, 0.0);
    }
    
    private String str2filename(String str) {
        return str.replace(' ', '_').replace('-','m').replace('.', '_');
    }

    @Override
    public void print(MeasurementLog log) {
        // label of the current execution is stored in the log as String tag: "label=..."
        String filename = this.dir;
        Set<Object> info = (Set<Object>) log.getTagsOfType(String.class);
        String title = "";
        String label = "";
        for(Object o : info) {
            String str = (String)o;
            if(str.startsWith("label=")) {
                label =  str2filename(str.substring(6,Math.min(str.length(), 36)));
            }
            if(str.startsWith("title=")) {
                title =  str2filename(str.substring(6,Math.min(str.length(), 33)));
            }
        }
        filename += "/movie_" + title + "_" + label + ".m";
        
        new File(filename).getParentFile().mkdir();
        
        try (PrintStream out = new PrintStream(filename)) {
            boolean first = true;

            Set<Object> sortedEntries = new TreeSet<>((x, y) -> Integer.compare(((Entry) x).iteration, ((Entry) y).iteration));
            sortedEntries.addAll(log.getTagsOfType(Entry.class));

            for (Object entryObj : sortedEntries) {
                Entry entry = (Entry) entryObj;

                if (first) {
                    out.println("D=zeros(" + entry.global.getNumberOfStates() + ",0);");
                    out.println("T=zeros(" + entry.iterativeCost.getNumberOfStates() + ",0);");
                    first = false;
                }

                if (entry.iteration == 0) {
                    out.println("C=" + entry.costSignal + "';");
                }
                out.println("D(:," + (entry.iteration + 1) + ")=" + entry.global + "';");
                out.println("T(:," + (entry.iteration + 1) + ")=" + entry.iterativeCost + "';");
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(MovieLogger.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private class Entry {

        public int iteration;
        public Plan costSignal;
        public Plan global;
        public Plan iterativeCost;

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + this.iteration;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Entry other = (Entry) obj;
            if (this.iteration != other.iteration) {
                return false;
            }
            return true;
        }
    }
}