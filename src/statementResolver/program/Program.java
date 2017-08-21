
package statementResolver.program;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import statementResolver.program.method.Method;
import statementResolver.program.variable.Variable;


public class Program {

	private final Map<String, Variable> globalVariables = new LinkedHashMap<String, Variable>();
	private final Map<String, Method> methods = new LinkedHashMap<String, Method>();

	private Method entryPoint;
	
}