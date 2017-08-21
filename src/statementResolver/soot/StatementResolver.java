package statementResolver.soot;


import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.base.Preconditions;

import soot.Body;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.toolkits.scalar.UnreachableCodeEliminator;
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
		
	}
}