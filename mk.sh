echo "compiling classylid (Scala)"
scalac -encoding UTF-8 -classpath lib/arg.jar:lib/avaje-ebeanorm-agent-3.1.1.jar:lib/commons-email-1.2.jar:lib/commons-io-2.4.jar:lib/commons-lang-2.5.jar:lib/jtr.jar:lib/libsvm.jar:lib/mail.jar:lib/mallet-deps.jar:lib/mallet.jar:lib/structlearn.jar src/*.scala
echo "creating classylid.jar"
jar cvmf MANIFEST.MF classylid.jar mitll/* lib/*
echo "done."
echo
