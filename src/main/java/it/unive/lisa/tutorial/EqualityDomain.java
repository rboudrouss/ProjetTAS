package it.unive.lisa.tutorial;

import java.util.function.Predicate;

import it.unive.lisa.analysis.ScopeToken;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.util.representation.StructuredRepresentation;

public class EqualityDomain implements ValueDomain<EqualityDomain> {

    @Override
    public EqualityDomain assign(Identifier id, ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'assign'");
    }

    @Override
    public EqualityDomain smallStepSemantics(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'smallStepSemantics'");
    }

    @Override
    public EqualityDomain assume(ValueExpression expression, ProgramPoint src, ProgramPoint dest, SemanticOracle oracle)
            throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'assume'");
    }

    @Override
    public boolean knowsIdentifier(Identifier id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'knowsIdentifier'");
    }

    @Override
    public EqualityDomain forgetIdentifier(Identifier id) throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'forgetIdentifier'");
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
    public StructuredRepresentation representation() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'representation'");
    }

    @Override
    public EqualityDomain pushScope(ScopeToken token) throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'pushScope'");
    }

    @Override
    public EqualityDomain popScope(ScopeToken token) throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'popScope'");
    }

    @Override
    public boolean lessOrEqual(EqualityDomain other) throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lessOrEqual'");
    }

    @Override
    public EqualityDomain lub(EqualityDomain other) throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lub'");
    }

    @Override
    public EqualityDomain top() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'top'");
    }

    @Override
    public EqualityDomain bottom() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'bottom'");
    }

}
