trendminer-sptempclustering
===========================

Spatio-temporal clustering methods developed as part of the D3.3.1 deliverable of the [Trendminer FP7 project] (http://www.trendminer-project.eu)

##### Prerequisites

-Java v1.6+

-[mallet v2.0.7+](http://mallet.cs.umass.edu/download.php)

This application implements three main functionalities:

1) transformation of the data from trendminer format to mallet format

2) creation of train and test instance files

3) execution of spatio-temporal clustering


##### 1) Data tansformation
===========================


A set of required data files (in trendminer format) are stored in the "sample-data" directory as follows:

-*sample-data/dictionary*: contains an index of the vocabulary found in the documents

-*sample-data/dates*: contains a list of dates capturing the time frame in which the documents were published, indexed from 0 

-*sample-data/cities*: contains a list of cities where the documents were published, index from 0

-*sample-data/GEO*: stores the latitude, longitude and country information for each city

-*sample-data/sora_vs*: stores the data according to indexed vocabulary (from "sample-data/dictionary"), the indexed dates (from "/sample-data/dates"), indexed cities (from "/sample-data/cities"), in the format:

 date_id[SPACE]city_id[SPACE]token_id[TAB]frequency
  

Several settings are available for importing the data for clustering:

a) import the data using monthly indicator features:

java -Xmx6G -jar trendminer-sptempclustering.jar --startMonth 06 --endMonth 06 --startYear 2012 --endYear 2013 --useMonthlyIndicatorFeatures true --mainDir [YOURDATADIR]

b) import the data using temporal smoothing features, sigma=30:

java -Xmx6G -jar trendminer-sptempclustering.jar --sigma 30 --startMonth 06 --endMonth 06 --startYear 2012 --endYear 2013 --mainDir [YOURDATADIR]

c) import the data using temporal smoothing features (sigma=30), and city indicator features:

java -Xmx6G -jar trendminer-sptempclustering.jar --sigma 30 --startMonth 06 --endMonth 06 --startYear 2012 --endYear 2013 --useCityFeatures true --mainDir [YOURDATADIR]

d) import the data using temporal smoothing features (sigma=30), and country indicator features:

java -Xmx6G -jar trendminer-sptempclustering.jar --sigma 30 --startMonth 06 --endMonth 06 --startYear 2012 --endYear 2013 --useCountryFeatures true --mainDir [YOURDATADIR]

e) import the data using temporal smoothing features (sigma=30), and country indicator features, and regional smooring feature sigma_GEO=2:

java -Xmx6G -jar trendminer-sptempclustering.jar --sigma 30 --startMonth 06 --endMonth 06 --startYear 2012 --endYear 2013 --useCountryFeatures true  --geokernel true --sigma_GEO 2 --mainDir [YOURDATADIR]

##### 2) Splitting the data into training and test set
===========================
Having the instances created by one of the options described in 1), the next is to split the instances into a training and test datasets:

java -Xmx6G -jar trendminer-sptempclustering.jar --instancesMalletFile [YOURMALLETINSTANCE] --trainingPortion 0.7

##### 3) Spatio-temporal clustering
===========================

Once the train and test instances have been created as described in 2), the spatio-temporal clustering model can be executed as follows:

a) java -Xmx6G -jar trendminer-sptempclustering.jar --trainInstanceList [YOURMALLETINSTANCE] --testInstanceList [YOURMALLETINSTANCE] --outputFolder [YOUROUTPUTDIR] --nrTopics 100

or

b) java -Xmx6G -jar trendminer-sptempclustering.jar --trainInstanceList [YOURMALLETINSTANCE] --testInstanceList [YOURMALLETINSTANCE] --outputFolder [YOUROUTPUTDIR] --nrTopics 100 --topWords 10

by setting the number of words to be displayed for each topic
