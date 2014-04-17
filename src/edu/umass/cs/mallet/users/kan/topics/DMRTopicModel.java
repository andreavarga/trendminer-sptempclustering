/*=============================================================================
  DMRTopicModel.java
                                    Created by jkan on Apr 21, 2010
                                    Copyright (c)2010 Essbare Weichware, GmbH
                                    All rights reserved.
  =============================================================================*/

package edu.umass.cs.mallet.users.kan.topics;

//import AndreaHelper.FileReader_WriterHelper;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

import cc.mallet.classify.MaxEnt;
import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.OptimizationException;
import cc.mallet.pipe.Noop;
import cc.mallet.pipe.Pipe;
import cc.mallet.topics.DMROptimizable;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureCounter;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.Randoms;


public class DMRTopicModel extends ParallelTopicModel {
    
    private static final long serialVersionUID = 1;
    MaxEnt dmrParameters;
    int numFeatures;
    int defaultFeatureIndex;
    Pipe parameterPipe = null;

    //	double[][] alphaCache;
    //  double[] alphaSumCache;
    // these don't seem to be used....
    public DMRTopicModel(int numberOfTopics) {
        super(numberOfTopics);
        
    }
    public DMRTopicModel(int numberOfTopics, double alphaSum, double beta) {
        super(numberOfTopics, alphaSum, beta);

    }

    @Override
    public void addInstances(InstanceList training) {

        super.addInstances(training);

        numFeatures = data.get(0).instance.getTargetAlphabet().size() + 1;
        defaultFeatureIndex = numFeatures - 1;

//		int numDocs = data.size(); // TODO consider beginning by sub-sampling?

//		alphaCache    = new double[numDocs][numTopics];
//      alphaSumCache = new double[numDocs];
    }

    @Override
    protected WorkerRunnable makeWorkerRunnable(int offset, int docsPerThread) {
        TypeTopicCounts typeTopicCounts = this.typeTopicCounts;
        int[] tokensPerTopic = this.tokensPerTopic;
        double[] alpha = this.alpha;

        // If there is only one thread, copy the typeTopicCounts
        //  arrays directly, rather than allocating new memory.
        // DMR also needs to copy the alpha array, since they are modified on a per-document basis.

        if (numThreads > 1) // otherwise, make a copy for the thread
        {
            typeTopicCounts = new TypeTopicCounts(typeTopicCounts);
            tokensPerTopic = Arrays.copyOf(tokensPerTopic, tokensPerTopic.length);
            alpha = Arrays.copyOf(alpha, alpha.length);
        }

        WorkerRunnable runnable = new DMRWorkerRunnable(
                alpha, beta,
                makeRandom(), data,
                typeTopicCounts, tokensPerTopic,
                offset, docsPerThread);

        runnable.initializeAlphaStatistics(docLengthCounts.length);

        return runnable;
    }

    /**
     * For the DMRTopicModel, optimizing alphas means training regression parameters
     * (was "learnParameters()")
     *
     *  @see edu.umass.cs.mallet.users.kan.topics.ParallelTopicModel#optimizeAlpha(edu.umass.cs.mallet.users.kan.topics.WorkerRunnable[])
     **/
    @Override
    public void optimizeAlpha() {

        // Create a "fake" pipe with the features in the data and 
        //  a trove int-int hashmap of topic counts in the target.

        if (parameterPipe == null) {
            parameterPipe = new Noop();

            parameterPipe.setDataAlphabet(data.get(0).instance.getTargetAlphabet());
            parameterPipe.setTargetAlphabet(topicAlphabet);
        }

        InstanceList parameterInstances = new InstanceList(parameterPipe);

        if (dmrParameters == null) {
            dmrParameters = new MaxEnt(parameterPipe, new double[numFeatures * numTopics]);
        }

        for (TopicAssignment ta : data) {

            if (ta.instance.getTarget() == null) {
                continue;
            }

            FeatureCounter counter = new FeatureCounter(topicAlphabet);

            for (int topic : ta.topicSequence.getFeatures()) {
                counter.increment(topic);
            }

            // Put the real target in the data field, and the
            //  topic counts in the target field
            parameterInstances.add(new Instance(ta.instance.getTarget(), counter.toFeatureVector(), null, null));
        }

        DMROptimizable optimizable = new DMROptimizable(parameterInstances, dmrParameters);
        optimizable.setRegularGaussianPriorVariance(0.5);
        optimizable.setInterceptGaussianPriorVariance(100.0);

        LimitedMemoryBFGS optimizer = new LimitedMemoryBFGS(optimizable);

        // Optimize once
        try {
            optimizer.optimize();
        } catch (OptimizationException e) {
            // step size too small
        }

        // Restart with a fresh initialization to improve likelihood
        try {
            optimizer.optimize();
        } catch (OptimizationException e) {
            // step size too small
        }
        dmrParameters = optimizable.getClassifier();

//        cacheAlphas();
    }

//	private void cacheAlphas() {
//		
//		for (int doc=0; doc < data.size(); doc++) {
//            Instance instance = data.get(doc).instance;
//            //FeatureSequence tokens = (FeatureSequence) instance.getData();
//            if (instance.getTarget() == null) { continue; }
//            //int numTokens = tokens.getLength();
//
//            alphaSumCache[doc] = setAlphasFromDocFeatures(alphaCache[doc], instance, this.dmrParameters, this.numFeatures, this.defaultFeatureIndex);
//        }
//	}
    /**
     *  Set alpha based on features in an instance
     *
     *  @param alpha		the array of alpha values to set (out parameter)
     *  @param instance		the instance from which to read the features
     *  @return the sum of the resulting alphas
     */
    static double setAlphasFromDocFeatures(double[] alpha, Instance instance, MaxEnt dmrParameters, int numFeatures, int defaultFeatureIndex) {

        // we can't use the standard score functions from MaxEnt,
        //  since our features are currently in the Target.
        FeatureVector features = (FeatureVector) instance.getTarget();
        if (features == null) {
            return setAlphasWithoutDocFeatures(alpha, dmrParameters, numFeatures, defaultFeatureIndex);
        }

        double[] parameters = dmrParameters.getParameters();
        double alphaSum = 0.0;

        for (int topic = 0; topic < alpha.length; topic++) {
            alpha[topic] = parameters[topic * numFeatures + defaultFeatureIndex]
                    + MatrixOps.rowDotProduct(parameters,
                    numFeatures,
                    topic, features,
                    defaultFeatureIndex,
                    null);

            alpha[topic] = Math.exp(alpha[topic]);
            alphaSum += alpha[topic];
        }
        return alphaSum;
    }

    /**
     *  Use only the default features to set the topic prior (use no document features)
     *
     *  @param alpha		the array of alpha values to set (out parameter)
     *  @return the sum of the resulting alphas
     */
    static double setAlphasWithoutDocFeatures(double[] alpha, MaxEnt dmrParameters, int numFeatures, int defaultFeatureIndex) {

        double[] parameters = dmrParameters.getParameters();
        double alphaSum = 0.0;

        // Use only the default features to set the topic prior (use no document features)
        for (int topic = 0; topic < alpha.length; topic++) {
            alpha[topic] = Math.exp(parameters[(topic * numFeatures) + defaultFeatureIndex]);
            alphaSum += alpha[topic];
        }
        return alphaSum;
    }

    public void printTopWords(PrintStream out, int numWords, boolean usingNewLines) {
        if (dmrParameters != null) {
            setAlphasWithoutDocFeatures(alpha, dmrParameters, numFeatures, defaultFeatureIndex);
        }
        super.printTopWords(out, numWords, usingNewLines);
    }

    public void writeParameters(File parameterFile) throws IOException {
        if (dmrParameters != null) {
            PrintStream out = new PrintStream(parameterFile);
            dmrParameters.print(out);
            out.close();
        }
    }

    public void writeParameters_Andrea(File parameterFile) throws IOException {
        if (dmrParameters != null) {
//            dmrParameters.print_SeparateFiles_Andrea(parameterFile.getParent().toString());
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(this.dmrParameters);
        out.writeInt(this.numFeatures);
        out.writeInt(this.defaultFeatureIndex);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        this.dmrParameters = (MaxEnt) in.readObject();
        this.numFeatures = in.readInt();
        this.defaultFeatureIndex = in.readInt();
    }

    public MaxEnt getRegressionParameters() {
        return dmrParameters;
    }

    class DMRWorkerRunnable extends WorkerRunnable {

        DMRWorkerRunnable(double[] alpha, double beta,
                Randoms random,
                ArrayList<TopicAssignment> data,
                TypeTopicCounts runnableCounts,
                int[] tokensPerTopic,
                int startDoc, int numDocs) {

            super(alpha, beta, random, data, runnableCounts, tokensPerTopic, startDoc, numDocs);
        }

        @Override
        protected void prepareToSample() {
            if (dmrParameters == null) { // before we start doing regression, behave like normal LDA
                super.prepareToSample();
            }
            // in normal LDA, this recalculates the smoothingOnlyMass and cachedCoefficients
            // but after we start regression, we have to do this with every doc since the alphas
            //   are different for every doc, so we can skip it here.
        }

        @Override
        protected void sampleTopicsForOneDoc(TopicAssignment document, boolean readjustTopicsAndStats) {

            if (dmrParameters != null) {
                // set the alphas for each doc before sampling
                setAlphasFromDocFeatures(this.alpha, document.instance, dmrParameters, numFeatures, defaultFeatureIndex);
                this.smoothingOnlyMass =
                        initSmoothingOnlyMassAndCachedCoefficients(this.cachedCoefficients,
                        this.alpha, this.beta, this.betaSum, this.tokensPerTopic);
            }
            super.sampleTopicsForOneDoc(document, readjustTopicsAndStats);
        }
    }

    public static class DMRTopicInferencer extends TopicInferencer {

        private static final long serialVersionUID = 1L;
        MaxEnt dmrParameters;
        int numFeatures;
        int defaultFeatureIndex;

        public DMRTopicInferencer(
                TypeTopicCounts typeTopicCounts, int[] tokensPerTopic, Alphabet alphabet,
                double[] alpha, double beta, double betaSum,
                final MaxEnt dmrParameters, final int numFeatures, final int defaultFeatureIndex) {
            super(typeTopicCounts, tokensPerTopic, alphabet, alpha, beta, betaSum);

            this.dmrParameters = dmrParameters;
            this.numFeatures = numFeatures;
            this.defaultFeatureIndex = defaultFeatureIndex;
        }

        /**
        @see edu.umass.cs.mallet.users.kan.topics.TopicInferencer#getSampledDistribution(cc.mallet.types.Instance, int, int, int)
         **/
        @Override
        public double[] getSampledDistribution(Instance instance, int numIterations, int thinning, int burnIn) {
            if (this.dmrParameters != null) {
                setAlphasFromDocFeatures(this.alpha, instance, this.dmrParameters, this.numFeatures, this.defaultFeatureIndex);
                this.smoothingOnlyMass =
                        WorkerRunnable.initSmoothingOnlyMassAndCachedCoefficients(this.cachedCoefficients,
                        this.alpha, this.beta, this.betaSum, this.tokensPerTopic);
            }
            return super.getSampledDistribution(instance, numIterations, thinning, burnIn);
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeObject(this.dmrParameters);
            out.writeInt(this.numFeatures);
            out.writeInt(this.defaultFeatureIndex);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            this.dmrParameters = (MaxEnt) in.readObject();
            this.numFeatures = in.readInt();
            this.defaultFeatureIndex = in.readInt();
        }
    }

    /**
    @see edu.umass.cs.mallet.users.kan.topics.ParallelTopicModel#getInferencer()
     **/
    @Override
    public TopicInferencer getInferencer() {
        return new DMRTopicInferencer(typeTopicCounts, tokensPerTopic,
                data.get(0).instance.getDataAlphabet(),
                alpha, beta, betaSum,
                this.dmrParameters, this.numFeatures, this.defaultFeatureIndex);
    }

    public static class DMRProbEstimator extends MarginalProbEstimator {

        private static final long serialVersionUID = 1L;
        MaxEnt dmrParameters;
        int numFeatures;
        int defaultFeatureIndex;

        public DMRProbEstimator(int numTopics, double[] alpha, double alphaSum, double beta,
                TypeTopicCounts typeTopicCounts, int[] tokensPerTopic, Logger logger,
                final MaxEnt dmrParameters, final int numFeatures, final int defaultFeatureIndex) {
            super(numTopics, alpha, alphaSum, beta, typeTopicCounts, tokensPerTopic, logger);

            this.dmrParameters = dmrParameters;
            this.numFeatures = numFeatures;
            this.defaultFeatureIndex = defaultFeatureIndex;
        }

        /**
        @see edu.umass.cs.mallet.users.kan.topics.MarginalProbEstimator#evaluateLeftToRight(cc.mallet.types.Instance, boolean, double, double[][])
         **/
        @Override
        protected double evaluateLeftToRight(Instance instance, boolean usingResampling, double logNumParticles,
                double[][] particleProbabilities) {
            if (this.dmrParameters != null) {
                setAlphasFromDocFeatures(this.alpha, instance, this.dmrParameters, this.numFeatures, this.defaultFeatureIndex);
                this.smoothingOnlyMass =
                        WorkerRunnable.initSmoothingOnlyMassAndCachedCoefficients(this.cachedCoefficients,
                        this.alpha, this.beta, this.betaSum, this.tokensPerTopic);
            }
            return super.evaluateLeftToRight(instance, usingResampling, logNumParticles, particleProbabilities);
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeObject(this.dmrParameters);
            out.writeInt(this.numFeatures);
            out.writeInt(this.defaultFeatureIndex);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            this.dmrParameters = (MaxEnt) in.readObject();
            this.numFeatures = in.readInt();
            this.defaultFeatureIndex = in.readInt();
        }
    }

    /**
    @see edu.umass.cs.mallet.users.kan.topics.ParallelTopicModel#getProbEstimator()
     **/
    @Override
    public MarginalProbEstimator getProbEstimator() {
        return new DMRProbEstimator(numTopics, alpha, alphaSum, beta,
                typeTopicCounts, tokensPerTopic, logger,
                dmrParameters, numFeatures, defaultFeatureIndex);
    }
}
