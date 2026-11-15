package practice.prep.problems;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// infix: (1+(4+5+2)*3)+(6+8)
// postfix: 1 4 5 + 2 + 3 * + 6 8 + +
//
// infix: U- 2 * 3 + 20 / 2
// postfix: 2 U- 3 * 20 2 / +
//
// infix: 2 * 3 * 4 + 5 * 6
// postfix: 2 3 * 4 * 5 6 * +
//
// infix: 2 + 4 * 5
// opStack: 
// postfix: 2 4 5 * +
//
// infix: (2 + 4) * 3
// opStack:
// postfix: 2 4 + 3 *
//
// infix: -(1 + 4) * 2
// opStack:
// postfix: 1 4 + U- 2 *
public class Calculator {
    public int calculate(String s) {
        List<String> tokensStr = splitKeepDelimiters(s, "[-+*/()]");
        List<Token> tokens = new ArrayList<>();
        for (String t : tokensStr) {
            if (t == null || t.isBlank()) {
                continue;
            }

            Token prev = tokens.size() == 0 ? null : tokens.get(tokens.size() - 1);
            Token token = new Token(prev, t);
            tokens.add(token);
        }

        List<Token> postOrder = convertToPostOrder(tokens);
        Deque<Integer> stack = new LinkedList<>();
        for (Token t : postOrder) {
            if (t.isOp()) {
                t.getOp().eval(stack);
            } else {
                stack.addFirst(t.getVal());
            }
        }

        return stack.removeFirst();
    }

    private static List<Token> convertToPostOrder(List<Token> tokens) {
        List<Token> postOrderTokens = new ArrayList<>();
        Deque<Token> prevOpTokens = new LinkedList<>();
        for (Token t : tokens) {
            if (t.isOp()) {
                if (t.getOp() == Operator.OPENB) {
                    prevOpTokens.addFirst(t);
                } else if (t.getOp() == Operator.CLOSEB) {
                    while (prevOpTokens.peekFirst().getOp() != Operator.OPENB) {
                        postOrderTokens.add(prevOpTokens.removeFirst());
                    }

                    prevOpTokens.removeFirst();
                } else {
                    while (!prevOpTokens.isEmpty() && !t.getOp().hasHigherPrec(prevOpTokens.peekFirst().getOp())) {
                        postOrderTokens.add(prevOpTokens.removeFirst());
                    }

                    prevOpTokens.addFirst(t);
                }
            } else {
                postOrderTokens.add(t);
            }
        }

        while (!prevOpTokens.isEmpty()) {
            postOrderTokens.add(prevOpTokens.removeFirst());
        }

        return postOrderTokens;
    }

    private static List<String> splitKeepDelimiters(String text, String regex) {
        List<String> parts = new ArrayList<>();
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            parts.add(text.substring(lastEnd, matcher.start()));
            parts.add(matcher.group());
            lastEnd = matcher.end();
        }

        parts.add(text.substring(lastEnd));
        return parts;
    }

    private static class Token {
        private final Optional<Operator> op;
        private final Optional<Integer> val;

        public Token(Token prev, String s) {
            switch (s.trim()) {
                case "+":
                case "-":
                case "*":
                case "/":
                case "(":
                case ")":
                    Operator operator = Operator.getOp(s.trim());
                    if (operator == Operator.MINUS && (prev == null || (prev.isOp() && prev.op.get() != operator.CLOSEB))) {
                        operator = Operator.UMINUS;
                    }

                    op = Optional.of(operator);
                    val = Optional.empty();
                    break;
                default:
                    op = Optional.empty();
                    val = Optional.of(Integer.valueOf(s.trim()));
            }
        }

        public int getVal() {
            return val.get();
        }

        public Operator getOp() {
            return op.get();
        }

        public boolean isOp() {
            return !op.isEmpty();
        }

        @Override
        public String toString() {
            if (!op.isEmpty()) {
                return op.get().toString();
            } else {
                return val.get().toString();
            }
        }
    }

    private enum Operator {
        ADD("+"),
        MINUS("-"),
        MULTIPLY("*"),
        DIVIDE("/"),
        OPENB("("),
        CLOSEB(")"),
        UMINUS("U-");

        private final String opStr;

        private Operator(String opStr) {
            this.opStr = opStr;
        }

        public static Operator getOp(String s) {
            switch (s) {
                case "+":
                    return ADD;
                case "-":
                    return MINUS;
                case "*":
                    return MULTIPLY;
                case "/":
                    return DIVIDE;
                case "(":
                    return OPENB;
                case ")":
                    return CLOSEB;
                default:
                    throw new RuntimeException("Unknown Operator: " + s);
            }
        }

        public void eval(Deque<Integer> stack) {
            switch (this) {
                case UMINUS: {
                    int operand = stack.removeFirst();
                    stack.addFirst(-operand);
                    break;
                }
                case ADD: {
                    int rightOperand = stack.removeFirst();
                    int leftOperand = stack.removeFirst();
                    stack.addFirst(leftOperand + rightOperand);
                    break;
                }
                case MINUS: {
                    int rightOperand = stack.removeFirst();
                    int leftOperand = stack.removeFirst();
                    stack.addFirst(leftOperand - rightOperand);
                    break;
                }
                case MULTIPLY: {
                    int rightOperand = stack.removeFirst();
                    int leftOperand = stack.removeFirst();
                    stack.addFirst(leftOperand * rightOperand);
                    break;
                }
                case DIVIDE: {
                    int rightOperand = stack.removeFirst();
                    int leftOperand = stack.removeFirst();
                    stack.addFirst(leftOperand / rightOperand);
                    break;
                }
                default:
                    throw new RuntimeException(String.format("Unexpected operator (%s) during expression evaluation.", this));
            }
        }

        public boolean hasHigherPrec(Operator other) {
            if (other == OPENB) {
                return true;
            }

            switch (this) {
                case UMINUS:
                    return true;
                case MULTIPLY:
                case DIVIDE:
                    return other == ADD || other == MINUS;
                default:
                    return false;
            }
        }

        @Override
        public String toString() {
            return opStr;
        }
    }
}
