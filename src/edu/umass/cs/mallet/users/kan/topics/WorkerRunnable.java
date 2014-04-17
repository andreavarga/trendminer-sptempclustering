/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.	For further
   information, see the file `LICENSE' included with this distribution. */

package edu.umass.cs.mallet.users.kan.topics;

import java.util.ArrayList;
import java.util.Arrays;

import cc.mallet.types.FeatureSequence;
import cc.mallet.util.Randoms;

/**
 * A parallel topic model runnable task.
 * 
 * @author David Mimno, Andrew McCallum
 */

public class WorkerRunnable implements Runnable {
	
	boolean isFinished = true;

	final ArrayList<TopicAssignment> data;
	final int startDoc, numDocs;

	protected final int numTopics; // Number of topics to be fit
	protected final int numTypes;

	protected double[] alpha;	 // Dirichlet(alpha,alpha,...) is the distribution over topics
	protected double beta;   // Prior on per-topic multinomial distribution over words
	protected double betaSum;
	
	public static final double DEFAULT_BETA = 0.01;
	
	protected double smoothingOnlyMass = 0.0;
	protected double[] cachedCoefficients;

	protected TypeTopicCounts typeTopicCounts; // indexed by <feature index, topic index>
	protected int[] tokensPerTopic; // indexed by <topic index>

	// for dirichlet estimation
	protected int[] docLengthCounts; // histogram of document sizes
	protected int[][] topicDocCounts; // histogram of document/topic counts, indexed by <topic index, sequence position index>

	boolean shouldSaveState = false;
	boolean shouldBuildLocalCounts = true;
	
	protected final Randoms random;
	
	/**
	     @param alpha
	     @param beta
	     @param random
	     @param data
	     @param runnableCounts     - note that the caller is responsible for cloning this if necessary
	     @param tokensPerTopic     - note that the caller is responsible for cloning this if necessary
	     @param startDoc
	     @param numDocs
	 **/
	public WorkerRunnable (double[] alpha, double beta, 
	                       Randoms random,
						   ArrayList<TopicAssignment> data,
						   TypeTopicCounts runnableCounts, 
						   int[] tokensPerTopic,
						   int startDoc, int numDocs) {

		this.data = data;

		this.typeTopicCounts = runnableCounts;
		this.tokensPerTopic = tokensPerTopic;
		
        this.numTopics = this.typeTopicCounts.numTopics;
        this.numTypes = this.typeTopicCounts.numTypes; 

		this.alpha = alpha;
		this.beta = beta;
		this.betaSum = beta * numTypes;
		this.random = random;
		
		this.startDoc = startDoc;
		this.numDocs = numDocs;

		this.cachedCoefficients = new double[ numTopics ];

		System.err.print("WorkerRunnable Thread: ");
		System.err.println(this.typeTopicCounts.getConfigSummary());
	}

	/**
	 *  If there is only one thread, we don't need to go through 
	 *   communication overhead. This method asks this worker not
	 *   to prepare local type-topic counts. The method should be
	 *   called when we are using this code in a non-threaded environment.
	 */
	public void makeOnlyThread() {
		shouldBuildLocalCounts = false;
	}

	public int[] getDocLengthCounts() { return docLengthCounts; }
	public int[][] getTopicDocCounts() { return topicDocCounts; }

	public void initializeAlphaStatistics(int maxDocLength) {
		docLengthCounts = new int[maxDocLength];
		topicDocCounts = new int[numTopics][maxDocLength];
	}

    public void resetAlphaStatistics() {
        Arrays.fill(docLengthCounts, 0);
        for (int[] topicCounts: topicDocCounts) {
            Arrays.fill(topicCounts, 0);
        }
    }

	public void collectAlphaStatistics() {
		shouldSaveState = true;
	}

	public void resetBeta(double beta, double betaSum) {
		this.beta = beta;
		this.betaSum = betaSum;
	}

	/**
	 *  Once we have sampled the local counts, trash the 
	 *   "global" type topic counts and reuse the space to 
	 *   build a summary of the type topic counts specific to 
	 *   this worker's section of the corpus.
	 */
	private void buildLocalTypeTopicCounts () {

		// Clear the topic totals
		Arrays.fill(tokensPerTopic, 0);

		// Clear the type/topic counts, only 
		//  looking at the entries before the first 0 entry.

		typeTopicCounts.clearCounts();

        for (int doc = startDoc;
			 doc < data.size() && doc < startDoc + numDocs;
             doc++) {

			TopicAssignment document = data.get(doc);

            FeatureSequence tokens = document.getTokens();
            int[] topics = document.getTopics();
            
            for (int position = 0; position < tokens.size(); position++) {

				int topic = topics[position];

				tokensPerTopic[topic]++;
				
				int type = tokens.getIndexAtPosition(position);
				
				typeTopicCounts.initializeTypeTopicCount(type, topic);
			}
		}
	}


	public void run () {

		try {
			
			if (! isFinished) { System.out.println("already running!"); return; }
			
			isFinished = false;
			
			prepareToSample();
			
			for (int doc = startDoc;
				 doc < data.size() && doc < startDoc + numDocs;
				 doc++) {
				
				/*
				  if (doc % 10000 == 0) {
				  System.out.println("processing doc " + doc);
				  }
				*/
				
				sampleTopicsForOneDoc (data.get(doc), true);
			}
			
			if (shouldBuildLocalCounts) {
				buildLocalTypeTopicCounts();
			}

			shouldSaveState = false;
			isFinished = true;

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void prepareToSample() {
	    this.smoothingOnlyMass = 
	        initSmoothingOnlyMassAndCachedCoefficients(
	                cachedCoefficients, alpha, beta, betaSum, tokensPerTopic);
	}

	
	/**
	     <pre>
                                         alpha[t] * beta
    	     smoothingOnlyMass    = sum  ---------------
    	                             t   N[t] + sum(beta)
    
                                            alpha[t] 
             cachedCoefficient[t] = sum  ---------------
                                     t   N[t] + sum(beta)
	                          
         </pre>

	     @param cachedCoefficients     out param where coefficient results are returned
	     @param alpha
	     @param beta
	     @param betaSum
	     @param tokensPerTopic
	     @return the new value for the smoothing only mass
	 **/
	protected static double initSmoothingOnlyMassAndCachedCoefficients(
	        double[] cachedCoefficients, 
	        final double[] alpha, double beta, double betaSum, final int[] tokensPerTopic) {
	    
		// Initialize the smoothing-only sampling bucket
	    //     \sum_{t}\frac{\alpha_t \beta}{n_t + \sum\beta}
	    double smoothingOnlyMass = 0.0;
		
		// Initialize the cached coefficients, using only smoothing.
		//  These values will be selectively replaced in documents with
		//  non-zero counts in particular topics.
		
		for (int topic=0; topic < alpha.length; topic++) {
		    smoothingOnlyMass += alpha[topic] * beta / (tokensPerTopic[topic] + betaSum);
			cachedCoefficients[topic] =  alpha[topic] / (tokensPerTopic[topic] + betaSum); // this is the cached coeff value for zero-count topics
		}
		return smoothingOnlyMass;
	}
	
	protected void sampleTopicsForOneDoc(TopicAssignment document, boolean readjustTopicsAndStats /* currently ignored */) {

        FeatureSequence tokenSequence = document.getTokens();
		int[] oneDocTopics = document.getTopics();

		//int[] currentTypeTopicCounts;
		int type, oldTopic, newTopic;
		//double topicWeightsSum;
		int docLength = tokenSequence.getLength();

		int[] localTopicCounts = new int[numTopics];	// doc topic counts
		int[] localTopicIndex = new int[numTopics];		// dense index of non-zero doc topics

		//		populate topic counts
		for (int position = 0; position < docLength; position++) {
			localTopicCounts[oneDocTopics[position]]++;
		}

		//		Initialize the topic count/beta sampling bucket
		double topicBetaMass = 0.0;   // = \sum_{t}\frac{n_{t|d}\beta}{n_t + \sum\beta}

		// Build an array that densely lists the topics that
		//  have non-zero counts.
		// Initialize cached coefficients and the topic/beta 
		//  normalizing constant.
        //                                                beta * n_{t|d}
        //                    topicBetaMass        = sum  ---------------
        //                                            t   N[t] + sum(beta)
        //        
        //                                                alpha[t] + n_{t|d}
        //                    cachedCoefficient[t] = sum  ------------------
        //                                            t    N[t] + sum(beta)

		int denseIndex = 0;
		for (int topic = 0; topic < numTopics; topic++) {
			
			int n = localTopicCounts[topic];
			
			if (n != 0) {
				localTopicIndex[denseIndex] = topic;
				denseIndex++;
				
				//	initialize the normalization constant for the (B * n_{t|d}) term
				topicBetaMass += beta * n /	(tokensPerTopic[topic] + betaSum);	

				//	update the coefficients for the non-zero topics
				//      \sum_{t}\frac{\alpha_t + N_{t|d}}{N_t + \sum\beta}
				
				cachedCoefficients[topic] =	(alpha[topic] + n) / (tokensPerTopic[topic] + betaSum);
			}
		}

		// Record the total number of non-zero topics
		int nonZeroTopics = denseIndex;

		//	Iterate over the positions (words) in the document 
		for (int position = 0; position < docLength; position++) {
			type = tokenSequence.getIndexAtPosition(position);
			oldTopic = oneDocTopics[position];

			//	Remove this token from all counts. 

			// Remove this topic's contribution to the 
			//  normalizing constants
			smoothingOnlyMass -= alpha[oldTopic] * beta / 
				(tokensPerTopic[oldTopic] + betaSum);
			topicBetaMass -= beta * localTopicCounts[oldTopic] /
				(tokensPerTopic[oldTopic] + betaSum);
			
			// Decrement the local doc/topic counts

			localTopicCounts[oldTopic]--;

			// Maintain the dense index, if we are deleting
			//  the old topic
			if (localTopicCounts[oldTopic] == 0) {

				// First get to the dense location associated with
				//  the old topic.
				
				denseIndex = 0;

				// We know it's in there somewhere, so we don't 
				//  need bounds checking.
				while (localTopicIndex[denseIndex] != oldTopic) {
					denseIndex++;
				}
				
				// shift all remaining dense indices to the left.
				while (denseIndex < nonZeroTopics) {
					if (denseIndex < localTopicIndex.length - 1) {
						localTopicIndex[denseIndex] = 
							localTopicIndex[denseIndex + 1];
					}
					denseIndex++;
				}

				nonZeroTopics --;
			}

			// Decrement the global topic count totals
			tokensPerTopic[oldTopic]--;
			assert(tokensPerTopic[oldTopic] >= 0) : "old Topic " + oldTopic + " below 0";
			

			// Add the old topic's contribution back into the
			//  normalizing constants.
			smoothingOnlyMass += alpha[oldTopic] * beta / 
				(tokensPerTopic[oldTopic] + betaSum);
			topicBetaMass += beta * localTopicCounts[oldTopic] /
				(tokensPerTopic[oldTopic] + betaSum);

			// Reset the cached coefficient for this topic
			cachedCoefficients[oldTopic] = 
				(alpha[oldTopic] + localTopicCounts[oldTopic]) /
				(tokensPerTopic[oldTopic] + betaSum);


			// Now go over the type/topic counts, decrementing
			//  where appropriate, and calculating the score
			//  for each topic at the same time.

			double   topicTermMass = typeTopicCounts.calculateTopicTermScoresWhileDecrementingOldTopic(type, oldTopic, cachedCoefficients); 
			double   sample        = random.nextUniform() * (smoothingOnlyMass + topicBetaMass + topicTermMass);
			double   origSample    = sample;

			//	Make sure it actually gets set
			newTopic = -1;

			if (sample < topicTermMass) {	// sample falls in topic term mass
				//topicTermCount++;

				newTopic = typeTopicCounts.updateTopicInTermMass(type, sample);
			}
			else {
				sample -= topicTermMass;
				
				boolean inBetaMass = sample < topicBetaMass; 	// sample falls in topic beta mass

				if (inBetaMass) {
					//betaTopicCount++;

					sample /= beta;

					for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
						int topic = localTopicIndex[denseIndex];

						sample -= localTopicCounts[topic] /
							(tokensPerTopic[topic] + betaSum);

						if (sample <= 0.0) {
							newTopic = topic;
							break;
						}
					}
				}
				else {                  // sample falls in smoothing only mass
					//smoothingOnlyCount++;

					sample -= topicBetaMass;
					
					assert(sample < smoothingOnlyMass);

					sample /= beta;

					newTopic = 0;
					sample -= alpha[newTopic] /
						(tokensPerTopic[newTopic] + betaSum);

					while (sample > 0.0) {
						newTopic++;
						sample -= alpha[newTopic] / 
							(tokensPerTopic[newTopic] + betaSum);
					}
					
				}

				// Move to the position for the new topic,
				//  which may be the first empty position if this
				//  is a new topic for this word.
				
				typeTopicCounts.updateTopicInSmoothingMass(type, newTopic, inBetaMass);
			}

			if (newTopic == -1) {
				System.err.println("WorkerRunnable sampling error: "+ origSample + " " + sample + " " + smoothingOnlyMass + " " + 
						topicBetaMass + " " + topicTermMass);
				newTopic = numTopics-1; // TODO is this appropriate
				//throw new IllegalStateException ("WorkerRunnable: New topic not sampled.");
			}
			//assert(newTopic != -1);

			//			Put that new topic into the counts
			oneDocTopics[position] = newTopic;

			smoothingOnlyMass -= alpha[newTopic] * beta / 
				(tokensPerTopic[newTopic] + betaSum);
			topicBetaMass -= beta * localTopicCounts[newTopic] /
				(tokensPerTopic[newTopic] + betaSum);

			localTopicCounts[newTopic]++;

			// If this is a new topic for this document,
			//  add the topic to the dense index.
			if (localTopicCounts[newTopic] == 1) {
				
				// First find the point where we 
				//  should insert the new topic by going to
				//  the end (which is the only reason we're keeping
				//  track of the number of non-zero
				//  topics) and working backwards

				denseIndex = nonZeroTopics;

				while (denseIndex > 0 &&
					   localTopicIndex[denseIndex - 1] > newTopic) {

					localTopicIndex[denseIndex] =
						localTopicIndex[denseIndex - 1];
					denseIndex--;
				}
				
				localTopicIndex[denseIndex] = newTopic;
				nonZeroTopics++;
			}

			tokensPerTopic[newTopic]++;

			//	update the coefficients for the non-zero topics
			cachedCoefficients[newTopic] =
				(alpha[newTopic] + localTopicCounts[newTopic]) /
				(tokensPerTopic[newTopic] + betaSum);

			smoothingOnlyMass += alpha[newTopic] * beta / 
				(tokensPerTopic[newTopic] + betaSum);
			topicBetaMass += beta * localTopicCounts[newTopic] /
				(tokensPerTopic[newTopic] + betaSum);

		}

		if (shouldSaveState) {
			// Update the document-topic count histogram,
			//  for dirichlet estimation
			docLengthCounts[ docLength ]++;

			for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
				int topic = localTopicIndex[denseIndex];
				
				topicDocCounts[topic][ localTopicCounts[topic] ]++;
			}
		}

		//	Clean up our mess: reset the coefficients to values with only
		//	smoothing. The next doc will update its own non-zero topics...

		for (denseIndex = 0; denseIndex < nonZeroTopics; denseIndex++) {
			int topic = localTopicIndex[denseIndex];

			cachedCoefficients[topic] =
				alpha[topic] / (tokensPerTopic[topic] + betaSum);
		}

	}

    /**
     *   Adds the contribution of this worker to the global count arrays
     *   
     *   @param sumTokensPerTopic   - global counts (in/out param) 
     *   @param sumTypeTopicCounts  - global counts (in/out param)
    **/
    public void aggregateCountsFromWorkersToGlobal(int[] sumTokensPerTopic, TypeTopicCounts sumTypeTopicCounts)
    {
        // Handle the total-tokens-per-topic array
        for (int topic = 0; topic < numTopics; topic++) {
            sumTokensPerTopic[topic] += tokensPerTopic[topic];
        }
        
        // Now handle the individual type topic counts
        sumTypeTopicCounts.addCounts(typeTopicCounts);
    }
    
    /**
     *   Resets this worker's counts to the global counts
     *   
     *   @param sourceTokensPerTopic
     *   @param sourceTypeTopicCounts
    **/
    public void resetWorkerCountsToGlobalValues(int[] sourceTokensPerTopic, TypeTopicCounts sourceTypeTopicCounts)
    {
        System.arraycopy(sourceTokensPerTopic, 0, tokensPerTopic, 0, numTopics);
        
        typeTopicCounts.setCounts(sourceTypeTopicCounts);
    }
}
