/*=============================================================================
  TypeTopicCounts.java
                                    Created by jkan on Jul 21, 2009
                                    Copyright (c)2009 Essbare Weichware, GmbH
                                    All rights reserved.
  =============================================================================*/

package edu.umass.cs.mallet.users.kan.topics;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.logging.Logger;

import cc.mallet.types.Alphabet;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.IDSorter;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.MalletLogger;


/**
 *  TypeTopicCounts
 *      
 *       is an attempt to abstract out the bitwise operations required to store type/topic counts 
 *       in a compact way into a separate class without sacrificing performance.
 *       
 *       @author jkan
 **/
public class TypeTopicCounts implements Serializable {

    protected static Logger logger = MalletLogger.getLogger(TypeTopicCounts.class.getName());
    
    // These values are used to encode type/topic counts as
	//  count/topic pairs in a single int.
	
    private static final long serialVersionUID = -100L;

    protected int             numTypes;
    protected int             numTopics;

    protected int             topicMask;
    protected int             topicBits;

    protected int[][]         typeTopicCounts;         // indexed by <feature index, topic index>
    private double[]          topicTermScores;

    /** The number of times each type appears in the corpus **/
    int[]                     typeTotals;
    /** The max over typeTotals, used for beta optimization **/
    int                       maxTypeCount;

    TopicProgressLogger       topicLogger;

    
	public TypeTopicCounts(int numTypes, int numTopics, InstanceList training) {
		
		this.numTypes  = numTypes;
		this.numTopics = numTopics;
		//this.trainingAlphabet  = training.getAlphabet();
		
        if (Integer.bitCount(numTopics) == 1) {
            // exact power of 2
            topicMask = numTopics - 1;
            topicBits = Integer.bitCount(topicMask);
        } else {
            // otherwise add an extra bit
            topicMask = Integer.highestOneBit(numTopics) * 2 - 1;
            topicBits = Integer.bitCount(topicMask);
        }
		
		this.typeTotals = calcTypeTotals(numTypes, training);

		this.typeTopicCounts = new int[numTypes][];

        this.maxTypeCount = 0;

		// Allocate enough space so that we never have to worry about
		//  overflows: either the number of topics or the number of times
		//  the type occurs.
		for (int type = 0; type < numTypes; type++) {
            if (typeTotals[type] > maxTypeCount) { maxTypeCount = typeTotals[type]; }
			typeTopicCounts[type] = new int[ Math.min(numTopics, typeTotals[type]) ];
		}

		this.topicTermScores = new double[this.numTopics];
	}
	
	
	/**
	 * Copy constructor
	 * 
	 *  @param source - the thing to copy
	 **/
	TypeTopicCounts(TypeTopicCounts source) {
			
		this.numTypes  = source.numTypes;
		this.numTopics = source.numTopics;
		
		this.topicMask = source.topicMask;
		this.topicBits = source.topicBits;
		
        this.topicTermScores = Arrays.copyOf(source.topicTermScores, source.topicTermScores.length);
        this.typeTopicCounts = new int[source.typeTopicCounts.length][];
		
		for (int type = 0; type < source.typeTopicCounts.length; type++) {
			this.typeTopicCounts[type] = Arrays.copyOf(source.typeTopicCounts[type], source.typeTopicCounts[type].length);
		}
		
        this.typeTotals   = Arrays.copyOf(source.typeTotals, source.typeTotals.length);
        this.maxTypeCount = source.maxTypeCount;
	}

	
    /**
     * calculates the total number of occurrences of each word type across documents
     *
     * @param numTypes - total number of types in the corpus
     * @param training - the (training) corpus of documents
     * @return the corpus type counts                                                  
     */
    private static int[] calcTypeTotals(int numTypes, InstanceList training) {
		// Get the total number of occurrences of each word type
		int[] typeTotals = new int[numTypes];

		for (Instance instance : training) {
			FeatureSequence tokens = (FeatureSequence) instance.getData();
			
			for (int type : tokens.getFeatures()) 
				++typeTotals[type];
			
//			for (int position = 0; position < tokens.getLength(); position++) {
//				int type = tokens.getIndexAtPosition(position);
//				typeTotals[ type ]++;
//			}
		}
		return typeTotals;
    }

    /**
     * Clear the type/topic counts, only looking at the entries before the first 0 entry.
     */
    public void clearCounts() {

		for (int[] topicCounts : typeTopicCounts) 
		{
			int position = 0;
			while (position < topicCounts.length && 
				   topicCounts[position] > 0) {
				topicCounts[position] = 0;
				position++;
			}
		}
	}
		

	public void initializeTypeTopicCount(int type, int topic) {

		// The format for these arrays is 
		//  the topic in the rightmost bits
		//  the count in the remaining (left) bits.
		// Since the count is in the high bits, sorting (desc)
		//  by the numeric value of the int guarantees that
		//  higher counts will be before the lower counts.
		
		int[] currentTypeTopicCounts = typeTopicCounts[ type ];

		// Start by assuming that the array is either empty
		//  or is in sorted (descending) order.
		
		// Here we are only adding counts, so if we find 
		//  an existing location with the topic, we only need
		//  to ensure that it is not larger than its left neighbor.
		
		int index = 0;
		int currentTopic = currentTypeTopicCounts[index] & topicMask;
		int currentValue;
		
		while (currentTypeTopicCounts[index] > 0 && currentTopic != topic) {
			index++;
			if (index == currentTypeTopicCounts.length) {
				System.out.println("overflow on type " + type);
			}
			currentTopic = currentTypeTopicCounts[index] & topicMask;
		}
		currentValue = currentTypeTopicCounts[index] >> topicBits;
		
		if (currentValue == 0) {
			// new value is 1, so we don't have to worry about sorting
			//  (except by topic suffix, which doesn't matter)
			
			currentTypeTopicCounts[index] =
				(1 << topicBits) + topic;
		}
		else {
			currentTypeTopicCounts[index] =
				((currentValue + 1) << topicBits) + topic;
			
			// Now ensure that the array is still sorted by 
			//  bubbling this value up.
			
			bubbleUp(currentTypeTopicCounts, index);
		}
	}


	/**
	 * Ensure that the array is still sorted by
	 * bubbling this value up.
	 * 
	 * @param index	the index containing the value to bubble up
	 **/
	private void bubbleUp(int[] currentTypeTopicCounts, int index) {
		
		// Now ensure that the array is still sorted by 
		//  bubbling this value up.
		
		while (index > 0 &&
			   currentTypeTopicCounts[index] > currentTypeTopicCounts[index - 1]) {
			int temp = currentTypeTopicCounts[index];
			currentTypeTopicCounts[index] = currentTypeTopicCounts[index - 1];
			currentTypeTopicCounts[index - 1] = temp;
			
			index--;
		}
	}

		
	public void addCounts(TypeTopicCounts sourceTypeTopicCounts) {
		
		for (int type = 0; type < typeTopicCounts.length; type++) {
				
			// Here the source is the individual thread counts,
			//  and the target is the global counts.
	
			int[] sourceCounts = sourceTypeTopicCounts.typeTopicCounts[type];
			int[] targetCounts = typeTopicCounts[type];
	
			int sourceIndex = 0;
			while (sourceIndex < sourceCounts.length &&
				   sourceCounts[sourceIndex] > 0) {
				
				int topic = sourceCounts[sourceIndex] & topicMask;
				int count = sourceCounts[sourceIndex] >> topicBits;
	
				int targetIndex = 0;
				int currentTopic = targetCounts[targetIndex] & topicMask;
				int currentCount;
				
				while (targetCounts[targetIndex] > 0 && currentTopic != topic) {
					targetIndex++;
					if (targetIndex == targetCounts.length) {
						System.out.println("overflow in merging on type " + type);
					}
					currentTopic = targetCounts[targetIndex] & topicMask;
				}
				currentCount = targetCounts[targetIndex] >> topicBits;
				
				targetCounts[targetIndex] =
					((currentCount + count) << topicBits) + topic;
				
				bubbleUp(targetCounts, targetIndex);
				sourceIndex++;
			}
		}
	}
	
	
	
	public void validateCounts() {
	    
		/* // Debuggging code to ensure counts are being 
		   // reconstructed correctly.

		for (int type = 0; type < numTypes; type++) {
			
			int[] targetCounts = typeTopicCounts[type];
			
			int index = 0;
			int count = 0;
			while (index < targetCounts.length &&
				   targetCounts[index] > 0) {
				count += targetCounts[index] >> topicBits;
				index++;
			}
			
			if (count != typeTotals[type]) {
				System.err.println("Expected " + typeTotals[type] + ", found " + count);
			}
			
		}
		*/
	}
	
	public void setCounts(TypeTopicCounts source) {
	    
		for (int type = 0; type < typeTopicCounts.length; type++) {
			int[] targetCounts = this.typeTopicCounts[type];
			int[] sourceCounts = source.typeTopicCounts[type]; //getTopicCountsForType(type);
			
			int index = 0;
			while (index < sourceCounts.length) {
				
				if (sourceCounts[index] != 0) {
					targetCounts[index] = sourceCounts[index];
				}
				else if (targetCounts[index] != 0) {
					targetCounts[index] = 0;
				}
				else {
					break;
				}
				
				index++;
			}
			//System.arraycopy(typeTopicCounts[type], 0, counts, 0, counts.length);
		}
	}
	
	
    /**
     * Now go over the type/topic counts, calculating the score for each topic.
     * 
     * @param type
     * @param cachedCoefficients
     * @param topicTermScores - out param to pass back the calculated topic term scores
     * @return topicTermMass - sum over topicTermScores
     **/
    public double calculateTopicTermScores(
            int type, 
            double[] cachedCoefficients, 
            double[] topicTermScores)
    {
        int   index = 0;
        int   currentTopic, currentValue;
        
        int[] currentTypeTopicCounts = typeTopicCounts[type];

        double   topicTermMass = 0.0;
        
        while (index < currentTypeTopicCounts.length && 
               currentTypeTopicCounts[index] > 0) {
            
            currentTopic = currentTypeTopicCounts[index] & topicMask;
            currentValue = currentTypeTopicCounts[index] >> topicBits;

            //this.debugPrintTypeTopicCounts(System.out, type, oldTopic, currentTopic);
            
            double score = cachedCoefficients[currentTopic] * currentValue;
            
            topicTermMass += score;
            topicTermScores[index] = score;

            index++;
        }
        return topicTermMass;
    }

    
	
	/**
	 * Now go over the type/topic counts, decrementing where appropriate, and 
	 * calculating the score for each topic at the same time.  This is the same
	 * as calculateTopicTermScores except this also does the decrementing.
	 *  
     * @param type
     * @param oldTopic
     * @param cachedCoefficients
     * @return topicTermMass - sum over topicTermScores
	 **/
	public double calculateTopicTermScoresWhileDecrementingOldTopic(
			int type, int oldTopic, 
			double[] cachedCoefficients)
	{
		int   index = 0;
		int   currentTopic, currentValue;
		
		int[] currentTypeTopicCounts = typeTopicCounts[type];

		boolean alreadyDecremented = false;

		double   topicTermMass = 0.0;
		
		while (index < currentTypeTopicCounts.length && 
			   currentTypeTopicCounts[index] > 0) {
			
			currentTopic = currentTypeTopicCounts[index] & topicMask;
			currentValue = currentTypeTopicCounts[index] >> topicBits;

			//this.debugPrintTypeTopicCounts(System.out, type, oldTopic, currentTopic);
			
			if (! alreadyDecremented && currentTopic == oldTopic) {

				// We're decrementing and adding up the 
				//  sampling weights at the same time, but
				//  decrementing may require us to reorder
				//  the topics, so after we're done here,
				//  look at this cell in the array again.

				currentValue --;
				if (currentValue == 0) {
					currentTypeTopicCounts[index] = 0;
				}
				else {
					currentTypeTopicCounts[index] =
						(currentValue << topicBits) + oldTopic;
				}
				
				// Shift the reduced value to the right, if necessary.

				int subIndex = index;
				while (subIndex < currentTypeTopicCounts.length - 1 && 
					   currentTypeTopicCounts[subIndex] < currentTypeTopicCounts[subIndex + 1]) {
					int temp = currentTypeTopicCounts[subIndex];
					currentTypeTopicCounts[subIndex] = currentTypeTopicCounts[subIndex + 1];
					currentTypeTopicCounts[subIndex + 1] = temp;
					
					subIndex++;
				}

				alreadyDecremented = true;
			}
			else
			{
				double score = cachedCoefficients[currentTopic] * currentValue;
				
				topicTermMass += score;
				topicTermScores[index] = score;
	
				index++;
			}
		}
		return topicTermMass;
	}
	
	
	public int updateTopicInTermMass(int type, double sample) {
	    
		int i = -1;
		while (sample > 0) {
			i++;
			sample -= topicTermScores[i];
		}

		int[] currentTypeTopicCounts = typeTopicCounts[type];
		
		int newTopic     = currentTypeTopicCounts[i] & topicMask;
		int currentValue = currentTypeTopicCounts[i] >> topicBits;

        if (topicLogger != null)
            topicLogger.updateTopicInTermMass(this, type, newTopic);
		
		currentTypeTopicCounts[i] = ((currentValue + 1) << topicBits) + newTopic;

		// Bubble the new value up, if necessary
		
		bubbleUp(currentTypeTopicCounts, i);
		return newTopic;
	}

	public void updateTopicInSmoothingMass(int type, int newTopic, boolean inBetaMass) {
	    
		int[] currentTypeTopicCounts = typeTopicCounts[type];
		
		// Move to the position for the new topic,
		//  which may be the first empty position if this
		//  is a new topic for this word.
		
		int index = 0;
		while (currentTypeTopicCounts[index] > 0 &&
			   (currentTypeTopicCounts[index] & topicMask) != newTopic) {
			index++;
		}
		
		if (index == currentTypeTopicCounts.length)	// only print if we've filled up the entire topic array (?)
            debugPrintNewTopic(type, newTopic);     //   this is probably an error condition
            //debugPrintTypeTopicCounts(System.out, 0, "new topic:", type, "", -1, newTopic);
			//debugPrintTypeTopicCounts(System.out, 0, "new topic:", type, strType, -1, newTopic);
		
		// index should now be set to the position of the new topic,
		//  which may be an empty cell at the end of the list.

		if (currentTypeTopicCounts[index] == 0) {
			// inserting a new topic, guaranteed to be in
			//  order w.r.t. count, if not topic.
			currentTypeTopicCounts[index] = (1 << topicBits) + newTopic;
		}
		else {
			int currentValue = currentTypeTopicCounts[index] >> topicBits;
			currentTypeTopicCounts[index] = ((currentValue + 1) << topicBits) + newTopic;

			bubbleUp(currentTypeTopicCounts, index);
		}
		
        if (topicLogger != null)
            topicLogger.updateTopicInSmoothingMass(this, type, newTopic, inBetaMass);
	}


	private void debugPrintNewTopic(int type, int newTopic) {
		
		int[] currentTypeTopicCounts = typeTopicCounts[type];

		System.out.format("type: %d new topic: %d", type, newTopic);
		for (int k=0; k<currentTypeTopicCounts.length; k++) {
			System.out.format(" %d:%d", (currentTypeTopicCounts[k] & topicMask), 
							            (currentTypeTopicCounts[k] >> topicBits));
		}
		System.out.println();
	}

	
	/**
	 *  Return an array of sorted sets (one set per topic). Each set 
	 *   contains IDSorter objects with integer keys into the alphabet.
	 *   To get direct access to the Strings, use getTopWords().
	 */
	public TreeSet<IDSorter>[] getSortedWords() {
		
		@SuppressWarnings("unchecked")
        TreeSet<IDSorter>[] topicSortedWords = (TreeSet<IDSorter>[]) new TreeSet[ numTopics ];

		// Initialize the tree sets
		for (int topic = 0; topic < numTopics; topic++) {
			topicSortedWords[topic] = new TreeSet<IDSorter>();
		}

		// Collect counts
		for (int type = 0; type < numTypes; type++) {

			int[] topicCounts = typeTopicCounts[type];

			int index = 0;
			while (index < topicCounts.length &&
				   topicCounts[index] > 0) {

				int topic = topicCounts[index] & topicMask;
				int count = topicCounts[index] >> topicBits;

				topicSortedWords[topic].add(new IDSorter(type, count));

				index++;
			}
		}
		return topicSortedWords;
	}
	
	/**
	 *  Write the internal representation of type-topic counts  
	 *   (count/topic pairs in descending order by count) to a file.
	 */
	public void print(File file, Alphabet alphabet) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (file) );

		try {
			print(out, alphabet);
		} finally {
			out.close();
		}
	}

	
	public void print(PrintWriter out, Alphabet alphabet) {
		
		for (int type = 0; type < typeTopicCounts.length; type++) {

			StringBuilder buffer = new StringBuilder();

			buffer.append(type).append(' ').append(alphabet.lookupObject(type));

			int[] topicCounts = typeTopicCounts[type];

			int index = 0;
			while (index < topicCounts.length &&
				   topicCounts[index] > 0) {

				int topic = topicCounts[index] & topicMask;
				int count = topicCounts[index] >> topicBits;
				
				buffer.append(' ').append(topic).append(':').append(count);

				index++;
			}

			out.println(buffer);
		}
	}

	
    public void debugPrint(PrintStream out, int type, double[] topicTermScores, double[] cachedCoefficients)
    {
        int[] topicCounts = typeTopicCounts[type];

        int index = 0;
        while (index < topicCounts.length &&
               topicCounts[index] > 0) {

            int topic = topicCounts[index] & topicMask;
            int count = topicCounts[index] >> topicBits;
            
            out.println(topic + "\t" + count + "\t" 
                              + topicTermScores[index] + "\t" 
                              + cachedCoefficients[topic]);
            index++;
        }
    }
	
//	public void debugPrintTopicTermScores(PrintStream out, String label, int type, int oldTopic, int currentTopic)
//	{
//		out.format("%10s%4d%20s", label, type, this.trainingAlphabet.lookupObject(type));
//
//		for (int i = 0; i < this.topicTermScores.length; ++i)
//		{
//			//out.print(currentTopic == i ? '*' : ' ');
//			out.format(" %5.0f", this.topicTermScores[i]*10000);
//		}
//		out.println();
//	}
	
	/**
	 * Print an unnormalized weight for every word in every topic.
	 *  Most of these will be equal to the smoothing parameter beta.
	 */
	public void printTopicWordWeights(PrintWriter out, Alphabet alphabet, double beta) throws IOException {
		// Probably not the most efficient way to do this...

		for (int topic = 0; topic < numTopics; topic++) {
			for (int type = 0; type < numTypes; type++) {

				int[] topicCounts = typeTopicCounts[type];
				
				double weight = beta;

				int index = 0;
				while (index < topicCounts.length &&
					   topicCounts[index] > 0) {

					int currentTopic = topicCounts[index] & topicMask;
					if (currentTopic == topic) {
						weight += topicCounts[index] >> topicBits;
						break;
					}
					index++;
				}
				out.println(topic + "\t" + alphabet.lookupObject(type) + "\t" + weight);
			}
		}
	}
	
	
	/**
	 * This method calculates the data-dependent term in the
	 * word|topic portion of the model log likelihood.
	 * See the comment in the
	 * {@link ParallelTopicModel#modelLogLikelihood()} method.
	 * 
	 * @param beta
	 * @return
	**/
	public double calcLogLikelihoodTerm(double beta) {
		
		double logLikelihood = 0.0;
		
		int[] topicCounts;
		
		// Count the number of type-topic pairs
		int nonZeroTypeTopics = 0;

		for (int type=0; type < numTypes; type++) {
			// reuse this array as a pointer

			topicCounts = typeTopicCounts[type];

			int index = 0;
			while (index < topicCounts.length &&
				   topicCounts[index] > 0) {
//				int topic = topicCounts[index] & topicMask;
				int count = topicCounts[index] >> topicBits;
				
				nonZeroTypeTopics++;
				logLikelihood += Dirichlet.logGammaStirling(beta + count);

                if (Double.isNaN(logLikelihood)) {
                    int topic = topicCounts[index] & topicMask;
                    logger.warning("NaN in log likelihood calculation: type = " + type + " topic = " + topic + " count = " + count);
                    return 0;
                }
                else if (Double.isInfinite(logLikelihood)) {
                    int topic = topicCounts[index] & topicMask;
                    logger.warning("infinite log likelihood: type = " + type + " topic = " + topic + " count = " + count);
                    return 0;
                }
				index++;
			}
		}

		logLikelihood += 
			(Dirichlet.logGammaStirling(beta * numTypes)) -
			(Dirichlet.logGammaStirling(beta) * nonZeroTypeTopics);
		
		return logLikelihood;
	}

	
	/**
	 * @return the number of type/topic pairs that have each number of tokens
	 **/
	public int[] getCountHistogram() {
        // The histogram starts at count 0, so if all of the
        //  tokens of the most frequent type were assigned to one topic,
        //  we would need to store a maxTypeCount + 1 count.
        int[] countHistogram = new int[maxTypeCount + 1];
        
        // Now count the number of type/topic pairs that have
        //  each number of tokens.

        int index;
        for (int type = 0; type < numTypes; type++) {
            int[] counts = typeTopicCounts[type];
            index = 0;
            while (index < counts.length &&
                   counts[index] > 0) {
                int count = counts[index] >> topicBits;
                countHistogram[count]++;
                index++;
            }
        }
        return countHistogram;
	}
	
	public String getConfigSummary() {
		return String.format("%d topics, %d topic bits, %s topic mask", this.numTopics, this.topicBits, Integer.toBinaryString(topicMask));
	}
	
	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeLong(serialVersionUID);
		
		out.writeInt(numTypes);
		out.writeInt(numTopics);

		out.writeInt(topicMask);
		out.writeInt(topicBits);

		out.writeObject(typeTopicCounts);
		out.writeObject(topicTermScores);

       out.writeObject(typeTotals);
       out.writeInt(maxTypeCount);
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		
		long version = in.readLong();
		
		if (version != serialVersionUID)
			throw new InvalidClassException(this.getClass().getName(), 
					"Expected version " + serialVersionUID + " but found " + version);

		numTypes  = in.readInt();
		numTopics = in.readInt();

		topicMask = in.readInt();
		topicBits = in.readInt();
		
		typeTopicCounts = (int[][])  in.readObject();
		topicTermScores = (double[]) in.readObject();
		
		typeTotals   = (int[])  in.readObject();
		maxTypeCount = in.readInt();
	}

	
    public int getNumTypes() { return this.numTypes;}

    public boolean typeExists(int type) {
        return (type < this.numTypes 
                && this.typeTopicCounts[type] != null
                && this.typeTopicCounts[type].length != 0);
    }
    
    public int getTypeTopicCount(int type, int i) {
        
        return typeTopicCounts[type][i] & topicMask;
    }
    
    
    public interface TopicFunction {
        public void doSomething(int topic, int count, double topicTermScore);
    };


    public void doForType(int type, TopicFunction f) {
        
        int[] topicCounts = typeTopicCounts[type];

        int index = 0;
        while (index < topicCounts.length && topicCounts[index] > 0) {

            int topic = topicCounts[index] & topicMask;
            int count = topicCounts[index] >> topicBits;

            f.doSomething(topic, count, topicTermScores[index]);

            index++;
        }
    }
}
