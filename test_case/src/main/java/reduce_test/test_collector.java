//https://searchcode.com/file/100326488/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-jobclient/src/test/java/org/apache/hadoop/mapred/TestMapRed.java#l-105

package reduce_test;

import java.io.IOException;
import java.util.Iterator;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapred.Reporter;

public class test_collector {

	public static void main(String[] args){
		/*
		for(int i=0; i<Common.maxcount ; i++){  
			ReducerO<IntWritable, IntWritable, IntWritable, IntWritable> reducer=new test_collector();
			Tester<IntWritable, IntWritable, IntWritable, IntWritable> tester=new Tester<IntWritable, IntWritable, IntWritable, IntWritable>();
			IntWritable[] solutionArray = { new IntWritable(1), new IntWritable(2), new IntWritable(3) };
			try {
				tester.test(new IntWritable(1), solutionArray, reducer);
			} catch (IOException | InterruptedException e1) {
				e1.printStackTrace();
			}
		}
		*/
	}

	public void reduce(IntWritable key, Iterator<IntWritable> it,
			OutputCollector<IntWritable, IntWritable> out,
			Reporter reporter) throws IOException {
		int keyint = key.get();
		int count = 0;
		while (it.hasNext()) {
			it.next();
			count++;
		}
		out.collect(new IntWritable(keyint), new IntWritable(count));
	}

}