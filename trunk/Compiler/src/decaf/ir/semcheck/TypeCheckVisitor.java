package decaf.ir.semcheck;

import java.util.ArrayList;
import java.util.List;

import decaf.ir.ASTVisitor;
import decaf.ir.ast.ArrayLocation;
import decaf.ir.ast.AssignStmt;
import decaf.ir.ast.BinOpExpr;
import decaf.ir.ast.Block;
import decaf.ir.ast.BooleanLiteral;
import decaf.ir.ast.BreakStmt;
import decaf.ir.ast.CalloutArg;
import decaf.ir.ast.CalloutExpr;
import decaf.ir.ast.CharLiteral;
import decaf.ir.ast.ClassDecl;
import decaf.ir.ast.ContinueStmt;
import decaf.ir.ast.Expression;
import decaf.ir.ast.Field;
import decaf.ir.ast.FieldDecl;
import decaf.ir.ast.ForStmt;
import decaf.ir.ast.IfStmt;
import decaf.ir.ast.IntLiteral;
import decaf.ir.ast.InvokeStmt;
import decaf.ir.ast.MethodCallExpr;
import decaf.ir.ast.MethodDecl;
import decaf.ir.ast.Parameter;
import decaf.ir.ast.ReturnStmt;
import decaf.ir.ast.Statement;
import decaf.ir.ast.UnaryOpExpr;
import decaf.ir.ast.VarDecl;
import decaf.ir.ast.VarLocation;

import decaf.test.Error;

public class TypeCheckVisitor implements ASTVisitor<Integer> {
	
	private ArrayList<Error> errors;
	private boolean inFor;
	
	public TypeCheckVisitor() {
		this.errors = new ArrayList<Error>();
		this.inFor = false;
	}
	
	@Override
	public Integer visit(ArrayLocation loc) {
		loc.getExpr().accept(this);
		return 0;
	}

	@Override
	public Integer visit(AssignStmt stmt) {
		stmt.getLocation().accept(this);
		stmt.getExrpression().accept(this);
		return 0;
	}

	@Override
	public Integer visit(BinOpExpr expr) {
		expr.getLeftOperand().accept(this);
		expr.getRightOperand().accept(this);
		return 0;
	}

	@Override
	public Integer visit(Block block) {
		
		List<VarDecl> vDecls = block.getVarDeclarations();
		
		for (int i = 0; i < vDecls.size(); i++) {
			vDecls.get(i).accept(this);
		}
		
		List<Statement> stmts = block.getStatements();
		
		for (int i = 0; i < stmts.size(); i++) {
			stmts.get(i).accept(this);
		}
		
		return 0;
	}

	@Override
	public Integer visit(BooleanLiteral lit) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer visit(BreakStmt stmt) {
		if (this.inFor == false) {
			int ln = stmt.getLineNumber();
			int cn = stmt.getColumnNumber();
			String msg = "break statement outside of for loop";
			Error err = new Error(ln, cn, msg);
			this.errors.add(err);
		}
		return 0;
	}

	@Override
	public Integer visit(CalloutArg arg) {
		if (!arg.isString()) {
			arg.getExpression().accept(this);
		}
		return 0;
	}

	@Override
	public Integer visit(CalloutExpr expr) {
		for (CalloutArg arg: expr.getArgs()) {
			arg.accept(this);
		}
		
		return 0;
	}

	@Override
	public Integer visit(CharLiteral lit) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer visit(ClassDecl cd) {
		for (FieldDecl fd: cd.getFieldDeclarations()) {
			fd.accept(this);
		}
		
		for (MethodDecl md: cd.getMethodDeclarations()) {
			md.accept(this);
		}
		return 0;
	}

	@Override
	public Integer visit(ContinueStmt stmt) {
		if (this.inFor == false) {
			int ln = stmt.getLineNumber();
			int cn = stmt.getColumnNumber();
			String msg = "continue statement outside of for loop";
			Error err = new Error(ln, cn, msg);
			this.errors.add(err);
		}
		return 0;
	}

	@Override
	public Integer visit(Field f) {
		
		// checking the size of the array
		if (f.isArray()) {
			if (!(f.getArrayLength().getValue() > 0)) {
				int ln = f.getLineNumber();
				int cn = f.getColumnNumber();
				String msg = "Size of array not greater than 0";
				Error err = new Error(ln, cn, msg);
				this.errors.add(err);
			}
		}
		return null;
	}

	@Override
	public Integer visit(FieldDecl fd) {
		//TODO: right now only checks the array size
		for (Field f: fd.getFields()) {
			f.accept(this);
		}
		return 0;
	}

	@Override
	public Integer visit(ForStmt stmt) {
		this.inFor = true;
		stmt.getInitialValue().accept(this);
		stmt.getFinalValue().accept(this);
		stmt.getBlock().accept(this);
		this.inFor  =false;
		return 0;
	}

	@Override
	public Integer visit(IfStmt stmt) {
		stmt.getCondition().accept(this);
		stmt.getIfBlock().accept(this);
		if (stmt.getElseBlock() != null) {
			stmt.getElseBlock().accept(this);
		}
		
		return 0;
	}

	@Override
	public Integer visit(IntLiteral lit) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer visit(InvokeStmt stmt) {
		stmt.getMethodCall().accept(this);
		return 0;
	}

	@Override
	public Integer visit(MethodCallExpr expr) {
		for (Expression arg: expr.getArgs()) {
			arg.accept(this);
		}
		return 0;
	}

	@Override
	public Integer visit(MethodDecl md) {
		for (Parameter p: md.getParamters()) {
			p.accept(this);
		}
		md.getBlock().accept(this);
		
		return 0;
	}

	@Override
	public Integer visit(Parameter param) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer visit(ReturnStmt stmt) {
		if (stmt.getExpression() != null) {
			stmt.getExpression().accept(this);
		}
		
		return 0;
	}

	@Override
	public Integer visit(UnaryOpExpr expr) {
		expr.getExpression().accept(this);
		return 0;
	}

	@Override
	public Integer visit(VarDecl vd) {
		return 0;
	}

	@Override
	public Integer visit(VarLocation loc) {
		return 0;
	}

	public ArrayList<Error> getErrors() {
		return errors;
	}

}