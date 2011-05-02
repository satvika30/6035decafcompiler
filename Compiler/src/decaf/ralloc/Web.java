package decaf.ralloc;

import java.util.ArrayList;
import java.util.List;

import decaf.codegen.flatir.CmpStmt;
import decaf.codegen.flatir.LIRStatement;
import decaf.codegen.flatir.LoadStmt;
import decaf.codegen.flatir.Name;
import decaf.codegen.flatir.PopStmt;
import decaf.codegen.flatir.PushStmt;
import decaf.codegen.flatir.QuadrupletStmt;
import decaf.codegen.flatir.Register;
import decaf.codegen.flatir.StoreStmt;

public class Web {
	private Name variable;
	private List<LIRStatement> definitions;
	private List<LIRStatement> uses;
	private Register register;
	private int firstStmtIndex;
	private int lastStmtIndex;
	
	public Web(Name variable) {
		this.variable = (Name)variable.clone(); // important to clone
		this.definitions = new ArrayList<LIRStatement>();
		this.uses = new ArrayList<LIRStatement>();
		this.register = null;
	}

	public Name getVariable() {
		return variable;
	}

	public void setVariable(Name variable) {
		this.variable = variable;
	}

	public List<LIRStatement> getDefinitions() {
		return definitions;
	}

	public void setDefinitions(List<LIRStatement> definitions) {
		this.definitions = definitions;
	}
	
	public void addDefinition(LIRStatement definition) {
		for (LIRStatement stmt: this.definitions) {
			if (stmt == definition) return;
		}
		
		processDefinition(definition);
		
		this.definitions.add(definition);
	}

	private void processDefinition(LIRStatement definition) {
		if (definition.getClass().equals(LoadStmt.class)) {
			LoadStmt lStmt = (LoadStmt) definition;
			lStmt.setVariable(this.variable);
		}
		else if (definition.getClass().equals(QuadrupletStmt.class)) {
			QuadrupletStmt qStmt = (QuadrupletStmt) definition;
			qStmt.setDestination(this.variable);
		}
	}

	public List<LIRStatement> getUses() {
		return uses;
	}

	public void setUses(List<LIRStatement> uses) {
		this.uses = uses;
	}
	
	public void addUse(LIRStatement use) {
		for (LIRStatement stmt: this.uses) {
			if (stmt == use) return;
		}
		
		processUse(use);
		
		this.uses.add(use);
	}

	private void processUse(LIRStatement use) {
		if (use.getClass().equals(QuadrupletStmt.class)) {
			QuadrupletStmt qStmt = (QuadrupletStmt) use;
			if (this.variable.equals(qStmt.getArg1())) {
				qStmt.setArg1(this.variable);
			}
			else if (this.variable.equals(qStmt.getArg2())) {
				qStmt.setArg2(this.variable);
			}
		}
		else if (use.getClass().equals(CmpStmt.class)) {
			CmpStmt cStmt = (CmpStmt) use;
			if (this.variable.equals(cStmt.getArg1())) {
				cStmt.setArg1(this.variable);
			}
			else if (this.variable.equals(cStmt.getArg2())) {
				cStmt.setArg2(this.variable);
			}
		}
		else if (use.getClass().equals(PushStmt.class)) {
			PushStmt pStmt = (PushStmt) use;
			if (pStmt.getName().equals(this.variable)) {
				pStmt.setName(this.variable);
			}
		}
		else if (use.getClass().equals(PopStmt.class)) {
			PopStmt pStmt = (PopStmt) use;
			if (pStmt.getName().equals(this.variable)) {
				pStmt.setName(this.variable);
			}
		}
		else if (use.getClass().equals(StoreStmt.class)) {
			StoreStmt sStmt = (StoreStmt) use;
			if (sStmt.getVariable().equals(this.variable)) {
				sStmt.setVariable(this.variable);
			}
		}
	}

	public Register getRegister() {
		return register;
	}

	public void setRegister(Register register) {
		this.register = register;
	}
	
	@Override
	public String toString() {
		String rtn = "VAR: " + this.variable.toString() + " - (" + this.firstStmtIndex + ", " + this.lastStmtIndex + ")\n";
		rtn += "DEF: " + this.definitions.toString() + "\n";
		rtn += "USE: " + this.uses.toString();
		
		return rtn;
	}

	public void setLastStmtIndex(int lastStmtIndex) {
		this.lastStmtIndex = lastStmtIndex;
	}

	public int getLastStmtIndex() {
		return lastStmtIndex;
	}

	public void setFirstStmtIndex(int firstStmtIndex) {
		this.firstStmtIndex = firstStmtIndex;
	}

	public int getFirstStmtIndex() {
		return firstStmtIndex;
	}
	
	public void combineWeb(Web w) {		
		for (LIRStatement s1: w.getUses()) {
			boolean add = true;
			for (LIRStatement s2: this.uses) {
				if (s1 == s2) add = false;
			}
			
			if (add) {
				processUse(s1);
				this.uses.add(s1);
			}
		}
		
		for (LIRStatement s1: w.getDefinitions()) {
			boolean add = true;
			for (LIRStatement s2: this.definitions) {
				if (s1 == s2) add = false;
			}
			
			if (add) {
				processDefinition(s1);
				this.definitions.add(s1);
			}
		}
	}
}
