
package statementResolver.soot;

import com.google.common.base.Preconditions;

import statementResolver.Option;
import statementResolver.color.Color;
import statementResolver.state.State;
import soot.Body;
import soot.Local;
import soot.NormalUnitPrinter;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.UnitBox;
import soot.UnitPrinter;
import soot.Value;
import soot.ValueBox;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.jimple.internal.JLookupSwitchStmt;
import soot.jimple.toolkits.pointer.LocalMustNotAliasAnalysis;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.BriefBlockGraph;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.CompleteUnitGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.FlowAnalysis;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.util.cfgcmd.CFGToDotGraph;
import soot.util.dot.DotGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StatementResolver {
	

	private final List<String> resolvedClassNames;
	Option op = new Option();

	public StatementResolver() {
		this(new ArrayList<String>());
	}
	
	public StatementResolver(List<String> resolvedClassNames) {
		this.resolvedClassNames = resolvedClassNames;
		// first reset everything:
		soot.G.reset();
	}

	public void run(String input, String classPath, Option option) {
		SootRunner runner = new SootRunner();
		
		runner.run(input, classPath);
		op = option;
		
		// Main analysis starts from here
		performAnalysis();
	}

	private void addDefaultInitializers(SootMethod constructor, SootClass containingClass) {
		if (constructor.isConstructor()) {
			Preconditions.checkArgument(constructor.getDeclaringClass().equals(containingClass));
			JimpleBody jbody = (JimpleBody) constructor.retrieveActiveBody();

			Set<SootField> instanceFields = new LinkedHashSet<SootField>();
			for (SootField f : containingClass.getFields()) {
				if (!f.isStatic()) {
					instanceFields.add(f);
				}
			}
			for (ValueBox vb : jbody.getDefBoxes()) {
				if (vb.getValue() instanceof InstanceFieldRef) {
					Value base = ((InstanceFieldRef) vb.getValue()).getBase();
					soot.Type baseType = base.getType();
					if (baseType instanceof RefType && ((RefType) baseType).getSootClass().equals(containingClass)) {
						// remove the final fields that are initialized anyways
						// from
						// our staticFields set.
						SootField f = ((InstanceFieldRef) vb.getValue()).getField();
						if (f.isFinal()) {
							instanceFields.remove(f);
						}
					}
				}
			}

			Unit insertPos = null;

			for (Unit u : jbody.getUnits()) {
				if (u instanceof IdentityStmt) {
					insertPos = u;
				} else {
					break; // insert after the last IdentityStmt
				}
			}

		}
	}
	

	public SootClass getAssertionClass() {
		return Scene.v().getSootClass(SootRunner.assertionClassName);
	}

	public void performAnalysis() {

		List<SootClass> classes = new LinkedList<SootClass>(Scene.v().getClasses());
		
		for (SootClass sc : classes) {

			if (sc == getAssertionClass()) {
				continue; // no need to process this guy.
			}

			
			if (sc.resolvingLevel() >= SootClass.SIGNATURES && sc.isApplicationClass()) {
				for (SootMethod sm : sc.getMethods()) {
					if (sm.isConcrete()) {
						addDefaultInitializers(sm, sc);
					}

					Body body = sm.retrieveActiveBody();
					try {
						body.validate();
					} catch (RuntimeException e) {
						System.out.println("Unable to validate method body. Possible NullPointerException?");
						throw e;	
					}

				}
			}
			
		}
		
		// init values of analysis
		Map<Value, String> local_vars = new HashMap<Value, String>();
		Map<Unit, List<Unit>> label_list = new HashMap<Unit, List<Unit>>();
		List<State> state_list = new ArrayList<State>();

		System.out.println("=======================================");	
		
		for (JimpleBody body : this.get_colloctor_SceneBodies()) {
			UnitGraph graph = new ExceptionalUnitGraph(body);
			Iterator gIt = graph.iterator();
			List<UnitBox> UnitBoxes = body.getUnitBoxes(true);
			
			System.out.println(Color.ANSI_BLUE+body.toString()+Color.ANSI_RESET);
			
			// Storing variables
			List<ValueBox> defBoxes = body.getDefBoxes();
			for (ValueBox d: defBoxes) {
				Value value = d.getValue();
				String str = d.getValue().toString();
				local_vars.put(value, "");
				System.out.println(Color.ANSI_RED+"Insert " + str +Color.ANSI_RESET);
			}

			
			// Variables value TODO
			List<ValueBox> useBoxes = body.getUseBoxes();
			for (ValueBox d: useBoxes) {
				Value value = d.getValue();
				String str = d.getValue().toString();
				if ( value instanceof Local ) {

				}
				else {
					//System.out.println(str + "\n");
				}
			}
			
			System.out.println("=======================================");	

			// initialize for controll flow
			// Set up label points
			List<Unit> no_label = new ArrayList<Unit>();
			for (UnitBox ub: UnitBoxes) {
				List<Unit> units = new ArrayList<Unit>();
				label_list.put(ub.getUnit(), units);				
			}
			Unit currentkey = null;
			boolean label_flag = false;
			while (gIt.hasNext()) {
				Unit u = (Unit)gIt.next();	
				List<Unit> units = label_list.get(u);
				if (units == null) {
					if (!label_flag) {
					// doesn't enter label yet
						no_label.add(u);
						System.out.println(u.toString());
					}
					else {
						units = label_list.get(currentkey);
						units.add(u);
						label_list.put(u, units);
						System.out.println("->"+currentkey.toString()+" - "+u.toString());
					}
				}
				else {
					currentkey = u;
					units.add(u);
					label_list.put(u, units);
					label_flag = true;
					System.out.println("->"+u.toString()+" - "+u.toString());
				}
			}


			System.out.println("=======================================");	
			
			// Starting to analysis
			Iterator no_label_it = no_label.iterator();
			while(no_label_it.hasNext()) {
				Unit u = (Unit)no_label_it.next();
				System.out.println(u.toString());
			}

			/*
			while (gIt.hasNext()) {

				Unit u = (Unit)gIt.next();	
				//System.out.println(Color.ANSI_GREEN+ u.toString() + "\n"+Color.ANSI_RESET);

				if (u instanceof JLookupSwitchStmt) {

				}
				else if (u instanceof AssignStmt) {
					
				}
				else if (u instanceof ArrayRef) {
					
				}
				else if (u instanceof BreakpointStmt) {
					
				}
				else if (u instanceof BinopExpr) {
					
				}
				else if (u instanceof CaughtExceptionRef) {
					
				}
				else if (u instanceof GotoStmt) {
				}
				else if (u instanceof NoSuchLocalException) {
					
				}
				else if (u instanceof NullConstant) {
					
				}
				else if (u instanceof IfStmt) {

					
				}
				else if (u instanceof IdentityStmt) {
					
				}
				else if (u instanceof JInstanceOfExpr) {
					
				}
				else if (u instanceof JExitMonitorStmt) {
					
				}
				else if (u instanceof JInvokeStmt) {
					
				}
				else if (u instanceof ReturnStmt) {
					
				}
				else if (u instanceof TableSwitchStmt) {
					
				}
				else if (u instanceof ThrowStmt) {
					
				}
				else if (u instanceof ReturnVoidStmt) {
					
				}
				else {
					System.out.println(u);
				}
			}
			System.out.println("=======================================");



			List<UnitBox> Boxes = body.getUnitBoxes(true);
			for (UnitBox u: Boxes) {

				// Generate reducer's graph
				String str = u.getUnit().toString();
				//System.out.println(u.getUnit().getTags());
				System.out.println(Color.ANSI_GREEN+str + "\n"+Color.ANSI_RESET);
				
				
					CFGToDotGraph cfgToDot = new CFGToDotGraph();
					//DirectedGraph g = new CompleteUnitGraph(body);
					
					//DotGraph dotGraph = cfgToDot.drawCFG(g, body);
					//dotGraph.plot("context0_90_11_2.dot");
					 
				
				// For further analysis
				if (u.getUnit() instanceof JLookupSwitchStmt) {

				}
				else if (u.getUnit() instanceof AssignStmt) {
					
				}
				else if (u.getUnit() instanceof ArrayRef) {
					
				}
				else if (u.getUnit() instanceof BreakpointStmt) {
					
				}
				else if (u.getUnit() instanceof BinopExpr) {
					
				}
				else if (u.getUnit() instanceof CaughtExceptionRef) {
					
				}
				else if (u.getUnit() instanceof GotoStmt) {
					
				}
				else if (u.getUnit() instanceof NoSuchLocalException) {
					
				}
				else if (u.getUnit() instanceof NullConstant) {
					
				}
				else if (u.getUnit() instanceof IfStmt) {
					
				}
				else if (u.getUnit() instanceof IdentityStmt) {
					
				}
				else if (u.getUnit() instanceof JInstanceOfExpr) {
					
				}
				else if (u.getUnit() instanceof JExitMonitorStmt) {
					
				}
				else if (u.getUnit() instanceof JInvokeStmt) {
					
				}
				else if (u.getUnit() instanceof ReturnStmt) {
					
				}
				else if (u.getUnit() instanceof TableSwitchStmt) {
					
				}
				else if (u.getUnit() instanceof ThrowStmt) {
					
				}
				else if (u.getUnit() instanceof ReturnVoidStmt) {
					
				}
				else {
					System.out.println(u.getUnit());
				}
			}
			
			*/
			
		}
		
		
		if (op.cfg_flag) {
			// TODO
			/*
			BlockGraph blockGraph = new BriefBlockGraph(body);
			System.out.println(blockGraph);
			}
			*/
			CFGToDotGraph cfgToDot = new CFGToDotGraph();
			int i = 0;
			for (JimpleBody body : this.getSceneBodies()) {
				DirectedGraph g = new CompleteUnitGraph(body);
				DotGraph dotGraph = cfgToDot.drawCFG(g, body);
				dotGraph.plot(i+".dot");
				i = i+1;
			}
		}
		
	}

	protected Set<JimpleBody> getSceneBodies() {
		Set<JimpleBody> bodies = new LinkedHashSet<JimpleBody>();
		for (SootClass sc : new LinkedList<SootClass>(Scene.v().getClasses())) {

			if (sc.resolvingLevel() >= SootClass.BODIES) {

				for (SootMethod sm : sc.getMethods()) {
					if (sm.isConcrete()) {
						bodies.add((JimpleBody) sm.retrieveActiveBody());
					}
				}
			}
		}
		return bodies;
	}
	

	protected Set<JimpleBody> get_colloctor_SceneBodies() {
		Set<JimpleBody> bodies = new LinkedHashSet<JimpleBody>();
		for (SootClass sc : new LinkedList<SootClass>(Scene.v().getClasses())) {
			//System.out.println(sc);
			if (sc.resolvingLevel() >= SootClass.BODIES && sc.toString().contains("collector0_90_1_7")) {
				//System.out.println("\n");
				//System.out.println(sc);

				for (SootMethod sm : sc.getMethods()) {
					if (sm.isConcrete() && (sm.toString().contains("reduce("))) {
						//System.out.println("method:"+sm.toString()+"\n");
						
						JimpleBody body = (JimpleBody) sm.retrieveActiveBody();
						System.out.println("=======================================");			
						System.out.println(sm.getName());
						bodies.add(body);
						break;
						//System.out.println(body.toString());
						/*
						List<UnitBox> Boxes = body.getUnitBoxes(true);
						for (UnitBox u: Boxes)
							System.out.println(u.getUnit().toString());
						//UnitGraph g = new CompleteUnitGraph(body);
						System.out.println("----------\n");

						CFGToDotGraph cfgToDot = new CFGToDotGraph();
						DirectedGraph g = new CompleteUnitGraph(body);
						DotGraph dotGraph = cfgToDot.drawCFG(g, body);
						dotGraph.plot(sm.toString()+".dot");
						
						*/
					}
				}
			}
		}
		return bodies;
	}
	
}