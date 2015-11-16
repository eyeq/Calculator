package calculator;

import org.apache.commons.lang3.math.NumberUtils;
import org.nevec.rjm.BigDecimalMath;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class Calculator {
    private static final int PRIORITY_OPERAND;
    private static final int PRIORITY_FUNCTION;
    private static final int SCALE = 128;
    private static final MathContext mc = new MathContext(SCALE, RoundingMode.HALF_UP);

    private static final Map<String, Integer> priorities = new HashMap<String, Integer>();
    private static final Map<String, Object> operators = new HashMap<String, Object>();

    private Map<String, BigDecimal> variables = new HashMap<String, BigDecimal>();

    static {
        setOperators("empty", -1);
        setOperators(")", 0);
        setOperators("+", 1, (x, y) -> x.add(y));
        setOperators("-", 1, (x, y) -> x.subtract(y));
        setOperators("*", 2, (x, y) -> x.multiply(y));
        setOperators("/", 2, (x, y) -> x.divide(y, mc));
        setOperators("%", 2, (x, y) -> x.remainder(y));
        setOperators("^", 3, (x, y) -> BigDecimalMath.pow(x, y));
        final UnaryOperator<BigDecimal> funcFact = x -> BigDecimalMath.Gamma(x.add(BigDecimal.ONE));
        final BinaryOperator<BigDecimal> funcP = (x, y) -> funcFact.apply(x).divide(funcFact.apply(x.subtract(y)), mc);
        setOperators("P", 3, funcP);
        setOperators("C", 3, (x, y) -> funcP.apply(x, y).divide(funcFact.apply(y), mc));
        final BinaryOperator<BigDecimal> funcGcd = (x, y) -> new BigDecimal(x.toBigInteger().gcd(y.toBigInteger()));
        setOperators("gcd", 3, funcGcd);
        setOperators("lcm", 3, (x, y) -> x.subtract(y).multiply(funcGcd.apply(x, y)));
        setOperators("root", 3, (x, y) -> BigDecimalMath.pow(x, BigDecimal.ONE.divide(y, mc)));
        setOperators("func", 4);
        final BigDecimal HALF = new BigDecimal("0.5");
        setOperators("√", 4, x -> BigDecimalMath.pow(x, HALF));
        setOperators("abs",  4, x -> x.abs());
        setOperators("sign", 4, x -> new BigDecimal(x.signum()));
        setOperators("ceil",  4, x -> x.setScale(0, BigDecimal.ROUND_CEILING));
        setOperators("round", 4, x -> x.setScale(0, BigDecimal.ROUND_HALF_UP));
        setOperators("floor", 4, x -> x.setScale(0, BigDecimal.ROUND_FLOOR));
        setOperators("sin",  4, x -> BigDecimalMath.sin(x));
        setOperators("asin", 4, x -> BigDecimalMath.asin(x));
        setOperators("cos",  4, x -> BigDecimalMath.cos(x));
        setOperators("acos", 4, x -> BigDecimalMath.acos(x));
        setOperators("tan",  4, x -> BigDecimalMath.tan(x));
        setOperators("atan", 4, x -> BigDecimalMath.atan(x));
        setOperators("log", 4, x -> {
            int n = x.scale() - x.precision();
            return BigDecimal.valueOf(Math.log10(x.movePointLeft(n).doubleValue())).add(new BigDecimal(n));
        });
        setOperators("ln",  4, x -> BigDecimalMath.log(x));
        setOperators("E", 4, x -> BigDecimalMath.pow(BigDecimal.TEN, x));
        // function's next priority is empty.
        setOperators("operand", 6);
        setOperators(",", 6);
        setOperators("!", 6, funcFact);
        final BigDecimal ROUND = new BigDecimal(360);
        setOperators("°", 6, x -> BigDecimal.valueOf(Math.toRadians(x.remainder(ROUND).doubleValue())));
        setOperators("(", 7);

        PRIORITY_OPERAND = priorities.get("operand");
        PRIORITY_FUNCTION = priorities.get("func");

        setOperators("rand", () -> BigDecimal.valueOf(Math.random()));
        setOperators("gmm", () -> BigDecimalMath.gamma(mc));
        setOperators("pi",  () -> BigDecimalMath.pi(mc));
        setOperators("e",   () -> BigDecimalMath.exp(mc));
    }

    private static void setOperators(String key, Integer priority) {
        priorities.put(key, priority);
    }

    private static void setOperators(String key, Supplier<BigDecimal> function) {
        if(function != null) {
            operators.put(key, function);
        }
    }

    private static void setOperators(String key, Integer priority, UnaryOperator<BigDecimal> function) {
        setOperators(key, priority);
        if(function != null) {
            operators.put(key, function);
        }
    }

    private static void setOperators(String key, Integer priority, BinaryOperator<BigDecimal> function) {
        setOperators(key, priority);
        if(function != null) {
            operators.put(key, function);
        }
    }

    public Calculator() {}

    public void setVariable(String key, BigDecimal decimal) {
        variables.put(key, decimal);
    }

    public BigDecimal getVariable(String key) {
        return variables.get(key);
    }

    private boolean isOperand(String key) {
        return !priorities.containsKey(key);
    }

    private boolean isVariable(String key) {
        return (this.isOperator(key) && operators.get(key) instanceof Supplier) || variables.containsKey(key);
    }

    private boolean isOperator(String key) {
        return operators.containsKey(key);
    }

    public int getPriority(String key) {
        if(this.isOperand(key)) return PRIORITY_OPERAND;
        return priorities.get(key);
    }

    public BigDecimal calculate(String formula) throws IllegalStateException {
        List<String> infix = new ArrayList<String>();
        List<String> postfix = new ArrayList<String>();

        this.pack(formula, infix);
        infix.forEach((str) -> System.out.print(str + " "));
        System.out.println();

        this.postfixNotation(infix, postfix);
        postfix.forEach((str) -> System.out.print(str + " "));
        System.out.println();

        return this.calculate(postfix).round(mc).stripTrailingZeros();
    }

    private List<String> pack(String formula, List<String> list) throws IllegalStateException {
        formula.replaceAll(" ", "");
        formula = '(' + formula + ')';
        formula = formula.replaceAll("\\(\\+", "(0+").replaceAll("\\(\\-", "(0-") + ';';

        StringBuffer key = new StringBuffer();
        boolean isNumber = false;
        for(char ch : formula.toCharArray()) {
            if(Character.isWhitespace(ch)) {
                continue;
            }
            boolean isDigit = Character.isDigit(ch) || ch == '.';
            if(key.toString().isEmpty()) {
                isNumber = isDigit;
            } else if(isNumber && !isDigit) {
                isNumber = false;
                this.addListAndInit(key, list);
            }

            key.append(ch);
            String str = key.toString();
            if(priorities.containsKey(str) || this.isVariable(str)) {
                this.addListAndInit(key, list);
            }
        }
        if(!key.toString().equals(";")) {
            System.out.println(key.toString());
            throw new IllegalStateException();
        }
        return list;
    }

    private List<String> addListAndInit(StringBuffer sb, List<String> list) {
        while(sb.charAt(0) == '0' && sb.length() > 1) {
            sb.deleteCharAt(0);
        }
        if(sb.charAt(0) == '.') {
            sb.insert(0, '0');
        }
        String str = sb.toString();
        sb.delete(0, sb.length());
        if(!list.isEmpty()) {
            String stackTop = list.get(list.size() - 1);
            if(stackTop.equals(")") || (this.getPriority(stackTop) == PRIORITY_OPERAND && !stackTop.equals(","))) {
                if(this.isOperand(str) || this.getPriority(str) == this.getPriority("func") || str.equals("(")) {
                    list.add("*");
                }
            }
        }
        list.add(str);
        return list;
    }

    private List<String> postfixNotation(List<String> infix, List<String> postfix) {
        Stack<String> stack = new Stack<String>();
        stack.push("empty");

        for(String str : infix) {
            String stackTop = stack.peek();
            int priority = getPriority(str);
            if(priority == PRIORITY_FUNCTION)
            {
                ++priority;
            }
            while(!stackTop.equals("(") && priority <= getPriority(stackTop)) {
                this.addListToPop(stack, postfix);
                stackTop = stack.peek();
            }
            if(str.equals(")")) {
                stack.pop();
                continue;
            }
            stack.push(str);
        }
        while(!stack.empty()) {
            this.addListToPop(stack, postfix);
        }
        return postfix;
    }

    private List<String> addListToPop(Stack stack, List list) {
        Object stackTop = stack.pop();
        String key = stackTop.toString();
        if(this.isOperand(key) || this.isOperator(key)) {
            list.add(stackTop);
        }
        return list;
    }

    private BigDecimal calculate(List<String> postfix) throws IllegalStateException {
        Stack<BigDecimal> operands = new Stack<BigDecimal>();
        for(String str : postfix) {
            if(NumberUtils.isNumber(str)) {
                operands.push(new BigDecimal(str).setScale(SCALE));
                continue;
            }

            Object func = operators.get(str);
            BigDecimal temp = null;
            try {
                if(variables.containsKey(str)) {
                    temp = variables.get(str);
                } else if(func instanceof Supplier) {
                    temp = ((Supplier<BigDecimal>) func).get();
                } else if(func instanceof UnaryOperator) {
                    temp = ((UnaryOperator<BigDecimal>) func).apply(operands.pop());
                } else if(func instanceof BinaryOperator) {
                    temp = operands.pop();
                    temp = ((BinaryOperator<BigDecimal>) func).apply(operands.pop(), temp);
                }
            } catch(Exception e) {
                e.printStackTrace();
                throw new IllegalStateException();
            }
            if(temp != null) {
                operands.push(temp.setScale(SCALE));
            }
        }
        if(operands.isEmpty()) {
            throw new IllegalStateException();
        }
        return operands.peek();
    }
}
