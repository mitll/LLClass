### Intro
LLClass can be used for a number of text classification problems including:

* Language Identification (LID)
* Automatic text difficulty assessment (Auto ILR)
* Sentiment analysis

It includes a number of different classifiers including MIRA, SVM, and a perceptron.

It also includes a simple REST service for doing classification and some pre trained models.

More documentation can be found under docs : [Auto ILR Paper](docs/Shen_Williams_Marius_Salesky_ACL2013.pdf)

* For performance benchmarks, see below.

###Build Dependencies
* scala 2.11.8
* Java 1.8

### To Compile Source Code and Build
At top-level directory type:
```
sbt assembly
```
Running this command will cause SBT to download some dependencies, this may take some time depending on your internet connection. If you use a proxy, you may need to adjust your local proxy settings to allow SBT to fetch dependencies.

This creates a jar under target at 
```
[info] Packaging ... target/scala-2.11/LLClass-assembly-1.0.jar
```
For examples below, you can add a link to the jar:
```
ln -s target/scala-2.11/LLClass-assembly-1.0.jar LLClass.jar
```

### Data Format Description
* The data should one line per example, separated with a tab or whitespace between label and document.

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


#### Use 85/15 train/test split and run for 10 iterations:
```
java -jar LLClass.jar LID -all test/news4L-500each.tsv.gz -split 0.15 -iterations 10
```


#### Save score files, model files, and log files, use 85/15 train/test split (optional - specify and save the resulting model, log and score files):
```
java -jar LLClass.jar LID -all test/news4L-500each.tsv.gz -split 0.15 -iterations 30 -model news4L.mod -log news4L.log -score news4L.score
```

#### Apply an existing model to new test data 
```
java -jar LLClass.jar LID -test new.tsv.gz -model models/news4L.mod
```

#### Train and test on different data sets
```
java -jar LLClass.jar LID -train data1.tsv.gz -test data2.tsv.gz
```

###To run with separate train/test sets specify model and score files (optional - specify and save the resulting model, log, and score files):
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

### REST service

* Start the service

```
java -jar LLClass.jar REST
```

* Simple text call in a browser (http://localhost:8080/classify?q=...)

```
http://localhost:8080/classify?q=No%20quiero%20pagar%20la%20cuenta%20del%20hospital.
```
```
es
```

* Curl

```
curl --noproxy localhost http://localhost:8080/classify?q=Necesito%20pagar%20los%20servicios%20de%20electricidad%20y%20cable.
es
```

* return JSON (http://localhost:8080/classify/json?q=...)

```
http://localhost:8080/classify/json?q=%22Necesito%20pagar%20los%20servicios%20de%20electricidad%20y%20cable.%22
```

```javascript
{
class: "es",
confidence: "47.82"
}
```

* Model labels
```
http://localhost:8080/labels
```

```javascript
{
labels: [
"dar",
"es",
"fa",
"ru"
]
}
```

* JSON scores for all labels in model (http://localhost:8080/classify/all/json?q=...)

```
http://localhost:8080/classify/all/json?q=%22Necesito%20pagar%20los%20servicios%20de%20electricidad%20y%20cable.%22
```

```javascript
results: [
{
class: "es",
confidence: 0.7623826612993418
},
{
class: "dar",
confidence: -0.128890820239746
},
{
class: "ru",
confidence: -0.2614167250657798
},
{
class: "fa",
confidence: -0.37207511599381426
}
]
}
```

* See RESTServiceSpec for details

### Tests
* LIDSpec has more usage examples. 
* EvalSpec has tests that show swapping out an LLClass classifier for a langid.py service classifier.
* TwitterEvalSpec has tests for running against twitter data from [Evaluating language identification performance](https://blog.twitter.com/2015/evaluating-language-identification-performance)
* RESTServiceSpec shows variations on running the RESTService 

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

### Twitter : Evaluating language identification performance 

From [Evaluating language identification performance](https://blog.twitter.com/2015/evaluating-language-identification-performance)

#### Precision dataset

Note that the precision_oriented dataset had 69000 tweets but we only could actually download 51567 tweets.

##### Train/Test 85/15 split all labels, no text normalization, minimum 500 examples per label

Labels with fewer than 500 examples were excluded.

|Info|Value|
|----|-----|
|Train|44352|
|Test|6654|
|Labels(44)|ar,bn,ckb,de,el,en,es,fa,fr,gu,he,hi,hi-Latn,hy,id,it,ja,ka,km,kn,lo,ml,mr,my,ne,nl,pa,pl,ps,pt,ru,sd,si,sr,sv,ta,te,th,und,ur,vi,zh-CN,zh-TW|
|Accuracy|0.86654645|

##### Train/Test 85/15 split all labels, no text normalization but skip the und label

The und label marked undefined tweets which could match several languages.

|Info|Value|
|----|-----|
|Train|34546|
|Test|5183|
|Labels(43)|ar,bn,ckb,de,el,en,es,fa,fr,gu,he,hi,hi-Latn,hy,id,it,ja,ka,km,kn,lo,ml,mr,my,ne,nl,pa,pl,ps,pt,ru,sd,si,sr,sv,ta,te,th,ur,vi,zh-CN,zh-TW|
|Accuracy|0.949836|

This model can be found in the release directory if you want to try it yourself.

##### Train/Test 85/15 split all labels, with text normalization, minimum 500 examples per label

Ran a python script to attempt to normalize tweet text to remove markup, hashtags, etc.

|Info|Value|
|----|-----|
|Train|44080|
|Test|6614|
|Labels(44)|ar,bn,ckb,de,el,en,es,fa,fr,gu,he,hi,hi-Latn,hy,id,it,ja,ka,km,kn,lo,ml,mr,my,ne,nl,pa,pl,ps,pt,ru,sd,si,sr,sv,ta,te,th,und,ur,vi,zh-CN,zh-TW|
|Accuracy|0.86815846|

##### Train/Test 85/15 split all labels, with text normalization but skip the und label

|Info|Value|
|----|-----|
|Train|34540|
|Test|5183|
|Labels(43)|ar,bn,ckb,de,el,en,es,fa,fr,gu,he,hi,hi-Latn,hy,id,it,ja,ka,km,kn,lo,ml,mr,my,ne,nl,pa,pl,ps,pt,ru,sd,si,sr,sv,ta,te,th,ur,vi,zh-CN,zh-TW|
|Accuracy|0.95504534|

#### Recall

##### Train/Test 85/15 split all labels, no text normalization, minimum 500 examples per label

Initial data had 72000 of 87585 tweets from recall_oriented.

|Info|Value|
|----|-----|
|Train|71196|
|Test|10682|
|Labels (67)|am, ar, bg, bn, bo, bs, ca, ckb, cs, cy, da, de, dv, el, en, es, et, eu, fa, fi, fr, gu, he, hi, hi-Latn, hr, ht, hu, hy, id, is, it, ja, ka, km, kn, ko, lo, lv, ml, mr, my, ne, nl, no, pa, pl, ps, pt, ro, ru, sd, si, sk, sl, sr, sv, ta, te, th, tl, tr, uk, ur, vi, zh-CN, zh-TW|
|Accuracy|0.9237971|

See model in releases.

##### Train/Test 85/15 split all labels, with text normalization, minimum 500 examples per label

Initial data had 72000 of 87585 tweets from recall_oriented.

|Info|Value|
|----|-----|
|Train|71187|
|Test|10680|
|Labels (67)|am, ar, bg, bn, bo, bs, ca, ckb, cs, cy, da, de, dv, el, en, es, et, eu, fa, fi, fr, gu, he, hi, hi-Latn, hr, ht, hu, hy, id, is, it, ja, ka, km, kn, ko, lo, lv, ml, mr, my, ne, nl, no, pa, pl, ps, pt, ro, ru, sd, si, sk, sl, sr, sv, ta, te, th, tl, tr, uk, ur, vi, zh-CN, zh-TW|
|Accuracy|0.9238764|

#### Uniform

##### Train/Test 85/15 split all labels, no text normalization, minimum 500 examples per label

|Info|Value|
|----|-----|
|Train|76442|
|Test|11467|
|Labels (13)|ar, en, es, fr, id, ja, ko, pt, ru, th, tl, tr, und|
|Accuracy|0.9098282|

##### Train/Test 85/15 split all labels, with text normalization, minimum 500 examples per label

|Info|Value|
|----|-----|
|Train|74854|
|Test|11228|
|Labels (13)|ar, en, es, fr, id, ja, ko, pt, ru, th, tl, tr, und|
|Accuracy|0.9291058|

### Freetext

#### [Europarl](https://code.google.com/archive/p/language-detection/downloads)

|Info|Value|
|----|-----|
|Train|74854|
|Test|3150|
|Labels (21)|bg cs da de el en es et fi fr hu it lt lv nl pl pt ro sk sl sv|
|Accuracy|0.99841267|

See releases for model.

### Twitter 11 Languages small dataset

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



