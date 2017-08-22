package statementResolver.soot;


import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.base.Preconditions;

import soot.Body;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.Expr;
import soot.jimple.GotoStmt;
import soot.jimple.IfStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.LookupSwitchStmt;
import soot.jimple.TableSwitchStmt;
import soot.jimple.internal.JLookupSwitchStmt;
import soot.jimple.toolkits.scalar.UnreachableCodeEliminator;
import soot.tagkit.Host;
import soottocfg.cfg.Program;
import soottocfg.cfg.SourceLocation;
import soottocfg.soot.util.DuplicatedCatchDetection;
import soottocfg.soot.util.SootTranslationHelpers;

import java.util.Set;


public class StatementResolver {
	
	public enum MemModel {
		PullPush
	}


	private final List<String> resolvedClassNames;

	private final Set<SourceLocation> locations = new LinkedHashSet<SourceLocation>();


	// Create a new program	
	private final Program program = new Program();

	//private final Program program = new Program();

	public StatementResolver() {
		this(new ArrayList<String>());
		SootTranslationHelpers.initialize(program);
	}


	public StatementResolver(List<String> resolvedClassNames) {
		this.resolvedClassNames = resolvedClassNames;
		// first reset everything:
		soot.G.reset();
		SootTranslationHelpers.initialize(program);
	}
	
	public void run(String input, String classPath) {
		SootRunner runner = new SootRunner();
		
		runner.run(input, classPath);
		performAnalysis();
	}
	private void addDefaultInitializers(SootMethod constructor, SootClass containingClass) {
		if (constructor.isConstructor()) {
			Preconditions.checkArgument(constructor.getDeclaringClass().equals(containingClass));
			JimpleBody jbody = (JimpleBody) constructor.retrieveActiveBody();

			// TODO: use this guy in instead.
			// jbody.insertIdentityStmts();

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
			for (SootField f : instanceFields) {
				Unit init;
				// if (SootTranslationHelpers.isDynamicTypeVar(f)) {
				// init = Jimple.v().newAssignStmt(
				// Jimple.v().newInstanceFieldRef(jbody.getThisLocal(),
				// f.makeRef()),
				// SootTranslationHelpers.v().getClassConstant(RefType.v(containingClass)));
				// } else {
				init = Jimple.v().newAssignStmt(Jimple.v().newInstanceFieldRef(jbody.getThisLocal(), f.makeRef()),
						SootTranslationHelpers.v().getDefaultValue(f.getType()));
				// }
				if (insertPos == null) {
					jbody.getUnits().addFirst(init);
				} else {
					jbody.getUnits().insertAfter(init, insertPos);
				}
			}

		}
	}
	
	public void performAnalysis() {

		List<SootClass> classes = new LinkedList<SootClass>(Scene.v().getClasses());
		for (SootClass sc : classes) {
			if (sc == SootTranslationHelpers.v().getAssertionClass()) {
				continue; // no need to process this guy.
			}

			if (sc.resolvingLevel() >= SootClass.SIGNATURES && sc.isApplicationClass()) {
				SootTranslationHelpers.v().setCurrentClass(sc);
				for (SootMethod sm : sc.getMethods()) {
					if (sm.isConcrete()) {
						addDefaultInitializers(sm, sc);

						SootTranslationHelpers.v().setCurrentMethod(sm);

						Body body = sm.retrieveActiveBody();
						try {
							body.validate();
						} catch (RuntimeException e) {
							System.out.println("Unable to validate method body. Possible NullPointerException?");
							throw e;
						}

						try {
							// System.out.println(body);
							UnreachableCodeEliminator.v().transform(body);
							// detect duplicated finally blocks
							DuplicatedCatchDetection duplicatedUnits = new DuplicatedCatchDetection();
							Map<Unit, Set<Unit>> duplicatedFinallyUnits = duplicatedUnits
									.identifiedDuplicatedUnitsFromFinallyBlocks(body);
							for (Entry<Unit, Set<Unit>> entry : duplicatedFinallyUnits.entrySet()) {
								locations.add(SootTranslationHelpers.v().getSourceLocation(entry.getKey()));
								for (Unit u : entry.getValue()) {
									locations.add(SootTranslationHelpers.v().getSourceLocation(u));
								}
							}
						} catch (RuntimeException e) {
							e.printStackTrace();
							throw new RuntimeException("Behavior preserving transformation failed " + sm.getSignature()
									+ " " + e.toString());
						}
					}
				}
			}
		}

		// Begin

		for (JimpleBody body : this.getSceneBodies()) {
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
			PatchingChain<Unit> units = body.getUnits();
			for (Unit u : units) {
				if (u instanceof JLookupSwitchStmt) {
					System.out.println("\u001B[34m--Switch--\u001B[0m");
					System.out.println("\u001B[35m"+u+"\u001B[0m");
					System.out.println("");
					/*
					switchMap.put(u, parseSwitchStatement((JLookupSwitchStmt) u));
					System.out.println(switchMap);
					*/
				}
				else if (u instanceof AssignStmt) {
					System.out.println("\u001B[34m--Assign--\u001B[0m");
					System.out.println("\u001B[35m"+u+"\u001B[0m");
					System.out.println("");
				}
				else if (u instanceof IfStmt) {
					System.out.println("\u001B[34m--IfStmt--\u001B[0m");
					System.out.println("\u001B[35m"+u+"\u001B[0m");
					System.out.println("");
				}
				else
					System.out.println(u);
			}
			/*
			for (Entry<Unit, List<Unit>> entry : switchMap.entrySet()) {
				System.out.println("entry");
				System.out.println(entry.getValue());
				System.out.println(entry.getKey());
			}
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
	
	private List<Unit> parseSwitchStatement(JLookupSwitchStmt s) {
		List<Unit> result = new LinkedList<Unit>();

		List<Expr> cases = new LinkedList<Expr>();
		List<Unit> targets = new LinkedList<Unit>();
		Unit defaultTarget = s.getDefaultTarget();

		if (s instanceof TableSwitchStmt) {
			TableSwitchStmt arg0 = (TableSwitchStmt) s;
			int counter = 0;
			for (int i = arg0.getLowIndex(); i <= arg0.getHighIndex(); i++) {
				cases.add(Jimple.v().newEqExpr(arg0.getKey(), IntConstant.v(i)));
				targets.add(arg0.getTarget(counter));
				counter++;
			}
		} else {
			LookupSwitchStmt arg0 = (LookupSwitchStmt) s;
			for (int i = 0; i < arg0.getTargetCount(); i++) {
				cases.add(Jimple.v().newEqExpr(arg0.getKey(), IntConstant.v(arg0.getLookupValue(i))));
				targets.add(arg0.getTarget(i));
			}
		}

		for (int i = 0; i < cases.size(); i++) {
			// create the ifstmt
			Unit ifstmt = ifStmtFor(cases.get(i), targets.get(i), s);
			result.add(ifstmt);
		}
		if (defaultTarget != null) {
			Unit gotoStmt = gotoStmtFor(defaultTarget, s);
			result.add(gotoStmt);
		}
		return result;
	
	}
	// Included
	protected Unit ifStmtFor(Value condition, Unit target, Host createdFrom) {
		IfStmt stmt = Jimple.v().newIfStmt(condition, target);
		stmt.addAllTagsOf(createdFrom);
		return stmt;
	}
	protected Unit gotoStmtFor(Unit target, Host createdFrom) {
		GotoStmt stmt = Jimple.v().newGotoStmt(target);
		stmt.addAllTagsOf(createdFrom);
		return stmt;
	}

}