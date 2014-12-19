Learner:
------------------------------------------------------------------------------
The NaiveBayesLearner.java program is essentially a full text cleaning and classifying library.  It classifies by using an optimized version of the Naive Bayes algorithm. The program is a simplified and generalized version of a portion of the work I did while working at Infosys in Summer 2014.

For a quick summary of its inner workings, it uses Porter's stemming and stopword removal, in addition to several other filters, to clean text data. It performs feature selection (you can choose among chi-squared and mutual information feature selection methods, as well as feature selectivity) to reduce the vocabulary. It then performs computations and stores data as necessitated for facile implementation of Naive Bayes classification.

Usage:
------------------------------------------------------------------------------
To use it on your own data, store your training data in an EXCEL (.xlsx) spreadsheet, with the category/class name of a given datum in column 1, and all of the corresponding text in column 2. Store your testing data (in the same format as training data) in a second .xlsx spreadheet. Then, in a client program, construct the a NaiveBayesLearner object with this input, and perform the "classify" operation on the testing data.

