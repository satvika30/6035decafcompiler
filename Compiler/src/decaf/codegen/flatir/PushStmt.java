package decaf.codegen.flatir;

import java.io.PrintStream;

public class PushStmt extends LIRStatement {
	private Name address; // Can be register, memory or immediate
	
	public PushStmt(Name address) {
		this.setAddress(address);
	}

	public void setAddress(Name address) {
		this.address = address;
	}

	public Name getAddress() {
		return address;
	}
	
	@Override
	public String toString() {
		return "push " + address;
	}
	
	@Override
	public void generateAssembly(PrintStream out) {
		out.println("\tpush\t" + this.address.getLocation().getASMRepresentation());
	}
}
