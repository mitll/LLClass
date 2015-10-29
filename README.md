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

### Local Version Settings (known to build and run in this environment)
* scala 2.10.6
* Java 1.7


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