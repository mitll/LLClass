###To Build
>> ./mk.sh

###Build Dependencies
* scala 2.10.6
* Java 1.8
* fastjar (Ubuntu)

### To Compile Source Code and Build
Go to top-level directory (ie. ../mira4) and type:
```
sbt assembly
```
Running this command will cause SBT to download some dependencies, this may take some time depending on your internet connection. If you use a proxy, you may need to adjust your local proxy settings to allow SBT to fetch dependencies.

This creates a jar in the folder called ../mira4/target
```
[info] Packaging ../mira4/target/scala-2.10/MITLL_LID-assembly-1.0.jar ...
```
For ease of use during tasks, you can rename the jar and put it in the top-level directory:
```
mv ../mira4/target/scala-2.10/MITLL_LID-assembly-1.0.jar ../mira4/MITLL-LID.jar 
```

### Data Format Description
* The data should be separated tab-separated between label and document example. Each document example should be newline-separated

##### Example Data Format:
```
en	this is english.
fr	quelle langue est-elle?
```

### Default MIRA Parameters - when unspecified, these parameters are automatically set:
* split: 0.10 (90/10 train-test split)
* word-ngram-order: 1
* char-ngram-order: 3
* bkg-min-count: 2
* slack: 0.01
* iterations: 20


### Quickstart:
```
java -jar MITLL_LID.jar LID -all test/news4L-500each.tsv.gz
```

### Quickstart Expected Results:
```
(truncated from above)
2015-10-05 15:56:25.912 [INFO]     Completed training
2015-10-05 15:56:25.912 [INFO]     Training complete.
2015-10-05 15:56:27.325 [INFO]     # of trials: 200
2015-10-05 15:56:27.325 [INFO]                        ru         fa         es        dar          N    class %
2015-10-05 15:56:27.325 [INFO]             ru         50          0          0          0         50   1.000000
2015-10-05 15:56:27.325 [INFO]             fa          0         46          0          4         50   0.920000
2015-10-05 15:56:27.326 [INFO]             es          0          0         50          0         50   1.000000
2015-10-05 15:56:27.326 [INFO]            dar          0          0          0         50         50   1.000000
2015-10-05 15:56:27.326 [INFO]     accuracy = 0.980000
```

### MITLL-LID Options
* Model - save a LID model for later application onto new data
* Log - log the parameters, accuracy per language, overall accuracy, debugging
* Score - generate a file with LID scores on each sentence 
* Data - use (-all) to generate models or do a train/test split. Data should be in TSV format and gzipped (gzip myfile.tsv)
* Train/Test - if not using (-all with optional -split), then specify separate train and test sets (-train mytrain.tsv.gz -test mytest.tsv.gz). This is useful for training out-of-domain followed by testing in-domain.
* Output Files - Scored files, model files, and log files are only saved when the user specifies them on command line at runtime


#### Use 85/15 train/test split and run for 10 iterations (optional - specify and save the resulting model, log and score files):
```
java -jar MITLL_LID.jar LID -all test/news4L-500each.tsv.gz -split 0.15 -iterations 10
```


#### Save score files, model files, and log files, use 85/15 train/test split:
```
java -jar MITLL_LID.jar LID -all test/news4L-500each.tsv.gz -split 0.15 -iterations 30 -model news4L.mod -log news4L.log -score news4L.score
```

#### Apply an existing model to new test data (optional - specify and save the resulting log and score files):
```
java -jar MITLL_LID.jar LID -test new.tsv.gz -model old.mod
```

<<<<<<< HEAD
###To run with separate train/test sets:
>> java -jar classylid.jar -train data1.tsv.gz -test data2.tsv.gz -model model.mod -log log.log -score score.score

###sbt behind a firewall
* You may need to add a repositories file like this under your ~/.sbt directory:

>> 515918-mitll:.sbt $ cat repositories

>> [repositories]

>>  local

>>  my-ivy-proxy-releases: http://repo.typesafe.com/typesafe/ivy-releases/, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]

>>  my-maven-proxy-releases: http://repo1.maven.org/maven2/

###sbt build notes
* To build a stand alone jar, do :

>> sbt assembly
=======
#### Train and test on different data sets (optional - specify and save the resulting model, log, and score files):
```
java -jar MITLL_LID.jar LID -train data1.tsv.gz -test data2.tsv.gz
```
>>>>>>> bce7b1150bded0db62c3c06f0655396925fce7b5
