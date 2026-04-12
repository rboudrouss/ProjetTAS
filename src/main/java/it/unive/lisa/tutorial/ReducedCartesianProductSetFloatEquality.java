package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.combination.ValueCartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.symbolic.value.Identifier;

public class ReducedCartesianProductSetFloatEquality
        extends ValueCartesianProduct<ValueEnvironment<SetOfFloatValuesWithOverflow>, EqualityDomain> {

    public ReducedCartesianProductSetFloatEquality(
            ValueEnvironment<SetOfFloatValuesWithOverflow> left,
            EqualityDomain right) {
        super(left, right);
    }

    @Override
    public ReducedCartesianProductSetFloatEquality mk(
            ValueEnvironment<SetOfFloatValuesWithOverflow> left,
            EqualityDomain right) {
        ReducedCartesianProductSetFloatEquality res = new ReducedCartesianProductSetFloatEquality(left, right);
        return res.reduce();
    }

    private ReducedCartesianProductSetFloatEquality reduce() {
        ValueEnvironment<SetOfFloatValuesWithOverflow> floatEnv = this.left;
        EqualityDomain equalityDomain = this.right;

        for (Identifier id : floatEnv.getKeys()) {
            EqualityDomain.SetOfIdentifiers equalVars = equalityDomain.getState(id);
            if (equalVars.isTop() || equalVars.elements.isEmpty())
                continue;

            SetOfFloatValuesWithOverflow refined = floatEnv.getState(id);
            for (Identifier eq : equalVars.elements) {
                try {
                    refined = refined.glb(floatEnv.getState(eq));
                } catch (SemanticException e) {
                    // conservative: skip this refinement step
                }
                if (refined.isBottom())
                    break;
            }
            floatEnv = floatEnv.putState(id, refined);
        }

        return new ReducedCartesianProductSetFloatEquality(floatEnv, equalityDomain);
    }
}
