
package statementResolver.soot;

import com.google.common.base.Preconditions;

import statementResolver.Option;
import statementResolver.color.Color;
import statementResolver.state.State;
import statementResolver.state.StateUnitPair;
import statementResolver.state.UnitSet;
import soot.Body;
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
		Map<String, String> local_vars = new HashMap<String, String>();
		Map<Unit, List<UnitSet>> label_list = new HashMap<Unit, List<UnitSet>>();
		List<State> state_list = new ArrayList<State>();
		Map<String, Boolean> input_list_used = new HashMap<String, Boolean>();
		
		int current_no = 0;
		int command_line_no = 1;

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
				local_vars.put(value.toString(), "");
				System.out.println(Color.ANSI_RED+"Insert " + str +Color.ANSI_RESET);
			}
			for (int i = 0; i < 3; i++) {
				String name = "X" + i;
				input_list_used.put(name, false);
				local_vars.put(name, name);
				System.out.println(Color.ANSI_RED+"Insert " + name +Color.ANSI_RESET);
			}
			
			
			System.out.println("=======================================");	

			// initialize for control flow
			// Set up goto label points
			List<UnitSet> no_label = new ArrayList<UnitSet>();
			for (UnitBox ub: UnitBoxes) {
				List<UnitSet> units = new ArrayList<UnitSet>();
				label_list.put(ub.getUnit(), units);				
			}
			Unit currentkey = null;
			boolean label_flag = false;
			while (gIt.hasNext()) {
				Unit u = (Unit)gIt.next();	
				List<UnitSet> units = label_list.get(u);
				if (units == null) {
					if (!label_flag) {
					// doesn't enter label yet
						no_label.add(new UnitSet(u, command_line_no));
						System.out.println(command_line_no+" "+u.toString());
						command_line_no++;
					}
					else {
						units = label_list.get(currentkey);
						units.add(new UnitSet(u, command_line_no));
						label_list.put(currentkey, units);
						System.out.println(command_line_no+" ->"+currentkey.toString()+" - "+u.toString());
						command_line_no++;
					}
				}
				else {
					currentkey = u;
					units.add(new UnitSet(u, command_line_no));
					label_list.put(u, units);
					label_flag = true;
					System.out.println(command_line_no+" ->"+u.toString()+" - "+u.toString());
					command_line_no++;
				}
			}
			System.out.println("=======================================");	
			// Starting to analysis
			for (UnitSet us : no_label) { // no label section
				if (deterUnit(us.getUnit()) == 1) {
					state_list.add( handleUnit(us.getUnit(),local_vars, current_no, us.getLine()).getState() );
					System.out.println( Color.ANSI_BLUE+"handling '" +us.getUnit().toString()+"'"+ Color.ANSI_RESET);
					current_no++;
					System.out.println("--");
				}
				else {
					//System.out.println(u.toString());
				}
			}
			
			// looping over label sections
			boolean unit_target_flag = false;
			boolean break_flag = false;
			Unit unit_target = null;
			do {
				if (!unit_target_flag) { // initially loop from the beginning
					for (Unit u_index : label_list.keySet()) {
						List<UnitSet> unit_list = label_list.get(u_index);
						for (UnitSet us : unit_list) {
							if (deterUnit(us.getUnit()) == 1) {
								State st = handleUnit(us.getUnit(), local_vars, current_no, us.getLine()).getState();
								state_list.add(st);
								System.out.println(Color.ANSI_BLUE + "handling '" + us.getUnit().toString() + "'"
										+ Color.ANSI_RESET);
								current_no++;
								st.printForm();
								System.out.println("------------------------------------");
							} else if (deterUnit(us.getUnit()) == 2) {
								StateUnitPair su = handleUnit(us.getUnit(), local_vars, current_no, us.getLine());
								state_list.add(su.getState());
								System.out.println(Color.ANSI_BLUE + "handling '" + us.getUnit().toString() + "'"
										+ Color.ANSI_RESET);
								current_no++;
								su.getState().printForm();
								System.out.println("------------------------------------");
								if (su.getUnit() != null) {
									unit_target = su.getUnit();
									break_flag = true;
									break; // break UnitSet for-loop
								}
							}
						}
						if (break_flag) {
							break_flag = false;
							break; // break U_index for-loop
						}
					} 
				}
				else {	// second loop starts from the target label section
					for (Unit u_index : label_list.keySet()) {
						if (u_index == unit_target) {
							List<UnitSet> unit_list = label_list.get(u_index);
							for (UnitSet us : unit_list) {
								if (deterUnit(us.getUnit()) == 1) {
									State st = handleUnit(us.getUnit(), local_vars, current_no, us.getLine()).getState();
									state_list.add(st);
									System.out.println(Color.ANSI_BLUE + "handling '" + us.getUnit().toString() + "'"
											+ Color.ANSI_RESET);
									current_no++;
									System.out.println("------------------------------------");
								} else if (deterUnit(us.getUnit()) == 2) {
									StateUnitPair su = handleUnit(us.getUnit(), local_vars, current_no, us.getLine());
									state_list.add(su.getState());
									if (su.getUnit() != null) {
										unit_target = su.getUnit();
									}
									System.out.println(Color.ANSI_BLUE + "handling '" + us.getUnit().toString() + "'"
											+ Color.ANSI_RESET);
									current_no++;
									System.out.println("------------------------------------");
									break_flag = true;
									break; // break UnitSet for-loop
								}
							}
							if (break_flag) {
								break_flag = false;
								break; // break U_index for-loop
							}
						}
					} 
				}
			} while (unit_target_flag);
			
			
		} // end of main analysis
		
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
	// TODO: match all cases.
	// 0 :
	// 1 : no unit return, eg: AssignStmt
	// 2 : going to a unit target, eg: GotoStmt
	protected int deterUnit(Unit u) {
		if (u instanceof JLookupSwitchStmt) {
			return 0;
		}
		else if (u instanceof AssignStmt) {
			return 1;
		}
		else if (u instanceof ArrayRef) {
			return 0;
		}
		else if (u instanceof BreakpointStmt) {
			return 0;
		}
		else if (u instanceof BinopExpr) {
			return 0;
		}
		else if (u instanceof CaughtExceptionRef) {
			return 0;
		}
		else if (u instanceof GotoStmt) {
			return 2;
		}
		else if (u instanceof NoSuchLocalException) {
			return 0;
		}
		else if (u instanceof NullConstant) {
			return 0;
		}
		else if (u instanceof IfStmt) {
			return 2;
		}
		else if (u instanceof IdentityStmt) {
			return 0;
		}
		else if (u instanceof JInstanceOfExpr) {
			return 0;
		}
		else if (u instanceof JExitMonitorStmt) {
			return 0;
		}
		else if (u instanceof JInvokeStmt) {
			return 0;
		}
		else if (u instanceof ReturnStmt) {
			return 0;
		}
		else if (u instanceof TableSwitchStmt) {
			return 0;
		}
		else if (u instanceof ThrowStmt) {
			return 0;
		}
		else if (u instanceof ReturnVoidStmt) {
			return 0;
		}
		return 0;
	}
	
	protected StateUnitPair handleUnit(Unit u, Map<String, String> local_vars, int num, int command_no) {
		System.out.println("++ no: " + num + ", line: " + command_no);
		State st = new State(local_vars, num, u.toString(), command_no);
		if (u instanceof JLookupSwitchStmt) {
		}
		else if (u instanceof AssignStmt) {			
			String assignment = "";
			String var = "";
			String u_s = u.toString();
			char c;
			boolean flag = false;
			for (int i = 0; i < u_s.length(); i++) {	// Separating var and assignment 
				if (!flag) {
					if ((c = u_s.charAt(i)) == '=') {
						i++;
						flag = true;
					}
					else {
						if (c != ' ') {
							var = var + c;
						}
					}
				}
				else {
					assignment = assignment + u_s.charAt(i);
				}
			}
			System.out.println("set: " + var + " -> " + assignment);
			st.update(var, assignment);
			StateUnitPair su = new StateUnitPair(st, null);
			return su; 
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
			GotoStmt gt_st = (GotoStmt) u;
			Unit goto_target = gt_st.getTarget();
			System.out.println("goto " + goto_target);
			StateUnitPair su = new StateUnitPair(st, null);
			return su;
		}
		else if (u instanceof NoSuchLocalException) {			
		}
		else if (u instanceof NullConstant) {			
		}
		else if (u instanceof IfStmt) {
			IfStmt if_st = (IfStmt) u;
			Unit goto_target = if_st.getTargetBox().getUnit();
			Value condiction = if_st.getCondition();
			System.out.println("goto " + goto_target + " when " + condiction);
			StateUnitPair su = new StateUnitPair(st, null);
			return su;
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

		StateUnitPair su = new StateUnitPair();
		return su;
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

				for (SootMethod sm : sc.getMethods()) {
					if (sm.isConcrete() && (sm.toString().contains("reduce("))) {
						//System.out.println("method:"+sm.toString()+"\n");
						
						JimpleBody body = (JimpleBody) sm.retrieveActiveBody();
						System.out.println("=======================================");			
						System.out.println(sm.getName());
						bodies.add(body);
						break;
					}
				}
			}
		}
		return bodies;
	}
	
}