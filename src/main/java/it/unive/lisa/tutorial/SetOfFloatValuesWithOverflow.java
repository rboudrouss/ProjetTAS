package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.util.representation.StructuredRepresentation;

public class SetOfFloatValuesWithOverflow implements BaseNonRelationalValueDomain<SetOfFloatValuesWithOverflow>{

    @Override
    public SetOfFloatValuesWithOverflow lubAux(SetOfFloatValuesWithOverflow other) throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lubAux'");
    }

    @Override
    public boolean lessOrEqualAux(SetOfFloatValuesWithOverflow other) throws SemanticException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lessOrEqualAux'");
    }

    @Override
    public SetOfFloatValuesWithOverflow top() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'top'");
    }

    @Override
    public SetOfFloatValuesWithOverflow bottom() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'bottom'");
    }

    @Override
    public StructuredRepresentation representation() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'representation'");
    }
    
}
