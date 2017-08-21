package statementResolver.soot;


import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import soot.Scene;
import soot.SootClass;

import java.util.Set;


public class StatementResolver {

	private final List<String> resolvedClassNames;
	
	//private final Set<SourceLocation> locations = new LinkedHashSet<SourceLocation>();

	// Create a new program
	//private final Program program = new Program();

	public StatementResolver() {
		this(new ArrayList<String>());
		//SootTranslationHelpers.initialize(program);
	}


	public StatementResolver(List<String> resolvedClassNames) {
		this.resolvedClassNames = resolvedClassNames;
		// first reset everything:
		soot.G.reset();
		//SootTranslationHelpers.initialize(program);
	}
	
	public void run(String input, String classPath) {
		SootRunner runner = new SootRunner();
		
		runner.run(input, classPath);
		performAnalysis();
	}
	
	public void performAnalysis() {

		List<SootClass> classes = new LinkedList<SootClass>(Scene.v().getClasses());

	}
}