/*===========================================================================
  LogFormatter.java
                               Created by kan on Jan 29, 2011
                               Copyright (c)2011 Intangible Industries, LLC
                               All rights reserved.
  ===========================================================================*/

package edu.umass.cs.mallet.users.kan.util;

import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;


/**
    LogFormatter
        
        contains useful code to do useful things....
        
        @author kan
 **/
public class LogFormatter extends SimpleFormatter
{
    public LogFormatter() { super(); }
    
    /**
         @see java.util.logging.SimpleFormatter#format(java.util.logging.LogRecord)
     **/
    @Override
    public synchronized String format(LogRecord record)
    {
        return "[" + record.getLoggerName() + "] " + record.getMessage() + "\n";
    }

}
