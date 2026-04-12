package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.combination.ValueCartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;

public class CartesianProductSetFloatEquality
        extends ValueCartesianProduct<ValueEnvironment<SetOfFloatValuesWithOverflow>, EqualityDomain> {

    public CartesianProductSetFloatEquality(
            ValueEnvironment<SetOfFloatValuesWithOverflow> left,
            EqualityDomain right) {
        super(left, right);
    }

    @Override
    public CartesianProductSetFloatEquality mk(
            ValueEnvironment<SetOfFloatValuesWithOverflow> left,
            EqualityDomain right) {
        return new CartesianProductSetFloatEquality(left, right);
    }
}
