package it.unive.lisa.tutorial;

import java.util.Collections;
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
import it.unive.lisa.util.representation.StructuredRepresentation;

public class EqualityDomain extends FunctionalLattice<EqualityDomain, Identifier, EqualityDomain.SetOfIdentifiers> implements ValueDomain<EqualityDomain> {

    public EqualityDomain(SetOfIdentifiers lattice) {
        super(lattice);
    }

    public EqualityDomain(SetOfIdentifiers lattice, Map<Identifier, SetOfIdentifiers> function) {
        super(lattice, function);
    }

    @Override
    public SetOfIdentifiers stateOfUnknown(Identifier key) {
        // for an unknown variable, assume no equalities: top of SetOfIdentifiers
        return new SetOfIdentifiers(Collections.emptySet(), true);
    }

    @Override
    public EqualityDomain mk(SetOfIdentifiers lattice, Map<Identifier, SetOfIdentifiers> function) {
        return new EqualityDomain(lattice, function);
    }

    // Tracks, for each variable, the set of variables known to be equal to it.
    // InverseSetLattice: more elements = more equalities known = more precise = lower in lattice.
    // lub (join at merge) = intersection: keep only equalities that hold in BOTH branches.
    static class SetOfIdentifiers extends InverseSetLattice<SetOfIdentifiers, Identifier> {

        public SetOfIdentifiers(Set<Identifier> elements, boolean isTop) {
            super(elements, isTop);
        }

        @Override
        public SetOfIdentifiers mk(Set<Identifier> set) {
            // isTop = true only when the set is empty and represents the top element
            return new SetOfIdentifiers(set, set.isEmpty());
        }

        @Override
        public SetOfIdentifiers top() {
            // top = empty set with isTop=true: no equality constraints known
            return new SetOfIdentifiers(Collections.emptySet(), true);
        }

        @Override
        public SetOfIdentifiers bottom() {
            // bottom = empty set with isTop=false: unreachable / contradiction
            return new SetOfIdentifiers(Collections.emptySet(), false);
        }
    }

}
