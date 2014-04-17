/*===========================================================================
  StochasticMetaOptimizer.java
                               Created by kan on Dec 21, 2010
                               Copyright (c)2010 Intangible Industries, LLC
                               All rights reserved.
  ===========================================================================*/

package edu.umass.cs.mallet.users.kan.topics;

import java.util.Random;

import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.Optimizer;
import cc.mallet.optimize.StochasticMetaAscent;

/**
    StochasticMetaOptimizer
        
        abstracts away the differences between the L-BFGS and StochasticMetaAscent
        interfaces so that they can be run interchangeably.  In particular, it 
        implements the Optimizer interface that LimitedMemoryBFGS also implements.
        
        @author kan
 **/
public class StochasticMetaOptimizer extends StochasticMetaAscent implements Optimizer
{
    Optimizable.ByBatchGradient mt;
    int                         numBatches;
    int[]                       batchAssignments; // i.e. which batch each instance belongs to
    boolean                     isConverged;
    Random                      random;

    
    public StochasticMetaOptimizer(Optimizable.ByBatchGradient mt, int numBatches, int numInstances, Random random)
    {
        super(mt);
        
        this.mt               = mt;
        this.isConverged      = false;
        this.numBatches       = numBatches; 
        this.batchAssignments = new int[numInstances]; 
        this.random           = random;
        
        assignBatches(this.numBatches, this.batchAssignments);
        
//        this.setUseHessian(useHessian);
//        this.setInitialStep(initialStep);
//        this.setMu(mu);
    }
    
    /**
         Randomly partition the training data into n batches
         
         @param numBatches
         @param batchAssignments - an int array containing the batch 
                 number of each instance in the training data
     **/
    private void assignBatches(int numBatches, int[] batchAssignments)
    {
        if (batchAssignments.length < 5000)
        {
            // make sure the batches are evenly sized
            
            int batch = 0;
            
            for (int i = 0; i < batchAssignments.length; ++i)
            {
                batchAssignments[i] = batch;
                batch = (batch + 1) % numBatches;
            }
            for (int i = batchAssignments.length-1; i >= 1; --i) 
            {
                int j   = random.nextInt(i);
                int tmp = batchAssignments[i];
                
                batchAssignments[i] = batchAssignments[j];
                batchAssignments[j] = tmp;
            }
        }
        else
        {
            for (int i = 0; i < batchAssignments.length; i++)
                batchAssignments[i] = random.nextInt(numBatches);
        }
    }
      

    public boolean optimize()
    {
        this.isConverged = this.optimize(numBatches, batchAssignments);

        return this.isConverged;
    }

    public boolean optimize(int numIterations)
    {
        this.isConverged = this.optimize(numIterations, numBatches, batchAssignments);

        return this.isConverged;
    }

    public Optimizable.ByBatchGradient getOptimizable()
    {
        return this.mt;
    }

    public boolean isConverged()
    {
        return this.isConverged;
    }
}

