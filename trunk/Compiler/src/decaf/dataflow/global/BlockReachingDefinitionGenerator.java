package decaf.dataflow.global;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import decaf.codegen.flatir.ArrayName;
import decaf.codegen.flatir.CallStmt;
import decaf.codegen.flatir.ConstantName;
import decaf.codegen.flatir.LIRStatement;
import decaf.codegen.flatir.Name;
import decaf.codegen.flatir.QuadrupletStmt;
import decaf.codegen.flatir.Register;
import decaf.codegen.flatir.RegisterName;
import decaf.codegen.flatir.VarName;
import decaf.codegen.flattener.ProgramFlattener;
import decaf.dataflow.cfg.CFGBlock;
import decaf.dataflow.cfg.MethodIR;

public class BlockReachingDefinitionGenerator {
	private HashMap<String, MethodIR> mMap;
	private HashMap<CFGBlock, BlockDataFlowState> blockReachingDefs;
	private HashSet<CFGBlock> cfgBlocksToProcess;
	// Map from Name to QuadrupletStmt which assign to that Name
	private HashMap<Name, ArrayList<QuadrupletStmt>> nameToQStmts;
	// Unique set of QuadrupletStmts
	private HashSet<QuadrupletStmt> uniqueQStmts;

	private int totalDefinitions;

	public BlockReachingDefinitionGenerator(HashMap<String, MethodIR> mMap) {
		this.mMap = mMap;
		nameToQStmts = new HashMap<Name, ArrayList<QuadrupletStmt>>();
		uniqueQStmts = new HashSet<QuadrupletStmt>();
		blockReachingDefs = new HashMap<CFGBlock, BlockDataFlowState>();
		cfgBlocksToProcess = new HashSet<CFGBlock>();
		totalDefinitions = 0;
	}
	
	public void generate() {
		initialize();
		if (totalDefinitions == 0)
			return;
		// Get the first block in the main function - TODO: is there a better way?
		CFGBlock entry = this.getBlockById("main", 0);
		BlockDataFlowState entryBlockFlow = new BlockDataFlowState(totalDefinitions);
		calculateGenKillSets(entry, entryBlockFlow);
		entryBlockFlow.setOut(entryBlockFlow.getGen());
		cfgBlocksToProcess.remove(entry);
		blockReachingDefs.put(entry, entryBlockFlow);
		
		while (cfgBlocksToProcess.size() != 0) {
			CFGBlock block = (CFGBlock)(cfgBlocksToProcess.toArray())[0];
			BlockDataFlowState bFlow = generateForBlock(block);
			blockReachingDefs.put(block, bFlow);
		}
		
		for (CFGBlock cfgBlock : blockReachingDefs.keySet()) {
			System.out.println(cfgBlock);
			System.out.println(blockReachingDefs.get(cfgBlock));
		}
	}
	
	private CFGBlock getBlockById(String name, int i) {
		if (this.mMap.containsKey(name)) {
			for (CFGBlock b: this.mMap.get(name).getCfgBlocks()) {
				if (b.getIndex() == i) return b;
			}
		}
		
		return null;
	}
	
	private void initialize() {
		// QuadrupletStmt IDs that we assign should start from 0, so they can
		// correspond to the appropriate index in the BitSet
		QuadrupletStmt.setID(0);
		for (String s: this.mMap.keySet()) {
			if (s.equals(ProgramFlattener.exceptionHandlerLabel)) continue;
			
			for (CFGBlock block: this.mMap.get(s).getCfgBlocks()) {
				List<LIRStatement> blockStmts = block.getStatements();
				for (int i = 0; i < blockStmts.size(); i++) {
					LIRStatement stmt = blockStmts.get(i);
					if (stmt.getClass().equals(QuadrupletStmt.class)) {
						QuadrupletStmt qStmt = (QuadrupletStmt)stmt;
						Name dest = qStmt.getDestination();
						if (dest != null) {
							// Destination has to be non-null for this to be a valid definition
							// of something
							qStmt.setMyId();
							if (!nameToQStmts.containsKey(dest)) {
								nameToQStmts.put(dest, new ArrayList<QuadrupletStmt>());
							}
							nameToQStmts.get(dest).add(qStmt);
							uniqueQStmts.add(qStmt);
							System.out.println(qStmt + " ==>> " + qStmt.getMyId());
							totalDefinitions++;
						}
					}
				}
				cfgBlocksToProcess.add(block);
			}
		}
	}
	
	private BlockDataFlowState generateForBlock(CFGBlock block) {
		// Get the original out BitSet for this block
		BitSet origOut;
		if (blockReachingDefs.containsKey(block)) {
			origOut = blockReachingDefs.get(block).getOut();
		} else {
			origOut = new BitSet(totalDefinitions);
		}
		// Calculate the in BitSet by taking union of predecessors
		BlockDataFlowState bFlow = new BlockDataFlowState(totalDefinitions);
		BitSet in = bFlow.getIn();
		for (CFGBlock pred : block.getPredecessors()) {
			if (blockReachingDefs.containsKey(pred)) {
				in.or(blockReachingDefs.get(pred).getOut());
			}
		} 
		calculateGenKillSets(block, bFlow);
		// Calculate Out
		BitSet out = bFlow.getOut();
		out.or(in);
		out.xor(bFlow.getKill()); // Invariant: kill is a subset of in
		out.or(bFlow.getGen());
		if (!out.equals(origOut)) {
			// Add successors to cfgBlocks list
			for (CFGBlock succ : block.getSuccessors()) {
				if (!cfgBlocksToProcess.contains(succ)) {
					cfgBlocksToProcess.add(succ);
				}
			}
		}
		// Remove this block, since it has been processed
		cfgBlocksToProcess.remove(block);
		return bFlow;
	}
	
	private void calculateGenKillSets(CFGBlock block, BlockDataFlowState bFlow) {
		BitSet gen = bFlow.getGen();
		List<LIRStatement> blockStmts = block.getStatements();
		
		for (LIRStatement stmt : blockStmts) {
			if (stmt.getClass().equals(CallStmt.class)) {
				CallStmt callStmt = (CallStmt) stmt;
				if (callStmt.getMethodLabel().equals(ProgramFlattener.exceptionHandlerLabel)) continue;
				
				invalidateFunctionCall(bFlow);
				continue;
			}
			if (stmt.getClass().equals(QuadrupletStmt.class)) {
				QuadrupletStmt qStmt = (QuadrupletStmt)stmt;
				Name dest = qStmt.getDestination();
				if (dest != null) {
					// Valid reaching definition
					updateKillSet(dest, bFlow);
					// Gen - add current statement id
					gen.set(qStmt.getMyId(), true);
				}
			}
		}
	}
	
	public void invalidateFunctionCall(BlockDataFlowState bFlow) {
		// Invalidate arg registers
		for (int i = 0; i < Register.argumentRegs.length; i++) {
			updateKillSet(new RegisterName(Register.argumentRegs[i]), bFlow);
		}
	
		// Reset symbolic value for %RAX
		updateKillSet(new RegisterName(Register.RAX), bFlow);
		
		// Invalidate global vars;
		for (Name name: this.nameToQStmts.keySet()) {
			if (name.getClass().equals(VarName.class)) {
				VarName var = (VarName) name;
				if (var.getBlockId() == -1) { // Global
					updateKillSet(name, bFlow);
				}
			}
			if (name.getClass().equals(ArrayName.class)) {
				updateKillSet(name, bFlow);
			}
		}
	}
	
	public void updateKillSet(Name dest, BlockDataFlowState bFlow) {
		BitSet kill = bFlow.getKill();
		BitSet in = bFlow.getIn();
		ArrayList<QuadrupletStmt> qStmtsForDest = nameToQStmts.get(dest);
		if (qStmtsForDest != null) {
			// Kill if it is part of In
			for (QuadrupletStmt q : qStmtsForDest) {
				int index = q.getMyId();
				if (in.get(index)) {
					kill.set(index, true); // Ensures Kill is always a subset of In
				}
			}
		}
		// If the dest is ArrayName of the form A[i], and if index is constant,
		// invalidate all statements which assign A[k] where k is a non-constant
		// and if index is not a constant, invalidate all statements which assign
		// any index in A
		if (dest.getClass().equals(ArrayName.class)) {
			Name arrIndex = ((ArrayName)dest).getIndex();
			String id = ((ArrayName)dest).getId();
			if (arrIndex.getClass().equals(ConstantName.class)) {
				for (QuadrupletStmt qStmt : uniqueQStmts) {
					if (isArrayNameWithIdAndVariableIndex(qStmt.getDestination(), id)) {
						int index = qStmt.getMyId();
						if (in.get(index)) {
							kill.set(index, true); // Ensures Kill is always a subset of In
						}
					}
				}
			} else {
				for (QuadrupletStmt qStmt : uniqueQStmts) {
					if (isArrayNameWithId(qStmt.getDestination(), id)) {
						int index = qStmt.getMyId();
						if (in.get(index)) {
							kill.set(index, true); // Ensures Kill is always a subset of In
						}
					}
				}
			}
		}
		// dest may be an index, so invalidate all
		// statements which use dest as an index in a statement's dest
		for (QuadrupletStmt qStmt : uniqueQStmts) {
			if (isQStmtUsingArrayIndexInDest(qStmt, dest)) {
				int index = qStmt.getMyId();
				if (in.get(index)) {
					kill.set(index, true); // Ensures Kill is always a subset of In
				}
			}
		}
	}
	
	public boolean isArrayNameWithIdAndVariableIndex(Name name, String id) {
		if (name == null)
			return false;
		if (name.getClass().equals(ArrayName.class)) {
			if (isArrayNameWithId(name, id)) {	
				Name arrIndex = ((ArrayName)name).getIndex();
				if (!(arrIndex.getClass().equals(ConstantName.class))) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean isArrayNameWithId(Name name, String id) {
		if (name == null)
			return false;
		if (name.getClass().equals(ArrayName.class)) {
			if (((ArrayName)name).getId().equals(id)) {
				return true;
			}
		}
		return false;
	}
	
	private boolean isQStmtUsingArrayIndexInDest(QuadrupletStmt stmt, Name index) {
		Name dest = stmt.getDestination();
		Name arrIndex;
		if (dest != null) {
			if (dest.getClass().equals(ArrayName.class)) {
				arrIndex = ((ArrayName)dest).getIndex();
				if (arrIndex.equals(index))
					return true;
			}
		}
		return false;
	}
	
	public HashSet<QuadrupletStmt> getUniqueQStmts() {
		return uniqueQStmts;
	}

	public void setUniqueQStmts(HashSet<QuadrupletStmt> uniqueQStmts) {
		this.uniqueQStmts = uniqueQStmts;
	}
	
	public HashMap<Name, ArrayList<QuadrupletStmt>> getNameToQStmts() {
		return nameToQStmts;
	}

	public void setNameToQStmts(HashMap<Name, ArrayList<QuadrupletStmt>> nameToQStmts) {
		this.nameToQStmts = nameToQStmts;
	}
	
	public HashMap<CFGBlock, BlockDataFlowState> getBlockReachingDefs() {
		return blockReachingDefs;
	}

	public void setBlockReachingDefs(HashMap<CFGBlock, BlockDataFlowState> blockReachingDefs) {
		this.blockReachingDefs = blockReachingDefs;
	}
	
	public int getTotalDefinitions() {
		return totalDefinitions;
	}

	public void setTotalDefinitions(int totalDefinitions) {
		this.totalDefinitions = totalDefinitions;
	}
}