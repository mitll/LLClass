###Build Dependencies
* scala 2.11.8
* Java 1.8

### To Compile Source Code and Build
At top-level directory type:
```
sbt assembly
```
Running this command will cause SBT to download some dependencies, this may take some time depending on your internet connection. If you use a proxy, you may need to adjust your local proxy settings to allow SBT to fetch dependencies.

This creates a jar under target :
```
[info] Packaging ... target/scala-2.11/LLClass-assembly-1.0.jar
```
For ease of use during tasks, you can rename the jar and put it in the top-level directory:
```
mv target/scala-2.10/LLClass-assembly-1.0.jar ./LLClass.jar 
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
java -jar LLClass.jar LID -all test/news4L-500each.tsv.gz
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
2015-10-05 15:56:27.326 [INFO]     accuracy = 0.985
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
java -jar LLClass.jar LID -all test/news4L-500each.tsv.gz -split 0.15 -iterations 10
```


#### Save score files, model files, and log files, use 85/15 train/test split:
```
java -jar LLClass.jar LID -all test/news4L-500each.tsv.gz -split 0.15 -iterations 30 -model news4L.mod -log news4L.log -score news4L.score
```

#### Apply an existing model to new test data (optional - specify and save the resulting log and score files):
```
java -jar LLClass.jar LID -test new.tsv.gz -model old.mod
```

#### Train and test on different data sets (optional - specify and save the resulting model, log, and score files):
```
java -jar LLClass.jar LID -train data1.tsv.gz -test data2.tsv.gz
```

###To run with separate train/test sets specify model and score files:
``` 
java -jar LLClass.jar -train data1.tsv.gz -test data2.tsv.gz -model model.mod -log log.log -score score.score
```

#### The wrapper class can be instantiated inside of another Java/Scala program. There are two functions to score text. The function textLID() returns the language code and a confidence value for that code. The function textLIDFull() returns a set of language labels ranked by most likely to least likely and a confidence value for each one. 

### Use the class mitll.SCORE
1) create an instance of the SCORE class and Sspecify the LID model
```
var newsRunner = mitll.SCORE("path/to/lid/model")
```
2) call the function mitll.SCORE.textLID()
```
var (language, confidence) = newsRunner.textLID("what language is this text string?")
```
3) call the function mitll.SCORE.textLIDFull()
```
var langConfArray : Array[(String,Double)] = newsRunner.textLIDFull("what language is this text string?")
```

### Tests
* See LIDSpec for more usage examples.

###sbt behind a firewall
* You may need to add a repositories file like this under your ~/.sbt directory:

```
515918-mitll:.sbt $ cat repositories
[repositories]
  local
  my-ivy-proxy-releases: http://repo.typesafe.com/typesafe/ivy-releases/, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  my-maven-proxy-releases: http://repo1.maven.org/maven2/
```

#### Twitter Results

### 11 Languages 

```
2016-04-15 16:11:37.257 [INFO]     # of trials: 825
2016-04-15 16:11:37.258 [INFO]                        zh         uk         ru         no         nl         ko         id         fa         en         da         ar          N    class %
2016-04-15 16:11:37.258 [INFO]             zh         74          1          0          0          0          0          0          0          0          0          0         75   0.986667
2016-04-15 16:11:37.258 [INFO]             uk          1         40         28          2          0          0          0          2          0          2          0         75   0.533333
2016-04-15 16:11:37.258 [INFO]             ru          0          4         71          0          0          0          0          0          0          0          0         75   0.946667
2016-04-15 16:11:37.258 [INFO]             no          1          1          0         28         11          1         10          0          8         15          0         75   0.373333
2016-04-15 16:11:37.258 [INFO]             nl          1          0          0          3         57          0          6          0          4          4          0         75   0.760000
2016-04-15 16:11:37.258 [INFO]             ko          0          0          0          0          0         74          0          0          1          0          0         75   0.986667
2016-04-15 16:11:37.258 [INFO]             id          1          0          0          1          2          0         68          0          2          1          0         75   0.906667
2016-04-15 16:11:37.258 [INFO]             fa          1          1          0          0          0          0          1         69          0          0          3         75   0.920000
2016-04-15 16:11:37.258 [INFO]             en          1          1          0          3          0          0          3          0         57         10          0         75   0.760000
2016-04-15 16:11:37.258 [INFO]             da          0          0          0          9          1          0          4          1          6         54          0         75   0.720000
2016-04-15 16:11:37.258 [INFO]             ar          0          0          0          0          0          0          0          1          0          0         74         75   0.986667
2016-04-15 16:11:37.258 [INFO]     accuracy = 0.807273
```

### 4 Languages 5K each

```
2016-04-15 16:17:37.165 [INFO]     # of trials: 3000
2016-04-15 16:17:37.166 [INFO]                        no         nl         en         da          N    class %
2016-04-15 16:17:37.166 [INFO]             no        652         41         36         21        750   0.869333
2016-04-15 16:17:37.166 [INFO]             nl         20        706         19          5        750   0.941333
2016-04-15 16:17:37.166 [INFO]             en         31         13        685         21        750   0.913333
2016-04-15 16:17:37.166 [INFO]             da         84         23         18        625        750   0.833333
2016-04-15 16:17:37.166 [INFO]     accuracy = 0.889333
```

### 4 Languages 500 each

```
2016-04-15 16:20:11.074 [INFO]     # of trials: 300
2016-04-15 16:20:11.075 [INFO]                        no         nl         en         da          N    class %
2016-04-15 16:20:11.075 [INFO]             no         45         13          7         10         75   0.600000
2016-04-15 16:20:11.075 [INFO]             nl          3         63          4          5         75   0.840000
2016-04-15 16:20:11.075 [INFO]             en          7          0         57         11         75   0.760000
2016-04-15 16:20:11.075 [INFO]             da         20          3          4         48         75   0.640000
2016-04-15 16:20:11.075 [INFO]     accuracy = 0.710000
```
