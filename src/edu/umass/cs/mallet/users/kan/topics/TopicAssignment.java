package edu.umass.cs.mallet.users.kan.topics;

import java.io.Serializable;
import cc.mallet.types.*;

/** This class combines a sequence of observed features
 *   with a sequence of hidden "labels".
 */

public class TopicAssignment implements Serializable {

    private static final long serialVersionUID = 1;
    
	public Instance instance;
	public LabelSequence topicSequence;
	public Labeling topicDistribution;
                
	public TopicAssignment (Instance instance, LabelSequence topicSequence) {
		this.instance = instance;
		this.topicSequence = topicSequence;
	}
	
	public FeatureSequence getTokens() { return (FeatureSequence) instance.getData(); }
	public int[] getTopics() { return topicSequence.getFeatures(); }
	public Alphabet getTypeAlphabet() { return instance.getDataAlphabet(); }
	
	public String typeStringFromTypeIndex(int type)
	{
	    Alphabet alphabet = getTypeAlphabet();
	    
	    return (alphabet == null) ? null : (String) alphabet.lookupObject(type);
	}
}
