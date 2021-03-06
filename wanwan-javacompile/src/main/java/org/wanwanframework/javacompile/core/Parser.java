package org.wanwanframework.javacompile.core;

import java.io.IOException;

import org.wanwanframework.javacompile.expresses.Constant;
import org.wanwanframework.javacompile.expresses.Express;
import org.wanwanframework.javacompile.expresses.Id;
import org.wanwanframework.javacompile.lexer.Lexer;
import org.wanwanframework.javacompile.lexer.Num;
import org.wanwanframework.javacompile.lexer.Tag;
import org.wanwanframework.javacompile.lexer.Token;
import org.wanwanframework.javacompile.lexer.Type;
import org.wanwanframework.javacompile.lexer.Word;
import org.wanwanframework.javacompile.logicals.And;
import org.wanwanframework.javacompile.logicals.Not;
import org.wanwanframework.javacompile.logicals.Or;
import org.wanwanframework.javacompile.logicals.Rel;
import org.wanwanframework.javacompile.ops.Access;
import org.wanwanframework.javacompile.ops.Arith;
import org.wanwanframework.javacompile.ops.Unary;
import org.wanwanframework.javacompile.stmts.Break;
import org.wanwanframework.javacompile.stmts.Do;
import org.wanwanframework.javacompile.stmts.Else;
import org.wanwanframework.javacompile.stmts.If;
import org.wanwanframework.javacompile.stmts.Seq;
import org.wanwanframework.javacompile.stmts.Set;
import org.wanwanframework.javacompile.stmts.SetElem;
import org.wanwanframework.javacompile.stmts.Stmt;
import org.wanwanframework.javacompile.stmts.While;
import org.wanwanframework.javacompile.symbols.Array;
import org.wanwanframework.javacompile.symbols.Env;

/**
 * 语法分析器
 * @author coco
 *
 */
public class Parser {

	private Lexer lex;
	private Token look;
	Env top = null;
	int used = 0; // 使用空间
	
	public Parser(Lexer l) throws IOException {
		lex = l; 
		moveToScan();
	}
	
	/**
	 * 读取一个词
	 * @throws IOException
	 */
	private void moveToScan() throws IOException {
		look = lex.scan();
	}
	
	private void error(String s) { 
		System.out.println("near line " + Lexer.line + ":" + s);
		//throw new Error("near line " + Lexer.line + ":" + s);
	}
	
	/**
	 * 如果读取到一个词的类型是t
	 * @param t
	 * @throws IOException
	 */
	private void matchAndScan(int t) throws IOException {
		if(look.tag == t) {
			moveToScan();
		}
		else {
			error("syntax error");
		}
	}
	
	public void start() throws IOException {
		Stmt s = readBlock();
		int begin = s.newlabel(); 
		int after = s.newlabel();
		s.emitlabel(begin); 
		s.gen(begin, after);
		s.emitlabel(after);
	}
	
	/**
	 * 大括号
	 * @return
	 * @throws IOException
	 */
	public Stmt readBlock() throws IOException {
		matchAndScan('{');
		Env savedEnv = top;
		top = new Env(top);
		decls(); 
		Stmt s = stmts();
		matchAndScan('}');
		top = savedEnv;
		return s;
	}
	
	public void decls() throws IOException {
		while(look.tag == Tag.BASIC ) {
			Type p = type(); 
			Token tok = look; 
			matchAndScan(Tag.ID );
			matchAndScan(';');
			Id id = new Id((Word) tok, p, used);
			top.put(tok, id);
			used = used + p.width;
		}
	}
	
	public Type type() throws IOException {
		Type p = (Type)look;
		matchAndScan(Tag.BASIC );
		if(look.tag != '[') return p;
		else return dims(p);
	}
	
	public Type dims(Type p) throws IOException {
		matchAndScan('[');
		Token tok = look;
		matchAndScan(Tag.NUM );
		matchAndScan(']');
		if(look.tag == '[')
			p = dims(p);
		return new Array(((Num)tok).value, p);
	}
	
	public Stmt stmts() throws IOException {
		if(look.tag == '}') {
			return Stmt.Null;
		} else {
			return new Seq(stmt(), stmts());
		}
	}
	
	public Stmt stmt() throws IOException {
		Express x; Stmt s1, s2;
		Stmt savedStmt;
		if(look.tag == ';') {
			moveToScan();
			return Stmt.Null;
		} else if(look.tag == Tag.IF ) {
			matchAndScan(Tag.IF );
			matchAndScan('(');
			x = bool();
			matchAndScan(')');
			s1 = stmt();
			if(look.tag != Tag.ELSE ) {
				return new If(x, s1);
			}
			matchAndScan(Tag.ELSE );
			s2 = stmt();
			return new Else(x, s1, s2);
		} else if(look.tag == Tag.WHILE ) {
			While whilenode = new While();
			savedStmt = Stmt.Enclosing;
			Stmt.Enclosing = whilenode;
			matchAndScan(Tag.WHILE );
			matchAndScan('(');
			x = bool();
			matchAndScan(')');
			s1 = stmt();
			whilenode.init(x, s1);
			Stmt.Enclosing = savedStmt;
			return whilenode;
		} else if(look.tag == Tag.DO ) {
			Do donode = new Do();
			savedStmt = Stmt.Enclosing;
			Stmt.Enclosing = donode;
			matchAndScan(Tag.DO );
			s1 = stmt();
			matchAndScan(Tag.WHILE );
			x = bool();
			matchAndScan(')');
			matchAndScan(';');
			donode.init(s1, x);
			Stmt.Enclosing = savedStmt;
			return donode;
		} else if(look.tag == Tag.BREAK ) {
			matchAndScan(Tag.BREAK );
			matchAndScan(';');
			return new Break();
		} else if(look.tag == '{') {
			return readBlock();
		}
		return assign();
	}
	
	/**
	 * 赋值语句
	 * @return
	 * @throws IOException
	 */
	public Stmt assign() throws IOException {
		Stmt stmt = null;
		Token t = look;
		matchAndScan(Tag.ID );
		Id id = top.get(t);
		if(id == null) {
			error(t.toString() + " undeclared");
		} else if(look.tag == '=') {
			moveToScan();
			stmt = new Set(id, bool());
		} else {
			System.out.println("look.tag:" + look.tag);
			Access x = null;
			if(id != null) {
				x = offset(id);
				matchAndScan('=');
				stmt = new SetElem(x, bool());
			}
			
		}
		matchAndScan(';');
		return stmt;
	}
	
	Express bool() throws IOException {
		Express x = join();
		while(look.tag == Tag.OR ) {
			Token tok = look;
			moveToScan();
			x = new Or(tok, x, join());
		}
		return x;
	}
	
	Express join() throws IOException {
		Express x = equality();
		while(look.tag == Tag.AND ) {
			Token tok = look;
			moveToScan();
			x = new And(tok, x, equality());
		}
		return x;
	}
	
	Express equality() throws IOException {
		Express x = rel();
		while(look.tag == Tag.EQ || look.tag == Tag.NE) {
			Token tok = look;
			moveToScan();
			x = new Rel(tok, x, rel());
		}
		return x;
	}
	
	Express rel() throws IOException {
		Express x = expr();
		if(look.tag == '<' ||
				look.tag == Tag.LE  ||
				look.tag == Tag.GE  ||
				look.tag == '>') {
			Token tok = look;
			moveToScan();
			return new Rel(tok, x, expr());
		}
		return x;
	}
	
	Express expr() throws IOException {
		Express x = term();
		while(look.tag == '+' || look.tag == '-') {
			Token tok = look; 
			moveToScan(); 
			x = new Arith(tok, x, term());
		}
		return x;
	}
	
	Express term() throws IOException {
		Express x = unary();
		while(look.tag == '*' || look.tag == '/') {
			Token tok = look;
			moveToScan();
			x = new Arith(tok, x, unary());
		}
		return x;
	}
	
	Express unary() throws IOException {
		if(look.tag == '-') {
			moveToScan();
			return new Unary(Word.minus, unary());
		}
		else if(look.tag == '!') {
			Token tok = look;
			moveToScan();
			return new Not(tok, unary());
		}
		return factor();
	}
	
	/**
	 * 数组地址计算
	 * @return
	 * @throws IOException
	 */
	public Express factor() throws IOException {
		Express x = null;
		if (look.tag == '(') {
			moveToScan();
			x = bool();
			matchAndScan(')');
			return x;
		} else if (look.tag == Tag.NUM ) {
			x = new Constant(look, Type.INT);
			moveToScan();
			return x;
		} else if (look.tag == Tag.REAL ) {
			x = new Constant(look, Type.FLOAT);
			moveToScan();
			return x;
		} else if (look.tag == Tag.TRUE ) {
			x = Constant.True;
			moveToScan();
			return x;
		} else if (look.tag == Tag.FALSE ) {
			x = Constant.False;
			moveToScan();
			return x;
		} else if (look.tag == Tag.ID ) {
			//String s = look.toString();
			Id id = top.get(look);
			if (id == null)
				error(look.toString() + " undeclared");
			moveToScan();
			if (look.tag != '[') {
				return id;
			} else {
				return offset(id);
			}
		} else {
			error("systax error");
			return x;
		}
	}
	
	public Access offset(Id a) throws IOException {
		if(a == null) {
			return null;
		}
		
		Express i; Express w; Express t1, t2; Express loc;
		Type type = a.type;
		matchAndScan('[');
		i = bool(); 
		matchAndScan(']');
		type = ((Array)type).of;
		w = new Constant(type.width);
		t1 = new Arith(new Token('*'), i, w);
		loc = t1;
		while(look.tag == '[') {
			matchAndScan('['); 
			i = bool(); 
			matchAndScan(']');
			type = ((Array)type).of;
			w = new Constant(type.width);
			t1 = new Arith(new Token('*'), i, w);
			t2 = new Arith(new Token('+'), loc, t1); 
			loc = t2;
		}
		return new Access(a, loc, type);
	}
}
