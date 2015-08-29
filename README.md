###To Build
>> ./mk.sh

###Build Dependencies
* scala 2.10.3
* Java 1.7
* fastjar (Ubuntu)


###Data Format Description
* The data should be separated tab-separated between label and document example. Each document example should be newline-separated

#####Text Language ID Classification Sample:
* en	this is english.
* fr	quelle langue est-elle?


###Command Line Parameters Description
* Model - apply it to new data
* Log - accuracy per language, overall accuracy, debugging
* Score - each testing instance gets a score 
* Data - use (-all) to generate models or do a train/test split. Data should be in TSV format and gzipped (gzip myfile.tsv)
* Train/Test - if not using (-all) with (-split), then specify separate train and test sets (-train mytrain.tsv.gz -test mytest.tsv.gz)
* Optional - Scored files, model files, and log files won't be generated if they aren't specified at runtime

###Quick start:
>> java -jar classylid.jar -all test/original4.tsv.gz -split 0.15 -iterations 10

###To run:
>> java -jar classylid.jar -all data.gz -split 0.15 -iterations 30 -model model.mod -log log.log -score score.score

###To apply a model to some data:
>> java -jar classylid.jar -test test/data1.tsv.gz -model model.mod -log log.log -score score.score

###To run with separate train/test sets:
>> java -jar classylid.jar -train data1.tsv.gz -test data2.tsv.gz -model model.mod -log log.log -score score.score
