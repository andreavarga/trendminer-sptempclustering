trendminer-sptempclustering
===========================

Spatio-temporal clustering methods developed as part of the D3.3.1 deliverable of the Trendminer FP7 project (http://www.trendminer-project.eu)

Prerequisites

*Java v1.6+

*mallet-2.0.7+ (http://mallet.cs.umass.edu/download.php)

This application implements three main functionalities:

1) transformation of the data from trendminer format to mallet format

2) creation of train and test instance files

3) execution of spatio-temporal clustering

===========================

1) Data tansformation

A set of required data files (in trendminer format) are stored in the "sample-data" directory as follows:

*sample-data/dictionary: contains an index of the vocabulary found in the documents

*sample-data/dates: contains a list of dates capturing the time frame in which the documents were published, indexed from 0 

*sample-data/cities: contains a list of cities where the documents were published, index from 0

*sample-data/GEO: stores the latitude, longitude and country information for each city

*sample-data/sora_vs: stores the data indexed according to indexed vocabulary (from "sample-data/dictionary"), the indexed dates (from "/sample-data/dates"), indexed cities (from "/sample-data/cities"), in the format:

 date_id[SPACE]user_id[SPACE]token_id[TAB]frequency
  