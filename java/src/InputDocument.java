public class InputDocument {
	String fileName;
	char[] content;
	int index; // set by getRandomDocuments
	int nlines;
    long time;
    int beforeDFASize; // gets bigger as we build new DFA; total after we parse a document
    int afterDFASize; // gets bigger as we build new DFA; total after we parse a document

	InputDocument(InputDocument d, int index) {
		this.fileName = d.fileName;
		this.content = d.content;
		this.index = index;
	}

	InputDocument(String fileName, char[] content) {
		this.content = content;
		this.fileName = fileName;
		nlines = lines(content);
	}

	public int lines(char[] text) {
		int n = 0;
		for (int i = 0; i < text.length; i++) {
			if ( text[i]=='\n' ) n++;
		}
		return n;
	}

	@Override
	public String toString() {
		return fileName+"["+content.length+"]"+"@"+index;
	}
}
