package macrobase;

import com.google.gson.Gson;
import macrobase.classifier.KDEClassifier;
import macrobase.classifier.KNNBoundEstimator;
import macrobase.conf.BenchmarkConf;
import macrobase.conf.TreeKDEConf;
import macrobase.data.CSVDataSource;
import macrobase.kde.TreeKDE;
import macrobase.knn.TreeKNN;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class KDEApp {
    private static final Logger log = LoggerFactory.getLogger(KDEApp.class);

    public static BenchmarkConf benchmarkConf;
    public static String outputPath;

    public static List<double[]> load(String[] args) throws Exception {
        String confPath = "conf/conf.yaml";
        if (args.length > 0) {
            confPath = args[0];
        }
        if (args.length > 1) {
            outputPath = args[1];
        }
        benchmarkConf = BenchmarkConf.load(confPath);

        CSVDataSource ds;
        if (benchmarkConf.inputColumns == null) {
            ds = new CSVDataSource(
                    benchmarkConf.inputPath,
                    benchmarkConf.startColumn,
                    benchmarkConf.endColumn);
        } else {
            ds = new CSVDataSource(
                    benchmarkConf.inputPath,
                    benchmarkConf.inputColumns);
        }
        StopWatch sw = new StopWatch();
        sw.start();
        List<double[]> metrics = ds
                .setLimit(benchmarkConf.inputRows)
                .get();
        sw.stop();
        log.info("Loaded "+metrics.size()+" in "+sw.toString());

        return metrics;
    }

    public static class BenchmarkNumbers {
        public String algorithm = "ic2";
        public String dataset;
        public int dim;
        public int num_train;
        public int num_test;
        public long train_time;
        public long test_time;
        public long num_kernels;
    }

    public static double processKDE(List<double[]> metrics) throws Exception {
        TreeKDEConf tConf = benchmarkConf.tKDEConf;

        BenchmarkNumbers bn = new BenchmarkNumbers();
        bn.dataset = benchmarkConf.inputPath;
        bn.dim = metrics.get(0).length;
        bn.num_train = metrics.size();

        StopWatch sw = new StopWatch();
        KDEClassifier classifier = new KDEClassifier(tConf);
        sw.reset();
        sw.start();
        classifier.train(metrics);
        sw.stop();
        long trainTime = sw.getTime();
        bn.train_time = trainTime;
        log.info("Trained in: {}", sw.toString());
        if (tConf.bwValue > 0) {
            log.info("BW: {}", classifier.bandwidth[0]);
        } else {
            log.info("BW: {}", Arrays.toString(classifier.bandwidth));
        }
        log.info("CutoffH: {}, CutoffL: {} Tolerance: {}",
                classifier.cutoffH,
                classifier.cutoffL,
                classifier.tolerance);

        sw.reset();
        sw.start();
        int densitySize = Math.min(metrics.size(), benchmarkConf.numToScore);
        double[] densities = new double[densitySize];
        long numKernels = 0;
        int actualNumScored = 0;
        double maxTime = Double.MAX_VALUE;
        if (benchmarkConf.timeToScore > 0.0) {
            maxTime = benchmarkConf.timeToScore;
        }
        for (int i = 0; i < benchmarkConf.numToScore; i++) {
            int modI = i % densities.length;
            densities[modI] = classifier.density(metrics.get(modI));
            numKernels += classifier.getNumKernels();
            actualNumScored++;
            if (sw.getTime()/1000.0 > maxTime) {
                break;
            }
        }
        sw.stop();
        long scoreTime = sw.getTime();
        if (classifier.kde instanceof TreeKDE) {
            ((TreeKDE) classifier.kde).showDiagnostics();
        }
        bn.num_kernels = numKernels;
        bn.test_time = scoreTime;
        bn.num_test = actualNumScored;

        log.info("Scored in {}", sw.toString());
        log.info("Scored @ {} / s",
                (float)actualNumScored * 1000/(scoreTime));
        log.info("Total Processing: {}", (double)(trainTime+scoreTime)/1000);
        log.info("Avg # Kernels: {}", (double)numKernels/actualNumScored);

        if (outputPath != null) {
            BufferedWriter out = Files.newBufferedWriter(Paths.get(outputPath));
            out.write("density\n");
            for (double d : densities) {
                out.write(Double.toString(d)+"\n");
            }
            out.close();
        }

        int numDensitiesToCopy = Math.min(actualNumScored, densitySize);
        double[] actualDensities = new double[numDensitiesToCopy];
        System.arraycopy(densities, 0, actualDensities, 0, numDensitiesToCopy);
        Arrays.sort(actualDensities);
        int expectedNumOutliers = (int)(actualDensities.length * tConf.percentile);
        double quantile = actualDensities[expectedNumOutliers];
        log.info("{} percentile: {}", tConf.percentile, quantile);

        Gson gs = new Gson();
        System.out.println("Parsed Output:");
        System.out.println(gs.toJson(bn));
        return quantile;
    }

    public static double processKNN(List<double[]> metrics) throws Exception {
        TreeKDEConf tConf = benchmarkConf.tKDEConf;

        StopWatch sw = new StopWatch();
        sw.reset();
        sw.start();
        KNNBoundEstimator qEstimator = new KNNBoundEstimator(tConf);
        qEstimator.estimateQuantiles(metrics);

        TreeKNN tKNN = new TreeKNN(tConf.k)
                .setBandwidth(qEstimator.bw)
                .setBounds(qEstimator.dL, qEstimator.dH)
                .setTree(qEstimator.tree)
                .train(metrics);
        sw.stop();
        long trainTime = sw.getTime();
        log.info("Trained in: {}", sw.toString());
        log.info("BW: {}", Arrays.toString(tKNN.bw));
        log.info("dL: {}, dH: {}", tKNN.dLB, tKNN.dHB);

        sw.reset();
        sw.start();
        int numDistances = Math.min(metrics.size(), benchmarkConf.numToScore);
        double[] distances = new double[numDistances];
        for (int i = 0; i < benchmarkConf.numToScore; i++) {
            int modI = i % distances.length;
            distances[modI] = tKNN.score(metrics.get(modI));
        }
        sw.stop();
        long scoreTime = sw.getTime();
        log.info("Scored in {}", sw.toString());
        log.info("Scored @ {} / s",
                (float)benchmarkConf.numToScore * 1000/(scoreTime));
        log.info("Total Processing: {}", (double)(trainTime+scoreTime)/1000);

        if (outputPath != null) {
            BufferedWriter out = Files.newBufferedWriter(Paths.get(outputPath));
            out.write("distance\n");
            for (double d : distances) {
                out.write(Double.toString(d)+"\n");
            }
            out.close();
        }

        Arrays.sort(distances);
        int expectedNumOutliers = (int)(distances.length * tConf.percentile);
        double quantile = distances[distances.length - expectedNumOutliers];
        log.info("{} percentile: {}", tConf.percentile, quantile);

        return quantile;
    }


    public static void main(String[] args) throws Exception {
        List<double[]> metrics = load(args);

        if (benchmarkConf.tKDEConf.algorithm == TreeKDEConf.Algorithm.TREEKNN) {
            processKNN(metrics);
        } else {
            processKDE(metrics);
        }
   }
}
