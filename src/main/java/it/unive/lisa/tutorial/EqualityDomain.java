package it.unive.lisa.tutorial;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import it.unive.lisa.analysis.ScopeToken;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.FunctionalLattice;
import it.unive.lisa.analysis.lattices.InverseSetLattice;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonEq;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonNe;

public class EqualityDomain extends FunctionalLattice<EqualityDomain, Identifier, EqualityDomain.SetOfIdentifiers>
        implements ValueDomain<EqualityDomain> {

    public EqualityDomain() {
        super(new SetOfIdentifiers(Collections.emptySet(), true));
    }

    private EqualityDomain(SetOfIdentifiers lattice, Map<Identifier, SetOfIdentifiers> function) {
        super(lattice, function);
    }

    @Override
    public SetOfIdentifiers stateOfUnknown(Identifier key) {
        // for an unknown variable, assume no equalities top of SetOfIdentifiers
        return new SetOfIdentifiers(Collections.emptySet(), true);
    }

    @Override
    public EqualityDomain mk(SetOfIdentifiers lattice, Map<Identifier, SetOfIdentifiers> function) {
        return new EqualityDomain(lattice, function);
    }

    // Tracks, for each variable, the set of variables known to be equal to it.
    // InverseSetLattice: more elements = more equalities known = more precise =
    // lower in lattice.
    // lub (join at merge) = intersection: keep only equalities that hold in BOTH
    // branches.
    static class SetOfIdentifiers extends InverseSetLattice<SetOfIdentifiers, Identifier> {

        public SetOfIdentifiers(Set<Identifier> elements, boolean isTop) {
            super(elements, isTop);
        }

        @Override
        public SetOfIdentifiers mk(Set<Identifier> set) {
            // ensemble vide = top (aucune info), jamais bottom.
            return new SetOfIdentifiers(set, set.isEmpty());
        }

        @Override
        public SetOfIdentifiers top() {
            // top = empty set with isTop=true no equality constraints known
            return new SetOfIdentifiers(Collections.emptySet(), true);
        }

        @Override
        public SetOfIdentifiers bottom() {
            // bottom = empty set with isTop=false. unreachable
            return new SetOfIdentifiers(Collections.emptySet(), false);
        }
    }

    @Override
    public EqualityDomain assign(Identifier id, ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        EqualityDomain ret = forgetIdentifier(id);
        if (!(expression instanceof Identifier))
            return ret;
        Identifier rhs = (Identifier) expression;
        Set<Identifier> merged = new HashSet<>();
        merged.add(id);
        merged.add(rhs);
        merged.addAll(ret.getState(rhs).elements);
        for (Identifier z : merged) {
            Set<Identifier> zSet = new HashSet<>(merged);
            zSet.remove(z);
            ret = ret.putState(z, new SetOfIdentifiers(zSet, false));
        }
        return ret;
    }

    @Override
    public EqualityDomain smallStepSemantics(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        return this;
    }

    @Override
    public EqualityDomain assume(ValueExpression expression, ProgramPoint src, ProgramPoint dest, SemanticOracle oracle)
            throws SemanticException {
        if (!(expression instanceof BinaryExpression))
            return this;
        BinaryExpression bin = (BinaryExpression) expression;
        if (!(bin.getOperator() instanceof ComparisonEq))
            return this;
        if (!(bin.getLeft() instanceof Identifier) || !(bin.getRight() instanceof Identifier))
            return this;
        Identifier left = (Identifier) bin.getLeft();
        Identifier right = (Identifier) bin.getRight();
        Set<Identifier> merged = new HashSet<>();
        merged.add(left);
        merged.addAll(getState(left).elements);
        merged.add(right);
        merged.addAll(getState(right).elements);
        EqualityDomain ret = this;
        for (Identifier z : merged) {
            Set<Identifier> zSet = new HashSet<>(merged);
            zSet.remove(z);
            ret = ret.putState(z, new SetOfIdentifiers(zSet, false));
        }
        return ret;
    }

    @Override
    public boolean knowsIdentifier(Identifier id) {
        return function != null && function.containsKey(id);
    }

    @Override
    public EqualityDomain forgetIdentifier(Identifier id) throws SemanticException {
        if (isTop() || isBottom() || function == null)
            return this;
        EqualityDomain ret = this;
        if (function.containsKey(id))
            ret = ret.putState(id, lattice.top());
        for (Identifier i : function.keySet()) {
            if (function.get(i).contains(id)) {
                Set<Identifier> updated = new HashSet<>(this.getState(i).elements);
                updated.remove(id);
                // updated.isEmpty() = isTop=true (top, pas bottom), oublier une variable
                // ne rend jamais le code inatteignable.
                ret = ret.putState(i, new SetOfIdentifiers(updated, updated.isEmpty()));
            }
        }
        return ret;
    }

    @Override
    public EqualityDomain forgetIdentifiersIf(Predicate<Identifier> test) throws SemanticException {
        if (isTop() || isBottom() || function == null)
            return this;
        EqualityDomain ret = this;
        for (Identifier id : function.keySet())
            if (test.test(id))
                ret = ret.forgetIdentifier(id);
        return ret;
    }

    @Override
    public Satisfiability satisfies(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        if (isBottom())
            return Satisfiability.BOTTOM;
        if (!(expression instanceof BinaryExpression))
            return Satisfiability.UNKNOWN;
        BinaryExpression bin = (BinaryExpression) expression;
        if (!(bin.getLeft() instanceof Identifier) || !(bin.getRight() instanceof Identifier))
            return Satisfiability.UNKNOWN;
        Identifier left = (Identifier) bin.getLeft();
        Identifier right = (Identifier) bin.getRight();
        boolean knownEqual = getState(left).elements.contains(right);
        if (bin.getOperator() instanceof ComparisonEq)
            return knownEqual ? Satisfiability.SATISFIED : Satisfiability.UNKNOWN;
        if (bin.getOperator() instanceof ComparisonNe)
            return knownEqual ? Satisfiability.NOT_SATISFIED : Satisfiability.UNKNOWN;
        return Satisfiability.UNKNOWN;
    }

    @Override
    public EqualityDomain pushScope(ScopeToken token) throws SemanticException {
        return this;
    }

    @Override
    public EqualityDomain popScope(ScopeToken token) throws SemanticException {
        return this;
    }

    @Override
    public EqualityDomain top() {
        return new EqualityDomain(lattice.top(), null);
    }

    @Override
    public EqualityDomain bottom() {
        return new EqualityDomain(lattice.bottom(), null);
    }

}
