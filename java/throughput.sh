ANTLR_JAR="`pwd`/lib/antlr-4.10.1-complete.jar"
HEAP_JAR="`pwd`/lib/hprof-heap-0.16.jar"

N="10"

echo "SPARQL"
java -Xmx10G -cp "out:$ANTLR_JAR:$HEAP_JAR" TestANTLR4 Sparql query -files '.*\.txt' -n $N input/sparql

echo "Java"
java -Xmx10G -cp "out:$ANTLR_JAR:$HEAP_JAR" TestANTLR4 Java compilationUnit -files '.*\.java' -n $N grammars

echo "postgresql"
java -Xmx10G -cp "out:$ANTLR_JAR:$HEAP_JAR" TestANTLR4 PostgreSQL root -files '.*\.sql' -n $N input/postgresql
