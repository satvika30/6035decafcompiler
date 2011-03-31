package decaf.codegen.flatir;

import java.io.PrintStream;

public abstract class LIRStatement {
	protected boolean isLeader;
	
	public abstract void generateAssembly(PrintStream out);
	
	public boolean isLeader() {
		return isLeader;
	}
	
	public void setIsLeader(boolean bool) {
		isLeader = bool;
	}
	
	public boolean isExpressionStatement() {
		return false;
	}
	
	public boolean isAvailableExpression() {
		return false;
	}
}