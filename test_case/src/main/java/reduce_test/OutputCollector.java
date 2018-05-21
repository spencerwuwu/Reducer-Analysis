package reduce_test;

import java.util.ArrayList;

public class OutputCollector<T1, T2> {
	ArrayList<T1> keyList;
	ArrayList<T2> valueList;
	
	
	public OutputCollector(){
		 keyList = new ArrayList<T1>();
		 valueList = new ArrayList<T2>();
	}

	public void collect(T1 key, T2 value){
		keyList.add(key);
		valueList.add(value);
	}
	
	public ArrayList<T1> getKeyList() {
		return keyList;
	}
	public ArrayList<T2> getValueList() {
		return valueList;
	}

	
}
