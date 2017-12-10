package statementResolver.state;

import java.util.HashMap;
import java.util.Map;

import soot.Value;

public class State {
	public Map<Value, String> local_vars;
	int num;	// State number
	String input_command;

	public State() {
		this.local_vars = new HashMap<Value, String>();
		this.num = 0;
	}
	
	public State(Map<Value, String> in, int number) {
		this.local_vars = in;
		this.num = number;
	}
	
	public void update(Value v, String str) {
		this.local_vars.put(v, str);
	}
}