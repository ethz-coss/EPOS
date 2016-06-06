/*
 * Copyright (C) 2015 Evangelos Pournaras
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
package experiments;

import experiments.output.MatlabEvaluator;
import experiments.output.IEPOSEvaluator;
import agents.network.TreeArchitecture;
import agents.*;
import agents.fitnessFunction.*;
import agents.dataset.PlanGenerator;
import dsutil.generic.RankPriority;
import dsutil.protopeer.services.topology.trees.DescriptorType;
import dsutil.protopeer.services.topology.trees.TreeType;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joda.time.DateTime;
import protopeer.measurement.MeasurementFileDumper;
import protopeer.measurement.MeasurementLog;
import tree.BalanceType;
import util.Util;
import agents.dataset.Dataset;
import agents.log.AgentLogger;
import agents.log.DetailLogger;
import agents.log.MovieLogger;
import agents.log.ProgressIndicator;
import experiments.parameters.AgentFactoryParam;
import experiments.parameters.BooleanParam;
import experiments.parameters.CostSignalParam;
import experiments.parameters.DatasetParam;
import experiments.parameters.Init;
import experiments.parameters.EnumParam;
import experiments.parameters.Initializer;
import experiments.parameters.InitializerMap;
import experiments.parameters.AggregatorParam;
import experiments.parameters.CostFunctionParam;
import experiments.parameters.FitnessFunctionParam;
import experiments.parameters.LazyMap;
import experiments.parameters.OptPosDoubleParam;
import experiments.parameters.PosIntParam;
import experiments.parameters.RankGeneratorParam;
import experiments.parameters.StringParam;
import experiments.output.IEPOSVisualizer;
import experiments.output.JFreeChartEvaluator;
import java.util.LinkedHashMap;
import protopeer.Experiment;

/**
 * @author Peter
 */
public class ConfigurableExperiment extends ExperimentLauncher implements Cloneable, Runnable {

    private AgentFactory agentFactory;
    private int numUser;
    private String title;
    private String label;
    private TreeArchitecture architecture;
    private PlanGenerator planGenerator;
    private Dataset dataset;

    private static String currentConfig = null;
    private static ConfigurableExperiment launcher;
    private static String outFile = null;
    private static boolean showGraph = false;

    private static LazyMap lazyInit = new LazyMap();

    private static String getConfigFile(String[] args) {
        if (args.length > 0) {
            return args[0];
        } else {
            return "experiments/default.cfg";
        }
    }

    private static String initPeersLog(String configFile) {
        File peersLogDir = new File(configFile);
        String peersLog = "peersLog/" + peersLogDir.getName().substring(0, peersLogDir.getName().indexOf('.'));
        peersLogDir = new File(peersLog);
        peersLogDir.mkdirs();
        Util.clearDirectory(peersLogDir);

        new File("output-data").mkdir();

        return peersLog;
    }

    private static Properties loadConfig(String configFile) {
        Properties config = new Properties();
        try (Reader configReader = new FileReader(configFile)) {
            config.load(configReader);
        } catch (IOException ex) {
            Logger.getLogger(ConfigurableExperiment.class.getName()).log(Level.SEVERE, null, ex);
        }
        return config;
    }

    public static void main(String[] args) {
        long t0 = System.currentTimeMillis();

        String configFile = getConfigFile(args);
        String peersLog = initPeersLog(configFile);
        Properties properties = loadConfig(configFile);
        IEPOSEvaluator evaluator = new MatlabEvaluator();
        //IEPOSEvaluator evaluator = new JFreeChartEvaluator();

        launcher = new ConfigurableExperiment();
        launcher.architecture = new TreeArchitecture();

        InitializerMap initializer = new InitializerMap();
        initializer.put("out", x -> outFile = x, new StringParam());
        initializer.put("numExperiments", x -> launcher.numExperiments = x, new PosIntParam());
        initializer.put("numUser", x -> launcher.numUser = x, new PosIntParam());
        initializer.put("dataset", x -> launcher.dataset = x, new DatasetParam());
        initializer.put("costSignal", x -> launcher.planGenerator = x, new CostSignalParam());
        initializer.put("architecture.priority", x -> launcher.architecture.priority = x, new EnumParam<>(RankPriority.class));
        initializer.put("architecture.rank", x -> launcher.architecture.rank = x, new EnumParam<>(DescriptorType.class));
        initializer.put("architecture.type", x -> launcher.architecture.type = x, new EnumParam<>(TreeType.class));
        initializer.put("architecture.balance", x -> launcher.architecture.balance = x, new EnumParam<>(BalanceType.class));
        initializer.put("architecture.rankGenerator", x -> launcher.architecture.rankGenerator = x, new RankGeneratorParam());
        initializer.put("architecture.maxChildren", x -> launcher.architecture.maxChildren = x, new PosIntParam());
        initializer.put("agentFactory", x -> launcher.agentFactory = x, new AgentFactoryParam());
        initializer.put("showGraph", x -> showGraph = x, new BooleanParam());
        initializer.put("inMemory", x -> launcher.agentFactory.inMemory = launcher.inMemoryLog = x, new BooleanParam(), 1);
        initializer.put("outputMovie", x -> launcher.agentFactory.addLogger("movie", x ? new MovieLogger() : null), new BooleanParam(), 1);
        initializer.put("outputDetail", x -> launcher.agentFactory.addLogger("detail", x ? new DetailLogger() : null), new BooleanParam(), 1);
        initializer.put("numIterations", x -> launcher.agentFactory.numIterations = x, new PosIntParam(), 1);
        initializer.put("measure", x -> launcher.agentFactory.measure = x, new CostFunctionParam(), 1);
        initializer.put("localMeasure", x -> launcher.agentFactory.localMeasure = x, new CostFunctionParam(), 1);
        initializer.put("aggregator", x -> launcher.agentFactory.aggregator = x, new AggregatorParam(), 1);
        initializer.put("rampUpRate", x -> launcher.agentFactory.rampUpRate = x, new OptPosDoubleParam(), 1);
        initializer.put("rampUpBias", x -> ((IterMinCost) launcher.agentFactory.fitnessFunction).rampUpBias = x, new OptPosDoubleParam(), 1);
        initializer.put("fitnessFunction", x -> currentConfig = x, new StringParam());

        lazyInit.put("runDuration", e -> launcher.runDuration = 3 + launcher.agentFactory.numIterations, 2);
        lazyInit.put("showProgress", e -> launcher.agentFactory.addLogger("progress", new ProgressIndicator()), 2);
        lazyInit.put("peersLog", e -> launcher.peersLog = peersLog + "/Experiment " + System.currentTimeMillis(), 2);

        List<Init> init = new ArrayList<>();
        List<Init> outer = new ArrayList<>();
        List<Init> inner = new ArrayList<>();
        Map<String, List<IterativeFitnessFunction>> ffConfigs = new HashMap<>();

        for (Map.Entry property : properties.entrySet()) {
            String var = (String) property.getKey();
            String propertyName = var.substring(var.indexOf('-') + 1);
            List<Init> target;
            if (var.startsWith("init")) {
                target = init;
            } else if (var.startsWith("plot")) {
                target = outer;
            } else if (var.startsWith("comp")) {
                target = inner;
            } else if (var.startsWith("list")) {
                ffConfigs.put(propertyName, Arrays.asList(
                        Arrays.stream(((String) property.getValue()).split("\\),"))
                        .map(s -> new FitnessFunctionParam().get(s))
                        .toArray(num -> new IterativeFitnessFunction[num])));
                continue;
            } else {
                throw new IllegalArgumentException("Invalid prefix for " + var);
            }

            if (initializer.containsKey(propertyName)) {
                Initializer i = initializer.get(propertyName);
                target.add(new Init<>(s -> i.init(propertyName, s, lazyInit), Util.trimSplit((String) property.getValue(), ",")));
            } else {
                throw new IllegalArgumentException("Property " + var + " not supported");
            }
        }

        inner.add(new Init<>((o) -> {
            lazyInit.put("fitnessFunction", e -> launcher.agentFactory.fitnessFunction = o, 0);
        }, () -> {
            return ffConfigs.get(currentConfig).iterator();
        }));

        for (Init d : init) {
            d.setter.accept(d.values.iterator().next());
        }

        try (PrintStream out = outFile == null ? System.out : new PrintStream(outFile)) {
            int plotNumber = 0;

            // outer loops
            List<Iterator<? extends Object>> outerState = Util.repeat(outer.size(), (Iterator<? extends Object>) null);
            List<String> outerName = Util.repeat(outer.size(), (String) null);
            while (true) {
                boolean boi = true, eoi = false; // begin/end of iteration
                for (int i = 0; i < outer.size() && (boi || eoi); i++) {
                    if ((boi = outerState.get(i) == null) || (eoi = !outerState.get(i).hasNext())) {
                        outerState.set(i, outer.get(i).values.iterator());
                    }
                    Object obj = outerState.get(i).next();
                    outer.get(i).setter.accept(obj);
                    outerState.set(i, outerState.get(i));
                    outerName.set(i, obj == null ? null : obj.toString());
                }
                if (eoi) { // all dimensions explored (iterator at the end)
                    break;
                }

                // init plot
                Map<String, MeasurementLog> experiments = new LinkedHashMap<>();
                String title = Util.merge(outerName);
                plotNumber++;

                // inner loops
                List<Iterator<? extends Object>> innerState = Util.repeat(inner.size(), (Iterator<? extends Object>) null);
                List<String> innerName = Util.repeat(inner.size(), (String) null);
                while (true) {
                    boi = true;
                    eoi = false; // begin/end of iteration
                    for (int i = 0; i < inner.size() && (boi || eoi); i++) {
                        if ((boi = innerState.get(i) == null) || (eoi = !innerState.get(i).hasNext())) {
                            innerState.set(i, inner.get(i).values.iterator());
                        }
                        Object obj = innerState.get(i).next();
                        inner.get(i).setter.accept(obj);
                        innerState.set(i, innerState.get(i));
                        innerName.set(i, obj == null ? null : obj.toString());
                    }
                    if (eoi) { // all dimensions explored (iterator at the end)
                        break;
                    }

                    // do lazyPriority initializations
                    for (Consumer<?> c : lazyInit.values()) {
                        c.accept(null);
                    }

                    // perform experiment
                    launcher.title = title;
                    launcher.label = Util.merge(innerName);
                    launcher.run();

                    if (showGraph) {
                        IEPOSVisualizer visualizer = IEPOSVisualizer.create(launcher.getMeasurementLog());
                        visualizer.showGraph();
                    }

                    experiments.put(launcher.peersLog, launcher.getMeasurementLog());

                    for(AgentLogger logger : launcher.agentFactory.getLoggers()) {
                        logger.print(launcher.getMeasurementLog());
                    }
                }

                // plot result
                evaluator.evaluateLogs(plotNumber, experiments, out);
            }

            long t1 = System.currentTimeMillis();
            System.out.println("%" + (t1 - t0) / 1000 + "s");
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ConfigurableExperiment.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        MeasurementLog log = new MeasurementLog();
        log.log(1, "title=" + title, 0);
        log.log(1, "label=" + label, 0);

        super.run(log);
    }

    @Override
    public IEPOSExperiment createExperiment(int num) {
        System.out.println("%Experiment " + getName(num) + ":");

        dataset.init(num);

        File outFolder = new File(peersLog);
        IEPOSExperiment experiment = new IEPOSExperiment(
                num,
                dataset,
                outFolder,
                architecture,
                "", DateTime.parse("0001-01-01"),
                DateTime.parse("0001-01-01"), 5, numUser,
                agentFactory,
                planGenerator);

        return experiment;
    }

    private String getName(int num) {
        return title + " - " + label + " - " + num;
    }

    @Override
    public ConfigurableExperiment clone() {
        try {
            ConfigurableExperiment clone = (ConfigurableExperiment) super.clone();
            clone.agentFactory = agentFactory.clone();
            clone.architecture = architecture.clone();
            return clone;
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(ConfigurableExperiment.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
