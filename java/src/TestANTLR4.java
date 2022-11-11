import com.sun.management.HotSpotDiagnosticMXBean;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import org.antlr.v4.runtime.misc.Triple;
import org.gridkit.jvmtool.heapdump.HeapHistogram;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;

import javax.management.MBeanServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

/** Parse a file or directory of files using the generated parser
 *  ANTLR builds from a grammar.
 *
 *  $ java Test C translation_unit -files '.*\.c' -showfiles -tokens t.c
 */
class TestANTLR4 {
	public static enum OptionArgType { NONE, STRING, INT } // NONE implies boolean
	public static class Option {
		String fieldName;
		String name;
		OptionArgType argType;
		String description;

		public Option(String fieldName, String name, String description) {
			this(fieldName, name, OptionArgType.NONE, description);
		}

		public Option(String fieldName, String name, OptionArgType argType, String description) {
			this.fieldName = fieldName;
			this.name = name;
			this.argType = argType;
			this.description = description;
		}
	}

	public static long lexerTime = 0;
	public static boolean profile = false;

	public String inputFilePattern = ".*\\.c";
	public boolean showTokens, showFileNames, wipeDFA, wipeFileDFA, showDocTiming, buildTrees=false, nodfa=false;
	public int nbatches = 5;
	public int batchSize = 5;
	public static int skip = 1;

	public static Option[] optionDefs = {
		new Option("inputFilePattern",	"-files", OptionArgType.STRING, "input files; e.g., '.*\\\\.java'"),
		new Option("showFileNames",	"-showfiles", "show file names as they are parsed"),
		new Option("showTokens",	"-tokens", "show input tokens"),
		new Option("showDocTiming",	"-timing", "dump time in ms to parse each file"),
		new Option("nbatches", "-nbatches", OptionArgType.INT, "run how many trials"),
		new Option("batchSize", "-batchsize", OptionArgType.INT, "parse input n times w/o wiping DFA cache"),
		new Option("skip", "-skip", OptionArgType.INT, "how many batches to skip to allow JIT to warm up"),
		new Option("wipeDFA", "-wipedfa", "wipe DFA before each lex/parse pass"),
		new Option("wipeFileDFA", "-wipefiledfa", "wipe DFA before each lex/parse of a file"),
		new Option("nodfa", "-nodfa", "don't use DFA lookahead cache"),
		new Option("buildTrees", "-trees", "build parse trees")
	};

	protected String grammarName;
	protected String startRuleName;
	List<String> inputFiles = new ArrayList<String>();
	Class<? extends Lexer> lexerClass;
	Class<? extends Parser> parserClass;

	public static void main(String[] args) throws Exception {
		TestANTLR4 tester = new TestANTLR4();
		if ( args.length == 0 ) { tester.help(); System.exit(0); }
		tester.go(args);
	}

	public void go(String[] args) throws Exception {
		handleArgs(args);
		if (inputFiles.size() == 0 ) {
			return;
		}

		List<String> allFiles = new ArrayList<>();
		for (String fileName : inputFiles) {
			List<String> files = getFilenames(new File(fileName));
			allFiles.addAll(files);
		}
		loadLexerAndParser();
		List<InputDocument> docs = load(allFiles);

		List<Double> batchAverages = new ArrayList<>();
		for (int i = 0; i < nbatches; i++) {
			System.out.printf("BATCH %d\n", i+1);
			List<Long> timings = runBatch(docs, batchSize);
			long minTime = Collections.min(timings);
			// timing info will show results of last pass over documents
			if ( showDocTiming ) {
				for (InputDocument doc : docs) {
					System.out.printf("%d %d %d %d %s\n",
							doc.content.length, doc.time, doc.beforeDFASize, doc.afterDFASize, doc.fileName);
				}
			}

			double mean = avg(timings);
			double timingStd = std(mean, timings);
			System.out.printf("average parse %.3fms, min %.3fms, stddev=%.3fms (First %d runs skipped for JIT warmup)\n", mean, (minTime / (1000.00 * 1000.00)), timingStd, skip);
			batchAverages.add(mean);
		}

		// skip first batch or a few for JIT compiler warmup
		if ( batchAverages.size()> skip) {
			batchAverages = batchAverages.subList(skip, batchAverages.size());
		}

		double overallAvg = avg(batchAverages);
		double overallStd = std(overallAvg, batchAverages);
		System.out.printf("Overall average parse %.3fms, stddev=%.3fms\n", overallAvg, overallStd);
	}

	/** Run a batch of trials and return the list of ms timings */
	private List<Long> runBatch(List<InputDocument> docs, int batchSize) throws Exception {
		List<Long> timings = new ArrayList<>();
		for (int i=1; i<=batchSize; i++) {
			if ( wipeDFA ) { // wipe DFA for lexer/parser before each pass?
				wipe();
			}
			Triple<Long, Long, Long> timeStats = runTrial(docs);
			long time = timeStats.a;
			timings.add((long)(time/1000.0/1000.0)); // get into ms
		}
		return timings;
	}

	public <T extends Number> double avg(List<T> values) {
		double sum = 0.0;
		for (T v : values) {
			sum += v.doubleValue();
		}
		return sum / values.size();
	}

	public <T extends Number> double std(double mean, List<T> values) { // unbiased std dev
		double sum = 0.0;
		for (T v : values) {
			double d = v.doubleValue() - mean;
			sum += d * d;
		}
		return Math.sqrt(sum / (values.size() - 1));
	}

	/** return num ns to parse all docs */
	public Triple<Long, Long, Long> runTrial(List<InputDocument> docs) throws Exception {
		System.gc();
		String heapFileName = dumpHeap("/tmp/test-heapdump", true);
		Pair<Long, Long> beforeStats = heapStats(heapFileName);

		long start = System.nanoTime();
		int nlines = 0;
		long nchar = 0;
		for (InputDocument doc : docs) {
			if ( wipeFileDFA ) { // wipe DFA for lexer/parser before each file parse?
				wipe();
			}
			parseFile(doc);
			nlines += doc.nlines;
			nchar += doc.content.length;
		}
		long stop = System.nanoTime();
		long tms = (long)((stop - start) / (1000.0 * 1000.0));
		double ts = tms / 1000.0;

		System.gc();
		heapFileName = dumpHeap("/tmp/test-heapdump", true);
		Pair<Long, Long> afterStats = heapStats(heapFileName);
		Pair<Long, Long> heapStats = new Pair<>(afterStats.a - beforeStats.a, afterStats.b - beforeStats.b);

		System.out.printf("Parsed %d files %,d lines %,d bytes in %4dms at %,9d lines/sec %,10d chars/sec instances %,9d heap %,13d bytes\n",
						  docs.size(), nlines, nchar, tms, (int)(nlines / ts), (int)(nchar/ts), heapStats.a, heapStats.b);
		return new Triple<>(stop - start, heapStats.a, heapStats.b);
	}

	/** Dump heap tracing (live or all) objects to a .hprof file.
	 *
	 * @param baseFileName Pass in "/tmp/mydump" and this will add .hprof and a timestamp.
	 * @throws IOException
	 */
	public static String dumpHeap(String baseFileName, boolean live) throws IOException {
		MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
				server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS");
		String timestamp = formatter.format(new Timestamp(System.currentTimeMillis()));
		String fileName = baseFileName+"-"+timestamp+".hprof";
		mxBean.dumpHeap(fileName, live);
		return fileName;
	}

	/** Return total instances, total heap size */
	public static Pair<Long,Long> heapStats(String heapFileName) throws IOException {
		Heap heap = HeapFactory.createFastHeap(new File(heapFileName));
		HeapHistogram histogram = new HeapHistogram();
		for (Instance i : heap.getAllInstances()) {
			histogram.accumulate(i);
		}
		return new Pair<>(histogram.getTotalCount(), histogram.getTotalSize());
	}

	public void parseFile(InputDocument doc) throws Exception {
		String fileName = doc.fileName;
		if ( showFileNames ) System.out.println(fileName);
		ANTLRInputStream input = new ANTLRInputStream(doc.content, doc.content.length);
		Constructor<? extends Lexer> lexerCtor =
			lexerClass.getConstructor(CharStream.class);
		Lexer lexer = lexerCtor.newInstance(input);
		input.name = fileName;

		Constructor<? extends Parser> parserCtor =
			parserClass.getConstructor(TokenStream.class);
		CommonTokenStream tokens = new CommonTokenStream(lexer);

		if ( showTokens ) {
			tokens.fill();
			for (Object tok : tokens.getTokens()) {
				System.out.println(tok);
			}
		}

		Parser parser = parserCtor.newInstance(tokens);

		ParserATNSimulator sim = parser.getInterpreter();
		parser.setInterpreter(sim);
		parser.setBuildParseTree(buildTrees); // no parse trees?
		Method startRule = parserClass.getMethod(startRuleName);
		parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
		parser.setErrorHandler(new BailErrorStrategy());  // SQL has lots of errors ;)
		parser.removeErrorListeners();

		if ( showDocTiming ) {
	        doc.beforeDFASize = getDFASize(parser);
		}

        long start, stop;
		start = System.nanoTime();
		try {
			startRule.invoke(parser, (Object[]) null);
		}
		catch (InvocationTargetException ex) {
			// gotta try full SLL if we failed with SLL
			if ( ex.getCause() instanceof ParseCancellationException) {
				tokens.reset(); // rewind input stream
				parser.reset();
				// back to standard listeners/handlers
				parser.addErrorListener(ConsoleErrorListener.INSTANCE);
				parser.setErrorHandler(new DefaultErrorStrategy());
				parser.getInterpreter().setPredictionMode(PredictionMode.LL);

				startRule.invoke(parser, (Object[])null);
				// if we parse ok, it's LL not SLL
				if ( parser.getNumberOfSyntaxErrors()==0 ) {
//					System.out.println("LL required");
				}
			}
		}
		finally {
			stop = System.nanoTime();
		}
		doc.time = stop - start;
		if ( showDocTiming ) {
	        doc.afterDFASize = getDFASize(parser);
		}
    }

	public List<String> getFilenames(File f) throws Exception {
		List<String> files = new ArrayList<String>();
		getFilenames_(f, files);
		return files;
	}

	public void getFilenames_(File f, List<String> files) throws Exception {
		// If this is a directory, walk each file/dir in that directory
		if (f.isDirectory()) {
			String flist[] = f.list();
			for(int i=0; i < flist.length; i++) {
				getFilenames_(new File(f, flist[i]), files);
			}
		}

		// otherwise, if this is an input file, parse it!
		else if ( f.getName().matches(inputFilePattern) &&
			f.getName().indexOf('-')<0 ) // don't allow preprocessor files like ByteBufferAs-X-Buffer.java
		{
			files.add(f.getAbsolutePath());
		}
	}

	/** Get all file contents into input array */
	public List<InputDocument> load(List<String> fileNames) throws IOException {
		List<InputDocument> input = new ArrayList<InputDocument>(fileNames.size());
		for (String f : fileNames) {
			input.add(load(f));
		}
		return input;
	}

	public InputDocument load(String fileName) throws IOException {
		File f = new File(fileName);
		int size = (int)f.length();
		FileInputStream fis = new FileInputStream(fileName);
		InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
		char[] data = null;
		long numRead = 0;
		try {
			data = new char[size];
			numRead = isr.read(data);
		}
		finally {
			isr.close();
		}
		if ( numRead != size ) {
			data = Arrays.copyOf(data, (int) numRead);
//			System.err.println("read error; read="+numRead+"!="+f.length());
		}
		return new InputDocument(fileName, data);
	}

	protected void handleArgs(String[] args) {
		// for each directory/file specified on the command line
		int i=0;
		grammarName = args[i];
		i++;
		startRuleName = args[i];
		i++;
		while ( args!=null && i<args.length ) {
			String arg = args[i];
			i++;
			if ( arg.charAt(0)!='-' ) { // file name
				if ( !inputFiles.contains(arg) ) inputFiles.add(arg);
				continue;
			}
			boolean found = false;
			for (Option o : optionDefs) {
				if ( arg.equals(o.name) ) {
					found = true;
					Object argValue = null;
					if ( o.argType==OptionArgType.STRING ) {
						argValue = args[i];
						i++;
					}
					else if ( o.argType==OptionArgType.INT ) {
						argValue = Integer.valueOf(args[i]);
						i++;
					}
					// use reflection to set field
					Class<? extends TestANTLR4> c = this.getClass();
					try {
						Field f = c.getField(o.fieldName);
						if ( argValue==null ) {
							if ( arg.startsWith("-no-") ) f.setBoolean(this, false);
							else f.setBoolean(this, true);
						}
						else f.set(this, argValue);
					}
					catch (Exception e) {
						System.out.println("can't access field "+o.fieldName);
					}
				}
			}
			if ( !found ) {
				System.out.println("invalid arg: " + arg);
			}
		}
	}

	public void help() {
		System.out.println("Test grammarname startrule [options] filename(s)");
		for (Option o : optionDefs) {
			String name = o.name + (o.argType!=OptionArgType.NONE? " ___" : "");
			String s = String.format(" %-19s %s", name, o.description);
			System.out.println(s);
		}
	}

	void loadLexerAndParser() throws Exception {
//		System.out.println("exec "+grammarName+"."+startRuleName);
		String lexerName = grammarName+"Lexer";
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try {
			lexerClass = cl.loadClass(lexerName).asSubclass(Lexer.class);
		}
		catch (java.lang.ClassNotFoundException cnfe) {
			System.err.println("Can't load "+lexerName+" as lexer");
			return;
		}
//		System.out.println("lexer is "+lexerName);

		String parserName = grammarName+"Parser";
		parserClass = cl.loadClass(parserName).asSubclass(Parser.class);
		if ( parserClass==null ) {
			System.err.println("Can't load "+parserName);
		}
//		System.out.println("parser is "+parserName);
	}

    public void wipe() throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Constructor<? extends Lexer> lexerCtor =
                lexerClass.getConstructor(CharStream.class);
        Lexer lexer = lexerCtor.newInstance((CharStream)null);
        ATN atn = lexer.getATN();
        LexerATNSimulator l = lexer.getInterpreter();
        for (int d = 0; d < l.decisionToDFA.length; d++) {
            l.decisionToDFA[d] = new DFA(atn.getDecisionState(d), d);
        }
        Constructor<? extends Parser> parserCtor =
                parserClass.getConstructor(TokenStream.class);
        Parser parser = parserCtor.newInstance((TokenStream)null);
        atn = parser.getATN();
        ParserATNSimulator p = parser.getInterpreter();
        for (int d = 0; d < p.decisionToDFA.length; d++) {
            p.decisionToDFA[d] = new DFA(atn.getDecisionState(d), d);
        }
    }

    public int getDFASize(Parser parser) {
        int n = 0;
        DFA[] decisionToDFA = parser.getInterpreter().decisionToDFA;
        for (int i = 0; i < decisionToDFA.length; i++) {
            int nstates = decisionToDFA[i].states.size();
            if ( nstates>0 ) {
                n += nstates;
            }
        }
        return n;
    }

}

