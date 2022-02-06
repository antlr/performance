ANTLR_JAR="`pwd`/lib/antlr4.10-SNAPSHOT-complete.jar"

N="10"

echo "SPARQL"
java -Xmx10G -cp "out:$ANTLR_JAR" TestANTLR4 Sparql query -files '.*\.txt' -n $N input/sparql

echo "Java"
java -Xmx10G -cp "out:$ANTLR_JAR" TestANTLR4 Java compilationUnit -files '.*\.java' -n $N grammars

echo "postgresql"
java -Xmx10G -cp "out:$ANTLR_JAR" TestANTLR4 PostgreSQL root -files '.*\.sql' -n $N input/postgresql
