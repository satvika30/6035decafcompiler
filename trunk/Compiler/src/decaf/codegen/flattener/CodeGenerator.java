package decaf.codegen.flattener;

import java.io.PrintStream;
import java.util.List;

import decaf.codegen.flatir.DataStmt;
import decaf.codegen.flatir.LIRStatement;
import decaf.codegen.flatir.LeaveStmt;
import decaf.ir.ast.ClassDecl;
import decaf.ir.ast.MethodDecl;

public class CodeGenerator {
	private ProgramFlattener pf;
	private PrintStream out;
	private ClassDecl cd;
	
	public CodeGenerator(ProgramFlattener pf, ClassDecl cd) {
		this.out = System.out;
		this.pf = pf;
		this.cd = cd;
	}
	
	public void generateCode() {
		out.println(".data");
		for (DataStmt s: pf.getDataStmtList()) {
			s.generateAssembly(out);
		}
		
		out.println();
		out.println(".text");
		for (MethodDecl md: cd.getMethodDeclarations()) {
			out.println();
			List<LIRStatement> lirList = pf.getLirMap().get(md.getId());
			if (md.getId().equals("main")) {
				out.println("\t.globl main");
			}
			for (LIRStatement s: lirList) {
				if (md.getId().equals("main") && s.getClass().equals(LeaveStmt.class)) {
					out.println("\tmov\t$0, %rax");
				}
				//out.println(s.toString());
				s.generateAssembly(out);
			}
		}
	}
}
