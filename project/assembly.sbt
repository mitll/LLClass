resolvers += Resolver.url("my-test-repo", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.0")
