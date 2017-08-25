# Java Statement Resolver
This is a derived project from [Soot](https://github.com/Sable/soot), 
and refers most of the code from [Jayhorn](https://github.com/jayhorn/jayhorn).

## Usage
```
usage: Input [options]

  Input: class/jar/directory
  Options:
    -h               help
    -c class_path    Set classpath
    -g               Generate control flow graph
```

This project demonstrates how to convert bytecodes into jimple
files, and runs through all the statements in the program.
In `StatementResolver.soot.StatementResolver.performAnalysis`, 
you can see that I extracted the body, classes and methods of 
the target program, then do the analysis. The `if/else` statements
classes can be fount in `soot.jimple`.   
By recording the statements and location in a program, further analysis, such as internal-domain analysis, is able to be implemented.  

Assertion classes are skipped for now.
