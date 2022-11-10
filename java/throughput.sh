ANTLR_JAR="`pwd`/lib/antlr-4.11.2-SNAPSHOT-complete.jar"
HEAP_JAR="`pwd`/lib/hprof-heap-0.16.jar"

N="20"
SKIP="5"

echo "SPARQL"
java -Xmx10G -cp "out:$ANTLR_JAR:$HEAP_JAR" TestANTLR4 Sparql query -files '.*\.txt' -n $N -skip $SKIP input/sparql

echo "Java"
java -Xmx10G -cp "out:$ANTLR_JAR:$HEAP_JAR" TestANTLR4 Java compilationUnit -files '.*\.java' -n $N -skip $SKIP grammars

echo "postgresql"
java -Xmx10G -cp "out:$ANTLR_JAR:$HEAP_JAR" TestANTLR4 PostgreSQL root -files '.*\.sql' -n $N -skip $SKIP input/postgresql
