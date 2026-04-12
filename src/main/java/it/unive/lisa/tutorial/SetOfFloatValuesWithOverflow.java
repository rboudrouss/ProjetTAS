package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.DivisionOperator;
import it.unive.lisa.symbolic.value.operator.MultiplicationOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.BinaryOperator;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonEq;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonGe;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonGt;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonLe;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonLt;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonNe;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Overflow (Infinity or NaN) is abstracted to TOP.
 * The set is bounded by MAX_N_ELEMENTS, exceeding it also yields TOP.
 */
public class SetOfFloatValuesWithOverflow
        implements BaseNonRelationalValueDomain<SetOfFloatValuesWithOverflow> {

    private static final int MAX_N_ELEMENTS = 5;

    // null values field marks the TOP element
    private final Set<Float> values;

    public static final SetOfFloatValuesWithOverflow TOP = new SetOfFloatValuesWithOverflow((Set<Float>) null);
    public static final SetOfFloatValuesWithOverflow BOTTOM = new SetOfFloatValuesWithOverflow(new HashSet<>());

    private SetOfFloatValuesWithOverflow(Set<Float> values) {
        this.values = values;
    }

    public SetOfFloatValuesWithOverflow(float value) {
        Set<Float> s = new HashSet<>();
        s.add(value);
        this.values = s;
    }

    @Override
    public SetOfFloatValuesWithOverflow top() {
        return TOP;
    }

    @Override
    public SetOfFloatValuesWithOverflow bottom() {
        return BOTTOM;
    }

    @Override
    public boolean isTop() {
        return this == TOP;
    }

    @Override
    public boolean isBottom() {
        return this == BOTTOM;
    }

    @Override
    public SetOfFloatValuesWithOverflow glbAux(SetOfFloatValuesWithOverflow other) throws SemanticException {
        Set<Float> intersection = new HashSet<>(this.values);
        intersection.retainAll(other.values);
        return intersection.isEmpty() ? BOTTOM : new SetOfFloatValuesWithOverflow(intersection);
    }

    @Override
    public SetOfFloatValuesWithOverflow lubAux(SetOfFloatValuesWithOverflow other) throws SemanticException {
        Set<Float> union = new HashSet<>(this.values);
        union.addAll(other.values);
        if (union.size() > MAX_N_ELEMENTS)
            return TOP;
        return new SetOfFloatValuesWithOverflow(union);
    }

    @Override
    public boolean lessOrEqualAux(SetOfFloatValuesWithOverflow other) throws SemanticException {
        // other.isTop() is handled by BaseLattice before reaching here
        return other.values.containsAll(this.values);
    }

    @Override
    public StructuredRepresentation representation() {
        if (isBottom())
            return Lattice.bottomRepresentation();
        if (isTop())
            return Lattice.topRepresentation();
        return new StringRepresentation(Arrays.toString(values.toArray()));
    }

    @Override
    public SetOfFloatValuesWithOverflow evalNonNullConstant(Constant constant, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        if (constant.getValue() instanceof Number) {
            float val = ((Number) constant.getValue()).floatValue();
            if (Float.isInfinite(val) || Float.isNaN(val))
                return TOP;
            return new SetOfFloatValuesWithOverflow(val);
        }
        return BaseNonRelationalValueDomain.super.evalNonNullConstant(constant, pp, oracle);
    }

    @Override
    public SetOfFloatValuesWithOverflow evalBinaryExpression(BinaryOperator operator,
            SetOfFloatValuesWithOverflow left, SetOfFloatValuesWithOverflow right,
            ProgramPoint pp, SemanticOracle oracle) throws SemanticException {

        if (left.isBottom() || right.isBottom())
            return BOTTOM;
        if (left.isTop() || right.isTop())
            return TOP;

        Set<Float> result = new HashSet<>();

        if (operator instanceof AdditionOperator) {
            for (Float l : left.values)
                for (Float r : right.values) {
                    float res = l + r;
                    if (Float.isInfinite(res) || Float.isNaN(res))
                        return TOP;
                    result.add(res);
                }
        } else if (operator instanceof SubtractionOperator) {
            for (Float l : left.values)
                for (Float r : right.values) {
                    float res = l - r;
                    if (Float.isInfinite(res) || Float.isNaN(res))
                        return TOP;
                    result.add(res);
                }
        } else if (operator instanceof MultiplicationOperator) {
            for (Float l : left.values)
                for (Float r : right.values) {
                    float res = l * r;
                    if (Float.isInfinite(res) || Float.isNaN(res))
                        return TOP;
                    result.add(res);
                }
        } else if (operator instanceof DivisionOperator) {
            for (Float l : left.values)
                for (Float r : right.values) {
                    if (r == 0.0f)
                        continue; // this execution path crashes; skip, don't discard others
                    float res = l / r;
                    if (Float.isInfinite(res) || Float.isNaN(res))
                        return TOP;
                    result.add(res);
                }
        } else {
            return BaseNonRelationalValueDomain.super.evalBinaryExpression(operator, left, right, pp, oracle);
        }

        if (result.isEmpty())
            return BOTTOM;
        if (result.size() > MAX_N_ELEMENTS)
            return TOP;
        return new SetOfFloatValuesWithOverflow(result);
    }

    @Override
    public Satisfiability satisfiesBinaryExpression(BinaryOperator operator,
            SetOfFloatValuesWithOverflow left, SetOfFloatValuesWithOverflow right,
            ProgramPoint pp, SemanticOracle oracle) throws SemanticException {

        if (left.isBottom() || right.isBottom())
            return Satisfiability.BOTTOM;
        if (left.isTop() || right.isTop())
            return Satisfiability.UNKNOWN;

        if (operator instanceof ComparisonLt) {
            boolean allSat = true, noneSat = true;
            for (Float l : left.values)
                for (Float r : right.values) {
                    if (l < r)
                        noneSat = false;
                    else
                        allSat = false;
                }
            if (allSat)
                return Satisfiability.SATISFIED;
            if (noneSat)
                return Satisfiability.NOT_SATISFIED;
            return Satisfiability.UNKNOWN;
        }

        if (operator instanceof ComparisonLe) {
            boolean allSat = true, noneSat = true;
            for (Float l : left.values)
                for (Float r : right.values) {
                    if (l <= r)
                        noneSat = false;
                    else
                        allSat = false;
                }
            if (allSat)
                return Satisfiability.SATISFIED;
            if (noneSat)
                return Satisfiability.NOT_SATISFIED;
            return Satisfiability.UNKNOWN;
        }

        if (operator instanceof ComparisonGt) {
            boolean allSat = true, noneSat = true;
            for (Float l : left.values)
                for (Float r : right.values) {
                    if (l > r)
                        noneSat = false;
                    else
                        allSat = false;
                }
            if (allSat)
                return Satisfiability.SATISFIED;
            if (noneSat)
                return Satisfiability.NOT_SATISFIED;
            return Satisfiability.UNKNOWN;
        }

        if (operator instanceof ComparisonGe) {
            boolean allSat = true, noneSat = true;
            for (Float l : left.values)
                for (Float r : right.values) {
                    if (l >= r)
                        noneSat = false;
                    else
                        allSat = false;
                }
            if (allSat)
                return Satisfiability.SATISFIED;
            if (noneSat)
                return Satisfiability.NOT_SATISFIED;
            return Satisfiability.UNKNOWN;
        }

        if (operator instanceof ComparisonEq) {
            boolean allSat = true, noneSat = true;
            for (Float l : left.values)
                for (Float r : right.values) {
                    if ((float) l == (float) r)
                        noneSat = false;
                    else
                        allSat = false;
                }
            if (allSat)
                return Satisfiability.SATISFIED;
            if (noneSat)
                return Satisfiability.NOT_SATISFIED;
            return Satisfiability.UNKNOWN;
        }

        if (operator instanceof ComparisonNe) {
            boolean allSat = true, noneSat = true;
            for (Float l : left.values)
                for (Float r : right.values) {
                    if ((float) l != (float) r)
                        noneSat = false;
                    else
                        allSat = false;
                }
            if (allSat)
                return Satisfiability.SATISFIED;
            if (noneSat)
                return Satisfiability.NOT_SATISFIED;
            return Satisfiability.UNKNOWN;
        }

        return BaseNonRelationalValueDomain.super.satisfiesBinaryExpression(operator, left, right, pp, oracle);
    }

    @Override
    public ValueEnvironment<SetOfFloatValuesWithOverflow> assumeBinaryExpression(
            ValueEnvironment<SetOfFloatValuesWithOverflow> environment,
            BinaryOperator operator, ValueExpression left, ValueExpression right,
            ProgramPoint src, ProgramPoint dest, SemanticOracle oracle)
            throws SemanticException {

        Identifier id;
        SetOfFloatValuesWithOverflow other;
        boolean leftIsId;

        if (left instanceof Identifier && right instanceof Constant) {
            id = (Identifier) left;
            Constant c = (Constant) right;
            if (!(c.getValue() instanceof Number))
                return BaseNonRelationalValueDomain.super.assumeBinaryExpression(
                        environment, operator, left, right, src, dest, oracle);
            other = new SetOfFloatValuesWithOverflow(((Number) c.getValue()).floatValue());
            leftIsId = true;
        } else if (right instanceof Identifier && left instanceof Constant) {
            id = (Identifier) right;
            Constant c = (Constant) left;
            if (!(c.getValue() instanceof Number))
                return BaseNonRelationalValueDomain.super.assumeBinaryExpression(
                        environment, operator, left, right, src, dest, oracle);
            other = new SetOfFloatValuesWithOverflow(((Number) c.getValue()).floatValue());
            leftIsId = false;
        } else {
            return BaseNonRelationalValueDomain.super.assumeBinaryExpression(
                    environment, operator, left, right, src, dest, oracle);
        }

        SetOfFloatValuesWithOverflow current = environment.getState(id);
        if (current.isBottom())
            return environment;

        float bound = other.values.iterator().next();
        if (Float.isNaN(bound) || Float.isInfinite(bound))
            return environment;

        // When current is TOP, only == can produce a finite refinement
        if (current.isTop()) {
            if (operator instanceof ComparisonEq)
                return environment.putState(id, new SetOfFloatValuesWithOverflow(bound));
            return environment;
        }
        Set<Float> filtered = new HashSet<>();

        for (Float v : current.values) {
            boolean keep;
            if (operator instanceof ComparisonLt)
                keep = leftIsId ? v < bound : bound < v;
            else if (operator instanceof ComparisonLe)
                keep = leftIsId ? v <= bound : bound <= v;
            else if (operator instanceof ComparisonGt)
                keep = leftIsId ? v > bound : bound > v;
            else if (operator instanceof ComparisonGe)
                keep = leftIsId ? v >= bound : bound >= v;
            else if (operator instanceof ComparisonEq)
                keep = (float) v == bound;
            else if (operator instanceof ComparisonNe)
                keep = (float) v != bound;
            else
                return BaseNonRelationalValueDomain.super.assumeBinaryExpression(
                        environment, operator, left, right, src, dest, oracle);
            if (keep)
                filtered.add(v);
        }

        SetOfFloatValuesWithOverflow newState = filtered.isEmpty()
                ? BOTTOM
                : new SetOfFloatValuesWithOverflow(filtered);
        return environment.putState(id, newState);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof SetOfFloatValuesWithOverflow))
            return false;
        SetOfFloatValuesWithOverflow other = (SetOfFloatValuesWithOverflow) o;
        return Objects.equals(this.values, other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(values);
    }
}
