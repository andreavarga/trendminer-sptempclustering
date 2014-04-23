/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package trendminer.sptempclustering;

import cc.mallet.types.InstanceList;
import cc.mallet.util.CommandOption;
import edu.umass.cs.mallet.users.kan.topics.*;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;

/**
 *
 * @author andreavarga
 */

public class Main {
    
    static CommandOption.Integer nrTopics = new CommandOption.Integer(Main.class, "nrTopics",
            "number of clusters to be discovered",
            false,
            100,
            "parameter for DMR", null);

    static CommandOption.Integer nrIterations = new CommandOption.Integer(Main.class, "nrIterations",
            "for Gibbs sampling",false,
            1000,
            "number of iterations for gibbs sampling "
            + "  ", null);
    
    static CommandOption.Integer burnIn = new CommandOption.Integer(Main.class, "burnIn",
            "for Gibbs sampling",
            false,
            250,
            "burnin for DMR", null);
    
    static CommandOption.Double beta =
            new CommandOption.Double(Main.class,
            "beta", "beta values", false,
            0.01,
            "phi beta values for DMR",
            "");
    
    static CommandOption.Integer topWords = new CommandOption.Integer(Main.class, "topWords",
            "for displaying the results",
            false,
            10,
            "number of words to be displayed for each topic ", null);
    
    static CommandOption.Integer nrThreads = new CommandOption.Integer(Main.class, "nrThreads",
            "for Gibbs sampling",
            false,
            1,
            "burnin for DMR", null);    
    
    static CommandOption.String trainInstanceList =
            new CommandOption.String(Main.class,
            "trainInstanceList", "tokenacc|multiseg",
            true,
            "/Users/andreavarga/NetBeansProjects/trendminer-sptempclustering_dist/mallet.train",
            "training file to train DMR",
            null);

    static CommandOption.String testInstanceList =
            new CommandOption.String(Main.class,
            "testInstanceList", "",
            true,
            "/Users/andreavarga/NetBeansProjects/trendminer-sptempclustering_dist/mallet.test",
            "test file to evaluate DMR",
            null);

    static CommandOption.String outputFolder =
            new CommandOption.String(Main.class,
            "outputFolder", "",
            true,
            "/Users/andreavarga/NetBeansProjects/trendminer-sptempclustering_dist/output/",
            "output folder to store the results",
            null);
    
    public static final String CLASSPATH_SEPARATOR = System.getProperty("os.name").contains("indows") ? "\\" : "/";    
    
    public static void main(String[] args) {
        try {
            CommandOption.setSummary(Main.class,
                    "Main file for the spatio-temporal clustering experiments, "
                    + "developed from 15 December 2013 till 31 April 2014");
            CommandOption.process(Main.class, args);
            CommandOption.printOptionValues(Main.class);

            PrintWriter fResults_Perplexity = new PrintWriter(outputFolder.value + "perplexity.txt");
            PrintStream fResults_Topics = new PrintStream(outputFolder.value + "topics.txt");
            
            
            String sDMR_ParametersFile = outputFolder.value + "_parameters_" + CLASSPATH_SEPARATOR;
            
            File f = new File(sDMR_ParametersFile);
            //creating the _parameters_ subdir for the results
            if (!f.exists()){
                f.mkdirs();
            }

            //loading the trainign file
            InstanceList training = InstanceList.load(new File(trainInstanceList.value));
            
            System.out.println("Loaded training instances");
            
            DMRTopicModel lda = new DMRTopicModel(nrTopics.value);
            lda.setBurninPeriod(burnIn.value);

            lda.setTopicDisplay(50, topWords.value);
            lda.addInstances(training);
            lda.setOptimizeInterval(50);
            lda.setNumThreads(nrThreads.value);
            lda.setNumIterations(nrIterations.value);
            System.out.println("Before estimating");
            lda.estimate();

            System.out.println("printing parameters");
            lda.writeParameters_TrendMinerFormat(new File(sDMR_ParametersFile));
            System.out.println("finished parameters");
            
            System.out.println("printing top words/topics");
            fResults_Topics.print(lda.displayTopWords(topWords.value, false));

            if (testInstanceList.value.length() > 0) {
                edu.umass.cs.mallet.users.kan.topics.MarginalProbEstimator evaluator = lda.getProbEstimator();

                InstanceList instances = InstanceList.load(new File(testInstanceList.value));
                System.out.println("Loaded test instances");
                
                PrintStream docProbabilityStream = new PrintStream(outputFolder.value + "docProbabilityFile.txt");
                double dTotalLogLikeliHood = evaluator.evaluateLeftToRight(instances, 10, false,
                        docProbabilityStream);
                docProbabilityStream.close();
                
                int iTotalWords = 0;
                PrintStream doclengthsStream = new PrintStream(outputFolder.value + "doclengths.txt");

                for (Instance instance : instances) {
                    if (!(instance.getData() instanceof FeatureSequence)) {
                        System.err.println("DocumentLengths is only applicable to FeatureSequence objects "
                                + "(use --keep-sequence when importing)");
                        System.exit(1);
                    }

                    FeatureSequence words = (FeatureSequence) instance.getData();
                    doclengthsStream.println(words.size());
                    iTotalWords += words.size();
                }
                doclengthsStream.close();
                double dPerplexity = Math.exp((-1.0 * dTotalLogLikeliHood) / iTotalWords);
                System.out.println("Perplexity:" + dPerplexity);
                fResults_Perplexity.println("TotalLogLikeliHood,iTotalWords,Perplexity");
                fResults_Perplexity.print(-1.0 * dTotalLogLikeliHood + ",");
                fResults_Perplexity.print(iTotalWords + ",");
                fResults_Perplexity.println(Double.toString(dPerplexity));
            }

            fResults_Perplexity.close();
            fResults_Topics.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}