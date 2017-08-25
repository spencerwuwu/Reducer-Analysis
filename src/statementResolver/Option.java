
package statementResolver;

public class Option {

	public final static String Usage = "usage: Input [options] \n"
			+ "* Input: class/jar/directory \n"
			+ "* Options:\n"
			+ "    -h               help \n"
			+ "    -c class_path    Set classpath \n"
			+ "    -g               Generate control flow graph \n";
	public final static String Warning = "Invalid input, use -h for help";
	public boolean cfg_flag;
	
	public Option() {
		cfg_flag = false;
	}
	
	public void parse(String input) {
		if (input.equals("-g")) {
			this.cfg_flag = true;
		}
	}
	
}