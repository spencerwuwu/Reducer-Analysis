/**
 * 
 */
package soottocfg.cfg.expression;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;

import soottocfg.cfg.SourceLocation;
import soottocfg.cfg.expression.literal.BooleanLiteral;
import soottocfg.cfg.expression.literal.IntegerLiteral;
import soottocfg.cfg.type.BoolType;
import soottocfg.cfg.type.Type;
import soottocfg.cfg.variable.Variable;
import soottocfg.soot.util.SootTranslationHelpers;

/**
 * @author schaef
 *
 */
public class BinaryExpression extends Expression {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1992559147136566989L;

	public enum BinaryOperator {
		Plus("+"), Minus("-"), Mul("*"), Div("/"), Mod("%"), And("&&"), Or("||"), Xor("^"), Implies("->"), Eq("=="), Ne(
				"!="), Gt(">"), Ge(">="), Lt("<"), Le("<="), Shl("<<"), Shr(">>"), Ushr("u>>"), BOr("|"), BAnd("&"),
		PoLeq("<:");

		private final String name;

		private BinaryOperator(String s) {
			name = s;
		}

		public boolean equalsName(String otherName) {
			return (otherName == null) ? false : name.equals(otherName);
		}

		@Override
		public String toString() {
			return this.name;
		}
	}

	private Expression left, right;
	private final BinaryOperator op;

	public BinaryExpression(SourceLocation loc, BinaryOperator op, Expression left, Expression right) {
		super(loc);
		if (left.getType().getClass() != right.getType().getClass()
				&& !SootTranslationHelpers.v().getMemoryModel().isNullReference(right)) {
			// TODO: this should be somewhere in the translation.
			if (left.getType() == BoolType.instance() && right instanceof IntegerLiteral) {
				if (((IntegerLiteral) right).getValue() == 0L) {
					right = BooleanLiteral.falseLiteral();
				} else if (((IntegerLiteral) right).getValue() == 1L) {
					right = BooleanLiteral.trueLiteral();
				} else {
					throw new RuntimeException("BinaryExpression: bool/int confusion");
				}
			} else if (right.getType() == BoolType.instance() && left instanceof IntegerLiteral) {
				if (((IntegerLiteral) left).getValue() == 0L) {
					left = BooleanLiteral.falseLiteral();
				} else if (((IntegerLiteral) left).getValue() == 1L) {
					left = BooleanLiteral.trueLiteral();
				} else {
					throw new RuntimeException("BinaryExpression: bool/int confusion");
				}				
			}
		}

		Preconditions.checkArgument(
				left.getType().getClass() == right.getType().getClass()
						|| SootTranslationHelpers.v().getMemoryModel().isNullReference(right)
						|| SootTranslationHelpers.v().getMemoryModel().isNullReference(left),
				"Types don't match: " + left.getType() + " and " + right.getType() + " for "+left.toString()+op+right.toString());
		// TODO: more type checking depending on operator
		this.left = left;
		this.right = right;
		this.op = op;
	}

	public Expression getLeft() {
		return left;
	}

	public Expression getRight() {
		return right;
	}

	public BinaryOperator getOp() {
		return op;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		sb.append(this.left);
		sb.append(" " + this.op + " ");
		sb.append(this.right);
		sb.append(")");
		return sb.toString();
	}

	@Override
	public Set<IdentifierExpression> getUseIdentifierExpressions() {
		Set<IdentifierExpression> ret = new HashSet<IdentifierExpression>();
		ret.addAll(left.getUseIdentifierExpressions());
		ret.addAll(right.getUseIdentifierExpressions());
		return ret;
	}

	@Override
	public Set<Variable> getDefVariables() {
		// because this can't happen on the left.
		Set<Variable> used = new HashSet<Variable>();
		return used;
	}

	@Override
	public Type getType() {
		switch (op) {
		case Plus:
		case Minus:
		case Mul:
		case Div:
		case Mod: {
			assert (left.getType().equals(right.getType()));
			return left.getType();
		}
		case Eq:
		case Ne:
		case Gt:
		case Ge:
		case Lt:
		case Le: {
			// assert(left.getType().equals(right.getType()));
			return BoolType.instance();
		}
		case And:
		case Or:
		case Implies: {
			assert (left.getType() == BoolType.instance() && right.getType() == BoolType.instance());
			return BoolType.instance();
		}

		case PoLeq: {
			return BoolType.instance();
		}
		
		default: {
			//TODO: more testing here?
			return left.getType();
		}
		}
	}

	@Override
	public BinaryExpression deepCopy() {		
		return new BinaryExpression(getSourceLocation(), op, left.deepCopy(), right.deepCopy());
	}

	public BinaryExpression substitute(Map<Variable, Variable> subs) {
		return new BinaryExpression(getSourceLocation(), op, left.substitute(subs), right.substitute(subs));
	}

	public BinaryExpression substituteVarWithExpression(Map<Variable, Expression> subs) {
		return new BinaryExpression(getSourceLocation(), op, left.substituteVarWithExpression(subs), right.substituteVarWithExpression(subs));
	}

}
