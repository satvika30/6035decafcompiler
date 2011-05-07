package decaf.dataflow.global;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import decaf.codegen.flatir.ArrayName;
import decaf.codegen.flatir.ConstantName;
import decaf.codegen.flatir.LIRStatement;
import decaf.codegen.flatir.LabelStmt;
import decaf.codegen.flatir.Name;
import decaf.codegen.flatir.QuadrupletStmt;
import decaf.codegen.flatir.RegisterName;
import decaf.dataflow.cfg.MethodIR;

// Finds loop invariant statements in loops
// A loop invariant statement (t = a1 op a2) satifies one of the following conditions:
// 1. ai is a constant
// 2. all definitions of ai that reach the statement are outside the loop

// Uses LoopQuadrupletStmt, a container class which stores QuadrupletStmt and CFGBlock id 
// of the loop body block which the QuadrupletStmt is in

public class LoopInvariantGenerator {
	private HashMap<String, MethodIR> mMap;
	// Map from QuadrupletStmt id to LoopQuadrupletStmt
	private HashSet<LoopQuadrupletStmt> loopInvariantStmts;
	// Map from QuadrupletStmt to BitSet representing all the QuadrupletStmt IDs which reach that point
	private HashMap<QuadrupletStmt, BitSet> reachingDefForQStmts;
	// Map from loop body id to a HashSet of all the LoopQuadrupletStmts in that loop body
	private HashMap<String, HashSet<LoopQuadrupletStmt>> allLoopBodyQStmts;
	// This optimizer isn't related to LoopInvariant optimizations, but it updates the Reaching definitions
	// which we need for loop optimizations
	private GlobalConstantPropagationOptimizer gcp;
	private static String ForInitLabelRegex = "[a-zA-z_]\\w*.for\\d+.init";
	private static String ForEndLabelRegex = "[a-zA-z_]\\w*.for\\d+.end";
	
	public LoopInvariantGenerator(HashMap<String, MethodIR> mMap) {
		this.mMap = mMap;
		this.loopInvariantStmts = new HashSet<LoopQuadrupletStmt>();
		gcp = new GlobalConstantPropagationOptimizer(mMap);
		// Generates the Map of QStmt -> BitSet representing all the QStmts IDs which reach at that point
		gcp.performGlobalConstantProp();
		reachingDefForQStmts = gcp.getReachingDefForQStmts();
		allLoopBodyQStmts = getLoopBodyQuadrupletStmts();
	}
	
	public void generateLoopInvariants() {
		// Keep loop until no more loop invariants are added
		int numLoopInvariants;
		do {
			numLoopInvariants = loopInvariantStmts.size();
			for (String loopBodyId : allLoopBodyQStmts.keySet()) {
				for (LoopQuadrupletStmt lQStmt : allLoopBodyQStmts.get(loopBodyId)) {
					if (isLoopInvariant(lQStmt)) {
						loopInvariantStmts.add(lQStmt);
					}
				}
			}
		} while (numLoopInvariants != loopInvariantStmts.size());
		
		System.out.println("LOOP INVARIANT STMTS: " + loopInvariantStmts);
	}
	
	// Returns a map which maps a loop body CFGBlock to all the QuadrupletStmts in that block
	// For QuadrupletStmts which are in nested for loops, the stmt is only added in the most nested
	// loop which it is a part of
	private HashMap<String, HashSet<LoopQuadrupletStmt>> getLoopBodyQuadrupletStmts() {
		HashMap<String, HashSet<LoopQuadrupletStmt>> loopQuadrupletStmts = 
			new HashMap<String, HashSet<LoopQuadrupletStmt>>();
		String forLabel;
		HashSet<LoopQuadrupletStmt> loopStmts;
		for (String s : mMap.keySet()) {
			boolean inFor = false;
			List<String> forIdList = new ArrayList<String>();
			for (LIRStatement stmt : mMap.get(s).getStatements()) {
				if (stmt.getClass().equals(LabelStmt.class)) {
					forLabel = ((LabelStmt)stmt).getLabelString();
					if (forLabel.matches(ForInitLabelRegex)) {
						forIdList.add(getIdFromForLabel(forLabel));
					} else if (forLabel.matches(ForEndLabelRegex)) {
						forIdList.remove(forIdList.size()-1);
					}
					inFor = !forIdList.isEmpty();
					continue;
				} else if (inFor) { 
					if (stmt.getClass().equals(QuadrupletStmt.class)) {
						// Ensure it doesn't assign to register or use register
						if (usesRegisters((QuadrupletStmt)stmt))
							continue;
						// Add the QuadrupletStmt to all the loops in the list
						for (String forId : forIdList) {
							if (!loopQuadrupletStmts.containsKey(forId)) {
								loopStmts = new HashSet<LoopQuadrupletStmt>();
								loopQuadrupletStmts.put(forId, loopStmts);
							} else {
								loopStmts = loopQuadrupletStmts.get(forId);
							}
							loopStmts.add(new LoopQuadrupletStmt((QuadrupletStmt)stmt, forId));
						}
					}
				}
			}
		}
		return loopQuadrupletStmts;
	}
	
	// Returns true if the LoopQuadrupletStmt is a loop invariant stmt, False otherwise
	private boolean isLoopInvariant(LoopQuadrupletStmt loopQStmt) {
		System.out.println("processing " + loopQStmt.getqStmt());
		QuadrupletStmt qStmt = loopQStmt.getqStmt();
		HashSet<QuadrupletStmt> loopQStmts = getQuadrupletStmtsInLoopBody(loopQStmt.getLoopBodyBlockId());
		BitSet reachingDefForQStmt = reachingDefForQStmts.get(qStmt);
		Name arg1 = qStmt.getArg1();
		Name arg2 = qStmt.getArg2();
		if (arg1 != null) {
			if (argSatisfiesLoopInvariant(arg1, reachingDefForQStmt, loopQStmts)) {
				if (arg2 != null) {
					if (argSatisfiesLoopInvariant(arg2, reachingDefForQStmt, loopQStmts)) {
						// Both args satisify loop invariant properties
						return true;
					}
				} else {
					// arg1 satisifies loop invariant properties, arg2 doesn't exist
					return true;
				}
			}
		} else {
			// Neither arg1 nor arg2 exists
			// Assumes that if arg1 doesn't exist, arg2 cannot exist
			return true;
		}
		return false;
	}
	
	private boolean argSatisfiesLoopInvariant(Name arg, BitSet reachingDefForQStmt, 
			HashSet<QuadrupletStmt> loopQStmts) {
		boolean argSatisfiesLoopInvariant = false;
		System.out.println("FOR ARG: " + arg);
		// Check if arg is a constant
		if (arg.getClass().equals(ConstantName.class)) {
			argSatisfiesLoopInvariant = true;
		} else {
			// Get all possible definitions for the arg
			List<QuadrupletStmt> defsForArg = gcp.getDefinitionsForName(arg);
			if (defsForArg != null) {
				List<QuadrupletStmt> reachingDefsForArg = new ArrayList<QuadrupletStmt>();
				// Use BitSet to generate list of reaching definitions of the arg
				for (QuadrupletStmt qs : defsForArg) {
					if (reachingDefForQStmt.get(qs.getMyId())) {
						reachingDefsForArg.add(qs);
					}
				}
				// Check if all defs which are reaching are outside the loop body
				argSatisfiesLoopInvariant = true;
				for (QuadrupletStmt reachingQStmt : reachingDefsForArg) {
					if (loopQStmts.contains(reachingQStmt)) {
						// A reaching definition is in the loop
						argSatisfiesLoopInvariant = false;
					}
				}
			} else {
				// No definitions for arg - can be a global arg which is never assigned
				argSatisfiesLoopInvariant = true;
			}
		}
		// If arg is ArrayName, ensure that the index also satisfies loop invariant properties
		if (arg.getClass().equals(ArrayName.class)) {
			boolean indexSatisifed = argSatisfiesLoopInvariant(((ArrayName)arg).getIndex(), 
					reachingDefForQStmt, loopQStmts);
			return indexSatisifed && argSatisfiesLoopInvariant;
		}
		return argSatisfiesLoopInvariant;
	}
	
	public HashSet<QuadrupletStmt> getQuadrupletStmtsInLoopBody(String forId) {
		HashSet<QuadrupletStmt> loopQStmts = new HashSet<QuadrupletStmt>();
		HashSet<LoopQuadrupletStmt> blockLoopQStmts = allLoopBodyQStmts.get(forId);
		for (LoopQuadrupletStmt lqs : blockLoopQStmts) {
			loopQStmts.add(lqs.getqStmt());
		}
		return loopQStmts;
	}
	
	private boolean usesRegisters(QuadrupletStmt qStmt) {
		Name dest = qStmt.getDestination();
		Name arg1 = qStmt.getArg1();
		Name arg2 = qStmt.getArg2();
		if (dest != null) {
			if (dest.getClass().equals(RegisterName.class)) {
				return true;
			}
		}
		if (arg1 != null) {
			if (arg1.getClass().equals(RegisterName.class)) {
				return true;
			}
		}
		if (arg2 != null) {
			if (arg2.getClass().equals(RegisterName.class)) {
				return true;
			}
		}
		return false;
	}
	
	private String getIdFromForLabel(String label) {
		String[] forInfo = label.split("\\.");
		System.out.println(label);
		return forInfo[0] + forInfo[1];
	}
	
	public HashSet<LoopQuadrupletStmt> getLoopInvariantStmts() {
		return loopInvariantStmts;
	}

	public void setLoopInvariantStmts(
			HashSet<LoopQuadrupletStmt> loopInvariantStmts) {
		this.loopInvariantStmts = loopInvariantStmts;
	}
	
	public HashMap<String, HashSet<LoopQuadrupletStmt>> getAllLoopBodyQStmts() {
		return allLoopBodyQStmts;
	}

	public void setAllLoopBodyQStmts(
			HashMap<String, HashSet<LoopQuadrupletStmt>> allLoopBodyQStmts) {
		this.allLoopBodyQStmts = allLoopBodyQStmts;
	}
	
	public class LoopQuadrupletStmt {
		private QuadrupletStmt qStmt;
		private String loopBodyBlockId;

		public LoopQuadrupletStmt(QuadrupletStmt q, String loopId) {
			this.qStmt = q;
			this.loopBodyBlockId = loopId;
		}
		
		public QuadrupletStmt getqStmt() {
			return qStmt;
		}

		public void setqStmt(QuadrupletStmt qStmt) {
			this.qStmt = qStmt;
		}
		
		public String getLoopBodyBlockId() {
			return loopBodyBlockId;
		}

		public void setLoopBodyBlockId(String loopBodyId) {
			this.loopBodyBlockId = loopBodyId;
		}
		
		@Override
		public int hashCode() {
			return toString().hashCode();
		}
		
		@Override
		public String toString() {
			return loopBodyBlockId + " " + qStmt.toString();
		}
	}
}
