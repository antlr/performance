# Test the performance of ANTLR parsers (initially just Java target)

Parser performance for the same grammar across code generation targets varies a great deal for two reasons:

1. raw performance of the underlying implementation language, such as python versus C++
2. the runtime support library must be carefully tuned, such as we did for the Java target; e.g., the [hash function](https://github.com/antlr/antlr4/blob/master/runtime/Java/src/org/antlr/v4/runtime/atn/ATNConfigSet.java#L47) used has to be appropriate for our unusual use case

It's also the case that different grammars for the same language can exhibit radically different performance. For example, in the [OOPSLA paper](https://dl.acm.org/doi/pdf/10.1145/2660193.2660202) we compared the performance of two different grammars for Java. The grammar from the Java language specification converted to ANTLR notation one to one performed much worse than one we hand tune to reduce lookahead requirements.

*A note on testing the performance of ANTLR parsers.* ANTLR v4 generates ALL(\*) parsers, which use a form of decision caching in order to improve future performance on the same or similar input statements.  That implies there is a warm up period associated with the parsers before they reach their final throughput speed, and of course, Java's JIT also has a warm up period (if using the Java target).

## Build and test Java Target on 3 grammars

At this point, the test script compares the performance of a tuned Java grammar (on all generated Java code from all three grammars), Postgresql, and sparql with sample input. For convenience, a snapshot of upcoming ANTLR 4.10 release is in the `lib` dir and is used by the scripts.

```bash
cd /tmp
git clone git@github.com:antlr/performance.git
cd performance/java
```

Then you can run the build script, which will download the grammar repository, pull out the 3 grammars of interest, generate code via ANTLR:

```bash
$ ./build.sh 
Download sample grammars
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100   133  100   133    0     0    565      0 --:--:-- --:--:-- --:--:--   568
100 27.1M    0 27.1M    0     0  5110k      0 --:--:--  0:00:05 --:--:-- 6674k
Unzip sample grammars
error:  cannot create grammars-v4-master/molecule/examples/NiC2O4 -? 2H2O.txt
        Illegal byte sequence
Copy sample grammars and input
Building parsers
warning(146): PostgreSQLLexer.g4:2610:0: non-fragment lexer rule AfterEscapeStringConstantMode_NotContinued can match the empty string
warning(146): PostgreSQLLexer.g4:2629:0: non-fragment lexer rule AfterEscapeStringConstantWithNewlineMode_NotContinued can match the empty string

Compiling parsers and test rig
Note: TestANTLR4.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
```

Here is how to test the throughput:

```bash
$ ./throughput.sh 
SPARQL
4 files
Parsed 4 files 24 lines 721 bytes in  151ms at       158 lines/sec 4,774 chars/sec
Parsed 4 files 24 lines 721 bytes in    7ms at     3,428 lines/sec 103,000 chars/sec
Parsed 4 files 24 lines 721 bytes in    6ms at     4,000 lines/sec 120,166 chars/sec
Parsed 4 files 24 lines 721 bytes in   17ms at     1,411 lines/sec 42,411 chars/sec
Parsed 4 files 24 lines 721 bytes in    7ms at     3,428 lines/sec 103,000 chars/sec
Parsed 4 files 24 lines 721 bytes in    9ms at     2,666 lines/sec 80,111 chars/sec
Parsed 4 files 24 lines 721 bytes in    7ms at     3,428 lines/sec 103,000 chars/sec
Parsed 4 files 24 lines 721 bytes in   10ms at     2,400 lines/sec 72,100 chars/sec
Parsed 4 files 24 lines 721 bytes in    6ms at     4,000 lines/sec 120,166 chars/sec
Parsed 4 files 24 lines 721 bytes in    6ms at     4,000 lines/sec 120,166 chars/sec
average parse 8.857ms, min 6.582ms, stddev=3.891ms (First 3 runs skipped for JIT warmup)
Java
16 files
Parsed 16 files 137,280 lines 4,341,964 bytes in 1455ms at    94,350 lines/sec 2,984,167 chars/sec
Parsed 16 files 137,280 lines 4,341,964 bytes in  429ms at   320,000 lines/sec 10,121,128 chars/sec
Parsed 16 files 137,280 lines 4,341,964 bytes in  312ms at   440,000 lines/sec 13,916,551 chars/sec
Parsed 16 files 137,280 lines 4,341,964 bytes in  327ms at   419,816 lines/sec 13,278,177 chars/sec
Parsed 16 files 137,280 lines 4,341,964 bytes in  290ms at   473,379 lines/sec 14,972,289 chars/sec
Parsed 16 files 137,280 lines 4,341,964 bytes in  284ms at   483,380 lines/sec 15,288,605 chars/sec
Parsed 16 files 137,280 lines 4,341,964 bytes in  363ms at   378,181 lines/sec 11,961,333 chars/sec
Parsed 16 files 137,280 lines 4,341,964 bytes in  433ms at   317,043 lines/sec 10,027,630 chars/sec
Parsed 16 files 137,280 lines 4,341,964 bytes in  232ms at   591,724 lines/sec 18,715,362 chars/sec
Parsed 16 files 137,280 lines 4,341,964 bytes in  245ms at   560,326 lines/sec 17,722,302 chars/sec
average parse 310.571ms, min 232.870ms, stddev=70.249ms (First 3 runs skipped for JIT warmup)
postgresql
3 files
Parsed 3 files 5,232 lines 181,261 bytes in 5506ms at       950 lines/sec 32,920 chars/sec
Parsed 3 files 5,232 lines 181,261 bytes in 1203ms at     4,349 lines/sec 150,674 chars/sec
Parsed 3 files 5,232 lines 181,261 bytes in  812ms at     6,443 lines/sec 223,227 chars/sec
Parsed 3 files 5,232 lines 181,261 bytes in  683ms at     7,660 lines/sec 265,389 chars/sec
Parsed 3 files 5,232 lines 181,261 bytes in  737ms at     7,099 lines/sec 245,944 chars/sec
Parsed 3 files 5,232 lines 181,261 bytes in  694ms at     7,538 lines/sec 261,182 chars/sec
Parsed 3 files 5,232 lines 181,261 bytes in  619ms at     8,452 lines/sec 292,828 chars/sec
Parsed 3 files 5,232 lines 181,261 bytes in  620ms at     8,438 lines/sec 292,356 chars/sec
Parsed 3 files 5,232 lines 181,261 bytes in  623ms at     8,398 lines/sec 290,948 chars/sec
Parsed 3 files 5,232 lines 181,261 bytes in  603ms at     8,676 lines/sec 300,598 chars/sec
average parse 654.143ms, min 603.979ms, stddev=50.453ms (First 3 runs skipped for JIT warmup)
```

## Improving parser performance

You'll notice that the SQL parser has much lower throughput than the Java parser. Part of this is due to the complexity of the grammars:

```bash
$ wc grammars/*.g4
     241     723    6877 grammars/JavaLexer.g4
     750    1825   16397 grammars/JavaParser.g4
    2650    6672   30064 grammars/PostgreSQLLexer.g4
    5327   12002  103840 grammars/PostgreSQLParser.g4
     505    1217    9337 grammars/Sparql.g4
```

But there is usually room to improve performance by left-factoring common grammatical prefixes. Using the intellij plug-in's built-in profiler, we can see that the amount of lookahead for even a small `create` statement is quite large.  Consider the highlighted section here:

<img width="800" alt="Screen Shot 2022-02-07 at 11 27 23 AM" src="https://user-images.githubusercontent.com/178777/152858274-872c152c-da7e-46b4-9b92-40cad07cfac5.png">

(Open `PostgreSQLParser.g4` in Intellij, right-click on rule `root` and select `Test rule root`, enter sample input in the ANTLR Preview tool pane, then click on the profiler, click on the various headers to sort forward and backwards.)

The parser needs 12 tokens of lookahead because of the way the grammar is expressed:

<img width="150" alt="Screen Shot 2022-02-07 at 11 28 33 AM" src="https://user-images.githubusercontent.com/178777/152858185-cac8af97-3a6e-42cb-a077-27f4783c3134.png">

There are multiple statements that either start with the same left prefix or are variations on a `create` statement. In this case, it looks like the parser is scanning the entire statement until the semicolon before deciding between the various grammatical forms. SQL is a very complex (or big at least) language and so merging grammatical rules might put a larger burden on a semantic analyzer phase and might also make the grammar less readable. This is a trade off to keep in mind when either designing languages or implementing grammars. :)

For example, let's say we have a simple rule in isolation that is very similar looking from the left edge:

```
create
    :    'create' 'table' ID '(' ID 'integer' ')' ';'
    |    'create' 'table' ID '(' ')' ';'
    ;
```

ALL(\*) has no problem with that rule; it just dynamically scans ahead until it can distinguish the alternatives of any decision it faces. The cost is having to look ahead to the token beyond '(' in order to make a decision. In this case, it looks ahead 5 tokens.

Without changing the language recognized, we can left factor and collapse the alternatives of that rule so that it needs only one symbol of look ahead to match the optional `(ID 'integer')?` subrule:

```
create
    :    'create' 'table' ID '(' (ID 'integer')? ')' ';'
    ;
```

But, of course, this grammar might be less easy to read. The language is the same but we have expressed it differently to improve performance; the usual trade off in optimizations.
