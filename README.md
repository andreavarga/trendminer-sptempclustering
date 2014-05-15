# Trendminer Spatio-temporal clustering

Spatio-temporal clustering methods developed as part of the [Deliverable 3.3.1](http://www.trendminer-project.eu/images/d3.3.1.pdf)  of the [Trendminer FP7 project] (http://www.trendminer-project.eu)

## Prerequisites

+ Java v1.6+
+ [Mallet v2.0.7+](http://mallet.cs.umass.edu/download.php)

This application implements three main functionalities:

1. transformation of the data from the Trendminer format to Mallet format
2. creation of train and test instance files
3. execution of spatio-temporal clustering using the [Dirichlet-multinomial regression model (DMR)](http://arxiv.org/ftp/arxiv/papers/1206/1206.3278.pdf)

## 1) Data tansformation

Transforms data from the Trendminer format to the Mallet format. The data is in a folder containing the following files:

+ dictionary: the dictionary used in the format 'word\_id word', one word/line, starts with 0
+ dates: the map to the actual calendar days in the format 'day\_id YYYY-MM-DD', one date/line, starts with 0
+ cities: the map to the city names in the format 'city\_id city', one city/line, starts with 0
+ GEO: the map with details of each city in the format 'city\_id city latitude longitude country', one city/line, starts with 0
+ sora\_vs: the data transformed in the following format 'word\_id day\_id city\_id[TAB]frequency', one word/line, representing the frequency of word\_id on day\_id and in city\_id

Several setups are available for transforming the data for clustering, based on the methods decribed in [D3.3.1](http://www.trendminer-project.eu/images/d3.3.1.pdf):

1) Monthly indicator features (Mid):

	java -Xmx6G -jar dist/trendminer-sptempclustering-importer.jar --startMonth 06 --endMonth 06 --startYear 2012 --endYear 2013 --useMonthlyIndicatorFeatures true --mainDir data/ 

mainDir represents the data directory; startMonth, startYear, endMonth, endYear indicate the time interval of the data to be processed

2) Temporal smoothing with RBF kernels (TimeRBF):

	java -Xmx6G -jar dist/trendminer-sptempclustering-importer.jar --sigma 30 --startMonth 06 --endMonth 06 --startYear 2012 --endYear 2013 --useMonthlyIndicatorFeatures true --mainDir data/

RBF kernels are situated equidistant at the middle of every month. The sigma parameter indicates the RBF width. 

3) To use city indicator features add the parameter:

	--useCityFeatures true

4) To use country indicator features add the parameter:

	--useCountryFeatures true

5) To use spatial smoothing features add the parameters:

	--geokernel true --sigma\_GEO=2

This represents the width of the RBF kernel. RBF kernels are situated with the center in each city in the city list.


## 2) Splitting the data into training and test set

This stage splits the data into two disjoint files, one for training and one for testing. Input is the file created at the previous stage and the proportion of training data.

	java -Xmx6G -jar dist/trendminer-sptempclustering-instancecreator.jar --instancesMalletFile data/mallet_file --trainingportion 0.7

## 3) Spatio-temporal clustering

The spatio-temporal clustering can be run using the files generated at the previous step as input.
	
	java -Xmx6G -jar dist/trendminer-sptempclustering.jar --trainInstanceList data/mallet_train_file --testInstanceList data/mallet_test_file --outputFolder output/ --nrTopics 100 --topWords 10

where mallet\_train\_file and mallet\_test\_file represent paths to the training and test files, output/ is the output folder of the model, nrTopics is the number of topics and topWords (optional) represents the top number of words which describe a topic in the output files.

The output folder contains:
+ perplexity.txt: the perplexity on the held-out test data
+ topics.txt: the top topWords words in each topic, one topic/line
+ \_parameters\_: the coeficients (weights) for each tempora/spatial feature, one file/topic

## References

Daniel Preotiuc-Pietro, Sina Samangooei, Andrea Varga, Douwe Gelling, Trevor Cohn, Mahesan Niranjan
[Tools for mining non-stationary data - v2. Clustering models for discovery of regional and demographic variation - v2.](http://www.trendminer-project.eu/images/d3.3.1.pdf)
Public Deliverable for Trendminer Project, 2014.
