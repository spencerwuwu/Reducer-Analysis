# Java Reducer Analysis
This is a derived project from [Soot](https://github.com/Sable/soot) 
and [Jayhorn](https://github.com/jayhorn/jayhorn).   
Currently it is just demonstrating a basic structure of
analyzing the symbolic states while executing a reducer. 
A more common-support tool is under development.

## Usage
```
usage: Input [options]

  Input: class/jar/directory
  Options:
    -h               help
    -c class_path    Set classpath
    -g               Generate control flow graph(TODO)
```

This project converts the specified function's bytecodes into jimple
files, and performs symbolic execution analysis.   
Specify the target class and function to analysis in 
`StatementResolver.soot.StatementResolver.get_colloctor_SceneBodies`.

In `StatementResolver.soot.StatementResolver.performAnalysis`, 
you can see that I extracted the body, classes and methods of 
the target program, then do the analysis.

## Example
The original purpose of this project is to check the commutative 
of a reducer function. In `test_case/` you can find a little Int-Int
reducer and some other classes. This is a part of another 
project. We can just treat these codes as a wrapper function of
`test_case/src/main/java/reduce_test/collector0_90_1_7.java`, which 
is our target function. The compiled executable file is 
`test_case/target/test_reducer-1.0.jar`. It is not necessary but you 
can rebuild it with `mvn package`.

Then run 
```
$ java -jar release/SR.jar test_case/target/test_reducer-1.0.jar
```
You can first see the extracted jimple code, all 
variables used in the jimple code, and each line of code in each label section. 
I mapped each label section with the first function of that section.   
Following is the symbolic analysis, where shows the value of each variable 
after executing each line.   
Currently the input is X0, X1, X2
