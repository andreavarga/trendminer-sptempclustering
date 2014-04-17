/*=============================================================================
  TopicProgressLogger.java
                                    Created by jkan on Sep 2, 2009
                                    Copyright (c)2009 Essbare Weichware, GmbH
                                    All rights reserved.
  =============================================================================*/

package edu.umass.cs.mallet.users.kan.topics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import cc.mallet.types.Alphabet;


/**
    TopicProgressLogger
        
        contains useful code to do useful things....
        
        @author jkan
 **/
public class TopicProgressLogger 
{
	int			iteration;
	PrintStream	stream;
	Alphabet    typeAlphabet;
	int         loggedType = 15;
    
	public TopicProgressLogger(String filename)
	{
		this(new File(filename));
	}

	public TopicProgressLogger(File file)
	{
		try {
			this.stream = new PrintStream(file);
		} catch (FileNotFoundException e) {
			System.err.println("Failed to open progress log file: " + file.getPath());
			System.err.println(e.getMessage());
		}
	}
	
	public void setTypeAlphabet(Alphabet typeAlphabet)
    {
        this.typeAlphabet = typeAlphabet;
    }
	
	public void beginIteration(int i)
	{
		this.iteration = i;
	}
	
	public boolean isLoggedType(int type)
	{
		return type == loggedType;
	}
	
	private String getTypeAsString(int type)
	{
        String strType = (this.typeAlphabet == null) ? null : (String) this.typeAlphabet.lookupObject(type);

        if (strType == null)
            strType = "???";
        
        return strType;
	}
	
	private void printTypeTopicCounts(final TypeTopicCounts typeTopicCounts, final String label, final int type, final int oldTopic, final int newTopic)
	{
        stream.format("%4d %s%6d%20s ", iteration, label, type, getTypeAsString(type));
        
        typeTopicCounts.doForType(type, new TypeTopicCounts.TopicFunction() {
            
            public void doSomething(int topic, int count, double topicTermScore)
            {
                stream.format("%3d%c%d/%-5.0f", topic, 
                        (topic == newTopic ? '+' : topic ==  oldTopic ? '-' : ':'), 
                        count, 
                        topicTermScore*1000);
            }
        });
        
        stream.println();
	}
	
	public void updateTopicInTermMass(TypeTopicCounts typeTopicCounts, int type, int topic)
	{
		if (isLoggedType(type)) 
		    printTypeTopicCounts(typeTopicCounts, "t", type, -1, topic);
	}

	public void updateTopicInSmoothingMass(TypeTopicCounts typeTopicCounts, int type, int topic, boolean inBetaMass)
	{
		if (isLoggedType(type))
            printTypeTopicCounts(typeTopicCounts, inBetaMass?"b":"s", type, -1, topic);
	}
	
	public void close()
	{
		if (this.stream != null)
			this.stream.close();
	}
}
