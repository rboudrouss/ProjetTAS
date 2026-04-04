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
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;

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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'assign'");
    }

    @Override
    public EqualityDomain smallStepSemantics(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        return this;
    }

    @Override
    public EqualityDomain assume(ValueExpression expression, ProgramPoint src, ProgramPoint dest, SemanticOracle oracle)
            throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'assume'");
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
                ret = ret.putState(i, new SetOfIdentifiers(updated, updated.isEmpty()));
            }
        }
        return ret;
    }

    @Override
    public EqualityDomain forgetIdentifiersIf(Predicate<Identifier> test) throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'forgetIdentifiersIf'");
    }

    @Override
    public Satisfiability satisfies(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'satisfies'");
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
