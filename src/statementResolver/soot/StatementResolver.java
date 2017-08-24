
package statementResolver.soot;

import com.google.common.base.Preconditions;

import statementResolver.color.Color;
import soot.Body;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.UnitBox;
import soot.Value;
import soot.ValueBox;
import soot.jimple.*;
import soot.jimple.internal.JLookupSwitchStmt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class StatementResolver {
	

	private final List<String> resolvedClassNames;


	public StatementResolver() {
		this(new ArrayList<String>());
	}
	
	public StatementResolver(List<String> resolvedClassNames) {
		this.resolvedClassNames = resolvedClassNames;
		// first reset everything:
		soot.G.reset();
	}

	public void run(String input, String classPath) {
		SootRunner runner = new SootRunner();
		
		runner.run(input, classPath);
		
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

	public void performAnalysis() {

		List<SootClass> classes = new LinkedList<SootClass>(Scene.v().getClasses());
		
		for (SootClass sc : classes) {
			
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
		

		// Begin

		for (JimpleBody body : this.getSceneBodies()) {
			System.out.println(Color.ANSI_CYAN+body+Color.ANSI_RESET);
			/*
			Iterator<Unit> iterator = body.getUnits().iterator();

			while (iterator.hasNext()) {
				Unit u = iterator.next();
				System.out.println("--AssignStmt--");
				if (u instanceof AssignStmt) {
					System.out.println(u);
				}
				System.out.println("--IfStmt--");
				if (u instanceof IfStmt) {
					System.out.println(u);
				}
			}
			List<Unit> switches = new LinkedList<Unit>();
			Map<Unit, List<Unit>> switchMap = new LinkedHashMap<Unit, List<Unit>>();

			
			*/
			List<UnitBox> Boxes = body.getUnitBoxes(true);
			for (UnitBox u: Boxes) {
				System.out.println(Color.ANSI_PURPLE+u+Color.ANSI_RESET);

				if (u.getUnit() instanceof JLookupSwitchStmt) {
					System.out.println(Color.ANSI_BLUE+"--Switch--"+Color.ANSI_RESET);
					System.out.println(Color.ANSI_GREEN+u.getUnit()+Color.ANSI_RESET);
					System.out.println(Color.ANSI_RED+u.getUnit().getUnitBoxes()+Color.ANSI_RESET);
					System.out.println("");

				}/*
				else if (u.getUnit() instanceof AssignStmt) {
					
					System.out.println(Color.ANSI_BLUE+"--Assign--"+Color.ANSI_RESET);
					System.out.println(Color.ANSI_GREEN+u.getUnit()+Color.ANSI_RESET);
					System.out.println(Color.ANSI_RED+u.getUnit().getUnitBoxes()+Color.ANSI_RESET);
					System.out.println("");
					
				}*/
				else if (u.getUnit() instanceof IfStmt) {
					System.out.println(Color.ANSI_BLUE+"--IfStmt--"+Color.ANSI_RESET);
					System.out.println(Color.ANSI_GREEN+u.getUnit()+Color.ANSI_RESET);
					System.out.println(Color.ANSI_RED+u.getUnit().getUnitBoxes()+Color.ANSI_RESET);
					System.out.println("");
				}
				else
					System.out.println(u.getUnit());
			}
			
			/*
			PatchingChain<Unit> units = body.getUnits();
			*/
			
		}
		
		// end
		
		
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
	
}