import mitll._


object wrapper {
  def main(args : Array[String]) {
    var newsRunner = new mitll.SCORE("/Users/je24095/Desktop/repos/mira/scala/mira4/models/news4L-500-default.lid")
    var (language, confidence) = newsRunner.textLID("what language is this text string?")
    var langConfArray : Array[(Double,Symbol)] = newsRunner.textLIDFull("what language is this text string?")
    println(language)
    println(confidence) 
    for (item <- langConfArray) {
      println(item)
    }
  }
}
