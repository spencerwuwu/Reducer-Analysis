
package statementResolver;

import statementResolver.soot.StatementResolver;

public class Main {

	public static void main(String[] args) {
		String javaInput = "";
		String classPath = "";
		if (args.length > 0) {
			javaInput = args[0];
			if (args.length > 1) {
				classPath = args[1];
			}
			StatementResolver SR = new StatementResolver();
			SR.run(javaInput, classPath);
			
		} else {
			System.err.println("usage: [class_dir/class_file] [(optional)classpath] / [jar_file]");
		}
	}
	
}