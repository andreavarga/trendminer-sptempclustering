/*===========================================================================
  DMROptimizable.java
                               Created by kan on Dec 19, 2010
                               Copyright (c)2010 Intangible Industries, LLC
                               All rights reserved.
  ===========================================================================*/

package edu.umass.cs.mallet.users.kan.topics;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.logging.Logger;

import cc.mallet.classify.MaxEnt;
import cc.mallet.optimize.Optimizable;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.MatrixOps;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.MalletProgressMessageLogger;


/** This class implements the value and gradient functions for
 *   Dirichlet-multinomial Regression. See Guimaraes and Lindrooth, 
 *   for a general introduction to DMR, 
 *   and Mimno and McCallum (UAI, 2008) for an application to 
 *   multinomial mixture models.
 */
public class DMROptimizable implements Optimizable.ByBatchGradient, Optimizable.ByGradientValue 
{
     // A class that wraps up a MaxEnt classifier and its training data.
     // The result is an Optimizable function.
    
    private static Logger logger = MalletLogger.getLogger(DMROptimizable.class.getName());
    private static Logger progressLogger = MalletLogger.getLogger(DMROptimizable.class.getName()+"-pl");

    MaxEnt                classifier;
    InstanceList          trainingList;
    
    int numGetValueCalls = 0;
    int numGetValueGradientCalls = 0;
    int numIterations = Integer.MAX_VALUE;

    NumberFormat formatter = null;

    static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 1;
    static final double DEFAULT_LARGE_GAUSSIAN_PRIOR_VARIANCE = 100;
    static final double DEFAULT_GAUSSIAN_PRIOR_MEAN = 0.0;
        
    double gaussianPriorMean = DEFAULT_GAUSSIAN_PRIOR_MEAN;
    double gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;

    // Allowing the default feature (the base level) to 
    //  fluctuate more freely than the feature parameters leads
    //  to much better results.
    double defaultFeatureGaussianPriorVariance = DEFAULT_LARGE_GAUSSIAN_PRIOR_VARIANCE;

    double[]              parameters;
    
    int                   numLabels;
    int                   numFeatures;
    int                   defaultFeatureIndex;
    int                   numBatches;
    
    static class Cache {
        // The expectations are (temporarily) stored in the cachedGradient
        double[]              cachedGradient; 
                                    // this cached gradient seems always to be stale in both L-BFGS and SMA
                                    //  but it saves us from reallocating this array with every iteration

        double                cachedValue;
        boolean               cachedValueStale;
        //boolean               cachedGradientStale;
        
        public Cache(int numLabels, int numFeatures) {
            this.cachedGradient = new double [numLabels * numFeatures];
            this.invalidate();
        }
        
        public void invalidate() {
            logger.fine("invalidate cache");
            cachedValueStale    = true;
            //cachedGradientStale = true;
        }
    }
  
    Cache[]               cache;
    
    
    public DMROptimizable () {}

    public DMROptimizable (InstanceList instances, MaxEnt initialClassifier) {
        this(instances, 1, initialClassifier);
    }

	public DMROptimizable (InstanceList instances, int numBatches, MaxEnt initialClassifier) {

        this.trainingList = instances;
        Alphabet alphabet = instances.getDataAlphabet();
        Alphabet labelAlphabet = instances.getTargetAlphabet();

        this.numLabels = labelAlphabet.size();

        // Add one feature for the "default feature".
        this.numFeatures = alphabet.size() + 1; // add a spot for the intercept term
        
        logger.info("num features: " + numFeatures + " numLabels: " + numLabels);

        this.defaultFeatureIndex = numFeatures - 1;

        this.parameters = new double [numLabels * numFeatures];

        this.numBatches = numBatches;
        this.cache      = new Cache[this.numBatches];
        
        for (int i = 0; i < numBatches; ++i)
            this.cache[i] = new Cache(numLabels, numFeatures);
        
        //this.constraints = new double [numLabels * numFeatures];

        if (initialClassifier != null) {
            this.classifier = initialClassifier;
            this.parameters = classifier.getParameters();
            this.defaultFeatureIndex = classifier.getDefaultFeatureIndex();
            assert (initialClassifier.getInstancePipe() == instances.getPipe());
        }
        else if (this.classifier == null) {
            this.classifier =
                new MaxEnt (instances.getPipe(), parameters);
        }

		formatter = new DecimalFormat("0.###E0");

        // Initialize the constraints
        
        logger.fine("Number of instances in training list = " + trainingList.size());

		for (Instance instance : trainingList) {
			FeatureVector multinomialValues = (FeatureVector) instance.getTarget();

            if (multinomialValues == null)
                continue;
    
            FeatureVector features = (FeatureVector) instance.getData();
            assert (features.getAlphabet() == alphabet);
    
            boolean hasNaN = false;
    
            for (int i = 0; i < features.numLocations(); i++) {
                if (Double.isNaN(features.valueAtLocation(i))) {
                    logger.info("NaN for feature " + alphabet.lookupObject(features.indexAtLocation(i)).toString()); 
                    hasNaN = true;
                }
            }
    
            if (hasNaN) {
                logger.info("NaN in instance: " + instance.getName());
            }

        }

        //TestMaximizable.testValueAndGradientCurrentParameters (this);
    }

	/** Set the variance for the default features (aka intercept terms), generally 
	 *   larger than the variance for the regular features.
	 */
	public void setInterceptGaussianPriorVariance(double sigmaSquared) {
		this.defaultFeatureGaussianPriorVariance = sigmaSquared;
	}

	/** Set the variance for regular (non default) features, generally 
	 *   smaller than the variance for the default features.
	 */
	public void setRegularGaussianPriorVariance(double sigmaSquared) {
		this.gaussianPriorVariance = sigmaSquared;
	}

    public MaxEnt getClassifier () { return classifier; }
        
    public double getParameter (int index) {
        return parameters[index];
    }
        
    public void setParameter (int index, double v) {
        for (Cache c: this.cache)
            c.invalidate();
        parameters[index] = v;
    }
        
    public int getNumParameters() {
        return parameters.length;
    }
        
    public void getParameters (double[] buff) {
        if (buff == null || buff.length != parameters.length) {
        buff = new double [parameters.length];
        }
        System.arraycopy (parameters, 0, buff, 0, parameters.length);
    }
    
    public void setParameters (double [] buff) {
        assert (buff != null);
        for (Cache c: this.cache)
            c.invalidate();
        if (buff.length != parameters.length)
        parameters = new double[buff.length];
        System.arraycopy (buff, 0, parameters, 0, buff.length);
    }

    public InstanceList getInstanceList() {
        return this.trainingList;
    }

    
    public double getValue() {
        return getBatchValue(0, null);
    }
    
    
    /**
         @see cc.mallet.optimize.Optimizable.ByBatchGradient#getBatchValue(int, int[])
     **/
    public double getBatchValue(int batchIndex, int[] batchAssignments) {
        
        Cache c = this.cache[batchIndex];
        
        logger.fine("getBatchValue[" + batchIndex + "]: " + c.cachedValueStale);

        if (! c.cachedValueStale) { 
            return c.cachedValue; 
        }
        
        numGetValueCalls++;
		c.cachedValue = 0;

        // Incorporate likelihood of data
        double[] scores = new double[ trainingList.getTargetAlphabet().size() ];   // i.e. num topics
        double   value  = 0.0;

		int instanceIndex = 0;
            
		for (Instance instance: trainingList) {
		    
			FeatureVector multinomialValues = (FeatureVector) instance.getTarget();
            if (multinomialValues == null) {
                ++instanceIndex;
                continue; 
            }
            
            if ((batchAssignments != null)                              // null means single batch, so no skipping
                && (batchIndex != batchAssignments[instanceIndex])) {   // instance is not in current batch
                    ++instanceIndex;
                    continue;               // skip if not in current batch
            }
    
            //System.out.println("L Now "+inputAlphabet.size()+" regular features.");
            
            // Get the predicted probability of each class
            //   under the current model parameters
            this.classifier.getUnnormalizedClassificationScores(instance, scores);
    
            double sumScores = exponentiateScores(scores);
    
			// This is really an int, but since FeatureVectors are defined as doubles, 
			//  avoid casting.
			double totalLength = 0;

			for (int i = 0; i < multinomialValues.numLocations(); i++) {
				int label = multinomialValues.indexAtLocation(i);
				double count = multinomialValues.valueAtLocation(i);

                if (scores[label] == 0.0)
                {
                    logger.warning("topic " + label + " has a zero score:");
                    
                    printFeatures(System.out, label, instance);
                }
                
                value += (Dirichlet.logGammaStirling(scores[label] + count) -
                          Dirichlet.logGammaStirling(scores[label]));
                totalLength += count;
            }
    
            value -= (Dirichlet.logGammaStirling(sumScores + totalLength) -
                      Dirichlet.logGammaStirling(sumScores));
    
            // Error Checking:
            
            if (Double.isNaN(value)) {
				logger.fine (this.getClass().getSimpleName() + ": Instance " + instance.getName() +
                             " has NaN value.");
    
				for (int label: multinomialValues.getIndices()) {
                    logger.fine("\tlabel: " + label 
                                    + "\tlog(scores) = " + Math.log(scores[label]) 
                                    + "\tscores = " + scores[label]);
                }
            }
    
            if (Double.isInfinite(value)) {
				logger.warning ("Instance " + instance.getSource() + 
                        " has infinite value; skipping value and gradient");
                
                logInfiniteValueDetails(multinomialValues, scores, sumScores);
                
                c.cachedValue -= value;
                c.cachedValueStale = false;
                return -value;
            }
    
            //System.out.println(value);

			c.cachedValue += value;
                
			instanceIndex++;
        }

        // Incorporate prior on parameters

        double prior = 0;

        // The log of a gaussian prior is x^2 / -2sigma^2

        for (int label = 0; label < numLabels; label++) {
			for (int feature = 0; feature < numFeatures - 1; feature++) {
                double param = parameters[label*numFeatures + feature];
                prior -= (param - gaussianPriorMean) * (param - gaussianPriorMean) / (2 * gaussianPriorVariance);
            }
			double param = parameters[label*numFeatures + defaultFeatureIndex];
			prior -= (param - gaussianPriorMean) * (param - gaussianPriorMean) /
				(2 * defaultFeatureGaussianPriorVariance);
        }

		double labelProbability = c.cachedValue;
		c.cachedValue += prior;
		c.cachedValueStale = false;
		progressLogger.info ("Value["+batchIndex+"] (likelihood=" + formatter.format(labelProbability) +
							     " prior=" + formatter.format(prior) +
							     ") = " + formatter.format(c.cachedValue));

		return c.cachedValue;
    }

    /**
         @param multinomialValues
         @param scores
         @param sumScores
     **/
    private void logInfiniteValueDetails(FeatureVector multinomialValues, double[] scores, double sumScores)
    {
        double total = 0.0;
   
        for (int i = 0; i < multinomialValues.numLocations(); i++) {
            
            int label = multinomialValues.indexAtLocation(i);
            double count = multinomialValues.valueAtLocation(i);
            
            if (count > 0.0)
            {
                double v = Dirichlet.logGammaStirling(scores[label] + count) -
                           Dirichlet.logGammaStirling(scores[label]);
                
                if (Double.isInfinite(v))
                {
                    logger.warning("\tlabel: " + label 
                                 + "\tscore: " + scores[label]
                                 + "\tcount: " + count
                                 + "\tvalue: " + v
                                 );
                }
            }
            total += count;
        }
        logger.warning("\ttotal: " 
                     + "\tsumscores: " + sumScores
                     + "\ttotalLength: " + total
                     + "\tvalue: " + (Dirichlet.logGammaStirling(sumScores + total) -
                                      Dirichlet.logGammaStirling(sumScores))
                      );
    }

    private double exponentiateScores(double[] scores)
    {
        double sumScores = 0.0;
  
        // Exponentiate the scores
        for (int i=0; i<scores.length; i++) {
            // Due to underflow, it's very likely that some of these scores will be 0.0.
            //if (instanceIndex == 224) {
            //  System.out.println(scores[i] + "\t" + Math.exp(scores[i]));
            //}
            scores[i] = Math.exp(scores[i]);
            sumScores += scores[i];
        }
        return sumScores;
    }

    public void printFeatures(PrintStream pw, int label, Instance instance)
    {
        FeatureVector fv    = (FeatureVector) instance.getData();
         
        // default feature index (i.e. the intercept) 
        //  is the last one for each topic, so don't print that
        
        pw.print("(default)\t");
        pw.println(parameters[label*numFeatures + defaultFeatureIndex]);

        for (int i = 0; i < defaultFeatureIndex; i++)
        {
            if (fv.value(i) > 0.0)
            {
                pw.print(instance.getDataAlphabet().lookupObject(i));
                pw.print('\t');
                pw.println(parameters[label*numFeatures + i]);
            }
        }
    }

    public void getValueGradient(double[] buffer)
    {
        this.getBatchValueGradient(buffer, 0, null);
    }
    
    public void getBatchValueGradient(double[] buffer, int batchIndex, int[] batchAssignments)
    {
        Cache c = this.cache[batchIndex];

        logger.fine("getBatchValueGradient[" + batchIndex + "]");

        MatrixOps.setAll (c.cachedGradient, 0.0);

        // Incorporate likelihood of data
        double[] scores = new double[ trainingList.getTargetAlphabet().size() ];
        //double[] diffs  = new double[numLabels];

		//int instanceIndex = 0;
        
        assert(batchAssignments.length == trainingList.size());
        
        int instanceIndex = 0;
        
		for (Instance instance: trainingList) {

			FeatureVector multinomialValues = (FeatureVector) instance.getTarget();
 
            if (multinomialValues == null) {
                ++instanceIndex;
                continue; 
            }
            
            if ((batchAssignments != null)                              // null means single batch, so no skipping
                && (batchIndex != batchAssignments[instanceIndex])) {   // instance is not in current batch
                    ++instanceIndex;
                    continue;               // skip if not in current batch
            }
   
            // Get the predicted probability of each class
            //   under the current model parameters
            this.classifier.getUnnormalizedClassificationScores(instance, scores);
    
            double sumScores = exponentiateScores(scores);
    
            FeatureVector features = (FeatureVector) instance.getData();
    
			double totalLength = 0;
    
			for (double count : multinomialValues.getValues()) {
				totalLength += count;
            }
    
            double digammaDifferenceForSums = 
                Dirichlet.digamma(sumScores + totalLength) -
                Dirichlet.digamma(sumScores);
    
            for (int loc = 0; loc < features.numLocations(); loc++) {
                int index = features.indexAtLocation(loc);
                double value = features.valueAtLocation(loc);
                    
                if (value == 0.0) { continue; }

                // In a FeatureVector, there's no easy way to say "do you know
                //   about this id?" so I've broken this into two for loops,
                //  one for all labels, the other for just the non-zero ones.

                for (int label=0; label<numLabels; label++) {
                    c.cachedGradient[label * numFeatures + index] -=
                        value * scores[label] * digammaDifferenceForSums;
                }

                for (int labelLoc = 0; labelLoc <multinomialValues.numLocations(); labelLoc++) {
                    int label = multinomialValues.indexAtLocation(labelLoc);
                    double count = multinomialValues.valueAtLocation(labelLoc);

                    double diff = 0.0;
                            
                    if (count < 20) {
                        for (int i=0; i < count; i++) {
                            diff += 1 / (scores[label] + i);
                        }
                    }
                    else {
                        diff = Dirichlet.digamma(scores[label] + count) -
                            Dirichlet.digamma(scores[label]);
                    }

                    c.cachedGradient[label * numFeatures + index] +=
                        value * scores[label] * diff;

                }
            }
            // Now add the default feature

            for (int label=0; label<numLabels; label++) {
                c.cachedGradient[label * numFeatures + defaultFeatureIndex] -=
                    scores[label] * digammaDifferenceForSums;
            }


            for(int labelLoc = 0; labelLoc <multinomialValues.numLocations(); labelLoc++) {
                int label = multinomialValues.indexAtLocation(labelLoc);
                double count = multinomialValues.valueAtLocation(labelLoc);
                
                double diff = 0.0;

                if (count < 20) {
                    for (int i=0; i < count; i++) {
                        diff += 1 / (scores[label] + i);
                    }
                }
                else {
                    diff = Dirichlet.digamma(scores[label] + count) -
                        Dirichlet.digamma(scores[label]);
                }

                c.cachedGradient[label * numFeatures + defaultFeatureIndex] +=
                    scores[label] * diff;
                    

            }
            ++instanceIndex;
        }

        numGetValueGradientCalls++;
            
        for (int label = 0; label < numLabels; label++) {
            for (int feature = 0; feature < numFeatures - 1; feature++) {
                double param = parameters[label*numFeatures + feature];

                c.cachedGradient[label * numFeatures + feature] -= 
                    (param - gaussianPriorMean) / gaussianPriorVariance;
            }

            double param = parameters[label*numFeatures + defaultFeatureIndex];
                
            c.cachedGradient[label * numFeatures + defaultFeatureIndex] -= 
                (param - gaussianPriorMean) / defaultFeatureGaussianPriorVariance;
        }

        // A parameter may be set to -infinity by an external user.
        // We set gradient to 0 because the parameter's value can
        // never change anyway and it will mess up future calculations
        // on the matrix, such as norm().
        MatrixOps.substitute (c.cachedGradient, Double.NEGATIVE_INFINITY, 0.0);

        assert (buffer != null && buffer.length == parameters.length);
        System.arraycopy (c.cachedGradient, 0, buffer, 0, c.cachedGradient.length);
        //System.out.println ("DCMMaxEntTrainer gradient infinity norm = "+MatrixOps.infinityNorm(cachedGradient));
    }
}

